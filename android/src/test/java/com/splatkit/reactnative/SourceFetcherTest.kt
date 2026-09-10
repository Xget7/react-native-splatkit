package com.splatkit.reactnative

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger

class SourceFetcherTest {
    @get:Rule val folder = TemporaryFolder()

    private lateinit var server: ServerSocket
    private val requests = AtomicInteger(0)
    private val body = ByteArray(1_000_000) { (it % 251).toByte() }

    private fun fetcher() = SourceFetcher(folder.root) { null }
    private fun url(path: String) = "http://127.0.0.1:${server.localPort}$path"
    private fun cacheFiles() = File(folder.root, "splatkit").listFiles()?.map { it.name } ?: emptyList()

    /**
     * The smallest HTTP/1.0 server that can answer the four cases the fetcher
     * has to survive: a whole body, a truncated one, a 404 and one with no
     * declared length. The JDK's own server is not on this module's test
     * classpath, which is built against android.jar.
     */
    @Before
    fun serve() {
        server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (e: IOException) { return@thread }
                thread(isDaemon = true) { answer(socket) }
            }
        }
    }

    private fun answer(socket: Socket) = socket.use { s ->
        val request = s.getInputStream().bufferedReader().readLine() ?: return@use
        val path = request.split(' ')[1]
        val out = s.getOutputStream()
        fun head(status: String, length: Long?) {
            val lengthLine = if (length != null) "Content-Length: $length\r\n" else ""
            out.write("HTTP/1.0 $status\r\n${lengthLine}Connection: close\r\n\r\n".toByteArray())
        }
        when (path) {
            "/world.spz" -> { requests.incrementAndGet(); head("200 OK", body.size.toLong()); out.write(body) }
            "/short.spz" -> { head("200 OK", body.size.toLong()); out.write(body, 0, 400_000) }
            "/missing.spz" -> head("404 Not Found", 0)
            "/unsized.spz" -> { head("200 OK", null); out.write(body, 0, 1234) }
            else -> head("404 Not Found", 0)
        }
        out.flush()
    }

    @After
    fun stop() = server.close()

    @Test
    fun `a whole body lands in the cache with a final progress event`() {
        val events = mutableListOf<Pair<Long, Long>>()
        val file = fetcher().fetch(url("/world.spz"), { false }) { b, t -> events += b to t }
        assertArrayEquals(body, file.readBytes())
        assertEquals(".spz", file.name.takeLast(4))
        assertEquals(body.size.toLong() to body.size.toLong(), events.last())
        assertEquals(listOf(file.name), cacheFiles())
    }

    @Test
    fun `a second fetch of the same uri is served from the cache`() {
        val f = fetcher()
        val first = f.fetch(url("/world.spz"), { false }) { _, _ -> }
        val events = mutableListOf<Pair<Long, Long>>()
        val second = f.fetch(url("/world.spz"), { false }) { b, t -> events += b to t }
        assertEquals(first, second)
        assertEquals(1, requests.get())
        assertEquals(listOf(body.size.toLong() to body.size.toLong()), events)
    }

    @Test
    fun `a body shorter than the declared length is not cached`() {
        val e = runCatching { fetcher().fetch(url("/short.spz"), { false }) { _, _ -> } }.exceptionOrNull()
        assertTrue("expected an IOException, got $e", e is IOException)
        assertTrue(e!!.message!!.contains("truncated"))
        assertEquals(emptyList<String>(), cacheFiles())
    }

    @Test
    fun `a non 2xx answer throws with the code and writes nothing`() {
        val e = runCatching { fetcher().fetch(url("/missing.spz"), { false }) { _, _ -> } }.exceptionOrNull()
        assertTrue(e is IOException)
        assertTrue(e!!.message!!.contains("404"))
        assertEquals(emptyList<String>(), cacheFiles())
    }

    @Test
    fun `an unknown length still ends with a final progress event`() {
        val events = mutableListOf<Pair<Long, Long>>()
        fetcher().fetch(url("/unsized.spz"), { false }) { b, t -> events += b to t }
        assertEquals(1234L to 1234L, events.last())
    }

    @Test
    fun `cancelling mid stream leaves neither a part nor a target`() {
        var calls = 0
        val e = runCatching {
            fetcher().fetch(url("/world.spz"), { ++calls > 1 }) { _, _ -> }
        }.exceptionOrNull()
        assertTrue("expected CancellationException, got $e", e is CancellationException)
        assertEquals(emptyList<String>(), cacheFiles())
    }

    @Test
    fun `two fetchers over one directory do not share a part file`() {
        val a = fetcher()
        val b = fetcher()
        val fromA = a.fetch(url("/world.spz"), { false }) { _, _ -> }
        val fromB = b.fetch(url("/world.spz"), { false }) { _, _ -> }
        assertEquals(fromA, fromB)
        assertArrayEquals(body, fromB.readBytes())
    }

    @Test
    fun `platform sources go through the opener and are cached by uri`() {
        val bytes = "glb".toByteArray()
        val f = SourceFetcher(folder.root) { uri ->
            if (uri.startsWith("asset://")) ByteArrayInputStream(bytes) to -1L else null
        }
        val file = f.fetch("asset://collider.glb", { false }) { _, _ -> }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(".glb", file.name.takeLast(4))
        val e = runCatching { f.fetch("ftp://x", { false }) { _, _ -> } }.exceptionOrNull()
        assertTrue(e is IOException && e.message!!.contains("unsupported"))
    }

    @Test
    fun `extension comes from the last path segment, ignoring query and fragment`() {
        assertEquals(".spz", SourceFetcher.extensionOf("https://h/a/b/world.spz?v=2#x"))
        assertEquals(".glb", SourceFetcher.extensionOf("asset://collider.glb"))
        assertEquals("", SourceFetcher.extensionOf("content://provider/doc/123"))
        assertEquals("", SourceFetcher.extensionOf("https://h/.hidden"))
    }

    @Test
    fun `a fresh fetcher does not sweep a part file another view is writing`() {
        val dir = File(folder.root, "splatkit").apply { mkdirs() }
        val live = File(dir, "abc.spz123.part").apply { writeText("x") }
        val old = File(dir, "old.spz456.part").apply { writeText("x"); setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000L) }
        fetcher()
        assertTrue(live.exists())
        assertFalse(old.exists())
    }

    @Test
    fun `finished files over the cap go oldest first and part files are left alone`() {
        val dir = File(folder.root, "splatkit").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val oldest = File(dir, "a.spz").apply { writeBytes(ByteArray(300)); setLastModified(now - 3000) }
        val middle = File(dir, "b.spz").apply { writeBytes(ByteArray(300)); setLastModified(now - 2000) }
        val newest = File(dir, "c.spz").apply { writeBytes(ByteArray(300)); setLastModified(now - 1000) }
        val part = File(dir, "d.spz1.part").apply { writeBytes(ByteArray(300)) }
        SourceFetcher(folder.root, cacheCapBytes = 700) { null }
        assertFalse(oldest.exists())
        assertTrue(middle.exists())
        assertTrue(newest.exists())
        assertTrue(part.exists())
    }

    @Test
    fun `a cache hit counts as use so the file is evicted last`() {
        val cached = fetcher().fetch(url("/world.spz"), { false }) { _, _ -> }
        val lastWeek = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        cached.setLastModified(lastWeek)
        fetcher().fetch(url("/world.spz"), { false }) { _, _ -> }
        assertTrue(cached.lastModified() > lastWeek)
        assertEquals(1, requests.get())
    }
}
