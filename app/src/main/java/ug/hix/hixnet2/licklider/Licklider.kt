package ug.hix.hixnet2.licklider

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.work.*
import com.google.gson.Gson
import com.snatik.storage.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.database.*
import ug.hix.hixnet2.models.*
import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.database.File as DFile
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.workers.MergeFileWorker
import ug.hix.hixnet2.workers.SendFileWorker
import java.io.File
import java.io.RandomAccessFile
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.Delegates

@ExperimentalCoroutinesApi
class Licklider(private val mContext: Context, private val repo: Repository){

    private val TAG = javaClass.simpleName
    private var group : InetAddress? = null
    private val storage = Storage(mContext)
    private var keyCounter = ConcurrentHashMap<String,Int>()
    private val ackTimerMap = ConcurrentHashMap<String,Job>()
    private val seqTimerMap = ConcurrentHashMap<String, Job>()
    private val nextPacket = ConcurrentHashMap<String,Int>()
    private val outOfSeqPackets = ConcurrentHashMap<String,List<Int>>()
    private val receiverBuffer = ConcurrentHashMap<String,ByteArray>()
    private val senderBuffer = ConcurrentHashMap<String, List<Packet>>()
    private val job = Job()
    private val scopeDefault = CoroutineScope(job + Dispatchers.Default)

    private val PORT = 33456
    private var p2pSocket = MulticastSocket(33456)
    private var wlanSocket = MulticastSocket(33456)
    private val p2p0 = NetworkInterface.getByName("p2p0")
    private val wlan0 = NetworkInterface.getByName("wlan0")
    lateinit var sockAddress : InetSocketAddress

    private val device  = runBlocking {repo.getMyDeviceInfo()}

    private var primaryBuffers = mutableListOf<Packet>()
    private var queueBuffer = mutableListOf<Packet>()

    private val RESEND_TIMEOUT = 600L
    private val ACK_TIMEOUT = 300L

    private var myMessagesQueue = mutableListOf<Packet>()

    init {
        listenerJob = listeners() //mesh update listeners
    }

    suspend fun loadData(message: Any, toMeshId: String = ""){
        Log.d(TAG,"Sending. $message to $toMeshId")
        var isFile = false
        var packet = Packet()
        lateinit var buffer: ByteArray
        packet = packet.copy(packetID = Generator.genMID(),originalFromMeshID = device.meshID,toMeshID = toMeshId)
        when (message) {
            is String -> {
                if(message.startsWith("HELLO")) {
                    packet = packet.copy(messageType = "HELLO",port = PORT)
                    buffer  = message.toByteArray()
                }

            }
//            is Command -> {
//                packet = packet.copy(messageType = message.type,port = PORT)
//                buffer  = Command.ADAPTER.encode(message)
//            }
            is ACK -> {
                packet = packet.copy(messageType = message.type,port = PORT)
                buffer = ACK.ADAPTER.encode(message)

            }
            is FileHashMap -> {
                packet = packet.copy(messageType = "filesUpdate",port = PORT)
                buffer = FileHashMap.ADAPTER.encode(message)
            }
            is PFileSeeder -> {
                packet = packet.copy(messageType = message.type,port = PORT)
                buffer = PFileSeeder.ADAPTER.encode(message)
            }
            is PName -> {
                packet = packet.copy(messageType = message.type,port = PORT)
                buffer = PName.ADAPTER.encode(message)
            }
            is PFileName -> {
                packet = packet.copy(messageType = message.type, port = PORT)
                buffer = PFileName.ADAPTER.encode(message)
            }
            is DeviceNode -> {
                packet = packet.copy(messageType = message.type, port = PORT)
                buffer  = DeviceNode.ADAPTER.encode(message)
            }
            is ListPFileSeeder -> {
                packet = packet.copy(messageType = message.type, port = PORT)
                buffer = ListPFileSeeder.ADAPTER.encode(message)
            }
            is ListPName -> {
                packet = packet.copy(messageType = message.type, port = PORT)
                buffer = ListPName.ADAPTER.encode(message)
            }
            is ListPFileName -> {
                packet = packet.copy(messageType = message.type, port = PORT)
                buffer = ListPFileName.ADAPTER.encode(message)
            }
            is ListDeviceNode -> {
                packet = packet.copy(messageType = message.type, port = PORT)
                buffer  = ListDeviceNode.ADAPTER.encode(message)
            }
            is TransFile -> {
                packet = packet.copy(messageType = "FILE",port = PORT)
                buffer = TransFile.ADAPTER.encode(message)

            }
            is DFile -> {
                isFile = true
                val file = File(message.path!!)
                packet = packet.copy(messageType = "FILE",port = PORT, packetID = message.CID)
                splitter(mPacket = packet, file = file)
            }
        }
        if(!isFile){
            splitter(packet,buffer)
        }
    }
    suspend fun loadData(message: ByteArray, type: String, toMeshId: String = ""){
        var packet = Packet()
        packet = packet.copy(packetID = Generator.genMID(), messageType = type, port = PORT, originalFromMeshID = device.meshID,toMeshID = toMeshId)
        splitter(packet, message)
    }
    suspend fun loadData(mFile : DFile, offsets: List<Int>,toMeshId: String){
        var packet = Packet()
        val file = File(mFile.path!!)
        packet = packet.copy(messageType = "FILE",port = PORT, packetID = mFile.CID,originalFromMeshID = device.meshID,toMeshID = toMeshId)
        splitter(mPacket = packet, file = file, offsets = offsets)
    }

