package ug.hix.hixnet2.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import ug.hix.hixnet2.R
import kotlinx.android.synthetic.main.fragment_files.*
import droidninja.filepicker.*
import droidninja.filepicker.utils.ContentUriUtils

import ug.hix.hixnet2.adapter.FileFragAdapter
import ug.hix.hixnet2.database.File
import ug.hix.hixnet2.util.OnSwipeTouchListener
import ug.hix.hixnet2.viewmodel.FileViewModel
import ug.hix.hixnet2.workers.SendFileWorker
import ug.hix.hixnet2.workers.UploadWorker

class FileFragment(val mContext: Context) : Fragment() {
    var mediaUri = arrayListOf<Uri>()
    lateinit var fileViewModel : FileViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_files, container, false)

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        filesRecycleView.setOnTouchListener(object : OnSwipeTouchListener(activity){
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                filesSideBar.visibility = View.VISIBLE
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                filesSideBar.visibility = View.GONE
            }
        })

        val fileAdapter = FileFragAdapter(mContext)
        filesRecycleView.apply{
            layoutManager = LinearLayoutManager(activity)
            adapter   = fileAdapter
        }
        fileViewModel = ViewModelProvider(this).get(FileViewModel::class.java)
        fileViewModel.getFiles(mContext).observe(viewLifecycleOwner,
            Observer<List<File>> {
                fileAdapter.setFiles(it)
            })

        val zipTypes = arrayOf("zip","rar","apk","tar","tz")
        uploadDoc.setOnClickListener {
            FilePickerBuilder.instance
                .enableDocSupport(true)
                .addFileSupport("Others",zipTypes,R.drawable.zip)
                .pickFile(this)
        }
        uploadMedia.setOnClickListener {
            FilePickerBuilder.instance
                .enableVideoPicker(true)
                .enableSelectAll(true)
                .showGifs(true)
                .pickPhoto(this)
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        mediaUri.clear()
        when(requestCode){
            FilePickerConst.REQUEST_CODE_PHOTO -> {
                if(resultCode == Activity.RESULT_OK && data != null){
                    data.getParcelableArrayListExtra<Uri>(FilePickerConst.KEY_SELECTED_MEDIA)?.toList()?.let {
                        mediaUri.addAll(
                            it
                        )
                    }
                }
            }
            FilePickerConst.REQUEST_CODE_DOC -> {
                if(resultCode == Activity.RESULT_OK && data != null ){
                    data.getParcelableArrayListExtra<Uri>(FilePickerConst.KEY_SELECTED_DOCS)?.toList()?.let {
                        mediaUri.addAll(
                            it
                        )
                    };

                }
            }
        }
        if(mediaUri.isNotEmpty()){
            fileViewModel.uploadFile(mediaUri,mContext.applicationContext)

        }
    }

    companion object {
        fun newFileInstance(context: Context) : FileFragment{
            return FileFragment(context)
        }
    }

}
