package com.alal.yft.extractor.master.lookup

import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStart
import com.alal.yft.extractor.master.modules.youtube.ladder.MediaStartReader
import kotlin.coroutines.coroutineContext

/**
 * Phase 1.4 item 7 (YouTube routing B) R2: the ladder's media-start reader (an L row's V and init
 * check, H's first segment) in a route-B build. Under a [RouteBLookup] each read is a new request
 * of Master's (listed in the record, never replayed) and none goes out after a 429 in the lookup
 * or with the user's session; otherwise it only passes through. Nothing is logged.
 */
class RouteBMediaStartReader(private val reader: MediaStartReader) : MediaStartReader {
    override suspend fun read(url: String, headers: Map<String, String>, maxBytes: Int): MediaStart {
        val route = coroutineContext[RouteBLookup] ?: return reader.read(url, headers, maxBytes)
        return route.read(LookupAsk(LookupAsk.GET, url, null, headers)) { reader.read(url, headers, maxBytes) }
    }

    override fun toString(): String = "RouteBMediaStartReader"
}
