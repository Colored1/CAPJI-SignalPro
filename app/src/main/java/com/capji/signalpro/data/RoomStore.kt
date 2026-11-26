package com.capji.signalpro.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RoomReading(
    val name: String,
    val rssi: Int,
    val quality: Int,
    val ssid: String?,
    val band: String,
    val linkSpeedMbps: Int?,
    val timestamp: Long,
)

/** Saved room measurements; measuring a room again replaces its previous reading. */
class RoomStore(context: Context) {
    private val store = context.applicationContext.getSharedPreferences("capji_rooms", Context.MODE_PRIVATE)

    fun load(): List<RoomReading> = runCatching {
        val array = JSONArray(store.getString(KEY, "[]"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            RoomReading(
                name = item.getString("name"),
                rssi = item.getInt("rssi"),
                quality = item.getInt("quality"),
                ssid = item.optString("ssid").ifEmpty { null },
                band = item.optString("band"),
                linkSpeedMbps = item.optInt("speed", 0).takeIf { it > 0 },
                timestamp = item.getLong("time"),
            )
        }
    }.getOrDefault(emptyList())

    fun upsert(reading: RoomReading): List<RoomReading> =
        save(load().filterNot { it.name.equals(reading.name, ignoreCase = true) } + reading)

    fun delete(name: String): List<RoomReading> = save(load().filterNot { it.name == name })

    private fun save(readings: List<RoomReading>): List<RoomReading> {
        val array = JSONArray()
        readings.forEach {
            array.put(
                JSONObject()
                    .put("name", it.name)
                    .put("rssi", it.rssi)
                    .put("quality", it.quality)
                    .put("ssid", it.ssid.orEmpty())
                    .put("band", it.band)
                    .put("speed", it.linkSpeedMbps ?: 0)
                    .put("time", it.timestamp),
            )
        }
        store.edit().putString(KEY, array.toString()).apply()
        return readings
    }

    private companion object {
        const val KEY = "readings"
    }
}
