package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.master.solver.OwnSolverTextFile

/**
 * VK-Master plan §4 (V2), §8: where V2's web-client values come from. [bundled] — main's values,
 * and in a debug build the owner's mutate hook — lists sets for V2 to ask with; [healing] (agent
 * D's wiring) heals the value VK refused from vkvideo.ru's public script (`VkClientSource`).
 *
 * [values] lists the sets best first; with [bundled], V2 asks with the first that is not main's own
 * ([VkClient.BUNDLED]: main asked with it in this lookup and it was refused), and with none when
 * every set is main's. A source that cannot answer lists main's only; it never throws for a value.
 */
interface VkClientValues {
    suspend fun values(): List<VkClient.Values>

    companion object {
        /**
         * Main's values only; with [mutate] (a debug build's hook), its change of them first, as the
         * debug source ([VkClient.Source.DEBUG]). A change that is no change, a value of the wrong
         * shape or a hook that throws: main's values only.
         *
         * The values VK checks are the client id and the API version: login.vk.ru answers
         * `invalid_client` (a 401) for another client id and mints a usable token for any client
         * secret (agent A's census §3.2a), `video.get` answers error 8 for a version it no longer
         * takes. So a hook that forces the value-rejected path breaks the client id or the version,
         * never the secret alone: a changed secret still gets a token.
         */
        fun bundled(mutate: ((VkClient.Values) -> VkClient.Values)? = null): VkClientValues = Bundled(mutate)

        /**
         * Agent D's wiring (plan §8, items 3 and 5): V2 heals the value VK refused main — the client
         * id, else the API version; never the client secret — with S5's order ([ValueSource]: healed
         * today, learned, else one read of vkvideo.ru's page and its common script over [http], at
         * most once a day per value, then nothing). A healed value is kept only after an OK answer
         * whose rows passed V, in [file] (the app's `files/master/site-health.json`; memory only
         * without one) with its source and day, so it survives a restart; one VK refuses again is
         * dropped and healed again. One per process: the day's memory is the instance's.
         *
         * [mutate]: a trial build's break of main's values (the owner's phone proof: main asks with
         * the broken set, so this side knows which value main sent). A change that is no change, a
         * value of the wrong shape or a hook that throws: main's own values.
         */
        fun healing(
            http: ExtractorHttpClient,
            file: OwnSolverTextFile? = null,
            mutate: ((VkClient.Values) -> VkClient.Values)? = null,
            clock: () -> Long = System::currentTimeMillis,
        ): VkClientValues = HealingVkValues(ValueSource(SiteHealth(file, clock), VkClientSource(http)), mainValues(mutate))

        /** Main's values as main sends them: [mutate]'s change ([VkClient.Source.DEBUG]), else [VkClient.BUNDLED]. */
        internal fun mainValues(mutate: ((VkClient.Values) -> VkClient.Values)?): VkClient.Values =
            mutate?.let { hook ->
                try {
                    hook(VkClient.BUNDLED).takeUnless { it.sameAs(VkClient.BUNDLED) }?.copy(source = VkClient.Source.DEBUG)
                } catch (_: Exception) {
                    null
                }
            } ?: VkClient.BUNDLED
    }
}

private class Bundled(private val mutate: ((VkClient.Values) -> VkClient.Values)?) : VkClientValues {
    override suspend fun values(): List<VkClient.Values> {
        val changed = mutate?.let { hook ->
            try {
                hook(VkClient.BUNDLED).takeUnless { it.sameAs(VkClient.BUNDLED) }
                    ?.copy(source = VkClient.Source.DEBUG)
            } catch (_: Exception) {
                null
            }
        }
        return listOfNotNull(changed, VkClient.BUNDLED)
    }

    override fun toString(): String = if (mutate == null) "VkClientValues(bundled)" else "VkClientValues(bundled, debug hook)"
}

/**
 * [VkClientValues.healing]: main's values as main sent them ([main]) and the heal's [source]; V2
 * ([VkLadder]) marks main's refused value rejected and asks [source] for the next. [values] is
 * main's set only (nothing else to ask with before a heal).
 */
internal class HealingVkValues(val source: ValueSource, val main: VkClient.Values) : VkClientValues {
    override suspend fun values(): List<VkClient.Values> = listOf(main)

    override fun toString(): String = "VkClientValues(healing, main's ${main.source.label})"
}
