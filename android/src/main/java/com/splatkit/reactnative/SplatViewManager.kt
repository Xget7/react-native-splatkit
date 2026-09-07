package com.splatkit.reactnative

import com.facebook.react.bridge.ReadableArray
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

    // The delegate routes commands on the new architecture; this keeps the view
    // usable through the interop layer as well.
    override fun receiveCommand(view: SplatKitView, command: String, args: ReadableArray?) {
        delegate.receiveCommand(view, command, args)
    }

    // Codegen wires these on the new architecture; declaring them keeps the view
    // working through the interop layer too.
    override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> =
        mutableMapOf(
            "topEngineReady" to mapOf("registrationName" to "onEngineReady"),
            "topWorldReady" to mapOf("registrationName" to "onWorldReady"),
            "topWorldFailed" to mapOf("registrationName" to "onWorldFailed"),
            "topColliderReady" to mapOf("registrationName" to "onColliderReady"),
            "topColliderFailed" to mapOf("registrationName" to "onColliderFailed"),
            "topStats" to mapOf("registrationName" to "onStats"),
        )

    companion object {
        const val NAME = "SplatView"

        private fun poseOf(map: ReadableMap) = CameraPose(
            x = map.getDouble("x").toFloat(),
            y = map.getDouble("y").toFloat(),
            z = map.getDouble("z").toFloat(),
            yaw = if (map.hasKey("yaw")) map.getDouble("yaw").toFloat() else 0f,
            pitch = if (map.hasKey("pitch")) map.getDouble("pitch").toFloat() else 0f,
        )
    }
}
