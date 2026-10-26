package com.vreal.signedcapture
// snippet-start:[s3.kotlin.copy_object.import]
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject  // Standard org.json, comes with Android
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream

// File size limits (must match server limits)
object FileSizeLimits {
    const val MAX_VIDEO_SIZE = 500 * 1024 * 1024L  // 500 MB
    const val MAX_PHOTO_SIZE = 50 * 1024 * 1024L   // 50 MB
    const val MAX_METADATA_SIZE = 1 * 1024 * 1024L // 1 MB
}

// Upload result sealed class for better error handling
sealed class UploadResult {
    data class Success(val url: String) : UploadResult()
    data class Error(val message: String, val code: Int) : UploadResult()
}

sealed class PresignedUrlResult {
    data class Success(val url: String) : PresignedUrlResult()
    data class FileTooLarge(val maxSize: Long, val requestedSize: Long) : PresignedUrlResult()
    data class QuotaExceeded(val message: String) : PresignedUrlResult()
    data class RateLimited(val retryAfter: Int) : PresignedUrlResult()
    data class Error(val message: String, val code: Int) : PresignedUrlResult()
}


class S3Stuff {
    private val videoBucketName = BuildConfig.ANDROID_UNVERIFIED_VIDEO_BUCKET
    private val photoBucketName = BuildConfig.ANDROID_UNVERIFIED_PHOTO_BUCKET
    private val verifiedVideoBucketName = BuildConfig.ANDROID_VERIFIED_VIDEO_BUCKET
    private val verifiedPhotoBucketName = BuildConfig.ANDROID_VERIFIED_PHOTO_BUCKET



