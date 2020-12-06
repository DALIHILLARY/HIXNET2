package ug.hix.hixnet2.fragments

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import ug.hix.hixnet2.R

import kotlinx.android.synthetic.main.fragment_cloud.*
import ug.hix.hixnet2.adapter.CloudFragAdapter
import ug.hix.hixnet2.services.MeshDaemon


class CloudFragment(private val mContext: Context) : Fragment() {
    val cloudAdapter = CloudFragAdapter(mContext)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cloudAdapter.updateCloudFiles(MeshDaemon.filesHashMap)
        retainInstance = true
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_cloud, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        cloudFile.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = cloudAdapter
        }
        cloud_search.setOnQueryTextListener( object: SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                cloudAdapter.filter.filter(newText)
                return false
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        Glide.get(mContext).clearMemory()
    }

    companion object {

        @JvmStatic
        fun newInstance(mContext: Context) = CloudFragment(mContext)
    }
}