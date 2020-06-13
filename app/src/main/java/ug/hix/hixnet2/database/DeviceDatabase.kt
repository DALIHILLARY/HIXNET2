package ug.hix.hixnet2.database

import android.bluetooth.BluetoothClass
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DeviceNode::class], version = 1, exportSchema = false)

abstract class DeviceDatabase : RoomDatabase() {
    abstract  fun deviceNodeDao() : DeviceNodeDao

    object instance{
        fun dbInstance(context: Context) : DeviceDatabase {
           return  Room.databaseBuilder(context.applicationContext, DeviceDatabase::class.java, "hixNetDB").build()
        }
    }

}