package com.ahmetokyay.pubmedtv

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

/** Slaytları elle açmak için; geri tuşu ile çıkılır. */
class MainActivity : Activity() {
    private lateinit var slideshow: Slideshow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // adb shell am start -n com.ahmetokyay.pubmedtv/.MainActivity --ez demo true
        slideshow = Slideshow(this, demo = intent.getBooleanExtra("demo", false))
        setContentView(slideshow.view)
    }

    override fun onStart() {
        super.onStart()
        slideshow.start()
    }

    override fun onStop() {
        slideshow.stop()
        super.onStop()
    }

    override fun onDestroy() {
        slideshow.release()
        super.onDestroy()
    }
}