    private suspend fun splitter(mPacket : Packet, buffer: ByteArray? = null, file: File? = null, offsets: List<Int>? = null){
        var packet = mPacket
        val blockSize = 1200
        val runtime = Runtime.getRuntime()
        val blockCount = if(file != null){
            ( file.length().toInt() + blockSize - 1) / blockSize
        }else{
            (buffer!!.size + blockSize - 1) / blockSize
        }
        packet = packet.copy(expected = blockCount)
            if(file != null){
                var i = 1
                var readBuffer = ByteArray(1200)
                if(offsets != null){
                    if(offsets.contains(-1))
                        packet = packet.copy(offset = -1, payload = "${file.name}:::$blockCount".toByteArray().toByteString())
                }else
                    packet = packet.copy(offset = -1, payload = "${file.name}:::$blockCount".toByteArray().toByteString())

               forward(packet)
                withContext(Dispatchers.IO){
                    while(true){
                        if(runtime.freeMemory() >= 10000) {
                            if(offsets != null){
                                if( i in offsets){
                                    val randomAccessFile = RandomAccessFile(file,"r")
                                    if(randomAccessFile.length() < i*1200L || randomAccessFile.length() == i*1200L) {
                                        randomAccessFile.close()
                                        break
                                    }
                                    randomAccessFile.seek((i-1)*1200L)
                                    Log.d("fp","filePointer: ${randomAccessFile.filePointer} length: ${randomAccessFile.length()}")
                                    randomAccessFile.read(readBuffer,0,readBuffer.size)
                                    randomAccessFile.close()
                                    packet = packet.copy(offset = i,payload = readBuffer.toByteString())
                                    forward(packet)
                                }
                            }else{
                                val fileStream = file.inputStream().buffered(1024 * 1024)
                                val remBytes = (file.length() - i*1200L).toInt()
                                if( remBytes == 0 || remBytes < 0) {
                                    fileStream.close()
                                    break
                                }
                                if(remBytes < 1200){
                                    readBuffer = ByteArray(remBytes.rem(1200))
                                }
                                fileStream.read(readBuffer,0,readBuffer.size)
                                packet = packet.copy(offset = i,payload = readBuffer.toByteString())
                                fileStream.close()
                                forward(packet)
                            }
                            i++
                        }else{
                            Log.e("runningOut", " I AM ALMOST DONE HERE")
                        }
                    }

                }
            }else{
                var i = 1
                while(i < blockCount){
                    val idx = (i - 1) * blockSize
                    val range = buffer!!. copyOfRange(idx, idx + blockSize)
                    packet = packet.copy(payload = range.toByteString(),offset = i)
                    forward(packet)
                    i++
                }

                // Last chunk from the calculation

                val end = if (buffer!!.size % blockSize == 0) {
                    buffer.size
                } else {
                    buffer.size % blockSize + blockSize * (blockCount - 1)
                }
                val range = buffer.copyOfRange((blockCount - 1) * blockSize, end)
                packet = packet.copy(payload = range.toByteString(),offset = blockCount)
                forward(packet)
            }
    }

    fun receiver(multicastAddress : String) = scopeDefault.launch{
        if(!multicastLock.isHeld){
            multicastLock.acquire()
        }
        if(group != null){
            withContext(Dispatchers.IO){
                p2pSocket.leaveGroup(sockAddress,p2p0)
                wlanSocket.leaveGroup(sockAddress,wlan0)
//                if (receiverJob.isActive)
//                    receiverJob.cancelChildren()
                while(!wlanSocket.isClosed) wlanSocket.close()
                while(!p2pSocket.isClosed ) p2pSocket.close()

                wlanSocket = MulticastSocket(33456)
                p2pSocket = MulticastSocket(33456)

            }
            Log.e(TAG,"finished closing sockets")
        }
        group = withContext(Dispatchers.IO){ InetAddress.getByName(multicastAddress) }
        Log.e(TAG,"GROUP ADDRESS")
        sockAddress = InetSocketAddress(group,PORT)
        Log.e(TAG,"NETSOCK   $sockAddress")
        coroutineScope {
            Log.e(TAG,"New receiver socket create")
            launch(Dispatchers.IO){
                p2pSocket.joinGroup(sockAddress,p2p0)
                while(true){
                    val rBuffer = ByteArray(1500)
                    val packet = DatagramPacket(rBuffer, rBuffer.size)
                    try{
                        p2pSocket.receive(packet)
                        Log.d("p2p0receiver","p2p0 data received")
                        scopeDefault.launch{
                            packetHandler(packet.data, "p2p0")
                        }
                    }catch(e: SocketException) {
                        Log.e(TAG,"Error in p22p receiver",e)
                        break
                    }

                }
            }

            launch(Dispatchers.IO){
                wlanSocket.joinGroup(sockAddress,wlan0)
                while(true) {
                    val rBuffer = ByteArray(1500)
                    val packet = DatagramPacket(rBuffer, rBuffer.size)
                    try{
                        wlanSocket.receive(packet)
                        Log.d("wlan0receiver","wlan0 data received")
                        scopeDefault.launch{
                            packetHandler(packet.data,"wlan0")
                        }
                    }catch(e: SocketException) {
                        Log.e(TAG,"Error in wlan0 receiver",e)
                        break
                    }

                }
            }
//            launch{
//                while(true){
//                    if(queueBuffer.isNotEmpty()){
//                        queueBuffer.forEach { packet ->
//                            coroutineLock.withLock {
//                                queueBuffer.remove(packet)
//                            }
//                            forward(packet)
//                            Log.d(TAG,"removed packet: $packet remaining buffer: $queueBuffer")
//                        }
//                    }
//                }
//            }
//            ack sender
//            launch{
//                val filesMap = mutableMapOf<String,Int>()
//                val path = withContext(Dispatchers.IO){mContext.getExternalFilesDir(null)?.absolutePath + "/Chunks"}
//                while (true) {
//                    if(withContext(Dispatchers.IO){storage.isDirectoryExists(path)}) {
//                        val directoryList = withContext(Dispatchers.IO) {
//                            storage.getFiles(path).map { it.absolutePath }
//                        }
//                        filesMap.keys.forEach { key ->
//                            if (key !in directoryList) filesMap.remove(key)
//                        }
//                        if (directoryList.isNotEmpty()) {
//
//                            directoryList.forEach { directory ->
//                                val fileCount = withContext(Dispatchers.IO) {
//                                    storage.getFiles(directory).count()
//                                }
//                                if (filesMap.keys.contains(directory)) {
//                                    if (filesMap[directory] == fileCount) {
////                                        TODO("Listen for merge worker if running stop and delete cache else proceed")
//                                        val packetAckId = directory.split("/").last().drop(1)
//                                        val workManager = WorkManager.getInstance(mContext)
//                                        val workInfos = workManager.getWorkInfosByTag(packetAckId).await()
//                                        if(workInfos.isNotEmpty()){
//                                            val workInfo = workInfos.first()
//                                            if(workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.BLOCKED){
////                                                DO NOTHING SINCE IS RUNNING
//                                                Log.d(TAG,"merge worker tag => $packetAckId  is running")
//                                            }else{
//                                                Log.d(TAG,"Merger worker dead maybe")
//                                                sendAck(storage,directory,packetAckId)
//                                            }
//                                        }else{
//                                            Log.d(TAG,"no info available about worker")
//                                            sendAck(storage,directory,packetAckId)
//                                        }
//                                    } else
//                                        filesMap[directory] = fileCount
//                                } else
//                                    filesMap[directory] = fileCount
//                            }
//                        }
//                    }
//                    delay(2000L)
//                }
//            }
        }
    }

