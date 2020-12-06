package ug.hix.hixnet2.licklider

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.snatik.storage.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.database.File as DFile
import ug.hix.hixnet2.meshlink.UpdateDevice
import ug.hix.hixnet2.models.*
import ug.hix.hixnet2.services.MeshDaemon
import java.io.BufferedInputStream
import java.io.File
import java.net.*
import kotlin.properties.Delegates

class Licklider(private val mContext: Context){

    private val TAG = javaClass.simpleName
    private var group : InetAddress? = null

    private val coroutineLock = Mutex()
    lateinit var dbDevice : ug.hix.hixnet2.database.DeviceNode
    var keyCounter = mutableMapOf<String, Int>()
    private val updateFun = UpdateDevice()
    private val ackChannel = Channel<Packet>(1024)
    private val fileChannel = Channel<Packet>(1024*1024)

    private val forbiddenPackets = mutableMapOf<String,Pair<List<Int>,Long>>()
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
                packet = packet.copy(messageType = "ACK",port = 33456)
                buffer = ACK.ADAPTER.encode(message)

            }
            is FileHashMap -> {
                packet = packet.copy(messageType = "filesUpdate",port = 33456)
                buffer = FileHashMap.ADAPTER.encode(message)
            }
            is DeviceNode -> {
                packet = packet.copy(messageType = "meshUpdate",port = 33456)
                packet = packet.copy(port = 33456)
                buffer  = DeviceNode.ADAPTER.encode(message)

            }
            is TransFile  -> {
                packet = packet.copy(messageType = "FILE",port = PORT)
                buffer = TransFile.ADAPTER.encode(message)
                packet = packet.copy(port = PORT)


            }
            is DFile -> {
                isFile = true
                val file = File(message.path)
                packet = packet.copy(messageType = "FILE",port = PORT)
                splitter(mPacket = packet, file = file)
            }
        }
        if(!isFile){
            splitter(packet,buffer)
        }
    }

    private fun splitter(mPacket : Packet, buffer: ByteArray? = null, file: File? = null){
        var packet = mPacket
        val blockSize = 1200
        val runtime = Runtime.getRuntime()
//        val scope = CoroutineScope(Dispatchers.Default)
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

                while(true){
                    if(runtime.freeMemory() >= 40000) {
                        val remBytes = fileStream.available()
                        if( remBytes == 0) break
                        if(remBytes < 1200){
                            readBuffer = ByteArray(remBytes.rem(1200))
                        }
                        fileStream.read(readBuffer,0,readBuffer.size)
                        packet = packet.copy(offset = i,payload = readBuffer.toByteString())

//                    scope.launch {
//                        coroutineScope {
//                            forward(packet)
//
//                        }
//                    }
                        runBlocking{
                            forward(packet)
                        }

                        i++
                    }else{
                        Log.d("runn9ingOut", " I AM ALMOST DONE HERE")
                    }

                }
                fileStream.close()

            }else{
                var i = 1

                while(i < blockCount){
                    val idx = (i - 1) * blockSize
                    val range = buffer!!.copyOfRange(idx, idx + blockSize)

                    packet = packet.copy(payload = range.toByteString(),offset = i)
//                    scope.launch {
//                        coroutineScope {
//                            forward(packet)
//
//                        }
//                    }
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
    private suspend fun ackListener(){
        while(true){
            forbiddenPackets.forEach {
                TODO("get missing packets for ack")
            }
            delay(1000L)
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

            launch(Dispatchers.IO){
                p2pSocket.joinGroup(InetSocketAddress(group,PORT),p2p0)

                while(true){
                    val rBuffer = ByteArray(1500)
                    val packet = DatagramPacket(rBuffer, rBuffer.size)
                    p2pSocket.receive(packet)
                    launch(Dispatchers.IO) {
                        packetHandler(mContext,packet.data)
                    }

                }
            }
            launch(Dispatchers.IO) {
                wlanSocket.joinGroup(InetSocketAddress(group,PORT),wlan0)
                while(true){
                    val rBuffer = ByteArray(1500)
                    val packet = DatagramPacket(rBuffer, rBuffer.size)
                    wlanSocket.receive(packet)
                    launch(Dispatchers.IO) {
                        packetHandler(mContext,packet.data)
                    }
                }
            }
            launch(Dispatchers.IO) {
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
            getLink(packet.toMeshID)
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

    private fun getLink(meshId : String) : String? {
        return if (meshId == "All"){
            val filteredList = device.peers.filter { it.multicastAddress.contains(".") }.toString()
            filteredList
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
    private suspend fun packetHandler(mContext: Context, buffer : ByteArray){
        var nonZeroIndex by Delegates.notNull<Int>()
        var count = 0
        val newBuffer = buffer.reversedArray()
        for(byte in newBuffer){
            if(byte.toString() != "0"){
                nonZeroIndex = count
                break
            }
            count++

        }

        val filteredBuffer = buffer.copyOfRange(0,buffer.size-nonZeroIndex)
        try{
            val packet = Packet.ADAPTER.decode(filteredBuffer)
            //we don't handle packet from our own node incase of broadcast conflict
//                if(packet.fromMeshID != Generator.getPID()){
            if(packet.toMeshID == Generator.getPID() || packet.toMeshID == device.multicastAddress){
//                coroutineLock.withLock {
                    dataMapping(packet, mContext.applicationContext)
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
    private fun dataMapping(packet : Packet, appContext: Context){
        if(!checkForbidden(packet)){
            var info = byteArrayOf()

            if(packet.expected > 1){
                val key = packet.packetID

                //enqueue message for sorting
                if(keyCounter.containsKey(packet.packetID)){
                    val value = keyCounter[key]

                    if (value != null) {
                        keyCounter[key] = value +1
                    }

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
                                decodeData(info,packet.messageType,appContext)
                                primaryBuffers = others as MutableList<Packet>

                            }
//                        }

                    }
                    "FILE" -> {
                        // to be implemented later
                        runBlocking {
                            val storage = Storage(appContext)
                            if(!storage.isDirectoryExists(storage.externalStorageDirectory +"HixNet/.${packet.packetID}")){
                                storage.createDirectory(storage.externalStorageDirectory +"HixNet/.${packet.packetID}")
                            }
                            storage.createFile(storage.externalStorageDirectory +"/HixNet/.${packet.packetID}/${packet.offset}.prt",packet.payload.toByteArray())
//                            mergeChannel.send(packet.packetID)
                        }

                    }
                    else -> {
                        myMessagesQueue.add(packet)

                        if (keyCounter[key] == packet.expected) {
                            val (fullyReceived, others) = myMessagesQueue.partition { it.packetID == key }
                            myMessagesQueue = others as MutableList<Packet>

                            fullyReceived.sortedBy { it.offset }.forEach {
                                info += it.payload.toByteArray()
                            }

                            decodeData(info, packet.messageType, appContext)

                        }
                    }
                }

            }else{
                //pass the message up without enqueue
                info = packet.payload.toByteArray()

                decodeData(info,packet.messageType,appContext)

            }
        }

    }
    private fun checkForbidden(packet: Packet) : Boolean {
        return if (forbiddenPackets.containsKey(packet.packetID)){
            var forbiddenOffsets = forbiddenPackets[packet.packetID]?.first
            if(forbiddenOffsets!!.contains(packet.offset)){
                true
            }else{
                forbiddenOffsets = forbiddenOffsets + packet.offset

//                coroutineLock.withLock {
                    forbiddenPackets[packet.packetID] = Pair(forbiddenOffsets,System.currentTimeMillis())

//                }
                Log.d(TAG,"Forbidden Packets: $forbiddenPackets")

                false
            }
        }else{
            
//            coroutineLock.withLock {
                forbiddenPackets[packet.packetID] = Pair(listOf(packet.offset),System.currentTimeMillis())
//            }
            Log.d(TAG,"Forbidden Packets: $forbiddenPackets")
            false
        }
    }
    private fun decodeData(array : ByteArray , type : String, appContext: Context){
        when(type){
            "COMMAND"  ->{
                //this just a bunch of string commands
                //to be see soon


            }
            "ACK"   -> {
                val ack = ACK.ADAPTER.decode(array)
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
            "FILE"  -> {
                val docFiles = listOf<String>("docx","doc","xlsx","pdf","xls")
                val mediaFiles = listOf<String>("mp4","mp3","mpeg","mkv","vob")
                val file = TransFile.ADAPTER.decode(array)
                val storage = Storage(appContext)

                when(file.extension){
                    in docFiles -> {
                        if(!storage.isDirectoryExists(storage.externalStorageDirectory+"/HixNet/Documents")){
                            storage.createDirectory(storage.externalStorageDirectory+"/HixNet/Documents")
                        }
                        storage.createFile(storage.externalStorageDirectory +"/HixNet/Documents/" + file.fileName,file.fileContent.toByteArray())
                    }
                    in mediaFiles -> {
                        storage.createFile(storage.externalStorageDirectory + "/Media/"+ file.fileName,file.fileContent.toByteArray())
                    }
                    else ->{
                        storage.createFile(storage.externalStorageDirectory +"/Others/" + file.fileName,file.fileContent.toByteArray())

                    }
                }

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

        val mergeChannel = Channel<String>(20)

        @JvmStatic private var primaryBuffers = mutableListOf<Packet>()
        @JvmStatic  private var queueBuffer = mutableListOf<Packet>()

        fun start(context: Context) : Licklider {
            mWifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = mWifiManager.createMulticastLock("multicastLock")

            return Licklider(context)
        }
        @JvmStatic
        @Synchronized private  fun send(packet : ByteArray?, ipAddress : String?, port : Int?){
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
        fun assembler(){
            //This function receives packets and merges them together
            runBlocking {
//                val packetIDs =
//                for (packetId in mergeChannel){
//
//                }
            }

        }

    }


}


