package ug.hix.hixnet2.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ug.hix.hixnet2.util.Util

@Dao
interface FileDao {
    @Query("SELECT * FROM file WHERE modified LIKE '2%'")
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
    fun getFileByCid(cid: String) : File?

    //some advanced queries for both name, file, cloudfile, and cloudfilename tables
    @Query("SELECT * FROM name WHERE name_slub LIKE :slub")
    fun getName(slub: String) : Name?

    @Query("SELECT * FROM name")
    fun getAllNames() : List<Name>

    @Query("SELECT * FROM filename WHERE CID = :cid AND name_slub = :name_slub")
    fun getFileName(cid: String, name_slub: String) : FileName?

    @Query("SELECT * FROM filename")
    fun getAllFileNames(): List<FileName>

    @Query("UPDATE filename SET status = :status, modified_by = :meshId, modified = :modified WHERE CID = :cid")
    fun updateFileNameStatus(cid: String, meshId: String, status: String, modified: String = Util.currentDateTime())

    @Query("SELECT * FROM fileseeder WHERE CID = :cid AND meshID = :meshId")
    fun getFileSeeder(cid: String, meshId: String) : FileSeeder?

    @Query("SELECT meshID FROM fileseeder WHERE CID = :cid AND status != 'Deleted'")
    fun getFileSeeders(cid: String) : List<String>

    @Query("SELECT * FROM fileseeder")
    fun getAllFileSeeders(): List<FileSeeder>

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
    fun getUpdatedFileNameFlow() : Flow<FileName?>

    @Query("SELECT * FROM filename WHERE status != 'Deleted' ORDER BY modified DESC")
    fun getUpdatedFileNameLiveData() : LiveData<List<FileName>>

    @Query("SELECT * FROM filename WHERE status != 'Deleted' ORDER BY modified DESC")
    fun getFileNames() : List<FileName>

    @Query("SELECT * FROM fileseeder ORDER BY modified DESC LIMIT 1")
    fun getUpdatedFileSeederFlow(): Flow<FileSeeder?>

    @Query("SELECT * FROM NAME ORDER BY modified DESC LIMIT 1")
    fun getUpdatedNamesFlow(): Flow<Name?>

}