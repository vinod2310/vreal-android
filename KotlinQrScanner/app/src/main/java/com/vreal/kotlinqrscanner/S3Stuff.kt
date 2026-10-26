package com.vreal.kotlinqrscanner
// snippet-start:[s3.kotlin.copy_object.import]
import android.content.Context
import android.util.Log
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.services.s3.AmazonS3Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.smithy.kotlin.runtime.content.asByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import java.io.FileInputStream


class S3Stuff {

    // Placeholders: supply your own bucket and credentials to run this.
    val bucketName = "<YOUR_S3_BUCKET>"
    val keyValue = "<YOUR_AWS_SECRET_ACCESS_KEY>"
    val keyName = "<YOUR_AWS_ACCESS_KEY_ID>"

    fun uploadFile(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                uploadFileToS3(bucketName, keyValue, file)
                Log.d("UploadActivity", "File uploaded successfully")
            } catch (e: Exception) {
                Log.e("UploadActivity", "Upload failed: ${e.message}")
            }
        }
    }

    private suspend fun uploadFileToS3(bucketName: String, keyValue: String, file: File) {
        val s3Client = S3Client { region = "ap-southeast-2"
            credentialsProvider = StaticCredentialsProvider {
                accessKeyId = keyName
                secretAccessKey = keyValue
            }
        }

        val request = PutObjectRequest {
            this.bucket = bucketName
            this.key = file.name
            this.body = file.asByteStream()
        }
        s3Client.putObject(request)
        }
    }
