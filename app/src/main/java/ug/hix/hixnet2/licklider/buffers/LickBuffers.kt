package ug.hix.hixnet2.licklider.buffers

import okio.ByteString
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.licklider.Licklider
import ug.hix.hixnet2.models.ACK
import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.models.Packet
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress



open class LickBuffers {

    var recvBuffer = ByteArray(2048)
    lateinit var packet : Packet
    lateinit var device : DeviceNode
    lateinit var ack  : ACK
    lateinit var queueBuffer : MutableList<Packet>
    lateinit var keyCounter : MutableMap<String, Int>
    lateinit var myMessagesQueue : MutableList<Packet>


    fun recv(){

        when(packet.messageType){
            "COMMAND"  ->{
                //this just a bunch of string commands
                //to be see soon
                TODO("ADD COMMANDS")

            }
            "ACK"   -> {
                ack = ACK.ADAPTER.decode(packet.payload)
            }
            "COMMAND_ACK" -> {
                device = DeviceNode.ADAPTER.decode(packet.payload)
            }
            "FILE"  -> {
                //to be done at a later tyme
            }
        }

    }


    fun forward(){
        val link = getLink(packet.toMeshID)
        if(!link.equals("notAvailable")){
            Licklider.send(Packet.ADAPTER.encode(packet),link,packet.port)
        }else{
            queueBuffer.add(packet)
        }
    }

    private fun getLink(meshId : String) : String? {
        //step 1 get all references of the meshId
        val filteredList = device.peers.filter { it.meshID == meshId }

        //step2 get the minimal hops and then the ipAddress
        val minDevice = filteredList.minBy { it.Hops }
        val minAddress = minDevice?.multicastAddress

        //step3 determine if the address is real
        return if (minAddress != null) {
            if(!minAddress.contains(".")){
                device.peers.find { it.meshID == minAddress && it.multicastAddress.contains(".") }?.multicastAddress
            }else{
                minAddress
            }
        }else{
            "notAvailable"
        }

    }

    protected fun dataMapping(packet : Packet){
        lateinit var info : ByteArray

        if(packet.expected as Int  > 1){
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
            myMessagesQueue.add(packet)

            if(keyCounter[key]  == packet.expected as Int){
                var (fullyReceived, others) = myMessagesQueue.partition { it.packetID == key }
                myMessagesQueue = others as MutableList<Packet>

                fullyReceived.sortedBy { it.offset }.forEach { info + it.payload.toByteArray() }

            }
        }else{
            //pass the message up without enqueue
            info = packet.payload.toByteArray()

        }
    }

}