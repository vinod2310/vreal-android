package com.vreal.kotlinqrscanner

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.MediaController
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class ViewVrealVideoWebView : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_view_video_webview)
        lateinit var simpleWebView: WebView
        lateinit var mediaControls: MediaController

        // assigning id of VideoView from
        // activity_main.xml layout file
        simpleWebView = findViewById<View>(R.id.simpleVideoViewWeb) as WebView

        simpleWebView.loadUrl("https://example.com/stream.mjpeg");


        mediaControls = MediaController(this)
        // set the anchor view for the video view
        mediaControls.setAnchorView(simpleWebView)

        // set the media controller for video view

    }
}

