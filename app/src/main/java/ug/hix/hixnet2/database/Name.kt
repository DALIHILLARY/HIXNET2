package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import ug.hix.hixnet2.util.Util

@Entity
data class Name(
    @PrimaryKey
    val name_slub : String,
    val name : String = "",
    val status : String = "Added", //Added/Deleted
    val modified : String = Util.currentDateTime(),
    val modified_by : String
)