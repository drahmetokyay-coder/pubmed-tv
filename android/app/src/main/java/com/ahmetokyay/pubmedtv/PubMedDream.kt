package com.ahmetokyay.pubmedtv

import android.service.dreams.DreamService

class PubMedDream : DreamService() {
    private var slideshow: Slideshow? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false   // herhangi bir tuş ekran koruyucudan çıkar
        isFullscreen = true
        isScreenBright = true
        slideshow = Slideshow(this).also { setContentView(it.view) }
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
