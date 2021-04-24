package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.ForeignKey
import ug.hix.hixnet2.util.Util

@Entity(
    primaryKeys = ["CID","meshID"]
)
data class FileSeeder(
    val CID: String,
    val meshID: String,
    val status : String = "Added", //Added/Deleted
    val modified : String = Util.currentDateTime(),
    val modified_by : String
)