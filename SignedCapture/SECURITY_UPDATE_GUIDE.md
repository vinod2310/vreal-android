# Android App Security Update Guide

## 🔐 What Changed

The Django backend now enforces **strict file size limits** and **upload quotas** to prevent abuse. Your Android app has been updated to:

1. ✅ Send file size when requesting presigned URLs
2. ✅ Validate file sizes client-side (better UX)
3. ✅ Handle server errors gracefully
4. ✅ Show meaningful error messages to users

---

## 📝 Updated S3Stuff.kt

### New Classes Added

```kotlin
// File size limits (matches server configuration)
object FileSizeLimits {
    const val MAX_VIDEO_SIZE = 500 * 1024 * 1024L  // 500 MB
    const val MAX_PHOTO_SIZE = 50 * 1024 * 1024L   // 50 MB
    const val MAX_METADATA_SIZE = 1 * 1024 * 1024L // 1 MB
}

// Upload results with detailed error information
sealed class UploadResult {
    data class Success(val url: String) : UploadResult()
    data class Error(val message: String, val code: Int) : UploadResult()
}

// Presigned URL results
sealed class PresignedUrlResult {
    data class Success(val url: String) : PresignedUrlResult()
    data class FileTooLarge(val maxSize: Long, val requestedSize: Long) : PresignedUrlResult()
    data class QuotaExceeded(val message: String) : PresignedUrlResult()
    data class RateLimited(val retryAfter: Int) : PresignedUrlResult()
    data class Error(val message: String, val code: Int) : PresignedUrlResult()
}
```

### New Methods (V4)

```kotlin
// Get presigned URL with file size validation
suspend fun getPresignedUrlV4(
    bucket: String,
    key: String,
    action: String,
    fileSize: Long = 0,
    contentType: String = ""
): PresignedUrlResult

// Upload file with full error handling
suspend fun uploadFileToS3V4(
    bucketName: String,
    keyValue: String,
    file: File
): UploadResult

// Convenience methods
suspend fun uploadToVideoBucketV4(file: File): UploadResult
suspend fun uploadToPhotoBucketV4(file: File): UploadResult
```

---

## 🚀 How to Update Your Code

### Old Code (Deprecated)

```kotlin
// ❌ Old way - no error handling
val s3Stuff = S3Stuff()
s3Stuff.uploadToVideoBucket(videoFile)  // Fire and forget, no error handling
```

### New Code (Recommended)

```kotlin
// ✅ New way - proper error handling
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

val s3Stuff = S3Stuff()

lifecycleScope.launch {
    when (val result = s3Stuff.uploadToVideoBucketV4(videoFile)) {
        is UploadResult.Success -> {
            // Upload successful!
            showSuccess("Video uploaded successfully")
        }

        is UploadResult.Error -> {
            // Handle specific error codes
            when (result.code) {
                413 -> {
                    // File too large
                    showError("Video is too large. Maximum size is 500 MB")
                }

                429 -> {
                    // Quota exceeded or rate limited
                    showError(result.message)
                    // e.g., "Daily upload limit reached. Resets at midnight UTC."
                }

                401, 403 -> {
                    // Authentication required
                    showError("Please login to upload")
                    // Navigate to login screen
                }

                else -> {
                    // Other errors
                    showError("Upload failed: ${result.message}")
                }
            }
        }
    }
}
```

---

## 📱 Example: Complete Upload Flow with UI

### In your ViewModel or Activity:

