package ug.hix.hixnet2.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import ug.hix.hixnet2.R

class CloudFragAdapter(private val mContext: Context) : RecyclerView.Adapter<CloudFragAdapter.CloudViewHolder>(), Filterable{
    var fileHashMap = mutableMapOf<String,MutableMap<String,MutableList<String>>>()
    var filteredFileHashMap: MutableMap<String,MutableMap<String,MutableList<String>>>
    init {
        filteredFileHashMap = fileHashMap
    }

    inner class CloudViewHolder(cloudView: View) : RecyclerView.ViewHolder(cloudView){
        private val cloudName: TextView = cloudView.findViewById(R.id.fileName)
        private val cloudModified: TextView = cloudView.findViewById(R.id.fileModifiedDate)
        private val cloudSize: TextView  = cloudView.findViewById(R.id.fileSize)
        private val cloudIcon: ImageView = cloudView.findViewById(R.id.fileIcon)
        private val cloudOptionsMenu: TextView = cloudView.findViewById(R.id.fileOptionsMenu)

        fun bind(pair: Pair<String,MutableMap<String,MutableList<String>>>){
            val cid = pair.first
            val properties = pair.second

            cloudName.text = properties["Name"]!![0]
            cloudModified.text = properties["Date"]!![0]
            cloudSize.text = properties["Size"]!![0]

            when(properties["Extension"]!![0].toLowerCase()){
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
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CloudViewHolder {
        val cloudView =  LayoutInflater.from(parent.context)
            .inflate(R.layout.file_item, parent,false)

        return CloudViewHolder(cloudView)
    }

    override fun getItemCount(): Int {
        return filteredFileHashMap.size
    }

    override fun onBindViewHolder(holder: CloudViewHolder, position: Int) {
        val filteredHashMapList = filteredFileHashMap.toList()
        holder.bind(filteredHashMapList[position])
    }

    fun updateCloudFiles(hashMap: MutableMap<String,MutableMap<String,MutableList<String>>>){
        fileHashMap = hashMap
        filteredFileHashMap = hashMap
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charSearch = constraint.toString()
                if(charSearch.isEmpty()){
                    filteredFileHashMap = fileHashMap
                }else{
                    val CIDs = fileHashMap.keys
                    val resultHashMap = mutableMapOf<String,MutableMap<String,MutableList<String>>>()
                    CIDs.forEach { cid ->
                        if(cid.contains(charSearch)){
                            resultHashMap[cid] = fileHashMap[cid]!!
                        }
                        val names = fileHashMap[cid]?.get("Name")!!
                        names.forEach { cloudName ->
                            if(cloudName.toLowerCase().contains(charSearch.toLowerCase())){
                                resultHashMap[cid] = fileHashMap[cid]!!
                            }
                        }

                    }
                    filteredFileHashMap = resultHashMap
                }
                val filterResults = FilterResults()
                filterResults.values = filteredFileHashMap
                filterResults.count = filteredFileHashMap.size

                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredFileHashMap = results?.values as MutableMap<String,MutableMap<String,MutableList<String>>>
                notifyDataSetChanged()
            }
        }
    }
}