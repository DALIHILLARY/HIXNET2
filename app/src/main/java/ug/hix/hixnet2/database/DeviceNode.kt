package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.io.Serializable

@Entity(primaryKeys = ["meshID","multicastAddress"])
data class DeviceNode (
    val meshID : String = "",
    val serviceAddress : String = "",
    val multicastAddress : String = "",
    val macAddress : String = "",
    val publicKey  : String = "",
    val privateKey : String = "",
    val relayDevice : Boolean = false,
    @TypeConverters(ServiceConverter::class)
    val services     : List<Services>? =  null,
    val hops      : Int = 0,
    val iface : String = "wlan0",
    val hasInternetWifi : Boolean = false,
    val wifiName : String = "",
    val passPhrase : String = "",
    val isMe  : Boolean = false,
    val version : String = "",
    val modified : String = "",
    val status : String = ""
) : Serializable