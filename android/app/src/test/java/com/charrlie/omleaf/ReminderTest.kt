package com.charrlie.omleaf

import com.charrlie.omleaf.domain.Reminder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int, day: Int = 14) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun laterTodayWhenTimeIsAhead() = assertEquals(at(19, 30), Reminder.nextTriggerMs(at(9, 0), zone, 19 * 60 + 30))
    @Test fun tomorrowWhenTimeHasPassed() = assertEquals(at(7, 0, 15), Reminder.nextTriggerMs(at(9, 0), zone, 7 * 60))
    @Test fun tomorrowWhenExactlyNow() = assertEquals(at(9, 0, 15), Reminder.nextTriggerMs(at(9, 0), zone, 9 * 60))
    @Test fun clampsOutOfRange() {
        assertEquals(0, Reminder.clamp(-5))
        assertEquals(1439, Reminder.clamp(5000))
    }
    @Test fun labels() {
        assertEquals("12:05 AM", Reminder.label(5))
        assertEquals("7:00 AM", Reminder.label(420))
        assertEquals("12:00 PM", Reminder.label(720))
        assertEquals("9:45 PM", Reminder.label(21 * 60 + 45))
    }
}
