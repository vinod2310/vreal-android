package com.vreal.kotlinqrscanner

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class ViewVideo : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_view_video)

        // assigning id of VideoView from
        // activity_main.xml layout file
        val simpleVideoView: VideoView = findViewById<View>(R.id.simpleVideoView) as VideoView

        val mediaControls =  MediaController(this)
        // set the anchor view for the video view
        mediaControls.setAnchorView(simpleVideoView)

        // set the media controller for video view
        simpleVideoView.setMediaController(mediaControls)
        val extras = intent.extras
        val videoUrl = extras?.getString("url")

        // set the absolute path of the video file which is going to be played
        simpleVideoView.setVideoURI(
            Uri.parse(videoUrl)
        )
        simpleVideoView.setOnPreparedListener {
            simpleVideoView.requestFocus()
            // starting the video
            simpleVideoView.start()
            }

            // display a toast message
            // after the video is completed
            simpleVideoView.setOnCompletionListener {
                Toast.makeText(
                    applicationContext, "Video completed",
                    Toast.LENGTH_LONG
                ).show()
                true
            }

            // display a toast message if any
            // error occurs while playing the video
            simpleVideoView.setOnErrorListener { mp, what, extra ->
                Toast.makeText(
                    applicationContext, "An Error Occurred " +
                            "While Playing Video !!!", Toast.LENGTH_LONG
                ).show()
                false
            }
        }
}

