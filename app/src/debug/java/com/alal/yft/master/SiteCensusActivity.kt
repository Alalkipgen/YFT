/*
 * Provenance (social plan S0c, copied, not moved; main 34a41890; listed in toolkit-provenance.tsv):
 *   app/.../detection/SiteAdapterModule.kt  WebViewAgent (the agent YFT's tab sends, read once on Main)
 */
package com.alal.yft.master

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.alal.yft.BuildConfig
import com.alal.yft.core.browser.policy.BrowserAddressNormalizer
import com.alal.yft.core.browser.policy.BrowserAddressResult
import com.alal.yft.core.browser.policy.BrowserUserAgent
import com.alal.yft.core.data.network.NetworkConfiguration
import com.alal.yft.detection.Av1MergeSwitch
import com.alal.yft.detection.OkHttpExtractorClient
import com.alal.yft.detection.master.InstagramProbeSwitch
import com.alal.yft.detection.master.MasterRecipeConfig
import com.alal.yft.detection.master.SocialLadder
import com.alal.yft.detection.tiktok.TikTokPageEngine
import com.alal.yft.detection.tiktok.TikTokPageScript
import com.alal.yft.detection.tiktok.TikTokPageSettings
import com.alal.yft.extractor.api.SiteAdapterSelection
import com.alal.yft.extractor.api.SitePageIdentity
import com.alal.yft.extractor.master.lookup.LookupRecorder
import com.alal.yft.extractor.master.lookup.RouteBHttpClient
import com.alal.yft.extractor.master.modules.youtube.ladder.ClientCensus
import com.alal.yft.extractor.master.social.Site
import com.alal.yft.extractor.master.social.SiteCensus
import com.alal.yft.extractor.master.social.SiteWays
import com.alal.yft.extractor.master.social.SiteWeekly
import com.alal.yft.extractor.master.verify.OkHttpMediaValidator
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Request
import okhttp3.Response

/**
 * Owner tool, debug build only (social plan S0c, `MASTER_KEY_SOCIAL_PLAN.md` §7.1): "YFT site
 * census" runs [SiteCensus] on the phone over the owner's links, one site or all: main's own
 * lookup of each link ([SiteCensusLookup]: fresh, the browser's identity, no retry, at most 90 s),
 * V on its best row, Master's R8 way once under route B's rules, the judge; a 429 or a bot check
 * stops the site for the run and for 30 minutes; at most 150 requests and 40 MB per site. Its
 * launcher entry exists only when the build has `yft.masterCapture` and `yft.ownSolver` on
 * (manifest placeholder `yftOwnerTools`). A run starts only on a tap and belongs to this screen
 * (closing the tool ends it). The links stay in this screen's own store on the phone; the report
 * and its JSON export, which never hold a link, a post ID, a name or an address, are shown with
 * copy buttons, and the state (cooldowns, the last run) is kept in
 * `files/master/site-census-last.json`.
 *
 * Social plan 0.4 (c) (§7.4 step 2): per site, every slot's main and V first, then the backup's
 * parts (route B, then the trials); a 429 or a bot check in the backup's parts skips only its
 * remaining parts (and its parts of the next runs for 30 minutes; main still runs). The run gets
 * whether Android's network is a VPN ([onVpn], as the screen reads Wi-Fi): the state keeps each
 * site's baseline per VPN and session (with each slot's link fingerprint, never in the export).
 *
 * Social plan S2d (decisions 19 and 24): in a social-ladder build the screen also holds the own
 * switches of Instagram's tab-fetch rung ([InstagramProbeSwitch], each off by default): "Instagram's
 * tab-fetch rung (signed out)" (part 2, task 13: the browser's Instagram lookups ask the rung only
 * while it is on, after I2 gave no rows) and "Instagram rung proof: set I2's rows aside" (debug:
 * with I2's rows, the rung is asked in their place, so a post main answers shows the rung's own
 * rows). The census itself has no tab: the rung is never asked in it.
 *
 * v1.0.2 item 26: in every debug build the screen also holds "AV1 merges (test, Android 14 or
 * newer)" ([Av1MergeSwitch], off by default): while it is on, the sheet keeps AV1 + AAC rows on
 * Android 14 or newer (with an AV1 decoder), named with "AV1", and the download merges them
 * into one MP4. The release and the previews have no such switch (AV1 merges stay off there).
 *
 * Social plan task 6b: in a social-ladder build each ladder site's backup part is that site's
 * ladder as the browser runs it ([census]'s pack: the rungs, each row checked against main's), and
 * the screen says so ([LADDER_NOTE]); every other build: the screen and the census as before.
 *
 * Social plan task 8 (S3, §7.5): in a social-ladder build only (behind the flag), "Weekly check W
 * (all sites)" runs the census's weekly check W ([SiteWeekly]: three slots per site, the backup's
 * part (the site's ladder) on slot 1 only, each way once, at most 60 s, 2 MB and 24 requests per
 * site, W's own baselines, each CHANGED or BREAKING line with its runbook step) and the intro says
 * so ([WEEKLY_NOTE]); the Run buttons run the full census as before; every other build: no W, the
 * screen as before.
 */
