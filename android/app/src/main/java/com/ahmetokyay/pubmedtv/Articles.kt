package com.ahmetokyay.pubmedtv

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Article(
    val pmid: String,
    val url: String,
    val topic: String,
    val journal: String,
    val date: String,
    val citations: Int,
    val isNew: Boolean,
    val studyType: String?,
    val title: String,
    val punchTr: String,
    val conclusionEn: String,
)

/** articles.json: GitHub'dan çek, cihazda sakla; ağ yoksa son kopya, o da yoksa APK içindeki kopya. */
class ArticleStore(private val context: Context) {
    private val cache = File(context.filesDir, "articles.json")

    fun load(): List<Article> = runCatching { parse(cache.readText()) }.getOrNull()
        ?: parse(context.assets.open("articles.json").bufferedReader().use { it.readText() })

    /** ağ çağrısı: ana iş parçacığında çağırma. Yeni liste gelirse true. */
    fun refresh(): Boolean {
        for (src in SOURCES) {
            val body = runCatching { download(src) }.onFailure { Log.w(TAG, "fetch $src", it) }.getOrNull() ?: continue
            val parsed = runCatching { parse(body) }.getOrNull()
            if (parsed.isNullOrEmpty()) continue
            if (cache.exists() && cache.readText() == body) return false
            cache.writeText(body)
            return true
        }
        return false
    }

    private fun download(src: String): String {
        val c = URL(src).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        return try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val TAG = "PubMedTV"
        // raw.githubusercontent ~5 dk önbellek; jsDelivr yedek
        private val SOURCES = listOf(
            "https://raw.githubusercontent.com/drahmetokyay-coder/pubmed-tv/main/data/articles.json",
            "https://cdn.jsdelivr.net/gh/drahmetokyay-coder/pubmed-tv@main/data/articles.json",
        )

        fun parse(json: String): List<Article> {
            val arr = JSONObject(json).getJSONArray("articles")
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Article(
                    pmid = o.getString("pmid"),
                    url = o.getString("url"),
                    topic = o.getString("topic"),
                    journal = o.getString("journal"),
                    date = o.optString("date"),
                    citations = o.optInt("citations"),
                    isNew = o.optBoolean("isNew"),
                    studyType = o.optString("studyType").takeIf { it.isNotEmpty() && it != "null" },
                    title = o.getString("title"),
                    punchTr = o.getString("punchTr"),
                    conclusionEn = o.getString("conclusionEn"),
                )
            }
        }
    }
}
