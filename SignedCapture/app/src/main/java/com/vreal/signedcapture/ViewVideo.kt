package com.vreal.signedcapture

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.ExpandableListView
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.text.SimpleDateFormat
import kotlin.io.path.Path
import androidx.core.net.toUri
import androidx.core.graphics.createBitmap
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.text.method.ScrollingMovementMethod
import android.util.Log
import java.io.IOException
import java.util.Locale


class ViewVideo : AppCompatActivity() {

    private val s3Stuff: S3Stuff = S3Stuff()
    private val METADATATEMPLATE =
        "This media was signed at the source of capture by V-Real.\n" +
                "Device used to capture %s. Time of capture: %s"



    private lateinit var deserialized : MetaDataObj.Root
    private lateinit var videoDetails: TextView

//    private fun parseJsonString(json: String, token: String, delimiter: Char): String {
//        return json.substring(
//            json.indexOf(token) + token.length,
//            json.indexOf(delimiter, json.indexOf(token) + token.length)
//        )
//
//    }

    private fun updateSignatures(){
        var mapOfSignature = HashMap<String, List<String>>()
        var listOfSigns = emptyList<String>()
        mapOfSignature.put("Signatures", listOf())
        var expandableListViewExample: ExpandableListView = findViewById<ExpandableListView>(R.id.signature_list) as ExpandableListView
        var iterator = deserialized.metadataMasterRecord.signatures.iterator()
        iterator.forEach {
            listOfSigns = listOfSigns.plus(it.value.signature)
        }
        mapOfSignature["Signatures"] = listOfSigns

        val expandableTitleList = listOf("Signatures")
        val expandableListAdapter = CustomizedExpandableListAdapter(this, expandableTitleList, mapOfSignature)
        expandableListViewExample.setAdapter(expandableListAdapter)


    }

    private fun getDateStringFromFilename(filename: String): String {
        val day = filename.substring(0, 2)
        val month = filename.substring(2, 4)
        val year = filename.substring(4, 8)
        val hour = filename.substring(9, 11)
        val min = filename.substring(11, 13)
        val sec = filename.substring(13, 15)
        val sdf = SimpleDateFormat("ddMMyyyy_hhmmss")
        return (sdf.parse(day + month + year + "_" + hour + min + sec)?.toString() ?: "")
    }
    private fun removeSpecialChars(input:String) :String{
        var output = input
        output = output.replace(':',0.toChar()).replace('"',0.toChar())
        return output
    }


