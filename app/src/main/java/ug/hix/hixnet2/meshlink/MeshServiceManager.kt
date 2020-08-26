package ug.hix.hixnet2.meshlink

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat

import com.knexis.hotspot.Hotspot

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.thread

import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.database.WifiConfig
import ug.hix.hixnet2.database.HixNetDatabase
import ug.hix.hixnet2.licklider.Licklider
import ug.hix.hixnet2.util.AddConfigs
import java.lang.Exception
import java.lang.Thread.sleep
import java.net.NetworkInterface


open class MeshServiceManager(context : Context, private val manager: WifiP2pManager, private val channel : WifiP2pManager.Channel) : WifiP2pManager.ConnectionInfoListener, WifiP2pManager.GroupInfoListener {

    private  var foundDevices = mutableMapOf<String,String>()
    private val hotspot = Hotspot(context.applicationContext)
    private val mContext = context.applicationContext
    private val serviceName =  "hixNet2"
    private val TAG = javaClass.simpleName
    private val receiver = MeshBroadcastReceiver(this,manager,channel)
    private val addConfig = AddConfigs()

    private var setResponders = false
    private var serviceKiller = false

    var isServiceStarted = false
    var isP2pRegistered  = false
    var isDiscovering   = false
    var hasWifiInternet  = false
    var isMaster         = false
    var isServerRunning = false
    var p2pSupport       = true
    private var addingToKnown = false


