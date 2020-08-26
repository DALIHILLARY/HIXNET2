package ug.hix.hixnet2.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.snatik.storage.Storage
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.util.Base58
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class UploadWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext,workerParams) {
    val TAG = javaClass.simpleName
    override fun doWork(): Result {
        val storage = Storage(applicationContext)
        val repo = Repository(applicationContext)
        val CIDs = repo.getCIDs()
        val digest : MessageDigest = MessageDigest.getInstance("SHA-256")
        val filePaths = inputData.getStringArray("filePaths")
        filePaths?.forEach { filepath ->
            val file = File(filepath)
            val name = file.name
            val size = (file.length()/ 1024).toInt() //in kilobytes
            val calender = Calendar.getInstance()
            val format = SimpleDateFormat("yyyy:mm:dd hh:mm:ss")
            val currentTime = format.format(calender.time)

            val fileBytes = storage.readFile(filepath)
            val encodedHash = digest.digest(fileBytes)
            val CID = Base58.encode(encodedHash)

            if(CID !in CIDs){
                val fileObj = ug.hix.hixnet2.database.File(CID,filepath,size,name,currentTime)
                repo.insertFile(fileObj)
                Log.d(TAG,"file added succefully")
            }
        }

        return Result.success()
    }
}