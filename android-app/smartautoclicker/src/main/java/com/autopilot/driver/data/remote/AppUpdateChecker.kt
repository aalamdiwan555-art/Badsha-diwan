package com.autopilot.driver.data.remote

import android.os.Build
import com.buzbuz.smartautoclicker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the public GitHub release that is used as the distribution source.
 *
 * Releases include a `versionCode=<number>` line in their body. Comparing the
 * integer version code avoids semantic-version ordering bugs with beta builds.
 * This check intentionally fails closed: an old build must not continue when
 * the release service cannot be verified.
 */
class AppUpdateChecker(
    private val releasesUrl: String = RELEASES_URL,
) {
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val connection = (URL(releasesUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Autopilot/${BuildConfig.VERSION_NAME}")
            doInput = true
        }

        try {
            val status = connection.responseCode
            val responseStream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IOException("Update check failed with HTTP $status")
            }

            val release = JSONObject(response)
            val latestVersionCode = parseVersionCode(
                tagName = release.optString("tag_name"),
                body = release.optString("body"),
            ) ?: throw IOException("Latest release has no valid version code")
            val updateUrl = release.optString("html_url").takeIf { it.startsWith("https://") }
                ?: RELEASES_PAGE_URL

            UpdateCheckResult(
                isUpdateRequired = latestVersionCode > BuildConfig.VERSION_CODE,
                latestVersionCode = latestVersionCode,
                updateUrl = updateUrl,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseVersionCode(tagName: String, body: String): Int? {
        val bodyVersion = VERSION_CODE_PATTERN.find(body)?.groupValues?.get(1)?.toIntOrNull()
        if (bodyVersion != null) return bodyVersion
        return TAG_VERSION_PATTERN.find(tagName)?.groupValues?.get(1)?.toIntOrNull()
    }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/aalamdiwan555-art/Badsha-diwan/releases/latest"
        const val RELEASES_PAGE_URL =
            "https://github.com/aalamdiwan555-art/Badsha-diwan/releases/latest"
        private val VERSION_CODE_PATTERN = Regex("""(?im)^\s*versionCode\s*=\s*(\d+)\s*$""")
        private val TAG_VERSION_PATTERN = Regex("""^v?(\d+)$""")
    }
}

data class UpdateCheckResult(
    val isUpdateRequired: Boolean,
    val latestVersionCode: Int,
    val updateUrl: String,
)