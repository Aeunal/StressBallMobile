package com.aeunal.stressball.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** Idle-game style number formatting: 1.23K, 45.6M, 7.89B, then aa, ab, ... */
object NumberFormat {
    private val suffixes = listOf("", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc")

    /**
     * Formats [value] with at most [decimals] fractional digits for values below
     * 1000, and three significant digits with a suffix above that.
     */
    fun compact(value: Double, decimals: Int = 1): String {
        if (value.isNaN()) return "?"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        val sign = if (value < 0) "-" else ""
        val v = abs(value)
        if (v < 1000.0) return sign + fixed(v, decimals)
        val exp = floor(log10(v) / 3.0).toInt()
        val scaled = v / 1000.0.pow(exp)
        val suffix = if (exp < suffixes.size) suffixes[exp] else letterSuffix(exp - suffixes.size)
        val digits = when {
            scaled >= 100 -> 0
            scaled >= 10 -> 1
            else -> 2
        }
        return sign + fixed(scaled, digits) + suffix
    }

    /** Whole-number formatting with thousands separators for small values, compact above 1M. */
    fun integer(value: Double): String {
        val v = floor(value)
        if (abs(v) < 1_000_000) {
            val n = v.roundToLong()
            val s = abs(n).toString()
            val sb = StringBuilder()
            for ((i, c) in s.withIndex()) {
                if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
                sb.append(c)
            }
            return (if (n < 0) "-" else "") + sb.toString()
        }
        return compact(v, 0)
    }

    fun fixed(v: Double, decimals: Int): String {
        if (decimals <= 0) return v.roundToLong().toString()
        val factor = 10.0.pow(decimals)
        val scaled = (v * factor).roundToLong()
        val whole = scaled / factor.toLong()
        val frac = (scaled % factor.toLong()).toString().padStart(decimals, '0')
        return "$whole.$frac"
    }

    /** Formats seconds as h:mm:ss / m:ss. */
    fun duration(seconds: Double): String {
        val total = seconds.toLong().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun letterSuffix(index: Int): String {
        val first = 'a' + index / 26
        val second = 'a' + index % 26
        return "$first$second"
    }
}
