package com.alal.yft.download

/** A time the phone stopped YFT while it held its wake lock (P34, R24). */
internal data class BackgroundFreeze(
    /** How long YFT did nothing beyond the tick it waited for. */
    val lostMs: Long,
    /** What was running, for the one log line. */
    val stage: TaskWork?,
)

/**
 * Notices that the phone froze or stopped YFT in the background (P34, R24): the download service
 * ticks once a [intervalMs] on its own clock (`SystemClock.elapsedRealtime`, which keeps counting
 * in deep sleep). A tick that comes more than [lateMs] late while YFT holds its wake lock cannot
 * be the phone sleeping: something stopped the app.
 */
internal class FreezeDetector(
    private val intervalMs: Long = TICK_MS,
    private val lateMs: Long = LATE_MS,
) {
    private var lastTickMs: Long? = null

    /** One tick at [nowMs]; returns the freeze it ends, if any. */
    fun tick(nowMs: Long, wakeLockHeld: Boolean, stage: TaskWork?): BackgroundFreeze? {
        val last = lastTickMs
        lastTickMs = nowMs
        if (last == null || !wakeLockHeld) return null
        val late = nowMs - last - intervalMs
        return if (late > lateMs) BackgroundFreeze(late, stage) else null
    }

    /** Nothing runs: the next tick starts a new count. */
    fun reset() {
        lastTickMs = null
    }

    companion object {
        const val TICK_MS = 1_000L
        const val LATE_MS = 10_000L
    }
}

/** "Your phone paused YFT in the background for 1 min 40 s." */
internal fun freezeMessage(lostMs: Long): String =
    "Your phone paused YFT in the background for ${pauseLength(lostMs)}."

/** "40 s", "1 min 40 s", "1 h 5 min": how long the phone kept YFT stopped. */
internal fun pauseLength(lostMs: Long): String {
    val seconds = ((lostMs + HALF_SECOND_MS) / MS_PER_SECOND).coerceAtLeast(1L)
    if (seconds < SECONDS_PER_MINUTE) return "$seconds s"
    val minutes = seconds / SECONDS_PER_MINUTE
    if (minutes < MINUTES_PER_HOUR) {
        val rest = seconds % SECONDS_PER_MINUTE
        return if (rest == 0L) "$minutes min" else "$minutes min $rest s"
    }
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return if (rest == 0L) "$hours h" else "$hours h $rest min"
}

private const val MS_PER_SECOND = 1_000L
private const val HALF_SECOND_MS = 500L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L
