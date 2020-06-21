package ug.hix.hixnet2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WifiConfig::class], version = 1, exportSchema = false)

abstract class WifiConfigDatabase : RoomDatabase() {
    abstract fun wifiConfigDao() : WifiConfigDao

    companion object{
        private var instance : WifiConfigDatabase? = null

        fun dbInstance(context: Context) : WifiConfigDatabase {
            if(instance == null){
                instance = Room.databaseBuilder(context.applicationContext, WifiConfigDatabase::class.java, "hixNetDB").build()

            }
            return instance as WifiConfigDatabase
        }
    }
}