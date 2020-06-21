package ug.hix.hixnet2.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ServiceConverter {
    @TypeConverter
    fun toService(json : String) : List<Services> {
        val type = object : TypeToken<List<Services>>(){}.type
        return Gson().fromJson(json,type)
    }

    @TypeConverter
    fun toJson(service : List<Services>) : String {
        val type = object : TypeToken<List<Services>>(){}.type
        return Gson().toJson(service,type)
    }

}