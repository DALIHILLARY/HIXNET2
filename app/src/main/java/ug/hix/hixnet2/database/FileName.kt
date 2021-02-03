package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    primaryKeys = ["CID","name_slub"],
    foreignKeys = [
        ForeignKey(
            entity = File::class,
            parentColumns = ["CID"],
            childColumns = ["CID"]
        ),
        ForeignKey(
            entity = Name::class,
            parentColumns = ["name_slub"],
            childColumns = ["name_slub"]
        )
    ]
)
data class FileName (
    val CID : String,
    val name_slub : String
)