package ug.hix.hixnet2.util

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*

class Util {

    companion object {
        fun slub(rawString: String) : String {
            return rawString.replace(" ", "-").toLowerCase(Locale.ROOT)
        }
        @SuppressLint("SimpleDateFormat")
        fun currentDateTime(): String{
            val calender = Calendar.getInstance()
            val format = SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS")
            return format.format(calender.time)
        }
    }

}