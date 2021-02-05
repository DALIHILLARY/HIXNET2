package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.ForeignKey
import ug.hix.hixnet2.util.Util

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
    val name_slub : String,
    val status : String = "Added", //Added/Deleted
    val modified : String = Util.currentDateTime(),
    val modified_by : String

)