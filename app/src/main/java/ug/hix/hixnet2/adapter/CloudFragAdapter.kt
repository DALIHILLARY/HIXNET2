package ug.hix.hixnet2.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.runBlocking
import ug.hix.hixnet2.R
import ug.hix.hixnet2.database.File
import ug.hix.hixnet2.database.FileName
import ug.hix.hixnet2.repository.Repository

class CloudFragAdapter(private val mContext: Context) : ListAdapter<FileName, CloudFragAdapter.CloudViewHolder>(DIFF_CALLBACK), Filterable{
    private val repo = Repository.getInstance(mContext)
    private var filteredFileNameList = listOf<FileName>()
    private val fileNameList =  runBlocking { repo.getFileNames() }
    private val TAG = javaClass.simpleName

    inner class CloudViewHolder(cloudView: View) : RecyclerView.ViewHolder(cloudView){
        private val cloudName: TextView = cloudView.findViewById(R.id.fileName)
        private val cloudModified: TextView = cloudView.findViewById(R.id.fileModifiedDate)
        private val cloudSize: TextView  = cloudView.findViewById(R.id.fileSize)
        private val cloudIcon: ImageView = cloudView.findViewById(R.id.fileIcon)
        private val cloudOptionsMenu: TextView = cloudView.findViewById(R.id.fileOptionsMenu)

        fun bind(fileName: FileName){
//            val cloudFile = repo.getCloudFileByCid(fileName)
            val extensionCheck = fileName.name_slub.split('.')
            val extension = if(extensionCheck.isNotEmpty()) extensionCheck.last() else "unknown"
            val cloudFile = File("","",fileName.file_size,fileName.name_slub,extension,fileName.modified)
            cloudFile.let {
                cloudName.text = it.cloudName
                cloudModified.text = fileName.modified
                cloudSize.text = fileName.file_size.toString()

                when(it.extension.toLowerCase()){
                    "pdf" -> Glide.with(mContext.applicationContext).load(R.drawable.pdf).diskCacheStrategy(
                        DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(cloudIcon)
                    in listOf("xlsx","xls") -> Glide.with(mContext.applicationContext).load(R.drawable.excel).diskCacheStrategy(
                        DiskCacheStrategy.ALL).skipMemoryCache(true).into(cloudIcon)
                    in listOf("doc","docx") -> Glide.with(mContext.applicationContext).load(R.drawable.word).diskCacheStrategy(
                        DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(cloudIcon)
                    in listOf("ppt","pptx") -> Glide.with(mContext.applicationContext).load(R.drawable.powerpoint).diskCacheStrategy(
                        DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(cloudIcon)
                    "txt"  -> Glide.with(mContext.applicationContext).load(R.drawable.text).diskCacheStrategy(
                        DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(cloudIcon)
                    "rar"  -> Glide.with(mContext.applicationContext).load(R.drawable.rar).diskCacheStrategy(
                        DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(cloudIcon)
                    in listOf("zip","tar","tz") -> Glide.with(mContext.applicationContext).load(R.drawable.zip).diskCacheStrategy(
                        DiskCacheStrategy.ALL).skipMemoryCache(true).fitCenter().into(cloudIcon)
                    else -> {}
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CloudViewHolder {
        val cloudView =  LayoutInflater.from(parent.context)
            .inflate(R.layout.file_item, parent,false)

        return CloudViewHolder(cloudView)
    }

    override fun onBindViewHolder(holder: CloudViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charSearch = constraint.toString()
                if(charSearch.isEmpty()){
                    filteredFileNameList = fileNameList
                }else{
                    val resultSet = mutableSetOf<FileName>()
                    fileNameList.forEach {
                        val cid = it.CID
                        if(cid.contains(charSearch)){
                            resultSet.add(it)
                        }
                        val name = it.name_slub
                        if(name.replace("-"," ").contains(charSearch.toLowerCase())){
                            resultSet.add(it)
                        }

                    }
                    filteredFileNameList = resultSet.toList()
                }
                val filterResults = FilterResults()
                filterResults.values = filteredFileNameList
                filterResults.count = filteredFileNameList.size

                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredFileNameList = results?.values as List<FileName>
                submitList(filteredFileNameList)
            }
        }
    }
    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FileName>(){
            override fun areItemsTheSame(oldItem: FileName, newItem: FileName): Boolean {
                return oldItem.CID == newItem.CID && oldItem.name_slub == newItem.name_slub
            }

            override fun areContentsTheSame(oldItem: FileName, newItem: FileName): Boolean {
                return oldItem.status == newItem.status && oldItem.modified == newItem.modified && oldItem.modified_by == newItem.modified_by
            }
        }

    }
}