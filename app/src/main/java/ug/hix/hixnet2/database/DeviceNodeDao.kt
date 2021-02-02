package ug.hix.hixnet2.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceNodeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addDevice(device : List<DeviceNode>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addDevice(device : DeviceNode)

    @Delete()
    fun removeDevice(device: DeviceNode)

    @Query("SELECT * FROM devicenode")
    fun getAllDevices() : LiveData<List<DeviceNode>>

    @Query("SELECT min(hops) FROM devicenode WHERE meshID LIKE :meshId")
    fun getMinHop(meshId: String) : Int?

    @Query("SELECT * FROM devicenode WHERE meshID = :meshId AND hops = :hops")
    fun getLink(meshId: String, hops: Int) : List<DeviceNode>

    @Query("SELECT * FROM devicenode WHERE meshID = :meshId AND multicastAddress LIKE '230.%'")
    fun getLink(meshId: String) : DeviceNode

    @Query("SELECT * FROM devicenode WHERE multicastAddress LIKE '230.%' AND isMe = 0")
    fun getNearLinks() : List<DeviceNode>

    @Query("SELECT * FROM devicenode WHERE multicastAddress LIKE '230.%' AND isMe = 0 AND meshID != :except")
    fun getNearLinks(except: String) : List<DeviceNode>

//    @Query("SELECT * FROM devicenode WHERE master = 1")
//    fun getMasterDevices() : LiveData<List<DeviceNode>>
//
//    @Query("SELECT * FROM devicenode WHERE master = 0")
//    fun getNoMasterDevices() : LiveData<List<DeviceNode>>

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

    //advanced queries for relationship operation
    @Query("DELETE FROM fileseeder WHERE meshID LIKE :meshId")
    fun removeSeeder(meshId: String)

    @Query("SELECT * FROM devicenode WHERE status ORDER BY modified DESC LIMIT 1 ")
    fun getDeviceUpdate(): Flow<DeviceNode>

}