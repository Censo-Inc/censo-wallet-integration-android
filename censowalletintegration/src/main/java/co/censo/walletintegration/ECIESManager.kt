package co.censo.walletintegration

import co.censo.walletintegration.ECPublicKeyDecoder.getPublicKeyFromBytes
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.KDF2BytesGenerator
import org.bouncycastle.crypto.params.KDFParameters
import java.security.*
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ECIESManager {

    //region variables
    private const val CIPHER_INSTANCE_TYPE = "AES/GCM/NoPadding"
    private const val KDF_BYTE_LENGTH = 32
    private const val PUBLIC_KEY_INDEX = 65
    private const val IV_SIZE = 16
    private const val AES_IV_INDEX = 16
    private const val AES = "AES"
    //endregion

    //region public methods
    fun encryptMessage(dataToEncrypt: ByteArray, publicKeyBytes: ByteArray): ByteArray {
        val publicKey = getPublicKeyFromBytes(publicKeyBytes)
        val ephemeralKeyPair = ECHelper.createECKeyPair()
        val ephemeralPublicKeyBytes =
            ECPublicKeyDecoder.extractUncompressedPublicKey(ephemeralKeyPair.public.encoded)

        val sharedSecret = createSharedSecret(
            privateKey = ephemeralKeyPair.private,
            publicKey = publicKey
        )

        val kdfResult = generateKDFBytes(
            sharedSecret = sharedSecret,
            publicKeyBytes = ephemeralPublicKeyBytes
        )

        val cipher: Cipher = Cipher.getInstance(CIPHER_INSTANCE_TYPE)
        cipher.init(Cipher.ENCRYPT_MODE, kdfResult.aesKey, kdfResult.ivParameterSpec)
        val cipherResult = cipher.doFinal(dataToEncrypt)

        return ephemeralPublicKeyBytes + cipherResult
    }

    fun decryptMessage(cipherData: ByteArray, privateKey: PrivateKey): ByteArray {
        //Public key is first 65 bytes
        val ephemeralPublicKeyBytes = cipherData.slice(0 until PUBLIC_KEY_INDEX).toByteArray()

        //Encrypted data is the rest of the data
        val encryptedData = cipherData.slice(PUBLIC_KEY_INDEX until cipherData.size).toByteArray()

        val ephemeralPublicKey = getPublicKeyFromBytes(ephemeralPublicKeyBytes)

        val sharedSecret = createSharedSecret(
            privateKey = privateKey,
            publicKey = ephemeralPublicKey
        )

        val kdfResult = generateKDFBytes(
            sharedSecret = sharedSecret,
            publicKeyBytes = ephemeralPublicKeyBytes
        )

        val cipher = Cipher.getInstance(CIPHER_INSTANCE_TYPE)
        cipher.init(Cipher.DECRYPT_MODE, kdfResult.aesKey, kdfResult.ivParameterSpec)

        return cipher.doFinal(encryptedData)
    }
    //endregion

    //region private helper methods
    private fun createSharedSecret(privateKey: PrivateKey, publicKey: Key): ByteArray {
        val keyAgreement = KeyAgreement.getInstance(ECHelper.ECDH)

        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)

        return keyAgreement.generateSecret()
    }

    private fun generateKDFBytes(
        sharedSecret: ByteArray,
        publicKeyBytes: ByteArray
    ): KDFResult {
        val aesKeyBytes = ByteArray(KDF_BYTE_LENGTH)
        val kdf = KDF2BytesGenerator(SHA256Digest())
        kdf.init(KDFParameters(sharedSecret, publicKeyBytes))
        kdf.generateBytes(aesKeyBytes, 0, KDF_BYTE_LENGTH)

        val iv = aesKeyBytes.slice(AES_IV_INDEX until aesKeyBytes.size).toByteArray()
        val aesKey = SecretKeySpec(aesKeyBytes.slice(0 until AES_IV_INDEX).toByteArray(), AES)

        val ivParameterSpec = GCMParameterSpec(IV_SIZE * Byte.SIZE_BITS, iv)

        return KDFResult(
            aesKey = aesKey,
            ivParameterSpec = ivParameterSpec
        )
    }
    //endregion
}

data class KDFResult(
    val aesKey: SecretKeySpec,
    val ivParameterSpec: GCMParameterSpec
)
