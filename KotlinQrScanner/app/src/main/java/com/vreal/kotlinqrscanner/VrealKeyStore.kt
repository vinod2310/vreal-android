package com.vreal.kotlinqrscanner

import android.graphics.Bitmap
import android.location.Location
import android.media.MediaMetadataRetriever
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.Signature
import java.security.spec.ECGenParameterSpec


class VrealKeyStore(var mediaLocation:File?, var mediaFile: File, mediaExtension: String) {
    private var metadataFileHandler: FileWriter? = null
    private var signatureCount = 0
    var metadataFileName:String
    lateinit var pair : KeyPair
    val CHUCNK_SIZE = 100 * 1024

    init {
        initializeKeyPair()
        metadataFileName = replaceExtension(mediaFile.name, mediaExtension, "metadata")
        initializeMetadataFile()
    }

    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchProviderException::class,
        IOException::class
    )
     fun init() {

    }

    private fun initializeKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        keyPairGenerator.initialize(
            KeyGenParameterSpec.Builder(
                "key1",
                KeyProperties.PURPOSE_SIGN
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256,
                    KeyProperties.DIGEST_SHA384,
                    KeyProperties.DIGEST_SHA512)
                // Only permit the private key to be used if the user authenticated
                // within the last five minutes.
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(5 * 60)
                .build());
        pair = keyPairGenerator.generateKeyPair();
    }

    @Throws(IOException::class)
    private fun initializeMetadataFile(
    ) {
        try {
            val file = File(mediaLocation,metadataFileName )
            metadataFileHandler = FileWriter(file)
            metadataFileHandler!!.write("{\"metadata_master_record\":")
            metadataFileHandler!!.write("{\"public_key\":\"")
            metadataFileHandler!!.write(byteArrayToHex(pair.public.encoded) + "\",")
            metadataFileHandler!!.write("\"signatures\":{")
            metadataFileHandler!!.flush()

        } catch (e: Exception) {
            System.err.println("Caught exception $e")
        }

    }


    @Throws(IOException::class)
    fun finishMetadataCreation(isVideo: Boolean, currentLocation: Location) {
        metadataFileHandler?.write("}},")
        metadataFileHandler?.write("\"device_info\":{\n" +
                "\"osversion\": " + "\"" + System.getProperty("os.version") + "\"," +
                "\"device\":" + "\"" +  android.os.Build.DEVICE +"\"," +
                "\"location\":" + "\"" +  currentLocation.toString() +"\"," +
                "\"model\": " + "\"" + android.os.Build.MODEL +"\"," +
                "\"product\": " + "\"" +  android.os.Build.PRODUCT +"\"" +
                "}}")
        metadataFileHandler?.flush()
        var ss  = MediaMetadataRetriever()
        ss.setDataSource(mediaFile.path)

        var frame = ss.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        var resized: Bitmap? = null
        if (frame != null) {
            if(frame.getWidth() > frame.getHeight()) {
                resized = Bitmap.createScaledBitmap(frame, 180, 150, true)
            }else{
                resized = Bitmap.createScaledBitmap(frame, 150, 150, true)
            }
        }
        val jpeg_path = replaceExtension(mediaFile.path, "mp4", "jpeg")
        val opStream = FileOutputStream(jpeg_path)
        resized?.compress(Bitmap.CompressFormat.JPEG, 50, opStream)
        S3Stuff().uploadFile(mediaFile)
        S3Stuff().uploadFile(File(jpeg_path) )
        S3Stuff().uploadFile( File(mediaLocation, metadataFileName))


    }

    private fun signPayload(payload: ByteArray): ByteArray? {
        try {
            val signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(pair.private);
            signature.update(payload)
            return signature.sign()
        } catch (e: Exception) {
            System.err.println("Caught exception $e")
        }
        return null
    }

    private fun replaceExtension(fileName: String, extension: String, newExtension: String): String {
        return fileName.replace(extension, newExtension)
    }

    @Throws(IOException::class)
    fun updateMetadata(payload: ByteArray?, last: Boolean = false) {
        val signature = signPayload(payload!!)
        signatureCount++
        checkNotNull(signature)
        metadataFileHandler?.write(("\"" + signatureCount).toString() + "\" : { \"signature\": \"")

        metadataFileHandler?.write(byteArrayToHex(signature))
        metadataFileHandler?.write("\", \"sig_len\":\"" + signature.size + "\"}")
        if(!last){
            metadataFileHandler?.write(",")

        }
    }

    fun updateMetadata(mediaFile: File) {

        val oneChunk = ByteArray(CHUCNK_SIZE)
        var lastChunk: ByteArray
        mediaFile.inputStream().buffered().use { input ->
            while(true) {
                val sz = input.read(oneChunk)
                if (sz <= 0) break
                if(sz< CHUCNK_SIZE){
                    lastChunk = ByteArray(sz)
                    oneChunk.copyInto(lastChunk, 0,0, sz)
                    updateMetadata(lastChunk, true)
                    break
                }
                updateMetadata(oneChunk)
            }
        }
    }


    private fun byteArrayToHex(a: ByteArray): String {
        val sb = StringBuilder(a.size * 2)
        for (b in a) sb.append(String.format("%02x", b))
        return sb.toString()
    }
}