    private suspend fun updateDeviceInfo(mediaUrl: String, mediaExtension: String) {

        val result = s3Stuff.extractBucketAndKey(mediaUrl)
        result?.let { (bucket, key) ->
            println("Bucket: $bucket")
            println("Key: $key")
            val metadataFileName = key.replace(mediaExtension, ".metadata")
            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "tmpfile.metatadata")
            s3Stuff.getObject(bucket,metadataFileName,outputFile = file
            )
            val allMetadata = file.readText()
            val json = Json { ignoreUnknownKeys = true }
            deserialized = json.decodeFromString<MetaDataObj.Root>(allMetadata)
            Files.delete(Path(file.absolutePath))

        }
    }
    private fun downloadBitmap(url: String): Bitmap {
        run {
            val urlConnection = URL(url).openConnection() as HttpURLConnection

            try {
                val iStream: InputStream = BufferedInputStream(urlConnection.inputStream)
                val bis = BufferedInputStream(iStream)
                return BitmapFactory.decodeStream(bis)

            } catch (e: Exception){
                e.printStackTrace()
                // Define the desired width and height for your empty bitmap
                val width = 100
                val height = 100

               // Create an empty Bitmap with ARGB_8888 configuration (supports transparency)
                return createBitmap(width, height)

            }
            finally {
                urlConnection.disconnect()
            }
        }
    }
    private fun extractLocation(locationString: String) :String{
        val token ="\"Location[fused"
        val delimiter = 'h'
        return locationString.substring(
            locationString.indexOf(token) + token.length,
            locationString.indexOf(delimiter, locationString.indexOf(token) + token.length))



    }
    private fun createEmptyBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        return Bitmap.createBitmap(width, height, config)
    }

    private suspend fun updateMetadata(mediaUrl: String, mediaExtension: String) {
        updateDeviceInfo(mediaUrl, mediaExtension)
        val mapView: ImageView = findViewById<View>(R.id.capture_location) as ImageView
        val jpegView: ImageView = findViewById<View>(R.id.jpeg_view) as ImageView
        var jpeg = createEmptyBitmap(100,100)
        if("jpeg" in mediaExtension) {
            jpeg = downloadBitmap(mediaUrl)
        }
        val location = parseLocationString(deserialized.deviceInfo.location)
        val captureCity: TextView = findViewById<View>(R.id.capture_city) as TextView
        if (location != null) {
            reverseGeocodeLocation(location) { friendlyName ->
                if (friendlyName != null) {
                    Log.d("LocationActivity", "Friendly Name: ${location.toString()}")
                    captureCity.text = friendlyName
                } else {
                    captureCity.text = """Could not get friendly name."""
                }  }
        }else{
            captureCity.text = """Could not get friendly name."""
        }

        val locationUrl = ("https://maps.googleapis.com/maps/api/staticmap?center=center=" +
                location?.latitude +"," + location?.longitude+
        "&scale=2&zoom=14&size=1000x400&markers=color:red%7C" +
                location?.latitude +"," + location?.longitude+
                "&key=<YOUR_GOOGLE_MAPS_API_KEY>").toUri()
        var bitMap =  downloadBitmap(locationUrl.toString())

        val metadataFileName = mediaUrl.substring(
            mediaUrl.lastIndexOf("/") + 1,
            mediaUrl.length
        ).replace(".mp4", ".metadata")

        withContext(Dispatchers.Main) {
            videoDetails.text = METADATATEMPLATE.format(
                deserialized.deviceInfo.model + "-" + deserialized.deviceInfo.device,
                getDateStringFromFilename(metadataFileName)
            )
            mapView.setImageBitmap(bitMap)
            if ("jpeg" in mediaExtension) {
                jpegView.setImageBitmap(jpeg)
            }
            updateSignatures()
            val progressBar = findViewById<ProgressBar>(R.id.progressBar)
            progressBar.visibility = View.GONE
        }

    }


    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        setContentView(R.layout.activity_view_video)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE
        videoDetails = findViewById<View>(R.id.capture_device) as TextView
        videoDetails.movementMethod = ScrollingMovementMethod();

        val player = ExoPlayer.Builder(applicationContext).build()
        var mediaSource: MediaSource
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(
            this
        )

        var videoUrl: String
        val intent = intent
        val data: Uri? = intent.data
        if (data == null) {
            val extras = intent.extras
            videoUrl = extras?.getString("url").toString()
        }else{
            videoUrl = data.toString()
            videoUrl = videoUrl.replace("https://vreal-gallery.com/","")
        }
        val playerView: PlayerView = findViewById<View>(R.id.player_view) as PlayerView
        val jpegView: ImageView = findViewById<View>(R.id.jpeg_view) as ImageView
        if (".mp4" in videoUrl){

            playerView.visibility - View.VISIBLE
            jpegView.visibility = View.INVISIBLE
            mediaSource =
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(videoUrl))



            player.repeatMode = Player.REPEAT_MODE_OFF
            player.setMediaSource(mediaSource)
            player.prepare()
            player.play()

            Assertions.checkNotNull<PlayerView>(playerView).player = player
            player.addAnalyticsListener(EventLogger())
            CoroutineScope(Dispatchers.IO).launch {
                updateMetadata(videoUrl, ".mp4")
            }



        }else{
            jpegView.visibility = View.VISIBLE
            playerView.visibility = View.INVISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                updateMetadata(videoUrl,".jpeg")

            }

        }
        val imageView: ImageView = findViewById(R.id.share)
        imageView.setOnClickListener { shareTextViaSignal(videoUrl) }


    }
    private fun shareTextViaSignal(url: String) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.setType("text/plain")
        intent.putExtra(Intent.EXTRA_TEXT, "https://vreal-gallery.com/"+url)


        startActivity(Intent.createChooser(intent, "Share to:"))
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        try {
            packageManager.getPackageInfo(packageName, 0)
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            return false
        }
    }
    fun reverseGeocodeLocation(location: Location, completion: (String?) -> Unit) {
        val geocoder = Geocoder(applicationContext, Locale.getDefault())

        // In newer Android versions (API 33+), the Geocoder's getFromLocation method
        // is deprecated for direct use on the main thread and recommends
        // using the asynchronous version or running it on a background thread.
        // For simplicity in this example, we'll demonstrate a common approach.
        // For production apps targeting API 33+, consider using
        // getFromLocation(latitude, longitude, maxResults, Geocoder.GeocodeListener)
        // or performing this on a Coroutine/Thread.

        try {
            val addresses: List<Address>? = geocoder.getFromLocation(location.latitude, location.longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val placemark = addresses[0]
                val nameParts = mutableListOf<String>()

                // Android's Address object has different methods for components
                // compared to iOS's CLPlacemark.
                // We'll try to map them as closely as possible.
                placemark.locality?.let { nameParts.add(it) }             // e.g. "Brisbane"
                placemark.adminArea?.let { nameParts.add(it) }           // e.g. "QLD" (state/province)
                placemark.countryName?.let { nameParts.add(it) }         // e.g. "Australia"

                val friendlyName = nameParts.joinToString(separator = ", ")
                completion(friendlyName)
            } else {
                Log.d("LocationGeocoder", "No address found for the given location.")
                completion(null)
            }
        } catch (e: IOException) {
            Log.e("LocationGeocoder", "Reverse geocoding error: ${e.localizedMessage}")
            completion(null)
        } catch (e: IllegalArgumentException) {
            Log.e("LocationGeocoder", "Invalid latitude or longitude: ${e.localizedMessage}")
            completion(null)
        }
    }

    fun parseLocationString(locationString: String): Location? {
        // Regex to extract relevant parts: provider, latitude, longitude, and horizontal accuracy
        val regex = "Location\\[(\\w+) ([\\d.-]+),([\\d.-]+) hAcc=([\\d.]+).*]".toRegex()
        val matchResult = regex.find(locationString)

        return if (matchResult != null && matchResult.groupValues.size >= 5) {
            try {
                val provider = matchResult.groupValues[1]
                val latitude = matchResult.groupValues[2].toDouble()
                val longitude = matchResult.groupValues[3].toDouble()
                val hAcc = matchResult.groupValues[4].toFloat()

                val location = Location(provider)
                location.latitude = latitude
                location.longitude = longitude
                location.accuracy = hAcc // Set horizontal accuracy

                // You can also try to parse 'et' (elapsed realtime) if needed,
                // but it's more complex as it represents elapsed time since boot,
                // and setting a precise 'time' (Unix epoch) might be challenging
                // without knowing the device's boot time.
                // For general use, latitude, longitude, and accuracy are often sufficient.

                location
            } catch (e: NumberFormatException) {
                println("Error parsing numbers from location string: ${e.localizedMessage}")
                null
            }
        } else {
            println("Invalid location string format: $locationString")
            null
        }
    }


}

