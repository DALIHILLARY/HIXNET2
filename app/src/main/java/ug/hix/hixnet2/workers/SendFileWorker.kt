package ug.hix.hixnet2.workers

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.snatik.storage.Storage
import ug.hix.hixnet2.R
import ug.hix.hixnet2.licklider.Licklider
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.services.MeshDaemon
import ug.hix.hixnet2.util.NotifyChannel


class SendFileWorker(private val mContext: Context, params: WorkerParameters) : CoroutineWorker(mContext,params){
    val TAG = "SendFileWorker"
    private val notificationManager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    override suspend fun doWork(): Result {
        return try{
            val repo = Repository.getInstance(applicationContext)
            val licklider = Licklider(mContext, repo)
            val CID = inputData.getString("fileCID")
            val toMeshId = inputData.getString("fromMeshId")
            val expectedOffsets = inputData.getString("offsets")
            val file = CID?.let { repo.getFileByCid(it) }!!
            setForeground(createForegroundInfo())
            if(expectedOffsets == null){
                licklider.loadData(file, toMeshId!!)
            }else{
                val storage = Storage(mContext)
                val cacheDir = mContext.externalCacheDir?.absolutePath
                val offsets = storage.readTextFile("$cacheDir/Acks/${expectedOffsets}.ch")
                    .split(",")
                    .map {
                            it.dropWhile { char ->
                                char.isWhitespace()
                            }.toInt()
                    }
                storage.deleteFile("$cacheDir/Acks/${expectedOffsets}.ch")
                licklider.loadData(file,offsets,toMeshId!!)
            }

//            Licklider(mContext).loadData(file,"dguufggfufvueffdgerge4834yrtr")

            Result.success()

        }catch (e: Exception){
            e.printStackTrace()
            Result.failure()
        }

    }
    private fun createForegroundInfo(): ForegroundInfo {

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotifyChannel.createNotificationChannel(mContext)

        }
//        pending intent to cancel the worker
        val intent = WorkManager.getInstance(mContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(mContext, NotifyChannel.CHANNEL_ID)
            .setSmallIcon(R.drawable.spider)
            .setContentTitle("HixNet2")
            .setContentText("Sending Files")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(R.drawable.ic_cancel_send,"STOP",intent)
            .build()

        return ForegroundInfo(55,notification!!)
    }
}