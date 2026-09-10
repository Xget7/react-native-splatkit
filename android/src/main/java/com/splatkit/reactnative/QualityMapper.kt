package com.splatkit.reactnative

import com.facebook.react.bridge.ReadableMap
import com.splatkit.RenderQuality

/**
 * The `quality` prop as the JavaScript wrapper sends it: a preset name and one
 * value per override, where -1 means "keep the preset's". Absent keys are
 * treated the same way.
 *
 * Never throws. A prop is not a place to crash the app from: an unknown preset,
 * a value of the wrong type or a negative number other than -1 is reported
 * through [warn] and the preset's value is used instead.
 */
internal object QualityMapper {
    const val UNSET = -1

    fun fromMap(map: ReadableMap?, warn: (String) -> Unit): RenderQuality {
        if (map == null) return RenderQuality.HIGH
        val name = map.stringOrNull("preset", warn)
        val preset = name?.let(RenderQuality::named) ?: run {
            if (name != null) warn("unknown quality preset '$name'; using high")
            RenderQuality.HIGH
        }
        return preset.copy(
            renderScale = map.floatOr("renderScale", preset.renderScale, warn),
            shDegree = map.intOr("shDegree", preset.shDegree, warn),
            splatBudget = map.intOr("splatBudget", preset.splatBudget, warn),
            cullMarginDegrees = map.floatOr("cullMarginDegrees", preset.cullMarginDegrees, warn),
            linearBlending = when (map.intOr("linearBlending", UNSET, warn)) {
                UNSET -> preset.linearBlending
                0 -> false
                else -> true
            },
        )
    }

    private fun ReadableMap.stringOrNull(key: String, warn: (String) -> Unit): String? {
        if (!hasKey(key) || isNull(key)) return null
        return try {
            getString(key)
        } catch (e: RuntimeException) {
            warn("quality.$key is not a string; ignoring it")
            null
        }
    }

    private fun ReadableMap.floatOr(key: String, fallback: Float, warn: (String) -> Unit): Float {
        if (!hasKey(key) || isNull(key)) return fallback
        val value = try {
            getDouble(key)
        } catch (e: RuntimeException) {
            warn("quality.$key is not a number; ignoring it")
            return fallback
        }
        if (value == UNSET.toDouble()) return fallback
        if (value < 0) {
            warn("quality.$key $value is negative; ignoring it")
            return fallback
        }
        return value.toFloat()
    }

    private fun ReadableMap.intOr(key: String, fallback: Int, warn: (String) -> Unit): Int {
        if (!hasKey(key) || isNull(key)) return fallback
        val value = try {
            getInt(key)
        } catch (e: RuntimeException) {
            warn("quality.$key is not a number; ignoring it")
            return fallback
        }
        if (value == UNSET) return fallback
        if (value < 0) {
            warn("quality.$key $value is negative; ignoring it")
            return fallback
        }
        return value
    }
}
