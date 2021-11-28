 package ug.hix.hixnet2.meshlink

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.*
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.EXTRA_WIFI_STATE
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.startActivity
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.database.WifiConfig
import ug.hix.hixnet2.licklider.Licklider
import ug.hix.hixnet2.models.*
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.util.AddConfigs


 class   ConnectionMonitor(private val mContext: Context, private val manager: WifiP2pManager, private val channel : WifiP2pManager.Channel, private var licklider : Licklider, private val repo: Repository)  : WifiP2pManager.ConnectionInfoListener, WifiP2pManager.GroupInfoListener{
    private val TAG = javaClass.simpleName
    private val cm = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mWifiManager = mContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val addConfig = AddConfigs(mContext,repo)
     private val job = Job()
//     private val scopeDefault = CoroutineScope(job + newSingleThreadContext("ConnThread"))
     private val scopeDefault = CoroutineScope(job + Dispatchers.Default)
     private val scopeIO = CoroutineScope(job + Dispatchers.IO)
    private val device = runBlocking {repo.getMyDeviceInfo()}
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

    private val isScanning = MutableStateFlow(Pair(false,0L))
    private val serviceRegister = MutableStateFlow(false)
    private val wifiEnabled = MutableStateFlow(false)

    private var ssid : String = ""
    private var bssid : String = ""
    private var passPhrase : String = ""
     private var conSSID : String = ""

    private lateinit var startJob : Job



    private lateinit var wifiScanResults : List<ScanResult>
    private lateinit var filteredResults : List<ScanResult>
    private val intentFilter = IntentFilter().apply {
        addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        addAction(WifiManager.RSSI_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)

    }

    @OptIn(DelicateCoroutinesApi::class)
    private val networkCallback = @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    object: ConnectivityManager.NetworkCallback() {
        @SuppressLint("MissingPermission")
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            val networkCapabilities = cm.getNetworkCapabilities(network)
            val hasInternet = networkCapabilities?.hasCapability(NET_CAPABILITY_INTERNET)
            val hasP2p = networkCapabilities?.hasCapability(NET_CAPABILITY_WIFI_P2P)
            val isWifi = networkCapabilities?.hasTransport(TRANSPORT_WIFI)
            val isVpn = networkCapabilities?.hasTransport(TRANSPORT_VPN)
            val isCellular = networkCapabilities?.hasTransport(TRANSPORT_CELLULAR)
            if (isWifi == true) {
                if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) conSSID = mWifiManager.connectionInfo.ssid
                scopeDefault.launch {
                    sendHello()
                }
                Log.d(TAG, "connected to wifi network")
            }
            if (isVpn == true) {
                Log.e(TAG, "Vpn detected must be switched off")
            }
            if (hasInternet == true) {
//                TODO("CHECK INTERNET WITH RUNTIME PING")
                Log.d(TAG, "internet Connection detected")
            }
            if (hasP2p == true) {
                Log.d(TAG, "P2P support")
            }

        }