```kotlin
class CaptureViewModel : ViewModel() {
    private val s3Stuff = S3Stuff()

    // UI state
    private val _uploadState = MutableLiveData<UploadState>()
    val uploadState: LiveData<UploadState> = _uploadState

    sealed class UploadState {
        object Idle : UploadState()
        data class Uploading(val progress: Int) : UploadState()
        data class Success(val message: String) : UploadState()
        data class Error(val message: String) : UploadState()
    }

    fun uploadVideo(videoFile: File) {
        viewModelScope.launch {
            // Validate file size client-side first (better UX)
            val fileSize = videoFile.length()
            val maxSize = FileSizeLimits.MAX_VIDEO_SIZE

            if (fileSize > maxSize) {
                val maxSizeMB = maxSize / (1024 * 1024)
                val fileSizeMB = fileSize / (1024 * 1024)
                _uploadState.value = UploadState.Error(
                    "Video too large: $fileSizeMB MB (max: $maxSizeMB MB)"
                )
                return@launch
            }

            _uploadState.value = UploadState.Uploading(0)

            when (val result = s3Stuff.uploadToVideoBucketV4(videoFile)) {
                is UploadResult.Success -> {
                    _uploadState.value = UploadState.Success("Video uploaded successfully!")
                    // Continue with metadata upload, etc.
                }

                is UploadResult.Error -> {
                    val errorMessage = when (result.code) {
                        413 -> "Video exceeds 500 MB limit"
                        429 -> {
                            if (result.message.contains("daily", ignoreCase = true)) {
                                "Daily upload quota reached. Try again tomorrow."
                            } else {
                                "Too many uploads. Please wait a minute and try again."
                            }
                        }
                        401, 403 -> "Authentication required. Please login."
                        else -> "Upload failed: ${result.message}"
                    }
                    _uploadState.value = UploadState.Error(errorMessage)
                }
            }
        }
    }

    fun uploadPhoto(photoFile: File) {
        viewModelScope.launch {
            // Validate file size
            val fileSize = photoFile.length()
            val maxSize = FileSizeLimits.MAX_PHOTO_SIZE

            if (fileSize > maxSize) {
                val maxSizeMB = maxSize / (1024 * 1024)
                _uploadState.value = UploadState.Error(
                    "Photo too large. Maximum size is $maxSizeMB MB"
                )
                return@launch
            }

            _uploadState.value = UploadState.Uploading(0)

            when (val result = s3Stuff.uploadToPhotoBucketV4(photoFile)) {
                is UploadResult.Success -> {
                    _uploadState.value = UploadState.Success("Photo uploaded successfully!")
                }

                is UploadResult.Error -> {
                    _uploadState.value = UploadState.Error(
                        when (result.code) {
                            413 -> "Photo exceeds 50 MB limit"
                            429 -> "Upload quota reached. Try again later."
                            else -> "Upload failed: ${result.message}"
                        }
                    )
                }
            }
        }
    }
}
```

### In your Composable or Fragment:

```kotlin
@Composable
fun UploadScreen(viewModel: CaptureViewModel) {
    val uploadState by viewModel.uploadState.observeAsState(UploadState.Idle)

    Column {
        when (val state = uploadState) {
            is UploadState.Idle -> {
                Button(onClick = { viewModel.uploadVideo(videoFile) }) {
                    Text("Upload Video")
                }
            }

            is UploadState.Uploading -> {
                CircularProgressIndicator()
                Text("Uploading... ${state.progress}%")
            }

            is UploadState.Success -> {
                Icon(Icons.Default.CheckCircle, contentDescription = "Success")
                Text(state.message, color = Color.Green)
            }

            is UploadState.Error -> {
                Icon(Icons.Default.Error, contentDescription = "Error")
                Text(state.message, color = Color.Red)

                // Allow retry
                Button(onClick = { viewModel.uploadVideo(videoFile) }) {
                    Text("Retry")
                }
            }
        }
    }
}
```

---

## ⚠️ Error Handling Reference

| Error Code | Meaning | User Message | Action |
|------------|---------|--------------|--------|
| **413** | File too large | "Video exceeds 500 MB limit" | Ask user to compress video |
| **429 (quota)** | Daily quota exceeded | "Daily upload limit reached. Try again tomorrow." | Wait until midnight UTC |
| **429 (rate)** | Rate limit exceeded | "Too many uploads. Wait 1 minute and try again." | Retry after delay |
| **401/403** | Not authenticated | "Please login to upload" | Navigate to login |
| **400** | Bad request | "Invalid upload request" | Check file/parameters |
| **-1** | Network error | "Network error. Check your connection." | Retry when online |

---

## 🧪 Testing the Updates

### Test 1: Oversized File Rejection

```kotlin
@Test
fun testOversizedVideoRejection() = runTest {
    // Create a mock file > 500 MB
    val largeFile = File("/path/to/large/video.mp4")  // > 500 MB

    val s3Stuff = S3Stuff()
    val result = s3Stuff.uploadToVideoBucketV4(largeFile)

    assertTrue(result is UploadResult.Error)
    assertEquals(413, (result as UploadResult.Error).code)
}
```

### Test 2: Normal Upload

