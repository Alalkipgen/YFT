package com.alal.yft.core.media.resolver

import com.alal.yft.core.model.media.BrowserReadAnswer
import com.alal.yft.core.model.media.BrowserReadRequest
import com.alal.yft.core.model.media.BrowserReads
import com.alal.yft.core.model.media.BrowserRequestContext
import com.alal.yft.core.model.media.MediaAsset
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.core.model.media.MediaSizeAccuracy
import com.alal.yft.core.model.media.MediaTrackType
import com.alal.yft.core.model.media.MediaVariant
import com.alal.yft.core.model.media.ResolutionStep
import com.alal.yft.core.model.media.VariantResolutionFailure
import com.alal.yft.core.model.media.VariantResolutionResult
import com.alal.yft.core.model.media.VariantSupport
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

@Singleton
class DefaultVariantResolver(
    client: OkHttpClient,
    private val policy: Policy,
    private val clock: () -> Long,
    private val browserReads: BrowserReads = BrowserReads.None,
) : VariantResolver {
    @Inject
    constructor(client: OkHttpClient, browserReads: BrowserReads) : this(
        client = client,
        policy = Policy(),
        clock = System::currentTimeMillis,
        browserReads = browserReads,
    )

    data class Policy(
        val maxRedirects: Int = 5,
        val callTimeoutSeconds: Long = 10,
        val maxManifestBytes: Int = 1_048_576,
    ) {
        init {
            require(maxRedirects >= 0)
            require(callTimeoutSeconds > 0)
            require(maxManifestBytes > 0)
        }
    }

    private val resolverClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(policy.callTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    /**
     * P24: a failure names the request it stopped at ([ResolutionStep]) and that request's host
     * (never its path or query), so the sheet's Details can say where it went wrong.
     */
    override suspend fun resolve(candidate: MediaCandidate): VariantResolutionResult {
        val trace = Trace(host = candidate.mediaUrl.toHttpUrlOrNull()?.host)
        val result = try {
            // P39 (R31): the requests never run on the caller's thread; the Download sheet
            // asks from the main thread, where closing a response can throw.
            withContext(Dispatchers.IO) { resolveSafely(candidate, trace) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: IOException) {
            // P39 step 7: every failure names its error's class, never its message.
            VariantResolutionResult.Failure(
                VariantResolutionFailure.NETWORK,
                error = error.errorClass(),
            )
        } catch (error: IllegalArgumentException) {
            VariantResolutionResult.Failure(
                VariantResolutionFailure.INVALID_URL,
                error = error.errorClass(),
            )
        } catch (error: Exception) {
            // P39 (R31): any other error ends as a failure at the step it stopped at, instead
            // of escaping to the sheet as "manifest not readable" without a step.
            VariantResolutionResult.Failure(
                unexpectedFailureAt(trace.step),
                error = error.errorClass(),
            )
        }
        return if (result is VariantResolutionResult.Failure) {
            result.copy(
                step = result.step ?: trace.step,
                host = result.host ?: trace.host,
                request = result.request ?: trace.request,
                browserStatus = result.browserStatus ?: trace.browserStatus,
            )
        } else {
            result
        }
    }

    /** P39: the exception's simple class name only, never its message, an address or a query. */
    private fun Exception.errorClass(): String = javaClass.simpleName.ifBlank { javaClass.name }

    /** The reason an unexpected error stands for at [step]. */
    private fun unexpectedFailureAt(step: ResolutionStep): VariantResolutionFailure = when (step) {
        ResolutionStep.ADDRESS -> VariantResolutionFailure.INVALID_URL
        ResolutionStep.MANIFEST, ResolutionStep.MEDIA_PLAYLIST ->
            VariantResolutionFailure.MALFORMED_MANIFEST
        else -> VariantResolutionFailure.NETWORK
    }

    /**
     * P24: where a resolution is: the step and host of its latest request. P45: also that
     * request's kind ("HEAD", "range GET", "GET") and what the browser answered when it was
     * asked the same link again ([BrowserReads]; 0 when it could not ask).
     */
    private class Trace(var host: String?, var step: ResolutionStep = ResolutionStep.ADDRESS) {
        var request: String? = null
        var browserStatus: Int? = null
    }

    private suspend fun resolveSafely(
        candidate: MediaCandidate,
        trace: Trace,
    ): VariantResolutionResult {
        val initialUrl = candidate.mediaUrl.toSafeHttpUrl()
            ?: return VariantResolutionResult.Failure(VariantResolutionFailure.INVALID_URL)
        val companion = candidate.audioCompanion
        val companionUrl = companion?.let {
            it.mediaUrl.toSafeHttpUrl()
                ?: return VariantResolutionResult.Failure(VariantResolutionFailure.INVALID_URL)
        }
        val expiresAt = listOfNotNull(
            candidate.expiresAtEpochMs ?: initialUrl.expiryEpochMillis(),
            companion?.let { it.expiresAtEpochMs ?: companionUrl?.expiryEpochMillis() },
        ).minOrNull()
        if (expiresAt != null && expiresAt <= clock()) {
            return VariantResolutionResult.Failure(VariantResolutionFailure.EXPIRED_URL)
        }
        if (candidate.drmHint == true) {
            return VariantResolutionResult.Failure(VariantResolutionFailure.DRM_PROTECTED)
        }

        return when (candidate.effectiveKind()) {
            MediaKind.HLS -> resolveManifest(candidate, initialUrl, expiresAt, MediaKind.HLS, trace)
            MediaKind.DASH ->
                resolveManifest(candidate, initialUrl, expiresAt, MediaKind.DASH, trace)
            MediaKind.DIRECT,
            MediaKind.UNKNOWN,
            -> resolveDirect(candidate, initialUrl, expiresAt, trace)
        }
    }

    private suspend fun resolveDirect(
        candidate: MediaCandidate,
        initialUrl: HttpUrl,
        expiresAt: Long?,
        trace: Trace,
    ): VariantResolutionResult {
        val head = when (
            val execution = execute(
                candidate = candidate,
                credentialOrigin = initialUrl,
                initialUrl = initialUrl,
                method = RequestMethod.HEAD,
                trace = trace,
                step = ResolutionStep.FILE_CHECK,
            )
        ) {
            is HttpExecution.Failed -> return execution.toResolutionFailure()
            is HttpExecution.Completed -> execution
        }

        var finalUrl = head.finalUrl
        var metadata = head.response.use { it.toDirectMetadata() }
        val headRefused = metadata.code !in SUCCESS_CODES
        if (headRefused) metadata = DirectMetadata(code = metadata.code)
        // P24: some file servers answer HEAD with a web page, or send it to their home page,
        // while a range GET of the same address gets the file: the address itself is asked again.
        if (metadata.mimeType.isWebPage()) {
            finalUrl = initialUrl
            metadata = DirectMetadata(code = metadata.code)
        }

        if (
            headRefused ||
            metadata.mimeType == null ||
            metadata.totalLengthBytes == null
        ) {
            val range = when (
                val execution = execute(
                    candidate = candidate,
                    credentialOrigin = initialUrl,
                    initialUrl = finalUrl,
                    method = RequestMethod.RANGE_GET,
                    trace = trace,
                    step = ResolutionStep.FILE_CHECK,
                )
            ) {
                is HttpExecution.Failed -> return execution.toResolutionFailure()
                is HttpExecution.Completed -> execution
            }
            finalUrl = range.finalUrl
            val rangeMetadata = range.response.use { it.toDirectMetadata() }
            if (rangeMetadata.code !in SUCCESS_CODES) {
                return VariantResolutionResult.Failure(
                    reason = VariantResolutionFailure.HTTP_STATUS,
                    httpStatusCode = rangeMetadata.code,
                )
            }
            metadata = DirectMetadata(
                code = rangeMetadata.code,
                mimeType = rangeMetadata.mimeType ?: metadata.mimeType,
                totalLengthBytes = rangeMetadata.totalLengthBytes
                    ?: metadata.totalLengthBytes,
            )
        }

        // P24: a web page is not the video, whatever its address says.
        if (metadata.mimeType.isWebPage()) {
            return VariantResolutionResult.Failure(VariantResolutionFailure.INVALID_URL)
        }
        val mimeType = soundMimeType(candidate, metadata.mimeType)
            ?: metadata.mimeType
            ?: candidate.mimeType?.normalizedMimeType()
        val support = if (mimeType.isKnownUnsupportedMediaMime()) {
            VariantSupport.UNSUPPORTED_CODEC
        } else {
            VariantSupport.SUPPORTED
        }
        if (support == VariantSupport.UNSUPPORTED_CODEC) {
            return VariantResolutionResult.Failure(
                VariantResolutionFailure.UNSUPPORTED_CODEC,
            )
        }
        val companion = candidate.audioCompanion
        val serverBytes = metadata.totalLengthBytes?.takeIf { it >= 0 }
        val sizeBytes = if (companion == null) {
            serverBytes ?: candidate.contentLengthBytes?.takeIf { it >= 0 }
        } else {
            // The merged file is about the size of both inputs; the extractor's sum is a fallback.
            companion.contentLengthBytes
                ?.let { audioBytes -> serverBytes?.plus(audioBytes) }
                ?: candidate.contentLengthBytes?.takeIf { it >= 0 }
        }
        val sizeAccuracy = when {
            sizeBytes == null -> null
            companion != null -> MediaSizeAccuracy.ESTIMATED
            else -> MediaSizeAccuracy.EXACT
        }
        // A video file that does not state its picture is read from its MP4 header (P3).
        val header = if (companion == null && candidate.height == null &&
            mimeType in PROBED_VIDEO_MIME_TYPES
        ) {
            probeMp4Header(candidate, initialUrl, finalUrl, serverBytes)
        } else {
            null
        }
        val trackType = when {
            companion != null -> MediaTrackType.AUDIO_VIDEO
            header != null && header.hasVideo && !header.hasAudio -> MediaTrackType.VIDEO
            header != null && !header.hasVideo && header.hasAudio -> MediaTrackType.AUDIO
            mimeType?.startsWith("audio/") == true -> MediaTrackType.AUDIO
            else -> MediaTrackType.AUDIO_VIDEO
        }
        val durationMillis = candidate.durationMillis ?: header?.durationMillis
        val variant = MediaVariant(
            id = "direct-0",
            playbackUrl = finalUrl.toString(),
            kind = MediaKind.DIRECT,
            trackType = trackType,
            requestContext = candidate.requestContext.forTarget(
                credentialOrigin = initialUrl,
                targetUrl = finalUrl,
            ),
            // Never the page title: Download as and the sheet name a quality from its height.
            label = null,
            mimeType = mimeType,
            container = mimeType.toContainer(finalUrl),
            codecs = candidate.codecs.ifEmpty {
                listOfNotNull(header?.videoCodec, header?.audioCodec)
            },
            width = candidate.width ?: header?.width,
            height = candidate.height ?: header?.height,
            framesPerSecond = candidate.framesPerSecond ?: header?.framesPerSecond,
            bitrateBitsPerSecond = candidate.bitrateBitsPerSecond,
            durationMillis = durationMillis,
            sizeBytes = sizeBytes,
            sizeAccuracy = sizeAccuracy,
            support = support,
            expiresAtEpochMs = expiresAt,
            audioCompanion = companion,
            audioBitrateBitsPerSecond = header?.audioBitrateBitsPerSecond,
        )
        return success(candidate, listOf(variant), durationMillis)
    }

    /**
     * Reads the start of an MP4 file in a few small ranged requests and returns what its boxes
     * state, or null when the server does not answer ranges or the file is not MP4. Sent with
     * the same headers and redirect checks as the size lookup.
     */
    private suspend fun probeMp4Header(
        candidate: MediaCandidate,
        credentialOrigin: HttpUrl,
        fileUrl: HttpUrl,
        totalBytes: Long?,
    ): Mp4Header? {
        val reader = RangeReader { offset, length ->
            val last = offset + length - 1
            val execution = execute(
                candidate = candidate,
                credentialOrigin = credentialOrigin,
                initialUrl = fileUrl,
                method = RequestMethod.RANGE_GET,
                range = "bytes=$offset-$last",
            )
            val completed = execution as? HttpExecution.Completed ?: return@RangeReader null
            completed.response.use { response ->
                val usable = response.code == HTTP_PARTIAL_CONTENT ||
                    (response.code == HTTP_OK && offset == 0L)
                if (!usable) return@RangeReader null
                response.body?.byteStream()?.readAtMost(length)
            }
        }
        return try {
            // Real time, not the caller's clock: the reads are network calls.
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) { Mp4HeaderParser.parse(reader, totalBytes) }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // P39: the header read is optional; any error leaves the row unmeasured.
            null
        }
    }

    private suspend fun resolveManifest(
        candidate: MediaCandidate,
        initialUrl: HttpUrl,
        expiresAt: Long?,
        kind: MediaKind,
        trace: Trace,
    ): VariantResolutionResult {
        val execution = when (
            val result = execute(
                candidate = candidate,
                credentialOrigin = initialUrl,
                initialUrl = initialUrl,
                method = RequestMethod.GET,
                accept = when (kind) {
                    MediaKind.HLS -> HLS_ACCEPT
                    MediaKind.DASH -> DASH_ACCEPT
                    else -> null
                },
                trace = trace,
                step = ResolutionStep.MANIFEST,
            )
        ) {
            is HttpExecution.Failed -> return result.toResolutionFailure()
            is HttpExecution.Completed -> result
        }
        val body = execution.response.use { response ->
            if (response.code !in SUCCESS_CODES) {
                return VariantResolutionResult.Failure(
                    reason = VariantResolutionFailure.HTTP_STATUS,
                    httpStatusCode = response.code,
                )
            }
            val responseBody = response.body
                ?: return VariantResolutionResult.Failure(
                    VariantResolutionFailure.MALFORMED_MANIFEST,
                )
            if (responseBody.contentLength() > policy.maxManifestBytes) {
                return VariantResolutionResult.Failure(
                    VariantResolutionFailure.MANIFEST_TOO_LARGE,
                )
            }
            val bytes = responseBody.byteStream()
                .readAtMost(policy.maxManifestBytes + 1)
            if (bytes.size > policy.maxManifestBytes) {
                return VariantResolutionResult.Failure(
                    VariantResolutionFailure.MANIFEST_TOO_LARGE,
                )
            }
            bytes.toString(Charsets.UTF_8)
        }
        val finalManifestUrl = execution.finalUrl.toString()
        val parsed = when (kind) {
            MediaKind.HLS -> HlsManifestParser.parse(
                manifest = body,
                manifestUrl = finalManifestUrl,
                requestContext = candidate.requestContext,
                expiresAtEpochMs = expiresAt,
            )
            MediaKind.DASH -> DashManifestParser.parse(
                manifest = body,
                manifestUrl = finalManifestUrl,
                requestContext = candidate.requestContext,
                expiresAtEpochMs = expiresAt,
            )
            else -> error("Only manifest kinds can be parsed")
        }
        return when (parsed) {
            ManifestParseResult.DrmProtected -> VariantResolutionResult.Failure(
                VariantResolutionFailure.DRM_PROTECTED,
            )
            ManifestParseResult.Malformed -> VariantResolutionResult.Failure(
                VariantResolutionFailure.MALFORMED_MANIFEST,
            )
            is ManifestParseResult.Parsed -> {
                val variants = parsed.variants.map { variant ->
                    val target = variant.playbackUrl.toSafeHttpUrl()
                        ?: return VariantResolutionResult.Failure(
                            VariantResolutionFailure.INVALID_URL,
                        )
                    variant.copy(
                        // P24: the qualities are fetched like the manifest: Referer and Origin.
                        requestContext = candidate.requestContext.forTarget(
                            credentialOrigin = initialUrl,
                            targetUrl = target,
                        ).withPageOrigin(),
                    )
                }
                if (variants.none(MediaVariant::isPreviewable)) {
                    VariantResolutionResult.Failure(
                        VariantResolutionFailure.UNSUPPORTED_CODEC,
                    )
                } else {
                    // P24: an HLS master states no length; its first quality's playlist does.
                    val length = parsed.durationMillis ?: candidate.durationMillis
                        ?: if (kind == MediaKind.HLS) {
                            playlistLength(candidate, initialUrl, variants, trace)
                        } else {
                            null
                        }
                    trace.step = ResolutionStep.PREPARE
                    val sized = variants.map { it.withLength(length) }
                    success(
                        candidate = candidate,
                        // P50: a video quality with its sound apart is merged with it.
                        variants = if (kind == MediaKind.HLS) {
                            HlsAudioPairing.pair(sized)
                        } else {
                            sized
                        },
                        durationMillis = length,
                    )
                }
            }
        }
    }

    /**
     * P24: the length of an HLS master's video from its first video quality's own playlist (the
     * sum of its pieces), in one bounded request sent like the master's; null when it cannot be
     * read in time. Sizes are then estimated from each quality's bitrate.
     */
    private suspend fun playlistLength(
        candidate: MediaCandidate,
        masterUrl: HttpUrl,
        variants: List<MediaVariant>,
        trace: Trace,
    ): Long? {
        val first = variants.firstOrNull {
            it.isPreviewable && it.trackType != MediaTrackType.AUDIO
        } ?: variants.firstOrNull(MediaVariant::isPreviewable) ?: return null
        val url = first.playbackUrl.toSafeHttpUrl() ?: return null
        return try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(PLAYLIST_TIMEOUT_MS) {
                    readPlaylistLength(candidate, masterUrl, url, trace)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private suspend fun readPlaylistLength(
        candidate: MediaCandidate,
        masterUrl: HttpUrl,
        playlistUrl: HttpUrl,
        trace: Trace,
    ): Long? {
        val execution = execute(
            candidate = candidate,
            credentialOrigin = masterUrl,
            initialUrl = playlistUrl,
            method = RequestMethod.GET,
            accept = HLS_ACCEPT,
            trace = trace,
            step = ResolutionStep.MEDIA_PLAYLIST,
        ) as? HttpExecution.Completed ?: return null
        val text = execution.response.use { response ->
            if (response.code !in SUCCESS_CODES) return null
            val bytes = response.body?.byteStream()?.readAtMost(policy.maxManifestBytes + 1)
                ?: return null
            if (bytes.size > policy.maxManifestBytes) return null
            bytes.toString(Charsets.UTF_8)
        }
        val parsed = HlsManifestParser.parse(
            manifest = text,
            manifestUrl = execution.finalUrl.toString(),
            requestContext = candidate.requestContext,
            expiresAtEpochMs = null,
        ) as? ManifestParseResult.Parsed ?: return null
        return parsed.durationMillis?.takeIf { it > 0 }
    }

    /** P24: a quality of a video of [length] states it, and its size from its bitrate. */
    private fun MediaVariant.withLength(length: Long?): MediaVariant {
        if (length == null || length <= 0) return this
        val estimate = if (sizeBytes == null) estimateSize(bitrateBitsPerSecond, length) else null
        return copy(
            durationMillis = durationMillis ?: length,
            sizeBytes = sizeBytes ?: estimate?.bytes,
            sizeAccuracy = sizeAccuracy ?: estimate?.accuracy,
        )
    }

    /**
     * P24: a manifest found on a page is fetched as the page's player fetches it: with the page as
     * `Referer` and its `Origin`. Only where the page is HTTPS and still named (same origin).
     */
    private fun BrowserRequestContext.withPageOrigin(): BrowserRequestContext {
        val origin = pageOrigin(pageUrl) ?: return this
        if (observedHeaders.keys.any { it.equals(ORIGIN, ignoreCase = true) }) return this
        return copy(observedHeaders = observedHeaders + (ORIGIN to origin))
    }

    private fun pageOrigin(pageUrl: String?): String? {
        val page = pageUrl?.toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
        val port = if (page.port == HTTPS_PORT) "" else ":${page.port}"
        return "https://${page.host}$port"
    }

    private fun success(
        candidate: MediaCandidate,
        variants: List<MediaVariant>,
        durationMillis: Long?,
    ): VariantResolutionResult = if (variants.isEmpty()) {
        VariantResolutionResult.Failure(VariantResolutionFailure.NO_VARIANTS)
    } else {
        VariantResolutionResult.Success(
            MediaAsset(
                sourcePageUrl = candidate.pageUrl,
                title = candidate.title,
                thumbnailUrl = candidate.thumbnailUrl,
                durationMillis = durationMillis,
                variants = variants,
                resolvedAtEpochMs = clock(),
            ),
        )
    }

    private suspend fun execute(
        candidate: MediaCandidate,
        credentialOrigin: HttpUrl,
        initialUrl: HttpUrl,
        method: RequestMethod,
        accept: String? = null,
        range: String? = null,
        trace: Trace? = null,
        step: ResolutionStep? = null,
    ): HttpExecution {
        var currentUrl = initialUrl
        var redirectCount = 0
        trace?.browserStatus = null
        while (true) {
            trace?.let {
                it.host = currentUrl.host
                if (step != null) it.step = step
                it.request = method.label
            }
            val requestBuilder = Request.Builder().url(currentUrl)
            val headers = candidate.requestContext.headersForTarget(credentialOrigin, currentUrl)
            // P24: a manifest request carries the page's Origin next to its Referer.
            val sent = if (accept != null && headers.keys.any { it.equals(REFERER, true) }) {
                candidate.requestContext.withPageOrigin().observedHeaders
                    .filterKeys { it.equals(ORIGIN, ignoreCase = true) } + headers
            } else {
                headers
            }
            sent.forEach { (name, value) ->
                runCatching { requestBuilder.header(name, value) }
            }
            if (accept != null) requestBuilder.header("Accept", accept)
            when (method) {
                RequestMethod.HEAD -> requestBuilder.head()
                RequestMethod.RANGE_GET -> requestBuilder
                    .get()
                    .header("Range", range ?: RANGE_FIRST_BYTE)
                RequestMethod.GET -> requestBuilder.get()
            }

            val request = requestBuilder.build()
            val response = resolverClient.newCall(request).await()
            if (response.code !in REDIRECT_CODES) {
                val browser = askBrowser(candidate, request, response.code, method, step, trace)
                if (browser != null) {
                    response.close()
                    return browser
                }
                return HttpExecution.Completed(response, currentUrl)
            }
            if (redirectCount >= policy.maxRedirects) {
                response.close()
                return HttpExecution.Failed(VariantResolutionFailure.TOO_MANY_REDIRECTS)
            }
            val target = response.header("Location")
                ?.let(currentUrl::resolve)
                ?.takeIf { it.isSafeHttpUrl() }
            response.close()
            if (target == null || currentUrl.isHttps && !target.isHttps) {
                return HttpExecution.Failed(VariantResolutionFailure.UNSAFE_REDIRECT)
            }
            currentUrl = target
            redirectCount += 1
        }
    }

    /**
     * P45: a refusal of a link a browser page names ([BrowserReads.isRefusal]: 403, 410, 412,
     * 452–499) is asked once more the way the page's player asks it: by the browser's own engine
     * ([browserReads]). Only a file check, a manifest or a playlist, never a HEAD (its range GET
     * follows) nor a site adapter's file (its own rules stand). The browser's answer stands in
     * for YFT's when it got the file or the text; otherwise YFT's refusal stands and the trace
     * keeps the browser's status for the sheet's Details.
     */
    private suspend fun askBrowser(
        candidate: MediaCandidate,
        request: Request,
        code: Int,
        method: RequestMethod,
        step: ResolutionStep?,
        trace: Trace?,
    ): HttpExecution.Completed? {
        if (method == RequestMethod.HEAD || step !in BROWSER_STEPS) return null
        if (!BrowserReads.isRefusal(code) || candidate.videoId != null) return null
        val pageUrl = candidate.requestContext.pageUrl
            ?.takeIf { it.toHttpUrlOrNull()?.isHttps == true }
            ?: return null
        if (!request.url.isHttps && !request.url.host.isLoopbackHost()) return null
        val wantsText = method == RequestMethod.GET
        val answer = try {
            browserReads.read(
                BrowserReadRequest(
                    url = request.url.toString(),
                    pageUrl = pageUrl,
                    userAgent = candidate.requestContext.userAgent,
                    wantsText = wantsText,
                    maxBytes = policy.maxManifestBytes,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        trace?.browserStatus = answer?.status ?: BROWSER_NOT_ASKED
        if (answer == null || !answer.isSuccess || wantsText && answer.text == null) return null
        val finalUrl = answer.finalUrl?.toSafeHttpUrl()?.takeIf { it.isHttps } ?: request.url
        return HttpExecution.Completed(answer.toResponse(request, wantsText), finalUrl)
    }

    /** P45: the browser's [BrowserReadAnswer] as the response the resolver reads. */
    private fun BrowserReadAnswer.toResponse(request: Request, wantsText: Boolean): Response {
        val type = contentType?.toMediaTypeOrNull()
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(status)
            .message(BROWSER_MESSAGE)
        contentType?.let { builder.header("Content-Type", it) }
        if (!wantsText) contentLength?.let { builder.header("Content-Length", it.toString()) }
        return builder.body(text.orEmpty().toResponseBody(type)).build()
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            },
        )
    }

    private fun HttpExecution.Failed.toResolutionFailure(): VariantResolutionResult.Failure =
        VariantResolutionResult.Failure(reason)

    private fun MediaCandidate.effectiveKind(): MediaKind {
        if (kind != MediaKind.UNKNOWN) return kind
        val mime = mimeType?.normalizedMimeType()
        return when {
            mime in HLS_MIME_TYPES || mediaUrl.substringBefore('?')
                .endsWith(".m3u8", ignoreCase = true) -> MediaKind.HLS
            mime == DASH_MIME_TYPE || mediaUrl.substringBefore('?')
                .endsWith(".mpd", ignoreCase = true) -> MediaKind.DASH
            else -> MediaKind.DIRECT
        }
    }

    private fun HttpUrl.expiryEpochMillis(): Long? {
        val value = queryParameterNames
            .firstOrNull { name -> name.lowercase(Locale.US) in EXPIRY_QUERY_NAMES }
            ?.let(::queryParameter)
            ?: return null
        value.toLongOrNull()?.let { numeric ->
            return if (numeric < EPOCH_MILLIS_THRESHOLD) {
                runCatching { Math.multiplyExact(numeric, 1_000L) }.getOrNull()
            } else {
                numeric
            }
        }
        return value.parseIsoInstantMillis()
    }

    private fun String.toSafeHttpUrl(): HttpUrl? = toHttpUrlOrNull()
        ?.takeIf { it.isSafeHttpUrl() }

    private fun HttpUrl.isSafeHttpUrl(): Boolean =
        username.isEmpty() &&
            password.isEmpty() &&
            (isHttps || scheme == "http" && host.isLoopbackHost())

    private fun String.isLoopbackHost(): Boolean =
        equals("localhost", ignoreCase = true) || this == "127.0.0.1" || this == "::1"

    private fun BrowserRequestContext.headersForTarget(
        credentialOrigin: HttpUrl,
        targetUrl: HttpUrl,
    ): Map<String, String> {
        val replayable = replayHeaders()
        if (targetUrl.hasSameOrigin(credentialOrigin)) return replayable
        return replayable.filterKeys { name ->
            name.lowercase(Locale.US) in CROSS_ORIGIN_HEADER_ALLOWLIST
        }
    }

    private fun BrowserRequestContext.forTarget(
        credentialOrigin: HttpUrl,
        targetUrl: HttpUrl,
    ): BrowserRequestContext {
        if (targetUrl.hasSameOrigin(credentialOrigin)) return this
        return BrowserRequestContext(
            pageUrl = null,
            userAgent = userAgent,
            cookie = null,
            observedHeaders = observedHeaders.filterKeys { name ->
                name.lowercase(Locale.US) in CROSS_ORIGIN_HEADER_ALLOWLIST
            },
        )
    }

    private fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
        scheme == other.scheme && host == other.host && port == other.port

    private fun Response.toDirectMetadata(): DirectMetadata {
        val contentRange = header("Content-Range")
        val contentRangeTotal = contentRange
            ?.substringAfterLast('/', missingDelimiterValue = "")
            ?.trim()
            ?.takeUnless { it == "*" }
            ?.toLongOrNull()
        val contentLength = header("Content-Length")
            ?.toLongOrNull()
            ?.takeUnless { code == 206 && contentRange != null }
        return DirectMetadata(
            code = code,
            mimeType = header("Content-Type")?.normalizedMimeType(),
            totalLengthBytes = contentRangeTotal ?: contentLength,
        )
    }

    private fun String.normalizedMimeType(): String =
        substringBefore(';').trim().lowercase(Locale.US)

    private fun String?.isWebPage(): Boolean =
        this == "text/html" || this == "application/xhtml+xml"

    /**
     * A sound track its source states as `audio/mp4` stays audio when the server calls the same
     * container `video/mp4`: Facebook's CDN labels every MP4 that way, sound-only tracks
     * included (P4). Another container, or a merge's video, keeps the server's type.
     */
    private fun soundMimeType(candidate: MediaCandidate, serverMime: String?): String? {
        if (candidate.audioCompanion != null || serverMime == null) return null
        val stated = candidate.mimeType?.normalizedMimeType() ?: return null
        if (!stated.startsWith(AUDIO_PREFIX)) return null
        return stated.takeIf { serverMime == VIDEO_PREFIX + stated.removePrefix(AUDIO_PREFIX) }
    }

    private fun String?.isKnownUnsupportedMediaMime(): Boolean {
        val mime = this ?: return false
        if (!mime.startsWith("video/") && !mime.startsWith("audio/")) return false
        return mime !in SUPPORTED_DIRECT_MIME_TYPES
    }

    private fun String?.toContainer(url: HttpUrl): String? = when (this) {
        "video/mp4", "audio/mp4" -> "MP4"
        "video/webm", "audio/webm" -> "WebM"
        "video/x-matroska" -> "Matroska"
        "audio/mpeg" -> "MP3"
        "audio/aac" -> "AAC"
        "audio/ogg", "video/ogg" -> "Ogg"
        "video/quicktime" -> "QuickTime"
        else -> url.pathSegments.lastOrNull()
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf(String::isNotBlank)
            ?.uppercase(Locale.US)
    }

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maxBytes, BUFFER_SIZE))
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = maxBytes
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    private fun String.parseIsoInstantMillis(): Long? {
        ISO_INSTANT_PATTERNS.forEach { pattern ->
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val position = ParsePosition(0)
            val parsed = parser.parse(this, position)
            if (parsed != null && position.index == length) return parsed.time
        }
        return null
    }

    private enum class RequestMethod(val label: String) {
        HEAD("HEAD"),
        RANGE_GET("range GET"),
        GET("GET"),
    }

    private sealed interface HttpExecution {
        data class Completed(
            val response: Response,
            val finalUrl: HttpUrl,
        ) : HttpExecution

        data class Failed(
            val reason: VariantResolutionFailure,
        ) : HttpExecution
    }

    private data class DirectMetadata(
        val code: Int,
        val mimeType: String? = null,
        val totalLengthBytes: Long? = null,
    )

    private companion object {
        const val ORIGIN = "Origin"
        const val REFERER = "Referer"
        const val HTTPS_PORT = 443
        const val PLAYLIST_TIMEOUT_MS = 5_000L
        const val HTTP_OK = 200
        const val HTTP_PARTIAL_CONTENT = 206
        const val PROBE_TIMEOUT_MS = 8_000L
        val PROBED_VIDEO_MIME_TYPES = setOf("video/mp4", "video/quicktime", "video/x-m4v")
        const val AUDIO_PREFIX = "audio/"
        const val VIDEO_PREFIX = "video/"
        const val BUFFER_SIZE = 8_192
        const val RANGE_FIRST_BYTE = "bytes=0-0"
        const val DASH_MIME_TYPE = "application/dash+xml"
        const val DASH_ACCEPT = "application/dash+xml, application/xml;q=0.9, */*;q=0.1"
        const val HLS_ACCEPT =
            "application/vnd.apple.mpegurl, application/x-mpegurl, */*;q=0.1"
        const val EPOCH_MILLIS_THRESHOLD = 100_000_000_000L
        val SUCCESS_CODES = 200..299
        const val BROWSER_NOT_ASKED = 0
        const val BROWSER_MESSAGE = "browser"
        val BROWSER_STEPS = setOf(
            ResolutionStep.FILE_CHECK,
            ResolutionStep.MANIFEST,
            ResolutionStep.MEDIA_PLAYLIST,
        )
        val REDIRECT_CODES = setOf(300, 301, 302, 303, 307, 308)
        val EXPIRY_QUERY_NAMES = setOf("exp", "expire", "expires", "expiration")
        val ISO_INSTANT_PATTERNS = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
        )
        val CROSS_ORIGIN_HEADER_ALLOWLIST = setOf(
            "accept",
            "accept-encoding",
            "accept-language",
            "user-agent",
        )
        val HLS_MIME_TYPES = setOf(
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "audio/mpegurl",
            "audio/x-mpegurl",
        )
        val SUPPORTED_DIRECT_MIME_TYPES = setOf(
            "audio/aac",
            "audio/mp4",
            "audio/mpeg",
            "audio/ogg",
            "audio/webm",
            "video/mp4",
            "video/ogg",
            "video/quicktime",
            "video/webm",
            "video/x-matroska",
        )
    }
}