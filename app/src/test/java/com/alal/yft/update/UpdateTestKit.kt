package com.alal.yft.update

import kotlinx.coroutines.CompletableDeferred

/** The record in memory only; [writes] counts what the controller saved. */
internal class InMemoryUpdateStore(var record: UpdateRecord = UpdateRecord()) : UpdateStore {
    var writes = 0
        private set

    override fun read(): UpdateRecord = record

    override fun write(record: UpdateRecord) {
        this.record = record
        writes += 1
    }
}

/** Answers with [next] (or waits for [gate]) and counts the questions. */
internal class FakeReleaseCheck(var next: CheckResult = CheckResult.UpToDate) : ReleaseCheck {
    var calls = 0
        private set
    var lastInstalledCode: Long? = null
        private set
    var lastTestMode: Boolean? = null
        private set
    var gate: CompletableDeferred<CheckResult>? = null

    override suspend fun check(installedCode: Long, testMode: Boolean): CheckResult {
        calls += 1
        lastInstalledCode = installedCode
        lastTestMode = testMode
        return gate?.await() ?: next
    }
}

internal object Updates {
    const val HOUR = 60 * 60_000L
    const val DAY = 24 * HOUR

    /** 2026-10-25 12:00 UTC. */
    const val START = 1_792_929_600_000L

    fun found(
        tag: String = "v1.0.1",
        notes: String = "Fixes and a faster download list.",
    ): FoundUpdate {
        val version = checkNotNull(ReleaseVersion.parse(tag))
        return FoundUpdate(
            version = version,
            code = version.ruleCode,
            prerelease = !version.isStable,
            apkUrl = "${ReleaseFixtures.DOWNLOAD}/$tag/${version.apkName}",
            apkSize = 6_334_176,
            sumsUrl = "${ReleaseFixtures.DOWNLOAD}/$tag/SHA256SUMS",
            notes = notes,
            publishedAt = "2026-10-25T09:00:00Z",
        )
    }

    fun release(code: Long = 1_000_000, name: String = "1.0.0") =
        UpdateEnvironment(UpdateMode.RELEASE, installedCode = code, installedName = name)

    fun test(code: Long = 3, name: String = "1.0.0-beta.3-debug") =
        UpdateEnvironment(UpdateMode.TEST, installedCode = code, installedName = name)

    fun off() =
        UpdateEnvironment(UpdateMode.OFF, installedCode = 1_000_000, installedName = "1.0.0")
}
