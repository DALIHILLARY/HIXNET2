package ug.hix.hixnet2.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ug.hix.hixnet2.licklider.Licklider
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.services.MeshDaemon
import ug.hix.hixnet2.database.File as DFile


class SendFileWorker(private val mContext: Context, params: WorkerParameters) : Worker(mContext,params){
    val TAG = "SendFileWorker"
    override fun doWork(): Result {
        return try{
            val repo = Repository(applicationContext)
            val CID = inputData.getString("fileCID")
            val file = CID?.let { repo.getFileByCid(it) }!!

            Licklider(mContext).loadData(file,MeshDaemon.device.multicastAddress)
//            Licklider(mContext).loadData(file,"dguufggfufvueffdgerge4834yrtr")

            Result.success()

        }catch (e: Exception){
            e.printStackTrace()
            Result.failure()
        }

    }
}