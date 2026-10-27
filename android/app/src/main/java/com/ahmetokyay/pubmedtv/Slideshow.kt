package com.ahmetokyay.pubmedtv

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.concurrent.Executors

/** Ekran koruyucu ve etkinliğin ortak slayt gösterisi. start()/stop() ile yaşam döngüsüne bağlanır. */
class Slideshow(private val context: Context) {
    val view: View = LayoutInflater.from(context).inflate(R.layout.slide, null)

    private val store = ArticleStore(context)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var articles: List<Article> = emptyList()
    private var queue = ArrayDeque<Article>()
    private var shown = 0
    private var running = false
    private var progressAnim: ObjectAnimator? = null

    private val card = view.findViewById<View>(R.id.card)
    private val badgeNew = view.findViewById<TextView>(R.id.badge_new)
    private val meta = view.findViewById<TextView>(R.id.meta)
    private val punch = view.findViewById<TextView>(R.id.punch)
    private val original = view.findViewById<TextView>(R.id.original)
    private val title = view.findViewById<TextView>(R.id.title)
    private val score = view.findViewById<TextView>(R.id.score)
    private val qr = view.findViewById<ImageView>(R.id.qr)
    private val counter = view.findViewById<TextView>(R.id.counter)
    private val progress = view.findViewById<View>(R.id.progress)

    private val next = Runnable { advance() }
    private val refresh = object : Runnable {
        override fun run() {
            io.execute {
                if (store.refresh()) main.post { articles = store.load(); queue.clear() }
            }
            main.postDelayed(this, REFRESH_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        articles = store.load()
        main.post(refresh)
        show(nextArticle(), animate = false)
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        progressAnim?.cancel()
    }

    fun release() {
        stop()
        io.shutdownNow()
    }

    private fun nextArticle(): Article {
        // her tur karışık sıra, tur bitmeden tekrar yok
        if (queue.isEmpty()) {
            queue = ArrayDeque(articles.shuffled())
            shown = 0
        }
        shown++
        return queue.removeFirst()
    }

    private fun advance() {
        if (!running) return
        val a = nextArticle()
        card.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            show(a, animate = true)
        }.start()
    }

    private fun show(a: Article, animate: Boolean) {
        badgeNew.visibility = if (a.isNew) View.VISIBLE else View.GONE
        meta.text = listOfNotNull(a.topic, a.studyType, a.journal, formatDate(a.date)).joinToString("  ·  ")
        punch.text = a.punchTr
        original.text = "“${a.conclusionEn}”"
        title.text = a.title
        score.text = if (a.isNew) "" else context.getString(R.string.citations, a.citations)
        score.visibility = if (a.isNew || a.citations == 0) View.INVISIBLE else View.VISIBLE
        qr.setImageBitmap(qrBitmap(a.url))
        counter.text = "$shown / ${articles.size}"
        if (animate) card.animate().alpha(1f).setDuration(FADE_MS).start() else card.alpha = 1f

        progressAnim?.cancel()
        progress.pivotX = 0f
        progress.scaleX = 0f
        progressAnim = ObjectAnimator.ofFloat(progress, View.SCALE_X, 0f, 1f).apply {
            duration = SLIDE_MS
            interpolator = LinearInterpolator()
            start()
        }
        main.removeCallbacks(next)
        main.postDelayed(next, SLIDE_MS)
    }

    private fun qrBitmap(text: String): Bitmap {
        val size = 400
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        val px = IntArray(size * size) { if (m[it % size, it / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.RGB_565)
    }

    private fun formatDate(ym: String): String? {
        val parts = ym.split("-")
        if (parts.size < 2) return null
        val month = MONTHS.getOrNull(parts[1].toIntOrNull()?.minus(1) ?: -1) ?: return parts[0]
        return "$month ${parts[0]}"
    }

    companion object {
        const val SLIDE_MS = 30_000L
        const val FADE_MS = 500L
        const val REFRESH_MS = 6 * 60 * 60 * 1000L
        private val MONTHS = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
    }
}
