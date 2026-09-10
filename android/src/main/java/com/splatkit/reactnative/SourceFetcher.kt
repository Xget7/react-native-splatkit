package com.splatkit.reactnative

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** A stream plus its declared length, -1 when the source does not say. */
internal typealias Opened = Pair<InputStream, Long>

/**
 * Turns a source that is not already a file on disk into one, in the app's
 * cache directory, so the engine can map it instead of the binding holding it
 * on the Java heap. A world is tens to hundreds of megabytes; a `ByteArray`
 * that size is an `OutOfMemoryError` waiting for a phone with less RAM.
 *
 * The cache is keyed by the URI. A changed file behind the same URI is not
 * noticed; the README tells apps to bust it with a query string.
 *
 * Writes go to a `.part` file unique to this fetch and are renamed at the end,
 * after the byte count matched what the source declared, so neither a crash,
 * a cancellation nor a connection cut short leaves a file that looks complete.
 *
 * `http(s)` is handled here with the JDK alone; `asset://` and `content://`
 * come through [platform] so the class can be tested on a plain JVM.
 */
internal class SourceFetcher(
    cacheRoot: File,
    private val cacheCapBytes: Long = CACHE_CAP_BYTES,
    private val platform: (String) -> Opened?,
) {
    constructor(context: Context) : this(context.cacheDir, platform = AndroidSources(context))

    private val dir = File(cacheRoot, "splatkit")

    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var closed = false

    init {
        if (!dir.mkdirs() && !dir.isDirectory) Log.e(TAG, "cannot create the cache directory at $dir")
        // Another view of this app may be writing its own .part right now, so
        // only what nobody could still be writing is swept.
        val stale = System.currentTimeMillis() - STALE_PART_MS
        dir.listFiles { f -> f.name.endsWith(".part") && f.lastModified() < stale }?.forEach { it.delete() }
        trim(cacheCapBytes)
    }

    /**
     * Keeps the finished files under the cap, oldest use first. A hit touches its
     * file, so a world in use stays while one from last month goes.
     */
    private fun trim(cap: Long) {
        val finished = dir.listFiles { f -> f.isFile && !f.name.endsWith(".part") } ?: return
        var kept = 0L
        finished.sortedByDescending { it.lastModified() }.forEach { f ->
            kept += f.length()
            if (kept > cap && !f.delete()) Log.w(TAG, "could not evict ${f.name}")
        }
    }

    /** Called from any thread; makes a blocked network read fail promptly. */
    fun disconnect() {
        closed = true
        connection?.disconnect()
    }

    @Throws(IOException::class)
    fun fetch(uri: String, cancelled: () -> Boolean, progress: (Long, Long) -> Unit): File {
        val target = File(dir, "${sha1(uri)}${extensionOf(uri)}")
        if (target.isFile && target.length() > 0) {
            target.setLastModified(System.currentTimeMillis())
            progress(target.length(), target.length())
            return target
        }
        val (stream, total) = open(uri)
        val part = File.createTempFile(target.name, ".part", dir)
        try {
            var copied = 0L
            stream.use { input ->
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        if (closed || cancelled() || Thread.currentThread().isInterrupted) {
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
            if (total >= 0 && copied != total) {
                throw IOException("truncated: got $copied of $total bytes for $uri")
            }
            if (!part.renameTo(target)) {
                throw IOException("could not move the downloaded file into place at $target for $uri")
            }
            progress(copied, if (total >= 0) total else copied)
            return target
        } catch (e: Throwable) {
            if (!part.delete() && part.exists()) Log.w(TAG, "could not delete ${part.name}")
            throw e
        } finally {
            connection = null
        }
    }

    private fun open(uri: String): Opened = when {
        uri.startsWith("http://") || uri.startsWith("https://") -> openHttp(uri)
        else -> platform(uri) ?: throw IOException("unsupported source: $uri")
    }

    private fun openHttp(uri: String): Opened {
        val c = (URL(uri).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        connection = c
        if (closed) {
            c.disconnect()
            throw CancellationException("cancelled: $uri")
        }
        val code = try {
            c.responseCode
        } catch (e: IOException) {
            connection = null
            throw e
        }
        if (code !in 200..299) {
            c.disconnect()
            connection = null
            throw IOException("HTTP $code for $uri")
        }
        // A transparently decompressed body reports the compressed length, which
        // the byte count can never match; treat it as unknown.
        val total = if (c.contentEncoding.isNullOrEmpty()) c.contentLengthLong else -1L
        return c.inputStream to total
    }

    companion object {
        private const val TAG = "SplatKit"
        private const val STALE_PART_MS = 60 * 60 * 1000L
        /** Worlds run to a hundred MB or more; a handful is worth keeping, a season's worth is not. */
        private const val CACHE_CAP_BYTES = 512L * 1024 * 1024

        /** The extension of the last path segment, before any query or fragment; empty when there is none. */
        fun extensionOf(uri: String): String {
            val name = uri.substringBefore('#').substringBefore('?').substringAfterLast('/')
            val dot = name.lastIndexOf('.')
            return if (dot > 0) name.substring(dot) else ""
        }

        fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

/** The sources only Android can open: app assets and content providers. */
internal class AndroidSources(private val context: Context) : (String) -> Opened? {
    override fun invoke(uri: String): Opened? = when {
        uri.startsWith("asset://") ->
            context.assets.open(uri.removePrefix("asset://")) to -1L

        uri.startsWith("${ContentResolver.SCHEME_CONTENT}://") -> {
            val parsed = Uri.parse(uri)
            val stream = context.contentResolver.openInputStream(parsed)
                ?: throw IOException("the content provider returned nothing for $uri")
            val length = try {
                context.contentResolver.openAssetFileDescriptor(parsed, "r")?.use { it.length } ?: -1L
            } catch (e: Exception) {
                Log.w("SplatKit", "no declared length for $uri", e)
                -1L
            }
            stream to length
        }

        else -> null
    }
}
