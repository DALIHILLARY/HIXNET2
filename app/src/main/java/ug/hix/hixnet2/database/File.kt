package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class File(
    @PrimaryKey val CID : String,
    val path : String? = null,
    val size : Int,
    val cloudName : String? = null,
    val extension : String,
    val modified : String? = null
)