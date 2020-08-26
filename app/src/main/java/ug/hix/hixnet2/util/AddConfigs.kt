package ug.hix.hixnet2.util

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.util.Log
import java.lang.Thread.sleep

class AddConfigs {
    fun insertNonP2pConfig(context : Context, device : ScanResult) : Pair<Int?,String> {
        val mWifiConfig = WifiConfiguration()
        val mWifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        mWifiConfig.SSID = "\"${device.SSID}\""
        val password = device.SSID.drop(6).reversed()
        mWifiConfig.preSharedKey = "\"$password\""
        val netId = mWifiManager.addNetwork(mWifiConfig)

        sleep(2000L)
        mWifiManager.enableNetwork(netId, false)
        Log.d("insertNonP2pConfig","SSID : ${mWifiConfig.SSID} passPhrase : $password netId : $netId")
        return Pair(netId,password)
    }

    fun insertP2pConfig(context : Context, connectInfo : List<String>) : Int {
        val mWifiConfig = WifiConfiguration()
        val mWifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        mWifiConfig.SSID = "\"${connectInfo[0]}\""
        mWifiConfig.BSSID = "\"${connectInfo[1]}\""
        mWifiConfig.preSharedKey = "\"${connectInfo[2]}\""
        val netId = mWifiManager.addNetwork(mWifiConfig)

        sleep(2000L)
        mWifiManager.enableNetwork(netId, false)

        Log.d("insertNonP2pConfig","SSID : ${mWifiConfig.SSID} passPhrase : ${mWifiConfig.preSharedKey} netId : $netId")

        return netId
    }


}