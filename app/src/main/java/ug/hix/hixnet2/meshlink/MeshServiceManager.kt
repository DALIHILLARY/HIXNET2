package ug.hix.hixnet2.meshlink

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.util.Log
import com.knexis.hotspot.Hotspot
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.licklider.Licklider
import kotlin.concurrent.thread

class MeshServiceManager(context : Context, manager: WifiP2pManager, channel: WifiP2pManager.Channel) : WifiP2pManager.ConnectionInfoListener,WifiP2pManager.GroupInfoListener {
    var foundDevices = mutableMapOf<String, String>()

    private val hotspot = Hotspot(context)
    private val manager = manager
    private val channel  = channel
    private val mContext = context.applicationContext
    private val serviceName =  "hixNet2"
    val TAG = javaClass.simpleName
    private val receiver = MeshBroadcastReceiver(this,manager,channel)

    var isServiceStarted = false
    var isDiscovering   = false
    var hasWifiInternet  = false
    var isMaster         = false
    var isRegisterRunning = false
    var p2pSupport       = true


    var SSID : String = ""
    var BSSID : String = ""
    var passPhrase : String = ""
    //var device  = DeviceNode()


    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
    }

    val wifiP2pBroadcastReceiver = mContext.registerReceiver(receiver,intentFilter)

    fun registerService(){
        val record = mutableMapOf<String,String>()
        record["service"] = "hixNet2"
        record["SSID"] = SSID
        record["BSSID"] = BSSID
        record["passPhrase"] = passPhrase

        val instanceName = "$SSID -> $BSSID"


        //clear any old registered services
        deactivateService()

        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(instanceName,"_TTP._udp",record)

        manager.addLocalService(channel,serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG,"Service Started successfully")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to Create service ERROR: $reason")
            }
        })

        //discover services to counter android bug
        startServiceDiscovery()
    }

    private fun setupDnsResponders(){

        val serviceListener = WifiP2pManager.DnsSdServiceResponseListener { instanceName, _, _ ->

            Log.d(TAG, "Found Device  $instanceName")
        }


        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { _, record, device ->
            Log.d(TAG, "DnsSdTxtRecord available -$record")

            if(record["service"] == serviceName){
                Log.d(TAG,"FOUND DEVICE "+ record["ConnectInfo"])
                record["ConnectInfo"]?.also {
                    foundDevices[device.deviceAddress] = it

                }

            }

        }
        manager.setDnsSdResponseListeners(channel, serviceListener, txtListener)

        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager.addServiceRequest(channel, serviceRequest,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG,"service request added successfully")
                }

                override fun onFailure(code: Int) {
                    Log.e(TAG, "SERVICE REQUEST FAILED ERROR: $code")
                }
            }
        )


    }
    fun startServiceDiscovery(){

        //set up service listeners
        setupDnsResponders()

        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()

        manager.addServiceRequest(channel,serviceRequest, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG,"Service Started successfully")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to Create service ERROR: $reason")
            }
        })
        manager.discoverServices(
            channel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG,"serviceDiscover initiated")
                }

                override fun onFailure(code: Int) {

                    when (code) {
                        WifiP2pManager.P2P_UNSUPPORTED -> {
                            Log.e(TAG, "P2P isn't supported on this device.")

                            p2pSupport = false
                            val (hotspotName,keyPass) = Generator.getWifiPassphrase()

                            hotspot.start(hotspotName,keyPass)
                            TODO("GET THE HOTSPOT FEATURE WORKING")
                        }
                        WifiP2pManager.NO_SERVICE_REQUESTS -> {
                            Log.d(TAG,"No service requests detected")
                        }
                        WifiP2pManager.BUSY -> {

                            Log.e(TAG,"NETWORK CARD BUSY")
                        }
                    }
                }
            }
        )

    }

    fun stopServiceDiscovery(){
        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()

        manager.removeServiceRequest(channel,serviceRequest, object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                Log.d(TAG,"successfully removed service request")
            }

            override fun onFailure(reason: Int) {
                Log.d(TAG, "failed to remove service request")
            }

        })
    }

    private fun deactivateService(){
        manager.clearLocalServices(channel, object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                Log.d(TAG, "Local service removed successfully")
            }

            override fun onFailure(reason: Int) {
                Log.d(TAG, "Failed to remove service")
            }

        })
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        if(!isMaster){

            TODO("Send data about client")

        }else{
            if(!isRegisterRunning){
                //listen for command and join packets
                thread{
                    Licklider.receiver()
                }
            }
        }
    }

    fun createGroup(){
        manager.createGroup(channel,object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                isMaster = true
                Log.d(TAG,"Group started")

            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Create group fail error: $reason")
            }
        })
    }

    override fun onGroupInfoAvailable(group: WifiP2pGroup?) {
        val p2pGroupInfo = WifiP2pGroup()
        if(p2pGroupInfo.isGroupOwner){
            SSID = p2pGroupInfo.networkName
            passPhrase = p2pGroupInfo.passphrase
            BSSID   = p2pGroupInfo.`interface`
            Log.d(TAG,"check interface $SSID  $BSSID  $passPhrase")        }
    }

    fun unregisterP2p(){
        mContext.unregisterReceiver(receiver)
    }

}