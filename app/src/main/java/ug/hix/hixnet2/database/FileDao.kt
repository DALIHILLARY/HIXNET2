package ug.hix.hixnet2.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FileDao {
    @Query("SELECT * FROM file")
    fun getAllFiles() : LiveData<List<File>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg : File)

    @Delete
    fun delete(file : File)

    @Query("SELECT CID FROM file")
    fun gelAllCID() : List<String>

    @Query("SELECT * FROM file")
    fun getFiles() : List<File>

    @Query("SELECT * FROM file WHERE CID LIKE :cid")
    fun getFileByCid(cid: String) : File
}