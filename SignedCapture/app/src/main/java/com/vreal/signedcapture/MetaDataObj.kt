package com.vreal.signedcapture
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.SerialName
class MetaDataObj {
    @Serializable
    data class Root(
        @SerialName("metadata_master_record") val metadataMasterRecord: MetadataMasterRecord,
        @SerialName("device_info") val deviceInfo: DeviceInfo
    )

    @Serializable
    data class MetadataMasterRecord(
        @SerialName("public_key") val publicKey: String,
        val signatures: Map<String, SignatureData>
    )

    @Serializable
    data class SignatureData(
        val signature: String,
        @SerialName("sig_len") val sigLen: String
    )

    @Serializable
    data class DeviceInfo(
        @SerialName("osversion") val osVersion: String,
        val device: String,
        val location: String,
        val model: String,
        val product: String = ""
    )

}