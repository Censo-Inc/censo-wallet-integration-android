package co.censo.walletintegration

import android.util.Log
import co.censo.core.model.Base64EncodedData
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.github.novacrypto.base58.Base58
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import java.security.KeyPair
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import retrofit2.Retrofit
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.time.Duration
import java.util.Base64
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.RejectedExecutionException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val AUTHORIZATION_HEADER = "Authorization"
private const val DEVICE_PUBLIC_KEY_HEADER = "X-Censo-Device-Public-Key"
private const val TIMESTAMP_HEADER = "X-Censo-Timestamp"

class Session(private val name: String, apiUrl: String, apiVersion: String, private val linkScheme: String, private val linkVersion: String, private val onFinished: (Boolean) -> Unit) {
    private val channelKeyPair: KeyPair = ECHelper.createECKeyPair()
    private val authKeyPair: KeyPair = ECHelper.createECKeyPair()
    private val keyPairsCreatedAt: Instant = Clock.System.now()
    private var ownerDeviceKey: ECPublicKey? = null
    var finished: Boolean = false
    private var base64UrlEncoder = Base64.getUrlEncoder()
    private val connectionChecker = newSingleThreadExecutor()
    private val censoApiScope = CoroutineScope(Job() + Dispatchers.IO)
    private val authIntercepter = Interceptor {
        val request = it.request()
        val now = Clock.System.now()
        val signature = Base64.getEncoder().encodeToString(authKeyPair.sign(request.dataToSign(now.toString())))
        it.proceed(
            request.newBuilder().apply {
                addHeader(AUTHORIZATION_HEADER, "signature $signature")
                addHeader(DEVICE_PUBLIC_KEY_HEADER, Base58.base58Encode(authKeyPair.publicRaw()))
                addHeader(TIMESTAMP_HEADER, now.toString())
            }.build()
        )
    }
    private val json: Json by lazy {
        Json { ignoreUnknownKeys = true }
    }
    private val apiService = Retrofit.Builder()
        .baseUrl("$apiUrl/$apiVersion/")
        .client(
            OkHttpClient.Builder()
                .addInterceptor(authIntercepter)
                .connectTimeout(Duration.ofSeconds(180))
                .readTimeout(Duration.ofSeconds(180))
                .callTimeout(Duration.ofSeconds(180))
                .writeTimeout(Duration.ofSeconds(180))
                .build())
        .addConverterFactory(
            json.asConverterFactory(
                "application/json".toMediaType()
            )
        )
        .build()
        .create(ApiService::class.java)

    fun channel(): String {
        return base64UrlEncoder.encodeToString(channelKeyRaw().sha256digest())
    }

    fun channelKeyRaw(): ByteArray {
        return (channelKeyPair.public as BCECPublicKey).q.let {
            it.affineXCoord.encoded + it.affineYCoord.encoded
        }
    }

    fun cancel() {
        finished = true
        onFinished(false)
        connectionChecker.shutdown()
        censoApiScope.cancel()
    }

    private fun checkConnection(onConnection: () -> Unit) {
        val self = this
        censoApiScope.launch {
            val result = apiService.getImportData(channel())
            if (result.isSuccessful) {
                when (val importState = result.body()?.importState) {
                    is ImportState.Accepted -> {
                        if (importState.ownerDeviceKey.ecPublicKey.verify(
                                importState.ownerProof.decoded(),
                                channelKeyRaw()
                            )
                        ) {
                            ownerDeviceKey = importState.ownerDeviceKey.ecPublicKey
                            onConnection()
                        } else {
                            Log.e("CENSO", "Could not verify user")
                            self.cancel()
                        }
                    }
                    else -> {}
                }
            } else if (result.code().let { it < 500 && it != 418 }) {
                Log.e("CENSO", "Connection to Censo terminated or timed-out")
                self.cancel()
            }
        }
    }

    fun phrase(binaryPhrase: String, language: Language? = null, label: String? = null) {
        val phraseExport = PhraseExport(binaryPhrase, language?.toId() ?: Language.English.toId(), label ?: "")
        val phraseExportData = json.encodeToString(phraseExport).toByteArray()
        val encryptedPhrase = ECIESManager.encryptMessage(phraseExportData, (ownerDeviceKey!! as BCECPublicKey).q.getEncoded(false))
        censoApiScope.launch {
            val response = apiService.setImportEncryptedData(
                channel(), SetImportEncryptedDataApiRequest(
                    Base64EncodedData.encode(encryptedPhrase)
                )
            )
            finished = true
            onFinished(response.isSuccessful)
            connectionChecker.shutdown()
        }
    }

    fun connect(onConnected: () -> Unit): String {
        val publicKeyBytes = channelKeyRaw()
        val dateInMillis = keyPairsCreatedAt.toEpochMilliseconds()
        val dateInMillisAsBytes = dateInMillis.toString(10).encodeToByteArray()
        val nameHash = name.sha256digest()
        val dataToSign = dateInMillisAsBytes + nameHash
        val signature = channelKeyPair.sign(dataToSign)
        val encodedSignature = base64UrlEncoder.encodeToString(signature)
        val encodedName = base64UrlEncoder.encodeToString(name.encodeToByteArray())
        return if (channelKeyPair.verify(signature, dataToSign)) {
            var checkConnected: Runnable? = null
            checkConnected = Runnable {
                // check if session is expired (10 minutes after keypair generation)
                if (Clock.System.now() - keyPairsCreatedAt > 10.minutes) {
                    cancel()
                } else {
                    if (!finished && ownerDeviceKey == null) {
                        checkConnection(onConnected)
                        Thread.sleep(2000)
                        try {
                            connectionChecker.execute(checkConnected)
                        } catch (e: RejectedExecutionException) {
                            if (!finished) {
                                Log.e(
                                    "CENSO",
                                    "Connection check task rejected but session is not finished"
                                )
                            }
                        }
                    }
                }
            }
            connectionChecker.execute(checkConnected)
            "$linkScheme://import/$linkVersion/${Base58.base58Encode(publicKeyBytes)}/$dateInMillis/$encodedSignature/$encodedName"
        } else {
            throw Exception("Could not verify signature")
        }
    }
}

fun KeyPair.sign(dataToSign: ByteArray): ByteArray {
    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initSign(this.private)
    signer.update(dataToSign)
    return signer.sign()
}

fun KeyPair.verify(signature: ByteArray, dataToSign: ByteArray) = this.public.verify(signature, dataToSign)

fun PublicKey.verify(signature: ByteArray, dataToSign: ByteArray): Boolean {
    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initVerify(this)
    signer.update(dataToSign)
    return signer.verify(signature)
}

fun KeyPair.publicRaw(): ByteArray {
    return (this.public as BCECPublicKey).q.let {
        it.affineXCoord.encoded + it.affineYCoord.encoded
    }
}

fun Request.dataToSign(timestampOrChallenge: String): ByteArray {
    val requestPathAndQueryParams =
        this.url.encodedPath + (this.url.encodedQuery?.let { "?$it" } ?: "")
    val requestBody = this.body?.let {
        val buffer = Buffer()
        it.writeTo(buffer)
        buffer.readByteArray()
    } ?: byteArrayOf()

    return (this.method + requestPathAndQueryParams + Base64.getEncoder()
        .encodeToString(requestBody) + timestampOrChallenge).toByteArray()
}