package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity
data class WifiConfig(
    @PrimaryKey
    val meshID : String,
    val netId : Int = 0,
    val ssid : String = "",
    val mac  : String = "",
    val passPhrase : String = "",
    val connAddress : String = ""
)