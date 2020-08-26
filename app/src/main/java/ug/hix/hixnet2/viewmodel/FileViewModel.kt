package ug.hix.hixnet2.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import ug.hix.hixnet2.database.File
import ug.hix.hixnet2.repository.Repository

class FileViewModel : ViewModel() {

    fun getFiles(context: Context) : LiveData<List<File>> {
        val repository = Repository(context)

        return repository.getFiles()
    }
}