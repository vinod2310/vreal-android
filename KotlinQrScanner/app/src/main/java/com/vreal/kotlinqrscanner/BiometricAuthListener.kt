package com.vreal.kotlinqrscanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat

interface BiometricAuthListener{


    fun onBiometricAuthenticateError(error: Int,errMsg: String);
    fun onBiometricAuthenticateSuccess(result: BiometricPrompt.AuthenticationResult)







}

/*
textureView = findViewById<TextureView>(R.id.capture_texture_view)!!
        findViewById<Button>(R.id.button_capture).setOnClickListener({
            captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequest.addTarget(imageReader.surface)
            cameraCaptureSession.capture(captureRequest.build(), null, null)

        })
        cameraMgr = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        imageReader = ImageReader.newInstance(1080,1920,ImageFormat.JPEG, 1)
        imageReader.setOnImageAvailableListener(object:ImageReader.OnImageAvailableListener{
            override fun onImageAvailable(p0: ImageReader?) {
                var image = p0?.acquireLatestImage()
                var buffer = image!!.planes.get(0).buffer
                var bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                var file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "vreal.jpeg")
                var opStream = FileOutputStream(file)
                opStream.write(bytes)
                opStream.close()
                image.close()
                Toast.makeText(this@VrealCapture, "image captured", Toast.LENGTH_SHORT).show()
            }

        }, handler)
        textureView.surfaceTextureListener = object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
//                biometric_prompt(textureView)
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

            }

        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }



        fun openCamera(ctx: Context){
        if (ActivityCompat.checkSelfPermission(
                ctx,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        cameraMgr.openCamera(cameraMgr.cameraIdList[0],
            object: CameraDevice.StateCallback(){
                override fun onOpened(p0: CameraDevice) {
                    cameraDevice = p0
                    var captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    var surface = Surface(textureView.surfaceTexture)
                    captureRequest.addTarget(surface)

                    cameraDevice.createCaptureSession(listOf(surface, imageReader.surface), object:
                        CameraCaptureSession.StateCallback(){
                        override fun onConfigured(p0: CameraCaptureSession) {
                            cameraCaptureSession = p0
                            cameraCaptureSession.setRepeatingRequest(captureRequest.build(), null, null)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {

                        }
                    }, handler)
                }

                override fun onDisconnected(camera: CameraDevice) {

                }

                override fun onError(camera: CameraDevice, error: Int) {

                }
            },
            handler)
    }


 */