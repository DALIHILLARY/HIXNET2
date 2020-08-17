package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.io.Serializable

@Entity
data class DeviceNode (
    @PrimaryKey val meshID : String = "",
    val serviceAddress : String = "",
    val multicastAddress : String = "",
    val macAddress   : String = "",
    val publicKey   : String = "",
    val privateKey : String = "",
    val relayDevice   : Boolean = false,
    @TypeConverters(ServiceConverter::class)
    val services     : List<Services>? =  null,
    val hops      : Int = 0,
    val slave  : Boolean = false,
    val master   : Boolean = false,
    val hasInternetWifi : Boolean = false,
    val isMe  : Boolean = false

) : Serializable