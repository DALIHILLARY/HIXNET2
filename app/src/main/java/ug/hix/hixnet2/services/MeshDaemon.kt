package ug.hix.hixnet2.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.os.IBinder
import ug.hix.hixnet2.meshlink.MeshServiceManager
import ug.hix.hixnet2.meshlink.NetworkCardManager
import kotlin.concurrent.thread

class MeshDaemon : Service() {
    private lateinit var cardManager : NetworkCardManager
    private lateinit var manager : WifiP2pManager
    private lateinit var channel : WifiP2pManager.Channel
    private lateinit var serviceHandler : MeshServiceManager


    override fun onCreate() {
        super.onCreate()

        //var deviceInstance = DeviceNode()
        val mContext : Context = this
        val TAG = javaClass.simpleName

        cardManager = NetworkCardManager(this)
        manager = mContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel =  manager.initialize(this,this.mainLooper, null)
        serviceHandler = MeshServiceManager(this,manager,channel)


    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        //execute on separate thread heavy tasks ahead
        thread{
            cardManager.isWiFiEnabled()

        }
        return START_STICKY

    }

    override fun onDestroy() {
        super.onDestroy()
        cardManager.disableWiFi()
        cardManager.unregisterCard()
        serviceHandler.unregisterP2p()
    }

    override fun onBind(intent: Intent?): IBinder? {

        return null
    }

    fun startComponents(){
        //to be written
    }
}