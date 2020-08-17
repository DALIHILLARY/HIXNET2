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
import ug.hix.hixnet2.R
import kotlinx.android.synthetic.main.fragment_files.*
import droidninja.filepicker.*
import droidninja.filepicker.utils.ContentUriUtils
import java.io.File

class FileFragment(val mContext: Context) : Fragment() {

    var mediaPaths = arrayListOf<Uri>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_files, container, false)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileUploadfab?.setOnClickListener {
            FilePickerBuilder.instance.enableVideoPicker(true)
                .enableDocSupport(true)
                .pickFile(this)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode){
            FilePickerConst.REQUEST_CODE_PHOTO -> {
                if(resultCode == Activity.RESULT_OK && data != null){
                    mediaPaths.addAll(data.getParcelableArrayListExtra<Uri>(FilePickerConst.KEY_SELECTED_MEDIA).toList())
                }
            }
            FilePickerConst.REQUEST_CODE_DOC -> {
                if(resultCode == Activity.RESULT_OK && data != null ){
                    mediaPaths.addAll(data.getParcelableArrayListExtra<Uri>(FilePickerConst.KEY_SELECTED_DOCS).toList());

                }
            }
        }
        getPaths(mContext)
    }

    fun getPaths(mContext : Context){
        mediaPaths.forEach{
            val filePath = ContentUriUtils.getFilePath(mContext,it)
            Log.i("fILESFragment",filePath!!)
        }
    }


}
