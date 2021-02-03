package ug.hix.hixnet2.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

import ug.hix.hixnet2.cyphers.Generator
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.util.Base58
import ug.hix.hixnet2.util.Util
import java.io.File


class UploadWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext,workerParams) {
    val TAG = javaClass.simpleName
    override fun doWork(): Result {
        try{
            val repo = Repository(applicationContext)
            val CIDs = repo.getCIDs()
//            val filesHashMap = MeshDaemon.filesHashMap
            val filePaths = inputData.getStringArray("filePaths")
            filePaths?.forEach { filepath ->
                val file = File(filepath)
                val name = file.name
                val extension = file.extension
                val size = (file.length()/ 1024).toInt() //in kilobytes
                val encodedHash = Generator.getEncodedHash(file)
                val CID = Base58.encode(encodedHash)
                val currentDate = Util().currentDateTime()

                if(CID !in CIDs) {
                    val fileObj = ug.hix.hixnet2.database.File(CID = CID,path = filepath, size = size, cloudName = name, extension = extension, modified = currentDate, mesh_modified = currentDate)
                    repo.insertOrUpdateFile(fileObj)

//                    val fileAttribute = mutableMapOf<String, MutableList<String>>()
//                    fileAttribute["Name"] = mutableListOf(fileObj.cloudName)
//                    fileAttribute["Seeders"] = mutableListOf(MeshDaemon.device.meshID)
//                    fileAttribute["Size"] = mutableListOf(fileObj.size.toString())
//                    fileAttribute["Date"] = mutableListOf(fileObj.modified)
//                    fileAttribute["Extension"] = mutableListOf(fileObj.extension)
//
//                    if (!filesHashMap.containsKey(fileObj.CID)) {
//                        filesHashMap[fileObj.CID] = fileAttribute
//                    } else {
//                        val names = filesHashMap[fileObj.CID]?.get("Name")
//                        fileAttribute["Name"]?.forEach {
//                            if (!names!!.contains(it)) {
//                                names.add(it)
//                            }
//                        }
//                        val seeders = filesHashMap[fileObj.CID]?.get("Seeders")
//                        fileAttribute["Seeders"]?.forEach {
//                            if (!seeders!!.contains(it)) {
//                                seeders.add(it)
//                            }
//                        }
//                        fileAttribute["Name"] = names!!
//                        fileAttribute["Seeders"] = seeders!!
//
//                        filesHashMap[fileObj.CID] = fileAttribute
//                    }
                }

            }

//            var files = FileHashMap()
//            val file = FileHashMap.CID()
//            val fileAttributes = FileHashMap.Attributes()
//            val filesList = mutableListOf<FileHashMap.CID>()
//            filesHashMap.forEach {
//                val attributeList = mutableListOf<FileHashMap.Attributes>()
//                val cid = it.key
//                val attributes = it.value.toList()
//                attributes.forEach {attribute ->
//                    attributeList.add(fileAttributes.copy(attribute.first,attribute.second))
//                }
//                filesList.add(file.copy(cid,attributeList))
//
//            }
//            files = files.copy(filesList)
////            Licklider.start(appContext).loadData(files)
//
            return Result.success()
        }catch (e: Exception){
            e.printStackTrace()
            return Result.failure()
        }

    }
}