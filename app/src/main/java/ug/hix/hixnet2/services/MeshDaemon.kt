package ug.hix.hixnet2.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

import ug.hix.hixnet2.HomeActivity
import ug.hix.hixnet2.R
import ug.hix.hixnet2.licklider.Licklider
import ug.hix.hixnet2.meshlink.ConnectionMonitor
import ug.hix.hixnet2.meshlink.NetworkCardManager
import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.util.NotifyChannel
import kotlin.concurrent.thread

class MeshDaemon : LifecycleService() {
    private lateinit var connMonitor : ConnectionMonitor
    private lateinit var cardManager : NetworkCardManager
    private lateinit var manager : WifiP2pManager
    private lateinit var channel : WifiP2pManager.Channel
    private lateinit var repo : Repository

    val TAG = javaClass.simpleName

    override fun onCreate() {
        super.onCreate()
        repo = Repository.getInstance(applicationContext)
        val deviceInfo = repo.getMyDeviceInfo()


        device = DeviceNode(
            meshID = deviceInfo.meshID,
            multicastAddress = deviceInfo.multicastAddress,
            hasInternetWifi = false,
            Hops = 0
        )

        manager = applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel =  NetworkCardManager.getChannelInstance(applicationContext,manager)
        connMonitor = ConnectionMonitor.getInstance(this,manager,channel)
//        cardManager = NetworkCardManager.getNetworkManagerInstance(this,manager, channel, Dispatchers.Default)

    }

    @ExperimentalCoroutinesApi
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        GlobalScope.launch(Dispatchers.Default){
            isServiceRunning = true


            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                NotifyChannel.createNotificationChannel(this@MeshDaemon)

            }
            val notificationIntent = Intent(this@MeshDaemon,HomeActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this@MeshDaemon,0,notificationIntent,0)
            val notification = NotificationCompat.Builder(this@MeshDaemon,NotifyChannel.CHANNEL_ID)
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

//            cardManager.isWiFiEnabled()
            connMonitor.isWiFiEnabled()
            launch {
                Repository.getInstance(this@MeshDaemon).getNewAddressFlow().collect {
                    Log.d(TAG,"New address: $it")
                    Licklider.start(this@MeshDaemon).receiver(it)
                }
            }
            launch{
                while(true){
                    Log.d(TAG,"wifi scan initiated")
                    connMonitor.wifiScan()
                    delay(4000L)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
//        cardManager.stop()
        connMonitor.stop()
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