package ug.hix.hixnet2.licklider.buffers

import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.models.ACK
import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.models.Packet


class LickBuffers {
    var recvBuffer = ByteArray(2048)
    
    lateinit var packet : Packet
    lateinit var device : DeviceNode
    lateinit var ack  : ACK

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

   private fun packetHandler(buffer : ByteArray){
       packet = Packet.ADAPTER.decode(buffer)

       if(packet.toMeshID == Generator().getPID()){

       }else{
           forward()
       }

   }

    private fun forward(meshId : String){
        val link = getLink(meshId)
        if(!link.equals("notAvailable")){

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

}