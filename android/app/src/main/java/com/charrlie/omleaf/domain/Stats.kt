package com.charrlie.omleaf.domain

data class SessionRecord(
    val epochDay: Long,
    val practice: String,
    val seconds: Int,
    val rounds: Int,
)

data class Stats(
    val sessions: Int,
    val totalMinutes: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    /** Minutes practised on each of the last seven days, oldest first, ending today. */
    val last7Minutes: List<Int>,
)

object StatsCalculator {
    fun compute(records: List<SessionRecord>, todayEpochDay: Long): Stats {
        val days = records.map { it.epochDay }.toSortedSet()
        var best = 0
        var run = 0
        var prev: Long? = null
        for (d in days) {
            run = if (prev != null && d == prev + 1) run + 1 else 1
            if (run > best) best = run
            prev = d
        }
        // A streak is still alive if you practised yesterday and today is not over yet.
        var cursor = if (todayEpochDay in days) todayEpochDay else todayEpochDay - 1
        var current = 0
        while (cursor in days) { current++; cursor-- }

        val last7 = (6 downTo 0).map { back ->
            val day = todayEpochDay - back
            records.filter { it.epochDay == day }.sumOf { it.seconds } / 60
        }
        return Stats(records.size, records.sumOf { it.seconds } / 60, current, best, last7)
    }
}
