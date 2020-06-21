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
import android.util.Log
import android.widget.Toast
import com.knexis.hotspot.Hotspot
import com.thanosfisherman.elvis.Main
import ug.hix.hixnet2.MainActivity
import ug.hix.hixnet2.database.WifiConfig
import ug.hix.hixnet2.database.WifiConfigDatabase


class NetworkCardManager(context : Context) {
    //this class will handle all wifi associated operations
    private val mContext = context
    val TAG  = javaClass.simpleName

    private var hadConnection = false
    private var connectingPassed = false
    private var serviceDiscovery = false
    private var scanStarted  = false
    private var BSSID : String? = null
    private var SSID  : String? = null
    private lateinit var wifiScanResults : List<ScanResult>
    private var  netId : Int? = null


    private val mWifiManager = mContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val cm =      mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mWifiConfig   = WifiConfiguration()

    //p2p operations
    private val manager = mContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    val channel =  manager.initialize(mContext,mContext.mainLooper, null)
    val serviceHandler = MeshServiceManager(mContext,manager,channel)

    private val intentFilter = IntentFilter().apply {
        addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        addAction(WifiManager.RSSI_CHANGED_ACTION)
    }

    //wifi Broadcaster
    private val wifiScanReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action){

                WifiManager.RSSI_CHANGED_ACTION -> {
                    val rssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI,1)
                    Log.d(TAG, "new rssi is $rssi")
                }
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED,false)

                    if(success){
                        scanSuccess()
                    }else{
                        scanFailure()
                    }

                }
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    val info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO) as NetworkInfo
                    if (info.isConnected) {
                        hadConnection = true

                        if(!serviceDiscovery){
                            serviceHandler.registerService()
                            serviceDiscovery = true

                        }

                        Log.d(TAG, "CONNECTED to wifi")

                    } else if (info.isConnectedOrConnecting) {
                            connectingPassed = true
                            Log.d(TAG,"is connecting to WIFI")
                        
                    } else {
                        wifiScanner()
//
//                        if(!serviceDiscovery ){
//                            serviceHandler.startServiceDiscovery()
//                            serviceDiscovery = true
//                        }

                        if (hadConnection) {
//                                mConectionState = ConnectManager.ConectionStateDisconnected
                            } else {
//                                mConectionState = ConnectManager.ConectionStatePreConnecting
                            }
                        Log.d(TAG,"Discoonected From WIFI")
                    }

                }

            }

        }
    }

    val registerReceiver = mContext.registerReceiver(wifiScanReceiver,intentFilter)

    fun wifiScanner(){
        scanStarted = mWifiManager.startScan()

        if(!scanStarted){
            scanFailure()
        }
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
        val filteredResults = wifiScanResults.filter { it.SSID.startsWith("DIRECT") or it.SSID.startsWith("HixNet") }

        if(filteredResults.isNotEmpty()){
            //there are Mesh nodes in surrounding thus go into hotspot mode

            /**try to connect to surrounding nodes
             * with the non-p2p nodes as highest priority
             */

            val nonP2pHotspots = filteredResults.filter{it.SSID.startsWith("HixNet")}

            if(nonP2pHotspots.isNotEmpty()){
                val wifiDb = WifiConfigDatabase.dbInstance(mContext).wifiConfigDao()
                nonP2pHotspots.forEach{
                    var (netId,password) = insertNonP2pConfig(it)

                    val wifiConfig = netId?.let { it1 -> WifiConfig(it1,it.SSID,password)} as WifiConfig

                    wifiDb.addConfig(wifiConfig)
                }

                //connect to any of the wifi
                mWifiManager.reconnect()


            }else{
                serviceHandler.startServiceDiscovery()

            }

        }

        if(serviceHandler.p2pSupport){
            serviceHandler.createGroup()
            if(serviceHandler.isMaster){
                serviceHandler.registerService()
            }
        }
    }

   private fun enableWiFi() {
        mWifiManager.isWifiEnabled = true
       Log.d(TAG,"Starting wifi card")
    }

    fun isWiFiEnabled(){
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
        var directWifi : MutableList<ScanResult>? = null

        for(mWifi in wifiScanResults){
            if (mWifi.SSID.startsWith("DIRECT")){
                directWifi?.add(mWifi)
            }
        }
        val maxRssi = directWifi?.maxBy { wifi -> wifi.level }
        Log.d(TAG,"MAXIMUM RSSI" + maxRssi.toString())

        SSID = maxRssi?.SSID
        BSSID = maxRssi?.BSSID

    }



    private fun p2pSupport() : Boolean{
        return mWifiManager.isP2pSupported
    }

    private fun connectPeer(): Boolean{
        mWifiConfig.SSID = String.format("\"%s\"", SSID)
//        mWifiConfig.preSharedKey = String.format("\"%s\"",password)
        mWifiConfig.BSSID      = String.format("\"%s\"",BSSID)
        netId = mWifiManager.addNetwork(mWifiConfig)
        //this.wifiManager.disconnect();
        mWifiManager.enableNetwork(netId!!, false)
        return mWifiManager.reconnect()
    }

    private fun insertNonP2pConfig(device : ScanResult) : Pair<Int?,String> {
        mWifiConfig.SSID = "\"${device.SSID}\""
        val password = device.SSID.drop(6).reversed()
        mWifiConfig.preSharedKey = "\"$password\""
        netId = mWifiManager.addNetwork(mWifiConfig)
        mWifiManager.enableNetwork(netId!!, false)
        return Pair(netId,password)
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
    }

}