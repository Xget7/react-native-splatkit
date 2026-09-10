package com.splatkit.reactnative

import com.facebook.react.bridge.JavaOnlyMap
import com.splatkit.RenderQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    @Test
    fun `linearBlending 0 turns the preset's blending off`() {
        val q = QualityMapper.fromMap(map("preset" to "ultra", "linearBlending" to 0), warnings::add)
        assertFalse(q.linearBlending)
        assertEquals(RenderQuality.ULTRA.copy(linearBlending = false), q)
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `wrong types warn instead of throwing`() {
        val q = QualityMapper.fromMap(
            map("preset" to 42, "shDegree" to "two", "renderScale" to true, "linearBlending" to "yes"),
            warnings::add,
        )
        assertEquals(RenderQuality.HIGH, q)
        assertEquals(4, warnings.size)
    }

    @Test
    fun `a negative value other than the sentinel is reported, not absorbed`() {
        val q = QualityMapper.fromMap(map("preset" to "high", "renderScale" to -0.5, "shDegree" to -2), warnings::add)
        assertEquals(RenderQuality.HIGH, q)
        assertEquals(2, warnings.size)
        assertNotEquals(RenderQuality.HIGH.copy(renderScale = -0.5f), q)
    }

    @Test
    fun `an explicit null is unset`() {
        val q = QualityMapper.fromMap(map("preset" to "medium").apply { putNull("renderScale") }, warnings::add)
        assertEquals(RenderQuality.MEDIUM, q)
        assertTrue(warnings.isEmpty())
    }
}
