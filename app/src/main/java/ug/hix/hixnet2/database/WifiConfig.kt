package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = DeviceNode::class,
            parentColumns = ["meshId"],
            childColumns = ["meshId"]
        )
    ],
    indices = [Index(value = ["meshId"], unique = true)]
)
data class WifiConfig(
    @PrimaryKey
    val meshId : String,
    val netId : Int = 0,
    val ssid : String = "",
    val mac  : String = "",
    val passPhrase : String = "",
    val connAddress : String = ""
)