    /**
     * Upload video to bucket with proper error handling
     * Returns UploadResult that can be checked for success/error
     */
    suspend fun uploadToVideoBucketV4(file: File): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val result = uploadFileToS3V4(videoBucketName, file.name, file)
                when (result) {
                    is UploadResult.Success -> {
                        Log.d("VREAL", "Video uploaded to $videoBucketName successfully")
                    }
                    is UploadResult.Error -> {
                        Log.e("VREAL", "Video upload failed: ${result.message}")
                    }
                }
                result
            } catch (e: Exception) {
                Log.e("VREAL", "Video upload exception: ${e.message}")
                UploadResult.Error("Upload failed: ${e.message}", -1)
            }
        }
    }

    /**
     * Upload photo to bucket with proper error handling
     * Returns UploadResult that can be checked for success/error
     */
    suspend fun uploadToPhotoBucketV4(file: File): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val result = uploadFileToS3V4(photoBucketName, file.name, file)
                when (result) {
                    is UploadResult.Success -> {
                        Log.d("VREAL", "Photo uploaded to $photoBucketName successfully")
                    }
                    is UploadResult.Error -> {
                        Log.e("VREAL", "Photo upload failed: ${result.message}")
                    }
                }
                result
            } catch (e: Exception) {
                Log.e("VREAL", "Photo upload exception: ${e.message}")
                UploadResult.Error("Upload failed: ${e.message}", -1)
            }
        }
    }

    /**
     * Legacy methods - kept for backward compatibility
     * Use uploadToVideoBucketV4() and uploadToPhotoBucketV4() for new code
     */
    @Deprecated("Use uploadToVideoBucketV4() instead")
    fun uploadToVideoBucket(file: File): Uri {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                uploadFileToS3_v2(videoBucketName, file.name, file)
                Log.d("UploadActivity", "File uploaded to $videoBucketName successfully")
            } catch (e: Exception) {
                Log.e("UploadActivity", "Upload failed: ${e.message}")
            }
        }
        val uri: Uri = Uri.Builder()
            .scheme("https")
            .authority(BuildConfig.ANDROID_VERIFIED_VIDEO_BUCKET_URL_PREFIX)
            .appendPath(BuildConfig.ANDROID_VERIFIED_VIDEO_BUCKET)
            .appendPath(file.name)
            .build()
        return uri
    }

    @Deprecated("Use uploadToPhotoBucketV4() instead")
    fun uploadToPhotoBucket(file: File): Uri {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                uploadFileToS3_v2(photoBucketName, file.name, file)
                Log.d("UploadActivity", "File uploaded to $photoBucketName successfully")
            } catch (e: Exception) {
                Log.e("UploadActivity", "Upload failed: ${e.message}")
            }
        }
        val uri: Uri = Uri.Builder()
            .scheme("https")
            .authority(BuildConfig.ANDROID_VERIFIED_VIDEO_BUCKET_URL_PREFIX)
            .appendPath(BuildConfig.ANDROID_VERIFIED_PHOTO_BUCKET)
            .appendPath(file.name)
            .build()
        return uri
    }
    private suspend fun checkFileExists_v2(bucketName: String, item: String) :Boolean {
        val presignedUrl =  getPresignedUrl(bucketName,item,"get_object").toString()

        try {
            val url = URL(presignedUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return true
            } else {
                return false
            }
        } catch (e: Exception) {
            Log.i("VREAL","Item does not exist, returned error: ${e.message}")
            return false
        }

    }

    private suspend fun checkFileExists(bucketName: String, item: String) :Boolean {
        return checkFileExists_v2(bucketName, item)
//        val request = HeadObjectRequest {
//            this.bucket = bucketName
//            this.key = item
//        }
//        try {
//            s3Client.headObject(request)
//            return true
//        }catch (e: Exception) {
//            Log.e("List object", "file not found: ${e.message}")
//            return false
//
//        }

    }


    /**
     * Get presigned URL with file size validation and proper error handling
     *
     * @param bucket S3 bucket name
     * @param key Object key (file name)
     * @param action "put_object" or "get_object"
     * @param fileSize File size in bytes (required for put_object, optional for get_object)
     * @param contentType MIME type (e.g., "video/mp4", "image/jpeg")
     * @return PresignedUrlResult with success or specific error
     */
    suspend fun getPresignedUrlV4(
        bucket: String,
        key: String,
        action: String,
        fileSize: Long = 0,
        contentType: String = ""
    ): PresignedUrlResult {
        return withContext(Dispatchers.IO) {
            // Build endpoint with all required parameters
            val endpoint = buildString {
                append("https://vreal-gallery.com/web/presigned_v4")
                append("?bucket=$bucket")
                append("&key=$key")
                append("&action=$action")

                // Add file size for uploads (required by server)
                if (action == "put_object" && fileSize > 0) {
                    append("&file_size=$fileSize")
                }

                // Add content type if provided
                if (contentType.isNotEmpty()) {
                    append("&content_type=$contentType")
                }
            }

            Log.i("VREAL", "Requesting presigned URL: $endpoint")

            try {
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                // TODO: Add device authentication token
                // connection.setRequestProperty("Authorization", "Bearer $authToken")

                val responseCode = connection.responseCode

                when (responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = reader.readText()
                        reader.close()
                        val json = JSONObject(response)
                        val presignedUrl = json.getString("url")
                        Log.i("VREAL", "Presigned URL obtained successfully")
                        PresignedUrlResult.Success(presignedUrl)
                    }

                    413 -> { // Payload Too Large
                        val reader = BufferedReader(InputStreamReader(connection.errorStream))
                        val response = reader.readText()
                        reader.close()
                        val json = JSONObject(response)
                        val maxSize = json.optLong("max_size_bytes", 0)
                        val requestedSize = json.optLong("requested_size_bytes", fileSize)
                        Log.w("VREAL", "File too large: $requestedSize bytes (max: $maxSize)")
                        PresignedUrlResult.FileTooLarge(maxSize, requestedSize)
                    }

                    429 -> { // Too Many Requests (quota or rate limit)
                        val reader = BufferedReader(InputStreamReader(connection.errorStream))
                        val response = reader.readText()
                        reader.close()
                        val json = JSONObject(response)
                        val detail = json.optString("detail", "Upload quota exceeded")
                        val retryAfter = json.optInt("retry_after", 60)

                        Log.w("VREAL", "Quota/Rate limit: $detail")

                        if (detail.contains("rate limit", ignoreCase = true)) {
                            PresignedUrlResult.RateLimited(retryAfter)
                        } else {
                            PresignedUrlResult.QuotaExceeded(detail)
                        }
                    }

                    401, 403 -> { // Unauthorized / Forbidden
                        Log.e("VREAL", "Authentication failed: $responseCode")
                        PresignedUrlResult.Error("Authentication required. Please login.", responseCode)
                    }

                    else -> {
                        val errorMsg = try {
                            val reader = BufferedReader(InputStreamReader(connection.errorStream))
                            val response = reader.readText()
                            reader.close()
                            val json = JSONObject(response)
                            json.optString("error", "Server error: $responseCode")
                        } catch (e: Exception) {
                            "Server error: $responseCode"
                        }
                        Log.e("VREAL", "Server error: $errorMsg")
                        PresignedUrlResult.Error(errorMsg, responseCode)
                    }
                }
            } catch (e: Exception) {
                Log.e("VREAL", "Network error: ${e.message}")
                PresignedUrlResult.Error("Network error: ${e.message}", -1)
            }
        }
    }

    /**
     * Legacy method - kept for backward compatibility
     * Use getPresignedUrlV4() for new code
     */
    @Deprecated("Use getPresignedUrlV4() instead", ReplaceWith("getPresignedUrlV4(bucket, key, action)"))
    suspend fun getPresignedUrl(bucket: String, key: String, action: String): String? {
        return when (val result = getPresignedUrlV4(bucket, key, action)) {
            is PresignedUrlResult.Success -> result.url
            else -> null
        }
    }
    fun ensureMediaUploaded(isVideo: Boolean, mediaFileName:String, timeout: Long) :Boolean{
        var localTimeout  = timeout
        var fileFound  = false;
        val bucketName = if (isVideo) videoBucketName else photoBucketName


        runBlocking {
            val job = launch {
                while (localTimeout > 0 && !fileFound) {
                    fileFound = checkFileExists(bucketName,mediaFileName)
                    delay(1000)
                    localTimeout -= 1000;
                }
            }
            job.join()
        }
        return fileFound
    }

    fun ensureMetadataInVerified(isVideo: Boolean, metadataFileName:String, timeout: Long) :Boolean{
        var localTimeout  = timeout
        var fileFound  = false;
        val bucketName = if (isVideo) verifiedVideoBucketName  else verifiedPhotoBucketName

        runBlocking {
            val job = launch {
                while (localTimeout > 0 && !fileFound) {
                    fileFound = checkFileExists(bucketName,metadataFileName)
                    Log.i("VReal:", "file $metadataFileName exists in verified bucket $fileFound")
                    delay(1000)
                    localTimeout -= 1000;
                }
            }
            job.join()
        }
        return fileFound
    }
    suspend fun getObject_v2(bucket: String, key: String, outputFile: File){
        try {
            val presignedUrl = getPresignedUrl(bucket, key, "get_object")

            return withContext(Dispatchers.IO) {
                try {
                    val url = URL(presignedUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        val inputStream = BufferedInputStream(connection.inputStream)
                        val outputStream = BufferedOutputStream(FileOutputStream(outputFile))

                        inputStream.copyTo(outputStream)

                        inputStream.close()
                        outputStream.flush()
                        outputStream.close()

                        println("Download successful! File saved to: ${outputFile.absolutePath}")
                        true
                    } else {
                        println("Download failed. HTTP status: $responseCode")
                        false
                    }
                } catch (e: Exception) {
                    println("Download failed: ${e.message}")
                    false
                }
            }


        }catch (e: Exception) {
            Log.e("Get activity", "Upload failed: ${e.message}")
        }

    }


    fun extractBucketAndKey(urlString: String): Pair<String, String>? {
        return try {
            val url = URL(urlString)
            val pathSegments = url.path.trimStart('/').split('/')

            if (pathSegments.size < 2) {
                println("URL path too short: ${url.path}")
                return null
            }

            val bucket = pathSegments[0]
            val key = pathSegments.drop(1).joinToString("/")

            Pair(bucket, key)
        } catch (e: Exception) {
            println("Invalid URL: ${e.message}")
            null
        }
    }


    suspend fun getObject(bucketName :String, objectName: String, outputFile: File){

        Log.i("VREAL:GetObject", bucketName + " " + objectName )

        getObject_v2(bucketName, objectName, outputFile)

//        val request = GetObjectRequest {
//            this.bucket = bucketName
//            this.key = objectName
//        }
//        try {
//            s3Client.getObject(request){ resp ->
//                resp.body?.writeToFile(outputFile)
//            }
//
//        }catch (e: Exception) {
//            Log.e("Get activity", "Upload failed: ${e.message}")
//        }

    }
    /**
     * Validate file size before upload
     */
    private fun validateFileSize(file: File, isVideo: Boolean): Boolean {
        val fileSize = file.length()
        val maxSize = if (isVideo) FileSizeLimits.MAX_VIDEO_SIZE else FileSizeLimits.MAX_PHOTO_SIZE

        if (fileSize > maxSize) {
            val maxSizeMB = maxSize / (1024 * 1024)
            val fileSizeMB = fileSize / (1024 * 1024)
            Log.e("VREAL", "File too large: $fileSizeMB MB (max: $maxSizeMB MB)")
            return false
        }
        return true
    }

    /**
     * Determine content type from file name
     */
    private fun getContentType(fileName: String): String {
        return when {
            fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            fileName.endsWith(".mov", ignoreCase = true) -> "video/quicktime"
            fileName.endsWith(".jpg", ignoreCase = true) ||
                    fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".metadata", ignoreCase = true) -> "application/json"
            else -> "application/octet-stream"
        }
    }

    /**
     * Upload file to S3 using presigned URL v4 with proper validation and error handling
     *
     * @param bucketName S3 bucket name
     * @param keyValue Object key (file name)
     * @param file File to upload
     * @return UploadResult with success or error details
     */
    suspend fun uploadFileToS3V4(bucketName: String, keyValue: String, file: File): UploadResult {
        return withContext(Dispatchers.IO) {
            // Validate file exists
            if (!file.exists()) {
                Log.e("VREAL", "File does not exist: ${file.absolutePath}")
                return@withContext UploadResult.Error("File not found", -1)
            }

            val fileSize = file.length()
            val contentType = getContentType(keyValue)
            val isVideo = contentType.startsWith("video/")

            // Client-side file size validation (provides better UX)
            if (!validateFileSize(file, isVideo)) {
                val maxSize = if (isVideo) FileSizeLimits.MAX_VIDEO_SIZE else FileSizeLimits.MAX_PHOTO_SIZE
                val maxSizeMB = maxSize / (1024 * 1024)
                return@withContext UploadResult.Error(
                    "File too large. Maximum ${if (isVideo) "video" else "photo"} size is $maxSizeMB MB",
                    413
                )
            }

            Log.i("VREAL", "Uploading: $keyValue (${fileSize / (1024 * 1024)} MB)")

            // Get presigned URL with file size
            when (val result = getPresignedUrlV4(bucketName, keyValue, "put_object", fileSize, contentType)) {
                is PresignedUrlResult.Success -> {
                    val presignedUrl = result.url
                    Log.d("VREAL", "Got presigned URL, starting upload...")

                    // Perform actual upload
                    try {
                        val url = URL(presignedUrl)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "PUT"
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", contentType)
                        connection.setRequestProperty("Content-Length", fileSize.toString())

                        val outputStream = BufferedOutputStream(connection.outputStream)
                        val inputStream = BufferedInputStream(FileInputStream(file))

                        // Copy file to S3
                        inputStream.copyTo(outputStream)

                        inputStream.close()
                        outputStream.flush()
                        outputStream.close()

                        val responseCode = connection.responseCode
                        if (responseCode in 200..299) {
                            Log.i("VREAL", "Upload successful! HTTP $responseCode")
                            UploadResult.Success(presignedUrl)
                        } else {
                            Log.e("VREAL", "S3 upload failed with HTTP $responseCode")
                            UploadResult.Error("Upload to S3 failed", responseCode)
                        }
                    } catch (e: Exception) {
                        Log.e("VREAL", "Upload exception: ${e.message}")
                        UploadResult.Error("Upload failed: ${e.message}", -1)
                    }
                }

                is PresignedUrlResult.FileTooLarge -> {
                    val maxSizeMB = result.maxSize / (1024 * 1024)
                    UploadResult.Error("File too large. Maximum size is $maxSizeMB MB", 413)
                }

                is PresignedUrlResult.QuotaExceeded -> {
                    UploadResult.Error(result.message, 429)
                }

                is PresignedUrlResult.RateLimited -> {
                    UploadResult.Error("Too many uploads. Please wait ${result.retryAfter} seconds", 429)
                }

                is PresignedUrlResult.Error -> {
                    UploadResult.Error(result.message, result.code)
                }
            }
        }
    }

    /**
     * Legacy method - kept for backward compatibility
     * Use uploadFileToS3V4() for new code which provides better error handling
     */
    @Deprecated("Use uploadFileToS3V4() instead", ReplaceWith("uploadFileToS3V4(bucketName, keyValue, file)"))
    suspend fun uploadFileToS3_v2(bucketName: String, keyValue: String, file: File) {
        when (val result = uploadFileToS3V4(bucketName, keyValue, file)) {
            is UploadResult.Success -> Log.i("VREAL", "Legacy upload succeeded")
            is UploadResult.Error -> Log.e("VREAL", "Legacy upload failed: ${result.message}")
        }
    }
    private suspend fun uploadFileToS3(bucketName: String, keyValue: String, file: File) {
        uploadFileToS3_v2(bucketName,file.name, file)
//        val request = PutObjectRequest {
//            this.bucket = bucketName
//            this.key = file.name
//            this.body = file.asByteStream()
//        }
//        try {
//            s3Client.putObject(request)
//        }catch (e: Exception) {
//            Log.e("UploadActivity", "Upload failed: ${e.message}")
//        }
//
        }
    }
