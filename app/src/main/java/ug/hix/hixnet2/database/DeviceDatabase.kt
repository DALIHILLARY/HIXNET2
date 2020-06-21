package ug.hix.hixnet2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [DeviceNode::class], version = 1, exportSchema = false)
@TypeConverters(ServiceConverter::class)

abstract class DeviceDatabase : RoomDatabase() {
    abstract  fun deviceNodeDao() : DeviceNodeDao

    companion object {
        private var instance : DeviceDatabase? = null

        fun dbInstance(context: Context) : DeviceDatabase {
            if(instance == null){
                instance  = Room.databaseBuilder(context.applicationContext, DeviceDatabase::class.java, "hixNetDB").build()

            }
            return instance as DeviceDatabase
        }
    }

}