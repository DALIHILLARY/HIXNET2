package ug.hix.hixnet2.repository

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ug.hix.hixnet2.database.File
import ug.hix.hixnet2.database.HixNetDatabase

class Repository(val context : Context) {

    private val databaseInstance = HixNetDatabase.dbInstance(context.applicationContext)
    private val fileDao = databaseInstance.fileDao()
    private val wifiDao = databaseInstance.wifiConfigDao()
    private val deviceDao = databaseInstance.deviceNodeDao()

    fun getFiles() : LiveData<List<File>> {
        return fileDao.getAllFiles()
    }

    fun getCIDs() : List<String> {
        return fileDao.gelAllCID()
    }

    fun insertFile(file: File){
        GlobalScope.launch {
            fileDao.insertAll(file)
        }
    }

    //fun deleteFile()


}