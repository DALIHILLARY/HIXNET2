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
import ug.hix.hixnet2.R
import ug.hix.hixnet2.database.DeviceNode
import ug.hix.hixnet2.repository.Repository

class DeviceFragAdapter(private val mContext: Context) : ListAdapter<DeviceNode, DeviceFragAdapter.DeviceFragViewHolder>(
    DIFF_CALLBACK), Filterable{
    private val repo = Repository.getInstance(mContext)
    private var filteredDeviceList = listOf<DeviceNode>()
    private val deviceList = repo.activeDevices()
    private val TAG = javaClass.simpleName
    
    inner class DeviceFragViewHolder(deviceView: View) : RecyclerView.ViewHolder(deviceView){
        private val deviceIcon: ImageView = deviceView.findViewById(R.id.deviceIcon)
        private var deviceName: TextView = deviceView.findViewById(R.id.deviceName)
        private var deviceSize: TextView = deviceView.findViewById(R.id.deviceSize)
        private val deviceModified: TextView = deviceView.findViewById(R.id.deviceModifiedDate)
        private val deviceOptionsMenu: TextView = deviceView.findViewById(R.id.deviceOptionsMenu)
        
        fun bind(device: DeviceNode) {
            deviceName.text = if (device.rName.isNotEmpty()) device.rName
                            else device.meshID
            deviceSize.text = device.version
            deviceModified.text = device.modified
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DeviceNode>(){
            override fun areItemsTheSame(oldItem: DeviceNode, newItem: DeviceNode): Boolean {
                return oldItem.meshID == newItem.meshID && oldItem.multicastAddress == newItem.multicastAddress && oldItem.hops == newItem.hops
            }

            override fun areContentsTheSame(oldItem: DeviceNode, newItem: DeviceNode): Boolean {
                return oldItem.modified == newItem.modified && oldItem.iface == newItem.iface && oldItem.status == newItem.status && oldItem.version == newItem.version && oldItem.rName == newItem.rName && oldItem.hasInternetWifi == newItem.hasInternetWifi
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceFragViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.device_item,parent,false)
        return DeviceFragViewHolder(v)
    }

    override fun onBindViewHolder(holder: DeviceFragViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val charSearch = constraint.toString()
                if(charSearch.isEmpty()){
                    filteredDeviceList = deviceList
                }else{
                    val resultSet = mutableSetOf<DeviceNode>()
                    deviceList.forEach {
                        val meshId = it.meshID
                        val name = it.rName
                        if(meshId.contains(charSearch)) resultSet.add(it)
                        if(name.contains(charSearch)) resultSet.add(it)
                    }
                    filteredDeviceList = resultSet.toList()
                }
                val filterResults = FilterResults()
                filterResults.values = filteredDeviceList
                filterResults.count = filteredDeviceList.size

                return filterResults
            }
            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredDeviceList = results?.values as List<DeviceNode>
                submitList(filteredDeviceList)
            }

        }
    }
}