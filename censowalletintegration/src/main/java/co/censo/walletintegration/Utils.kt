package co.censo.walletintegration

import java.security.MessageDigest

fun ByteArray.sha256digest(): ByteArray {
    return MessageDigest
        .getInstance("SHA-256")
        .digest(this)
}
fun String.sha256digest() = this.toByteArray().sha256digest()