package ug.hix.hixnet2.repository

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.LiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ug.hix.hixnet2.database.*
import ug.hix.hixnet2.services.MeshDaemon
import ug.hix.hixnet2.util.Util

class Repository(val context : Context) {

    private val databaseInstance = HixNetDatabase.dbInstance(context.applicationContext)
    private val fileDao = databaseInstance.fileDao()
    private val wifiDao = databaseInstance.wifiConfigDao()
    private val deviceDao = databaseInstance.deviceNodeDao()

    fun getFilesLiveData() : LiveData<List<File>> {
        return fileDao.getAllFiles()
    }
    fun getCloudFilesLiveData(): LiveData<List<FileName>> {
        return fileDao.getUpdatedFileNameLiveData()
    }
    fun getFileNames() : List<FileName> {
        return runBlocking(Dispatchers.IO) { fileDao.getFileNames()}
    }
    fun getFileByCid(cid: String) : File? {
        return runBlocking(Dispatchers.IO){fileDao.getFileByCid(cid)}
    }
    fun getAllFiles() : List<File> {
        return runBlocking(Dispatchers.IO){fileDao.getFiles()}
    }

    fun getCIDs() : List<String> {
        return runBlocking(Dispatchers.IO){fileDao.gelAllCID()}
    }
    fun deleteFile(file: File){
        runBlocking(Dispatchers.IO){
            val meshId = MeshDaemon.device.meshID
            fileDao.delete(file)
            fileDao.addFileSeeder(FileSeeder(file.CID,meshId,"Deleted",modified_by = meshId))
            if(fileDao.getFileSeeders(file.CID).isNullOrEmpty())
                fileDao.updateFileNameStatus(file.CID,meshId,"Deleted")
        }
    }
    fun updateAddress(address: String){
        return runBlocking(Dispatchers.IO){deviceDao.updateAddress(address)}
    }
    @ExperimentalCoroutinesApi
    fun getNewAddressFlow(): Flow<String> = deviceDao.getNewAddressFlow().distinctUntilChanged()

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
            val result :  List<Triple<String,String,Int>> = if(except.isEmpty())
                deviceDao.getNearLinks().map { Triple(it.multicastAddress,it.iface,it.hops) }
            else
                deviceDao.getNearLinks(except).map { Triple(it.multicastAddress,it.iface,it.hops) }
            if(result.isNotEmpty())
                result
            else
                listOf(Triple("notAvailable","None",0))
        }
    }
    fun getNearMultiAddresses() : List<String>{
        return runBlocking(Dispatchers.IO) { deviceDao.getNearLinks().map { it.multicastAddress } }
    }
    fun insertOrUpdateFile(file: File){
        runBlocking(Dispatchers.IO) {
            val device = MeshDaemon.device.meshID
            val nameSlub = file.cloudName?.let { Util.slub(it) }
            val name = nameSlub?.let { fileDao.getName(it) }
            if(name == null){
                fileDao.addName(Name(nameSlub!!, file.cloudName,modified_by = device))
            }
            fileDao.insertAll(file)
            fileDao.addFileName(FileName(file.CID,nameSlub,modified_by = device))
            fileDao.addFileSeeder(FileSeeder(CID = file.CID, meshID = device,modified_by = device))
        }
    }
    fun updateFileName(fileName: FileName){
        runBlocking(Dispatchers.IO){
            if(!isExistFileName(fileName))
                fileDao.addFileName(fileName)
        }
    }
    private fun isExistFileName(fileName: FileName) : Boolean {
        val result = fileDao.getFileName(fileName.CID, fileName.name_slub)
        return if (result == null) false
        else
            if(fileName.modified > result.modified) fileName.status == result.status
            else true
    }
    fun updateName(name: Name){
        runBlocking(Dispatchers.IO){
            if(!isExistName(name))
                fileDao.addName(name)
        }
    }
    private fun isExistName(name: Name) : Boolean {
        val result = fileDao.getName(name.name_slub)
        return if(result == null) false
        else
            if(name.modified > result.modified) name.status == result.status
            else true
    }
    fun updateFileSeeder(fileSeeder: FileSeeder){
        runBlocking(Dispatchers.IO){
            if(!isExistFileSeeder(fileSeeder))
                fileDao.addFileSeeder(fileSeeder)
        }
    }
    private fun isExistFileSeeder(fileSeeder: FileSeeder) : Boolean {
        val result = fileDao.getFileSeeder(fileSeeder.CID,fileSeeder.meshID)
        return if(result == null) false
        else
            if(fileSeeder.modified > result.modified) fileSeeder.status == result.status
            else true
    }

    suspend fun getAllFileNames() : List<FileName> = withContext(Dispatchers.IO) { fileDao.getAllFileNames()}
    suspend fun getAllNames() : List<Name> = withContext(Dispatchers.IO){fileDao.getAllNames()}
    suspend fun getAllFileSeeders(): List<FileSeeder> = withContext(Dispatchers.IO){fileDao.getAllFileSeeders()}
    fun insertOrUpdateDevice(devices: List<DeviceNode>){
        runBlocking(Dispatchers.IO){
            deviceDao.addDevice(devices)
        }
    }
    @ExperimentalCoroutinesApi
    fun getUpdatedNameFlow(): Flow<Name?> = fileDao.getUpdatedNamesFlow().distinctUntilChanged()

    @ExperimentalCoroutinesApi
    fun getUpdatedFileNameFlow(): Flow<FileName?> = fileDao.getUpdatedFileNameFlow().distinctUntilChanged()

    @ExperimentalCoroutinesApi
    fun getUpdatedFileSeederFlow(): Flow<FileSeeder?> = fileDao.getUpdatedFileSeederFlow().distinctUntilChanged()

    fun insertOrUpdateDeviceWithConfig(devices: List<DeviceNode>, wifiConfigs: List<WifiConfig>){
        runBlocking(Dispatchers.IO){
            if(validateDeviceUpdate(devices[0]))
                deviceDao.addDeviceWithConfig(devices,wifiConfigs)
        }
    }
    private fun validateDeviceUpdate(device : DeviceNode) : Boolean {
        val result = deviceDao.getDevice(device.meshID,device.multicastAddress)
        return if(result == null) true
        else device.modified > result.modified


    }
    fun deleteDevice(device: DeviceNode){
        runBlocking(Dispatchers.IO) {
            deviceDao.removeDevice(device)
            deviceDao.removeChildDevices(device.meshID) //delete children with mAddress as device meshId
            deviceDao.removeSeeder(device.meshID) //remove all files connected to device
        }
    }

    fun getMyDeviceInfo() : DeviceNode{

        return runBlocking(Dispatchers.IO) {
            deviceDao.getDeviceInfo()
        }
    }
    @ExperimentalCoroutinesApi
    fun getUpdatedDeviceFlow(): Flow<DeviceNodeWithWifiConfig?>{
        return deviceDao.getDeviceUpdateFlow().map { device ->
            if(device != null ){
                DeviceNodeWithWifiConfig(
                    device = device,
                    wifiConfig = getWifiConfigByMeshId(device.meshID)
                )
            }else
                null

        }.distinctUntilChanged()
    }
    fun getAllDevices(): List<DeviceNodeWithWifiConfig> {
        return runBlocking(Dispatchers.IO) {
            deviceDao.getAllDevices().map { device ->
                DeviceNodeWithWifiConfig(
                    device = device,
                    wifiConfig = getWifiConfigByMeshId(device.meshID)
                )
            }
        }
    }
    fun activeDevicesLiveData(): LiveData<List<DeviceNode>> {
        return deviceDao.activeDevicesLiveData()
    }
    fun activeDevices(): List<DeviceNode> = runBlocking(Dispatchers.IO) { deviceDao.activeDevices() }

    fun addWifiConfig(config: WifiConfig) {
        return runBlocking(Dispatchers.IO) { wifiDao.addConfig(config) }
    }

    fun getAllWifiConfig() : List<WifiConfig>{
        return runBlocking(Dispatchers.IO) {wifiDao.getAllConfig()}
    }
    fun getAllMac() : List<String> {
        return runBlocking(Dispatchers.IO) { wifiDao.getAllMac() }
    }
    fun getAllWifiNetIds() : List<Int> {
        return runBlocking(Dispatchers.IO) { wifiDao.getAllNetId() }
    }
    fun getWifiConfigBySsid(ssid : String) : WifiConfig {
        return runBlocking(Dispatchers.IO) { wifiDao.getWifiConfigBySsid(ssid) }
    }
    fun isWifiConfig(ssid: String) : Boolean {
        return runBlocking(Dispatchers.IO) {wifiDao.getWifiConfigBySsidList(ssid).isNotEmpty()}
    }
    fun getWifiConfigByMac(mac : String) : WifiConfig {
        return runBlocking(Dispatchers.IO) { wifiDao.getWifiConfigByMac(mac) }
    }
    private fun getWifiConfigByMeshId(meshId: String) : WifiConfig {
        return runBlocking(Dispatchers.IO) { wifiDao.getWifiConfigByMeshId(meshId) }
    }


    companion object{
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance : Repository? = null
        @Synchronized  fun getInstance(mContext: Context) : Repository{
            if(instance == null){
                instance = Repository(mContext)
            }

            return instance as Repository
        }
    }
}