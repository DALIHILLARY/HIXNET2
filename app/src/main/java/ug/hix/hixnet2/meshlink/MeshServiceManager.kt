package ug.hix.hixnet2.meshlink

import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.util.Log
import ug.hix.hixnet2.R

class MeshServiceManager(manager: WifiP2pManager?,channel: WifiP2pManager.Channel?) {
    public val foundDevices = mutableMapOf<String, String>()
    val manager = manager
    val channel  = channel
    val serviceName =  "hixNet2"
    val TAG = javaClass.simpleName

    public fun registerService(){
        val record = device.txtRecord

        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(device.instanceName,"_TTP._udp",record)
    
        manager.addLocalService(channel,serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG,"Service Started successfully")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG,"Failed to Create service ERROR: " + reason)
            }
        })
    }

    private fun discoverServices(){

        val servListener = WifiP2pManager.DnsSdServiceResponseListener { instanceName, registrationType, resourceType ->

            Log.d(TAG, "onBonjourServiceAvailable $instanceName")
        }


        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { fullDomain, record, device ->
            Log.d(TAG, "DnsSdTxtRecord available -$record")

            if(record["service"] == serviceName){
                Log.d(TAG,"FOUND DEVICE "+ record["ConnectInfo"])
                record["ConnectInfo"]?.also {
                    foundDevices[device.deviceAddress] = it

                }

            }

        }
        manager!!.setDnsSdResponseListeners(channel, servListener, txtListener)

        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager.addServiceRequest(channel, serviceRequest,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG,"servicerequest added successfully")
                }

                override fun onFailure(code: Int) {
                    Log.e(TAG,"SERVICEREQUEST FAILED ERROR: "+code)
                }
            }
        )


    }
    public fun startServiceDiscover(){
        manager!!.discoverServices(
            channel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG,"serviceDiscover initiated")
                }

                override fun onFailure(code: Int) {
                    // Command failed. Check for P2P_UNSUPPORTED, ERROR, or BUSY
                    when (code) {
                        WifiP2pManager.P2P_UNSUPPORTED -> {
                            Log.d(TAG, "P2P isn't supported on this device.")
                            TODO("START AS OPEN HOTSPOT")
                        }
                        WifiP2pManager.BUSY->{
                            Log.e(TAG,"NETWORKCARD BUSY")
                        }
                    }
                }
            }
        )

    }

}