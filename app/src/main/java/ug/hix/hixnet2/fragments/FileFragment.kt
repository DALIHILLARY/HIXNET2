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
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.Observer
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
import ug.hix.hixnet2.viewmodel.FileViewModel
import ug.hix.hixnet2.workers.UploadWorker

class FileFragment(val mContext: Context) : Fragment() {
    var mediaUri = arrayListOf<Uri>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_files, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fileViewModel = ViewModelProvider(this).get(FileViewModel::class.java)
        fileViewModel.getFiles(mContext).observe(viewLifecycleOwner,
            Observer<List<ug.hix.hixnet2.database.File>> {
                Toast.makeText(mContext,"datacahnged",Toast.LENGTH_SHORT).show()
                //TODO("upadet recycleview")
            })
//        filesRecycleView.apply{
//            layoutManager = LinearLayoutManager(mContext)
//            adapter   = FileFragAdapter(hixtestData)
//        }


        fileUploadfab?.setOnClickListener {
            FilePickerBuilder.instance.enableVideoPicker(true)
                .enableDocSupport(true)
                .pickFile(this)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        mediaUri.clear()
        when(requestCode){
            FilePickerConst.REQUEST_CODE_PHOTO -> {
                if(resultCode == Activity.RESULT_OK && data != null){
                    mediaUri.addAll(data.getParcelableArrayListExtra<Uri>(FilePickerConst.KEY_SELECTED_MEDIA).toList())
                }
            }
            FilePickerConst.REQUEST_CODE_DOC -> {
                if(resultCode == Activity.RESULT_OK && data != null ){
                    mediaUri.addAll(data.getParcelableArrayListExtra<Uri>(FilePickerConst.KEY_SELECTED_DOCS).toList());

                }
            }
        }

        uploadFile(mediaUri)
    }


    private fun uploadFile(selectedPaths: ArrayList<Uri>){
        val filePaths  = arrayListOf<String>()

        selectedPaths.forEach{uri ->
            ContentUriUtils.getFilePath(mContext,uri)?.let { filePaths.add(it) }
        }
        val filepaths = filePaths.toTypedArray()

       val uploadWorker = OneTimeWorkRequestBuilder<UploadWorker>()
           .setInputData(workDataOf("filePaths" to filepaths))
           .build()

        WorkManager.getInstance(mContext.applicationContext).enqueue(uploadWorker)
    }

}
