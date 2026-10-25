package com.alal.yft.extractor.master.social

/**
 * VK-Master plan §4 (V2), §8: where V2's web-client values come from. Agent C implements the live
 * source (the signed config, the web player's public script: `VkClientSource`); this pack ships
 * [bundled] — main's values, and in a debug build the owner's mutate hook — and tests fake it.
 *
 * [values] lists the sets best first; V2 asks with the first that is not main's own
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
         * takes. So a hook that forces the value-rejected path (agent D's, the phone proof) breaks
         * the client id or the version, never the secret alone: a changed secret still gets a token.
         */
        fun bundled(mutate: ((VkClient.Values) -> VkClient.Values)? = null): VkClientValues = Bundled(mutate)
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