    private suspend fun sendAck(storage: Storage, directory: String, packetAckId: String){
        var packetAck = ACK()
        val files = withContext(Dispatchers.IO) {
            storage.getFiles(directory).map { it.absolutePath } as MutableList
        }
        if ("$directory/filename.txt" !in files) {
            packetAck = packetAck.copy(
                ackID = packetAckId,
                expectedOffset = listOf(-1),
                toMeshID = device.meshID
            )
            loadData(message = packetAck, toMeshId = device.meshID)
        } else {
            files.remove("$directory/filename.txt")
            val offsets = files.map{it.split("/").last().split(".").first().toInt()} as MutableList
            val expected = withContext(Dispatchers.IO) {
                storage.readTextFile("$directory/filename.txt").split(":::").last()
                    .toInt()
            }
            val missingPackets = mutableListOf<Int>()
            (1..expected).forEach { value ->
                if(value in offsets)
                    offsets.remove(value)
                else
                    missingPackets.add(value)

            }

            packetAck = packetAck.copy(
                ackID = packetAckId,
                expectedOffset = missingPackets,
                toMeshID = device.meshID
            )
            loadData(message = packetAck, toMeshId = device.meshID)
        }
    }

    private suspend fun forward(mPacket: Packet, reliable : Boolean = true){
        var packet = mPacket
        val link = getLink(packet.toMeshID,packet.messageType)
        //send the packet if route found
        if(link.first().first != "notAvailable") {
            val data = Packet.ADAPTER.encode(packet)
            val port = packet.port
            packet = packet.copy(fromMeshID = device.multicastAddress)
            //check if primary mesh buffers are empty
    //        if(primaryBuffers.isEmpty()) {
    //        if(receiverBuffer.isEmpty()) {
    //            if(link.first().first != "notAvailable"){

    //                link.forEach { address ->
    //                    packet = packet.copy(fromMeshID = device.meshID,timeToLive = address.third*2)
    //                    send(data,address.first,port,address.second)
    //                }
    //            }else{
    //                if(!packet.messageType.endsWith("Update"))
    //                    coroutineLock.withLock {
    //                        queueBuffer.add(packet)
    //                    }
    //                else {}
    //            }
    //        }else{
    //            coroutineLock.withLock {
    //                queueBuffer.add(packet)
    //            }
    //        }
            when {
                packet.messageType.startsWith("ACK") -> {
                    //DO NOTHING
                }
                packet.messageType == "FILE" -> {
    //                ensure that the receiverBuffer is empty before any transmissions

                }
                else -> {
                    if(reliable) {
    //                these are mesh updates
                        if (senderBuffer.containsKey(packet.packetID)) {
    //                    append packet to the senderBuffer
                            val packets = senderBuffer[packet.packetID]!!
                            senderBuffer[packet.packetID] = packets.plus(packet)
                        } else {
                            senderBuffer[packet.packetID] = listOf(packet)
                        }
//                        reset seqTimer on each packet chunk
                        val previousSeqTimer = seqTimerMap[packet.packetID]
                        previousSeqTimer?.cancel()
                        seqTimerMap[packet.packetID] = seqTimer(packet, 1)
                    }
                    Log.e(TAG,"senderBuffer: $senderBuffer")
                }

            }

            link.forEach { address ->
//                packet = packet.copy(fromMeshID = device.meshID, timeToLive = address.third * 2)
                send(data, address.first, port, address.second)
            }
        }
    }

    private suspend fun getLink(meshId : String, packetType : String) : List<Triple<String,String,Int>> {
        return when {
            packetType.endsWith("update",true) && !packetType.startsWith("ACK")-> {
                repo.getNearLinks(meshId) //get all neighbour links except parent of meshId
//            repo.getNearLinks()  //TEST get all the multiAddress with digits
            }
//            packetType == "ACK" -> {
//                //                TODO("get address nearest with file")
////                this in a test will be deleted
//                listOf(repo.getLink(meshId))
//            }
            packetType.endsWith("helloAck",true) -> {
                listOf(Triple(meshId,"p2p0",1))
            }
            packetType.endsWith("hello",true) -> {
                listOf(Triple(meshId, "wlan0", 1))
            }
            packetType.endsWith("helloAckList",true) -> {
                listOf(Triple(meshId,"p2p0",1))
            }
            packetType.endsWith("helloList",true) -> {
                listOf(Triple(meshId, "wlan0", 1))
            }else -> {
                listOf(repo.getLink(meshId))
            }
        }

    }
    private suspend fun packetHandler(buffer : ByteArray, iFace: String) = withContext(Dispatchers.IO){
//        var nonZeroIndex by Delegates.notNull<Int>()
        var count = 0
//        val newBuffer = buffer.reversedArray()
        lateinit var packet: Packet
//        for(byte in newBuffer){
//            if(byte.toString() != "0"){
//                nonZeroIndex = count
//                break
//            }
//            count++
//
//        }

//        val filteredBuffer = buffer.copyOfRange(0,buffer.size-nonZeroIndex)
        val filteredBuffer = buffer.dropLastWhile { it.toString() == "0" }.toByteArray()
        try{
            packet = Packet.ADAPTER.decode(filteredBuffer)

            //we don't handle packet from our own node in case of broadcast conflict
            if(packet.originalFromMeshID != device.meshID){
                if(packet.toMeshID in listOf(device.meshID, device.multicastAddress)){
                    dataMapping(packet,iFace)
                }else{
                    forward(packet)
                }
            }else{}

        }catch(e: Exception){
            Log.e(TAG,"FAILED TO HANDLE",e)
        }

    }

