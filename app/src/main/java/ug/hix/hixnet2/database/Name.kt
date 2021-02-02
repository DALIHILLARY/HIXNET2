package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Name(
    @PrimaryKey
    val name_slub : String,
    val name : String = ""
)