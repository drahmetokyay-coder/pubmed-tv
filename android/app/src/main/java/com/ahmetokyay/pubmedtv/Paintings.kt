package com.ahmetokyay.pubmedtv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Painting(
    val id: String,
    val title: String,
    val artist: String,
    val year: String,
    val museum: String,
    val url: String,
)

/** paintings.json ve tablo görselleri. Görsel bir kez indirilir, TV ekranına küçültülüp diskte saklanır. */
class PaintingStore(private val context: Context) {
    private val list = File(context.filesDir, "paintings.json")
    private val dir = File(context.filesDir, "paintings").apply { mkdirs() }

    fun load(): List<Painting> = runCatching { parse(list.readText()) }.getOrNull()
        ?: parse(context.assets.open("paintings.json").bufferedReader().use { it.readText() })

    /** ağ: liste güncellenirse true */
    fun refresh(): Boolean {
        val body = runCatching { download(LIST_URL).decodeToString() }
            .onFailure { Log.w(TAG, "paintings.json", it) }.getOrNull() ?: return false
        if (runCatching { parse(body) }.getOrNull().isNullOrEmpty()) return false
        if (list.exists() && list.readText() == body) return false
        list.writeText(body)
        return true
    }

    fun isCached(p: Painting) = file(p).exists()

    /** ağ: indirip 1920x1080'e sığacak şekilde küçültür ve saklar */
    fun fetch(p: Painting): Boolean {
        if (isCached(p)) return true
        return runCatching {
            val bytes = download(p.url)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_W || bounds.outHeight / (sample * 2) >= MAX_H) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("decode")
            val scale = minOf(1f, MAX_W.toFloat() / bmp.width, MAX_H.toFloat() / bmp.height)
            val out = if (scale < 1f)
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            else bmp
            val tmp = File(dir, "${p.id}.tmp")
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            tmp.renameTo(file(p))
            if (out !== bmp) bmp.recycle()
            out.recycle()
            true
        }.onFailure { Log.w(TAG, "fetch ${p.id}", it) }.getOrDefault(false)
    }

    fun bitmap(p: Painting): Bitmap? = BitmapFactory.decodeFile(file(p).path)

    private fun file(p: Painting) = File(dir, "${p.id}.jpg")

    private fun download(src: String): ByteArray {
        var url = URL(src)
        repeat(5) {  // Special:FilePath farklı alan adına yönlendirir
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.setRequestProperty("User-Agent", USER_AGENT)
            try {
                when (c.responseCode) {
                    200 -> return c.inputStream.use { it.readBytes() }
                    301, 302, 303, 307, 308 -> url = URL(url, c.getHeaderField("Location"))
                    else -> error("HTTP ${c.responseCode}")
                }
            } finally {
                c.disconnect()
            }
        }
        error("çok fazla yönlendirme")
    }

    companion object {
        private const val TAG = "PubMedTV"
        private const val MAX_W = 1920
        private const val MAX_H = 1080
        // Wikimedia kullanıcı aracısı politikası iletişim bilgisi ister
        private const val USER_AGENT = "PubMedTV/1.0 (https://github.com/drahmetokyay-coder/pubmed-tv; drahmetokyay@gmail.com)"
        private const val LIST_URL = "https://raw.githubusercontent.com/drahmetokyay-coder/pubmed-tv/main/data/paintings.json"

        fun parse(json: String): List<Painting> {
            val arr = JSONObject(json).getJSONArray("paintings")
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Painting(o.getString("id"), o.getString("title"), o.getString("artist"),
                    o.optString("year"), o.optString("museum"), o.getString("url"))
            }
        }
    }
}
