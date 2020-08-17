package ug.hix.hixnet2.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ServicesDao {
    @Query("SELECT * FROM services")
    fun getAll() : LiveData<List<Services>>

    @Query("SELECT * FROM services WHERE name LIKE :serviceName")
    fun getService(serviceName : String) : Services

    @Insert
    fun insertAll(vararg services : Services)

    @Delete
    fun delete(service : Services)

    @Query( "DELETE FROM services WHERE name LIKE :serviceName")
    fun removeService(serviceName: String)
}