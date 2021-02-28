package ug.hix.hixnet2.fragments

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import ug.hix.hixnet2.R

import kotlinx.android.synthetic.main.fragment_cloud.*
import ug.hix.hixnet2.adapter.CloudFragAdapter
import ug.hix.hixnet2.database.FileName
import ug.hix.hixnet2.viewmodel.CloudFileViewModel


class CloudFragment(private val mContext: Context) : Fragment() {
    lateinit var cloudFileViewModel: CloudFileViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        cloudAdapter.updateCloudFiles(MeshDaemon.filesHashMap)
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
        val cloudAdapter = CloudFragAdapter(mContext)
        cloudFile.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = cloudAdapter
        }
        cloudFileViewModel = ViewModelProvider(this).get(CloudFileViewModel::class.java)
        cloudFileViewModel.getCloudFiles(mContext).observe(viewLifecycleOwner,
            Observer<List<FileName>> {
                cloudAdapter.submitList(it)
            }
        )
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