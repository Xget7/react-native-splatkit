package com.splatkit.reactnative

import com.facebook.react.bridge.JavaOnlyMap
import com.splatkit.RenderQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityMapperTest {
    private val warnings = mutableListOf<String>()

    private fun map(vararg pairs: Pair<String, Any>) =
        JavaOnlyMap.of(*pairs.flatMap { listOf(it.first, it.second) }.toTypedArray())

    @Test
    fun `null map is the default preset`() {
        assertEquals(RenderQuality.HIGH, QualityMapper.fromMap(null, warnings::add))
    }

    @Test
    fun `unset sentinels keep the preset`() {
        val q = QualityMapper.fromMap(
            map(
                "preset" to "medium", "renderScale" to -1.0, "shDegree" to -1,
                "splatBudget" to -1, "cullMarginDegrees" to -1.0, "linearBlending" to -1,
            ),
            warnings::add,
        )
        assertEquals(RenderQuality.MEDIUM, q)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `overrides replace the preset's values`() {
        val q = QualityMapper.fromMap(
            map(
                "preset" to "low", "renderScale" to 0.8, "shDegree" to -1,
                "splatBudget" to -1, "cullMarginDegrees" to -1.0, "linearBlending" to 1,
            ),
            warnings::add,
        )
        assertEquals(RenderQuality.LOW.copy(renderScale = 0.8f, linearBlending = true), q)
    }

    @Test
    fun `unknown preset falls back to high and warns instead of throwing`() {
        val q = QualityMapper.fromMap(map("preset" to "ulta"), warnings::add)
        assertEquals(RenderQuality.HIGH, q)
        assertEquals(1, warnings.size)
        assertTrue(warnings.first().contains("ulta"))
    }

    @Test
    fun `missing keys mean unset too`() {
        val q = QualityMapper.fromMap(map("preset" to "ultra"), warnings::add)
        assertEquals(RenderQuality.ULTRA, q)
    }
}
