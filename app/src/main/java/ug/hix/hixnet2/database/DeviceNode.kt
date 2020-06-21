package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.io.Serializable

@Entity
data class DeviceNode (
    @PrimaryKey val meshID : String,
    val instanceName   : String,
    val serviceAddress : String,
    val multicastAddress : String,
    val macAddress   : String,
    val publicKey   : String,
    val privateKey : String,
    val relayDevice   : Boolean,
    @TypeConverters(ServiceConverter::class)
    val services     : List<Services>,
    val hops      : Int,
    val slave  : Boolean,
    val master   : Boolean,
    val hasInternetWifi : Boolean,
    val isMe  : Boolean

) : Serializable