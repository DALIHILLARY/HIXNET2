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
import kotlinx.coroutines.delay
import ug.hix.hixnet2.R
import ug.hix.hixnet2.models.TransFile
import ug.hix.hixnet2.util.NotifyChannel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class MergeFileWorker(private val mContext: Context, params: WorkerParameters) : CoroutineWorker(mContext,params) {
    val TAG = "MergeFileWorker"
    private val notificationManager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    override suspend fun doWork(): Result {
        return try{
            Log.d(TAG,"MERGE WORKER STARTED")
            val storage = Storage(mContext)
            val fileDir = inputData.getString("fileDir")
            val expected = storage.getFiles(fileDir).count() - 1
            setForeground(createForegroundInfo())
            appendFile(fileDir!!,expected,storage)
            val filepath = "$fileDir/${storage.readTextFile("$fileDir/fileName.txt")}".split(":::").first()
            while(true){
                if(storage.rename("$fileDir/1.prt",filepath)) break
            }
            while(true)
                if (storage.deleteFile("$fileDir/fileName.txt")) break
            moveFile(storage, fileDir,filepath)
            Result.success()
        }catch (e: Exception){
            e.printStackTrace()
            Result.failure()
        }

    }
    private fun appendFile(fileDir: String, expected: Int,storage: Storage){
        try{
            val stream = FileOutputStream(File("$fileDir/1.prt"),true)
            var counter = 2
            while (true){
                if(counter > expected) break
                stream.write(storage.readFile("$fileDir/$counter.prt"))
                while(true)
                    if (storage.deleteFile("$fileDir/$counter.prt")) break
                counter +=1
            }
            stream.flush()
            stream.close()
        }catch(e: IOException){
            Log.e(TAG,"Failed to append File")
        }
    }
    private fun moveFile(storage: Storage,directory: String,path: String){
        val docFiles = listOf("docx","doc","xlsx","pdf","xls","pptx","ppt")
        val videoFiles = listOf("mp4","mpeg","mkv","vob")
        val audio = listOf("mp3","aac","wav")
        val thumbnails = listOf("jpg","jpeg","png")
        val archives  = listOf("zip","tar","rar","tz","gz")
        val extension = path.split(".").last()
        val filename = path.split("/").last()
        when (extension.toLowerCase(Locale.ROOT)){
            in docFiles -> {
                if(!storage.isDirectoryExists(storage.externalStorageDirectory+"/HixNet2/Documents")){
                    storage.createDirectory(storage.externalStorageDirectory+"/HixNet2/Documents")
                }
                storage.move(path,"${storage.externalStorageDirectory}/HixNet2/Documents/$filename")
            }
            in videoFiles -> {
                if(!storage.isDirectoryExists(storage.externalStorageDirectory+"/HixNet2/Videos")){
                    storage.createDirectory(storage.externalStorageDirectory+"/HixNet2/Videos")
                }
                storage.move(path,"${storage.externalStorageDirectory}/HixNet2/Videos/$filename")
            }
            in thumbnails -> {
                if(!storage.isDirectoryExists(storage.externalStorageDirectory+"/HixNet2/Images")){
                    storage.createDirectory(storage.externalStorageDirectory+"/HixNet2/Images")
                }
                storage.move(path,"${storage.externalStorageDirectory}/HixNet2/Images/$filename")
            }
            in audio -> {
                if(!storage.isDirectoryExists(storage.externalStorageDirectory+"/HixNet2/Audio")){
                    storage.createDirectory(storage.externalStorageDirectory+"/HixNet2/Audio")
                }
                storage.move(path,"${storage.externalStorageDirectory}/HixNet2/Audio/$filename")
            }
            in archives -> {
                if(!storage.isDirectoryExists(storage.externalStorageDirectory+"/HixNet2/Archives")){
                    storage.createDirectory(storage.externalStorageDirectory+"/HixNet2/Archives")
                }
                storage.move(path,"${storage.externalStorageDirectory}/HixNet2/Archives/$filename")
            }
            else ->{
                if(!storage.isDirectoryExists(storage.externalStorageDirectory+"/HixNet2/Others")){
                    storage.createDirectory(storage.externalStorageDirectory+"/HixNet2/Others")
                }
                storage.move(path,"${storage.externalStorageDirectory}/HixNet2/Others/$filename")


            }
        }
        storage.deleteDirectory(directory)
    }
    private fun createForegroundInfo(): ForegroundInfo {

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            NotifyChannel.createNotificationChannel(mContext)

        }
//        pending intent to cancel the worker
//        val intent = WorkManager.getInstance(mContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(mContext, NotifyChannel.CHANNEL_ID)
            .setSmallIcon(R.drawable.spider)
            .setContentTitle("HixNet2")
            .setContentText("Merging Files")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
//            .addAction(R.drawable.ic_cancel_send,"STOP",intent)
            .build()

        return ForegroundInfo(54,notification!!)
    }
}