package com.example.bluetoothtracker.data.billing

import com.example.bluetoothtracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal object BillingVerifier {
    val isConfigured: Boolean get() = BuildConfig.BILLING_VERIFICATION_URL.isNotEmpty()

    /** A positive result is bounded by both the Play expiry and a short verification lifetime. */
    suspend fun verify(purchaseToken: String): Long? = withContext(Dispatchers.IO) {
        val connection = URL(BuildConfig.BILLING_VERIFICATION_URL).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 5_000
            connection.readTimeout = 40_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("purchaseToken", purchaseToken).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == 200) { "Billing verification service unavailable" }
            val bytes = connection.inputStream.use { input ->
                val result = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(256)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    check(result.size() + count <= 1_024) { "Invalid verification response" }
                    result.write(buffer, 0, count)
                }
                result.toByteArray()
            }
            val response = JSONObject(bytes.toString(Charsets.UTF_8))
            if (!response.getBoolean("entitled")) return@withContext null
            val now = System.currentTimeMillis()
            minOf(response.getLong("validUntilMs"), now + 15 * 60_000L).takeIf { it > now }
        } finally {
            connection.disconnect()
        }
    }
}
