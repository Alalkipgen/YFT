package com.alal.yft.detection.reads

import com.alal.yft.detection.JsonText
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import com.alal.yft.extractor.master.social.InstagramProbeQuery
import com.alal.yft.extractor.master.social.InstagramTabProbe
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Social plan S2d (`MASTER_KEY_SOCIAL_PLAN.md` decisions 19 and 24, debug only, off by
 * default): Instagram's tab-fetch rung (part 1's probe) as the browser screen runs it in its
 * visible tab when Instagram's ladder asks ([InstagramTabProbe]), and how its answer is read. Pure
 * (the tab's `evaluateJavascript` and its cookies are passed in), so both are unit-tested.
 *
 * Only on the post's own page on Instagram's web host ([onPost]) and only signed out (task 13:
 * the tab's own cookies for that host, as the browser's cookie store holds them, its session
 * cookie included, name no `sessionid`: [signedIn]): the page's own `fetch` of
 * yt-dlp's signed-out post query ([InstagramProbeQuery]), POST, same origin with the page's own
 * cookies (the tab's TLS, agent, cookies and origin: S3 forbids imitating them); its form in
 * yt-dlp's order with the page's LSD value (`<script id="__eqmc">`'s `l`, else
 * `["LSD",[],{"token":…}]`); the query's headers, `X-FB-LSD` and, when the tab has its
 * `csrftoken` cookie, `X-CSRFToken`. At most [InstagramProbeQuery.MAX_ANSWER_CHARS] bytes of the
 * answer are kept (over them: its size only), and the fetch is cut off after
 * [FETCH_TIMEOUT_MILLIS]. The answer is kept in `window.__yftProbe[key]` as one small object the
 * app reads back, then forgotten. Whether the tab shows a wall (a sign-in form, a login or
 * checkpoint address) is read when the answer arrives. Nothing is logged; the answer goes to
 * Instagram's ladder only, which reads its rows (the rung, decision 24) and never one from a page
 * with a wall.
 */
internal object InstagramProbeScript {
    const val STORE = "__yftProbe"

    /** Instagram's web host, the query's origin: the tab must be on it. */
    const val WEB_HOST = "www.instagram.com"

    /** Task 13: the address whose cookies (the tab's, for [WEB_HOST]) tell whether the tab is signed in. */
    const val COOKIE_PAGE = "https://$WEB_HOST/"

    /** The script cuts its fetch off after this long (within the ladder's 5 s). */
    const val FETCH_TIMEOUT_MILLIS = 4_000L

    /** How long [run] waits for the script's answer after it started. */
    const val WAIT_MILLIS = 4_500L

    /** How often [run] asks the tab for the answer. */
    const val POLL_MILLIS = 100L

    /** How long one `evaluateJavascript` may take. */
    const val STEP_MILLIS = 1_000L

    private val keys = AtomicInteger()

    /**
     * Whether [tabUrl] is the post [code]'s own page on Instagram's web host (`/p/`, `/reel/`,
     * `/reels/` or `/tv/` and the code, a profile's prefix allowed).
     */
    fun onPost(tabUrl: String?, code: String): Boolean {
        if (tabUrl == null || code.isBlank()) return false
        val uri = runCatching { URI(tabUrl) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.host?.lowercase(Locale.US) != WEB_HOST || uri.port !in setOf(-1, HTTPS_PORT)) return false
        val segments = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
        val at = segments.indexOf(code)
        return at >= 1 && segments[at - 1] in POST_SEGMENTS
    }

    /** The script that starts the probe's fetch for [mediaId] under [key]. */
    fun start(key: Int, mediaId: String, timeoutMillis: Long = FETCH_TIMEOUT_MILLIS): String {
        val fields = StringBuilder("[")
        InstagramProbeQuery.form(mediaId, InstagramProbeQuery.LSD_PLACEHOLDER).forEachIndexed { index, (name, value) ->
            if (index > 0) fields.append(", ")
            fields.append('[')
            JsonText.appendString(fields, name)
            fields.append(", ")
            JsonText.appendString(fields, value)
            fields.append(']')
        }
        fields.append(']')
        val headers = StringBuilder("{")
        InstagramProbeQuery.HEADERS.entries.forEachIndexed { index, (name, value) ->
            if (index > 0) headers.append(", ")
            JsonText.appendString(headers, name)
            headers.append(": ")
            JsonText.appendString(headers, value)
        }
        headers.append('}')
        val endpoint = StringBuilder().also { JsonText.appendString(it, InstagramProbeQuery.ENDPOINT) }
        val placeholder = StringBuilder().also { JsonText.appendString(it, InstagramProbeQuery.LSD_PLACEHOLDER) }
        return """
            (function () {
              var store = window.$STORE = window.$STORE || {};
              var key = "$key";
              var max = ${InstagramProbeQuery.MAX_ANSWER_CHARS};
              function wall() {
                var path = location.pathname || "";
                if (path.indexOf("/accounts/login") === 0 || path.indexOf("/challenge") === 0) return true;
                return !!document.querySelector("input[type=password], input[name=password]");
              }
              function lsdOf() {
                try {
                  var eqmc = document.getElementById("__eqmc");
                  var data = eqmc ? JSON.parse(eqmc.textContent || "null") : null;
                  if (data && typeof data.l === "string" && data.l) return data.l;
                } catch (ignored) {}
                var html = document.documentElement ? document.documentElement.innerHTML : "";
                var found = /\["LSD",\[\],\{"token":"([^"]+)"/.exec(html);
                return found ? found[1] : null;
              }
              function cookie(name) {
                var parts = (document.cookie || "").split("; ");
                for (var i = 0; i < parts.length; i++) {
                  var at = parts[i].indexOf("=");
                  if (at > 0 && parts[i].substring(0, at) === name) {
                    try { return decodeURIComponent(parts[i].substring(at + 1)); } catch (ignored) { return null; }
                  }
                }
                return null;
              }
              var lsd = lsdOf();
              if (!lsd) {
                store[key] = { asked: false, why: "$NO_LSD_MARK" };
                return true;
              }
              var form = new URLSearchParams();
              $fields.forEach(function (field) {
                form.append(field[0], field[1] === $placeholder ? lsd : field[1]);
              });
              var headers = $headers;
              headers["X-FB-LSD"] = lsd;
              var csrf = cookie("csrftoken");
              if (csrf) headers["X-CSRFToken"] = csrf;
              var control = new AbortController();
              var timer = setTimeout(function () { control.abort(); }, $timeoutMillis);
              var answer = null;
              var read = 0;
              function done(text) {
                clearTimeout(timer);
                if (store[key]) return;
                store[key] = {
                  asked: true,
                  status: answer ? answer.status : 0,
                  bytes: read,
                  text: text,
                  type: answer ? (answer.headers.get("content-type") || "") : "",
                  url: answer ? (answer.url || "") : "",
                  wall: wall()
                };
              }
              fetch($endpoint, {
                method: "POST", mode: "same-origin", credentials: "same-origin",
                cache: "no-store", redirect: "follow", headers: headers,
                body: form.toString(), signal: control.signal
              }).then(function (response) {
                answer = response;
                if (!response.body) { done(null); return; }
                var reader = response.body.getReader();
                var decoder = new TextDecoder();
                var text = "";
                function pump() {
                  return reader.read().then(function (part) {
                    if (part.done) { done(text + decoder.decode()); return; }
                    read += part.value.length;
                    if (read > max) {
                      done(null);
                      try { control.abort(); } catch (ignored) {}
                      return;
                    }
                    text += decoder.decode(part.value, { stream: true });
                    return pump();
                  });
                }
                return pump();
              }).catch(function () { done(null); });
              return true;
            })();
        """.trimIndent()
    }

    /** The script that returns the answer under [key], or null while there is none. */
    fun poll(key: Int): String = "(window.$STORE && window.$STORE[\"$key\"]) || null"

    /** The script that forgets the answer under [key]. */
    fun forget(key: Int): String = "if (window.$STORE) { delete window.$STORE[\"$key\"]; }"

    /**
     * The probe's reply in [json] (what `evaluateJavascript` returned for [poll]); null while there
     * is none. Its text is dropped over [InstagramProbeQuery.MAX_ANSWER_CHARS]; an address that is
     * not HTTPS is dropped.
     */
    fun reply(json: String?): InstagramTabProbe.Reply? {
        if (json.isNullOrBlank() || json == "null") return null
        val value = BoundedJsonParser.parse(json, maxNodes = MAX_NODES) as? JsonValue.Object ?: return null
        val asked = value["asked"].asBooleanOrNull ?: return null
        if (!asked) {
            val why = if (value["why"].asStringOrNull == NO_LSD_MARK) InstagramTabProbe.NO_LSD else InstagramTabProbe.NO_ANSWER
            return InstagramTabProbe.Reply.NotAsked(why)
        }
        return InstagramTabProbe.Reply.Answered(
            status = value["status"].asLongOrNull?.toInt()?.takeIf { it in 0..MAX_STATUS } ?: 0,
            bytes = value["bytes"].asLongOrNull?.coerceAtLeast(0) ?: 0,
            body = (value["text"] as? JsonValue.Text)?.value?.takeIf { it.length <= InstagramProbeQuery.MAX_ANSWER_CHARS },
            contentType = value["type"].asStringOrNull?.takeIf(String::isNotEmpty),
            finalUrl = value["url"].asStringOrNull?.takeIf(BrowserReadScript::isHttps),
            wall = value["wall"].asBooleanOrNull == true,
        )
    }

    /**
     * The probe for [request] in the tab at [tabUrl], by [evaluate] (the tab's own
     * `evaluateJavascript`, on the main thread): not asked off the post's page
     * ([InstagramTabProbe.NOT_ON_POST]), signed in (task 13: [tabCookie], the tab's cookies for
     * [COOKIE_PAGE], name a session: [InstagramTabProbe.SIGNED_IN]), without the page's LSD value
     * ([InstagramTabProbe.NO_LSD]) or when the script did not run ([InstagramTabProbe.NO_ANSWER]:
     * nothing went out); else the tab's answer, or, without one in [waitMillis], a fetch with no
     * HTTP answer (it may have gone out). The answer is forgotten in the tab either way.
     */
    suspend fun run(
        request: InstagramTabProbe.Request,
        tabUrl: String?,
        evaluate: (script: String, reply: (String?) -> Unit) -> Unit,
        waitMillis: Long = WAIT_MILLIS,
        pollMillis: Long = POLL_MILLIS,
        tabCookie: () -> String? = { null },
    ): InstagramTabProbe.Reply {
        if (!onPost(tabUrl, request.code)) return InstagramTabProbe.Reply.NotAsked(InstagramTabProbe.NOT_ON_POST)
        if (signedIn(tabCookie())) return InstagramTabProbe.Reply.NotAsked(InstagramTabProbe.SIGNED_IN)
        val key = keys.incrementAndGet()
        if (ask(evaluate, start(key, request.mediaId)) != "true") {
            return InstagramTabProbe.Reply.NotAsked(InstagramTabProbe.NO_ANSWER)
        }
        return try {
            withTimeoutOrNull(waitMillis) { answerOf(key, evaluate, pollMillis) }
                ?: InstagramTabProbe.Reply.Answered(0, 0, null, null, null, wall = false)
        } finally {
            evaluate(forget(key)) { }
        }
    }

    private suspend fun answerOf(
        key: Int,
        evaluate: (String, (String?) -> Unit) -> Unit,
        pollMillis: Long,
    ): InstagramTabProbe.Reply {
        while (true) {
            delay(pollMillis)
            reply(ask(evaluate, poll(key)))?.let { return it }
        }
    }

    /** [script]'s result in the tab, or null when the tab gives none within [STEP_MILLIS]. */
    private suspend fun ask(evaluate: (String, (String?) -> Unit) -> Unit, script: String): String? =
        withTimeoutOrNull(STEP_MILLIS) {
            val result = CompletableDeferred<String?>()
            evaluate(script) { result.complete(it) }
            result.await()
        }

    /** Task 13: whether the tab's [cookie] (its cookie header for [COOKIE_PAGE]) names a session. */
    fun signedIn(cookie: String?): Boolean =
        cookie?.split(';')?.map(String::trim)?.any { part ->
            part.startsWith("$SESSION_COOKIE=") && part.substringAfter('=').let { it.isNotBlank() && it.none(Char::isWhitespace) }
        } == true

    private const val SESSION_COOKIE = "sessionid"
    private const val NO_LSD_MARK = "no-lsd"
    private const val MAX_NODES = 64
    private const val MAX_STATUS = 999
    private const val HTTPS_PORT = 443
    private val POST_SEGMENTS = setOf("p", "reel", "reels", "tv")
}
