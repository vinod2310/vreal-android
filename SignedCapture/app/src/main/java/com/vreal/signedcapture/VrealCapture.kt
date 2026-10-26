@file:OptIn(ExperimentalMaterial3Api::class)

package com.vreal.signedcapture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Chronometer
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresPermission
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import androidx.core.net.toUri
import androidx.core.content.edit
import java.net.URL
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.withContext


class VrealCapture : ComponentActivity() {
    lateinit var currentFileName: String
    private var recording: Recording? = null
    private lateinit var createMetaDatafile: VrealKeyStore
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    lateinit var currentLocation: Location
    lateinit var lastUploadedFileUri: Uri
    private val PREFS_NAME = "NONPRIVATEDATA"
    private val KEY_LAST_USED_URL = "last-used-url"


    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vreal_capture)
        lastUploadedFileUri = Uri.EMPTY

        val previewView = findViewById<PreviewView>(R.id.previewView)
        val recordToggle = findViewById<ToggleButton>(R.id.recordToggle)
        val switchCameraBtn = findViewById<ImageButton>(R.id.switchCameraButton)
        val mediaModeToggle = findViewById<ImageButton>(R.id.media_mode_toggle)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_LAST_USED_URL, null)
        val galleryPreview = findViewById<ImageButton>(R.id.galleryPreview)

        if (savedUrl != null) {
            lastUploadedFileUri = savedUrl.toUri()
            lifecycleScope.launch {
                val thumbnail = createThumbnailForMedia()
                galleryPreview.setImageBitmap(thumbnail)
            }


        }


        galleryPreview.setOnClickListener {
            val intent = Intent(this, ViewVideo::class.java)
            if (lastUploadedFileUri != Uri.EMPTY) {
                intent.putExtra("url", lastUploadedFileUri.toString())
                startActivity(intent)
            } else {
                Toast.makeText(
                    applicationContext,
                    "No preview available, capture a media first",
                    Toast.LENGTH_LONG
                ).show()
            }

        }


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentLocation = location
                }
            }

        // Setup camera controller
        val controller = LifecycleCameraController(this).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
            bindToLifecycle(this@VrealCapture)
        }
        previewView.controller = controller

        var selectedMediaType = 1
        // initialize icon
        mediaModeToggle.setImageResource(R.drawable.video_capture_selected_v2)

        mediaModeToggle.setOnClickListener {
            if (selectedMediaType == 1) {
                selectedMediaType = 0
                mediaModeToggle.setImageResource(R.drawable.photo_capture_selected_v2)
            } else {
                selectedMediaType = 1
                mediaModeToggle.setImageResource(R.drawable.video_capture_selected_v2)
            }
        }
        switchCameraBtn.setOnClickListener {
            controller.cameraSelector =
                if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else CameraSelector.DEFAULT_BACK_CAMERA
        }

        recordToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                recordToggle.isClickable = false
                recordMedia(selectedMediaType, controller)
                recordToggle.isClickable = true
            } else {
                if (selectedMediaType == 1) recording?.stop()
                recordToggle.isChecked = false
            }
        }
    }

    private fun recordMedia(selectedMediaType: Int, controller: LifecycleCameraController) {
        if (selectedMediaType == 1) {
            recordVideo(controller)
        } else {
            takePhoto(controller)
        }

    }

    private fun recordVideo(controller: LifecycleCameraController) {
        if (recording != null) {
            recording?.stop()
            recording = null
            return
        }
        if (!hasRequiredPermissions()) {
            Toast.makeText(
                this,
                "It seems the app does not have necessary permissions! " +
                        "Go to app settings and provide camera and location permissions",
                Toast.LENGTH_LONG
            ).show()

            return
        }
        val chronometer = findViewById<Chronometer>(R.id.recordTimer)
        chronometer.visibility = View.VISIBLE
        chronometer.base = SystemClock.elapsedRealtime()
        chronometer.start()
        val sdf = SimpleDateFormat("ddMMyyyy_hhmmss")
        currentFileName = sdf.format(Date()) + ".mp4"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), currentFileName)
        recording = controller.startRecording(
            FileOutputOptions.Builder(file).build(),
            AudioConfig.AUDIO_DISABLED,
            ContextCompat.getMainExecutor(applicationContext),

            ) { event -> handleVideoEvents(event, file) }

    }

    fun drawableToBitmap(context: Context, @DrawableRes drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return createBitmap(1, 1)

        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = createBitmap(drawable.intrinsicWidth.takeIf { it > 0 } ?: 1,
            drawable.intrinsicHeight.takeIf { it > 0 } ?: 1)

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    suspend fun createThumbnailForMedia(): Bitmap = withContext(Dispatchers.IO) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_LAST_USED_URL, null)
        var placeholderBitmap =
            drawableToBitmap(applicationContext, R.drawable.logo_for_capture_page)
        if (savedUrl.isNullOrBlank()) {
            return@withContext placeholderBitmap
        }

        return@withContext try {
            // If it's an mp4, use its matching jpeg instead
            val finalUrl = if (savedUrl.lowercase().endsWith(".mp4")) {
                savedUrl.substringBeforeLast(".mp4") + ".jpeg"
            } else {
                savedUrl
            }

            val bitmap = if (finalUrl.startsWith("http", true)) {
                // Load from URL
                URL(finalUrl).openStream().use { BitmapFactory.decodeStream(it) }
            } else {
                // Load from local file path
                BitmapFactory.decodeFile(finalUrl)
            }

            bitmap?.scale(140, 140) ?: placeholderBitmap

        } catch (e: Exception) {
            e.printStackTrace()
            placeholderBitmap
        }
    }


    private fun handleVideoEvents(
        event: VideoRecordEvent,
        file: File
    ): VideoRecordEvent {
        val statusText = findViewById<TextView>(R.id.statusText)
        val chronometer = findViewById<TextView>(R.id.recordTimer) as Chronometer
        when (event) {
            is VideoRecordEvent.Finalize -> {
                if (event.hasError()) {
                    recording?.close()
                    recording = null

                    Toast.makeText(
                        applicationContext,
                        "Video capture failed",
                        Toast.LENGTH_LONG
                    ).show()
                    chronometer.stop()

                } else {
                    val galleryPreview = findViewById<ImageButton>(R.id.galleryPreview)

                    // Use coroutine to give the UI time to update
                    val scope = CoroutineScope(Dispatchers.IO)
                    scope.launch {

                        chronometer.visibility = View.INVISIBLE
                        statusText.setText(R.string.video_signing)
                        delay(50) // Give Compose time to recompose
                        createMetaDatafile = VrealKeyStore(
                            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                            file,
                            "mp4"
                        )
                        createMetaDatafile.updateMetadata(file)
                        statusText.setText(R.string.video_uploading)
                        delay(50)
                        lastUploadedFileUri =
                            createMetaDatafile.finishMetadataCreation(true, currentLocation)
                        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        prefs.edit() {
                            putString(
                                KEY_LAST_USED_URL,
                                lastUploadedFileUri.toString()
                            )
                        }
                        delay(100)
                        val thumbnail = createThumbnailForMedia()
                        withContext(Dispatchers.Main) {
                            galleryPreview.setImageBitmap(thumbnail)
                            statusText.setText(R.string.video_process_complete)
                            recording?.close()
                            recording = null
                        }
                        delay(5000)
                        withContext(Dispatchers.Main) {
                            statusText.setText("")
                        }


                    }
                }
            }
        }
        return event

    }

    private fun takePhoto(
        controller: LifecycleCameraController,

        ) {
        val statusText = findViewById<TextView>(R.id.statusText)

        if (!hasRequiredPermissions()) {
            Toast.makeText(
                this,
                "It seems the app does not have necessary permissions! " +
                        "Go to app settings and provide camera and location permissions",
                Toast.LENGTH_LONG
            ).show()

            return
        }
        controller.takePicture(
            ContextCompat.getMainExecutor(applicationContext),
            object : OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)

                    val matrix = Matrix().apply {
                        postRotate(image.imageInfo.rotationDegrees.toFloat())
                    }
                    val rotatedBitmap = Bitmap.createBitmap(
                        image.toBitmap(),
                        0,
                        0,
                        image.width,
                        image.height,
                        matrix,
                        true
                    )

                    val sdf = SimpleDateFormat("ddMMyyyy_hhmmss")
                    currentFileName = sdf.format(Date()) + ".jpeg"
                    val file =
                        File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), currentFileName)
                    val opStream = FileOutputStream(file)
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, opStream)
                    val galleryPreview = findViewById<ImageButton>(R.id.galleryPreview)


