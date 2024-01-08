package co.censo.walletintegration

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

class CensoWalletIntegration: ContentProvider() {
    val apiUrl = "https://api.censo.co"
    val apiVersion = "v1"
    val linkScheme = "censo-main"
    val linkVersion = "v1"
    private var appName: String = "UNKNOWN"

    fun initiate(onFinished: (Boolean) -> Unit): Session {
        return Session(
            name = appName, apiUrl = apiUrl, apiVersion = apiVersion,
            linkScheme = linkScheme, linkVersion = linkVersion, onFinished = onFinished
        )
    }

    override fun onCreate(): Boolean {
        appName = context?.applicationInfo?.name ?: "UNKNOWN"
        return true
    }

    override fun query(
        p0: Uri,
        p1: Array<out String>?,
        p2: String?,
        p3: Array<out String>?,
        p4: String?
    ): Cursor? {
        return null
    }

    override fun getType(p0: Uri): String? {
        return null
    }

    override fun insert(p0: Uri, p1: ContentValues?): Uri? {
        return null
    }

    override fun delete(p0: Uri, p1: String?, p2: Array<out String>?): Int {
        return 0
    }

    override fun update(p0: Uri, p1: ContentValues?, p2: String?, p3: Array<out String>?): Int {
        return 0
    }
}