package ug.hix.hixnet2.database

import androidx.room.Embedded
import androidx.room.Relation

data class DeviceNodeWithWifiConfig (
    @Embedded
    val device: DeviceNode,
    @Relation(
        parentColumn = "meshId",
        entityColumn = "meshId"
    )
    val wifiConfig: WifiConfig

    )