    private suspend fun  dataMapping(packet : Packet, iFace: String){
        try{
            when {
                packet.messageType == "FILE" -> {
                    // to be implemented later
                    val path = mContext.getExternalFilesDir(null)?.absolutePath
                    if(!storage.isDirectoryExists(path +"/Chunks/.${packet.packetID}")){
                        storage.createDirectory(path +"/Chunks/.${packet.packetID}")
                    }
                    if(packet.offset != -1)
                        storage.createFile(path +"/Chunks/.${packet.packetID}/${packet.offset}.prt",packet.payload.toByteArray())
                    else{
                        storage.createFile(path +"/Chunks/.${packet.packetID}/filename.txt", String(packet.payload.toByteArray()))
                    }
                    if(storage.getFiles(path +"/Chunks/.${packet.packetID}/").count() == packet.expected + 1 ){
                        val mergeWorker = OneTimeWorkRequestBuilder<MergeFileWorker>()
                            .setInputData(workDataOf("fileDir" to path +"/Chunks/.${packet.packetID}"))
                            .addTag(packet.packetID)
                            .build()
                        WorkManager.getInstance(mContext).enqueueUniqueWork(packet.packetID,ExistingWorkPolicy.KEEP,mergeWorker)
                    }
                }
                packet.messageType.startsWith("ACK") -> {
                    decodeData(packet.payload.toByteArray(),packet.messageType,iFace)

                }
                else -> {
                    if(receiverBuffer.containsKey(packet.packetID)){
                        if(packet.offset < nextPacket[packet.packetID]!!){
                            //drop the packet and send ack for next expected packet
                            val ack = ACK(packet.packetID,listOf(nextPacket[packet.packetID]!!),type= "ACK-${packet.messageType}")
                            loadData(ack,packet.fromMeshID)
                        }else{
                            val buffer = receiverBuffer[packet.packetID]!!
                            var nextIndex = nextPacket[packet.packetID]!!
                            if(packet.offset > nextIndex) {
                                val outOfSeqList = outOfSeqPackets[packet.packetID]!!
                                outOfSeqPackets[packet.packetID] = outOfSeqList.plus(packet.offset)
                                //send ack for next expected  packet
                                val ack = ACK(packet.packetID,listOf(nextIndex),type= "ACK-${packet.messageType}")
                                loadData(ack,packet.fromMeshID)
                            }
//                        increment expected to next and must not be in out seq
                            while(true) {
                                nextIndex += 1
                                if(!outOfSeqPackets[packet.packetID]!!.contains(nextIndex)) break
                            }
                            nextPacket[packet.packetID] = nextIndex

                            /**
                             * write the received byte data buffer positions
                             */
                            val startIndex = (packet.offset - 1)*1200
                            packet.payload.toByteArray().forEachIndexed { index, byte ->
                                buffer[startIndex + index] = byte
                            }
                        }

                    }else{
                        outOfSeqPackets[packet.packetID] = listOf()
                        val buffer = ByteArray(packet.expected * 1200)
                        val startIndex = (packet.offset - 1)*1200
                        packet.payload.toByteArray().forEachIndexed { index, byte ->
                            buffer[startIndex + index] = byte
                        }
                        receiverBuffer[packet.packetID] = buffer
                        if(packet.offset != 1) {
                            nextPacket[packet.packetID] = 1
                            outOfSeqPackets[packet.packetID] = listOf(packet.offset)
                        }else{
                            nextPacket[packet.packetID] = packet.offset+1
                        }
                    }

                    if(nextPacket[packet.packetID]!! > packet.expected){
                        val data = receiverBuffer[packet.packetID]!!.dropLastWhile { it.toString() == "0" }.toByteArray()
//                    send empty ack to signify end of packet transfer
                        val ack = ACK(packet.packetID,listOf(),type= "ACK-${packet.messageType}")
                        loadData(ack,packet.fromMeshID)
//                 HOUSE CLEANING
                        receiverBuffer.remove(packet.packetID)
//                  nextPacket.remove(packet.packetID)    //keep reference to last so no duplicates received
                        outOfSeqPackets.remove(packet.packetID)
                        ackTimerMap[packet.packetID]?.cancel()   //stop ackTimer for the packet
                        decodeData(data,packet.messageType,iFace)

                    }else{
                        //reset the ack timer for that packetID
                        val previousAckTimer = ackTimerMap[packet.packetID]
                        previousAckTimer?.cancel()
                        ackTimerMap[packet.packetID] = ackTimer(packet)
                    }
                }
            }
//        if(!checkForbidden(packet)){
//            if(packet.messageType == "FILE"){
//                withContext(Dispatchers.IO){
//                    // to be implemented later
//                    val path = mContext.getExternalFilesDir(null)?.absolutePath
//                    if(!storage.isDirectoryExists(path +"/Chunks/.${packet.packetID}")){
//                        storage.createDirectory(path +"/Chunks/.${packet.packetID}")
//                    }
//                    if(packet.offset != -1)
//                        storage.createFile(path +"/Chunks/.${packet.packetID}/${packet.offset}.prt",packet.payload.toByteArray())
//                    else{
//                        storage.createFile(path +"/Chunks/.${packet.packetID}/filename.txt", String(packet.payload.toByteArray()))
//                    }
//                    if(storage.getFiles(path +"/Chunks/.${packet.packetID}/").count() == packet.expected + 1 ){
//                        val mergeWorker = OneTimeWorkRequestBuilder<MergeFileWorker>()
//                            .setInputData(workDataOf("fileDir" to path +"/Chunks/.${packet.packetID}"))
//                            .addTag(packet.packetID)
//                            .build()
//                        WorkManager.getInstance(mContext).enqueueUniqueWork(packet.packetID,ExistingWorkPolicy.KEEP,mergeWorker)
//                    }
//                }
//            }
//            else{
//                var info = byteArrayOf()
//
//                if(packet.expected > 1){
//                    val key = packet.packetID
//
//                    //enqueue message for sorting
//                    if(keyCounter.containsKey(key)){
//                        val value = keyCounter[key]
//                        keyCounter[key] = value!! + 1
//                    }else{
//                        keyCounter[key]  = 1
//                    }
//                    when(packet.messageType){
//                        "meshUpdate" -> {
////                        coroutineLock.withLock {
//                            primaryBuffers.add(packet)
//
//                            if(keyCounter[key]  == packet.expected){
//                                val (fullyReceived, others) = primaryBuffers.partition { it.packetID == key }
//                                fullyReceived.sortedBy { it.offset }.forEach { info += it.payload.toByteArray() }
//                                decodeData(info,packet.messageType,iFace)
//                                primaryBuffers = others as MutableList<Packet>
//
//                            }
////                        }
//                        }
//                        else -> {
//                            myMessagesQueue.add(packet)
//
//                            if (keyCounter[key] == packet.expected) {
//                                val (fullyReceived, others) = myMessagesQueue.partition { it.packetID == key }
//                                myMessagesQueue = others as MutableList<Packet>
//
//                                fullyReceived.sortedBy { it.offset }.forEach {
//                                    info += it.payload.toByteArray()
//                                }
//                                decodeData(info, packet.messageType,iFace)
//                            }
//                        }
//                    }
//
//                }else{
//                    //pass the message up without enqueue
//                    info = packet.payload.toByteArray()
//
//                    decodeData(info,packet.messageType,iFace)
//
//                }
//            }
//
//        }

        }catch(e: NullPointerException) {
            Log.e(TAG,"Null pointer exception during decode: ${packet.packetID}")
        }

    }
    private fun checkForbidden(packet: Packet) : Boolean {
//        return if (forbiddenPackets.containsKey(packet.packetID)){
//            var forbiddenOffsets = forbiddenPackets[packet.packetID]?.first
//            if(forbiddenOffsets!!.contains(packet.offset)){
//                true
//            }else{
//                forbiddenOffsets = forbiddenOffsets + packet.offset
//
////                coroutineLock.withLock {
//                    forbiddenPackets[packet.packetID] = Pair(forbiddenOffsets,System.currentTimeMillis())
//
////                }
//                Log.d(TAG,"Forbidden Packets: $forbiddenPackets")
//
//                false
//            }
//        }else{
//
////            coroutineLock.withLock {
//                forbiddenPackets[packet.packetID] = Pair(listOf(packet.offset),System.currentTimeMillis())
////            }
//            Log.d(TAG,"Forbidden Packets: $forbiddenPackets")
//            false
//        }
        return false
    }
    private suspend fun sendHelloAck(receiver : String){
        //send all tables to the slave device
        val fileSeeders = repo.getAllFileSeeders()
        val fileNames = repo.getAllFileNames()
        val names = repo.getAllNames()
        val devices = repo.getAllDevices()
        try{
            devices.forEach {
                Log.e(TAG,"sending helloAck devices to : $receiver")
                it?.let {
                    val deviceSend = DeviceNode(
                        fromMeshID = device.meshID,
                        meshID = it.device.meshID,
                        multicastAddress = if (it.device.meshID == device.meshID) device.multicastAddress else {device.meshID},
                        connAddress = it.wifiConfig.connAddress,
                        Hops = it.device.hops,
                        macAddress = it.wifiConfig.mac,
                        publicKey = it.device.publicKey,
                        hasInternetWifi = it.device.hasInternetWifi,
                        wifi = it.wifiConfig.ssid,
                        passPhrase = it.wifiConfig.passPhrase,
                        version = it.device.version,
                        status = it.device.status,
                        modified = it.device.modified,
                        type = "meshHelloAck"
                    )
                    loadData(message = deviceSend, toMeshId = receiver)
                }
            }
        }catch (e: Throwable) {
            Log.e(TAG, "Something happened to the ack hello devices",e)
            e.printStackTrace()
        }
        try{
            fileSeeders.forEach {
                Log.e(TAG,"sending helloAck fileSeeders to : $receiver")
                val pFileSeeder = PFileSeeder(it.CID,it.meshID,it.status,device.meshID,type = "fileSeederHelloAck")
                loadData(pFileSeeder, toMeshId = receiver)
            }
        }catch (e: Throwable) {
            Log.e(TAG, "Something happened to the ack hello file seeder",e)
            e.printStackTrace()
        }
        try{
            fileNames.forEach {
                Log.e(TAG,"sending helloAck filenames to : $receiver")
                val pFileName = PFileName(it.CID,it.name_slub,it.status,device.meshID,it.modified,"fileNameHelloAck",it.file_size)
                loadData(pFileName, toMeshId = receiver)
            }
        }catch (e: Throwable) {
            Log.e(TAG, "Something happened to the ack hello filename ",e)
            e.printStackTrace()
        }
        try{
            names.forEach {
                Log.e(TAG,"sending helloAck names to : $receiver")
                val pName = PName(it.name, it.name_slub, device.meshID,it.status, type = "nameHelloAck")
                loadData(pName, toMeshId = receiver)
            }
        }catch (e: Throwable) {
            Log.e(TAG, "Something happened to the ack hello name",e)
            e.printStackTrace()
        }
    }
    private suspend fun decodeData(array : ByteArray , type : String, iFace: String){
        Log.d("decoded","DECODED TYPE: $type")
        when{
            type == "HELLO" -> {
                val command = String(array).split("@")
//                val command = withContext(Dispatchers.IO) {Command.ADAPTER.decode(array)}
                Log.e(TAG,"HELLO RECEIVED")
                //send all tables to the slave device
                val fileSeeders = repo.getAllFileSeeders()
                val fileNames = repo.getAllFileNames()
                val names = repo.getAllNames()
                val devices = repo.getAllDevices()
                try{
                    if(devices.isNotEmpty()) {
                        val sendDevices = devices.mapNotNull {
                            it?.let {
//                                DeviceNode(
//                                    fromMeshID = device.meshID,
//                                    meshID = it.device.meshID,
//                                    multicastAddress = if (it.device.meshID == device.meshID) device.multicastAddress else {
//                                        device.meshID
//                                    },
//                                    connAddress = it.wifiConfig.connAddress,
//                                    Hops = it.device.hops,
//                                    macAddress = it.wifiConfig.mac,
//                                    publicKey = it.device.publicKey,
//                                    hasInternetWifi = it.device.hasInternetWifi,
//                                    wifi = it.wifiConfig.ssid,
//                                    passPhrase = it.wifiConfig.passPhrase,
//                                    version = it.device.version,
//                                    status = it.device.status,
//                                    modified = it.device.modified,
//                                    type = "meshHelloAck"
//                                )
                                Gson().toJson(it)
                            }

                        }
                        loadData(message = ListDeviceNode(sendDevices,"meshHelloAckList"), toMeshId = command.last())

                    }
//                    devices.forEach {
//                        Log.e(TAG,"sending helloAck devices to : ${command.last()}")
//                        it?.let {
//                            val deviceSend = DeviceNode(
//                                fromMeshID = device.meshID,
//                                meshID = it.device.meshID,
//                                multicastAddress = if (it.device.meshID == device.meshID) device.multicastAddress else {device.meshID},
//                                connAddress = it.wifiConfig.connAddress,
//                                Hops = it.device.hops,
//                                macAddress = it.wifiConfig.mac,
//                                publicKey = it.device.publicKey,
//                                hasInternetWifi = it.device.hasInternetWifi,
//                                wifi = it.wifiConfig.ssid,
//                                passPhrase = it.wifiConfig.passPhrase,
//                                version = it.device.version,
//                                status = it.device.status,
//                                modified = it.device.modified,
//                                type = "meshHelloAck"
//                            )
//                            loadData(message = deviceSend, toMeshId = command.last())
//                        }
//                    }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the ack hello devices",e)
                    e.printStackTrace()
                }
                try{
                    if(fileSeeders.isNotEmpty()) {
                        val pFileSeeders =  fileSeeders.map {Gson().toJson(it)}
                        loadData(ListPFileSeeder(pFileSeeders,"fileSeederHelloAckList"), toMeshId = command.last())
                    }
//                    fileSeeders.forEach {
//                        Log.e(TAG,"sending helloAck fileSeeders to : ${command.last()}")
//                        val pFileSeeder = PFileSeeder(it.CID,it.meshID,it.status,device.meshID,type = "fileSeederHelloAck")
//                        loadData(pFileSeeder, toMeshId = command.last())
//                    }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the ack hello file seeder",e)
                    e.printStackTrace()
                }
                try{
                    if (fileNames.isNotEmpty()) {
                        val pFileNames = fileNames.map{Gson().toJson(it)}
                        loadData(ListPFileName(pFileNames,"fileNameHelloAckList"), toMeshId = command.last())
                    }
//                    fileNames.forEach {
//                        Log.e(TAG,"sending helloAck filenames to : ${command.last()}")
//                        val pFileName = PFileName(it.CID,it.name_slub,it.status,device.meshID,it.modified,"fileNameHelloAck",it.file_size)
//                        loadData(pFileName, toMeshId = command.last())
//                    }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the ack hello filename ",e)
                    e.printStackTrace()
                }
                try{
                    if(names.isNotEmpty()) {
                        val pNames = names.map{ Gson().toJson(it) }
                        loadData(ListPName(pNames,"nameHelloAckList"), toMeshId = command.last())
                    }
//                    names.forEach {
//                        Log.e(TAG,"sending helloAck names to : ${command.last()}")
//                        val pName = PName(it.name, it.name_slub, device.meshID,it.status, type = "nameHelloAck")
//                        loadData(pName, toMeshId = command.last())
//                    }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the ack hello name",e)
                    e.printStackTrace()
                }
            }
//            "COMMAND"  -> {
//                withContext(Dispatchers.IO){
//                    val command = Command.ADAPTER.decode(array)
//                    Log.d(TAG,"dataType : Command")
//                    //this just a bunch of string commands
//                    when(command.type) {
//                        "HELLO" -> {
//
//                        }
//                        "LEAVING" -> {
//
//                        }
//                    }
//                }
//            }
//            type == "ACK"   -> {
//                Log.d(TAG,"dataType : ack")
//                withContext(Dispatchers.IO){
//                    val ack = ACK.ADAPTER.decode(array)
//                    val cacheDir = mContext.externalCacheDir?.absolutePath
//                    if(!storage.isDirectoryExists("$cacheDir/Acks")){
//                        storage.createDirectory("$cacheDir/Acks")
//                    }
//                    storage.createFile("$cacheDir/Acks/${ack.ackID}.ch",ack.expectedOffset.joinToString())
//
//                    val sendWorker = OneTimeWorkRequestBuilder<SendFileWorker>()
//                        .setInputData(workDataOf("fileCID" to ack.ackID, "offsets" to ack.ackID, "fromMeshId" to ack.fromMeshID))
//                        .build()
//                    WorkManager.getInstance(mContext).enqueue(sendWorker)
//
//                }
//            }
            type.startsWith("ACK")  -> {
                withContext(Dispatchers.IO) {
                    val ack = ACK.ADAPTER.decode(array)
                    val packets = ack.expectedOffset
                    val sendPackets = senderBuffer[ack.ackID]

                    if(packets.isEmpty()) {
//                        clear sender buffer and seqTimer
                        senderBuffer.remove(ack.ackID)
                        seqTimerMap.remove(ack.ackID)

                    }else if(packets.size == 1) {
                        sendPackets?.let {
                            val packet = sendPackets.first { it.offset == packets.first() }
                            //delete the packets below that
                            senderBuffer[ack.ackID] = sendPackets.filter {it.offset >= packets.first()}
                            forward(packet,false)
                        }
                    }else{
                        sendPackets?.let {
                            val ackPackets = sendPackets.filter{ it.offset in packets}
                            ackPackets.forEach {
                                forward(it,false)
                            }
                        }
                    }
                }
            }

            type.startsWith("fileSeeder") && !type.endsWith("List") -> {
                Log.d(TAG,"RECEIVED FileSeeder")
                val pFileSeeder = withContext(Dispatchers.IO){PFileSeeder.ADAPTER.decode(array)}
                val fileSeeder = FileSeeder(pFileSeeder.cid,pFileSeeder.meshId,pFileSeeder.status,pFileSeeder.modified,pFileSeeder.modified_by)
                repo.updateFileSeeder(fileSeeder)
            }
            type.startsWith("name") && !type.endsWith("List") -> {
                Log.d(TAG,"RECEIVED NAME")
                val pName = withContext(Dispatchers.IO){PName.ADAPTER.decode(array)}
                val name = Name(pName.name_slub,pName.name,pName.status,pName.modified,pName.modified_by)

                repo.updateName(name)


            }
            type.startsWith("fileName") && !type.endsWith("List") -> {
                Log.d(TAG,"RECEIVED FILENAME")
                val pFileName = withContext(Dispatchers.IO){PFileName.ADAPTER.decode(array)}
                val fileName = FileName(pFileName.cid,pFileName.name_slub,pFileName.file_size,pFileName.status,pFileName.modified,pFileName.modified_by)
                repo.updateFileName(fileName)
            }
            type.startsWith("mesh") && !type.endsWith("List") -> {
                Log.d(TAG,"RECEIVED MESH")
                val deviceUpdate = withContext(Dispatchers.IO){DeviceNode.ADAPTER.decode(array)}
                val deviceObj = ug.hix.hixnet2.database.DeviceNode(
                        meshID = deviceUpdate.meshID,
                        multicastAddress = deviceUpdate.multicastAddress,
                        hops = deviceUpdate.Hops + 1,
                        publicKey = deviceUpdate.publicKey,
                        hasInternetWifi = deviceUpdate.hasInternetWifi,
                        iface = iFace,
                        status = deviceUpdate.status,
                        version = deviceUpdate.version,
                        modified = deviceUpdate.modified
                    )
                val wifiConfigObj = WifiConfig(
                        meshID = deviceUpdate.meshID,
                        mac = deviceUpdate.macAddress,
                        ssid = deviceUpdate.wifi,
                        passPhrase = deviceUpdate.passPhrase,
                        connAddress = deviceUpdate.connAddress
                    )
                repo.insertOrUpdateDeviceWithConfig(deviceObj,wifiConfigObj)

                //disable wifi if is hello and wifi is known to refuse reconnection
                if(type.endsWith("hello") && deviceObj.multicastAddress.startsWith("230.")){
                    val wifi = repo.getWifiConfigBySsid(deviceUpdate.meshID)
                    wifi?.let{
                        if(it.netId > 0){
                            //disable hello wifi
                            val status = mWifiManager.disableNetwork(it.netId)
                            repo.updateWifiStatusBySsid(deviceObj.meshID,status)
                        }
                    }
                }

            }
            type.startsWith("fileSeeder") && type.endsWith("List")-> {
                val pFileSeederList = withContext(Dispatchers.IO){ListPFileSeeder.ADAPTER.decode(array)}
                val fileSeeder = pFileSeederList.data.map{Gson().fromJson(it,FileSeeder::class.java)}
                Log.d(TAG,"Received file seeders: $fileSeeder")
                repo.updateFileSeeder(fileSeeder)
            }
            type.startsWith("name") && type.endsWith("List") -> {
                val pNameList = withContext(Dispatchers.IO){ListPName.ADAPTER.decode(array)}
                val name = pNameList.data.map{Gson().fromJson(it,Name::class.java)}
                Log.d(TAG,"Received Names: $name")
                repo.updateName(name)


            }
            type.startsWith("fileName") && type.endsWith("List") -> {
                val pFileNameList = withContext(Dispatchers.IO){ListPFileName.ADAPTER.decode(array)}
                val fileName = pFileNameList.data.map{Gson().fromJson(it,FileName::class.java)}
                Log.d(TAG,"Received File names: $fileName")
                repo.updateFileName(fileName)
            }
            type.startsWith("mesh") && type.endsWith("List") -> {
                Log.d(TAG,"RECEIVED MESH")
                val deviceUpdateList = withContext(Dispatchers.IO){ListDeviceNode.ADAPTER.decode(array)}
                val devWithWifi = deviceUpdateList.data.map{ Gson().fromJson(it,DeviceNodeWithWifiConfig::class.java) }
                val deviceObj = devWithWifi.map{
                    val device = it.device
                    ug.hix.hixnet2.database.DeviceNode(
                    meshID = device.meshID,
                    multicastAddress = device.multicastAddress,
                    hops = device.hops + 1,
                    publicKey = device.publicKey,
                    hasInternetWifi = device.hasInternetWifi,
                    iface = iFace,
                    status = device.status,
                    version = device.version,
                    modified = device.modified
                )}
                val wifiConfigObj = devWithWifi.map{
                    val wifi = it.wifiConfig
                    WifiConfig(
                    meshID = wifi.meshID,
                    mac = wifi.mac,
                    ssid = wifi.ssid,
                    passPhrase = wifi.passPhrase,
                    connAddress = wifi.connAddress
                )}
                Log.d(TAG,"Received devices: $deviceObj")
                repo.insertOrUpdateDeviceWithConfig(deviceObj,wifiConfigObj)
//
//                //disable wifi if is hello and wifi is known to refuse reconnection
//                if(type.endsWith("hello") && deviceObj.multicastAddress.startsWith("230.")){
//                    val wifi = repo.getWifiConfigBySsid(deviceUpdate.meshID)
//                    wifi?.let{
//                        if(it.netId > 0){
//                            //disable hello wifi
//                            val status = mWifiManager.disableNetwork(it.netId)
//                            repo.updateWifiStatusBySsid(deviceObj.meshID,status)
//                        }
//                    }
//                }

            }
        }

    }
    private suspend fun ackTimer(packet : Packet) = scopeDefault.launch {
        var count = 0
        while(isActive) {
            try {
                delay(ACK_TIMEOUT)
                if (count > 20) {
                    outOfSeqPackets.remove(packet.packetID)
                    receiverBuffer.remove(packet.packetID)
                    nextPacket.remove(packet.packetID)
                    break
                }
                /**
                 * After the delay, check for out seq packets
                 * and the unreceived packets
                 */
                val outOfSeqPacketList = outOfSeqPackets[packet.packetID]
                val mNextPacket  = nextPacket[packet.packetID]
                if(mNextPacket != null && !outOfSeqPacketList.isNullOrEmpty()){
                    outOfSeqPackets[packet.packetID] = outOfSeqPacketList.filter { offset ->
                        offset > mNextPacket }
                }

                if(outOfSeqPackets[packet.packetID].isNullOrEmpty()) break
                val unAckPackets = IntRange(nextPacket[packet.packetID]!!,packet.expected+1).filter { !outOfSeqPackets[packet.packetID]!!.contains(it) }
                val ack = ACK(packet.packetID,unAckPackets)
                loadData(ack, packet.fromMeshID)
                count += 1
            }catch (e: Exception){
                Log.e(TAG,"Something happened to the ackTimer",e)
            }

        }
    }
    private suspend fun seqTimer(packet: Packet, hops : Int) = scopeDefault.launch {
        var count = 0
        while(isActive) {
            try {
                delay(RESEND_TIMEOUT * hops)
                val packets = senderBuffer[packet.packetID]
                if(packets.isNullOrEmpty()) break
                else{
//                TODO("If count greater and is file u will find out")
                    if(count > 10) {
//                        deque packets and counter since counter is out
                        senderBuffer.remove(packet.packetID)
                        break
                    }
                    else{
                        count += 1
//                    retransmit all remaining packet
                        packets.forEach{
//                            do not enqueue the packets to sender buffer again
                            forward(it, false)
                        }

                    }
                }
            }catch (e: Exception) {
                Log.e(TAG,"Something happened to the seq timer",e)
            }

        }
    }
    @Synchronized private suspend fun send(packet : ByteArray?, ipAddress : String?, port : Int?, iface: String){
        withContext(Dispatchers.IO){
            Log.e(companionTAG,"SENDING DATA TO $ipAddress ON PORT $port VIA $iface")
            multicastLock.setReferenceCounted(false)
            if(!multicastLock.isHeld){
                multicastLock.acquire()
            }
            try{
                val receiver = InetAddress.getByName(ipAddress)
                val payload = DatagramPacket(packet,packet!!.size,receiver,port!!)
                if(iface == "wlan0"){
                    wlanSocket.networkInterface = wlan0
                    wlanSocket.send(payload)
                    Log.d(TAG, "Sending via wlan0")
                }else{
                    p2pSocket.networkInterface = p2p0
                    p2pSocket.send(payload)
                    Log.d(TAG,"Sending via p2p0")
                }

            }catch (e : Throwable){
                Log.e(companionTAG,"ERROR OCCURRED WHILE SENDING",e)
            }
        }

    }
    private fun listeners() = scopeDefault.launch {
        if(isActive) {
            //coroutine for new device and cloud file listeners
            launch {
                if(isActive) {
                    /*
                     * CLoud file listeners
                     * listen for name changes, seeders a
                     */
                    try {
                        repo.getUpdatedNameFlow().collect { name ->
                            name?.let {
                                Log.e(TAG,"sending update names to except: ${it.modified_by}")
                                val pName = PName(it.name, it.name_slub, device.meshID,it.status,it.modified,type="nameUpdate")
                                loadData(pName,it.modified_by)
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Something happened to the name listener",e)
                    }
                }

            }
            launch {
                if(isActive) {
                    try {
                        repo.getUpdatedFileNameFlow().collect { filename ->
                            filename?.let {
                                Log.e(TAG,"sending update file names to except: ${it.modified_by}")
                                val pFileName = PFileName(it.CID,it.name_slub,it.status,
                                    device.meshID,it.modified,"fileNameUpdate",it.file_size)
                                loadData(pFileName,it.modified_by)
                            }
                        }

                    } catch (e: Throwable) {
                        Log.e(TAG, "Something happened to the filename listener",e)
                    }
                }

            }
            launch {
//                fileSeeder listener
                if(isActive) {
                    try {
                        repo.getUpdatedFileSeederFlow().collect { fileSeeder ->
                            fileSeeder?.let {
                                Log.e(TAG,"sending update fileSeeders to except: ${it.modified_by}")
                                val pFileSeeder = PFileSeeder(it.CID,it.meshID,it.status, device.meshID,it.modified,type = "fileSeederUpdate")
                                loadData(pFileSeeder,it.modified_by)
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Something happened to the fileSeeder listener",e)
                    }
                }
            }
            launch {
//                new device listener
                if(isActive) {
                    try {
                        repo.getUpdatedDeviceFlow().collect {
                            it?.let{
                                Log.e(TAG,"sending update devices to except: ${it.device.meshID}")
                                val deviceSend = DeviceNode(
                                    fromMeshID = device.meshID,
                                    meshID = it.device.meshID,
                                    multicastAddress = if (it.device.meshID == device.meshID) device.multicastAddress else {device.meshID},
                                    Hops = it.device.hops,
                                    macAddress = it.wifiConfig.mac,
                                    publicKey = it.device.publicKey,
                                    hasInternetWifi = it.device.hasInternetWifi,
                                    wifi = it.wifiConfig.ssid,
                                    passPhrase = it.wifiConfig.passPhrase,
                                    version = it.device.version,
                                    status = it.device.status,
                                    connAddress = it.wifiConfig.connAddress,
                                    modified = it.device.modified,
                                    type = "meshUpdate"
                                )
                                Log.d(TAG, "New device detected: $it")
                                loadData(message = deviceSend, toMeshId = it.device.meshID)
                                if (it.device.status == "DISCONNECTED") repo.deleteDevice(it.device)

                            }

                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Something happened to the device listener",e)
                    }
                }

            }
        }
    }
//    @Suppress("UNCHECKED_CAST")
//    private fun <T> runNonBlocking(block: suspend CoroutineScope.() -> T) : T {
//        var finished = false
//        var result : T? = null
//        GlobalScope.launch {
//            result = block()
//            finished = true
//        }
//        while(!finished);
//        return result as T
//    }

    companion object {
        private lateinit var mWifiManager: WifiManager
        lateinit var multicastLock: WifiManager.MulticastLock
        @SuppressLint("StaticFieldLeak")
        private var instance: Licklider? = null
        var receiverJob: Job? = null //keep reference to the receiver job for easy destroying
        lateinit var listenerJob: Job //keep reference to the listeners for later cleaning

        private val companionTAG = "CompanionLicklider"

        fun start(context: Context, repo : Repository) : Licklider {
            if(instance == null ){
                mWifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = mWifiManager.createMulticastLock("multicastLock")
                instance = Licklider(context, repo )
            }
            return instance as Licklider
        }
        fun stop() {
            listenerJob.cancel()
        }

    }
}


