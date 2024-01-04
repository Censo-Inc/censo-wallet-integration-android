package co.censo.walletintegration

import co.censo.core.model.Base58EncodedPublicKey
import co.censo.core.model.Base64EncodedData
import io.github.novacrypto.base58.Base58
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetImportEncryptedDataApiResponse(
    val importState: ImportState,
)

@Serializable
@JvmInline
value class Base58EncodedDevicePublicKey(override val value: String) : Base58EncodedPublicKey {
    init {
        runCatching {
            Base58.base58Decode(value)
        }.onFailure {
            throw IllegalArgumentException("Invalid device public key format")
        }
    }
}

@Serializable
sealed class ImportState {
    @Serializable
    @SerialName("Initial")
    data object Initial : ImportState()

    @Serializable
    @SerialName("Accepted")
    data class Accepted(
        val ownerDeviceKey: Base58EncodedDevicePublicKey,
        val ownerProof: Base64EncodedData,
        val acceptedAt: Instant,
    ) : ImportState()

    @Serializable
    @SerialName("Completed")
    data class Completed(
        val encryptedData: Base64EncodedData,
    ) : ImportState()
}

@Serializable
data class SetImportEncryptedDataApiRequest(
    val encryptedData: Base64EncodedData,
)


@Serializable
data class PhraseExport(
    val binaryPhrase: String,
    val language: Byte,
    val label: String,
)

enum class Language {
    English,
    Spanish,
    French,
    Italian,
    Portugese,
    Czech,
    Japanese,
    Korean,
    ChineseTraditional,
    ChineseSimplified,
    ;

    companion object {
        fun fromLanguageId(id: Byte): Language {
            return when (id.toInt()) {
                1 -> English
                2 -> Spanish
                3 -> French
                4 -> Italian
                5 -> Portugese
                6 -> Czech
                7 -> Japanese
                8 -> Korean
                9 -> ChineseTraditional
                10 -> ChineseSimplified

                else -> throw Exception("Unknown wordlist language id $id")
            }
        }
    }

    fun toId(): Byte {
        return when (this) {
            English -> 1
            Spanish -> 2
            French -> 3
            Italian -> 4
            Portugese -> 5
            Czech -> 6
            Japanese -> 7
            Korean -> 8
            ChineseTraditional -> 9
            ChineseSimplified -> 10
        }
    }
}
