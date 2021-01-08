package ug.hix.hixnet2.licklider

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.snatik.storage.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.database.File as DFile
import ug.hix.hixnet2.meshlink.UpdateDevice
import ug.hix.hixnet2.models.*
import ug.hix.hixnet2.services.MeshDaemon
import ug.hix.hixnet2.workers.MergeFileWorker
import ug.hix.hixnet2.workers.SendFileWorker
import java.io.File
import java.net.*
import kotlin.properties.Delegates

class Licklider(private val mContext: Context){

    private val TAG = javaClass.simpleName
    private var group : InetAddress? = null
    private val storage = Storage(mContext)

    private val coroutineLock = Mutex()
    lateinit var dbDevice : ug.hix.hixnet2.database.DeviceNode
    var keyCounter = mutableMapOf<String, Int>()
    private val updateFun = UpdateDevice()

    private var myMessagesQueue = mutableListOf<Packet>()


    fun loadData(message: Any, toMeshId: String = "All"){
        var isFile = false
        var packet = Packet()
        lateinit var buffer: ByteArray
        packet = packet.copy(packetID = Generator.genMID(),originalFromMeshID = Generator.getPID(),toMeshID = toMeshId)

        when (message) {
            is String -> {
                packet = packet.copy(messageType = "COMMAND",port = 45345)
                buffer  = message.toByteArray()

            }
            is ACK -> {
                packet = packet.copy(messageType = "ACK",port = PORT)
                buffer = ACK.ADAPTER.encode(message)

            }
            is FileHashMap -> {
                packet = packet.copy(messageType = "filesUpdate",port = PORT)
                buffer = FileHashMap.ADAPTER.encode(message)
            }
            is DeviceNode -> {
                packet = packet.copy(messageType = "meshUpdate",port = PORT)
                buffer  = DeviceNode.ADAPTER.encode(message)

            }
            is TransFile  -> {
                packet = packet.copy(messageType = "FILE",port = PORT)
                buffer = TransFile.ADAPTER.encode(message)

            }
            is DFile -> {
                isFile = true
                val file = File(message.path)
                packet = packet.copy(messageType = "FILE",port = PORT, packetID = message.CID)
                splitter(mPacket = packet, file = file)
            }
        }
        if(!isFile){
            splitter(packet,buffer)

        }
    }
    fun loadData(mFile : DFile, offsets: List<Int>){
        var packet = Packet()
        val file = File(mFile.path)
        packet = packet.copy(messageType = "FILE",port = PORT, packetID = mFile.CID,originalFromMeshID = Generator.getPID())
        splitter(mPacket = packet, file = file, offsets = offsets)

    }

    private fun splitter(mPacket : Packet, buffer: ByteArray? = null, file: File? = null, offsets: List<Int>? = null){
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
                val fileStream = file.inputStream().buffered(1024 * 1024)
                var readBuffer = ByteArray(1200)
                if(offsets != null){
                    if(offsets.contains(-1))
                        packet = packet.copy(offset = -1, payload = "${file.name}:::$blockCount".toByteArray().toByteString())
                }else
                    packet = packet.copy(offset = -1, payload = "${file.name}:::$blockCount".toByteArray().toByteString())

                runBlocking {
                    forward(packet)
                }
                while(true){
                    if(runtime.freeMemory() >= 18000) {
                        val remBytes = fileStream.available()
                        if( remBytes == 0) break
                        if(remBytes < 1200){
                            readBuffer = ByteArray(remBytes.rem(1200))
                        }
                        fileStream.read(readBuffer,0,readBuffer.size)
                        if(offsets != null){
                            if( i in offsets){
                                packet = packet.copy(offset = i,payload = readBuffer.toByteString())
                                runBlocking{
                                    forward(packet)
                                }
                            }
                        }else{
                            packet = packet.copy(offset = i,payload = readBuffer.toByteString())
                            runBlocking{
                                forward(packet)
                            }
                        }
                        i++
                    }else{
                        Log.d("runningOut", " I AM ALMOST DONE HERE")
                    }

                }
                fileStream.close()

            }else{
                var i = 1

                while(i < blockCount){
                    val idx = (i - 1) * blockSize
                    val range = buffer!!.copyOfRange(idx, idx + blockSize)

                    packet = packet.copy(payload = range.toByteString(),offset = i)

                    runBlocking{
                        forward(packet)
                    }

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

                runBlocking{
                    forward(packet)
                }
            }

    }

