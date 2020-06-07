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
import android.util.Log
import android.widget.Toast
import com.knexis.hotspot.Hotspot
import com.thanosfisherman.elvis.Main
import ug.hix.hixnet2.MainActivity


class NetworkCardManager(context : Context) {
    //this class will handle all wifi asssociated operations
    val mContext = context
    val TAG  = javaClass.simpleName


    var scanStarted  = false
    var BSSID : String? = null
    var SSID  : String? = null
    private lateinit var wifiScanResults : List<ScanResult>
    private var  netId : Int? = null


    val mWifiManager = mContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val cm =      mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val mWifiConfig   = WifiConfiguration()


    val intentFilter = IntentFilter().apply {
        addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        addAction(WifiManager.RSSI_CHANGED_ACTION)
    }

    //wifi Broadcaster
    val wifiScanReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action){
                WifiManager.RSSI_CHANGED_ACTION -> {
                    var rssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI,1)
                    Log.d(TAG,"new rssi is " + rssi)
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
                    if (info != null) {
                        if (info.isConnected) {
//                            hadConnection = true
//                            mConectionState = ConnectManager.ConectionStateConnected
                            Log.d(TAG, "CONNECTED")
                        } else if (info.isConnectedOrConnecting) {
//                            mConectionState = ConnectManager.ConectionStateConnecting
                        } else {
//                            if (hadConnection) {
//                                mConectionState = ConnectManager.ConectionStateDisconnected
//                            } else {
//                                mConectionState = ConnectManager.ConectionStatePreConnecting
//                            }
                        }
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
    }
    private fun scanSuccess(){
        Log.d(TAG,"NEW  scan complete results")
        wifiScanResults = mWifiManager.scanResults

    }

   private fun enableWiFi() {
        mWifiManager.isWifiEnabled = true
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
    private fun disconnectPeer() {

        if (isWifiActive()) {
            mContext.unregisterReceiver(wifiScanReceiver)
            mWifiManager.disconnect()
            netId?.let { mWifiManager.disableNetwork(it) }
            netId?.let { mWifiManager.removeNetwork(it) }

        }
    }

}