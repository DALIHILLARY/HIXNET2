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

    @Query("SELECT * FROM wificonfig WHERE ssid = :ssid")
    fun getDeviceConfigBySsid(ssid : String) : WifiConfig

    @Query("SELECT netId FROM WifiConfig WHERE ssid = :ssid")
    fun getDeviceNetId(ssid : String) : Int

    @Query("SELECT * FROM wificonfig WHERE mac = :mac")
    fun getDeviceConfigByMac(mac : String) : WifiConfig

}