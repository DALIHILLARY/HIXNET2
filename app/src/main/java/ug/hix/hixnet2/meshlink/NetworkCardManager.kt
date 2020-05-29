package ug.hix.hixnet2.meshlink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.util.Log
import ug.hix.hixnet2.interlink.connect.ConnectManager

class NetworkCardManager(context : Context) {
    //this class will handle all wifi asssociated operations
    val mContext = context.applicationContext
    val TAG  = javaClass.simpleName

    public var scanStarted  = false
    private var wifiScanResults : Any? = null

    val wifiManager = mContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    //wifi Broadcaster
    val wifiScanReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action){
                WifiManager.RSSI_CHANGED_ACTION -> {
                    TODO("GET NEW RSSI VALUE OF CURRENT WIFI")
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
                            hadConnection = true
                            mConectionState = ConnectManager.ConectionStateConnected
                            Log.d(TAG, "CONNECTED")
                        } else if (info.isConnectedOrConnecting) {
                            mConectionState = ConnectManager.ConectionStateConnecting
                        } else {
                            if (hadConnection) {
                                mConectionState = ConnectManager.ConectionStateDisconnected
                            } else {
                                mConectionState = ConnectManager.ConectionStatePreConnecting
                            }
                        }
                    }

                }

            }

        }
    }

    val intentFilter = IntentFilter().apply {
        addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        addAction(WifiManager.RSSI_CHANGED_ACTION)
    }

    val registerReceiver = mContext.registerReceiver(wifiScanReceiver,intentFilter)

    public fun wifiScanner(){
        scanStarted = wifiManager.startScan()

        if(!scanStarted){
            scanFailure()
        }
    }
    private fun scanFailure(){
        Log.d(TAG,"Scan Failed using Previous results")
        wifiScanResults = wifiManager.scanResults
    }
    private fun scanSuccess(){
        Log.d(TAG,"NEW  scan complete results")
        wifiScanResults = wifiManager.scanResults

    }


}