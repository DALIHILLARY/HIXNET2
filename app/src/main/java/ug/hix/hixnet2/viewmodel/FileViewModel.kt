package ug.hix.hixnet2.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import droidninja.filepicker.utils.ContentUriUtils
import ug.hix.hixnet2.database.File
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.workers.UploadWorker

class FileViewModel : ViewModel() {

    fun getFiles(context: Context) : LiveData<List<File>> {
        val repository = Repository.getInstance(context)

        return repository.getFilesLiveData()
    }

    fun uploadFile(selectedPaths: ArrayList<Uri>,mContext: Context){
        val filePaths  = arrayListOf<String>()

        selectedPaths.forEach{uri ->
            ContentUriUtils.getFilePath(mContext,uri)?.let { filePaths.add(it) }
        }
        val filepaths = filePaths.toTypedArray()

        val uploadWorker = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf("filePaths" to filepaths))
            .build()

        WorkManager.getInstance(mContext).enqueue(uploadWorker)
    }
}