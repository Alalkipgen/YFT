package com.alal.yft.extractor.master.policy

import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.PageSnapshot

/** Read limits for one snapshot; anything larger is refused rather than partially read. */
internal object SnapshotBudget {
    const val MAX_PAYLOADS = 16

    fun oversized(snapshot: PageSnapshot, policy: MasterPolicy): Boolean {
        val characters = snapshot.apiResponses.sumOf { it.length.toLong() } +
            (snapshot.html?.length ?: 0)
        return characters > policy.maxSnapshotChars ||
            snapshot.requests.size > policy.maxObservations ||
            snapshot.apiResponses.size > MAX_PAYLOADS
    }
}
