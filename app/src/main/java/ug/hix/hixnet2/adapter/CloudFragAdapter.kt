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

            when(properties["Extension"]!![0]){
                "pdf" -> Glide.with(mContext).load(R.drawable.pdf).fitCenter().into(cloudIcon)
                in listOf("xlsx","xls") -> Glide.with(mContext).load(R.drawable.excel).fitCenter().into(cloudIcon)
                in listOf("doc","docx") -> Glide.with(mContext).load(R.drawable.word).fitCenter().into(cloudIcon)
                in listOf("ppt","pptx") -> Glide.with(mContext).load(R.drawable.powerpoint).fitCenter().into(cloudIcon)
                "txt"  -> Glide.with(mContext).load(R.drawable.text).fitCenter().into(cloudIcon)
                "rar"  -> Glide.with(mContext).load(R.drawable.rar).fitCenter().into(cloudIcon)
                in listOf("zip","tar","tz") -> Glide.with(mContext).load(R.drawable.zip).fitCenter().into(cloudIcon)
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