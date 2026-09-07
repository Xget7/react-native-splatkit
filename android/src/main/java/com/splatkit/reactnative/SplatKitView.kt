package com.splatkit.reactnative

import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import com.facebook.react.bridge.ReadableMap
import com.splatkit.CameraPose
import com.splatkit.RenderQuality
import com.splatkit.SplatStats
import com.splatkit.SplatSurfaceView
import java.io.File
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Hosts the engine's own view inside a React Native tree.
 *
 * This class owns no rendering. Every frame, every splat and every Vulkan call
 * belongs to `splatkit-android`, which arrives as a published artifact. What is
 * here is the part React Native needs and the engine has no business knowing
 * about: props, events, the host lifecycle, and turning a URI into bytes.
 *
 * `SplatSurfaceView` is final, so this composes rather than extends.
 */
class SplatKitView(private val reactContext: ThemedReactContext) :
    FrameLayout(reactContext), LifecycleEventListener {

    private val surface = SplatSurfaceView(reactContext)
    private val main = Handler(Looper.getMainLooper())
    private val stats = SplatStats()

    // File and network reads only. Decoding already runs on the engine's own
    // loader thread, so this stays free for the next source.
    private val io = Executors.newSingleThreadExecutor { Thread(it, "SplatKitRnIo") }

    // A source that arrives while an older one is still being read must win, and
    // the older result must be dropped rather than replace it. The world and the
    // collider are independent, so they count separately; one shared counter
    // would let setting the collider discard a world still being read.
    private val worldGeneration = AtomicLong(0)
    private val colliderGeneration = AtomicLong(0)

    // resume() restarts the sensor listener, so the transitions are tracked here
    // instead of being handed to the engine twice.
    private var attached = false
    private var hostResumed = true
    private var running = false

    private var statsIntervalMs = 0
    private var statsTicking = false
    private var announcedEngine = false

    private var currentWorldUri: String? = null
    private var currentColliderUri: String? = null

    // The `cameraPose` prop is a declaration, not a one time command: it is
    // applied when it changes and again when the world and the collider become
    // ready, so an app can set it before the world exists.
    private var declaredPose: CameraPose? = null
    private var worldReady = false

    init {
        addView(
            surface,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        surface.listener = object : SplatSurfaceView.Listener {
            override fun onWorldReady(splatCount: Int) {
                worldReady = true
                declaredPose?.let { surface.cameraPose = it }
                emit("topWorldReady", Arguments.createMap().apply {
                    putInt("splatCount", splatCount)
                })
            }

            override fun onWorldFailed(message: String) {
                emit("topWorldFailed", Arguments.createMap().apply {
                    putString("message", message)
                })
            }

            override fun onColliderReady() {
                if (worldReady) declaredPose?.let { surface.cameraPose = it }
                emit("topColliderReady", Arguments.createMap())
            }

            override fun onColliderFailed(message: String) {
                emit("topColliderFailed", Arguments.createMap().apply {
                    putString("message", message)
                })
            }
        }
        reactContext.addLifecycleEventListener(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // The engine resolves availability in its constructor, but the view has no
        // React tag to dispatch against until it is in the tree.
        if (!announcedEngine) {
            announcedEngine = true
            emit("topEngineReady", Arguments.createMap().apply {
                putBoolean("available", surface.isAvailable)
                putString("gpu", surface.gpuDescription)
            })
        }
        attached = true
        syncRunning()
    }

    override fun onDetachedFromWindow() {
        attached = false
        syncRunning()
        super.onDetachedFromWindow()
    }

    private fun syncRunning() {
        val shouldRun = attached && hostResumed
        if (shouldRun == running) return
        running = shouldRun
        if (shouldRun) surface.resume() else surface.pause()
    }

    // React Native does not lay out children of a view it does not manage, and a
    // SurfaceView with no size renders nothing and reports no error.
    override fun requestLayout() {
        super.requestLayout()
        post {
            measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
            layout(left, top, right, bottom)
        }
    }

    fun setSource(uri: String?) {
        if (uri == currentWorldUri) return
        currentWorldUri = uri
        worldReady = false
        load(uri, worldGeneration, "topWorldFailed", surface::loadWorld, surface::loadWorld)
    }

    fun setCollider(uri: String?) {
        if (uri == currentColliderUri) return
        currentColliderUri = uri
        load(uri, colliderGeneration, "topColliderFailed", surface::loadCollider, surface::loadCollider)
    }

    /**
     * A file on disk goes to the engine as a path, which maps it instead of
     * copying it through the Java heap. Everything else is read to bytes here.
     * Either way the hand over is posted, so it lands after the props of the
     * same transaction (quality applies to worlds loaded after it is set).
     */
    private fun load(
        uri: String?,
        generations: AtomicLong,
        failureEvent: String,
        handFile: (File) -> Unit,
        handBytes: (ByteArray) -> Unit,
    ) {
        if (uri.isNullOrEmpty()) return
        val generation = generations.incrementAndGet()
        fileOf(uri)?.let { file ->
            main.post { if (generation == generations.get()) handFile(file) }
            return
        }
        io.execute {
            val bytes = try {
                readBytes(uri)
            } catch (e: Exception) {
                if (generation != generations.get()) return@execute
                main.post {
                    emit(failureEvent, Arguments.createMap().apply {
                        putString("message", "${e.javaClass.simpleName}: ${e.message} ($uri)")
                    })
                }
                return@execute
            }
            if (generation != generations.get()) return@execute
            main.post { handBytes(bytes) }
        }
    }

    private fun fileOf(uri: String): File? = when {
        uri.startsWith("file://") -> Uri.parse(uri).path?.let(::File)
        uri.startsWith("/") -> File(uri)
        else -> null
    }

    /**
     * Worlds are tens to hundreds of megabytes, so the bytes are read here and
     * never serialised through the bridge.
     */
    private fun readBytes(uri: String): ByteArray = when {
        uri.startsWith("asset://") ->
            reactContext.assets.open(uri.removePrefix("asset://")).use { it.readBytes() }

        uri.startsWith("http://") || uri.startsWith("https://") ->
            URL(uri).openStream().use { it.readBytes() }

        uri.startsWith("${ContentResolver.SCHEME_CONTENT}://") ->
            reactContext.contentResolver.openInputStream(Uri.parse(uri))
                ?.use { it.readBytes() }
                ?: throw IllegalArgumentException("the content provider returned nothing")

        else -> throw IllegalArgumentException("unsupported source")
    }

    /** A preset plus overrides, or the engine's default when the prop is absent. */
    fun setQuality(map: ReadableMap?) {
        val presetName = map?.takeIf { it.hasKey("preset") }?.getString("preset")
        val preset = when (presetName) {
            null -> RenderQuality.HIGH
            else -> RenderQuality.named(presetName)
                ?: throw IllegalArgumentException("unknown quality preset: $presetName")
        }
        val quality = if (map == null) preset else preset.copy(
            renderScale = map.floatOr("renderScale", preset.renderScale),
            maxShDegree = map.intOr("maxShDegree", preset.maxShDegree),
            splatBudget = map.intOr("splatBudget", preset.splatBudget),
            cullMarginDegrees = map.floatOr("cullMarginDegrees", preset.cullMarginDegrees),
            linearBlending = if (map.hasKey("linearBlending")) map.getBoolean("linearBlending") else preset.linearBlending,
        )
        surface.applyQuality(quality)
    }

    private fun ReadableMap.floatOr(key: String, fallback: Float) =
        if (hasKey(key)) getDouble(key).toFloat() else fallback

    private fun ReadableMap.intOr(key: String, fallback: Int) =
        if (hasKey(key)) getInt(key) else fallback

    fun setDeclaredPose(pose: CameraPose?) {
        declaredPose = pose
        if (pose != null && worldReady) surface.cameraPose = pose
    }

    fun teleport(pose: CameraPose) { surface.cameraPose = pose }
    fun setLookSensitivity(value: Float) { surface.lookSensitivity = value }
    fun setWalkSensitivity(value: Float) { surface.walkSensitivity = value }
    fun setMotionEnabled(value: Boolean) = surface.setMotionEnabled(value)

    fun setWalkVelocity(forward: Float, right: Float) = surface.setWalkVelocity(forward, right)

    fun startBenchmark(seconds: Float) = surface.startBenchmark(seconds)

    fun setStatsInterval(millis: Int) {
        statsIntervalMs = millis
        if (millis > 0) startStatsTicker() else statsTicking = false
    }

    private fun startStatsTicker() {
        if (statsTicking) return
        statsTicking = true
        val tick = object : Runnable {
            override fun run() {
                if (!statsTicking || statsIntervalMs <= 0) {
                    statsTicking = false
                    return
                }
                surface.readStats(stats)
                emit("topStats", Arguments.createMap().apply {
                    putDouble("fps", stats.fps.toDouble())
                    putDouble("frameMs", stats.frameMillis.toDouble())
                    putDouble("gpuMs", stats.gpuMillis.toDouble())
                    putDouble("sortMs", stats.sortMillis.toDouble())
                    putInt("splatCount", stats.splatCount)
                    val pose = surface.cameraPose
                    putMap("pose", Arguments.createMap().apply {
                        putDouble("x", pose.x.toDouble())
                        putDouble("y", pose.y.toDouble())
                        putDouble("z", pose.z.toDouble())
                        putDouble("yaw", pose.yaw.toDouble())
                        putDouble("pitch", pose.pitch.toDouble())
                    })
                })
                main.postDelayed(this, statsIntervalMs.toLong())
            }
        }
        main.postDelayed(tick, statsIntervalMs.toLong())
    }

    /** Called when React Native drops the view; the engine's resources go with it. */
    fun release() {
        statsTicking = false
        running = false
        main.removeCallbacksAndMessages(null)
        io.shutdownNow()
        reactContext.removeLifecycleEventListener(this)
        surface.listener = null
        surface.release()
    }

    override fun onHostResume() {
        hostResumed = true
        syncRunning()
    }

    override fun onHostPause() {
        hostResumed = false
        syncRunning()
    }
    override fun onHostDestroy() { /* release() runs when the view is dropped */ }

    private fun emit(name: String, payload: WritableMap) {
        UIManagerHelper.getEventDispatcherForReactTag(reactContext, id)
            ?.dispatchEvent(SplatEvent(UIManagerHelper.getSurfaceId(this), id, name, payload))
    }
}

/** One shape for every event the view sends; the name carries which one it is. */
private class SplatEvent(
    surfaceId: Int,
    viewTag: Int,
    private val name: String,
    private val payload: WritableMap,
) : Event<SplatEvent>(surfaceId, viewTag) {
    override fun getEventName(): String = name
    override fun getEventData(): WritableMap = payload
}
