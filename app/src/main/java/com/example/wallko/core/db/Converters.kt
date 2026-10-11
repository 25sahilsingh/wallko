package com.example.wallko.core.db

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

/** List/map columns stored as JSON text. Enums use Room's built-in name mapping. */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun stringListToJson(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = json.decodeFromString(value)

    @TypeConverter
    fun stringMapToJson(value: Map<String, String>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToStringMap(value: String): Map<String, String> = json.decodeFromString(value)
}
