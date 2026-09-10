package com.splatkit.reactnative

import com.facebook.react.bridge.ReadableMap
import com.splatkit.RenderQuality

/**
 * The `quality` prop as the JavaScript wrapper sends it: a preset name and one
 * value per override, where a negative number means "keep the preset's".
 * Absent keys are treated the same way, so a caller through the interop layer
 * that sends only a preset still works.
 *
 * Never throws. A prop is not a place to crash the app from: an unknown preset
 * is reported and `high` is used.
 */
internal object QualityMapper {
    fun fromMap(map: ReadableMap?, warn: (String) -> Unit): RenderQuality {
        if (map == null) return RenderQuality.HIGH
        val name = map.stringOrNull("preset")
        val preset = name?.let(RenderQuality::named) ?: run {
            if (name != null) warn("unknown quality preset '$name'; using high")
            RenderQuality.HIGH
        }
        return preset.copy(
            renderScale = map.floatOr("renderScale", preset.renderScale),
            shDegree = map.intOr("shDegree", preset.shDegree),
            splatBudget = map.intOr("splatBudget", preset.splatBudget),
            cullMarginDegrees = map.floatOr("cullMarginDegrees", preset.cullMarginDegrees),
            linearBlending = when (map.intOr("linearBlending", -1)) {
                -1 -> preset.linearBlending
                0 -> false
                else -> true
            },
        )
    }

    private fun ReadableMap.stringOrNull(key: String): String? =
        if (hasKey(key) && !isNull(key)) getString(key) else null

    private fun ReadableMap.floatOr(key: String, fallback: Float): Float {
        if (!hasKey(key) || isNull(key)) return fallback
        val value = getDouble(key)
        return if (value < 0) fallback else value.toFloat()
    }

    private fun ReadableMap.intOr(key: String, fallback: Int): Int {
        if (!hasKey(key) || isNull(key)) return fallback
        val value = getInt(key)
        return if (value < 0) fallback else value
    }
}
