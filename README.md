# vreal-android

Android clients for vreal. Photos and videos are signed on the device with a hardware-backed key (Android Keystore) and uploaded together with a signed metadata file for verification.

- `SignedCapture/`: the current app. It uploads through presigned URLs from the vreal backend (`https://vreal-gallery.com/web/presigned_v4`), so the app holds no AWS credentials.
- `KotlinQrScanner/`: the earlier prototype. It uploads straight to S3 with static credentials.

## Credentials

This repository does not include any credentials. Every key, password and bucket name has been replaced with a placeholder:

- `SignedCapture/app/src/main/java/com/vreal/signedcapture/ViewVideo.kt`: `<YOUR_GOOGLE_MAPS_API_KEY>` (Maps Static API)
- `SignedCapture/signedcapture.properties.example`: bucket names. Copy it to `signedcapture.properties` (gitignored) to build.
- `KotlinQrScanner/app/src/main/java/com/vreal/kotlinqrscanner/S3Stuff.kt`: `<YOUR_S3_BUCKET>`, `<YOUR_AWS_ACCESS_KEY_ID>`, `<YOUR_AWS_SECRET_ACCESS_KEY>`

Release signing configuration and keystores are not included.
