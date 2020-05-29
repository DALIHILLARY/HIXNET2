package ug.hix.hixnet2.interlink.connect

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import ug.hix.hixnet2.interlink.salut.Callbacks.SalutCallback
import ug.hix.hixnet2.interlink.salut.Callbacks.SalutDataCallback
import ug.hix.hixnet2.interlink.salut.Salut
import ug.hix.hixnet2.interlink.salut.Salut.*
import ug.hix.hixnet2.interlink.salut.SalutDataReceiver
import ug.hix.hixnet2.interlink.salut.SalutServiceData
import java.util.*

class SwitchConnectionManager : Service() {


    val TAG = javaClass.simpleName

    inner class MySalut(dataReceiver: SalutDataReceiver?, salutServiceData: SalutServiceData?, deviceNotSupported: SalutCallback?) : Salut(dataReceiver, salutServiceData, deviceNotSupported) {
        override fun serialize(o: Any?): String {
            TODO("Not yet implemented")
        }
    }
    val dataReceiver = SalutDataReceiver(applicationContext, SalutDataCallback {

    })

    val instanceName = "Demo ID ${Random().nextInt(200)}"

    val serviceData = SalutServiceData("hixNet2", 50489, instanceName)

    val salut = MySalut(dataReceiver, serviceData, SalutCallback {
        Log.e(TAG, "Device does not support WiFi P2P")
    })

    var isServiceStarted = false

    fun start(){
        if (!isWiFiEnabled(applicationContext)){
            enableWiFi(applicationContext)
            Toast.makeText(this,"switching on wifi",Toast.LENGTH_SHORT)
            salut.createGroup({Toast.makeText(this, "starting hotspot", Toast.LENGTH_SHORT)}, {Toast.makeText(this,"failed to create hotspot",Toast.LENGTH_SHORT)})

        }else{
            //start p2p group
            salut.createGroup({Toast.makeText(this, "starting hotspot", Toast.LENGTH_SHORT)}, {Toast.makeText(this,"failed to create hotspot",Toast.LENGTH_SHORT)})

            if(isWifiActive(applicationContext)){
                salut.startNetworkService({
                    Toast.makeText(this, it.readableName + " connected.", Toast.LENGTH_SHORT).show();
                }, {
                    isServiceStarted = true
                    Log.d(TAG, "Network service started")
                }, {
                    Log.e(TAG, "Can not start network service")
                })
            }
        }
        if(!isServiceStarted){

        }


    }
    //function to implement the switching logic
    fun switcher(){
        salut.startNetworkService(null,null,null)

    }
    inner private class WifiConnectionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          if(intent?.action == WifiManager.NETWORK_STATE_CHANGED_ACTION){
             if(!isWifiActive(applicationContext)){

             }
          }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }


}