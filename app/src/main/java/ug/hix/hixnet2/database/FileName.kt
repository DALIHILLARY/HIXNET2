package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    primaryKeys = ["CID","name_id"],
    foreignKeys = [
        ForeignKey(
            entity = File::class,
            parentColumns = ["CID"],
            childColumns = ["CID"]
        ),
        ForeignKey(
            entity = Name::class,
            parentColumns = ["name_id"],
            childColumns = ["name_id"]
        )
    ]
)
data class FileName (
    val CID : String,
    val name_id : String
)