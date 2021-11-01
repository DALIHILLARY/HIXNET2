package ug.hix.hixnet2.util

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ug.hix.hixnet2.database.WifiConfig
import ug.hix.hixnet2.repository.Repository
import java.lang.Thread.sleep
import java.util.*

class AddConfigs(private val context: Context,private val repo: Repository) {
    private lateinit var wifiConfig : WifiConfig
    private val mWifiConfig = WifiConfiguration()
    private val mWifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val TAG = javaClass.simpleName
    fun insertNonP2pConfig(device : ScanResult) {
        GlobalScope.launch(Dispatchers.Default){
            val password = device.SSID.drop(6).reversed()
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
                insert10(context,null,device,null)
                val netId = (1000 + Random().nextInt(1000 -1))
                wifiConfig = WifiConfig(netId = netId,ssid =device.SSID,mac = device.BSSID,passPhrase = password,connAddress = "address",meshID = "testing")

            }else{

                mWifiConfig.SSID = "\"${device.SSID}\""
                mWifiConfig.preSharedKey = "\"$password\""
                mWifiConfig.priority = 99999
                val netId = mWifiManager.addNetwork(mWifiConfig)
                mWifiManager.disconnect()
                mWifiManager.enableNetwork(netId, false)
                mWifiManager.reconnect()
                wifiConfig = WifiConfig(netId = netId,ssid =device.SSID,mac = device.BSSID,passPhrase = password,connAddress = "address",meshID = "testing")
            }
            repo.addWifiConfig(wifiConfig)
            Log.d(TAG,"ADDED NETWORK: $wifiConfig")
        }

    }

    fun insertP2pConfig(connectInfo : List<String>) {
        GlobalScope.launch(Dispatchers.Default){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                insert10(context,connectInfo,null,null)
                val netId = (1000 + Random().nextInt(1000 -1))
                wifiConfig = WifiConfig(netId = netId,ssid = connectInfo[0],mac =connectInfo[1],passPhrase = connectInfo[2],connAddress = connectInfo[3],meshID = connectInfo[4])


            }else{
                mWifiConfig.SSID = "\"${connectInfo[0]}\""
                mWifiConfig.preSharedKey = "\"${connectInfo[2]}\""
                mWifiConfig.priority = 99999
                val netId = mWifiManager.addNetwork(mWifiConfig)
                mWifiManager.disconnect()
                mWifiManager.enableNetwork(netId, false)
                mWifiManager.reconnect()
                wifiConfig = WifiConfig(netId = netId,ssid = connectInfo[0],mac =connectInfo[1],passPhrase = connectInfo[2],connAddress = connectInfo[3],meshID = connectInfo[4])
            }

            repo.addWifiConfig(wifiConfig)
            Log.d(TAG,"ADDED NETWORK: $wifiConfig")

        }

    }
    @SuppressLint("MissingPermission")
    private fun getExistingNetworkId(ssid: String) : Int {
        val configuredNetworks = mWifiManager.configuredNetworks
        configuredNetworks?.forEach {
            if(it.SSID == ssid)
                return it.networkId
        }
        return -1

    }
    fun insertScanConfig(wifiConfig: WifiConfig) {
        GlobalScope.launch(Dispatchers.Default){
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                insert10(context,null,null,wifiConfig)
                val netId = (1000 + Random().nextInt(1000 -1))
                this@AddConfigs.wifiConfig = WifiConfig(netId = netId, ssid = wifiConfig.ssid,mac = wifiConfig.mac,passPhrase = wifiConfig.passPhrase, meshID = wifiConfig.meshID,connAddress = wifiConfig.connAddress)
            }else{
                mWifiConfig.SSID = "\"${wifiConfig.ssid}\""
                mWifiConfig.preSharedKey = "\"${wifiConfig.passPhrase}\""
                mWifiConfig.priority = 99999
                val netId = mWifiManager.addNetwork(mWifiConfig)
                mWifiManager.disconnect()
                mWifiManager.enableNetwork(netId, false)
                mWifiManager.reconnect()

                this@AddConfigs.wifiConfig = WifiConfig(netId = netId, ssid = wifiConfig.ssid,mac = wifiConfig.mac,passPhrase = wifiConfig.passPhrase, meshID = wifiConfig.meshID, connAddress = wifiConfig.connAddress)
            }
            Log.d(TAG,"ADDED NETWORK: ${this@AddConfigs.wifiConfig}")
            repo.addWifiConfig(this@AddConfigs.wifiConfig)
        }
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private  fun insert10(context : Context, connectInfo : List<String>?, device : ScanResult?, qrcodeScan: WifiConfig?){
        val mWifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val myWifiSuggestion = if(connectInfo.isNullOrEmpty()){
            device?.SSID?.drop(6)?.reversed()?.let {
                WifiNetworkSuggestion.Builder()
                    .setSsid(device.SSID!!)
                    .setWpa2Passphrase(it)
                    .setPriority(99999)
                    .build()
            }
        }else if(device != null){
            WifiNetworkSuggestion.Builder()
                .setSsid(connectInfo[0])
                .setWpa2Passphrase(connectInfo[2])
                .setPriority(99999)
                .build()
        }else{
            WifiNetworkSuggestion.Builder()
                .setSsid(qrcodeScan?.ssid!!)
                .setWpa2Passphrase(qrcodeScan.passPhrase)
                .setPriority(99999)
                .build()
        }

        val wifiSuggestions = listOf(myWifiSuggestion)
        val status = mWifiManager.addNetworkSuggestions(wifiSuggestions)
        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Failed to connect to wifi Try Again", Toast.LENGTH_SHORT).show()

            }
        }

//        // Optional (Wait for post connection broadcast to one of your suggestions)
//        val intentFilter = IntentFilter(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)
//
//        val broadcastReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context, intent: Intent) {
//                if (!intent.action.equals(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)) {
//                    return
//                }
//                // do post connect processing here
//            }
//        }
//        context.registerReceiver(broadcastReceiver, intentFilter)

    }

}