package ug.hix.hixnet2.util

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*

class Util {

    fun slub(rawString: String) : String {
        return rawString.replace(" ", "-").toLowerCase(Locale.ROOT)
    }
    @SuppressLint("SimpleDateFormat")
    fun currentDateTime(): String{
        val calender = Calendar.getInstance()
        val format = SimpleDateFormat("yyyy/mm/dd hh:mm:ss.SSS")
        return format.format(calender.time)
    }
}