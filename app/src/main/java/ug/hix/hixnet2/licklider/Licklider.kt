package ug.hix.hixnet2.licklider

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.work.*
import com.snatik.storage.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.database.FileName
import ug.hix.hixnet2.database.FileSeeder
import ug.hix.hixnet2.database.Name
import ug.hix.hixnet2.database.WifiConfig
import ug.hix.hixnet2.models.*
import ug.hix.hixnet2.database.File as DFile
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.services.MeshDaemon
import ug.hix.hixnet2.workers.MergeFileWorker
import ug.hix.hixnet2.workers.SendFileWorker
import java.io.File
import java.io.RandomAccessFile
import java.net.*
import kotlin.properties.Delegates

class Licklider(private val mContext: Context){

    private val TAG = javaClass.simpleName
    private var group : InetAddress? = null
    private val storage = Storage(mContext)
    private val repo = Repository.getInstance(mContext)
    private val coroutineLock = Mutex()
    private var keyCounter = mutableMapOf<String,Int>()
    private val ackTimerMap = mutableMapOf<String,Job>()
    private val seqTimerMap = mutableMapOf<String, Job>()
    private val nextPacket = mutableMapOf<String,Int>()
    private val outOfSeqPackets = mutableMapOf<String,List<Int>>()
    private val receiverBuffer = mutableMapOf<String,ByteArray>()
    private val senderBuffer = mutableMapOf<String, List<Packet>>()
    private val senderCounter = mutableMapOf<String, Int>()

    private val RESEND_TIMEOUT = 600L
    private val ACK_TIMEOUT = 300L

    private var myMessagesQueue = mutableListOf<Packet>()

    init {
//        TODO("Initial data setup like mesh and device")
    }

