package ua.kyiv.putivnyk.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromDoubleList(value: List<Double>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toDoubleList(value: String): List<Double> {
        val type = object : TypeToken<List<Double>>() {}.type
        return gson.fromJson(value, type)
    }
}
