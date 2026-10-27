package com.ahmetokyay.pubmedtv

import android.service.dreams.DreamService
import android.view.KeyEvent

class PubMedDream : DreamService() {
    private var slideshow: Slideshow? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true   // sağ/sol ok slayt değiştirir, diğer tuşlar çıkar
        isFullscreen = true
        isScreenBright = true
        slideshow = Slideshow(this).also { setContentView(it.view) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE ->
                return super.dispatchKeyEvent(event)
        }
        val nav = event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
        if (!nav) {
            // yalnızca basışta çık: koruyucu açılmadan önce basılmış bir tuşun bırakılması çıkarmasın
            if (event.action == KeyEvent.ACTION_DOWN) finish()
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) slideshow?.next() else slideshow?.previous()
        }
        return true
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        slideshow?.start()
    }

    override fun onDreamingStopped() {
        slideshow?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        slideshow?.release()
        slideshow = null
        super.onDetachedFromWindow()
    }
}