//                    onPhotoTaken(rotatedBitmap)
                    val scope = CoroutineScope(Dispatchers.IO)
                    scope.launch {
                        statusText.setText(R.string.photo_processing)
                        delay(50) // Give Compose time to recompose
                        createMetaDatafile = VrealKeyStore(
                            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                            file, "jpeg"
                        )
                        createMetaDatafile.updateMetadata(file)
                        lastUploadedFileUri =
                            createMetaDatafile.finishMetadataCreation(false, currentLocation)
                        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        prefs.edit() {
                            putString(
                                KEY_LAST_USED_URL,
                                lastUploadedFileUri.toString()
                            )
                        }
                        val thumbnail = createThumbnailForMedia()
                        withContext(Dispatchers.Main) {
                            galleryPreview.setImageBitmap(thumbnail)
                            statusText.setText(R.string.photo_capture_complete)
                        }

                        delay(2000)
                        withContext(Dispatchers.Main) {
                            statusText.setText("")
                        }

                    }
                    val recordToggle = findViewById<ToggleButton>(R.id.recordToggle)
                    recordToggle.isChecked = false


                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    Log.e("Camera", "Couldn't take photo: ", exception)
                }
            }
        )
    }


    private fun hasRequiredPermissions(): Boolean {
//

        return (ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED) &&
                (
                        ContextCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                                ||
                                ContextCompat.checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED)


    }

    companion object {
        private val CAMERAX_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}