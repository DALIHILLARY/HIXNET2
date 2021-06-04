package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import ug.hix.hixnet2.util.Util

@Entity(
    primaryKeys = ["CID","name_slub"]
)
data class FileName (
    val CID : String,
    val name_slub : String,
    val file_size : Int,
    val status : String = "Added", //Added/Deleted
    val modified : String = Util.currentDateTime(),
    val modified_by : String

)