package co.censo.censowalletintegration

import android.os.Build
import io.github.novacrypto.base58.Base58
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.jce.spec.ECNamedCurveSpec
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.util.encoders.Hex
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldF2m
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.EllipticCurve
import java.util.HexFormat

private val bcProvider = BouncyCastleProvider()
private const val curveName = "secp256r1"

object ECHelper {

    private const val SECP_256_R1 = "secp256r1"
    const val ECDH = "ECDH"
    private const val EC = "EC"

    val keyFactory = KeyFactory.getInstance(EC, BouncyCastleProvider())
    val keyPairGenerator = KeyPairGenerator.getInstance(EC, BouncyCastleProvider())

    val spec: ECNamedCurveParameterSpec = ECNamedCurveTable.getParameterSpec(SECP_256_R1)
    val params: ECParameterSpec = ECNamedCurveSpec(SECP_256_R1, spec.curve, spec.g, spec.n)
    val bouncyParams: org.bouncycastle.jce.spec.ECParameterSpec =
        ECNamedCurveParameterSpec(SECP_256_R1, spec.curve, spec.g, spec.n)

    init {
        val secp256r1 = ECNamedCurveTable.getParameterSpec(SECP_256_R1)
        keyPairGenerator.initialize(secp256r1)
    }

    fun createECKeyPair(): KeyPair {
        return keyPairGenerator.generateKeyPair()
    }
}

object ECPublicKeyDecoder {
    init {
        java.security.Security.addProvider(bcProvider)
    }

    fun extractUncompressedPublicKey(uncompressedPublicKey: ByteArray): ByteArray {
        val sequence: ASN1Sequence = DERSequence.getInstance(uncompressedPublicKey)
        val subjectPublicKey: DERBitString = sequence.getObjectAt(1) as DERBitString
        return subjectPublicKey.bytes
    }

    fun getPublicKeyFromBytes(pubKey: ByteArray): ECPublicKey {
        val securityPoint: org.bouncycastle.math.ec.ECPoint =
            createPoint(ECHelper.params.curve, pubKey)
        val pubKeySpec =
            org.bouncycastle.jce.spec.ECPublicKeySpec(securityPoint, ECHelper.bouncyParams)

        return ECHelper.keyFactory.generatePublic(pubKeySpec) as ECPublicKey
    }

    private fun createPoint(
        curve: EllipticCurve,
        encoded: ByteArray?
    ): org.bouncycastle.math.ec.ECPoint {
        val c: ECCurve = if (curve.field is ECFieldFp) {
            ECCurve.Fp(
                (curve.field as ECFieldFp).p, curve.a, curve.b, null, null
            )
        } else {
            val k = (curve.field as ECFieldF2m).midTermsOfReductionPolynomial
            if (k.size == 3) {
                ECCurve.F2m(
                    (curve.field as ECFieldF2m).m,
                    k[2], k[1], k[0], curve.a, curve.b, null, null
                )
            } else {
                ECCurve.F2m(
                    (curve.field as ECFieldF2m).m, k[0], curve.a, curve.b, null, null
                )
            }
        }
        return c.decodePoint(encoded)
    }


    fun fromHexEncodedString(hexKey: String): ECPublicKey {
        // create a public key using the provided hex string
        val bytes = Hex.decode(hexKey)
        val keyLength = 64
        val startingOffset = if (bytes.size == keyLength + 1 && bytes[0].compareTo(4) == 0) 1 else 0
        val x = bytes.slice(IntRange(startingOffset, 31 + startingOffset)).toByteArray()
        val y = bytes.slice(IntRange(startingOffset + 32, 63 + startingOffset)).toByteArray()

        val pubPoint = ECPoint(BigInteger(1, x), BigInteger(1, y))
        val params = AlgorithmParameters.getInstance("EC", bcProvider).apply {
            init(ECGenParameterSpec(curveName))
        }
        val pubECSpec = ECPublicKeySpec(
            pubPoint,
            params.getParameterSpec(ECParameterSpec::class.java),
        )
        return KeyFactory.getInstance("EC", bcProvider)
            .generatePublic(pubECSpec) as ECPublicKey
    }

    fun fromBase58EncodedString(base58Key: String): ECPublicKey {
        if (Build.VERSION.SDK_INT >= 34) {
            val hexFormatter = HexFormat.of()
            val keyBytes = Base58.base58Decode(base58Key)
            return if (keyBytes.size == 33 || keyBytes.size == 32) {
                // compressed bytes case
                val hexKey = hexFormatter.formatHex(keyBytes)
                val spec = ECNamedCurveTable.getParameterSpec(curveName)
                val pubPoint = spec.curve.decodePoint(Hex.decode(hexKey))
                fromHexEncodedString(hexFormatter.formatHex(pubPoint.getEncoded(false)))
            } else {
                fromHexEncodedString(hexFormatter.formatHex(keyBytes))
            }
        } else {
            val keyBytes = Base58.base58Decode(base58Key)
            return getPublicKeyFromBytes(keyBytes)
        }
    }
}