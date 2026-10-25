package com.alal.yft.extractor.master.android.solver

/**
 * Phase 1.2 N4 (strategy E): which message from the page environment ends the run.
 *
 * Only the host page posts (its reply object exists only in frames of the app's origin), and it
 * posts once. The gate still checks every message: one from another frame or origin is ignored;
 * the first one from the host page ends the run, with its text when that is a string within
 * [maxChars], else as a failure; anything later is ignored.
 */
internal class OwnSolverPageReplyGate(
    private val maxChars: Int = MAX_REPLY_CHARS,
) {
    sealed interface Verdict {
        /** The run's reply, for OwnSolverProtocol to decode. */
        data class Reply(val text: String) : Verdict

        /** The host page answered with something that is not a reply. */
        data object Failed : Verdict

        /** Not from the host page, or after its answer. */
        data object Ignored : Verdict
    }

    private var answered = false

    /** [text] is the message's data when it is a string message, else null. */
    @Synchronized
    fun offer(sourceOrigin: String?, isMainFrame: Boolean, text: String?): Verdict {
        if (!isMainFrame || sourceOrigin?.removeSuffix("/") != OwnSolverPageEnvRoutes.ORIGIN) {
            return Verdict.Ignored
        }
        if (answered) return Verdict.Ignored
        answered = true
        return if (text != null && text.length <= maxChars) Verdict.Reply(text) else Verdict.Failed
    }

    companion object {
        /** Upper bound for one reply, which may carry the prepared program (as the worker's). */
        const val MAX_REPLY_CHARS: Int = 12 * 1024 * 1024
    }
}
