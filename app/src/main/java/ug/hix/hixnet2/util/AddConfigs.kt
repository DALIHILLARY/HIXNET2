package ug.hix.hixnet2.util

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
import ug.hix.hixnet2.database.WifiConfig
import ug.hix.hixnet2.repository.Repository
import java.lang.Thread.sleep
import java.util.*

class AddConfigs(private val context: Context,private val repo: Repository) {
    lateinit var wifiConfig : WifiConfig
    private val mWifiConfig = WifiConfiguration()
    private val mWifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun insertNonP2pConfig(device : ScanResult) {
        val password = device.SSID.drop(6).reversed()
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            insert10(context,null,device)
            val netId = (1000 + Random().nextInt(1000 -1))
            wifiConfig = WifiConfig(netId = netId,ssid =device.SSID,mac = device.BSSID,passPhrase = password,connAddress = "address",meshID = "testing")

        }else{

            mWifiConfig.SSID = "\"${device.SSID}\""
            mWifiConfig.preSharedKey = "\"$password\""
            val netId = mWifiManager.addNetwork(mWifiConfig)

            sleep(2000L)
            mWifiManager.enableNetwork(netId, false)
            Log.d("insertNonP2pConfig","SSID : ${mWifiConfig.SSID} passPhrase : $password netId : $netId")
            wifiConfig = WifiConfig(netId = netId,ssid =device.SSID,mac = device.BSSID,passPhrase = password,connAddress = "address",meshID = "testing")
        }
        repo.addWifiConfig(wifiConfig)

    }

    fun insertP2pConfig(connectInfo : List<String>) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            insert10(context,connectInfo,null)
            val netId = (1000 + Random().nextInt(1000 -1))
            wifiConfig = WifiConfig(netId = netId,ssid = connectInfo[0],mac =connectInfo[1],passPhrase = connectInfo[2],connAddress = connectInfo[3],meshID = "testing")


        }else{
            mWifiConfig.SSID = "\"${connectInfo[0]}\""
            mWifiConfig.BSSID = "\"${connectInfo[1]}\""
            mWifiConfig.preSharedKey = "\"${connectInfo[2]}\""
            val netId = mWifiManager.addNetwork(mWifiConfig)

            sleep(2000L)
            mWifiManager.enableNetwork(netId, false)

            Log.d("insertNonP2pConfig","SSID : ${mWifiConfig.SSID} passPhrase : ${mWifiConfig.preSharedKey} netId : $netId")
            wifiConfig = WifiConfig(netId = netId,ssid = connectInfo[0],mac =connectInfo[1],passPhrase = connectInfo[2],connAddress = connectInfo[3],meshID = "testing")
        }

        repo.addWifiConfig(wifiConfig)
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private  fun insert10(context : Context, connectInfo : List<String>?, device : ScanResult?){
        Log.d("QRSHOW", "HIT")
        val mWifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val myWifiSuggestion = if(connectInfo.isNullOrEmpty()){
            device?.SSID?.drop(6)?.reversed()?.let {
                WifiNetworkSuggestion.Builder()
                    .setSsid(device.SSID!!)
                    .setWpa2Passphrase(it)
                    .setIsAppInteractionRequired(true) // Optional (Needs location permission)
                    .build()
            }
        }else{
            WifiNetworkSuggestion.Builder()
                .setSsid(connectInfo[0])
                .setWpa2Passphrase(connectInfo[2])
                .setIsAppInteractionRequired(true) // Optional (Needs location permission)
                .build()
        }

        val wifiSuggestions = listOf(myWifiSuggestion)
        val status = mWifiManager.addNetworkSuggestions(wifiSuggestions)
        if (status != WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                Toast.makeText(context, "Failed to connect to wifi Try Again", Toast.LENGTH_SHORT).show()

            }
        }

        // Optional (Wait for post connection broadcast to one of your suggestions)
        val intentFilter = IntentFilter(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)

        val broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!intent.action.equals(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)) {
                    return
                }
                // do post connect processing here
            }
        }
        context.registerReceiver(broadcastReceiver, intentFilter)

    }

}