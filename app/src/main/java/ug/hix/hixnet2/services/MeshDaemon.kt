package ug.hix.hixnet2.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.os.IBinder
import ug.hix.hixnet2.meshlink.MeshServiceManager
import ug.hix.hixnet2.meshlink.NetworkCardManager
import ug.hix.hixnet2.models.DeviceNode
import java.util.*

class MeshDaemon(context : Context): Service() {
    var deviceInstance = DeviceNode()
    val mContext : Context = context
    val TAG = javaClass.simpleName

    val cardManager = NetworkCardManager(mContext)
    val manager = mContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    val channel =  manager.initialize(mContext,mContext.mainLooper, null)
    var serviceHandler = MeshServiceManager(this,manager,channel)


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        cardManager.isWiFiEnabled()
        Timer().schedule(object : TimerTask(){
            override fun run() {
                serviceHandler.startServiceDiscover()
            }
        },3000)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }
}