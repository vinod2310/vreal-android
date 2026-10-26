import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "1.9.0"

}


android {
    namespace = "com.vreal.signedcapture"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vreal.signedcapture"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "SomethingFest"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
val kotlinSdkVersion = "1.3.112"
val smithyKotlinVersion = "1.0.10"

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.zxing.android.embedded)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle.v130rc01)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.aws.android.sdk.s3)
    implementation((libs.aws.android.sdk.mobileclient))
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.play.services.location)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation (libs.material)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")


    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
    implementation("androidx.media3:media3-common:1.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.3.9")








    implementation("aws.sdk.kotlin:s3:$kotlinSdkVersion")
    implementation("aws.sdk.kotlin:s3control:$kotlinSdkVersion")
    implementation("aws.sdk.kotlin:sts:$kotlinSdkVersion")
    implementation("aws.sdk.kotlin:secretsmanager:$kotlinSdkVersion")
    implementation("aws.smithy.kotlin:http-client-engine-okhttp:$smithyKotlinVersion")
    implementation("aws.smithy.kotlin:http-client-engine-crt:$smithyKotlinVersion")
    implementation("aws.smithy.kotlin:aws-signing-crt:$smithyKotlinVersion")
    implementation("aws.smithy.kotlin:http-auth-aws:$smithyKotlinVersion")




    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}



// Load the secrets.properties file
val secretsPropertiesFile = rootProject.file("signedcapture.properties")
val secrets = Properties()

if (secretsPropertiesFile.exists()) {
    secrets.load(secretsPropertiesFile.inputStream())
} else {
    throw GradleException("Missing signedcapture.properties file (copy signedcapture.properties.example)")
}

android {
    defaultConfig {
        buildConfigField(
            "String",
            "AWS_BUCKET_NAME",
            "\"${secrets["AWS_BUCKET_NAME"]}\""
        )
        buildConfigField(
            "String",
            "AWS_REGION",
            "\"${secrets["AWS_REGION"]}\""
        )
        buildConfigField(
            "String",
            "ANDROID_VERIFIED_VIDEO_BUCKET",
            "\"${secrets["ANDROID_VERIFIED_VIDEO_BUCKET"]}\""
        )
        buildConfigField(
            "String",
            "ANDROID_VERIFIED_PHOTO_BUCKET",
            "\"${secrets["ANDROID_VERIFIED_PHOTO_BUCKET"]}\""
        )
        buildConfigField(
            "String",
            "ANDROID_UNVERIFIED_VIDEO_BUCKET",
            "\"${secrets["ANDROID_UNVERIFIED_VIDEO_BUCKET"]}\""
        )
        buildConfigField(
            "String",
            "ANDROID_UNVERIFIED_PHOTO_BUCKET",
            "\"${secrets["ANDROID_UNVERIFIED_PHOTO_BUCKET"]}\""
        )

        buildConfigField(
            "String",
            "ANDROID_VERIFIED_VIDEO_BUCKET_URL_PREFIX",
            "\"${secrets["ANDROID_VERIFIED_VIDEO_BUCKET_URL_PREFIX"]}\""
        )

        buildConfigField(
            "String",
            "ANDROID_VERIFIED_PHOTO_BUCKET_URL_PREFIX",
            "\"${secrets["ANDROID_VERIFIED_PHOTO_BUCKET_URL_PREFIX"]}\""
        )
    }
}