```kotlin
@Test
fun testNormalVideoUpload() = runTest {
    val normalFile = File("/path/to/normal/video.mp4")  // < 500 MB

    val s3Stuff = S3Stuff()
    val result = s3Stuff.uploadToVideoBucketV4(normalFile)

    assertTrue(result is UploadResult.Success)
}
```

### Manual Testing:

1. **Test file size validation:**
   - Try uploading a 600 MB video
   - Expected: Error "Video too large. Maximum size is 500 MB"

2. **Test daily quota:**
   - Upload 101 videos in one day
   - Expected: #101 shows "Daily upload limit reached"

3. **Test rate limiting:**
   - Rapidly upload 25 videos
   - Expected: After 20, see "Too many uploads. Please wait..."

---

## 🔄 Migration Checklist

- [ ] Update all calls from `uploadToVideoBucket()` to `uploadToVideoBucketV4()`
- [ ] Update all calls from `uploadToPhotoBucket()` to `uploadToPhotoBucketV4()`
- [ ] Add proper error handling with `when (result) { ... }`
- [ ] Show user-friendly error messages in UI
- [ ] Test with oversized files (> 500 MB video, > 50 MB photo)
- [ ] Test quota limits (upload > 100 files)
- [ ] Test rate limiting (upload rapidly)
- [ ] Add retry logic for temporary failures
- [ ] Update any hardcoded URLs to use new `/web/presigned_v4` endpoint

---

## 🔮 Future Enhancements

### 1. Device Authentication (High Priority)

Currently, authentication is commented out:
```kotlin
// TODO: Add device authentication token
// connection.setRequestProperty("Authorization", "Bearer $authToken")
```

**Next steps:**
1. Implement device registration (see DEVICE_AUTHENTICATION_GUIDE.md)
2. Store auth token securely in EncryptedSharedPreferences
3. Add token to all presigned URL requests

### 2. Upload Progress Tracking

```kotlin
suspend fun uploadFileToS3V4WithProgress(
    bucketName: String,
    keyValue: String,
    file: File,
    onProgress: (Int) -> Unit  // Progress callback
): UploadResult {
    // Implementation with progress updates
}
```

### 3. Automatic Retry Logic

```kotlin
suspend fun uploadWithRetry(
    file: File,
    maxRetries: Int = 3
): UploadResult {
    repeat(maxRetries) { attempt ->
        val result = uploadToVideoBucketV4(file)
        if (result is UploadResult.Success) return result

        if (result is UploadResult.Error && result.code == 429) {
            delay(60000)  // Wait 1 minute for rate limit
        } else {
            delay(1000 * (attempt + 1))  // Exponential backoff
        }
    }
    return UploadResult.Error("Upload failed after $maxRetries attempts", -1)
}
```

---

## 📚 Server Limits Reference

| Resource | Limit | Enforcement |
|----------|-------|-------------|
| **Video size** | 500 MB | Server-side (client-side validation recommended) |
| **Photo size** | 50 MB | Server-side (client-side validation recommended) |
| **Metadata size** | 1 MB | Server-side |
| **Daily uploads** | 100 files/device | Server-side |
| **Daily bandwidth** | 5 GB/device | Server-side |
| **Presigned URLs** | 20/minute/device | Server-side (rate limiting) |

---

## 🆘 Troubleshooting

### Issue: "Authentication required" error

**Cause:** Missing or invalid auth token

**Solution:**
1. Check device is registered (see DEVICE_AUTHENTICATION_GUIDE.md)
2. Verify auth token is stored and being sent
3. Check token hasn't been revoked

### Issue: Upload fails with no error message

**Cause:** Network timeout or connection issue

**Solution:**
1. Check internet connection
2. Increase timeout values in `HttpURLConnection`
3. Add retry logic

### Issue: "File too large" even for small files

**Cause:** File size not being sent to server

**Solution:**
1. Ensure using `uploadFileToS3V4()` (not deprecated v2)
2. Check `file.length()` returns correct size
3. Verify `file_size` parameter in URL

---

## 📞 Support

For issues or questions:
1. Check server logs for detailed error messages
2. Review Django admin panel for device quotas
3. Test with curl to isolate client vs server issues

---

**Last Updated:** 2026-02-15
**Android SDK:** Compatible with API 26+
**Kotlin:** 1.9+
