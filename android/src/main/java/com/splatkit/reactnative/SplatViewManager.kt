package com.splatkit.reactnative

import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.SplatViewManagerDelegate
import com.facebook.react.viewmanagers.SplatViewManagerInterface
import com.splatkit.CameraPose

@ReactModule(name = SplatViewManager.NAME)
class SplatViewManager :
    SimpleViewManager<SplatKitView>(),
    SplatViewManagerInterface<SplatKitView> {

    private val delegate: ViewManagerDelegate<SplatKitView> = SplatViewManagerDelegate(this)

    override fun getDelegate(): ViewManagerDelegate<SplatKitView> = delegate

    override fun getName(): String = NAME

    override fun createViewInstance(context: ThemedReactContext) = SplatKitView(context)

    override fun onDropViewInstance(view: SplatKitView) {
        view.release()
        super.onDropViewInstance(view)
    }

    @ReactProp(name = "source")
    override fun setSource(view: SplatKitView, value: ReadableMap?) {
        view.setSource(value?.getString("uri"))
    }

    @ReactProp(name = "collider")
    override fun setCollider(view: SplatKitView, value: ReadableMap?) {
        view.setCollider(value?.getString("uri"))
    }

    @ReactProp(name = "quality")
    override fun setQuality(view: SplatKitView, value: ReadableMap?) {
        view.setQuality(value)
    }

    @ReactProp(name = "cameraPose")
    override fun setCameraPose(view: SplatKitView, value: ReadableMap?) {
        view.setDeclaredPose(value?.let(::poseOf))
    }

    @ReactProp(name = "motionEnabled", defaultBoolean = false)
    override fun setMotionEnabled(view: SplatKitView, value: Boolean) {
        view.setMotionEnabled(value)
    }

    @ReactProp(name = "lookSensitivity", defaultDouble = 0.004)
    override fun setLookSensitivity(view: SplatKitView, value: Double) {
        view.setLookSensitivity(value.toFloat())
    }

    @ReactProp(name = "walkSensitivity", defaultDouble = 0.01)
    override fun setWalkSensitivity(view: SplatKitView, value: Double) {
        view.setWalkSensitivity(value.toFloat())
    }

    @ReactProp(name = "statsInterval", defaultInt = 0)
    override fun setStatsInterval(view: SplatKitView, value: Int) {
        view.setStatsInterval(value)
    }

    override fun setWalkVelocity(view: SplatKitView, forward: Double, right: Double) {
        view.setWalkVelocity(forward.toFloat(), right.toFloat())
    }

    override fun setCameraPose(
        view: SplatKitView,
        x: Double,
        y: Double,
        z: Double,
        yaw: Double,
        pitch: Double,
    ) {
        view.teleport(CameraPose(x.toFloat(), y.toFloat(), z.toFloat(), yaw.toFloat(), pitch.toFloat()))
    }

    override fun startBenchmark(view: SplatKitView, seconds: Double) {
        view.startBenchmark(seconds.toFloat())
    }

    // Codegen names the events on the JavaScript side; the view manager registry
    // still asks for them here, and an unnamed direct event is silently dropped.
    override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> =
        mutableMapOf(
            "topEngineReady" to mapOf("registrationName" to "onEngineReady"),
            "topWorldReady" to mapOf("registrationName" to "onWorldReady"),
            "topWorldFailed" to mapOf("registrationName" to "onWorldFailed"),
            "topColliderReady" to mapOf("registrationName" to "onColliderReady"),
            "topColliderFailed" to mapOf("registrationName" to "onColliderFailed"),
            "topLoadProgress" to mapOf("registrationName" to "onLoadProgress"),
            "topStats" to mapOf("registrationName" to "onStats"),
        )

    companion object {
        const val NAME = "SplatView"

        /** A missing or null coordinate is 0 rather than a crash; the prop is typed, so this only guards the interop path. */
        private fun ReadableMap.floatOrZero(key: String): Float =
            if (hasKey(key) && !isNull(key)) getDouble(key).toFloat() else 0f

        private fun poseOf(map: ReadableMap) = CameraPose(
            x = map.floatOrZero("x"),
            y = map.floatOrZero("y"),
            z = map.floatOrZero("z"),
            yaw = map.floatOrZero("yaw"),
            pitch = map.floatOrZero("pitch"),
        )
    }
}
