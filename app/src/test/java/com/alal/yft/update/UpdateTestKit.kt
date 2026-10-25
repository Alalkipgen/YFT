package com.alal.yft.update

import java.io.File
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

/** The release's checksum and APK from memory; [gate] holds the download until it completes. */
internal class FakeApkSource(
    var sums: SumsResult = SumsResult.Found(Installs.SHA256),
    var download: DownloadResult? = null,
    var file: File = File("yft-update.apk"),
) : ApkSource {
    val events = mutableListOf<String>()
    var progress = listOf(0, 42)
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun checksum(update: FoundUpdate): SumsResult {
        events += "checksum ${update.version.apkName}"
        return sums
    }

    override suspend fun download(
        update: FoundUpdate,
        sha256: String,
        onProgress: (percent: Int) -> Unit,
    ): DownloadResult {
        events += "download $sha256"
        progress.forEach(onProgress)
        gate?.await()
        return download ?: DownloadResult.Done(file)
    }

    override fun discard() {
        events += "discard"
    }
}

/** Android's answer about a file: [facts] for every file; [read] lists the files asked about. */
internal class FakeApkReader(var facts: ApkFacts? = Installs.releaseApk()) : ApkReader {
    val read = mutableListOf<String>()

    override fun read(file: File): ApkFacts? {
        read += file.name
        return facts
    }
}

/** Android's side from memory: [events] lists sessions made, committed and given up. */
internal class FakeInstallSystem(
    var allowed: Boolean = true,
    var free: Long = Long.MAX_VALUE,
    var installed: InstalledApp = Installs.release(),
    val own: File = File("base.apk"),
) : InstallSystem {
    val events = mutableListOf<String>()
    var nextSession = 7
    var commitFailure: Exception? = null

    override fun canInstall(): Boolean = allowed

    override fun freeBytes(): Long = free

    override fun installed(): InstalledApp = installed

    override fun ownApk(): File = own

    override fun createSession(size: Long): Int {
        events += "create"
        return nextSession
    }

    override fun commit(sessionId: Int, file: File) {
        commitFailure?.let { throw it }
        events += "commit $sessionId ${file.name}"
    }

    override fun abandon(sessionId: Int) {
        events += "abandon $sessionId"
    }

    override fun abandonAll() {
        events += "abandonAll"
    }
}

internal object Installs {
    const val SHA256 = "5F0C6A0A0D4C7A3B2E9F1D8C7B6A5F4E3D2C1B0A9F8E7D6C5B4A3F2E1D0C9B8A"
    const val RELEASE_KEY = UpdatePolicy.RELEASE_CERT_SHA256
    const val OTHER_KEY = "0000000000000000000000000000000000000000000000000000000000000000"

    fun release(code: Long = 1_000_000) =
        InstalledApp("com.alal.yft", versionCode = code, signers = setOf(RELEASE_KEY))

    fun debug(code: Long = 3) =
        InstalledApp("com.alal.yft.debug", versionCode = code, signers = setOf(OTHER_KEY))

    fun releaseApk(code: Long = 1_000_100, packageName: String = "com.alal.yft") =
        ApkFacts(packageName, versionCode = code, signers = setOf(RELEASE_KEY))
}
