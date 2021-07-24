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
import ug.hix.hixnet2.models.DeviceNode
import ug.hix.hixnet2.models.PFileName
import ug.hix.hixnet2.models.PFileSeeder
import ug.hix.hixnet2.models.PName
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.util.NotifyChannel
import kotlin.concurrent.thread

class MeshDaemon : LifecycleService() {
    @ExperimentalCoroutinesApi
    private lateinit var connMonitor : ConnectionMonitor
    private lateinit var manager : WifiP2pManager
    private lateinit var channel : WifiP2pManager.Channel
    private lateinit var repo : Repository
    private lateinit var meshDaemon: Job
   

    val TAG = javaClass.simpleName
    var stopScan = false

    @ExperimentalCoroutinesApi
    override fun onCreate() {
        super.onCreate()
        repo = Repository.getInstance(applicationContext)
        manager = applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel =  ConnectionMonitor.getChannelInstance(applicationContext,manager)
        connMonitor = ConnectionMonitor.getInstance(this,manager,channel)
//        cardManager = NetworkCardManager.getNetworkManagerInstance(this,manager, channel, Dispatchers.Default)

    }

    @FlowPreview
    @ExperimentalCoroutinesApi
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        thread{
            val deviceInfo = runBlocking { repo.getMyDeviceInfo() }
            device = DeviceNode(
                meshID = deviceInfo.meshID,
                multicastAddress = deviceInfo.multicastAddress,
                hasInternetWifi = false,
                Hops = 0
            )
            val licklider = Licklider.start(this@MeshDaemon)
            meshDaemon = GlobalScope.launch(Dispatchers.Default){
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

                launch {
                    connMonitor.start()
                }

                //coroutine for new device and cloud file listeners
                launch {

                    /*
                    * CLoud file listeners
                    * listen for name changes, seeders a
                     */
                    try {
                        repo.getUpdatedNameFlow().collect { name ->
                            name?.let {
                                Log.e(TAG,"sending update names to except: ${it.modified_by}")
                                val pName = PName(it.name, it.name_slub, device.meshID,it.status,it.modified,type="nameUpdate")
                                licklider.loadData(pName,it.modified_by)
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Something happened to the name listener",e)
                    }
                }
                launch {
                    try {
                        repo.getUpdatedFileNameFlow().collect { filename ->
                            filename?.let {
                                Log.e(TAG,"sending update file names to except: ${it.modified_by}")
                                val pFileName = PFileName(it.CID,it.name_slub,it.status,
                                    device.meshID,it.modified,"fileNameUpdate",it.file_size)
                                licklider.loadData(pFileName,it.modified_by)
                            }
                        }

                    } catch (e: Throwable) {
                        Log.e(TAG, "Something happened to the filename listener",e)
                    }
                }
                launch {
//                fileSeeder listener
                    try {
                        repo.getUpdatedFileSeederFlow().collect { fileSeeder ->
                            fileSeeder?.let {
                                Log.e(TAG,"sending update fileSeeders to except: ${it.modified_by}")
                                val pFileSeeder = PFileSeeder(it.CID,it.meshID,it.status, device.meshID,it.modified,type = "fileSeederUpdate")
                                licklider.loadData(pFileSeeder,it.modified_by)
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Something happened to the fileSeeder listener",e)
                    }
                }
                launch {
//                new device listener
                    try {
                        repo.getUpdatedDeviceFlow().collect {
                            it?.let{
                                Log.e(TAG,"sending update devices to except: ${it.device.meshID}")
                                val deviceSend = DeviceNode(
                                    fromMeshID = MeshDaemon.device.meshID,
                                    meshID = it.device.meshID,
                                    multicastAddress = if (it.device.meshID == MeshDaemon.device.meshID) MeshDaemon.device.multicastAddress else {MeshDaemon.device.meshID},
                                    Hops = it.device.hops,
                                    macAddress = it.wifiConfig.mac,
                                    publicKey = it.device.publicKey,
                                    hasInternetWifi = it.device.hasInternetWifi,
                                    wifi = it.wifiConfig.ssid,
                                    passPhrase = it.wifiConfig.passPhrase,
                                    version = it.device.version,
                                    status = it.device.status,
                                    connAddress = it.wifiConfig.connAddress,
                                    modified = it.device.modified,
                                    type = "meshUpdate"
                                )
                                Log.d(TAG, "New device detected: $it")
                                licklider.loadData(message = deviceSend, toMeshId = it.device.meshID)
                                if (it.device.status == "DISCONNECTED") repo.deleteDevice(it.device)

                            }

                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Something happened to the device listener",e)
                    }
                }

                launch {
                    repo.getNewAddressFlow().collect {
                        Log.d(TAG,"New address: $it")
                        Licklider.receiverJob = licklider.receiver(it)
                    }
                }

            }
        }
        return START_STICKY
    }

    @ExperimentalCoroutinesApi
    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        meshDaemon.cancelChildren()
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