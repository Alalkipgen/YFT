package com.alal.yft.detection.reads

import com.alal.yft.core.model.media.BrowserReadAnswer
import com.alal.yft.core.model.media.BrowserReadRequest
import com.alal.yft.detection.JsonText
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import java.net.URI
import java.util.Locale

/**
 * P45: the script [WebViewBrowserReads] runs in its blank page, and how its answer is read.
 * Pure, so both are unit-tested.
 *
 * The page's own `fetch` asks [BrowserReadRequest.url] the way a page's player asks a file of
 * another origin: CORS, the page's cookies only for its own origin, nothing from the cache. When
 * no text is wanted, the answer's body is dropped as soon as its status and headers arrived.
 * The answer is kept in `window.__yftReads[key]` as one small object the app reads back.
 */
internal object BrowserReadScript {
    const val STORE = "__yftReads"

    /** The script that starts the read of [request] under [key]. */
    fun start(key: Int, request: BrowserReadRequest, timeoutMillis: Long): String {
        val url = StringBuilder().also { JsonText.appendString(it, request.url) }
        return """
            (function () {
              var store = window.$STORE = window.$STORE || {};
              var key = "$key";
              var wantsText = ${request.wantsText};
              var max = ${request.maxBytes};
              var control = new AbortController();
              var timer = setTimeout(function () { control.abort(); }, $timeoutMillis);
              function done(response, text, error) {
                clearTimeout(timer);
                store[key] = {
                  status: response ? response.status : 0,
                  url: response ? (response.url || "") : "",
                  type: response ? (response.headers.get("content-type") || "") : "",
                  length: response ? (response.headers.get("content-length") || "") : "",
                  text: text,
                  error: error || ""
                };
              }
              fetch($url, {
                method: "GET", mode: "cors", credentials: "same-origin",
                cache: "no-store", redirect: "follow", signal: control.signal
              }).then(function (response) {
                var ok = response.status >= 200 && response.status < 300;
                if (!wantsText || !ok || !response.body) {
                  done(response, null, "");
                  try { control.abort(); } catch (ignored) {}
                  return;
                }
                var reader = response.body.getReader();
                var decoder = new TextDecoder();
                var text = "";
                var read = 0;
                function pump() {
                  return reader.read().then(function (part) {
                    if (part.done) { done(response, text + decoder.decode(), ""); return; }
                    read += part.value.length;
                    if (read > max) {
                      done(response, null, "TooLarge");
                      try { control.abort(); } catch (ignored) {}
                      return;
                    }
                    text += decoder.decode(part.value, { stream: true });
                    return pump();
                  });
                }
                return pump();
              }).catch(function (error) {
                if (!store[key]) done(null, null, (error && error.name) || "Error");
              });
              return true;
            })();
        """.trimIndent()
    }

    /** The script that returns the answer under [key], or null while there is none. */
    fun poll(key: Int): String = "(window.$STORE && window.$STORE[\"$key\"]) || null"

    /** The script that forgets the answer under [key]. */
    fun forget(key: Int): String = "if (window.$STORE) { delete window.$STORE[\"$key\"]; }"

    /**
     * The answer in [json] (what `evaluateJavascript` returned for [poll]); null while there is
     * none. Its text is dropped when it is longer than [maxChars].
     */
    fun answer(json: String?, maxChars: Int): BrowserReadAnswer? {
        if (json.isNullOrBlank() || json == "null") return null
        val value = BoundedJsonParser.parse(json, maxNodes = MAX_NODES) as? JsonValue.Object
            ?: return null
        val status = value["status"].asLongOrNull?.toInt()?.takeIf { it in 0..MAX_STATUS }
            ?: return null
        val text = (value["text"] as? JsonValue.Text)?.value?.takeIf { it.length <= maxChars }
        return BrowserReadAnswer(
            status = status,
            finalUrl = value["url"].asStringOrNull?.takeIf(::isHttps),
            contentType = value["type"].asStringOrNull,
            contentLength = value["length"].asLongOrNull?.takeIf { it >= 0 },
            text = text,
            error = value["error"].asStringOrNull?.takeIf { it.length <= MAX_ERROR_CHARS },
        )
    }

    /** `https://host[:port]` of [pageUrl], the origin of the blank page; null if not HTTPS. */
    fun originOf(pageUrl: String): String? {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase(Locale.US)?.takeIf(String::isNotBlank) ?: return null
        val port = uri.port.takeIf { it > 0 && it != HTTPS_PORT }?.let { ":$it" }.orEmpty()
        return "https://$host$port"
    }

    fun isHttps(url: String): Boolean = url.startsWith("https://", ignoreCase = true)

    private const val MAX_NODES = 64
    private const val MAX_STATUS = 999
    private const val MAX_ERROR_CHARS = 64
    private const val HTTPS_PORT = 443
}
