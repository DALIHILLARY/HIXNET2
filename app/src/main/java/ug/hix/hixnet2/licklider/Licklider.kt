package ug.hix.hixnet2.licklider

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import okio.ByteString.Companion.toByteString
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.licklider.buffers.LickBuffers
import ug.hix.hixnet2.models.ACK
import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.models.Packet
import java.io.IOException
import java.net.*

open class Licklider(private val mContext : Context) : LickBuffers() {

    private val TAG = javaClass.simpleName
    private val PORT = 33456

    private val rBuffer = ByteArray(2048)
    private val socket = MulticastSocket(PORT)
    private val p2p0 = NetworkInterface.getByName("p2p0")
    private val wlan0 = NetworkInterface.getByName("wlan0")

    private lateinit var message : Any
    private lateinit var buffer : ByteArray
    private lateinit var range : ByteArray
    protected lateinit var packet : Packet

    private val mWifiManager = mContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val multicastLock = mWifiManager.createMulticastLock("multicastLock")

    fun loadData(message : Any){
        this.message = message

        packet.packetID = Generator.genMID()
        packet.fromMeshID = Generator.getPID()
        when (this.message) {
            is String -> {
                packet.messageType = "COMMAND"
                packet.port = 45345
                buffer  = (message as String).toByteArray()

            }
            is ACK -> {
                packet.messageType = "ACK"
                packet.port = 33456
                buffer = (message as ACK).encode()

            }
            is DeviceNode -> {
                packet.messageType = "COMMAND_ACK"
                packet.port = 45345
                buffer  = (message as DeviceNode).encode()
            }
            is Byte  -> {
                packet.messageType = "FILE"

                /**
                 * reserved for file transfer
                 */


            }
        }

        splitter(packet)
    }

    private fun splitter(packet : Packet){

         val blockSize = 1200;
         val blockCount = (buffer.size + blockSize - 1) / blockSize
         packet.expected = blockCount

         var i = 1

         while(i < blockCount){
             packet.offset = i
             val idx = (i - 1) * blockSize
             range = buffer.copyOfRange(idx, idx + blockSize)

             packet.payload = range.toByteString()

             forward(packet,::send)

             Log.d(TAG,"chucked msg $i  :  ${range.toString()}")
             i++

         }

        // Last chunk from the calculation

         val end = if (buffer.size % blockSize == 0) {
             buffer.size
         } else {
             buffer.size % blockSize + blockSize * (blockCount - 1);
         }

         packet.offset = blockCount
         range = buffer.copyOfRange((blockCount - 1) * blockSize, end)
         packet.payload = range.toByteString()

        forward(packet,::send) //higher order function call

        Log.d(TAG, "Chunk $blockCount :  ${range.toString()}")

    }



    private fun packetHandler(buffer : ByteArray){
        val packet = Packet.ADAPTER.decode(buffer)

        //we don't handle packet from our own node incase of broadcast conflict
        if(packet.fromMeshID != Generator.getPID()){
            if(packet.toMeshID == Generator.getPID()){
                dataMapping(packet)
            }else{
                forward(packet,::send)
            }
        }

    }


    @Synchronized private fun send(packet : ByteArray?, ipAddress : String?, port : Int?){
        if(!multicastLock.isHeld){
            multicastLock.acquire()
        }
        val socket =  DatagramSocket()
        val receiver = InetAddress.getByName(ipAddress)
        val payload = DatagramPacket(packet,packet!!.size,receiver,port!!)


        try{
            socket.send(payload)
            socket.close()

        }catch (e : IOException){
            Log.e(TAG,"NO NETWORK AVAILABLE")
        }

    }

    @Synchronized fun receiver(multicastAddress : String){
        if(!multicastLock.isHeld){
            multicastLock.acquire()
        }
        if(group != null){
            endSocket()
        }
        group = InetAddress.getByName(multicastAddress)
        socket.joinGroup(InetSocketAddress(group,PORT),p2p0)
        socket.joinGroup(InetSocketAddress(group,PORT),wlan0)

        //always listen for incoming data
        while(true){
            val packet = DatagramPacket(rBuffer, rBuffer.size)
            socket.receive(packet)

            packetHandler(packet.data)
        }

    }

    private fun endSocket(){
        socket.leaveGroup(InetSocketAddress(group,PORT),p2p0)
        socket.leaveGroup(InetSocketAddress(group,PORT),wlan0)
    }

    fun enqueuePacket() {
        queueBuffer.add(packet)
    }

    fun dequePacket(){
        if(!queueBuffer.isNullOrEmpty()){
            queueBuffer.forEach {
                forward(packet,::send)
                queueBuffer.remove(it)
            }
        }
    }

    companion object {
        var group : InetAddress? = null

    }

}


