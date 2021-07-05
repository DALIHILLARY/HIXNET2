package ug.hix.hixnet2.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.runBlocking
import ug.hix.hixnet2.database.FileName
import ug.hix.hixnet2.repository.Repository

class CloudFileViewModel : ViewModel() {
    fun getCloudFiles(context: Context) : LiveData<List<FileName>> {
        val repository = Repository.getInstance(context)
        return runBlocking { repository.getCloudFilesLiveData() }
    }

}