    fun receiver(multicastAddress : String){
        if(!multicastLock.isHeld){
            multicastLock.acquire()
        }

        if(group != null){
            endSocket()
        }

        group = InetAddress.getByName(multicastAddress)

        GlobalScope.launch{

            launch{
                withContext(Dispatchers.IO){
                    p2pSocket.joinGroup(InetSocketAddress(group,PORT),p2p0)
                }

                while(true){
                    val rBuffer = ByteArray(1500)
                    val packet = DatagramPacket(rBuffer, rBuffer.size)
                    withContext(Dispatchers.IO){
                        p2pSocket.receive(packet)
                    }
                    launch{
                        packetHandler(packet.data)
                    }

                }
            }
            launch{
                withContext(Dispatchers.IO){
                    wlanSocket.joinGroup(InetSocketAddress(group,PORT),wlan0)
                }
                while(true){
                    val rBuffer = ByteArray(1500)
                    val packet = DatagramPacket(rBuffer, rBuffer.size)
                    withContext(Dispatchers.IO){
                        wlanSocket.receive(packet)
                    }
                    launch{
                        packetHandler(packet.data)
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
                val path = mContext.getExternalFilesDir(null)?.absolutePath + "/Chunks"
                while (true){
                    lateinit var directoryList : List<String>
                    withContext(Dispatchers.IO){
                        directoryList = storage.getFiles(path).map{ it.absolutePath}
                    }
                    Log.d(TAG,"DIRECTORYlIST: $directoryList filesMao: $filesMap")
                    filesMap.keys.forEach { key ->
                        if(key !in directoryList) filesMap.remove(key)
                    }
                    if(directoryList.isNotEmpty()){
                        var fileCount by Delegates.notNull<Int>()
                        directoryList.forEach {directory ->
                            withContext(Dispatchers.IO){
                                fileCount = storage.getFiles(directory).count()
                            }
                            if(filesMap.keys.contains(directory)){
                                if(filesMap[directory] == fileCount){
                                    var packetAck = ACK()
                                    val packetAckId = directory.drop(1)
                                    lateinit var files : MutableList<String>
                                    withContext(Dispatchers.IO){
                                        files = storage.getFiles(directory).map { it.absolutePath } as MutableList
                                    }
                                    Log.d(TAG, "files: $files")
                                    if ("$directory/fileName.txt" !in files){
                                        packetAck = packetAck.copy(ackID = packetAckId,expectedOffset = listOf(-1), fromMeshID = device.meshID)
                                        loadData(message = packetAck)
                                        TODO("reorder the filename as this gives as the expected number")
                                    }else{
                                        var expected by Delegates.notNull<Int>()
                                        files.remove("$directory/fileName.txt")
                                        withContext(Dispatchers.IO){
                                            expected = storage.readTextFile("$directory/fileName.txt").split(":::").last().toInt()

                                        }
                                        val missingPackets = (1..expected).apply {
                                            this.dropWhile { value->
                                                value in files.map{it.split(".").first().toInt()}
                                            }
                                        }
                                        Log.d(TAG, "missing packets ${missingPackets.toString()}")
                                        packetAck = packetAck.copy(ackID = packetAckId,expectedOffset = missingPackets.toList(), fromMeshID = device.meshID)
                                        loadData(message = packetAck)
                                    }
                                }else
                                    filesMap[directory] = fileCount
                            }else
                                filesMap[directory] = fileCount
                        }
                    }
                    delay(3000L)
                }
            }
        }

    }

    private fun endSocket(){
        p2pSocket.leaveGroup(InetSocketAddress(group,PORT),p2p0)
        wlanSocket.leaveGroup(InetSocketAddress(group,PORT),wlan0)
    }

    private suspend fun forward(mPacket: Packet){
        var packet = mPacket
        val link = if(packet.toMeshID.contains(".")){
            packet.toMeshID
        }else{
            getLink(packet.toMeshID,packet.messageType)
        }
        packet = packet.copy(fromMeshID = Generator.getPID(),timeToLive = 4)
        val data = Packet.ADAPTER.encode(packet)
        val port = packet.port

        //check if primary mesh buffers are empty
        if(primaryBuffers.isEmpty()) {
            if(!link.equals("notAvailable")){
                if (link != null) {
                    if(link.contains(",")){
                        link.split(",").forEach { address ->
                            send(data,address,port)
                        }

                    }else{
                        send(data, link, port)
                    }
                }
            }else{
                coroutineLock.withLock {
                    queueBuffer.add(packet)
                }

            }
        }else{
            coroutineLock.withLock {
                queueBuffer.add(packet)
            }
        }
    }

    private fun getLink(meshId : String, packetType : String) : String? {
        return if (meshId == "All"){
            val filteredList = device.peers.filter { it.multicastAddress.contains(".") }.toString()
            filteredList
        }else{
            if(packetType == "ACK"){
                    TODO("get address nearest with file")
            }else{
                //step 1 get all references of the meshId
                val filteredList = device.peers.filter { it.meshID == meshId }

                //step2 get the minimal hops and then the ipAddress
                val minDevice = filteredList.minBy { it.Hops }
                val minAddress = minDevice?.multicastAddress

                //step3 determine if the address is real
                if (minAddress != null) {
                    if(!minAddress.contains(".")){
                        device.peers.find { it.meshID == minAddress && it.multicastAddress.contains(".") }?.multicastAddress
                    }else{
                        minAddress
                    }
                }else{
                    "notAvailable"
                }
            }

        }


    }
    private suspend fun packetHandler(buffer : ByteArray){
        var nonZeroIndex by Delegates.notNull<Int>()
        var count = 0
        val newBuffer = buffer.reversedArray()
        lateinit var packet: Packet
        for(byte in newBuffer){
            if(byte.toString() != "0"){
                nonZeroIndex = count
                break
            }
            count++

        }

        val filteredBuffer = buffer.copyOfRange(0,buffer.size-nonZeroIndex)
        try{
            withContext(Dispatchers.IO){
                packet = Packet.ADAPTER.decode(filteredBuffer)
            }
            //we don't handle packet from our own node incase of broadcast conflict
//                if(packet.fromMeshID != Generator.getPID()){
            if(packet.toMeshID == Generator.getPID() || packet.toMeshID == device.multicastAddress){
//                coroutineLock.withLock {
                    dataMapping(packet)
//                }

            }else{
                forward(packet)
            }
//                }

        }catch(e: Exception){
            Log.e(TAG,"fAILED TO HANDLE")
            e.printStackTrace()
        }

    }
    private suspend fun  dataMapping(packet : Packet){
        if(!checkForbidden(packet)){
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
            }else{
                var info = byteArrayOf()

                if(packet.expected > 1){
                    val key = packet.packetID

                    //enqueue message for sorting
                    if(keyCounter.containsKey(key)){
                        val value = keyCounter[key]
                        keyCounter[key] = value!! + 1
                    }else{
                        keyCounter[key]  = 1
                    }
                    when(packet.messageType){
                        "meshUpdate" -> {
//                        coroutineLock.withLock {
                            primaryBuffers.add(packet)

                            if(keyCounter[key]  == packet.expected){
                                val (fullyReceived, others) = primaryBuffers.partition { it.packetID == key }
                                fullyReceived.sortedBy { it.offset }.forEach { info += it.payload.toByteArray() }
                                decodeData(info,packet.messageType)
                                primaryBuffers = others as MutableList<Packet>

                            }
//                        }

                        }
                        else -> {
                            myMessagesQueue.add(packet)

                            if (keyCounter[key] == packet.expected) {
                                val (fullyReceived, others) = myMessagesQueue.partition { it.packetID == key }
                                myMessagesQueue = others as MutableList<Packet>

                                fullyReceived.sortedBy { it.offset }.forEach {
                                    info += it.payload.toByteArray()
                                }

                                decodeData(info, packet.messageType)

                            }
                        }
                    }

                }else{
                    //pass the message up without enqueue
                    info = packet.payload.toByteArray()

                    decodeData(info,packet.messageType)

                }
            }

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

    private fun decodeData(array : ByteArray , type : String){
        when(type){
            "COMMAND"  ->{
                //this just a bunch of string commands
                //to be see soon

            }
            "ACK"   -> {
                val ack = ACK.ADAPTER.decode(array)
                val sendWorker = OneTimeWorkRequestBuilder<SendFileWorker>()
                    .setInputData(workDataOf("fileCID" to ack.ackID, "offsets" to ack.expectedOffset.toIntArray(), "fromMeshId" to ack.fromMeshID))
                    .build()
                WorkManager.getInstance(mContext).enqueue(sendWorker)
            }
            "filesUpdate" -> {
                val fileUpdate = FileHashMap.ADAPTER.decode(array)
                val files = fileUpdate.cids
                files.forEach { file ->
                    val fileAttribute = mutableMapOf<String,MutableList<String>>()
                    val fileCID = file.cid
                    file.attributes.forEach {attribute ->
                        fileAttribute[attribute.key] = attribute.value.toMutableList()
                    }
                    if(!filesHashMap.containsKey(fileCID)){
                        filesHashMap[fileCID] = fileAttribute
                    }else{
                        val names = filesHashMap[fileCID]?.get("Name")
                        fileAttribute["Name"]?.forEach {
                            if(!names!!.contains(it)){
                                names.add(it)
                            }
                        }
                        val seeders = filesHashMap[fileCID]?.get("Seeders")
                        fileAttribute["Seeders"]?.forEach {
                            if(!seeders!!.contains(it)){
                                seeders.add(it)
                            }
                        }
                        fileAttribute["Name"] = names!!
                        fileAttribute["Seeders"] = seeders!!

                        filesHashMap[fileCID] = fileAttribute
                    }
                }

            }
            "meshUpdate" -> {
                val deviceUpdate = DeviceNode.ADAPTER.decode(array)

//                    updateFun.updateDevice(device, deviceUpdate)
//                    updateFun.updateNeighbours(device,appContext)
            }
        }

    }

    companion object {
        lateinit var mWifiManager: WifiManager
        lateinit var multicastLock: WifiManager.MulticastLock

        val device = MeshDaemon.device
        private val filesHashMap = MeshDaemon.filesHashMap

        private val PORT = 33456
        private val p2pSocket = MulticastSocket(PORT)
        private val wlanSocket = MulticastSocket(PORT)
        private val p2p0 = NetworkInterface.getByName("p2p0")
        private val wlan0 = NetworkInterface.getByName("wlan0")

        @JvmStatic private var primaryBuffers = mutableListOf<Packet>()
        @JvmStatic  private var queueBuffer = mutableListOf<Packet>()

        fun start(context: Context) : Licklider {
            mWifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = mWifiManager.createMulticastLock("multicastLock")

            return Licklider(context)
        }
        @JvmStatic
        @Synchronized private suspend fun send(packet : ByteArray?, ipAddress : String?, port : Int?){
            withContext(Dispatchers.IO){
                if(!multicastLock.isHeld){
                    multicastLock.acquire()
                }
                val receiver = InetAddress.getByName(ipAddress)
                val payload = DatagramPacket(packet,packet!!.size,receiver,port!!)

                try{
                    wlanSocket.networkInterface = wlan0
                    p2pSocket.networkInterface = p2p0
                    wlanSocket.send(payload)
                    p2pSocket.send(payload)

                }catch (e : Exception){
                    e.printStackTrace()
                }
            }

        }

    }


}


