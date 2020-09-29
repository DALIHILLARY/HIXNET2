package ug.hix.hixnet2.repository

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import kotlinx.coroutines.*
import ug.hix.hixnet2.database.DeviceNode
import ug.hix.hixnet2.database.File
import ug.hix.hixnet2.database.HixNetDatabase
import ug.hix.hixnet2.database.WifiConfig

class Repository(val context : Context) {

    private val databaseInstance = HixNetDatabase.dbInstance(context.applicationContext)
    private val fileDao = databaseInstance.fileDao()
    private val wifiDao = databaseInstance.wifiConfigDao()
    private val deviceDao = databaseInstance.deviceNodeDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun getFiles() : LiveData<List<File>> {
        return fileDao.getAllFiles()
    }
    fun getFileByCid(cid: String) : File{
        return fileDao.getFileByCid(cid)
    }
    fun getAllFiles() : List<File> {
        return fileDao.getFiles()
    }

    fun getCIDs() : List<String> {
        return fileDao.gelAllCID()
    }

    fun insertFile(file: File){
        scope.launch {
            fileDao.insertAll(file)
        }
    }
    fun insertDevice(device: DeviceNode){
        scope.launch{
            withContext(Dispatchers.IO){
                deviceDao.addDevice(device)
            }
        }
    }

    fun getMyDeviceInfo() : DeviceNode{
        lateinit var deviceInfo: DeviceNode
        runBlocking(Dispatchers.IO) {
            launch {
                deviceInfo = deviceDao.getDeviceInfo()
            }
        }

        return deviceInfo
    }
    fun addWifiConfig(config: WifiConfig){
        return wifiDao.addConfig(config)
    }
    fun getAllWifiConfig() : List<WifiConfig>{
        return wifiDao.getAllConfig()
    }
    fun getAllMac() : List<String> {
        return wifiDao.getAllMac()
    }
    fun getAllWifiNetIds() : List<Int> {
        return wifiDao.getAllNetId()
    }
    fun getWifiConfigBySsid(ssid : String) : WifiConfig {
        return wifiDao.getDeviceConfigBySsid(ssid)
    }
    fun getWifiConfigByMac(mac : String) : WifiConfig {
        return wifiDao.getDeviceConfigByMac(mac)
    }

    //fun deleteFile()


}