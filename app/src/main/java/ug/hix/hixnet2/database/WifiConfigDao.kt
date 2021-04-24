package ug.hix.hixnet2.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WifiConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addConfig( config : WifiConfig)

    @Query("SELECT * FROM wificonfig")
    fun getAllConfig() : List<WifiConfig>

    @Query("SELECT mac FROM wificonfig")
    fun getAllMac()   : List<String>

    @Query("SELECT netId FROM wificonfig")
    fun getAllNetId() : List<Int>

    @Query("SELECT * FROM wificonfig WHERE ssid = :ssid")
    fun getWifiConfigBySsid(ssid : String) : WifiConfig

    @Query("SELECT netId FROM wificonfig WHERE ssid = :ssid")
    fun getWifiNetId(ssid : String) : Int

    @Query("SELECT * FROM wificonfig WHERE mac = :mac")
    fun getWifiConfigByMac(mac : String) : WifiConfig

    @Query("SELECT * FROM wificonfig WHERE ssid = :ssid")
    fun getWifiConfigBySsidList(ssid : String): List<WifiConfig>

    @Query("SELECT * FROM wificonfig WHERE meshID = :meshId")
    fun getWifiConfigByMeshId(meshId: String) : WifiConfig
}