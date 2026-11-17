package test.smartlinksoft

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A writer takes a sample and does one thing with it.
 * The dropdown picks which one is live. Add a new object
 * to HealthWriters.all when a new target is needed.
 */
interface HealthWriter {
    val id: String
    val label: String
    fun write(sample: HealthSample, ctx: Context): String
}

/** CSV in the app's files dir. Always works. */
object FileCsvWriter : HealthWriter {
    override val id = "file_csv"
    override val label = "file (csv)"

    override fun write(sample: HealthSample, ctx: Context): String {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val f = File(dir, "health.csv")
        val header = !f.exists()
        FileOutputStream(f, true).use { out ->
            if (header) out.write("ts,bpm,spo2,rmssd,note\n".toByteArray())
            val line = "${sample.timestampMs},${sample.bpm ?: ""},${sample.spo2 ?: ""},${sample.rmssdMs ?: ""},${sample.note ?: ""}\n"
            out.write(line.toByteArray())
        }
        return "wrote ${f.name}"
    }
}

/** JSON lines. Same dir, different shape. */
object FileJsonWriter : HealthWriter {
    override val id = "file_json"
    override val label = "file (json)"

    override fun write(sample: HealthSample, ctx: Context): String {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val f = File(dir, "health.jsonl")
        val json = buildString {
            append("{\"ts\":").append(sample.timestampMs)
            sample.bpm?.let { append(",\"bpm\":").append(it) }
            sample.spo2?.let { append(",\"spo2\":").append(it) }
            sample.rmssdMs?.let { append(",\"rmssd\":").append(it) }
            sample.note?.let { append(",\"note\":\"").append(it.replace("\"", "\\\"")).append("\"") }
            append("}\n")
        }
        FileOutputStream(f, true).use { it.write(json.toByteArray()) }
        return "wrote ${f.name}"
    }
}

/**
 * Health Connect target. The SDK is not linked yet.
 * When it is, this becomes insertRecords of HeartRateRecord /
 * OxygenSaturationRecord / HeartRateVariabilityRmssdRecord.
 * Until then it records what it would have written.
 */
object HealthConnectWriter : HealthWriter {
    override val id = "health_connect"
    override val label = "health connect"

    override fun write(sample: HealthSample, ctx: Context): String {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val f = File(dir, "health_connect_pending.jsonl")
        val json = buildString {
            append("{\"ts\":").append(sample.timestampMs)
            sample.bpm?.let { append(",\"HeartRateRecord\":").append(it) }
            sample.spo2?.let { append(",\"OxygenSaturationRecord\":").append(it) }
            sample.rmssdMs?.let { append(",\"HeartRateVariabilityRmssdRecord\":").append(it) }
            append("}\n")
        }
        FileOutputStream(f, true).use { it.write(json.toByteArray()) }
        return "queued for health connect (sdk not linked)"
    }
}

/**
 * Custom HTTP endpoint. URL is stored in prefs under "health_url".
 * Stub for now: writes the payload that would be POSTed.
 */
object CustomEndpointWriter : HealthWriter {
    override val id = "custom"
    override val label = "custom endpoint"

    override fun write(sample: HealthSample, ctx: Context): String {
        val url = ctx.getSharedPreferences("smartlink", Context.MODE_PRIVATE)
            .getString("health_url", "") ?: ""
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val f = File(dir, "health_custom.jsonl")
        val json = "{\"url\":\"$url\",\"ts\":${sample.timestampMs},\"bpm\":${sample.bpm},\"spo2\":${sample.spo2}}\n"
        FileOutputStream(f, true).use { it.write(json.toByteArray()) }
        return if (url.isEmpty()) "no url set (prefs health_url)" else "queued for $url"
    }
}

object HealthWriters {
    val all: List<HealthWriter> = listOf(
        FileCsvWriter,
        FileJsonWriter,
        HealthConnectWriter,
        CustomEndpointWriter,
    )

    fun byId(id: String): HealthWriter =
        all.firstOrNull { it.id == id } ?: FileCsvWriter
}
