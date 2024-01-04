package co.censo.core.model

import kotlinx.serialization.Serializable
import java.util.Base64

@Serializable
@JvmInline
value class Base64EncodedData(val base64Encoded: String) {
    companion object {
        fun encode(data: ByteArray): Base64EncodedData {
            return Base64EncodedData(Base64.getEncoder().encodeToString(data))
        }
    }
    init {
        runCatching {
            Base64.getDecoder().decode(this.base64Encoded)
        }.onFailure {
            throw IllegalArgumentException("Invalid encrypted data format")
        }
    }

    fun decoded(): ByteArray = Base64.getDecoder().decode(this.base64Encoded)
}
