package com.aeunal.stressball.core

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberFormatTest {
    @Test
    fun `compact formatting`() {
        assertEquals("0.0", NumberFormat.compact(0.0))
        assertEquals("999.9", NumberFormat.compact(999.9))
        assertEquals("1.00K", NumberFormat.compact(1000.0))
        assertEquals("12.3K", NumberFormat.compact(12_345.0))
        assertEquals("123K", NumberFormat.compact(123_456.0))
        assertEquals("1.50M", NumberFormat.compact(1_500_000.0))
        assertEquals("2.00B", NumberFormat.compact(2e9))
        assertEquals("3.00T", NumberFormat.compact(3e12))
        assertEquals("1.00aa", NumberFormat.compact(1e36))
        assertEquals("-4.5", NumberFormat.compact(-4.5))
        assertEquals("7", NumberFormat.compact(7.4, 0))
    }

    @Test
    fun `integer formatting`() {
        assertEquals("0", NumberFormat.integer(0.0))
        assertEquals("999", NumberFormat.integer(999.9))
        assertEquals("1,000", NumberFormat.integer(1000.0))
        assertEquals("123,456", NumberFormat.integer(123_456.7))
        assertEquals("1.00M", NumberFormat.integer(1_000_000.0))
    }

    @Test
    fun `durations`() {
        assertEquals("0:05", NumberFormat.duration(5.0))
        assertEquals("2:03", NumberFormat.duration(123.0))
        assertEquals("1:01:01", NumberFormat.duration(3661.0))
    }
}
