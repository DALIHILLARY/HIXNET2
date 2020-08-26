package ug.hix.hixnet2.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.knexis.hotspot.Hotspot
import io.karn.notify.Notify
import io.karn.notify.NotifyCreator
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ug.hix.hixnet2.HomeActivity
import ug.hix.hixnet2.R
import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.meshlink.MeshServiceManager
import ug.hix.hixnet2.meshlink.NetworkCardManager
import ug.hix.hixnet2.util.NotifyChannel
import kotlin.concurrent.thread

class MeshDaemon : Service() {
    private lateinit var cardManager : NetworkCardManager
    private lateinit var manager : WifiP2pManager
    private lateinit var channel : WifiP2pManager.Channel

    val TAG = javaClass.simpleName
    override fun onCreate() {
        super.onCreate()

        //var deviceInstance = DeviceNode()


    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MeshDaemon.isServiceRunning = true
        manager = applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel =  NetworkCardManager.getChannelInstance(applicationContext,manager)
        cardManager = NetworkCardManager.getNetworkManagerInstance(this,manager, channel)


        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotifyChannel.createNotificationChannel(this)

        }
        val notificationIntent = Intent(this,HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this,0,notificationIntent,0)
        val notification = NotificationCompat.Builder(this,NotifyChannel.CHANNEL_ID)
            .setSmallIcon(R.drawable.spider)
            .setContentTitle("HixNet2 Daemon")
            .setContentText("Mesh Service Running")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(23256, notification)
        }else{
            with(NotificationManagerCompat.from(this)){
                notify(23256,notification)
            }
        }

         //cardManager.startServiceDiscovery()
//        cardManager.createGroup()
        cardManager.isWiFiEnabled()
        return START_STICKY

    }

    override fun onDestroy() {
        super.onDestroy()
        MeshDaemon.isServiceRunning = false
        cardManager.removeGroup()
        cardManager.unregisterCard()
    }

    override fun onBind(intent: Intent?): IBinder? {

        return null
    }

    companion object {
        var isServiceRunning = false
    }
}