package ug.hix.hixnet2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [WifiConfig::class,DeviceNode::class,File::class,Services::class], version = 1, exportSchema = false)
@TypeConverters(ServiceConverter::class)

abstract class HixNetDatabase : RoomDatabase() {
    abstract  fun deviceNodeDao() : DeviceNodeDao
    abstract fun servicesDao() : ServicesDao
    abstract fun wifiConfigDao() : WifiConfigDao
    abstract fun fileDao() : FileDao


    companion object {
        private var instance : HixNetDatabase? = null

        @Synchronized fun  dbInstance(context: Context) : HixNetDatabase {
            if(instance == null){
                instance  = Room.databaseBuilder(context.applicationContext, HixNetDatabase::class.java, "hixNetDB").build()

            }
            return instance as HixNetDatabase
        }
    }

}