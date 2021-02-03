package ug.hix.hixnet2.database

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    primaryKeys = ["CID","meshID"],
    foreignKeys = [
        ForeignKey(
            entity = File::class,
            parentColumns = ["CID"],
            childColumns = ["CID"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FileSeeder(
    val CID: String,
    val meshID: String
)