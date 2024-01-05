package co.censo.walletintegration

import android.util.Log
import io.github.novacrypto.base58.Base58
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.awaitility.Awaitility
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.junit.Test

import org.junit.Assert.*
import java.time.Duration
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.Instant.Companion.now
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Before
import java.nio.charset.Charset
import java.security.KeyPair
import java.util.Base64
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TestSession {
    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
    }

    @Test
    fun `Test session instantiation`() {
        Session("name", "http://apiUrl", "apiVersion", "linkScheme", "linkVersion") {}
    }

    private fun withMockServer(onFinished: (Boolean) -> Unit, test: (MockWebServer, Session) -> Unit) {
        val mockWebServer = MockWebServer()
        mockWebServer.start()
        val session = Session(
            "name",
            mockWebServer.url("/").toString().removeSuffix("/"),
            "v1",
            "linkScheme",
            "v1",
            onFinished
        )
        test(mockWebServer, session)
        mockWebServer.shutdown()
    }

    @Test
    fun `Test session connect cancel`() {
        var result: Boolean? = null
        val onFinished: (Boolean) -> Unit = { r -> result = r }

        withMockServer(onFinished) { mockWebServer, session ->
            mockWebServer.enqueue(
                MockResponse()
                    .setBody("""{"importState": {"type": "Initial"}}""")
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
            )

            val link = session.connect {}
            assertTrue(link.startsWith("linkScheme://v1"))

            Awaitility.await().atMost(
                Duration.ofSeconds(1)
            ).until {
                mockWebServer.requestCount == 1
            }

            assertNull(result)
            session.cancel()
            assertFalse(result!!)

            verifyRequests(mockWebServer, listOf(
                "GET" to "/v1/import/${session.channel()}",
            ))
        }
    }

    private fun acceptedImportState(ownerDeviceKey: KeyPair, dataToSign: ByteArray): String {
        return """
            {"importState": {
                "type": "Accepted",
                "ownerDeviceKey": "${
            Base58.base58Encode(
                (ownerDeviceKey.public as BCECPublicKey).q.getEncoded(
                    false
                )
            )
        }",
                "acceptedAt": "${Clock.System.now()}",
                "ownerProof": "${
            Base64.getEncoder()
                .encodeToString(ownerDeviceKey.sign(dataToSign))
        }"
            }}""".trimIndent()
    }

    @Test
    fun `test session connect succeeds`() {
        var result: Boolean? = null
        val onFinished: (Boolean) -> Unit = { r -> result = r }

        withMockServer(onFinished) { mockWebServer, session ->
            val ownerDeviceKey = ECHelper.createECKeyPair()

            mockWebServer.enqueue(
                MockResponse()
                    .setBody(acceptedImportState(ownerDeviceKey, session.channelKeyRaw()))
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
            )

            var connected = false
            session.connect {
                connected = true
            }

            Awaitility.await().atMost(Duration.ofSeconds(1)).until { connected }

            assertNull(result)
            session.cancel()
            assertFalse(result!!)

            verifyRequests(mockWebServer, listOf(
                "GET" to "/v1/import/${session.channel()}",
            ))
        }
    }

    @Test
    fun `test session connect succeeds but not immediately`() {
        var result: Boolean? = null
        val onFinished: (Boolean) -> Unit = { r -> result = r }

        withMockServer(onFinished) { mockWebServer, session ->
            val ownerDeviceKey = ECHelper.createECKeyPair()

            mockWebServer.enqueue(
                MockResponse()
                    .setBody("""{"importState": {"type": "Initial"}}""")
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
            )

            mockWebServer.enqueue(
                MockResponse()
                    .setBody(acceptedImportState(ownerDeviceKey, session.channelKeyRaw()))
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
            )

            var connected = false
            val link = session.connect {
                connected = true
            }
            assertTrue(link.startsWith("linkScheme://v1"))

            Awaitility.await().atMost(Duration.ofSeconds(3)).until { connected }

            assertNull(result)
            session.cancel()
            assertFalse(result!!)

            verifyRequests(mockWebServer, listOf(
                "GET" to "/v1/import/${session.channel()}",
                "GET" to "/v1/import/${session.channel()}",
            ))
        }
    }

    @Test
    fun `test session completes`() {
        var result: Boolean? = null
        val onFinished: (Boolean) -> Unit = { r -> result = r }

        withMockServer(onFinished) { mockWebServer, session ->
            val ownerDeviceKey = ECHelper.createECKeyPair()

            mockWebServer.enqueue(
                MockResponse()
                    .setBody(acceptedImportState(ownerDeviceKey, session.channelKeyRaw()))
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
            )

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
            )

            val binaryPhrase = "66c6a14c56cd7435d51a61b5aac215824dddd81917f6f80ed10bbf037c8e3676"
            val link = session.connect {
                session.phrase(binaryPhrase)
            }
            assertTrue(link.startsWith("linkScheme://v1"))

            Awaitility.await().atMost(Duration.ofSeconds(1)).until { result }

            val lastRequest = verifyRequests(mockWebServer, listOf(
                "GET" to "/v1/import/${session.channel()}",
                "POST" to "/v1/import/${session.channel()}/encrypted"
            ))
            val setImportEncryptedDataApiRequest = Json.decodeFromString<SetImportEncryptedDataApiRequest>(
                lastRequest!!.body.readString(
                    Charset.defaultCharset()
                )
            )
            val decrypted = ECIESManager.decryptMessage(setImportEncryptedDataApiRequest.encryptedData.decoded(), ownerDeviceKey.private)
            val decoded = Json.decodeFromString<PhraseExport>(decrypted.decodeToString())
            assertEquals("", decoded.label)
            assertEquals(Language.English.toId(), decoded.language)
            assertEquals(binaryPhrase, decoded.binaryPhrase)
        }
    }

    @Test
    fun `test session connect 400 error`() {
        var result: Boolean? = null
        val onFinished: (Boolean) -> Unit = { r -> result = r }

        withMockServer(onFinished) { mockWebServer, session ->
            mockWebServer.enqueue(
                MockResponse().setResponseCode(422)
            )

            session.connect {}

            Awaitility.await().atMost(Duration.ofSeconds(1)).until {
                result == false
            }

            verifyRequests(mockWebServer, listOf(
                "GET" to "/v1/import/${session.channel()}",
            ))
        }
    }

    @Test
    fun `test session connect succeeds after 418 and 500 errors`() {
        var result: Boolean? = null
        val onFinished: (Boolean) -> Unit = { r -> result = r }

        withMockServer(onFinished) { mockWebServer, session ->
            val ownerDeviceKey = ECHelper.createECKeyPair()

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(418)
            )

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
            )

            mockWebServer.enqueue(
                MockResponse()
                    .setBody(acceptedImportState(ownerDeviceKey, session.channelKeyRaw()))
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
            )

            var connected = false
            val link = session.connect {
                connected = true
            }
            assertTrue(link.startsWith("linkScheme://v1"))

            Awaitility.await().atMost(Duration.ofSeconds(5)).until { connected }

            assertNull(result)
            session.cancel()
            assertFalse(result!!)

            verifyRequests(mockWebServer, listOf(
                "GET" to "/v1/import/${session.channel()}",
                "GET" to "/v1/import/${session.channel()}",
                "GET" to "/v1/import/${session.channel()}",
            ))
        }
    }

    @Test
    fun `test session connect fails with invalid owner proof`() {
        var result: Boolean? = null
        val onFinished: (Boolean) -> Unit = { r -> result = r }

        withMockServer(onFinished) { mockWebServer, session ->
            val ownerDeviceKey = ECHelper.createECKeyPair()

            mockWebServer.enqueue(
                MockResponse()
                    .setBody(acceptedImportState(ownerDeviceKey, ownerDeviceKey.publicRaw()))
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
            )

            session.connect {}

            Awaitility.await().atMost(Duration.ofSeconds(1)).until { result == false }

            verifyRequests(mockWebServer, listOf(
                "GET" to "/v1/import/${session.channel()}",
            ))
        }
    }

    @Test
    fun `test phrase export fails`() {
        var result: Boolean? = null
        val onFinished: (Boolean) -> Unit = { r -> result = r }

        withMockServer(onFinished) { mockWebServer, session ->
            val ownerDeviceKey = ECHelper.createECKeyPair()

            mockWebServer.enqueue(
                MockResponse()
                    .setBody(acceptedImportState(ownerDeviceKey, session.channelKeyRaw()))
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
            )

            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
            )

            val binaryPhrase = "66c6a14c56cd7435d51a61b5aac215824dddd81917f6f80ed10bbf037c8e3676"
            session.connect {
                session.phrase(binaryPhrase)
            }

            Awaitility.await().atMost(Duration.ofSeconds(1)).until { result == false }

            verifyRequests(mockWebServer, listOf(
                "GET" to "/v1/import/${session.channel()}",
                "POST" to "/v1/import/${session.channel()}/encrypted"
            ))
        }
    }

    @Test
    fun `Test session expiration`() {
        var result: Boolean? = null
        val onFinished: (Boolean) -> Unit = { r -> result = r }

        val time1 = Clock.System.now()
        val time2 = time1 + 11.minutes
        mockkObject(Clock.System)
        every { Clock.System.now() } returns time1 andThen time2

        withMockServer(onFinished) { mockWebServer, session ->
            session.connect {}

            Awaitility.await().atMost(
                Duration.ofSeconds(1)
            ).until {
                result == false
            }

            assertEquals(false, result)

            verifyRequests(mockWebServer, listOf())
        }
    }

    private fun verifyRequests(mockWebServer: MockWebServer, requests: List<Pair<String, String>>): RecordedRequest? {
        var lastRequest: RecordedRequest? = null
        assertEquals(requests.size, mockWebServer.requestCount)
        requests.indices.forEach {
            val(expectedMethod, expectedPath) = requests[it]
            val request = mockWebServer.takeRequest()
            assertEquals(expectedMethod, request.method)
            assertEquals(expectedPath, request.path)
            lastRequest = request
        }
        return lastRequest
    }
}