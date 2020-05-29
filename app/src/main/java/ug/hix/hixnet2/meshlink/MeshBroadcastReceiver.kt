package ug.hix.hixnet2.meshlink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import java.util.*

class MeshBroadcastReceiver(instance : Any? , manager: WifiP2pManager, channel: WifiP2pManager.Channel) : BroadcastReceiver() {
    val TAG = javaClass.simpleName
    val manager = manager
    val channel =  channel
    val instance = instance

    override fun onReceive(context: Context?, intent: Intent) {
        when(intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                // Determine if Wifi P2P mode is enabled or not, alert
                // the Activity.
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if( state  != WifiP2pManager.WIFI_P2P_STATE_ENABLED){
                    Log.v(TAG,"P2P is disabled")
                }
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                if (manager == null) {
                    return
                }

                val networkInfo = intent!!.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo

                if (networkInfo.isConnected && networkInfo.typeName == "WIFI_P2P") {
                    instance.hasMaster  = true
                    manager.requestConnectionInfo(channel, salutInstance)
                } else {
                    instance.hasMaster = false
                    instance.hasInternetWifi = true
                    Log.v(TAG, "Not connected to another device.")
                    if (salutInstance.thisDevice.isRegistered) {
                        if (salutInstance.unexpectedDisconnect != null) {
//                        salutInstance.unregisterClient(salutInstance.unexpectedDisconnect, null, false);
                        }
                    }
                }

            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                val device =  intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE) as WifiP2pDevice
                DeviceNode.deviceName = device.deviceName
                DeviceNode.macAddress  = device.deviceAddress

            }
        }

    }
}