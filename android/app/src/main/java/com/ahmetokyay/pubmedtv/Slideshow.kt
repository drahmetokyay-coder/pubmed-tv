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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.concurrent.Executors

/** Ekran koruyucu ve etkinliğin ortak slayt gösterisi: 1 makale, ardından PAINTINGS_PER_ARTICLE tablo.
 *  start()/stop() ile yaşam döngüsüne bağlanır. */
class Slideshow(private val context: Context, demo: Boolean = false) {
    private val articleMs = if (demo) 10_000L else ARTICLE_MS
    private val paintingMs = if (demo) 15_000L else PAINTING_MS

    val view = FrameLayout(context)
    private val articleView: View = LayoutInflater.from(context).inflate(R.layout.slide, view, false)
    private val paintingView: View = LayoutInflater.from(context).inflate(R.layout.painting, view, false)

    private val store = ArticleStore(context)
    private val paintingStore = PaintingStore(context)
    private var paintings: List<Painting> = emptyList()
    private var paintingQueue = ArrayDeque<Painting>()
    private var step = 0  // 0 = makale, 1..PAINTINGS_PER_ARTICLE = tablo
    private var currentBitmap: Bitmap? = null
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var articles: List<Article> = emptyList()
    private var queue = ArrayDeque<Article>()
    private var shown = 0
    private var running = false
    private var progressAnim: ObjectAnimator? = null

    private val card = articleView.findViewById<View>(R.id.card)
    private val badgeNew = articleView.findViewById<TextView>(R.id.badge_new)
    private val meta = articleView.findViewById<TextView>(R.id.meta)
    private val punch = articleView.findViewById<TextView>(R.id.punch)
    private val original = articleView.findViewById<TextView>(R.id.original)
    private val title = articleView.findViewById<TextView>(R.id.title)
    private val score = articleView.findViewById<TextView>(R.id.score)
    private val qr = articleView.findViewById<ImageView>(R.id.qr)
    private val counter = articleView.findViewById<TextView>(R.id.counter)
    private val progress = articleView.findViewById<View>(R.id.progress)

    private val paintingCard = paintingView.findViewById<View>(R.id.painting_card)
    private val paintingImage = paintingView.findViewById<ImageView>(R.id.painting_image)
    private val paintingTitle = paintingView.findViewById<TextView>(R.id.painting_title)
    private val paintingMeta = paintingView.findViewById<TextView>(R.id.painting_meta)
    private val paintingProgress = paintingView.findViewById<View>(R.id.painting_progress)

    init {
        view.addView(articleView)
        view.addView(paintingView)
        paintingView.visibility = View.GONE
    }

    private val next = Runnable { advance() }
    private val refresh = object : Runnable {
        override fun run() {
            io.execute {
                if (store.refresh()) main.post { articles = store.load(); queue.clear() }
                if (paintingStore.refresh()) main.post { paintings = paintingStore.load(); paintingQueue.clear() }
            }
            main.postDelayed(this, REFRESH_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        articles = store.load()
        paintings = paintingStore.load()
        main.post(refresh)
        step = 0
        show(nextArticle(), animate = false)
        prefetch()
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        progressAnim?.cancel()
    }

    fun release() {
        stop()
        io.shutdownNow()
        paintingImage.setImageDrawable(null)
        currentBitmap?.recycle()
        currentBitmap = null
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
        step = (step + 1) % (PAINTINGS_PER_ARTICLE + 1)
        val painting = if (step == 0) null else nextCachedPainting()
        if (painting == null) step = 0  // indirilmiş tablo yoksa makaleye geç
        val outgoing = if (articleView.visibility == View.VISIBLE) card else paintingCard
        outgoing.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            if (painting == null) show(nextArticle(), animate = true) else showPainting(painting)
            prefetch()
        }.start()
    }

    private fun nextCachedPainting(): Painting? {
        if (paintingQueue.isEmpty()) paintingQueue = ArrayDeque(paintings.shuffled())
        // sıradakilerden diskte olan ilkini al; olmayanlar sırada kalır, prefetch indirir
        val p = paintingQueue.firstOrNull { paintingStore.isCached(it) } ?: return null
        paintingQueue.remove(p)
        return p
    }

    /** sıradaki birkaç tabloyu arka planda indir */
    private fun prefetch() {
        val upcoming = paintingQueue.ifEmpty { ArrayDeque(paintings.shuffled()).also { paintingQueue = it } }
            .filterNot { paintingStore.isCached(it) }.take(PREFETCH)
        if (upcoming.isNotEmpty()) io.execute { upcoming.forEach { paintingStore.fetch(it) } }
    }

    private fun showPainting(p: Painting) {
        val bmp = paintingStore.bitmap(p) ?: return show(nextArticle(), animate = true)
        articleView.visibility = View.GONE
        paintingView.visibility = View.VISIBLE
        paintingImage.setImageBitmap(bmp)
        currentBitmap?.recycle()
        currentBitmap = bmp
        paintingTitle.text = p.title
        paintingMeta.text = listOf(p.artist, p.year, p.museum).filter { it.isNotBlank() }.joinToString("  ·  ")
        paintingCard.alpha = 0f
        paintingCard.animate().alpha(1f).setDuration(FADE_MS * 3).start()
        runProgress(paintingProgress, paintingMs)
    }

    private fun show(a: Article, animate: Boolean) {
        paintingView.visibility = View.GONE
        articleView.visibility = View.VISIBLE
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

        runProgress(progress, articleMs)
    }

    private fun runProgress(bar: View, durationMs: Long) {
        progressAnim?.cancel()
        bar.pivotX = 0f
        bar.scaleX = 0f
        progressAnim = ObjectAnimator.ofFloat(bar, View.SCALE_X, 0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            start()
        }
        main.removeCallbacks(next)
        main.postDelayed(next, durationMs)
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
        const val ARTICLE_MS = 2 * 60 * 1000L
        const val PAINTING_MS = 15 * 60 * 1000L
        const val PAINTINGS_PER_ARTICLE = 5
        private const val PREFETCH = 3
        const val FADE_MS = 500L
        const val REFRESH_MS = 6 * 60 * 60 * 1000L
        private val MONTHS = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
    }
}
