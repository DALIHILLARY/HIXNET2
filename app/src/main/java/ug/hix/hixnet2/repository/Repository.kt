package ug.hix.hixnet2.repository

import android.content.Context
import androidx.lifecycle.LiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import ug.hix.hixnet2.database.*
import ug.hix.hixnet2.services.MeshDaemon
import ug.hix.hixnet2.util.Util

class Repository(val context : Context) {

    private val databaseInstance = HixNetDatabase.dbInstance(context.applicationContext)
    private val fileDao = databaseInstance.fileDao()
    private val wifiDao = databaseInstance.wifiConfigDao()
    private val deviceDao = databaseInstance.deviceNodeDao()

    fun getFiles() : LiveData<List<File>> {
        return fileDao.getAllFiles()
    }
    fun getFileByCid(cid: String) : File{
        return runBlocking(Dispatchers.IO){fileDao.getFileByCid(cid)}
    }
    fun getAllFiles() : List<File> {
        return runBlocking(Dispatchers.IO){fileDao.getFiles()}
    }
    @ExperimentalCoroutinesApi
    fun getNewCloudFile(): Flow<File> {
        return runBlocking(Dispatchers.IO){
            fileDao.getNewCloudFile().distinctUntilChanged()
        }
    }

    fun getCIDs() : List<String> {
        return runBlocking(Dispatchers.IO){fileDao.gelAllCID()}
    }
    fun deleteFile(file: File){
        runBlocking(Dispatchers.IO){
            fileDao.deleteFileSeeder(FileSeeder(file.CID,MeshDaemon.device.meshID))
            fileDao.delete(file)
        }
    }

    fun getLink(meshId: String) : Triple<String,String,Int> {
        return runBlocking(Dispatchers.IO) {
            val minHop = deviceDao.getMinHop(meshId)
            minHop?.let {
                val link = deviceDao.getLink(meshId,it).map { device ->
                    Triple(device.multicastAddress,device.iface,device.hops) }.first()
                if(link.first.contains("."))
                    return@runBlocking link
                else{
                    val aLink = deviceDao.getLink(link.first)
                    return@runBlocking Triple(aLink.multicastAddress,aLink.iface,aLink.hops)
                }

            }
            Triple("notAvailable","None",0)
        }
    }
    fun getNearLinks(except: String  = ""): List<Triple<String,String,Int>> {
        return runBlocking(Dispatchers.IO) {
            if(except.isEmpty())
                deviceDao.getNearLinks().map { Triple(it.multicastAddress,it.iface,it.hops) }
            else
                deviceDao.getNearLinks(except).map { Triple(it.multicastAddress,it.iface,it.hops) }

        }
    }
    fun insertFile(file: File){
        runBlocking(Dispatchers.IO) {
            fileDao.insertAll(file)
            val nameSlub = Util().slub(file.cloudName)
            val name = fileDao.getName(nameSlub)
            if(name == null){
                fileDao.addName(Name(nameSlub,file.cloudName))
            }
            fileDao.addFileName(FileName(file.CID,nameSlub))
            fileDao.addFileSeeder(FileSeeder(file.CID,MeshDaemon.device.meshID))
        }
    }
    fun insertOrUpdateDevice(devices: List<DeviceNode>){
        runBlocking(Dispatchers.IO){
            deviceDao.addDevice(devices)
        }
    }
    fun deleteDevice(device: DeviceNode){
        runBlocking(Dispatchers.IO) {
            deviceDao.removeDevice(device)  //seeder removed by relations in fileseeder
//            deviceDao.removeSeeder(device.meshID)
        }
    }

    fun getMyDeviceInfo() : DeviceNode{

        return runBlocking(Dispatchers.IO) {
            deviceDao.getDeviceInfo()
        }
    }
    @ExperimentalCoroutinesApi
    fun getUpdatedDevice(): Flow<DeviceNode> {
        return runBlocking(Dispatchers.IO){
            deviceDao.getDeviceUpdate().distinctUntilChanged()
        }
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


    companion object{
        private var instance : Repository? = null
        @Synchronized  fun getInstance(mContext: Context) : Repository{
            if(instance == null){
                instance = Repository(mContext)
            }

            return instance as Repository
        }
    }
}