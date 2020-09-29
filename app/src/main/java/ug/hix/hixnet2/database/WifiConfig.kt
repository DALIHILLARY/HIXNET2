package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class WifiConfig(
    @PrimaryKey val netId : Int,
    val ssid : String,
    val mac  : String,
    val passPhrase : String,
    val connAddress : String
)