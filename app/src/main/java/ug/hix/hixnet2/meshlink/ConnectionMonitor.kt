package ug.hix.hixnet2.meshlink

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities.*
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.startActivity
import com.knexis.hotspot.Hotspot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.licklider.Licklider
import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.models.PFileName
import ug.hix.hixnet2.models.PFileSeeder
import ug.hix.hixnet2.models.PName
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.services.MeshDaemon
import ug.hix.hixnet2.util.AddConfigs
import java.lang.Thread.sleep
import kotlin.coroutines.CoroutineContext

class ConnectionMonitor(private val mContext: Context, private val manager: WifiP2pManager, private val channel : WifiP2pManager.Channel)  : WifiP2pManager.ConnectionInfoListener, WifiP2pManager.GroupInfoListener{
    private val TAG = javaClass.simpleName
    private val cm = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mWifiManager = mContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val hotspot = Hotspot(mContext.applicationContext)
    private val repo = Repository.getInstance(mContext)
    private val addConfig = AddConfigs(mContext,repo)
    private  var foundDevices = mutableMapOf<String,String>()
    private val serviceName =  "kil3rH1xNet2"
    private var isWifiRegistered = false
    private var isDiscovering = false
    private var setResponders = false
    private var serviceKiller = false
    private var isMaster      = false
    private var LAST_CONNECT_TIMESYNC = 0L
    private var LAST_DISCON_TIMESYNC = 0L
    private val SYNCTIME = 500L


    private var ssid : String = ""
    private var bssid : String = ""
    private var passPhrase : String = ""

    private lateinit var wifiScanResults : List<ScanResult>
    private lateinit var filteredResults : List<ScanResult>
    private val intentFilter = IntentFilter().apply {
        addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        addAction(WifiManager.RSSI_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)

    }

