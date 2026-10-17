package com.alal.yft.extractor.master.parity

/** §4.2 pass criteria as pure checks. An empty list means the case passes for its scope. */
object ParityVerdict {
    const val DURATION_SLACK_MS = 2_000L

    fun problems(case: ParityCase, a: ArmObservation?, b: ArmObservation?): List<String> {
        if (a == null || b == null) return listOf("missing arm")
        val problems = mutableListOf<String>()
        if (!case.positive && b.outcome !in case.expect && a.outcome in case.expect) {
            problems += "B outcome ${b.outcome} not expected (A ${a.outcome})"
        }
        if (!case.positive || a.outcome != ParityCase.VIDEO) {
            // Negative case or A failed: B must give the same reason and stop there.
            if (a.outcome != ParityCase.VIDEO && b.outcome != ParityCase.VIDEO &&
                a.outcome != b.outcome
            ) problems += "reason differs: A=${a.outcome} B=${b.outcome}"
            if ((b.afterTerminal ?: 0) > 0) {
                problems += "B made ${b.afterTerminal} requests after a terminal failure"
            }
            return problems
        }
        if (b.outcome != ParityCase.VIDEO) return problems + "B found no video where A did"
        if (a.contentId != null && a.contentId != b.contentId) problems += "content ID differs"
        val da = a.durationMillis
        val db = b.durationMillis
        if (da != null && db != null && kotlin.math.abs(da - db) > DURATION_SLACK_MS) {
            problems += "duration differs by more than 2 s"
        }
        if (b.rows.size < a.rows.size) problems += "B rows ${b.rows.size} < A rows ${a.rows.size}"
        b.rows.filter { it.opened != true }.forEach { problems += "B row ${it.label} not opened" }
        b.rows.filter { !it.audioOnly && it.height == null }
            .forEach { problems += "B row ${it.label} has no stated height" }
        val bKeys = b.rows.map { it.key }.toSet()
        a.rows.filter { it.key !in bKeys }.forEach { problems += "B lacks A row ${it.label}" }
        return problems
    }

    /** §4.2 time: B's median ≤ A's median × 1.2 + 500 ms. */
    fun timeOk(aMedianMs: Long, bMedianMs: Long): Boolean = bMedianMs * 10 <= aMedianMs * 12 + 5_000

    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1] + sorted[middle]) / 2
        }
    }
}
