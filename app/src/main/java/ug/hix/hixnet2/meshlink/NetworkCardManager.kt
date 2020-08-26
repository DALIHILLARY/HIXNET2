package ug.hix.hixnet2.meshlink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import com.knexis.hotspot.Hotspot
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ug.hix.hixnet2.database.WifiConfig
import ug.hix.hixnet2.database.HixNetDatabase
import ug.hix.hixnet2.util.AddConfigs
import java.lang.Thread.sleep


class NetworkCardManager(context : Context, manager: WifiP2pManager, channel: WifiP2pManager.Channel) : MeshServiceManager(context,manager,channel) {
    //this class will handle all wifi associated operations
    private val mContext = context
    private val TAG = javaClass.simpleName

    private var hadConnection = false
    private var hasInternet = false
    private var connectingPassed = false
    private var serviceDiscovery = false
    private var connecting   = false
    private var scanStarted  = false
    private var lowSignal  = false
    private var isScanning = false
    private var isWifiRegistered = false
    private lateinit var wifiScanResults : List<ScanResult>
    private lateinit var filteredResults : List<ScanResult>
    private var  netId : Int? = null


    private val mWifiManager = mContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cm =      mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mWifiConfig   = WifiConfiguration()
    private val addConfig = AddConfigs()

    private val SYNCTIME = 800L
    private var LAST_CONNECT_TIMESYNC = 0L
    private var LAST_DISCON_TIMESYNC = 0L
    private var LAST_WIFI_SCAN = 0L

    private val intentFilter = IntentFilter().apply {
        addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        addAction(WifiManager.RSSI_CHANGED_ACTION)
        addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_SCREEN_ON)
    }

    //wifi Broadcaster
    private val wifiScanReceiver = object : BroadcastReceiver(){

        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action){


                WifiManager.RSSI_CHANGED_ACTION -> {
                    val rssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI,1)
                    if (rssi < -90){
                        if(!lowSignal and hadConnection){
                            lowSignal = true
                            wifiScanner()
                            //TODO("send about to disconnect event")

                        }
                    }else{
                        lowSignal = false
                    }
                    Log.d(TAG, "new rssi is $rssi")
                }
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    if(System.currentTimeMillis() - LAST_WIFI_SCAN >= 120000L) {
                        LAST_WIFI_SCAN = System.currentTimeMillis()

                        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                        } else {
                            intent.getBooleanExtra("resultsUpdated", false)
                        }
                        if (success) {
                            scanSuccess()
                        } else {
                            scanFailure()
                        }

                    }

                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    val info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO) as NetworkInfo
                    if (info.isConnected) {

                        if(System.currentTimeMillis() - LAST_CONNECT_TIMESYNC >= SYNCTIME){
                            LAST_CONNECT_TIMESYNC = System.currentTimeMillis()
                            hadConnection = true
                            isScanning  = true

                            //determine wifi type direct or normal fro ssid
                            val conSSID = mWifiManager.connectionInfo.ssid

                            if(conSSID.startsWith("DIRECT") || conSSID.startsWith("HIXNET")){

//                                runBlocking {
//                                    launch {
//                                        val stringTest = "am here please".toByteArray()
//                                        while(true){
//                                            Licklider.getInstance(mContext).send(stringTest,"230.123.0.1",33456)
//                                            delay(3000L)
//                                        }
//                                    }
//                                }
                                //TODO("send connect event to master")

                            }else{
                                hasInternet = true
                            }

                            if(!isMaster){
                                createGroup()
                            }

                            Log.d(TAG, "CONNECTED to wifi")
                        }


                    } else if(info.detailedState == NetworkInfo.DetailedState.DISCONNECTED) {
                            if(System.currentTimeMillis() - LAST_DISCON_TIMESYNC >= 120000L){
                                LAST_DISCON_TIMESYNC = System.currentTimeMillis()
                                hasInternet = false
                                hadConnection = false

                                mWifiManager.reconnect() //try reconnecting to wifi
                                wifiScanner()

                                Log.d(TAG,"Disconnected From WIFI")

                            }

                    }

                }

            }

        }
    }

    private fun registerWifiBroadcast(){
        isWifiRegistered = true
        mContext.registerReceiver(wifiScanReceiver,intentFilter)

    }

    private fun wifiScanner(){
        scanStarted = mWifiManager.startScan()

    }
    private fun scanFailure(){
        Log.d(TAG,"Scan Failed using Previous results")
        wifiScanResults = mWifiManager.scanResults

        filterResults()
    }
    private fun scanSuccess(){
        Log.d(TAG,"NEW  scan complete results")
        wifiScanResults = mWifiManager.scanResults

        filterResults()

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
                    val dbInstance = HixNetDatabase.dbInstance(mContext)
                    val wifiDb = dbInstance.wifiConfigDao()

                    val macList = wifiDb.getAllMac()

                    var firstDevice = false
                    nonP2pHotspots.forEach{

                        if(!macList.contains(it.BSSID)){
                            val (netId,password) = addConfig.insertNonP2pConfig(mContext,it)

                            if(!firstDevice){
                                firstDevice = true
                                mWifiManager.enableNetwork(netId!!,true)
                                mWifiManager.reconnect()
                            }

                            val wifiConfig = netId?.let { it1 -> WifiConfig(it1,it.SSID,it.BSSID ,password)} as WifiConfig

                            wifiDb.addConfig(wifiConfig)
                        }

                    }
                    //close db connection
                    dbInstance.close()

                }

            }else{
                if(p2pHotspots.isNotEmpty()){

                    GlobalScope.launch {
                        val dbInstance = HixNetDatabase.dbInstance(mContext)
                        val wifiDb = dbInstance.wifiConfigDao()

                        val macList = wifiDb.getAllMac()

                        nonP2pHotspots.forEach {
                            if (!macList.contains(it.BSSID)) {
                                if(!connecting){
                                    connecting = true
                                    val netId = wifiDb.getDeviceConfigByMac(it.BSSID).netId
                                    mWifiManager.enableNetwork(netId,true)
                                }
                            }else{
                                if(!isDiscovering){
                                    isDiscovering = true
                                    startServiceDiscovery()
                                    sleep(60000L)
                                }

                            }
                        }
                        dbInstance.close()
                    }

                }

            }

        }

        sleep(2000L)
        if(!isMaster){
            createGroup()
        }

    }

   private fun enableWiFi() {
        mWifiManager.isWifiEnabled = true
       Log.d(TAG,"Starting wifi card")
    }

    fun isWiFiEnabled(){
        //register broadcast if not registered
        if(!isWifiRegistered){
            registerWifiBroadcast()
        }

        if (hotspotIsEnabled()) {
            Hotspot(mContext).stop()
        }
        if(!mWifiManager.isWifiEnabled){
            enableWiFi()
        }
    }

    fun disableWiFi() {
        mWifiManager.isWifiEnabled = false
        if(isWifiActive()){
            //disable the wifi for good

        }
        Log.d(TAG,"stopping wifi")
    }

    private fun hotspotIsEnabled(): Boolean {

        return Hotspot(mContext).isON

    }

    fun isWifiActive(): Boolean {
        val nwkInterface = cm.activeNetworkInfo
        return nwkInterface != null && nwkInterface.type == ConnectivityManager.TYPE_WIFI
    }

    private fun getMaxRssi(){
        val directWifi : MutableList<ScanResult>? = null

        for(mWifi in wifiScanResults){
            if (mWifi.SSID.startsWith("DIRECT")){
                directWifi?.add(mWifi)
            }
        }
        val maxRssi = directWifi?.maxBy { wifi -> wifi.level }
        Log.d(TAG,"MAXIMUM RSSI" + maxRssi.toString())

        SSID = maxRssi?.SSID.toString()
        BSSID = maxRssi?.BSSID.toString()

    }



