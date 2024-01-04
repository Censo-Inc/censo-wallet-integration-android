package co.censo.core.model

import co.censo.walletintegration.ECPublicKeyDecoder
import io.github.novacrypto.base58.Base58
import kotlinx.serialization.Serializable
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import java.security.interfaces.ECPublicKey

interface Base58EncodedPublicKey {
    val value: String

    val ecPublicKey: ECPublicKey
        get() = ECPublicKeyDecoder.fromBase58EncodedString(this.value)
}

