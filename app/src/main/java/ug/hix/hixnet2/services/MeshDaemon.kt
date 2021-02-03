package ug.hix.hixnet2.services

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.Dispatchers

import ug.hix.hixnet2.HomeActivity
import ug.hix.hixnet2.R
import ug.hix.hixnet2.meshlink.NetworkCardManager
import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.util.NotifyChannel
import java.util.jar.Attributes
import kotlin.concurrent.thread

class MeshDaemon : LifecycleService() {
    private lateinit var cardManager : NetworkCardManager
    private lateinit var manager : WifiP2pManager
    private lateinit var channel : WifiP2pManager.Channel
    private lateinit var repo : Repository

    val TAG = javaClass.simpleName

    override fun onCreate() {
        super.onCreate()
        repo = Repository(applicationContext)
        val deviceInfo = repo.getMyDeviceInfo()


        device = DeviceNode(
            meshID = deviceInfo.meshID,
            macAddress = deviceInfo.macAddress,
            multicastAddress = deviceInfo.multicastAddress,
            hasInternetWifi = false,
            Hops = 0
        )

        manager = applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel =  NetworkCardManager.getChannelInstance(applicationContext,manager)
        cardManager = NetworkCardManager.getNetworkManagerInstance(this,manager, channel, Dispatchers.Default)
        thread {
//            val uploadedfiles = repo.getAllFiles()
//
//            uploadedfiles.forEach { file ->
//                val fileAttribute = mutableMapOf<String,MutableList<String>>()
//                fileAttribute["Name"] = mutableListOf(file.cloudName)
//                fileAttribute["Seeders"] = mutableListOf(device.meshID)
//                fileAttribute["Size"] = mutableListOf(file.size.toString())
//                fileAttribute["Date"] = mutableListOf(file.modified)
//                fileAttribute["Extension"] = mutableListOf(file.extension)
//
//                filesHashMap[file.CID] = fileAttribute
//
//            }
            val netIds = repo.getAllWifiNetIds()
            netIds.forEach { netId ->
                cardManager.mWifiManager.enableNetwork(netId,false)
            }
            cardManager.mWifiManager.reconnect()
        }


    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        thread {
            isServiceRunning = true


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
                .setOngoing(true)
                .build()


//            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(23256, notification)
//            }else{
//                with(NotificationManagerCompat.from(this)){
//                    notify(23256,notification)
//                }*
//            }

            cardManager.isWiFiEnabled()

        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        cardManager.stop()

        thread {
            val netIds = repo.getAllWifiNetIds()
            netIds.forEach { netId ->
                cardManager.mWifiManager.disableNetwork(netId)
            }
        }

    }


    companion object {
        var isServiceRunning = false
        lateinit var device : DeviceNode
        var filesHashMap = mutableMapOf<String,MutableMap<String,MutableList<String>>>()

        fun startService( context: Context){
            if(!isServiceRunning){
                isServiceRunning = true
                val startIntent = Intent(context, MeshDaemon::class.java)
                ContextCompat.startForegroundService(context,startIntent)
            }
        }

        fun stopService(context: Context){
            isServiceRunning = false
            val stopIntent = Intent(context, MeshDaemon::class.java)
            context.stopService(stopIntent)
        }
    }
}