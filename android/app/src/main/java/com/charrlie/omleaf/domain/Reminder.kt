package com.charrlie.omleaf.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Daily reminder time, kept as minutes after midnight (0..1439). */
object Reminder {
    const val DEFAULT_MINUTE = 7 * 60

    fun clamp(minuteOfDay: Int): Int = minuteOfDay.coerceIn(0, 24 * 60 - 1)

    /** The next moment (epoch ms) strictly after [nowMs] when the clock shows [minuteOfDay]. */
    fun nextTriggerMs(nowMs: Long, zone: ZoneId, minuteOfDay: Int): Long {
        val m = clamp(minuteOfDay)
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        var at = now.toLocalDate().atTime(LocalTime.of(m / 60, m % 60)).atZone(zone)
        if (!at.toInstant().isAfter(now.toInstant())) at = at.toLocalDate().plusDays(1).atTime(LocalTime.of(m / 60, m % 60)).atZone(zone)
        return at.toInstant().toEpochMilli()
    }

    fun label(minuteOfDay: Int): String {
        val m = clamp(minuteOfDay)
        val h12 = if (m / 60 % 12 == 0) 12 else m / 60 % 12
        return "%d:%02d %s".format(h12, m % 60, if (m / 60 < 12) "AM" else "PM")
    }
}
