package com.alexblock.netstatus.data.network

import com.alexblock.netstatus.data.ProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ConnectivityDiagnostics {

    private val ENDPOINTS = listOf(
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://www.baidu.com/",
    )

    suspend fun probe(): ProbeResult? = withContext(Dispatchers.IO) {
        var last: ProbeResult? = null
        for (endpoint in ENDPOINTS) {
            val result = probeOnce(endpoint)
            if (result.success) return@withContext result
            last = result
        }
        last
    }

    private fun probeOnce(endpoint: String): ProbeResult {
        val start = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 3000
                readTimeout = 3000
                instanceFollowRedirects = true
                useCaches = false
            }
            val code = conn.responseCode
            val elapsed = System.currentTimeMillis() - start
            ProbeResult(
                endpoint = endpoint,
                latencyMs = elapsed,
                success = code in 200..399,
                atMillis = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            ProbeResult(
                endpoint = endpoint,
                latencyMs = System.currentTimeMillis() - start,
                success = false,
                atMillis = System.currentTimeMillis(),
            )
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Exception) {
                // Ignore.
            }
        }
    }
}
