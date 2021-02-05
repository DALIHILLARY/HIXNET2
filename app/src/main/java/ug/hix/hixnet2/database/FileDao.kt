package ug.hix.hixnet2.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FileDao {
    @Query("SELECT * FROM file WHERE modified != null")
    fun getAllFiles() : LiveData<List<File>>

    @Query("SELECT * FROM file WHERE modified == null")
    fun getCloudFiles() : LiveData<List<File>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg : File)

    @Delete
    fun delete(file : File)

    @Query("SELECT CID FROM file")
    fun gelAllCID() : List<String>

    @Query("SELECT * FROM file WHERE modified != null")
    fun getFiles() : List<File>

    @Query("SELECT * FROM file WHERE CID LIKE :cid")
    fun getFileByCid(cid: String) : File

    //some advanced queries for bo9th name, file, cloudfile, and cloudfilename tables
    @Query("SELECT * FROM name WHERE name_slub LIKE :slub")
    fun getName(slub: String) : Name?

    @Insert(onConflict = OnConflictStrategy.REPLACE, entity = Name::class)
    fun addName(name: Name)

    @Insert(onConflict = OnConflictStrategy.REPLACE, entity = FileName::class)
    fun addFileName(relation: FileName)

    @Delete(entity = FileSeeder::class)
    fun deleteFileSeeder(relation: FileSeeder)

    @Insert(onConflict = OnConflictStrategy.REPLACE, entity = FileSeeder::class )
    fun addFileSeeder(relation: FileSeeder)

//    @Query("SELECT * FROM File WHERE modified == null ORDER BY mesh_modified DESC LIMIT 1")
//    fun getNewCloudFile(): Flow<File?>
//
//    @Query("SELECT * FROM File WHERE modified == null ORDER BY mesh_modified DESC LIMIT 1")
//    fun getNewCloudFileLiveData(): LiveData<File?>
    @Query("SELECT * FROM filename ORDER BY modified DESC LIMIT 1")
    fun getUpdatedFileName() : Flow<FileName?>

    @Query("SELECT * FROM fileseeder ORDER BY modified DESC LIMIT 1")
    fun getUpdatedFileSeeder(): Flow<FileSeeder?>

    @Query("SELECT * FROM NAME ORDER BY modified DESC LIMIT 1")
    fun getUpdatedNames(): Flow<Name?>

}