//    private fun p2pSupport() : Boolean{
//
//        return mWifiManager.isP2pSupported
//    }

    private fun connectPeer(): Boolean{
        mWifiConfig.SSID = String.format("\"%s\"", SSID)
//        mWifiConfig.preSharedKey = String.format("\"%s\"",password)
        mWifiConfig.BSSID      = String.format("\"%s\"",BSSID)
        netId = mWifiManager.addNetwork(mWifiConfig)
        //this.wifiManager.disconnect();
        mWifiManager.enableNetwork(netId!!, false)
        return mWifiManager.reconnect()
    }


    private fun disconnectPeer() {

        if (isWifiActive()) {
            mContext.unregisterReceiver(wifiScanReceiver)
            mWifiManager.disconnect()
            netId?.let { mWifiManager.disableNetwork(it) }
            netId?.let { mWifiManager.removeNetwork(it) }

        }
    }
    fun unregisterCard(){
        mContext.unregisterReceiver(wifiScanReceiver)
        unregisterP2p()
        isWifiRegistered = false
    }

    companion object{
        var instance : NetworkCardManager? = null
        var channel : WifiP2pManager.Channel? = null

        fun getNetworkManagerInstance(context : Context, manager: WifiP2pManager, channel: WifiP2pManager.Channel) : NetworkCardManager{
            if(instance == null){
                instance = NetworkCardManager(context,manager, channel)
            }

            return instance as NetworkCardManager
        }
        fun getChannelInstance(context : Context, manager: WifiP2pManager) : WifiP2pManager.Channel{
            if(channel == null){
                channel = manager.initialize(context,context.mainLooper,null)
            }
            return channel as WifiP2pManager.Channel
        }

    }

}