//        to be used by android 12 to replace the getConnectionInfo since no support for peer-to-peer
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val transportInfo = networkCapabilities.transportInfo
                if(transportInfo !is WifiInfo)  return
                val wifiInfo : WifiInfo = transportInfo
                conSSID = wifiInfo.ssid
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
                    scopeDefault.launch{
                        if(wifiScanResults.isNotEmpty()) filterResults()
                    }

                }
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
//                    check if wifi is on or off
                    when(intent.getIntExtra(EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED)) {
                        WifiManager.WIFI_STATE_DISABLED -> {
                            Log.d(TAG,"Wifi is disabled")
                        }
                        WifiManager.WIFI_STATE_ENABLED -> {
                            Log.d(TAG,"Wifi is enabled")
                            wifiEnabled.value = true
                        }
                    }
                }
            }
        }

    }
    private suspend fun sendHello() {
        //determine wifi type direct or normal from ssid

        //TODO("CHECK Both ssid and bssid, possible ssid duplicate")
        if(conSSID.startsWith("\"DIRECT") || conSSID.startsWith("\"HIXNET") || conSSID.startsWith("\"kali",true)) {
            val connAddress = repo.getWifiConfigBySsid(conSSID.trim('"'))?.connAddress
            connAddress?.let{
                Log.d(TAG,"connected to host")
                //send all tables to the master device
                val fileSeeders = repo.getAllFileSeeders()
                val fileNames = repo.getAllFileNames()
                val names = repo.getAllNames()
                val devices = repo.getAllDevices()
//                    val bonjour = Command(type = "HELLO",from = device.multicastAddress)
                val bonjour = "HELLO@${device.multicastAddress}"
                licklider.loadData(message = bonjour,toMeshId = connAddress)
                delay(500L)
                try{
                    if(devices.isNotEmpty()) {
                        val sendDevices = devices.mapNotNull {
                            it?.let {
//                                DeviceNode(
//                                    fromMeshID = device.meshID,
//                                    multicastAddress = if (it.device.meshID == device.meshID) device.multicastAddress else {device.meshID},
//                                    connAddress = it.wifiConfig.connAddress,
//                                    meshID = it.device.meshID,
//                                    Hops = it.device.hops,
//                                    macAddress = it.wifiConfig.mac,
//                                    publicKey = it.device.publicKey,
//                                    hasInternetWifi = it.device.hasInternetWifi,
//                                    wifi = it.wifiConfig.ssid,
//                                    passPhrase = it.wifiConfig.passPhrase,
//                                    version = it.device.version,
//                                    status = it.device.status,
//                                    modified = it.device.modified,
//                                    type = "meshHello"
//                                )
                                Gson().toJson(it)
                            }
                        }

                        licklider.loadData(message = ListDeviceNode(sendDevices,"meshHelloList"), toMeshId = connAddress)

                    }
//                        devices.forEach {
//                            it?.let {
//                                Log.e(TAG,"sending hello devices to : $connAddress")
//                                val deviceSend = DeviceNode(
//                                    fromMeshID = device.meshID,
//                                    multicastAddress = if (it.device.meshID == device.meshID) device.multicastAddress else {device.meshID},
//                                    connAddress = it.wifiConfig.connAddress,
//                                    meshID = it.device.meshID,
//                                    Hops = it.device.hops,
//                                    macAddress = it.wifiConfig.mac,
//                                    publicKey = it.device.publicKey,
//                                    hasInternetWifi = it.device.hasInternetWifi,
//                                    wifi = it.wifiConfig.ssid,
//                                    passPhrase = it.wifiConfig.passPhrase,
//                                    version = it.device.version,
//                                    status = it.device.status,
//                                    modified = it.device.modified,
//                                    type = "meshHello"
//                                )
//                                licklider.loadData(message = deviceSend, toMeshId = connAddress)
//                            }
//                        }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the hello devices")
                    e.printStackTrace()
                }
                Log.e(TAG,"Sending file seeders")
                try{
                    if(fileSeeders.isNotEmpty()) {
                        val jiller = fileSeeders
                        val pFileSeeders = fileSeeders.map{ Gson().toJson(it)}
                        licklider.loadData(ListPFileSeeder(pFileSeeders,"fileSeederHelloList"), toMeshId = connAddress)

                    }
//                        fileSeeders.forEach {
//                            Log.e(TAG,"sending hello fileSeeders to : $connAddress")
//                            val pFileSeeder = PFileSeeder(it.CID,it.meshID,it.status,device.meshID,type = "fileSeederHello")
//                            licklider.loadData(pFileSeeder, toMeshId = connAddress)
//                        }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the hello file seeder")
                    e.printStackTrace()
                }
                Log.e(TAG,"SENDING FILE NAMES")
                try{
                    if(fileNames.isNotEmpty()) {
                        val pFileNames = fileNames.map{ Gson().toJson(it)}
                        licklider.loadData(ListPFileName(pFileNames,"fileNameHelloList"), toMeshId = connAddress)

                    }
//                        fileNames.forEach {
//                            Log.e(TAG,"sending hello filenames to : $connAddress")
//                            val pFileName = PFileName(it.CID,it.name_slub,it.status,device.meshID,it.modified,"fileNameHello",it.file_size)
//                            licklider.loadData(pFileName, toMeshId = connAddress)
//                        }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the hello filename ")
                    e.printStackTrace()
                }
                Log.e(TAG,"SENDING NAMES")
                try{
                    if(names.isNotEmpty()) {
                        val pNames = names.map { Gson().toJson(it) }
                        licklider.loadData(ListPName(pNames,"nameHelloList"), toMeshId = connAddress)

                    }
//                        names.forEach {
//                            Log.e(TAG,"sending hello names to : $connAddress")
//                            val pName = PName(it.name, it.name_slub, device.meshID,it.status,type = "nameHello")
//                            licklider.loadData(pName, toMeshId = connAddress)
//                        }
                }catch (e: Throwable) {
                    Log.e(TAG, "Something happened to the hello name")
                    e.printStackTrace()
                }

            }

        }
    }
    @SuppressLint("MissingPermission")
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
        try{
            mContext.unregisterReceiver(meshReceiver)
        }catch (e : IllegalArgumentException) {
            Log.e(TAG,"Mesh Receiver not registered")
        }
    }
    fun wifiScan() {
        Log.d(TAG,"Performing wifi scan")
        while(true) {
            if(mWifiManager.startScan()) {
                Log.d(TAG,"wifi scan successful")
                break
            }else { Log.e(TAG,"Wifi scan failed") }
        }

    }
    private fun enableWiFi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q){
            mWifiManager.isWifiEnabled = true
        }
        else{
            Handler(Looper.getMainLooper()).post{
                val builder = AlertDialog.Builder(mContext)
                builder.setMessage("Please Turn On your wifi")
                    .setCancelable(false)
                    .setPositiveButton("ENABLE"
                    ) { _, _ ->
                        run {
                            val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
                            startActivity(mContext,panelIntent,null)
                        }
                    }
//            .setNegativeButton("No") { dialog, _ -> dialog.cancel() }
                val alert: AlertDialog = builder.create()
                alert.show()
          }

        }
        Log.d(TAG,"Starting wifi card")
    }

    private suspend fun isWiFiEnabled(){
        withContext(Dispatchers.IO) {
            if (hotspotIsEnabled()) {
                val method= mWifiManager.javaClass.getMethod("setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType)
                method.invoke(mWifiManager, null, false)
            }
            if(!mWifiManager.isWifiEnabled){
                enableWiFi()
            }
            delay(2000L)
        }
    }

    fun disableWiFi() {
        mWifiManager.isWifiEnabled = false
        if(isWifiActive()){
            //disable the wifi for good

        }
        Log.d(TAG,"stopping wifi")
    }
    private fun isWifiActive(): Boolean {
        return if (mWifiManager.isWifiEnabled) { // Wi-Fi adapter is ON
            val wifiInfo: WifiInfo = mWifiManager.connectionInfo
            wifiInfo.networkId != -1
            // Connected to access point
        } else {
            false // Wi-Fi adapter is OFF
        }
    }

    private fun hotspotIsEnabled(): Boolean {
        try{
            val method = mWifiManager.javaClass.getMethod("getWifiApState")
            method.isAccessible = true
            return method.invoke(mWifiManager) as Boolean
        }catch (ignore : Throwable) {}
        return false
    }
    private suspend fun filterResults() {
        filteredResults = wifiScanResults.filter { it.SSID.startsWith("DIRECT") or it.SSID.startsWith("HixNet")}
        if(filteredResults.isNotEmpty()){
            Log.d(TAG,"filtered: $filteredResults")
            //there are Mesh nodes in surrounding thus go into hotspot mode
            /**try to connect to surrounding nodes
             * with the non-p2p nodes as highest priority
             */
            val (nonP2pHotspots,p2pHotspots) = filteredResults.partition{it.SSID.startsWith("HixNet")}
            if(nonP2pHotspots.isNotEmpty()){
                Log.d(TAG,"NonP2p: $nonP2pHotspots")
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
            if(p2pHotspots.isNotEmpty()){
                isScanning.value = Pair(false,0L)
                val wifiConfigs = repo.getAllWifiConfig().map{ "${it.ssid}::::${it.mac}" }
                for(device in p2pHotspots.map{"${it.SSID}::::${it.BSSID}"}){
                    if(device !in wifiConfigs){
                        serviceRegister.value =true
                        break
                    }
                }
            }
        }else{
            isScanning.value = Pair(true,15000L * 60)
        }
    }
    private fun createHotspot(ssid : String, password : String) {
        val netConfig = WifiConfiguration()
        netConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN)
        netConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN)
        netConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA)
        netConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
        netConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
        netConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
        netConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
        netConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
    }
    @SuppressLint("MissingPermission")
    private fun serviceDiscovery(terminate : Boolean = false){
        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager.removeServiceRequest(channel,serviceRequest, object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                isDiscovering = false
                if(terminate){
                    serviceKiller = false
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
//                                            TODO("CREATE HOTSPOT HERE")
//                                            hotspot.start(hotspotName,keyPass)
                                            //TODO("GET THE HOTSPOT FEATURE WORKING")
                                            Handler(Looper.getMainLooper()).post {
                                                Toast.makeText(mContext,"Phone doesn't support p2p", Toast.LENGTH_LONG).show()
                                            }
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
            Log.d(TAG,"Service name :   ${record["service"]}  actualName:   $serviceName")
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
            scopeDefault.launch {
                delay(1000)
                deactivateService()  //unregister the service
                serviceKiller = true //terminate service discover
//                val macList = repo.getAllMac()
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
                isScanning.value = Pair(true,15000L * 60)
                serviceRegister.value = false

            }
        }
        manager.setDnsSdResponseListeners(channel, serviceListener, txtListener)
    }
    @SuppressLint("MissingPermission")
    private suspend fun registerService(){
        val device = repo.getMyDeviceInfo()
        val record : Map<String, String> = mapOf(
            "service" to serviceName,
            "connectInfo" to "$ssid::$bssid::$passPhrase::${device.multicastAddress}::${device.meshID}",
            "badMulticastAddresses" to Generator.getBadMultiAddress(mContext)
        )
        //clear any old registered services
        deactivateService()
        delay(1000L)
        var registerSuccess = false
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(ssid,"_TTP._udp",record)
        manager.addLocalService(channel,serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                if(!isDiscovering){
                    registerSuccess = true
                }
                Log.d(TAG,"Service Started successfully")
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "Failed to Create service ERROR: $reason")
            }
        })
        delay(2000L)
        if(registerSuccess) startServiceDiscovery()

    }
    private suspend fun startServiceDiscovery(){
        serviceKiller = false

        coroutineScope{
            launch {
                while(isActive){
                    if(serviceKiller){
                        serviceDiscovery(true)
                        break
                    }
                    serviceDiscovery()
                    delay(2000L)
                }
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
                scopeDefault.launch {
                    delay(1000L)
                    manager.requestGroupInfo(channel,this@ConnectionMonitor)
                    Log.d(TAG,"Group started")
                }

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
        scopeDefault.launch {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                unregisterNetworkCallback()
            unregisterWifiBroadcast()
            removeGroup()
            serviceKiller = true
            isScanning.value = Pair(false,0L)
            serviceRegister.value = false
            if (startJob.isActive) startJob.cancel()
        }
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
//        TODO("Not yet implemented")
    }

    suspend fun start() {
        isWiFiEnabled()
        //register broadcast if not registered
        if(!isWifiRegistered){
            registerWifiBroadcast()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                registerNetworkCallback()
            }
        }
        startJob = scopeDefault.launch(Dispatchers.IO){
            while(isActive){
                Log.d(TAG,"ISmASTER: $isMaster")
                if(isMaster) break
                createGroup()
                delay(2000L)
            }
            //set up service listeners
            if(!setResponders){
                setResponders = true
                setupDnsResponders()
                delay(1000L)

            }
            var wifiScanJob : Job? = null
            var serviceRegisterJob: Job? = null
            wifiEnabled.asStateFlow().collect{status ->
                if(status){
                    //                            TODO("fix the peer discovery algorithm")

                    launch{
                        isScanning.asStateFlow().collect{
                            Log.d(TAG,"WIFI scan channel command: ${it.first}  time: ${it.second}")
                            wifiScanJob?.let { job ->
                                if(job.isActive) job.cancel()
                            }
                            if(it.first)
                                wifiScanJob = scopeDefault.launch {
                                    while(isActive){
                                        wifiScan()
                                        delay(it.second)
                                    }
                                }
                        }
                    }
                    delay(1000L) //wait for scan channel to open
                    isScanning.value = Pair(true, 60 * 4000L) //scan after 4min
                    launch{
                        serviceRegister.asStateFlow().collect {
                            Log.d(TAG,"Service register channel command: $it")
                            serviceRegisterJob?.let{ job ->
                                if(job.isActive) job.cancel()
                            }
                            if(it)
                                serviceRegisterJob = scopeDefault.launch {
                                    try{
                                        withTimeout( 2* 60* 1000L){
                                            registerService()
                                        }
                                    }catch(e: TimeoutCancellationException){
                                        Log.d(TAG,"register && discover service timeout")
                                        serviceKiller = true
                                        deactivateService()
                                        isScanning.value = Pair(true,15000L * 60) //scan after 15min

                                    }

                                }
                        }
                    }
                }
            }

        }

    }

    override fun onGroupInfoAvailable(group: WifiP2pGroup?) {
        if(group != null){
            if(group.isGroupOwner){
                isMaster = true
                ssid = group.networkName
                passPhrase = group.passphrase
                bssid   = device.mac
                val multicastAddress = runBlocking{
                    repo.addWifiConfig(WifiConfig(device.meshID,ssid = ssid,mac = bssid,passPhrase = passPhrase,connAddress = device.multicastAddress))
                    repo.getMyDeviceInfo().multicastAddress
                }
                if(Licklider.receiverJob != null && Licklider.receiverJob!!.isActive) {
                     Licklider.receiverJob!!.cancel()

                }
                licklider = Licklider(mContext,repo)
                Licklider.receiverJob = licklider.receiver(multicastAddress)
            }
            Log.d("onCreateListener","group available")

        }else{
            Log.d("onCreateListener","No group available")
        }

    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> runNonBlocking(block: suspend CoroutineScope.() -> T) : T {
        var finished = false
        var result : T? = null
        val job = scopeDefault.launch {
            result = block()
            finished = true
        }
        while(!finished);
        return result as T
    }
    companion object {
        var isConnected = false //check if connected to  any wifi
        @SuppressLint("StaticFieldLeak")
        private var instance: ConnectionMonitor? = null
        private var channel: WifiP2pManager.Channel? = null
        fun getInstance(context : Context, manager : WifiP2pManager, channel : WifiP2pManager.Channel, licklider : Licklider, repo : Repository) : ConnectionMonitor {
            if(instance ==  null) {
                instance = ConnectionMonitor(context,manager,channel,licklider,repo)
            }
            return instance as ConnectionMonitor
        }
        fun getChannelInstance(context : Context, manager: WifiP2pManager) : WifiP2pManager.Channel{
            if(channel == null){
                channel = manager.initialize(context,context.mainLooper,null)
            }
            return channel as WifiP2pManager.Channel
        }
    }
}