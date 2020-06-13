package ug.hix.hixnet2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeviceNodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addDevice(device : DeviceNode)

    @Query("SELECT * FROM devicenode")
    fun getAllDevices() : List<DeviceNode>

    @Query("SELECT * FROM devicenode WHERE isMaster = 1")
    fun getMasterDevices() : List<DeviceNode>

    @Query("SELECT * FROM devicenode WHERE isMaster = 0")
    fun getNoMasterDevices() : List<DeviceNode>

    @Query("SELECT instanceName FROM devicenode ")
    fun getAllInstanceNames()  : List<String>

    @Query("SELECT instanceName FROM devicenode WHERE meshID LIKE :meshId")
    fun getInstanceName(meshId : String) : String

    @Query("SELECT * FROM devicenode WHERE isMe = 1")
    fun getDeviceInfo() : DeviceNode

    @Query("SELECT privateKey FROM devicenode WHERE isMe = 1 ")
    fun getMyPrivateKey() : String

    @Query("SELECT publicKey FROM devicenode WHERE isMe =  1")
    fun getMyPublicKey() : String

    @Query("SELECT publicKey FROM devicenode WHERE meshID LIKE :meshId")
    fun getNodePublicKey(meshId : String) : String

}