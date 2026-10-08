package com.alal.yft.extractor.master

/**
 * Bounded producer/consumer boundary for a future browser host. It is not a WebView hook.
 *
 * Every mutating operation takes a generation so a delayed response from an old page cannot
 * populate a new one. A store is per tab, synchronized, memory-only and cleared on navigation.
 */
class InMemoryCaptureStore(
    private val maxRequests: Int = 200,
    private val maxPayloadChars: Int = 2 * 1024 * 1024,
    private val maxPayloads: Int = 16,
    private val maxRequestChars: Int = 64 * 1024,
) : PlaybackCaptureProvider {
    init {
        require(maxRequests > 0 && maxPayloadChars > 0 && maxPayloads in 1..16)
        require(maxRequestChars > 0)
    }

    private var pageUrl: String? = null
    private var generation: Long = -1
    private var lastGeneration: Long = -1
    private var html: String? = null
    private val payloads = ArrayDeque<String>()
    private val requests = ArrayDeque<CapturedRequest>()
    private var payloadChars = 0
    private var playingUrl: String? = null
    private var authorizedPlayback = false

    @Synchronized
    fun navigate(pageUrl: String, generation: Long) {
        require(UrlPolicy.secure(pageUrl) != null)
        require(generation > lastGeneration)
        clear()
        this.pageUrl = pageUrl
        this.generation = generation
        lastGeneration = generation
    }

    @Synchronized
    fun recordRequest(generation: Long, request: CapturedRequest): Boolean {
        if (generation != this.generation || pageUrl == null) return false
        if (!request.method.equals("GET", true) || UrlPolicy.secure(request.url) == null) {
            return false
        }
        val context = request.context
        val characters = request.url.length.toLong() + (context.cookie?.length ?: 0) +
            (context.userAgent?.length ?: 0) + (context.pageUrl?.length ?: 0) +
            context.observedHeaders.entries.sumOf { it.key.length.toLong() + it.value.length }
        if (characters > maxRequestChars) return false
        while (requests.size >= maxRequests) requests.removeFirst()
        requests.addLast(request)
        return true
    }

    @Synchronized
    fun recordPayload(generation: Long, body: String): Boolean {
        if (generation != this.generation || pageUrl == null) return false
        if (body.length > maxPayloadChars || payloads.size >= maxPayloads) return false
        if (payloadChars.toLong() + body.length + (html?.length ?: 0) > maxPayloadChars) {
            return false
        }
        payloads.addLast(body)
        payloadChars += body.length
        return true
    }

    @Synchronized
    fun recordHtml(generation: Long, body: String): Boolean {
        if (generation != this.generation || pageUrl == null) return false
        if (body.length.toLong() + payloadChars > maxPayloadChars) return false
        html = body
        return true
    }

    @Synchronized
    fun playing(generation: Long, mediaUrl: String?, authorized: Boolean): Boolean {
        if (generation != this.generation || pageUrl == null) return false
        if ((mediaUrl?.length ?: 0) > maxRequestChars) return false
        playingUrl = mediaUrl
        authorizedPlayback = authorized
        return true
    }

    @Synchronized
    fun clear() {
        pageUrl = null
        generation = -1
        html = null
        payloads.clear()
        requests.clear()
        payloadChars = 0
        playingUrl = null
        authorizedPlayback = false
    }

    override suspend fun capture(request: MasterRequest): CaptureResult = snapshot(request)

    @Synchronized
    private fun snapshot(request: MasterRequest): CaptureResult {
        val page = pageUrl ?: return CaptureResult.Unavailable
        if (request.generation != generation || !UrlPolicy.samePage(request.pageUrl, page)) {
            return CaptureResult.Unavailable
        }
        return CaptureResult.Available(
            PageSnapshot(
                pageUrl = page,
                generation = generation,
                html = html,
                apiResponses = payloads.toList(),
                requests = requests.toList(),
                playingMediaUrl = playingUrl,
                authorizedPlayback = authorizedPlayback,
            ),
        )
    }

    override fun toString(): String = "InMemoryCaptureStore(memoryOnly=true)"
}