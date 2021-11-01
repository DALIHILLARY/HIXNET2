package ug.hix.hixnet2.services

import android.app.PendingIntent
import android.app.Service.START_STICKY
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
import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.models.PFileName
import ug.hix.hixnet2.models.PFileSeeder
import ug.hix.hixnet2.models.PName
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.util.NotifyChannel
import kotlin.concurrent.thread

class MeshDaemon : LifecycleService() {
    private lateinit var connMonitor : ConnectionMonitor
    private lateinit var manager : WifiP2pManager
    private lateinit var channel : WifiP2pManager.Channel
    private lateinit var repo : Repository
    private lateinit var licklider: Licklider
    private val job = Job()
    private val meshDaemon = CoroutineScope(job + Dispatchers.IO)


    val TAG = javaClass.simpleName
    var stopScan = false

    override fun onCreate() {
        super.onCreate()
        repo = Repository.getInstance(applicationContext)
        licklider = Licklider.start(this@MeshDaemon, repo)
        manager = applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel =  ConnectionMonitor.getChannelInstance(applicationContext,manager)
        connMonitor = ConnectionMonitor.getInstance(this,manager,channel,licklider,repo)
//        cardManager = NetworkCardManager.getNetworkManagerInstance(this,manager, channel, Dispatchers.Default)

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
            val deviceInfo = runBlocking(Dispatchers.IO) { repo.getMyDeviceInfo() }
            device = DeviceNode(
                meshID = deviceInfo.meshID,
                multicastAddress = deviceInfo.multicastAddress,
                hasInternetWifi = false,
                Hops = 0
            )
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

            startForeground(23256, notification)

            meshDaemon.launch{
                launch {
                    if(isActive) {
                        repo.getNewAddressFlow().collect {
                            mAddress = it
                            Log.d(TAG,"New address: $it")
                            if(Licklider.receiverJob != null && Licklider.receiverJob!!.isActive) {
                                Licklider.receiverJob!!.cancel()
                            }
                            licklider = Licklider.start(this@MeshDaemon, repo)
                            Licklider.receiverJob = licklider.receiver(it)

                        }
                    }
                }
                launch {
                    if(isActive) {
                        connMonitor.start()
                    }
                }

            }

        return START_STICKY
    }

    @ExperimentalCoroutinesApi
    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        meshDaemon.cancel()
//        cardManager.stop()
        connMonitor.stop()
//        thread {
//            val netIds = repo.getAllWifiNetIds()
//            netIds.forEach { netId ->
//                cardManager.mWifiManager.disableNetwork(netId)
//            }
//        }

    }


    companion object {
        var isServiceRunning = false
        lateinit var device : DeviceNode
        var mAddress = ""

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