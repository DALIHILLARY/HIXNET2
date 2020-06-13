package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Services (
    @PrimaryKey val name : String,
    val port  : Int
)