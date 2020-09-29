package ug.hix.hixnet2.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeviceNodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addDevice(device : DeviceNode)

    @Query("SELECT * FROM devicenode")
    fun getAllDevices() : LiveData<List<DeviceNode>>

    @Query("SELECT * FROM devicenode WHERE master = 1")
    fun getMasterDevices() : LiveData<List<DeviceNode>>

    @Query("SELECT * FROM devicenode WHERE master = 0")
    fun getNoMasterDevices() : LiveData<List<DeviceNode>>

    @Query("SELECT meshID FROM devicenode ")
    fun getAllPids()  : LiveData<List<String>>

    @Query("SELECT meshID FROM devicenode WHERE meshID LIKE :meshId")
    fun getPid(meshId : String) : String

    @Query("SELECT * FROM devicenode WHERE isMe = 1")
    fun getDeviceInfo() : DeviceNode

    @Query("SELECT privateKey FROM devicenode WHERE isMe = 1 ")
    fun getMyPrivateKey() : String

    @Query("SELECT publicKey FROM devicenode WHERE isMe =  1")
    fun getMyPublicKey() : String

    @Query("SELECT meshID FROM devicenode WHERE isMe = 1")
    fun getMyPid()  : String

    @Query("SELECT publicKey FROM devicenode WHERE meshID LIKE :meshId")
    fun getNodePublicKey(meshId : String) : String

}