    private val networkCallback = @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    object: ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            val networkCapabilities = cm.getNetworkCapabilities(network)
            val hasInternet = networkCapabilities?.hasCapability(NET_CAPABILITY_INTERNET)
            val hasP2p = networkCapabilities?.hasCapability(NET_CAPABILITY_WIFI_P2P)
            val isWifi = networkCapabilities?.hasTransport(TRANSPORT_WIFI)
            val isVpn = networkCapabilities?.hasTransport(TRANSPORT_VPN)
            val isCellular = networkCapabilities?.hasTransport(TRANSPORT_CELLULAR)
            if(isWifi == true) {
                sendHello()
                Log.d(TAG,"connected to wifi network")
            }
            if(isVpn == true) {
                Log.e(TAG,"Vpn detected must be switched off")
            }
            if(hasInternet == true) {
                Log.d(TAG,"internet Connection detected")
            }
            if(hasP2p == true) {
                Log.d(TAG,"P2P support")
            }

        }

        override fun onLost(network: Network) {
            super.onLost(network)
        }

    }
    private val meshReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when(intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    // Determine if Wifi P2P mode is enabled or not

                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if( state  != WifiP2pManager.WIFI_P2P_STATE_ENABLED){
                        Log.v(TAG,"P2P is disabled")
                    }
                }
                WifiManager.RSSI_CHANGED_ACTION -> {
                    val rssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI,1)
                    if (rssi < -90){
                        //TODO("send about to disconnect event")
                        Log.i(TAG,"connection is very weak")

                    }
                    Log.d(TAG, "new rssi is $rssi")
                }
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                    } else {
                        intent.getBooleanExtra("resultsUpdated", false)
                    }
                    if(success)
                        Log.d(TAG,"wifi scan success")
                    else
                        Log.e(TAG,"wifi Scan Failed")
                    wifiScanResults = mWifiManager.scanResults
                    if(wifiScanResults.isNotEmpty()) filterResults()

                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                        val info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO) as NetworkInfo
                        if (info.isConnected) {
                            if(System.currentTimeMillis() - LAST_CONNECT_TIMESYNC >= SYNCTIME){
                                LAST_CONNECT_TIMESYNC = System.currentTimeMillis()
                                sendHello()
                                Log.d(TAG, "CONNECTED to wifi")
                            }


                        } else if(info.detailedState == NetworkInfo.DetailedState.DISCONNECTED) {
                            if(System.currentTimeMillis() - LAST_DISCON_TIMESYNC >= SYNCTIME){
                                LAST_DISCON_TIMESYNC = System.currentTimeMillis()

                                mWifiManager.reconnect() //try reconnecting to wifi
                                Log.d(TAG,"Disconnected From WIFI")

                            }

                        }
                    }
                }
            }
        }

    }
    private fun sendHello() {
        //determine wifi type direct or normal fro ssid
        val conSSID = mWifiManager.connectionInfo.ssid

        if(conSSID.startsWith("DIRECT") || conSSID.startsWith("HIXNET")) {
            val connAddress = repo.getWifiConfigBySsid(conSSID).connAddress

            GlobalScope.launch {
                val licklider = Licklider.start(mContext)
                licklider.loadData("HELLO",connAddress)

                //send all tables to the master device
                val fileSeeders = repo.getAllFileSeeders()
                val fileNames = repo.getAllFileNames()
                val names = repo.getAllNames()
                val devices = repo.getAllDevices()
                try{
                    devices.forEach {
                        val deviceSend = DeviceNode(
                            meshID = MeshDaemon.device.meshID,
                            peers = listOf(
                                DeviceNode(
                                    meshID = it.device.meshID,
                                    Hops = it.device.hops,
                                    macAddress = it.wifiConfig.mac,
                                    publicKey = it.device.publicKey,
                                    hasInternetWifi = it.device.hasInternetWifi,
                                    wifi = it.wifiConfig.ssid,
                                    passPhrase = it.wifiConfig.passPhrase,
                                    version = it.device.version,
                                    status = it.device.status,
                                    modified = it.device.modified
                                )

                            )
                        )
                        licklider.loadData(message = deviceSend, toMeshId = it.device.meshID)
                    }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the hello devices")
                    e.printStackTrace()
                }
                try{
                    fileSeeders.forEach {
                        val pFileSeeder = PFileSeeder(it.CID,it.meshID,it.status,MeshDaemon.device.meshID)
                        licklider.loadData(pFileSeeder,it.modified_by)
                    }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the hello file seeder")
                    e.printStackTrace()
                }
                try{
                    fileNames.forEach {
                        val pFileName = PFileName(it.CID,it.name_slub,it.status,MeshDaemon.device.meshID)
                        licklider.loadData(pFileName,it.modified_by)
                    }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the hello filename ")
                    e.printStackTrace()
                }
                try{
                    names.forEach {
                        val pName = PName(it.name, it.name_slub, MeshDaemon.device.meshID,it.status)
                        licklider.loadData(pName,it.modified_by)
                    }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the hello name")
                    e.printStackTrace()
                }

            }
        }
//                                else{
//                                    hasInternet = true
//                                }
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun registerNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .build()
        cm.registerNetworkCallback(networkRequest,networkCallback)
    }
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun unregisterNetworkCallback() {
        cm.unregisterNetworkCallback(networkCallback)
    }

    private fun registerWifiBroadcast(){
        isWifiRegistered = true
        mContext.registerReceiver(meshReceiver,intentFilter)
    }
    private fun unregisterWifiBroadcast() {
        isWifiRegistered = false
        mContext.unregisterReceiver(meshReceiver)
    }
    fun wifiScan() {
        mWifiManager.startScan()
    }
    private fun enableWiFi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            mWifiManager.isWifiEnabled = true
        else{
            val dialog = Dialog(mContext)
            dialog.setTitle("Please switch on wifi")
            dialog.setCanceledOnTouchOutside(true)
            dialog.show()
            sleep(4000)
            if(dialog.isShowing){
                dialog.dismiss()
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                startActivity(mContext,panelIntent,null)
            }
        }
        Log.d(TAG,"Starting wifi card")
    }

    fun isWiFiEnabled(){
        //register broadcast if not registered
        if(!isWifiRegistered){
            registerWifiBroadcast()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                registerNetworkCallback()
            }

        }

        if (hotspotIsEnabled()) {
            Hotspot(mContext).stop()
        }
        if(!mWifiManager.isWifiEnabled){
            enableWiFi()
        }
        sleep(2000L)
        createGroup()
    }

    fun disableWiFi() {
        mWifiManager.isWifiEnabled = false
        if(isWifiActive()){
            //disable the wifi for good

        }
        Log.d(TAG,"stopping wifi")
    }
    private fun isWifiActive(): Boolean {
        val nwkInterface = cm.activeNetworkInfo
        return nwkInterface != null && nwkInterface.type == ConnectivityManager.TYPE_WIFI
    }

    private fun hotspotIsEnabled(): Boolean {
        return Hotspot(mContext).isON
    }
    private fun filterResults(){
        filteredResults = wifiScanResults.filter { it.SSID.startsWith("DIRECT") or it.SSID.startsWith("HixNet") }
        if(filteredResults.isNotEmpty()){
            //there are Mesh nodes in surrounding thus go into hotspot mode
            /**try to connect to surrounding nodes
             * with the non-p2p nodes as highest priority
             */
            val (nonP2pHotspots,p2pHotspots) = filteredResults.partition{it.SSID.startsWith("HixNet")}
            if(nonP2pHotspots.isNotEmpty()){
                GlobalScope.launch{
                    val macList = repo.getAllMac()
                    var firstDevice = false
                    nonP2pHotspots.forEach{scanResult ->
                        if(!macList.contains(scanResult.BSSID)){
                            addConfig.insertNonP2pConfig(scanResult)
//                            if(!firstDevice){
//                                firstDevice = true
////                                mWifiManager.enableNetwork(netId!!,true)
//                                mWifiManager.reconnect()
//                            }
//                            TODO("FIX CONN ADDrESS BUG IN NONP2P HOTSPOTS 4 INITIAL TRANSACTIONS")
//                            val wifiConfig = netId?.let { _netId -> WifiConfig(_netId,scanResult.SSID,scanResult.BSSID ,password,"")} as WifiConfig
//
//                            repo.addWifiConfig(wifiConfig)
                        }
                    }
                }
            }else{
                if(p2pHotspots.isNotEmpty()){
                    GlobalScope.launch {
                        nonP2pHotspots.forEach {
                            if(!repo.isWifiConfig(it.SSID)){
                                registerService()
                            }
                        }
                    }
                }
            }
        }
    }
    @SuppressLint("MissingPermission")
    private fun serviceDiscovery(terminate : Boolean = false){
        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager.removeServiceRequest(channel,serviceRequest, object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                isDiscovering = false
                if(terminate){
                    return
                }
                Log.d(TAG,"successfully removed service request")
                manager.addServiceRequest(channel,serviceRequest, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.d(TAG,"Service request successfully added")
                        manager.discoverServices(
                            channel,
                            object : WifiP2pManager.ActionListener {
                                override fun onSuccess() {
                                    isDiscovering = true
//                                    Log.d(TAG,"serviceDiscover initiated")
                                }
                                override fun onFailure(code: Int) {
                                    when (code) {
                                        WifiP2pManager.P2P_UNSUPPORTED -> {
//                                            Toast.makeText(mContext,"P2p not supported",Toast.LENGTH_SHORT).show()
                                            Log.e(TAG, "P2P isn't supported on this device.")
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
    private fun setupDnsResponders(){
        val serviceListener = WifiP2pManager.DnsSdServiceResponseListener { instanceName, _, _ ->
            Log.d(TAG, "Found Device  $instanceName")
        }
        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { _, record, _ ->
            if(record["service"] == serviceName){
                val result = record["connectInfo"]
                val scanMulticastAddresses = record["badMulticastAddresses"]

                if(!result.isNullOrEmpty()){
                    val connectInfo = record["connectInfo"]!!.split("::")
                    val bssid = connectInfo[1]
                    foundDevices[bssid] = "$result@$scanMulticastAddresses"
                }
                Log.d(TAG,"Found Mesh Device $result")
            }

            //self terminate responders on time expire
            GlobalScope.launch {
                delay(1000)
                deactivateService()  //unregister the service
                serviceKiller = true //terminate service discover
                val macList = repo.getAllMac()
                val devices = foundDevices.toList()
//                var connecting = false
                devices.forEach{
                    val payload = it.second.split("@")
                    val connectInfo = payload[0].split("::")
                    val scanAddresses = payload[1]

                    if(!repo.isWifiConfig(connectInfo[0])){
                        addConfig.insertP2pConfig(connectInfo)
                        Generator.getMultiAddress(mContext,scanAddresses)
//                        if(device.multicastAddress == Generator.getMultiAddress(mContext,scanAddresses) && !connecting){
//                            connecting = true
//                            mWifiManager.reconnect()
//                        }

                    }
                }
                foundDevices.clear()

            }
        }
        manager.setDnsSdResponseListeners(channel, serviceListener, txtListener)
    }
    @SuppressLint("MissingPermission")
    private fun registerService(){
        val record = mutableMapOf<String,String>()
        val device = repo.getMyDeviceInfo()
        record["service"] = serviceName
        record["connectInfo"] = "$ssid::$bssid::$passPhrase::${device.multicastAddress}::${device.meshID}"
        record["badMulticastAddresses"] = Generator.getBadMultiAddress(mContext)
        //clear any old registered services
        deactivateService()
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(ssid,"_TTP._udp",record)
        manager.addLocalService(channel,serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG,"Service Started successfully")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to Create service ERROR: $reason")
            }
        })
//        TODO("AND time out to this")
        if(!isDiscovering){
            startServiceDiscovery()
        }
    }
    private fun startServiceDiscovery(){
        serviceKiller = false

        //set up service listeners
        if(!setResponders){
            setResponders = true
            setupDnsResponders()

        }

        GlobalScope.launch {
            while(true){
                if(serviceKiller){
                    serviceDiscovery(true)
                    break
                }
                serviceDiscovery()
                delay(2000L)
            }
        }

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
                Log.e(TAG, "Failed to remove service")
            }

        })
    }
    @SuppressLint("MissingPermission")
    fun createGroup(){
        removeGroup()
        manager.createGroup(channel,object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                isMaster = true
                sleep(1000)
                manager.requestGroupInfo(channel,this@ConnectionMonitor)
                Log.d(TAG,"Group started")

            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Create group fail error: $reason")
            }
        })
    }
    private fun removeGroup(){
        manager.removeGroup(channel,object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                isMaster = false
            }
            override fun onFailure(reason: Int) {
            }

        })
    }
    fun stop(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            unregisterNetworkCallback()
        unregisterWifiBroadcast()
        removeGroup()
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
//        TODO("Not yet implemented")
    }

    @ExperimentalCoroutinesApi
    override fun onGroupInfoAvailable(group: WifiP2pGroup?) {
        if(group != null){
            if(group.isGroupOwner){
                ssid = group.networkName
                passPhrase = group.passphrase
                bssid   = Repository.getInstance(mContext).getMyDeviceInfo().mac
//                Licklider.start(mContext).receiver(device.multicastAddress)
            }
            Log.d("OncreateListner","group available")

        }else{
            Log.d("OncreateListner","No group available")
        }
    }
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: ConnectionMonitor? = null
        fun getInstance(context : Context, manager: WifiP2pManager, channel: WifiP2pManager.Channel) : ConnectionMonitor {
            if(instance ==  null) {
                instance = ConnectionMonitor(context,manager,channel)
            }
            return instance as ConnectionMonitor
        }
    }
}