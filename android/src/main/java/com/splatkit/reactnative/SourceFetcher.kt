package com.splatkit.reactnative

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/**
 * Turns a source that is not already a file on disk into one, in the app's
 * cache directory, so the engine can map it instead of the binding holding it
 * on the Java heap. A world is tens to hundreds of megabytes; a `ByteArray`
 * that size is an `OutOfMemoryError` waiting for a phone with less RAM.
 *
 * The cache is keyed by the URI. A changed file behind the same URI is not
 * noticed; the README tells apps to bust it with a query string.
 *
 * Writes go to a `.part` file and are renamed at the end, so a crash or a
 * cancellation never leaves a truncated file behind that looks complete.
 */
internal class SourceFetcher(private val context: Context) {
    private val dir = File(context.cacheDir, "splatkit").apply {
        mkdirs()
        listFiles { f -> f.name.endsWith(".part") }?.forEach { it.delete() }
    }

    @Volatile private var connection: HttpURLConnection? = null

    /** Called from any thread; makes a blocked network read fail promptly. */
    fun disconnect() {
        connection?.disconnect()
    }

    @Throws(IOException::class, InterruptedException::class)
    fun fetch(uri: String, cancelled: () -> Boolean, progress: (Long, Long) -> Unit): File {
        val target = File(dir, "${sha1(uri)}${extensionOf(uri)}")
        if (target.isFile && target.length() > 0) {
            progress(target.length(), target.length())
            return target
        }
        val part = File(dir, "${target.name}.part")
        val (stream, total) = open(uri)
        try {
            stream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var copied = 0L
                    while (true) {
                        if (cancelled() || Thread.currentThread().isInterrupted) {
                            throw CancellationException("cancelled: $uri")
                        }
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        copied += n
                        progress(copied, total)
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("could not move ${part.name} into place")
            return target
        } catch (e: Throwable) {
            part.delete()
            throw e
        } finally {
            connection = null
        }
    }

    private fun open(uri: String): Pair<InputStream, Long> = when {
        uri.startsWith("asset://") ->
            context.assets.open(uri.removePrefix("asset://")) to -1L

        uri.startsWith("http://") || uri.startsWith("https://") -> {
            val c = (URL(uri).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            connection = c
            val code = c.responseCode
            if (code !in 200..299) {
                c.disconnect()
                throw IOException("HTTP $code for $uri")
            }
            c.inputStream to c.contentLengthLong
        }

        uri.startsWith("${ContentResolver.SCHEME_CONTENT}://") -> {
            val parsed = Uri.parse(uri)
            val stream = context.contentResolver.openInputStream(parsed)
                ?: throw IOException("the content provider returned nothing for $uri")
            val length = runCatching {
                context.contentResolver.openAssetFileDescriptor(parsed, "r")?.use { it.length } ?: -1L
            }.getOrDefault(-1L)
            stream to length
        }

        else -> throw IOException("unsupported source: $uri")
    }

    private fun extensionOf(uri: String): String {
        val path = Uri.parse(uri).path ?: return ""
        val dot = path.lastIndexOf('.')
        return if (dot >= 0 && dot > path.lastIndexOf('/')) path.substring(dot) else ""
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
