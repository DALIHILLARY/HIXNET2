package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity
data class Services (
    @PrimaryKey val name : String,
    val port  : Int
) : Serializable