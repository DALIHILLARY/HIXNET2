package ug.hix.hixnet2.licklider

import android.util.Log
import okio.ByteString.Companion.toByteString
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.models.ACK
import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.models.Packet
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.util.*
import kotlin.concurrent.thread

class Licklider(message : Any) {

    val TAG = javaClass.simpleName
    val packet = Packet()
    val cypher = Generator()
    val device = DeviceNode()
    private var message  = message
    private var PORT : Int? = null
    private lateinit var buffer : ByteArray
    private lateinit var range : ByteArray

    private fun filter() {
        packet.packetID = cypher.genMID()
        packet.fromMeshID = cypher.getPID()
        when (message) {
            is String -> {
                packet.messageType = "COMMAND"
                PORT = 45345
                buffer  = (message as String).toByteArray()

            }
            is ACK -> {
                packet.messageType = "ACK"
                PORT = 33456
                buffer = (message as ACK).encode()

            }
            is DeviceNode -> {
                packet.messageType = "COMMAND_ACK"
                PORT  = 45345
                buffer  = (message as DeviceNode).encode()
            }
            is Byte  -> {
                packet.messageType = "FILE"
                //reserved for file transfer
                TODO("reserved for file transfer")
            }
        }
    }

    private fun splitter(){

         val blockSize = 1200;
         val blockCount = (buffer.size + blockSize - 1) / blockSize
         packet.expected = blockCount

         var i = 1

         while(i < blockCount){
             packet.offset = i
             val idx = (i - 1) * blockSize
             range = buffer.copyOfRange(idx, idx + blockSize)

             packet.payload = range.toByteString()

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

         Log.d(TAG, "Chunk $blockCount :  ${range.toString()}")

    }

    fun send(packet : ByteArray, ipAddress : String, port : Int){
        val socket =  DatagramSocket()
        val receiver = InetAddress.getByName(ipAddress)
        val payload = DatagramPacket(packet,packet.size,receiver,port)
        socket.send(payload)
        socket.close()
        TODO("GET REAL IP ADDRESS FROM PID")

    }
    private fun receiver(){
        thread{
            val rBuffer = ByteArray(2048)
            val socket = MulticastSocket(33456)
            val group = InetAddress.getByName("230.0.0.1")
            socket.joinGroup(group)

            //always listen for incoming data
            while(true){
                val packet = DatagramPacket(rBuffer, rBuffer.size)
                socket.receive(packet)

            }
        }
    }

}