    suspend fun loadData(message: Any, toMeshId: String = ""){
        var isFile = false
        var packet = Packet()
        lateinit var buffer: ByteArray
        packet = packet.copy(packetID = Generator.genMID(),originalFromMeshID = Generator.getPID(),toMeshID = toMeshId)
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
                packet = packet.copy(messageType = "ACK",port = PORT)
                buffer = ACK.ADAPTER.encode(message)

            }
            is FileHashMap -> {
                packet = packet.copy(messageType = "filesUpdate",port = PORT)
                buffer = FileHashMap.ADAPTER.encode(message)
            }
//            is PFileSeeder -> {
//                packet = packet.copy(messageType = message.type,port = PORT)
//                buffer = PFileSeeder.ADAPTER.encode(message)
//            }
//            is PName -> {
//                packet = packet.copy(messageType = message.type,port = PORT)
//                buffer = PName.ADAPTER.encode(message)
//            }
//            is PFileName -> {
//                packet = packet.copy(messageType = message.type,port = PORT)
//                buffer = PFileName.ADAPTER.encode(message)
//            }
//            is DeviceNode -> {
//                packet = packet.copy(messageType = message.type,port = PORT)
//                buffer  = DeviceNode.ADAPTER.encode(message)
//            }
            is ListPFileSeeder -> {
                packet = packet.copy(messageType = message.type,port = PORT)
                buffer = ListPFileSeeder.ADAPTER.encode(message)
            }
            is ListPName -> {
                packet = packet.copy(messageType = message.type,port = PORT)
                buffer = ListPName.ADAPTER.encode(message)
            }
            is ListPFileName -> {
                packet = packet.copy(messageType = message.type,port = PORT)
                buffer = ListPFileName.ADAPTER.encode(message)
            }
            is ListDeviceNode -> {
                packet = packet.copy(messageType = message.type,port = PORT)
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
    suspend fun loadData(mFile : DFile, offsets: List<Int>,toMeshId: String){
        var packet = Packet()
        val file = File(mFile.path!!)
        packet = packet.copy(messageType = "FILE",port = PORT, packetID = mFile.CID,originalFromMeshID = Generator.getPID(),toMeshID = toMeshId)
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
                        if(runtime.freeMemory() >= 18000) {
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
                            Log.d("runningOut", " I AM ALMOST DONE HERE")
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

    @ExperimentalCoroutinesApi
    suspend fun receiver(multicastAddress : String) = GlobalScope.launch{
        if(!multicastLock.isHeld){
            multicastLock.acquire()
        }
        if(group != null){
            withContext(Dispatchers.IO){
                p2pSocket.leaveGroup(sockAddress,p2p0)
                wlanSocket.leaveGroup(sockAddress,wlan0)
//                if (receiverJob.isActive)
//                    receiverJob.cancelChildren()
                while(!p2pSocket.isClosed && !wlanSocket.isClosed) {
                    p2pSocket.close()
                    wlanSocket.close()
                }
                while(p2pSocket.isClosed && wlanSocket.isClosed) {
                    p2pSocket = MulticastSocket(33456)
                    wlanSocket = MulticastSocket(33456)
                }
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
                    p2pSocket.receive(packet)
                    Log.d("p2p0receiver","p2p0 data received")
                    GlobalScope.launch{
                        packetHandler(packet.data, "p2p0")
                    }

                }
            }

            launch(Dispatchers.IO){
                wlanSocket.joinGroup(sockAddress,wlan0)
                while(true){
                    val rBuffer = ByteArray(1500)
                    val packet = DatagramPacket(rBuffer, rBuffer.size)
                    wlanSocket.receive(packet)
                    Log.d("wlan0receiver","wlan0 data received")
                    GlobalScope.launch{
                        packetHandler(packet.data,"wlan0")
                    }
                }
            }
            launch{
                while(true){
                    if(queueBuffer.isNotEmpty()){
                        queueBuffer.forEach { packet ->
                            coroutineLock.withLock {
                                queueBuffer.remove(packet)
                            }
                            forward(packet)
                            Log.d(TAG,"removed packet: $packet remaining buffer: $queueBuffer")
                        }
                    }
                }
            }
//            ack sender
            launch{
                val filesMap = mutableMapOf<String,Int>()
                val path = withContext(Dispatchers.IO){mContext.getExternalFilesDir(null)?.absolutePath + "/Chunks"}
                while (true) {
                    if(withContext(Dispatchers.IO){storage.isDirectoryExists(path)}) {
                        val directoryList = withContext(Dispatchers.IO) {
                            storage.getFiles(path).map { it.absolutePath }
                        }
                        filesMap.keys.forEach { key ->
                            if (key !in directoryList) filesMap.remove(key)
                        }
                        if (directoryList.isNotEmpty()) {

                            directoryList.forEach { directory ->
                                val fileCount = withContext(Dispatchers.IO) {
                                    storage.getFiles(directory).count()
                                }
                                if (filesMap.keys.contains(directory)) {
                                    if (filesMap[directory] == fileCount) {
//                                        TODO("Listen for merge worker if running stop and delete cache else proceed")
                                        val packetAckId = directory.split("/").last().drop(1)
                                        val workManager = WorkManager.getInstance(mContext)
                                        val workInfos = workManager.getWorkInfosByTag(packetAckId).await()
                                        if(workInfos.isNotEmpty()){
                                            val workInfo = workInfos.first()
                                            if(workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.BLOCKED){
//                                                DO NOTHING SINCE IS RUNNING
                                                Log.d(TAG,"merge worker tag => $packetAckId  is running")
                                            }else{
                                                Log.d(TAG,"Merger worker dead maybe")
                                                sendAck(storage,directory,packetAckId)
                                            }
                                        }else{
                                            Log.d(TAG,"no info available about worker")
                                            sendAck(storage,directory,packetAckId)
                                        }
                                    } else
                                        filesMap[directory] = fileCount
                                } else
                                    filesMap[directory] = fileCount
                            }
                        }
                    }
                    delay(2000L)
                }
            }
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
                fromMeshID = device.meshID
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
                fromMeshID = device.meshID
            )
            loadData(message = packetAck, toMeshId = device.meshID)
        }
    }

    private suspend fun forward(mPacket: Packet, reliable : Boolean = true){
        var packet = mPacket
        val link = getLink(packet.toMeshID,packet.messageType)
        val data = Packet.ADAPTER.encode(packet)
        val port = packet.port
        //check if primary mesh buffers are empty
//        if(primaryBuffers.isEmpty()) {
//        if(receiverBuffer.isEmpty()) {
//            if(link.first().first != "notAvailable"){
//                link.forEach { address ->
//                    packet = packet.copy(fromMeshID = Generator.getPID(),timeToLive = address.third*2)
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
        when (packet.messageType) {
            "ACK" -> {
                //DO NOTHING
            }
            "FILE" -> {
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
                        senderCounter[packet.packetID] = 0
                        seqTimerMap[packet.packetID] = seqTimer(packet, 1)
                    }
                }
                //send the packet
                if(link.first().first != "notAvailable") {
                    link.forEach { address ->
                        packet = packet.copy(fromMeshID = Generator.getPID(), timeToLive = address.third * 2)
                        send(data, address.first, port, address.second)
                    }
                }
            }
        }
    }

    private suspend fun getLink(meshId : String, packetType : String) : List<Triple<String,String,Int>> {
        val repo = Repository.getInstance(mContext)
        return when {
            packetType.endsWith("update",true) -> {
                repo.getNearLinks(meshId) //get all neighbour links except parent of meshId
//            repo.getNearLinks()  //TEST get all the multiAddress with digits
            }
            packetType == "ACK" -> {
                //                TODO("get address nearest with file")
//                this in a test will be deleted
                listOf(repo.getLink(meshId))
            }
            packetType.endsWith("helloAck",true) -> {
                listOf(Triple(meshId,"p2p0",1))
            }
            packetType.endsWith("hello",true) -> {
                listOf(Triple(meshId, "wlan0", 1))
            } else -> {
                listOf(repo.getLink(meshId))
            }
        }

    }
    private suspend fun packetHandler(buffer : ByteArray, iFace: String){
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
            packet = withContext(Dispatchers.IO){
                Packet.ADAPTER.decode(filteredBuffer)
            }
            //we don't handle packet from our own node incase of broadcast conflict
            if(packet.fromMeshID != Generator.getPID()){
                if(packet.toMeshID in listOf(device.meshID,Generator.getPID(),device.multicastAddress)){
    //                coroutineLock.withLock {
                        dataMapping(packet,iFace)
    //                }

                }else{
                    forward(packet)
                }
            }

        }catch(e: Exception){
            Log.e(TAG,"fAILED TO HANDLE",e)
        }

    }

    private suspend fun  dataMapping(packet : Packet, iFace: String){
        if(packet.messageType == "FILE"){
            withContext(Dispatchers.IO){
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
        }
        else{
            if(receiverBuffer.containsKey(packet.packetID)){
                if(packet.offset < nextPacket[packet.packetID]!!){
                    //drop the packet and send ack for next expected packet
                    val ack = ACK(packet.packetID,listOf(nextPacket[packet.packetID]!!))
                    loadData(ack,packet.fromMeshID)
                }else{
                    val buffer = receiverBuffer[packet.packetID]!!
                    if(packet.offset > nextPacket[packet.packetID]!!) {
                        val outOfSeqList = outOfSeqPackets[packet.packetID] as MutableList<Int>
                        outOfSeqPackets[packet.packetID] = outOfSeqList.plus(packet.offset)
                        //drop the packet and send ack for next expected  packet
                        val ack = ACK(packet.packetID,listOf(nextPacket[packet.packetID]!!))
                        loadData(ack,packet.fromMeshID)
                    }else{
//                        increment expected to next and must not be in outseq
                        var nextIndex = packet.offset
                        while(true) {
                            nextIndex++
                            if(!outOfSeqPackets[packet.packetID]!!.contains(nextIndex)) break
                        }
                        nextPacket[packet.packetID] = packet.offset + 1
                    }
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
                ackTimerMap[packet.packetID] = ackTimer(packet)
            }

            if(nextPacket[packet.packetID]!! > packet.expected){
                val data = receiverBuffer[packet.packetID]!!.dropLastWhile { it.toString() == "0" }.toByteArray()
//                 HOUSE CLEANING
                receiverBuffer.remove(packet.packetID)
                nextPacket.remove(packet.packetID)
                outOfSeqPackets.remove(packet.packetID)
                decodeData(data,packet.messageType,iFace)

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
        val repo = Repository.getInstance(mContext)
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
                        fromMeshID = MeshDaemon.device.meshID,
                        meshID = it.device.meshID,
                        multicastAddress = if (it.device.meshID == MeshDaemon.device.meshID) MeshDaemon.device.multicastAddress else {MeshDaemon.device.meshID},
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
                val pFileSeeder = PFileSeeder(it.CID,it.meshID,it.status,MeshDaemon.device.meshID,type = "fileSeederHelloAck")
                loadData(pFileSeeder, toMeshId = receiver)
            }
        }catch (e: Throwable) {
            Log.e(TAG, "Something happened to the ack hello file seeder",e)
            e.printStackTrace()
        }
        try{
            fileNames.forEach {
                Log.e(TAG,"sending helloAck filenames to : $receiver")
                val pFileName = PFileName(it.CID,it.name_slub,it.status,MeshDaemon.device.meshID,it.modified,"fileNameHelloAck",it.file_size)
                loadData(pFileName, toMeshId = receiver)
            }
        }catch (e: Throwable) {
            Log.e(TAG, "Something happened to the ack hello filename ",e)
            e.printStackTrace()
        }
        try{
            names.forEach {
                Log.e(TAG,"sending helloAck names to : $receiver")
                val pName = PName(it.name, it.name_slub, MeshDaemon.device.meshID,it.status, type = "nameHelloAck")
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
                val repo = Repository.getInstance(mContext)
                //send all tables to the slave device
                val fileSeeders = repo.getAllFileSeeders()
                val fileNames = repo.getAllFileNames()
                val names = repo.getAllNames()
                val devices = repo.getAllDevices()
                try{
                    devices.forEach {
                        Log.e(TAG,"sending helloAck devices to : ${command.last()}")
                        it?.let {
                            val deviceSend = DeviceNode(
                                fromMeshID = MeshDaemon.device.meshID,
                                meshID = it.device.meshID,
                                multicastAddress = if (it.device.meshID == MeshDaemon.device.meshID) MeshDaemon.device.multicastAddress else {MeshDaemon.device.meshID},
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
                            loadData(message = deviceSend, toMeshId = command.last())
                        }
                    }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the ack hello devices",e)
                    e.printStackTrace()
                }
                try{
                    fileSeeders.forEach {
                        Log.e(TAG,"sending helloAck fileSeeders to : ${command.last()}")
                        val pFileSeeder = PFileSeeder(it.CID,it.meshID,it.status,MeshDaemon.device.meshID,type = "fileSeederHelloAck")
                        loadData(pFileSeeder, toMeshId = command.last())
                    }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the ack hello file seeder",e)
                    e.printStackTrace()
                }
                try{
                    fileNames.forEach {
                        Log.e(TAG,"sending helloAck filenames to : ${command.last()}")
                        val pFileName = PFileName(it.CID,it.name_slub,it.status,MeshDaemon.device.meshID,it.modified,"fileNameHelloAck",it.file_size)
                        loadData(pFileName, toMeshId = command.last())
                    }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the ack hello filename ",e)
                    e.printStackTrace()
                }
                try{
                    names.forEach {
                        Log.e(TAG,"sending helloAck names to : ${command.last()}")
                        val pName = PName(it.name, it.name_slub, MeshDaemon.device.meshID,it.status, type = "nameHelloAck")
                        loadData(pName, toMeshId = command.last())
                    }
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
            type == "ACK"  -> {
                withContext(Dispatchers.IO) {
                    val ack = ACK.ADAPTER.decode(array)
                    val packets = ack.expectedOffset
                    val sendPackets = senderBuffer[ack.ackID]

                    if(packets.isEmpty()) {
                        senderBuffer.remove(ack.ackID)

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

//            type.startsWith("fileSeeder") -> {
//                Log.d(TAG,"RECEIVED FileSeeder")
//                val pFileSeeder = withContext(Dispatchers.IO){PFileSeeder.ADAPTER.decode(array)}
//                val fileSeeder = FileSeeder(pFileSeeder.cid,pFileSeeder.meshId,pFileSeeder.status,pFileSeeder.modified,pFileSeeder.modified_by)
//                val repo = Repository.getInstance(mContext)
//                repo.updateFileSeeder(fileSeeder)
//            }
//            type.startsWith("name") -> {
//                Log.d(TAG,"RECEIVED NAME")
//                val pName = withContext(Dispatchers.IO){PName.ADAPTER.decode(array)}
//                val name = Name(pName.name_slub,pName.name,pName.status,pName.modified,pName.modified_by)
//
//                repo.updateName(name)
//
//
//            }
//            type.startsWith("fileName") -> {
//                Log.d(TAG,"RECEIVED FILENAME")
//                val pFileName = withContext(Dispatchers.IO){PFileName.ADAPTER.decode(array)}
//                val fileName = FileName(pFileName.cid,pFileName.name_slub,pFileName.file_size,pFileName.status,pFileName.modified,pFileName.modified_by)
//                val repo = Repository.getInstance(mContext)
//                repo.updateFileName(fileName)
//            }
//            type.startsWith("mesh") -> {
//                Log.d(TAG,"RECEIVED MESH")
//                val deviceUpdate = withContext(Dispatchers.IO){DeviceNode.ADAPTER.decode(array)}
//                val deviceObj = ug.hix.hixnet2.database.DeviceNode(
//                        meshID = deviceUpdate.meshID,
//                        multicastAddress = deviceUpdate.multicastAddress,
//                        hops = deviceUpdate.Hops + 1,
//                        publicKey = deviceUpdate.publicKey,
//                        hasInternetWifi = deviceUpdate.hasInternetWifi,
//                        iface = iFace,
//                        status = deviceUpdate.status,
//                        version = deviceUpdate.version,
//                        modified = deviceUpdate.modified
//                    )
//                val wifiConfigObj = WifiConfig(
//                        meshID = deviceUpdate.meshID,
//                        mac = deviceUpdate.macAddress,
//                        ssid = deviceUpdate.wifi,
//                        passPhrase = deviceUpdate.passPhrase,
//                        connAddress = deviceUpdate.connAddress
//                    )
//                repo.insertOrUpdateDeviceWithConfig(deviceObj,wifiConfigObj)
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
//
//            }
            type.startsWith("fileSeeder") -> {
                val pFileSeederList = withContext(Dispatchers.IO){ListPFileSeeder.ADAPTER.decode(array)}
                val fileSeeder = pFileSeederList.data.map{FileSeeder(it.cid,it.meshId,it.status,it.modified,it.modified_by)}
                Log.d(TAG,"Received file seeders: $fileSeeder")
//                val repo = Repository.getInstance(mContext)
//                repo.updateFileSeeder(fileSeeder)
            }
            type.startsWith("name") -> {
                val pNameList = withContext(Dispatchers.IO){ListPName.ADAPTER.decode(array)}
                val name = pNameList.data.map{Name(it.name_slub,it.name,it.status,it.modified,it.modified_by)}
                Log.d(TAG,"Received Names: $name")

//                repo.updateName(name)


            }
            type.startsWith("fileName") -> {
                val pFileNameList = withContext(Dispatchers.IO){ListPFileName.ADAPTER.decode(array)}
                val fileName = pFileNameList.data.map{FileName(it.cid,it.name_slub,it.file_size,it.status,it.modified,it.modified_by)}
                Log.d(TAG,"REceived File names: $fileName")
//                val repo = Repository.getInstance(mContext)
//                repo.updateFileName(fileName)
            }
            type.startsWith("mesh") -> {
                Log.d(TAG,"RECEIVED MESH")
                val deviceUpdateList = withContext(Dispatchers.IO){ListDeviceNode.ADAPTER.decode(array)}
                val deviceObj = deviceUpdateList.data.map{ug.hix.hixnet2.database.DeviceNode(
                    meshID = it.meshID,
                    multicastAddress = it.multicastAddress,
                    hops = it.Hops + 1,
                    publicKey = it.publicKey,
                    hasInternetWifi = it.hasInternetWifi,
                    iface = iFace,
                    status = it.status,
                    version = it.version,
                    modified = it.modified
                )}
                val wifiConfigObj = deviceUpdateList.data.map{WifiConfig(
                    meshID = it.meshID,
                    mac = it.macAddress,
                    ssid = it.wifi,
                    passPhrase = it.passPhrase,
                    connAddress = it.connAddress
                )}
                Log.d(TAG,"Received devices: $deviceObj")
//                repo.insertOrUpdateDeviceWithConfig(deviceObj,wifiConfigObj)
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
    private suspend fun ackTimer(packet : Packet) = GlobalScope.launch {
        while(isActive) {
            delay(ACK_TIMEOUT)
            /**
             * After the delay, check for outseq packets
             * and the unreceived packets
             */
            if(outOfSeqPackets[packet.packetID].isNullOrEmpty() && nextPacket[packet.packetID]!! > packet.expected) break
            val unAckPackets = IntRange(nextPacket[packet.packetID]!!,packet.expected+1).filter { !outOfSeqPackets[packet.packetID]!!.contains(it) }
            val ack = ACK(packet.packetID,unAckPackets)
            loadData(ack, packet.fromMeshID)
        }
        this.cancel()
    }
    private suspend fun seqTimer(packet: Packet, hops : Int) = GlobalScope.launch {
        while(isActive) {
            delay(RESEND_TIMEOUT * hops)
            val packets = senderBuffer[packet.packetID]
            if(packets.isNullOrEmpty()) break
            else{
                val count = senderCounter[packet.packetID]!!
                if(count > 10) break
                else{
                    senderCounter[packet.packetID] = count + 1
//                    retransmit all the packet
                    packets.forEach{
                        forward(it)
                    }

                }
            }

        }
    }

    companion object {
        private lateinit var mWifiManager: WifiManager
        lateinit var multicastLock: WifiManager.MulticastLock
        @SuppressLint("StaticFieldLeak")
        private var instance: Licklider? = null
        val device = MeshDaemon.device

        private val companionTAG = "CompanionLicklider"
        private const val PORT = 33456
        private var p2pSocket = MulticastSocket(33456)
        private var wlanSocket = MulticastSocket(33456)
        private val p2p0 = NetworkInterface.getByName("p2p0")
        private val wlan0 = NetworkInterface.getByName("wlan0")
        lateinit var sockAddress : InetSocketAddress

        lateinit var receiverJob: Job //keep reference to the receiver job for easy destroying

        @JvmStatic private var primaryBuffers = mutableListOf<Packet>()
        @JvmStatic  private var queueBuffer = mutableListOf<Packet>()

        fun start(context: Context) : Licklider {
            if(instance == null ){
                mWifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = mWifiManager.createMulticastLock("multicastLock")
                instance = Licklider(context)
            }
            return instance as Licklider
        }
        @JvmStatic
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
                    }else{
                        p2pSocket.networkInterface = p2p0
                        p2pSocket.send(payload)
                    }

                }catch (e : Exception){
                    Log.e(companionTAG,"ERROR OCCURRED WHILE SENDING",e)
                }
            }

        }

    }
}


