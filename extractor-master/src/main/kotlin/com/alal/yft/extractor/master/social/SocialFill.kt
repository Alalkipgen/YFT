package com.alal.yft.extractor.master.social

import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.MediaGroups
import com.alal.yft.core.model.media.MediaKind
import com.alal.yft.extractor.master.MasterFallbackEngine
import com.alal.yft.extractor.master.MasterMediaValidator
import com.alal.yft.extractor.master.MasterPolicy
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.MasterResult
import com.alal.yft.extractor.master.NoPlaybackCaptureProvider
import com.alal.yft.extractor.master.contract.SiteContracts
import com.alal.yft.extractor.master.toolkit.QualityLadder
import com.alal.yft.extractor.master.toolkit.UrlPolicy
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Weak-answer fill (`B+` in Lookup details). Route B
 * runs Master only after main fails; the fill also runs it when main's answer for a page is weak by an
 * objective signal ([weakness]: no HD row, no sound row), and Master then adds only the formats
 * main's rows lack ([added]). Main's rows stay exactly as main gave them, first, as ClientLadder's
 * merge rule (the chain's rows stay, the ladder adds only a format they lack); route A
 * (Master in main's place) is not done.
 *
 * - **Weak** ([weakness]): main's rows name one video (no item of a post with several) and
 *   - [Weak.NO_HD]: every picture row's height is known — its stated picture (the short side's
 *     nearest standard height, as the sheet names it), else the adapter's own quality word (`HD`
 *     720, `SD` 480, `720p`; the sheet's rule) — and the tallest is under 720;
 *   - [Weak.NO_SOUND]: no sound-only row, and every picture row is proven silent (video codecs
 *     stated, every one a video codec, no sound file to merge).
 *
 *   A manifest main did not read into rows (an HLS or DASH row without a height, and no row of
 *   main's that names its codecs) may hold any size and sound: unknown, never weak. So is a row
 *   whose height main states in no way. Unknown is strong: Master does not run.
 * - **Run** ([run]): the lookup's own ladder ([SocialStage], the site's rungs in their order),
 *   inside the lookup's record (main's asks included): its budgets ([SocialBudget]), its stops
 *   (no new request after a 429 in the lookup; a host that answered a bot check or still cools is
 *   not asked) and its V ([SocialRowCheck]), with no capture of the tab ([MasterPolicy.capture]
 *   off) and only rows that may fill the need going to V ([Need.admits]; another row is never
 *   read). Its time is [POLICY]'s.
 * - **Added** ([added]): of Master's rows, only those proven main's video ([TrialCompare]'s MATCH:
 *   the video main's rows name; Instagram's the same item, which the fill reads as the Instagram tab-fetch step's
 *   set does: the rung's answer offered one of main's files, [Need.sharesMainsFile], as the fill never
 *   reads main's own files again; a duration more than 1 s off stays WRONG), none of main's files (the file's
 *   address or path, a row's sound file included), and each filling the need: an HD row (720 or
 *   taller) not proven silent (with [Weak.NO_SOUND] too: one that states its sound), a picture row
 *   that states its sound, a sound-only row; the best per height ([RowPick]'s rate; a whole file
 *   before a merge), the tallest first, at most [MAX_ADDED]. They carry main's video key (and
 *   main's base title when they have none), so the sheet lists them with main's.
 *
 * The app then checks each added row's file once more with the census's one-row check
 * ([SiteRowCheck]) under route B's rules before it adds it. Pure but for [run]: nothing here is
 * logged; lines name counts, heights and words only, never a link or an ID.
 */
object SocialFill {
    /** The sites the fill runs for (the sites promoted after three phone runs): Facebook, Instagram and VK. */
    val SITES: Set<String> = setOf(FacebookLadder.SITE_ID, InstagramLadder.SITE_ID, VkClient.SITE_ID)

    /** An HD row's least height (the sheet's "HD"). */
    const val HD_HEIGHT = 720

    /** The most rows the fill adds to one answer. */
    const val MAX_ADDED = 4

    /**
     * The fill's engine time: the ladder's ask within its own share ([MasterPolicy.contractTimeoutMillis]),
     * the Row check's reads after it, no capture (the wait a weak answer may add to its lookup).
     */
    val POLICY = MasterPolicy(enabled = true, timeoutMillis = 10_000, contractTimeoutMillis = 6_000, capture = false)

    /** An objective weakness of main's answer. */
    enum class Weak(val label: String) { NO_HD("no HD"), NO_SOUND("no sound") }

    /** [site]'s ID of a video key (`facebook:123`), when the fill runs for it, else null. */
    fun siteOf(videoKey: String?): String? = videoKey?.substringBefore(':')?.takeIf { it in SITES }

    /** Main's [mains]' weaknesses; empty when it is strong or not known to be weak. */
    fun weakness(mains: List<MediaCandidate>): Set<Weak> {
        if (mains.isEmpty()) return emptySet()
        val videos = mains.map { it.videoId?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        if (videos.size != 1 || videos.single() == null || mains.any { it.postItem != null }) return emptySet()
        val pictures = mains.filterNot(QualityLadder::isAudio)
        if (pictures.isEmpty()) return emptySet()
        val unread = pictures.filter { manifest(it) && heightOf(it) == null }
        val parsed = pictures.any { !manifest(it) && it.codecs.isNotEmpty() }
        // A manifest main did not read into rows may hold any size and sound: not known to be weak.
        if (unread.isNotEmpty() && !parsed) return emptySet()
        val measured = pictures - unread.toSet()
        if (measured.isEmpty()) return emptySet()
        val heights = measured.map(::heightOf)
        val weak = LinkedHashSet<Weak>()
        if (heights.all { it != null } && heights.maxOf { checkNotNull(it) } < HD_HEIGHT) weak += Weak.NO_HD
        if (mains.none(QualityLadder::isAudio) && measured.all(::silent)) weak += Weak.NO_SOUND
        return weak
    }

    /**
     * One weak answer's need: main's [mains] (as main gave them) and their [weak]nesses. [admits]
     * is the fill's Row check rule; [unread] counts the rows it kept from the Row check (never read).
     */
    class Need(val mains: List<MediaCandidate>, val weak: Set<Weak>) {
        private val files = filesOf(mains)
        private val unreadCount = AtomicInteger()
        private val answer = AtomicReference(AtomicBoolean(false))

        /** Master's rows the fill kept from the Row check. */
        val unread: Int get() = unreadCount.get()

        /**
         * Whether the rung's answer Master's rows came from (the last one staged) offered one of
         * main's files: Instagram's proof that its rows are main's item (the Instagram tab-fetch step's rule, the trial
         * line's "the same item"), as the fill never reads main's own files again.
         */
        val sharesMainsFile: Boolean get() = answer.get().get()

        /** A rung's answer starts: its rows' proof of the same item is its own. */
        internal fun answerStarts(): AtomicBoolean = AtomicBoolean(false).also(answer::set)

        /**
         * Whether [row] goes to V: none of main's files, not a manifest (V never passes one), and
         * possibly filling the need (its height may be unknown before V reads its file).
         */
        fun admits(row: MediaCandidate): Boolean = admits(row, answer.get())

        internal fun admits(row: MediaCandidate, shared: AtomicBoolean): Boolean {
            val own = mainFile(row, files)
            if (own) shared.set(true)
            val admitted = !own && !manifest(row) && when {
                QualityLadder.isAudio(row) -> Weak.NO_SOUND in weak
                silent(row) -> false
                else -> Weak.NO_SOUND in weak || heightOf(row)?.let { it >= HD_HEIGHT } != false
            }
            if (!admitted) unreadCount.incrementAndGet()
            return admitted
        }
    }

    /**
     * The fill's answer: the [rows] to add (none: [why], a few words), of Master's [offered] rows (after
     * the app's own focus and merge checks).
     */
    class Fill(val rows: List<MediaCandidate>, val why: String?, val offered: Int)

    /**
     * The rows of Master's [masters] that fill [weak] for main's [mains], each carrying main's
     * video key; [video]: the video a row's key names in the lookup (as the trial's line reads it).
     */
    fun added(
        mains: List<MediaCandidate>,
        masters: List<MediaCandidate>,
        weak: Set<Weak>,
        sharesMainsFile: Boolean = false,
        video: (String?) -> String?,
    ): Fill {
        if (weak.isEmpty()) return Fill(emptyList(), "main's rows are not weak", masters.size)
        if (masters.isEmpty()) return Fill(emptyList(), "Master found no row", 0)
        val verdicts = TrialCompare.compare(masters, mains, video).verdicts
        val mainVideo = mains.map { video(it.videoId) }.distinct().singleOrNull()
        // Instagram (the Instagram tab-fetch step's rule): its rows are main's item when their answer offered one of
        // main's files (the fill never reads that file again, so no row of its own shares one).
        val sameItem = sharesMainsFile && mainVideo != null && siteOf(mains.first().videoId) == InstagramLadder.SITE_ID
        val proven = masters.filterIndexed { index, row ->
            verdicts[index] == TrialCompare.Verdict.MATCH ||
                sameItem && verdicts[index] == TrialCompare.Verdict.UNKNOWN && video(row.videoId) == mainVideo
        }
        if (proven.isEmpty()) return Fill(emptyList(), "no row of Master's proven main's video", masters.size)
        val files = filesOf(mains)
        val fresh = proven.filterNot { mainFile(it, files) }
        if (fresh.isEmpty()) return Fill(emptyList(), "Master's rows are main's files", masters.size)
        val filling = fresh.filter { fills(it, weak) }
        if (filling.isEmpty()) return Fill(emptyList(), "no row of Master's has what main's lack", masters.size)
        val key = mains.firstNotNullOfOrNull { it.videoId }
        val title = mains.firstNotNullOfOrNull { MediaGroups.baseTitle(it.title) }
        val rows = filling
            .groupBy { if (QualityLadder.isAudio(it)) SOUND_SLOT else checkNotNull(heightOf(it)) }
            .map { (_, same) -> same.sortedWith(BEST).first() }
            .sortedByDescending { if (QualityLadder.isAudio(it)) SOUND_SLOT else checkNotNull(heightOf(it)) }
            .take(MAX_ADDED)
            .map { it.copy(videoId = key ?: it.videoId, title = it.title ?: title) }
        return Fill(rows, null, masters.size)
    }

    /**
     * The fill's run for [request] (main's page, its lookup's context: the record and main's pages) with
     * [need]: the lookup's ladder ([social]) when it claims the lookup, else nothing is asked
     * ([MasterResult.Skipped]).
     */
    suspend fun run(
        request: MasterRequest,
        need: Need,
        validator: MasterMediaValidator,
        contracts: SiteContracts?,
        social: SocialStage,
        policy: MasterPolicy = POLICY,
    ): MasterResult {
        if (!social.claims(request)) return MasterResult.Skipped(request.primaryFailure)
        val engine = MasterFallbackEngine(
            validator,
            NoPlaybackCaptureProvider,
            policy.copy(capture = false),
            modules = emptyList(),
            contracts = contracts,
            social = Admitting(social, need),
        )
        return engine.extract(request)
    }

    /** [stage]'s rungs, each answer's rows going to V only when [need] admits them. */
    private class Admitting(private val stage: SocialStage, private val need: Need) : SocialStage {
        override suspend fun claims(request: MasterRequest): Boolean = stage.claims(request)

        override suspend fun stage(
            request: MasterRequest,
            policy: MasterPolicy,
            details: MutableList<String>,
        ): SocialStage.Staged? = stage.stage(request, policy, details)?.let { staged ->
            val shared = need.answerStarts()
            staged.admitting { row -> need.admits(row, shared) }
        }

        override suspend fun captureOff(request: MasterRequest): String? = stage.captureOff(request)

        override fun toString(): String = "SocialFill.Admitting($stage)"
    }

    /** The fill's Details line: `master: facebook B+ (main's rows: no HD): Master added 1: 720p`. */
    fun line(site: String, weak: Set<Weak>, added: List<MediaCandidate>): String =
        "${prefix(site, weak)}Master added ${added.size}: " + added.joinToString(", ") { row ->
            if (QualityLadder.isAudio(row)) "sound" else "${heightOf(row)}p"
        }

    /** The fill's Details line when it adds none: `…: Master added none (Master found no row)`. */
    fun none(site: String, weak: Set<Weak>, why: String): String = "${prefix(site, weak)}Master added none ($why)"

    /** The start of the fill's lines, `master: <site> B+`. */
    fun label(site: String): String = "master: $site $B_PLUS"

    private fun prefix(site: String, weak: Set<Weak>): String =
        "${label(site)} (main's rows: ${weak.joinToString(" and ") { it.label }}): "

    const val B_PLUS = "B+"

    /**
     * The height [row]'s picture is named after: its stated picture's (the short side's nearest
     * standard height), else the adapter's own quality word in a named video's title (`HD` 720,
     * `SD` 480, `720p`; Facebook's `720p · HD` by its first word that is one), else null.
     */
    fun heightOf(row: MediaCandidate): Int? {
        RowPick.standardHeight(row)?.let { return it }
        if (row.videoId == null) return null
        val word = MediaGroups.titleLabel(row.title)?.split(WORD_SEPARATOR)?.map(String::trim)
            ?.firstOrNull { QUALITY_WORD.matches(it) } ?: return null
        return when (word.uppercase(Locale.US)) {
            "HD" -> HD_HEIGHT
            "SD" -> SD_HEIGHT
            else -> word.lowercase(Locale.US).substringBefore('p').toIntOrNull()?.let { stated ->
                RowPick.standardHeight(row.copy(width = null, height = stated))
            }
        }
    }

    /** Whether [row] fills [weak] (after V: its height and sound as its file states them). */
    private fun fills(row: MediaCandidate, weak: Set<Weak>): Boolean {
        if (manifest(row)) return false
        if (QualityLadder.isAudio(row)) return Weak.NO_SOUND in weak
        val height = heightOf(row) ?: return false
        val sound = statesSound(row)
        val hd = Weak.NO_HD in weak && height >= HD_HEIGHT && !silent(row) && (Weak.NO_SOUND !in weak || sound)
        return hd || Weak.NO_SOUND in weak && sound
    }

    /** A picture proven silent: video codecs stated, each a video codec, and no sound file to merge. */
    private fun silent(row: MediaCandidate): Boolean =
        row.audioCompanion == null && row.codecs.isNotEmpty() &&
            row.codecs.all { QualityLadder.family(it).rank < QualityLadder.Family.AAC.rank }

    /** A picture that states its sound: a sound file to merge, or a sound codec among its own. */
    private fun statesSound(row: MediaCandidate): Boolean =
        row.audioCompanion != null || row.codecs.any { QualityLadder.family(it) in SOUND_FAMILIES }

    private fun manifest(row: MediaCandidate): Boolean = row.kind == MediaKind.HLS || row.kind == MediaKind.DASH

    /** Main's files: each row's file and its sound file, whole and by path. */
    private fun filesOf(rows: List<MediaCandidate>): Set<String> = rows.flatMap { row ->
        listOfNotNull(row.mediaUrl, row.audioCompanion?.mediaUrl)
    }.flatMap { url -> listOfNotNull(UrlPolicy.whole(url), pathOf(url)) }.toSet()

    /** Whether [row]'s own file is one of main's [files] (its address or its path). */
    private fun mainFile(row: MediaCandidate, files: Set<String>): Boolean =
        UrlPolicy.whole(row.mediaUrl) in files || pathOf(row.mediaUrl)?.let { it in files } == true

    private fun pathOf(url: String): String? =
        runCatching { URI(url.trim()).path }.getOrNull()?.takeIf { it.length > 1 }?.let { "path:$it" }

    /** Of one height's rows: a whole file before a merge, then the highest rate, then the first. */
    private val BEST = compareBy<MediaCandidate> { if (it.audioCompanion == null) 0 else 1 }
        .thenByDescending { RowPick.rateOf(it) ?: -1L }

    private val SOUND_FAMILIES = setOf(QualityLadder.Family.AAC, QualityLadder.Family.OPUS)
    private const val SD_HEIGHT = 480
    private const val SOUND_SLOT = -1

    /** The sheet's quality words (`QuickDownloadChoices`): HD, SD, `720p`, `1080p60`. */
    private val QUALITY_WORD = Regex("(?i)hd|sd|\\d{3,4}p\\d{0,3}")

    /** Between the words of one label (`720p · HD`). */
    private const val WORD_SEPARATOR = " · "
}
