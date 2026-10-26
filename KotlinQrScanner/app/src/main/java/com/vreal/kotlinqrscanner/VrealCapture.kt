@file:OptIn(ExperimentalMaterial3Api::class)

package com.vreal.kotlinqrscanner
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.IconToggleButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoCameraFront
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.vreal.kotlinqrscanner.theme.CameraXGuideTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class VrealCapture : ComponentActivity() {
    lateinit var currentFileName: String
    private var recording : Recording? = null
    private lateinit var createMetaDatafile : VrealKeyStore
    private lateinit var fusedLocationClient : FusedLocationProviderClient
    lateinit var currentLocation: Location

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        if (!hasRequiredPermissions()) {
            ActivityCompat.requestPermissions(
                this, CAMERAX_PERMISSIONS, 0
            )
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location : Location? ->
                if (location != null) {
                    currentLocation = location
                }
            }


        setContent {
            CameraXGuideTheme {
                var selected by remember { mutableStateOf(false) }
                val color = if (selected) Color.Blue else Color.Yellow
                val scope = rememberCoroutineScope()
                val scaffoldState = rememberBottomSheetScaffoldState()
                val controller = remember {
                    LifecycleCameraController(applicationContext).apply {
                        setEnabledUseCases(
                            CameraController.IMAGE_CAPTURE or
                                    CameraController.VIDEO_CAPTURE
                        )
                    }
                }
                val viewModel = viewModel<MainViewModel>()
                val bitmaps by viewModel.bitmaps.collectAsState()

                BottomSheetScaffold(
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = 15.dp,
                    sheetContent = {
                        PhotoBottomSheetContent(
                            bitmaps = bitmaps,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        CameraPreview(
                            controller = controller,
                            modifier = Modifier
                                .fillMaxSize()
                        )

                        IconButton(
                            onClick = {
                                controller.cameraSelector =
                                    if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    } else CameraSelector.DEFAULT_BACK_CAMERA
                            },
                            modifier = Modifier
                                .offset(16.dp, 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch camera"
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(30.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            IconButton(
                                onClick = {
                                    takePhoto(
                                        controller = controller,
                                        onPhotoTaken = viewModel::onTakePhoto
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Take photo"
                                )
                            }

                            var checked by remember { mutableStateOf(false) }
                            FilledIconToggleButton(checked = checked, onCheckedChange = { checked = it
                                recordVideo(
                                        controller = controller,
                                    )}) {
                                if (checked) {
                                    Icon(ImageVector.vectorResource(R.drawable.recording_in_progress),"")
                                } else {
                                    Icon(ImageVector.vectorResource(R.drawable.start_recording),"")
                                }

                            }

//                            IconButton(
//                                colors= IconButtonDefaults.filledIconButtonColors(),
//                                onClick = {
//                                    selected = !selected
//
//                                    recordVideo(
//                                        controller = controller,
//                                    )
//                                }
//                            ) {
//                                Icon(
//                                    imageVector = Icons.Default.PhotoCameraFront,
//                                    contentDescription = "Take video"
//                                )
//                            }
                        }
                    }
                }
            }
        }
    }

    private fun recordVideo(controller:LifecycleCameraController) {
        if (recording != null) {
            recording?.stop()
            recording = null
            return
        }
        if (!hasRequiredPermissions()) {
            Toast.makeText(this, "It seems the app does not have necessary permissions! " +
                    "Go to app settings and provide camera and location permissions", Toast.LENGTH_LONG).show()

            return
        }
        val sdf = SimpleDateFormat("ddMMyyyy_hhmmss")
        currentFileName = sdf.format(Date()) + ".mp4"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), currentFileName)
        recording = controller.startRecording(
            FileOutputOptions.Builder(file).build(),
            AudioConfig.AUDIO_DISABLED,
            ContextCompat.getMainExecutor(applicationContext),


            ) { event ->
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
                    } else {
                        createMetaDatafile = VrealKeyStore(
                            getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                            file, "mp4")
                        createMetaDatafile.updateMetadata(file)
                        createMetaDatafile.finishMetadataCreation(true,currentLocation)
                        Toast.makeText(
                            applicationContext,
                            "Video capture succeeded",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun takePhoto(
        controller: LifecycleCameraController,
        onPhotoTaken: (Bitmap) -> Unit
    ) {
        if(!hasRequiredPermissions()){
            Log.e("VReal", "No permission ")
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
                    val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), currentFileName)
                    val opStream = FileOutputStream(file)
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, opStream)

                    onPhotoTaken(rotatedBitmap)
                    createMetaDatafile = VrealKeyStore(
                        getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                            file, "jpeg")
                    createMetaDatafile.updateMetadata(file)
                    createMetaDatafile.finishMetadataCreation(false, currentLocation)
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

        return (ContextCompat.checkSelfPermission(applicationContext,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ) &&
                (
                                ContextCompat.checkSelfPermission(applicationContext,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ||
                                ContextCompat.checkSelfPermission(applicationContext,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED )



    }

    companion object {
        private val CAMERAX_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}