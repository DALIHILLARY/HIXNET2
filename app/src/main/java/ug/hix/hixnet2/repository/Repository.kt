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
import java.lang.Thread.sleep

class Repository(val context : Context) {

    private val databaseInstance = HixNetDatabase.dbInstance(context.applicationContext)
    private val fileDao = databaseInstance.fileDao()
    private val wifiDao = databaseInstance.wifiConfigDao()
    private val deviceDao = databaseInstance.deviceNodeDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    suspend fun getFilesLiveData() : LiveData<List<File>> {
        return fileDao.getAllFiles()
    }
    suspend fun getCloudFilesLiveData(): LiveData<List<FileName>> {
        return fileDao.getUpdatedFileNameLiveData()
    }
    suspend fun getFileNames() : List<FileName> {
        return withContext(Dispatchers.IO) { fileDao.getFileNames()}
    }
    suspend fun getCloudFileByCid(filename: FileName) : File? {
        val readableName = withContext(Dispatchers.IO){ fileDao.getName(filename.CID) }
        return if(readableName != null) {
            val extensionList = filename.name_slub.split('.')
            val extension = if(extensionList.isNotEmpty()) extensionList.last() else "null"
            File("","",0,readableName.name,extension)
        }else{
            null
        }
    }
    suspend fun getFileByCid(cid: String) : File? {
        return withContext(Dispatchers.IO){fileDao.getFileByCid(cid)}
    }
    suspend fun getAllFiles() : List<File> {
        return withContext(Dispatchers.IO){fileDao.getFiles()}
    }

    suspend fun getCIDs() : List<String> {
        return withContext(Dispatchers.IO){fileDao.gelAllCID()}
    }
    suspend fun deleteFile(file: File){
        withContext(Dispatchers.IO){
            val meshId = MeshDaemon.device.meshID
            fileDao.delete(file)
            fileDao.addFileSeeder(FileSeeder(file.CID,meshId,"Deleted",modified_by = meshId))
            if(fileDao.getFileSeeders(file.CID).isNullOrEmpty())
                fileDao.updateFileNameStatus(file.CID,meshId,"Deleted")
        }
    }
    suspend fun updateAddress(address: String){
        return withContext(Dispatchers.IO){deviceDao.updateAddress(address)}
    }
    @ExperimentalCoroutinesApi
    suspend fun getNewAddressFlow(): Flow<String> = deviceDao.getNewAddressFlow().distinctUntilChanged()

