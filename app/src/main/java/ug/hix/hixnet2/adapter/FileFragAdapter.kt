package ug.hix.hixnet2.adapter

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File as JFile
import ug.hix.hixnet2.R
import ug.hix.hixnet2.database.File
import ug.hix.hixnet2.repository.Repository
import ug.hix.hixnet2.services.MeshDaemon
import ug.hix.hixnet2.workers.SendFileWorker

class FileFragAdapter(private val context: Context) : ListAdapter<File, FileFragAdapter.FileFragViewHolder>(DIFF_CALLBACK) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileFragViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.file_item, parent,false)

        return FileFragViewHolder(v)
    }

    override fun onBindViewHolder(holder: FileFragViewHolder, position: Int) {
       holder.bind(getItem(position))
    }

    inner class FileFragViewHolder(fileView: View) : RecyclerView.ViewHolder(fileView) {
        private val fileIcon: ImageView = fileView.findViewById(R.id.fileIcon)
        private var fileName: TextView = fileView.findViewById(R.id.fileName)
        private var fileSize: TextView = fileView.findViewById(R.id.fileSize)
        private val fileModified: TextView   = fileView.findViewById(R.id.fileModifiedDate)
        private val fileOptionsMenu: TextView = fileView.findViewById(R.id.fileOptionsMenu)
        private val repo = Repository.getInstance(context)


        fun bind(file: File){
            fileName.text = file.cloudName
            fileSize.text = file.size.toString()
            fileModified.text = file.modified

            when(file.extension.toLowerCase()){
                "pdf" -> Glide.with(context.applicationContext).load(R.drawable.pdf).diskCacheStrategy(
                    DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(fileIcon)
                in listOf("xlsx","xls") -> Glide.with(context.applicationContext).load(R.drawable.excel).diskCacheStrategy(
                    DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(fileIcon)
                in listOf("doc","docx") -> Glide.with(context.applicationContext).load(R.drawable.word).diskCacheStrategy(
                    DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(fileIcon)
                in listOf("ppt","pptx") -> Glide.with(context.applicationContext).load(R.drawable.powerpoint).diskCacheStrategy(
                    DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(fileIcon)
                "txt"  -> Glide.with(context.applicationContext).load(R.drawable.text).diskCacheStrategy(
                    DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(fileIcon)
                "rar"  -> Glide.with(context.applicationContext).load(R.drawable.rar).diskCacheStrategy(
                    DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(fileIcon)
                in listOf("zip","tar","tz") -> Glide.with(context.applicationContext).load(R.drawable.zip).diskCacheStrategy(
                    DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(fileIcon)
                in listOf("mp4","jpg","mkv","3gp") ->  Glide.with(context.applicationContext).load(JFile(file.path)).diskCacheStrategy(
                    DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().centerCrop().into(fileIcon)

            }
            fileOptionsMenu.setOnClickListener {
                val popUpMenu = PopupMenu(context,fileOptionsMenu)
                popUpMenu.inflate(R.menu.files_menu)
                popUpMenu.setOnMenuItemClickListener {
                    when(it.itemId){
                        R.id.fileOpen -> {
                            runBlocking(Dispatchers.IO) {
                                try{
                                    val myFile = JFile(file.path!!)
                                    val intent = Intent(Intent.ACTION_VIEW)
                                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    val uri = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        FileProvider.getUriForFile(context,"${context.applicationContext.packageName}.provider",myFile)

                                    }else
                                        Uri.fromFile(myFile)
                                    val cR = context.contentResolver
                                    intent.setDataAndType(uri,cR.getType(uri))
                                    context.startActivity(intent)
                                }catch (e : NullPointerException){
                                    Log.e("FileFragAdapter","File path in null")
                                }catch (e: ActivityNotFoundException){
                                    Log.e("FileFragAdapter","Activity not found")
                                }
                            }

                        }
                        R.id.fileSend -> {
                            val fileCid = file.CID
                            val sendWorker = OneTimeWorkRequestBuilder<SendFileWorker>()
                                .setInputData(workDataOf("fileCID" to fileCid,"fromMeshId" to MeshDaemon.device.meshID))
                                .build()
                            WorkManager.getInstance(context).enqueue(sendWorker)

                        }
                        R.id.fileInfo -> {
//                            TODO("Open an activity showing info")
                        }
                        R.id.filesDelete -> {
                                repo.deleteFile(file)
                        }
                        R.id.filesEdit -> {
//                            TODO("modify name in database")
                        }
                    }
                    false
                }
                val menuHelper = MenuPopupHelper(context, popUpMenu.menu as MenuBuilder, fileOptionsMenu)
                menuHelper.setForceShowIcon(true)
                popUpMenu.show()
            }
        }

    }
    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<File>(){
            override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
                return oldItem.CID == newItem.CID
            }

            override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
                return oldItem.cloudName == newItem.cloudName && oldItem.extension == newItem.extension && oldItem.path == newItem.path && oldItem.size == newItem.size
            }
        }
    }
}
