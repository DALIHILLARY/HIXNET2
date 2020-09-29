package ug.hix.hixnet2.meshlink

import android.content.Context
import ug.hix.hixnet2.licklider.Licklider
import ug.hix.hixnet2.models.DeviceNode

class UpdateDevice() {
    fun updateDevice(device: DeviceNode, deviceUpdate: DeviceNode){
        val tempDevice = DeviceNode()

        tempDevice.copy(
            meshID = deviceUpdate.meshID,
            multicastAddress = deviceUpdate.multicastAddress,
            macAddress = deviceUpdate.macAddress,
            services = deviceUpdate.services,
            Hops = deviceUpdate.Hops + 1,
            hasInternetWifi = deviceUpdate.hasInternetWifi,
            deviceName = deviceUpdate.deviceName
        )
        device.copy(peers = device.peers.plus(tempDevice))
        deviceUpdate.peers.forEach { peer ->
            if(peer.multicastAddress != device.meshID){
                tempDevice.copy(
                    meshID = peer.meshID,
                    multicastAddress = deviceUpdate.meshID,
                    macAddress = peer.macAddress,
                    services = peer.services,
                    Hops = peer.Hops + 1,
                    hasInternetWifi = peer.hasInternetWifi,
                    deviceName = peer.deviceName
                )
                device.copy(peers = device.peers.plus(tempDevice))
            }

        }

        //TODO(" solve the peer paradox ")
    }
    suspend fun updateNeighbours(device: DeviceNode, context: Context){

            Licklider.start(context).loadData(device)

    }
}