    suspend fun getLink(meshId: String) : Triple<String,String,Int> {
        return withContext(Dispatchers.IO) {
            val minHop = deviceDao.getMinHop(meshId)
            minHop?.let {
                val link = deviceDao.getLink(meshId,it).map { device ->
                    Triple(device.multicastAddress,device.iface,device.hops) }.first()
                if(link.first.contains("."))
                    return@withContext link
                else{
                    val aLink = deviceDao.getLink(link.first)
                    return@withContext Triple(aLink.multicastAddress,aLink.iface,aLink.hops)
                }

            }
            Triple("notAvailable","None",0)
        }
    }
    suspend fun getNearLinks(except: String  = ""): List<Triple<String,String,Int>> {
        return withContext(Dispatchers.IO) {
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
    suspend fun getNearMultiAddresses() : List<String>{
        return withContext(Dispatchers.IO) { deviceDao.getNearLinks().map { it.multicastAddress } }
    }
    suspend fun insertOrUpdateFile(file: File){
        withContext(Dispatchers.IO) {
            val device = MeshDaemon.device.meshID
            val nameSlub = file.cloudName?.let { Util.slub(it) }
            val name = nameSlub?.let { fileDao.getName(it) }
            if(name == null){
                fileDao.addName(Name(nameSlub!!, file.cloudName,modified_by = device))
            }
            fileDao.insertAll(file)
            fileDao.addFileName(FileName(file.CID,nameSlub,file.size,modified_by = device))
            fileDao.addFileSeeder(FileSeeder(CID = file.CID, meshID = device,modified_by = device))
        }
    }
    suspend fun updateFileName(fileName: FileName){
        withContext(Dispatchers.IO){
            if(!isExistFileName(fileName))
                fileDao.addFileName(fileName)
        }
    }
    suspend fun updateFileName(fileName: List<FileName>){
        withContext(Dispatchers.IO){
            fileName.forEach {
                if(!isExistFileName(it))
                    fileDao.addFileName(it)
            }
        }
    }
    private suspend fun isExistFileName(fileName: FileName) : Boolean {
        val result = fileDao.getFileName(fileName.CID, fileName.name_slub)
        return if (result == null) false
        else
            if(fileName.modified > result.modified) fileName.status == result.status
            else true
    }
    suspend fun updateName(name: Name){
        withContext(Dispatchers.IO){
            if(!isExistName(name))
                fileDao.addName(name)
        }
    }
    suspend fun updateName(name: List<Name>){
        withContext(Dispatchers.IO){
            name.forEach {
                if(!isExistName(it))
                    fileDao.addName(it)
            }
        }
    }

    /**
     * This a security measure
     */
    private suspend fun isExistName(name: Name) : Boolean {
        val result = fileDao.getName(name.name_slub)
        return if(result == null) false
        else
            if(name.modified > result.modified) name.status == result.status
            else true
    }
    suspend fun updateFileSeeder(fileSeeder: FileSeeder){
        withContext(Dispatchers.IO){
            if(!isExistFileSeeder(fileSeeder))
                fileDao.addFileSeeder(fileSeeder)
        }
    }
    suspend fun updateFileSeeder(fileSeeder: List<FileSeeder>){
        withContext(Dispatchers.IO){
            fileSeeder.forEach {
                if(!isExistFileSeeder(it))
                    fileDao.addFileSeeder(it)
            }
        }
    }
    private suspend fun isExistFileSeeder(fileSeeder: FileSeeder) : Boolean {
        val result = fileDao.getFileSeeder(fileSeeder.CID,fileSeeder.meshID)
        return if(result == null) false
        else
            if(fileSeeder.modified > result.modified) fileSeeder.status == result.status
            else true
    }

    suspend fun getAllFileNames() : List<FileName> = withContext(Dispatchers.IO) { fileDao.getAllFileNames()}
    suspend fun getAllNames() : List<Name> = withContext(Dispatchers.IO){fileDao.getAllNames()}
    suspend fun getAllFileSeeders(): List<FileSeeder> = withContext(Dispatchers.IO){fileDao.getAllFileSeeders()}
    suspend fun insertOrUpdateDevice(devices: List<DeviceNode>){
        withContext(Dispatchers.IO){
            deviceDao.addDevice(devices)
        }
    }
    @ExperimentalCoroutinesApi
    suspend fun getUpdatedNameFlow(): Flow<Name?> = fileDao.getUpdatedNamesFlow().distinctUntilChanged()

    @ExperimentalCoroutinesApi
    suspend fun getUpdatedFileNameFlow(): Flow<FileName?> = fileDao.getUpdatedFileNameFlow().distinctUntilChanged()

    @ExperimentalCoroutinesApi
    suspend fun getUpdatedFileSeederFlow(): Flow<FileSeeder?> = fileDao.getUpdatedFileSeederFlow().distinctUntilChanged()

    suspend fun insertOrUpdateDeviceWithConfig(device: DeviceNode, wifiConfig: WifiConfig){
        withContext(Dispatchers.IO){
            if(validateDeviceUpdate(device))
                deviceDao.addDeviceWithConfig(device,wifiConfig)
        }
    }
    suspend fun insertOrUpdateDeviceWithConfig(device: List<DeviceNode>, wifiConfig: List<WifiConfig>){
        withContext(Dispatchers.IO){
            device.forEachIndexed { index, deviceNode ->
                if(validateDeviceUpdate(deviceNode))
                    deviceDao.addDeviceWithConfig(deviceNode,wifiConfig[index])
            }
        }
    }
    private suspend fun validateDeviceUpdate(device : DeviceNode) : Boolean {
        val result = deviceDao.getDevice(device.meshID,device.multicastAddress)
        return if(result == null) true
        else device.modified > result.modified


    }
    suspend fun deleteDevice(device: DeviceNode){
        withContext(Dispatchers.IO) {
            deviceDao.removeDevice(device)
            deviceDao.removeChildDevices(device.meshID) //delete children with mAddress as device meshId
            deviceDao.removeSeeder(device.meshID) //remove all files connected to device
        }
    }

    suspend fun getMyDeviceInfo() = withContext(Dispatchers.IO) {
            deviceDao.getDeviceInfo()
    }

    @ExperimentalCoroutinesApi
    suspend fun getUpdatedDeviceFlow(): Flow<DeviceNodeWithWifiConfig?>{
        return deviceDao.getDeviceUpdateFlow().map { device ->
            device?.let{
                getWifiConfigByMeshId(device.meshID)?.let {
                    DeviceNodeWithWifiConfig(
                        device = device,
                        wifiConfig = it
                    )
                }
            }

        }.distinctUntilChanged()
    }
    suspend fun getAllDevices(): List<DeviceNodeWithWifiConfig?> {
        return withContext(Dispatchers.IO) {
            deviceDao.getAllDevices().map { device ->
                val mDevice = device.copy(privateKey = "")
                getWifiConfigByMeshId(device.meshID)?.let {
                    DeviceNodeWithWifiConfig(
                        device = mDevice,
                        wifiConfig = it
                    )
                }
            }
        }
    }
    suspend fun activeDevicesLiveData(): LiveData<List<DeviceNode>> {
        return deviceDao.activeDevicesLiveData()
    }
    suspend fun activeDevices(): List<DeviceNode> = withContext(Dispatchers.IO) { deviceDao.activeDevices() }

    suspend fun addWifiConfig(config: WifiConfig) {
        return withContext(Dispatchers.IO) { wifiDao.addConfig(config) }
    }
    suspend fun updateWifiStatusBySsid(meshId: String, status: Boolean){
        val status2Int = if(status) 1 else 0
        return withContext(Dispatchers.IO) { wifiDao.updateWifiStatusBySsid(meshId,status2Int)}
    }

    suspend fun getAllWifiConfig() : List<WifiConfig>{
        return withContext(Dispatchers.IO) {wifiDao.getAllConfig()}
    }
    suspend fun getAllMac() : List<String> {
        return withContext(Dispatchers.IO) { wifiDao.getAllMac() }
    }
    suspend fun getAllWifiNetIds() : List<Int> {
        return withContext(Dispatchers.IO) { wifiDao.getAllNetId() }
    }
    suspend fun getWifiConfigBySsid(ssid : String) : WifiConfig? {
        return withContext(Dispatchers.IO) { wifiDao.getWifiConfigBySsid(ssid) }
    }
    suspend fun isWifiConfig(ssid: String) : Boolean {
        return withContext(Dispatchers.IO) {wifiDao.getWifiConfigBySsidList(ssid).isNotEmpty()}
    }
    suspend fun getWifiConfigByMac(mac : String) : WifiConfig? {
        return withContext(Dispatchers.IO) { wifiDao.getWifiConfigByMac(mac) }
    }
    private suspend fun getWifiConfigByMeshId(meshId: String) : WifiConfig? {
        return withContext(Dispatchers.IO) { wifiDao.getWifiConfigByMeshId(meshId) }
    }
    suspend fun getMyWifiConfig() : WifiConfig? {
        return withContext(Dispatchers.IO) { wifiDao.getMyConfig(MeshDaemon.device.meshID) }
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