    var SSID : String = ""
    var BSSID : String = ""
    var passPhrase : String = ""
    //var device  = DeviceNode()


    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
    }
  private fun registerP2pBroadcast(){

      mContext.registerReceiver(receiver,intentFilter)
      isP2pRegistered = true

  }
    @SuppressLint("MissingPermission")
    private fun registerService(){

        val record = mutableMapOf<String,String>()
        record["service"] = serviceName
        record["connectInfo"] = "$SSID::$BSSID::$passPhrase"

        val instanceName = "$SSID -> $BSSID->$passPhrase"


        //clear any old registered services
        deactivateService()

        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(instanceName,"_TTP._udp",record)

        manager.addLocalService(channel,serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(mContext,instanceName,Toast.LENGTH_LONG).show()

                Log.d(TAG,"Service Started successfully")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to Create service ERROR: $reason")
            }
        })

        //discover services to counter android bug
        if(!isDiscovering){
            startServiceDiscovery()

        }
    }

    private fun setupDnsResponders(){

        val serviceListener = WifiP2pManager.DnsSdServiceResponseListener { instanceName, _, _ ->

            Log.d(TAG, "Found Device  $instanceName")
        }


        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { _, record, _ ->

            if(record["service"] == serviceName){
                val result = record["connectInfo"]

                if(!result.isNullOrEmpty()){
                    val connectInfo = record["connectInfo"]?.split("::")
                    val BSSID = connectInfo?.get(1)
                    foundDevices[BSSID!!] = result
                }
                Log.d(TAG,"Found Mesh Device $result")

            }

            GlobalScope.launch {

                val dbInstance = HixNetDatabase.dbInstance(mContext)
                val wifiDb = dbInstance.wifiConfigDao()

                val macList = wifiDb.getAllMac()
                val devices = foundDevices.toList()

                devices.forEach{
                    val connectInfo = it.second.split("::")

                    if(!macList.contains(connectInfo[1])){
                        val netId = addConfig.insertP2pConfig(mContext,connectInfo)

                        val wifiConfig = WifiConfig(netId,connectInfo[0],connectInfo[1],connectInfo[2])
                        wifiDb.addConfig(wifiConfig)
                    }
                }

                dbInstance.close()
            }

        }
        manager.setDnsSdResponseListeners(channel, serviceListener, txtListener)


    }
    fun startServiceDiscovery(){
        serviceKiller = false
        if(!isP2pRegistered){
            registerP2pBroadcast()
        }
        //set up service listeners
        if(!setResponders){
            setResponders = true
            setupDnsResponders()

        }

        GlobalScope.launch {
            while(true){
                if(foundDevices.isNotEmpty() || serviceKiller){
                    break
                }
                serviceDiscovery()
                delay(2000L)
            }
        }

    }

    private fun serviceDiscovery(){
        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()

        manager.removeServiceRequest(channel,serviceRequest, object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                isDiscovering = false

                Log.d(TAG,"successfully removed service request")

                manager.addServiceRequest(channel,serviceRequest, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {

                        Log.d(TAG,"Service request successfully added")

                        if (ActivityCompat.checkSelfPermission(
                                mContext,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return
                        }
                        manager.discoverServices(
                            channel,
                            object : WifiP2pManager.ActionListener {
                                override fun onSuccess() {
                                    isDiscovering = true
                                    Log.d(TAG,"serviceDiscover initiated")
                                }

                                override fun onFailure(code: Int) {

                                    when (code) {
                                        WifiP2pManager.P2P_UNSUPPORTED -> {
                                            Toast.makeText(mContext,"P2p not supported",Toast.LENGTH_SHORT).show()
                                            Log.e(TAG, "P2P isn't supported on this device.")

                                            p2pSupport = false
                                            val (hotspotName,keyPass) = Generator.getWifiPassphrase()

                                            hotspot.start(hotspotName,keyPass)
                                            //TODO("GET THE HOTSPOT FEATURE WORKING")
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

                    override fun onFailure(reason: Int) {
                        Log.e(TAG, "Failed to Create service request ERROR: $reason")
                    }
                })


            }

            override fun onFailure(reason: Int) {
                Log.d(TAG, "failed to remove service request")
            }

        })
    }

    private fun discoverPeers(){
        manager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            @SuppressLint("MissingPermission")
            override fun onSuccess() {
                manager.discoverPeers(channel,
                    object : WifiP2pManager.ActionListener{
                        override fun onSuccess() {
                            Log.d(TAG,"Discover peers success")
                        }

                        override fun onFailure(reason: Int) {
                            Log.e(TAG,"Discover Peers failed")
                        }

                    })
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG,"stop peer discovery")
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
          //TODO("Send data about client")
            

        }else{
            if(!isServerRunning){
                isServerRunning = true
                //listen for command and join packets
                thread{
//                    Licklider(mContext).receiver()
                }
            }
        }
    }

    fun createGroup(){
        if(!isP2pRegistered){
            registerP2pBroadcast()
        }
        if (ActivityCompat.checkSelfPermission(
                mContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        manager.createGroup(channel,object : WifiP2pManager.ActionListener{
            @SuppressLint("MissingPermission")
            override fun onSuccess() {
                isMaster = true
                sleep(1000)
                manager.requestGroupInfo(channel,this@MeshServiceManager)

                Log.d(TAG,"Group started")

            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Create group fail error: $reason")
            }
        })
    }

    override fun onGroupInfoAvailable(group: WifiP2pGroup?) {
        if(group != null){
            if(group.isGroupOwner){
                SSID = group.networkName
                passPhrase = group.passphrase
                BSSID   = getMacAddress()

                registerService()
            }
            Log.d("OncreateListner","group available")

        }else{
            Log.d("OncreateListner","No group available")
        }

    }

    private fun getMacAddress() : String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()

            for (nif in interfaces){
                if(nif.name == "wlan0"){
                    val macBytes = nif.hardwareAddress ?: return ""

                    val res1 = StringBuffer()
                    macBytes.forEach {
                        res1.append(String.format("%02X:",it))
                    }

                    if(res1.isNotEmpty()){
                        res1.deleteCharAt(res1.length - 1)
                    }

                    return res1.toString()
                }

            }
        }catch (e :Exception){ }
        return "02:00:00:00:00:00:00"
    }

    fun removeGroup(){
        manager.removeGroup(channel,object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                isMaster = false
                //TODO("Not yet implemented")
            }

            override fun onFailure(reason: Int) {
                //TODO("Not yet implemented")
            }

        })
    }

    fun unregisterP2p(){
        serviceKiller = true
        isP2pRegistered = false
        mContext.unregisterReceiver(receiver)
    }

}