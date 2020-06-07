package ug.hix.hixnet2.meshlink

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.util.Log
import ug.hix.hixnet2.models.DeviceNode
//import ug.hix.hixnet2.services.chinnchiMeshDaemon

class MeshServiceManager(context : Context, manager: WifiP2pManager, channel: WifiP2pManager.Channel) : WifiP2pManager.ConnectionInfoListener,WifiP2pManager.GroupInfoListener {
    val foundDevices = mutableMapOf<String, String>()
    private val manager = manager
    private val channel  = channel
    private val mContext = context.applicationContext
    private val serviceName =  "hixNet2"
    val TAG = javaClass.simpleName
    private val receiver = MeshBroadcastReceiver(this,manager,channel)

    var isServiceStarted = false
    var isDiscoverying   = false
    var hasWifiInternet  = false
    var isMaster         = false


    var SSID : String = ""
    var BSSID : String = ""
    var passPhrase : String = ""
    var device  = DeviceNode()


    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
    }

    val wifiP2pBroadcastReceiver = mContext.registerReceiver(receiver,intentFilter)

    public fun registerService(){
        val record = device.txtRecord

        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance("hix","_TTP._udp",record)
    
        manager?.addLocalService(channel,serviceInfo, object : WifiP2pManager.ActionListener {
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
        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()

        manager?.addServiceRequest(channel,serviceRequest, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG,"Service Started successfully")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG,"Failed to Create service ERROR: " + reason)
            }
        })
        manager!!.discoverServices(
            channel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG,"serviceDiscover initiated")
                }

                override fun onFailure(code: Int) {
                    // Command failed. Check for P2P_UNSUPPORTED, ERROR, or BUSY
                    Log.e(TAG,"ERROR Incurred while service discover" + code)

                    when (code) {
                        WifiP2pManager.P2P_UNSUPPORTED -> {
                            Log.d(TAG, "P2P isn't supported on this device.")
                            TODO("START AS OPEN HOTSPOT and configure it with unique things")
                        }
                        WifiP2pManager.BUSY->{
                            Log.e(TAG,"NETWORKCARD BUSY")
                        }
                    }
                }
            }
        )

    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {

    }

    private fun createGroup(){
        manager.createGroup(channel,object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                Log.d(TAG,"Group started")

            }

            override fun onFailure(reason: Int) {
                Log.e(TAG,"Create group fail error: " + reason)
            }
        })
    }

    override fun onGroupInfoAvailable(group: WifiP2pGroup?) {
        val p2pgroupInfo = WifiP2pGroup()
        if(p2pgroupInfo.isGroupOwner){
            SSID = p2pgroupInfo.networkName
            passPhrase = p2pgroupInfo.passphrase
            BSSID   = p2pgroupInfo.`interface`
            Log.d(TAG,"check interface $SSID  $BSSID  $passPhrase")        }
    }

}