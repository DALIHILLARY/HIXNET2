package ug.hix.hixnet2.licklider

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import androidx.work.*
import com.snatik.storage.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.database.WifiConfig
import ug.hix.hixnet2.database.File as DFile
import ug.hix.hixnet2.models.*
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.services.MeshDaemon
import ug.hix.hixnet2.util.Util
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

    private val coroutineLock = Mutex()
    lateinit var dbDevice : ug.hix.hixnet2.database.DeviceNode
    var keyCounter = mutableMapOf<String, Int>()

    private var myMessagesQueue = mutableListOf<Packet>()


    fun loadData(message: Any, toMeshId: String = ""){
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
                val file = File(message.path!!)
                packet = packet.copy(messageType = "FILE",port = PORT, packetID = message.CID)
                splitter(mPacket = packet, file = file)
            }
        }
        if(!isFile){
            splitter(packet,buffer)

        }
    }
    fun loadData(mFile : DFile, offsets: List<Int>,toMeshId: String){
        var packet = Packet()
        val file = File(mFile.path!!)
        packet = packet.copy(messageType = "FILE",port = PORT, packetID = mFile.CID,originalFromMeshID = Generator.getPID(),toMeshID = toMeshId)
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
                        if(offsets != null){
                            if( i in offsets){
                                val randomAccessFile = RandomAccessFile(file,"r")
                                if(randomAccessFile.length() < i*1200L || randomAccessFile.length() == i*1200L) {
                                    randomAccessFile.close()
                                    break
                                }
                                randomAccessFile.seek((i-1)*1200L)
                                Log.d("fp","filepointer: ${randomAccessFile.filePointer} length: ${randomAccessFile.length()}")
                                randomAccessFile.read(readBuffer,0,readBuffer.size)
                                randomAccessFile.close()
                                packet = packet.copy(offset = i,payload = readBuffer.toByteString())
                                runBlocking{
                                    forward(packet)
                                }
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
                            runBlocking{
                                forward(packet)
                            }
                        }
                        i++
                    }else{
                        Log.d("runningOut", " I AM ALMOST DONE HERE")
                    }

                }

            }else{
                var i = 1

                while(i < blockCount){
                    val idx = (i - 1) * blockSize
                    val range = buffer!!. copyOfRange(idx, idx + blockSize)

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

    @ExperimentalCoroutinesApi
    fun receiver(multicastAddress : String){
        if(!multicastLock.isHeld){
            multicastLock.acquire()
        }

        if(group != null){
            endSocket()
        }

        group = InetAddress.getByName(multicastAddress)

        GlobalScope.launch{
            //coroutine for new device and cloud file listeners
            launch {
                val repo = Repository(mContext)

                /*
                * CLoud file listeners
                * listen for name changes, seeders a
                 */
                try{
                    repo.getUpdatedName().collect { name ->
                        name?.let {
                            //                    TODO("send file info to neighbours")

                            Log.d(TAG,"New file detected: $it")
                        }

                    }
                }catch (e: Throwable){
                    Log.e(TAG,"Something happened to the name listener")
                }
                try{
                    repo.getUpdatedFileName().collect { filename ->
                        filename?.let {
                            //                    TODO("send file info to neighbours")

                            Log.d(TAG,"New file detected: $it")
                        }

                    }
                }catch (e: Throwable){
                    Log.e(TAG,"Something happened to the filename listener")
                }

//                fileSeeder listener
                try{
                    repo.getUpdatedFileSeeder().collect { fileSeeder ->
                        fileSeeder?.let {
                            //                    TODO("send file info to neighbours")

                            Log.d(TAG,"New file detected: $it")
                        }

                    }
                }catch (e: Throwable){
                    Log.e(TAG,"Something happened to the fileSeeder listener")
                }

//                new device listener
                try{

                    repo.getUpdatedDevice().collect {
                        it?.let{
                            val deviceSend = DeviceNode(
                                meshID = MeshDaemon.device.meshID,
                                peers = listOf(
                                    DeviceNode(
                                        meshID = it!!.device.meshID,
                                        Hops = it.device.hops,
                                        macAddress = it.wifiConfig.mac,
                                        publicKey = it.device.publicKey,
                                        hasInternetWifi = it.device.hasInternetWifi,
                                        wifi = it.wifiConfig.ssid,
                                        passPhrase = it.wifiConfig.passPhrase,
                                        version = it.device.version,
                                        status = it.device.status
                                    )

                                )
                            )
                            Log.d(TAG,"New device detected: $it")
                            loadData(message = deviceSend, toMeshId = it!!.device.meshID)
                            if(it.device.status == "DISCONNECTED") repo.deleteDevice(it.device)
                        }

                    }
                }catch (e: Throwable){
                    Log.e(TAG,"Something happened to the device listener")
                }

            }
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
                    Log.d("p2p0receiver","data received")
                    launch{
                        packetHandler(packet.data, "p2p0")
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
                    Log.d("wlan0receiver","data received")
                    launch{
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

    private fun endSocket(){
        p2pSocket.leaveGroup(InetSocketAddress(group,PORT),p2p0)
        wlanSocket.leaveGroup(InetSocketAddress(group,PORT),wlan0)
    }

    private suspend fun forward(mPacket: Packet){
        var packet = mPacket
        val link = getLink(packet.toMeshID,packet.messageType)

        val data = Packet.ADAPTER.encode(packet)
        val port = packet.port
//        Log.d("forwarder addresses", link.toString())
        //check if primary mesh buffers are empty
        if(primaryBuffers.isEmpty()) {
            if(link.first().first != "notAvailable"){
                link.forEach { address ->
                    packet = packet.copy(fromMeshID = Generator.getPID(),timeToLive = address.third*2)
                    send(data,address.first,port,address.second)
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

    private fun getLink(meshId : String, packetType : String) : List<Triple<String,String,Int>> {
        val repo = Repository(mContext)
        return if (packetType == "meshUpdate"){
           repo.getNearLinks(meshId) //get all neighbour links except parent of meshId
        }else{
            if(packetType == "ACK"){
//                TODO("get address nearest with file")
//                this in a test will be deleted
                listOf(repo.getLink(meshId))
            }else{
                listOf(repo.getLink(meshId))
            }

        }

    }
    private suspend fun packetHandler(buffer : ByteArray, iFace: String){
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
            packet = withContext(Dispatchers.IO){
                Packet.ADAPTER.decode(filteredBuffer)
            }
            //we don't handle packet from our own node incase of broadcast conflict
//                if(packet.fromMeshID != Generator.getPID()){
            if(packet.toMeshID == Generator.getPID() || packet.toMeshID == device.meshID){
//                coroutineLock.withLock {
                    dataMapping(packet,iFace)
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
    private suspend fun  dataMapping(packet : Packet, iFace: String){
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
                                decodeData(info,packet.messageType,iFace)
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

                                decodeData(info, packet.messageType,iFace)

                            }
                        }
                    }

                }else{
                    //pass the message up without enqueue
                    info = packet.payload.toByteArray()

                    decodeData(info,packet.messageType,iFace)

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

    private suspend fun decodeData(array : ByteArray , type : String, iFace: String){
        when(type){
            "COMMAND"  ->{
                //this just a bunch of string commands
                //to be see soon

            }
            "ACK"   -> {
                withContext(Dispatchers.IO){
                    val ack = ACK.ADAPTER.decode(array)
                    val cacheDir = mContext.externalCacheDir?.absolutePath
                    if(!storage.isDirectoryExists("$cacheDir/Acks")){
                        storage.createDirectory("$cacheDir/Acks")
                    }
                    storage.createFile("$cacheDir/Acks/${ack.ackID}.ch",ack.expectedOffset.joinToString())

                    val sendWorker = OneTimeWorkRequestBuilder<SendFileWorker>()
                        .setInputData(workDataOf("fileCID" to ack.ackID, "offsets" to ack.ackID, "fromMeshId" to ack.fromMeshID))
                        .build()
                    WorkManager.getInstance(mContext).enqueue(sendWorker)

                }
            }
            "filesUpdate" -> {
                val fileUpdate = withContext(Dispatchers.IO){FileHashMap.ADAPTER.decode(array)}
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
                val deviceUpdate = withContext(Dispatchers.IO){DeviceNode.ADAPTER.decode(array)}

                val deviceObj = deviceUpdate.peers.map { device ->
                    ug.hix.hixnet2.database.DeviceNode(
                        meshID = device.meshID,
                        multicastAddress = deviceUpdate.meshID,
                        hops = device.Hops + 1,
                        publicKey = device.publicKey,
                        hasInternetWifi = device.hasInternetWifi,
                        iface = iFace,
                        status = device.status,
                        version = device.version,
                        modified = Util.currentDateTime()
                    )
                }
                val wifiConfigObj =  deviceUpdate.peers.map {device ->
                    WifiConfig(
                        meshID = device.meshID,
                        mac = device.macAddress,
                        ssid = device.wifi,
                        passPhrase = device.passPhrase
                    )
                }
                val repo = Repository(mContext)  
                repo.insertOrUpdateDeviceWithConfig(deviceObj,wifiConfigObj)

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
        @Synchronized private suspend fun send(packet : ByteArray?, ipAddress : String?, port : Int?, iface: String){
            withContext(Dispatchers.IO){
                if(!multicastLock.isHeld){
                    multicastLock.acquire()
                }
                val receiver = InetAddress.getByName(ipAddress)
                val payload = DatagramPacket(packet,packet!!.size,receiver,port!!)

                try{
                    if(iface == "wlan0"){
                        wlanSocket.networkInterface = wlan0
                        wlanSocket.send(payload)
                    }else{
                        p2pSocket.networkInterface = p2p0
                        p2pSocket.send(payload)
                    }

                }catch (e : Exception){
                    e.printStackTrace()
                }
            }

        }

    }


}


