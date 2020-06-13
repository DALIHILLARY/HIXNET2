package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val services     : List<Services>,
    val hops      : Int,
    val hasMaster  : Boolean,
    val isMaster   : Boolean,
    val hasInternetWifi : Boolean,
    val isMe  : Boolean

)