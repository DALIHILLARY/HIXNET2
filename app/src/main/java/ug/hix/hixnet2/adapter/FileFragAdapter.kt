package ug.hix.hixnet2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ug.hix.hixnet2.R
import ug.hix.hixnet2.database.File

class FileFragAdapter(private val fileSet: Array<File>) : RecyclerView.Adapter<FileFragAdapter.FileFragViewHolder>() {


    class FileFragViewHolder(fileView: View) : RecyclerView.ViewHolder(fileView) {
        val fileIcon: ImageView = fileView.findViewById<ImageView>(R.id.fileIcon)
        val fileName = fileView.findViewById<TextView>(R.id.fileName)
        val fileSize = fileView.findViewById<TextView>(R.id.fileSize)


    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileFragViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.file_item, parent,false)

        return FileFragViewHolder(v)
    }

    override fun getItemCount() = fileSet.size

    override fun onBindViewHolder(holder: FileFragViewHolder, position: Int) {
        holder.fileName.text = fileSet[position].cloudName
        holder.fileSize.text = fileSet[position].modified
    }

}