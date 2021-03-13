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
import kotlinx.android.synthetic.main.fragment_device.*
import ug.hix.hixnet2.R
import ug.hix.hixnet2.adapter.DeviceFragAdapter
import ug.hix.hixnet2.database.DeviceNode
import ug.hix.hixnet2.util.OnSwipeTouchListener
import ug.hix.hixnet2.viewmodel.DeviceViewModel


class DeviceFragment (val mContext: Context): Fragment() {

    lateinit var deviceViewModel: DeviceViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        devicesRecycleView.setOnTouchListener(object : OnSwipeTouchListener(activity){
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                devicesSideBar.visibility = View.VISIBLE
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                devicesSideBar.visibility = View.GONE
            }
        })

        val deviceAdapter = DeviceFragAdapter(mContext)
        devicesRecycleView.apply{
            layoutManager = LinearLayoutManager(activity)
            adapter = deviceAdapter
        }
        deviceViewModel = ViewModelProvider(this).get(DeviceViewModel::class.java)
        deviceViewModel.getActiveDeviceLiveData(mContext).observe(viewLifecycleOwner,
            Observer<List<DeviceNode>> {
                deviceAdapter.submitList(it)
            })

        uploadDoc.setOnClickListener {
//            TODO('GET MY PROFILE')
        }
        uploadMedia.setOnClickListener {
//            TODO('SCAN TO ADD DEVICE')
        }

        device_search.setOnQueryTextListener( object: SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                deviceAdapter.filter.filter(newText)
                return false
            }
        })
    }

    companion object {
        @JvmStatic fun newInstance(mContext: Context) = DeviceFragment(mContext)
    }
}