class SiteCensusActivity : ComponentActivity() {
    private var running = false
    private var job: Job? = null
    private var status: TextView? = null
    private var exported = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        fun add(view: View) = column.addView(view, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        add(TextView(this).apply {
            text = if (SocialLadder.enabled) INTRO.replace(LINKS_LINE, LADDER_NOTE + WEEKLY_NOTE + LINKS_LINE) else INTRO
            textSize = 15f
        })
        val status = TextView(this).apply { textSize = 15f }
        this.status = status
        // S4: the signed recipe config in use, a social-ladder build only (asked again on open).
        val recipes = if (SocialLadder.enabled) TextView(this).apply { textSize = 14f } else null
        val all = Button(this).apply { text = "Run all sites" }
        // Task 8: W's button, a social-ladder build only (behind the flag; every other build as before).
        val weeklyButton = if (SocialLadder.enabled) Button(this).apply { text = "Weekly check W (all sites)" } else null
        val perSite = SiteWays.ALL.associateWith { site -> Button(this).apply { text = "Run ${site.name}" } }
        val result = TextView(this).apply {
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            textSize = 12f
        }
        val copy = Button(this).apply {
            text = "Copy report"
            isEnabled = false
        }
        val copyJson = Button(this).apply {
            text = "Copy JSON"
            isEnabled = false
        }
        add(status)
        recipes?.let(::add)
        add(all)
        perSite.values.forEach(::add)
        weeklyButton?.let(::add)
        // S2d (task 13): the rung's own switches under the Run buttons, each off by default (a
        // social-ladder build only); they stay as set until the owner turns them off.
        if (SocialLadder.enabled) {
            add(CheckBox(this).apply {
                text = "Instagram's tab-fetch rung (signed out)"
                isChecked = InstagramProbeSwitch.on(this@SiteCensusActivity)
                setOnCheckedChangeListener { _, checked -> InstagramProbeSwitch.set(this@SiteCensusActivity, checked) }
            })
            add(CheckBox(this).apply {
                text = "Instagram rung proof: set I2's rows aside"
                isChecked = InstagramProbeSwitch.proof(this@SiteCensusActivity)
                setOnCheckedChangeListener { _, checked -> InstagramProbeSwitch.setProof(this@SiteCensusActivity, checked) }
            })
        }
        // v1.0.2 item 26: AV1 merges as a test switch, off by default (a debug build only).
        if (Av1MergeSwitch.built) {
            add(CheckBox(this).apply {
                text = AV1_SWITCH_LABEL
                isChecked = Av1MergeSwitch.on(this@SiteCensusActivity)
                setOnCheckedChangeListener { _, checked -> Av1MergeSwitch.set(this@SiteCensusActivity, checked) }
            })
        }
        listOf(copy, copyJson, result).forEach(::add)

        val preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val fields = SiteWays.ALL.associateWith { site ->
            add(TextView(this).apply {
                text = "\n${site.name} (${site.slots.size} slots)"
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            })
            site.slots.indices.associate { index ->
                val slot = index + 1
                slot to EditText(this).apply {
                    hint = "$slot · ${site.slots[index].label} (link)"
                    isSingleLine = true
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    setText(preferences.getString(key(site, slot), null).orEmpty())
                }.also(::add)
            }
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column)
        })

        val stateFile = File(filesDir, STATE_PATH)
        status.text = SiteCensus.statusOf(readState(stateFile), System.currentTimeMillis())
        val runButtons = listOf(all) + perSite.values + listOfNotNull(weeklyButton)
        if (!(BuildConfig.MASTER_CAPTURE_ENABLED && BuildConfig.OWN_SOLVER_ENABLED)) {
            status.text = "Turned off in this build (needs yft.masterCapture and yft.ownSolver)."
            runButtons.forEach { it.isEnabled = false }
            return
        }
        // S4: the config is asked for on open (one bounded ask, whatever its interval), then shown.
        recipes?.let { line ->
            line.text = MasterRecipeConfig.status()
            lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    MasterRecipeConfig.refreshNow(OkHttpExtractorClient(NetworkConfiguration.createClient()))
                }
                line.text = MasterRecipeConfig.status()
            }
        }

        fun start(sites: List<Site>, mode: ClientCensus.Mode = ClientCensus.Mode.FULL) {
            if (running) return
            val links = LinkedHashMap<String, Map<Int, String>>()
            for (site in sites) {
                val chosen = LinkedHashMap<Int, String>()
                for ((slot, field) in fields.getValue(site)) {
                    val text = field.text.toString().trim()
                    if (text.isEmpty()) continue
                    val url = (BrowserAddressNormalizer.normalize(text) as? BrowserAddressResult.Valid)?.url
                        ?.takeIf { it.startsWith("https://") }
                    if (url == null) {
                        status.text = "${site.name} $slot: not an HTTPS link."
                        return
                    }
                    chosen[slot] = url
                }
                links[site.id] = chosen
            }
            if (links.values.all(Map<Int, String>::isEmpty)) {
                status.text = "No links: paste a link into a slot first."
                return
            }
            preferences.edit().apply {
                fields.forEach { (site, slots) ->
                    slots.forEach { (slot, field) -> putString(key(site, slot), field.text.toString().trim()) }
                }
            }.apply()
            running = true
            runButtons.forEach { it.isEnabled = false }
            copy.isEnabled = false
            copyJson.isEnabled = false
            result.text = ""
            exported = ""
            status.text = "Starting…"
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val census = census()
            job = lifecycleScope.launch {
                try {
                    val network = networkNote()
                    val vpn = onVpn()
                    // S4: the recipe config in use is one of the report's notes (a social-ladder build only).
                    val notes = listOf(network) + listOfNotNull(recipes?.let { MasterRecipeConfig.status() })
                    val outcome = withContext(Dispatchers.Default) {
                        census.run(sites, links, readState(stateFile), notes, vpn = vpn, mode = mode) { step ->
                            runOnUiThread { status.text = step }
                        }
                    }
                    writeState(stateFile, outcome.stateJson)
                    status.text = outcome.firstLine + "\nTap Copy report and send it (Copy JSON for the export)."
                    result.text = outcome.report
                    exported = outcome.json
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Exception) {
                    status.text = "Stopped (${failure.javaClass.simpleName}). Nothing was saved."
                    result.text = "{\"tool\":\"${SiteCensus.TOOL_LABEL}\",\"error\":\"${failure.javaClass.simpleName}\"}"
                } finally {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    running = false
                    recipes?.text = MasterRecipeConfig.status()
                    runButtons.forEach { it.isEnabled = true }
                    copy.isEnabled = result.text.isNotEmpty()
                    copyJson.isEnabled = exported.isNotEmpty()
                }
            }
        }
        all.setOnClickListener { start(SiteWays.ALL) }
        weeklyButton?.setOnClickListener { start(SiteWays.ALL, ClientCensus.Mode.WEEKLY) }
        perSite.forEach { (site, button) -> button.setOnClickListener { start(listOf(site)) } }
        copy.setOnClickListener { copyText(result.text) }
        copyJson.setOnClickListener { copyText(exported) }
    }

    /** A run belongs to this screen: leaving it ends the run; turning the phone does not. */
    override fun onStop() {
        super.onStop()
        if (running && !isChangingConfigurations) {
            job?.cancel()
            status?.text = "Stopped: the tool was left before the end; nothing was saved. Start again."
        }
    }

    private fun copyText(text: CharSequence) {
        getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(SiteCensus.TOOL_LABEL, text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    /**
     * The census as the app builds main's lookups, on its own clients: the app's
     * [NetworkConfiguration] client under [OkHttpExtractorClient] (no cookie jar, no redirect
     * followed blindly, the same timeouts), but no retry (each ask once; the census records a
     * failure), with its wire bytes counted for the budget. Main's asks go through
     * [LookupRecorder] into each row's record: TikTok's own client sends a short link's redirect
     * cookies (as the app's), the others share one. TikTok's hidden page is the app's own
     * ([TikTokPageEngine], the owner's settings). Master's contracts and V use the same client
     * class without the recorder (route B records Master's asks itself); Master's media checks
     * are the app's ([OkHttpMediaValidator]) on the counted client. Task 6b: in a social-ladder
     * build main's clients also keep main's pages ([SocialLadder.pages], outermost, as the app's)
     * and the census runs each ladder site's Master part as the app's ladder
     * ([SiteCensus.Pack]: [SocialLadder.stage] on Master's client, S6's row check around the
     * media checks, main's adapters, the app's cooldown); every other build: no pack, as before.
     */
    private fun census(): SiteCensus {
        val bytes = AtomicLong()
        val meter = object : EventListener() {
            override fun requestHeadersEnd(call: Call, request: Request) {
                bytes.addAndGet(request.headers.byteCount())
            }

            override fun requestBodyEnd(call: Call, byteCount: Long) {
                bytes.addAndGet(byteCount)
            }

            override fun responseHeadersEnd(call: Call, response: Response) {
                bytes.addAndGet(response.headers.byteCount())
            }

            override fun responseBodyEnd(call: Call, byteCount: Long) {
                bytes.addAndGet(byteCount)
            }
        }
        val client = NetworkConfiguration.createClient().newBuilder().eventListener(meter).build()
        val http = OkHttpExtractorClient(client, NO_RETRY)
        val main = SocialLadder.pages(LookupRecorder(http))
        val tikTok = SocialLadder.pages(LookupRecorder(OkHttpExtractorClient(client, NO_RETRY.copy(sendResponseCookies = true))))
        val agent = WebViewAgent(applicationContext)
        val registry = SiteCensusLookup.registry(main, tikTok, agent::get)
        val identify: (String) -> SitePageIdentity? = { link ->
            (registry.select(link) as? SiteAdapterSelection.Matched)?.identity
        }
        val hiddenPages = TikTokPageEngine(
            context = applicationContext,
            settings = TikTokPageSettings.OWNER,
            isMedia = { url -> registry.enabled(TikTokPageScript.SITE_ID)?.isPlayerMediaRequest(url) == true },
        )
        return SiteCensus(
            main = SiteCensusLookup(
                registry = registry,
                hiddenPages = hiddenPages,
                userAgent = agent::get,
                cookies = ::browserCookie,
            ),
            masterHttp = http,
            validator = OkHttpMediaValidator(client),
            meter = SiteCensus.Meter { bytes.get() },
            pack = SocialLadder.stage(http, identify)?.let { stage ->
                SiteCensus.Pack(
                    stage = stage,
                    // Task 9: the app's S6 wiring: one check and reader, each read on its lookup's site's hosts.
                    validator = { checks -> SocialLadder.validator(checks, client) },
                    identify = identify,
                    sites = SocialLadder.SITES,
                    cooldown = SocialLadder.cooldown,
                    // S4: the engine's recipes as the app's (its signed config, the ladders' own source).
                    contracts = MasterRecipeConfig.contracts(RouteBHttpClient(http)),
                )
            },
        )
    }

    private fun browserCookie(link: String): String? =
        runCatching { CookieManager.getInstance().getCookie(link) }.getOrNull()

    private fun readState(file: File): String? = runCatching { file.takeIf(File::isFile)?.readText() }.getOrNull()

    /** Written whole, then moved over the last state, so a stopped write never leaves half a file. */
    private fun writeState(file: File, text: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val next = File(file.parentFile, file.name + ".tmp")
            next.writeText(text)
            if (!next.renameTo(file)) {
                file.delete()
                next.renameTo(file)
            }
        }
    }

    private fun onWifi(): Boolean = runCatching {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(false)

    private fun networkNote(): String = if (onWifi()) "network: Wi-Fi" else "network: mobile data"

    /** 0.4 (c): whether Android's active network is a VPN (each site's baseline is kept per VPN). */
    private fun onVpn(): Boolean = runCatching {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)

    private fun key(site: Site, slot: Int) = "${site.id}-$slot"

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PREFERENCES = "site-census"
        const val STATE_PATH = "master/site-census-last.json"

        /** Each ask once, at most 60 s: the census records a failure instead of repeating it. */
        val NO_RETRY = OkHttpExtractorClient.Policy(callTimeoutSeconds = 60, retryDelaysMillis = emptyList())

        const val LINKS_LINE = "Links per site (empty slots are skipped):"

        /** v1.0.2 item 26: the AV1 merge switch's box (every debug build; off by default). */
        const val AV1_SWITCH_LABEL = "AV1 merges (test, Android 14 or newer)"

        /** Task 8: the weekly check W's lines, a social-ladder build only (after [LADDER_NOTE]). */
        val WEEKLY_NOTE: String = "Weekly check W: slots " +
            SiteWeekly.SLOTS.entries.joinToString(" · ") { (id, slots) -> "${SiteWays.site(id)?.name ?: id} ${slots.joinToString(", ")}" } +
            ", the backup's part on slot ${SiteWeekly.MASTER_SLOT} only, each way once, at most ${SiteWeekly.MAX_MILLIS / 1000} s, " +
            "${SiteWeekly.MAX_BYTES / (1024 * 1024)} MB and ${SiteWeekly.MAX_REQUESTS} requests per site; compared with W's own " +
            "last run on the same VPN state and session (never the full census's), each change with its runbook step. A " +
            "stopped or cooling site is not run (never waited out).\n\n"

        /** Task 6b: the intro's added lines in a social-ladder build only (before [LINKS_LINE]). */
        const val LADDER_NOTE = "This build runs the social ladder: for Facebook, TikTok, X, Instagram and VK the backup's " +
            "part is that site's ladder as the browser runs it (no tab here, so no capture), with each rung's state " +
            "and each row checked against YFT's own (wrong rows and unknown rows apart). A ladder with no wrong row " +
            "is not parity and not release readiness.\n\n"

        const val INTRO = "YFT site census (owner tool, debug build)\n\n" +
            "For each link: YFT's own lookup as Home runs it (fresh, with the agent and cookies of " +
            "YFT's browser, each request once, at most 90 s), a one-byte check of its best quality, " +
            "and the backup's way once (after a failure the backup would take, or as a trial).\n" +
            "A sign-in-only slot downloads only when you are signed in to the site in YFT's browser.\n" +
            "Per site, every link's own lookup and check come first, then the backup's ways (after failures, " +
            "then the trials).\n" +
            "An HTTP 429 or a bot check in YFT's own lookups stops that site for the run and no run of it starts " +
            "for 30 min; in the backup's ways it stops only the backup's ways (for 30 min; YFT's own lookups still run). " +
            "At most 150 requests and 40 MB per site.\n" +
            "Each site is compared with its own last run of the same kind (VPN on or off, as Android reports it; " +
            "signed in or not).\n" +
            "Nothing runs unless you tap, and only while this screen stays open (leaving it stops the run). " +
            "The links stay on this phone; the report and the JSON hold no link, post ID, name or address.\n\n" +
            LINKS_LINE
    }
}

/** The WebView's own user agent (the tab's, without the WebView marks), read once on Main. */
private class WebViewAgent(private val context: Context) {
    @Volatile
    private var cached: String? = null

    suspend fun get(): String? = cached ?: withContext(Dispatchers.Main) {
        runCatching { BrowserUserAgent.from(WebSettings.getDefaultUserAgent(context)) }
            .getOrNull()
            ?.also { cached = it }
    }
}
