package ug.hix.hixnet2.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Services::class], version = 1, exportSchema = false)

abstract class ServicesDatabase : RoomDatabase() {
    abstract fun servicesDao() : ServicesDao

    object instance{
        fun dbInstance(context: Context) : ServicesDatabase {
            return  Room.databaseBuilder(context.applicationContext, ServicesDatabase::class.java, "hixNetDB").build()
        }
    }
}