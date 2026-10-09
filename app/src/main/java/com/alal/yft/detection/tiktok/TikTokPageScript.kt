package com.alal.yft.detection.tiktok

import android.content.Context
import com.alal.yft.detection.JsonText
import com.alal.yft.detection.TabData
import com.alal.yft.extractor.api.SitePageData
import com.alal.yft.extractor.api.SitePageDataSource
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asBooleanOrNull
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.asStringOrNull
import com.alal.yft.extractor.api.json.get
import java.net.URI
import java.util.Locale

/**
 * P40: the page script asset ([ASSET]) that reads TikTok's own data for one post from a page
 * TikTok shows (the user's tab or the hidden page), and what its answers mean.
 *
 * The script is the same text everywhere; [store] installs its copy of TikTok's own API
 * answers at document start, [item] asks it for one post. Its answers hold signed media
 * addresses, so nothing here logs them: Details name sources and counts only.
 */
object TikTokPageScript {
    const val ASSET = "tiktok/page-data.js"
    const val SITE_ID = "tiktok"

    /** The origins whose pages get the store script (P40 step 3). */
    val ORIGINS: Set<String> = setOf("https://www.tiktok.com", "https://m.tiktok.com")

    /** The page whose cookies a TikTok row carries. */
    const val COOKIE_PAGE = "https://www.tiktok.com/"

    private val POST_ID = Regex("^[0-9]{5,25}$")

    /** The store script: wraps TikTok's own fetch and XMLHttpRequest answers on this page. */
    fun store(source: String): String = "($source)('store', null);"

    /** The item script for [postId]; a missing or malformed id asks for the address's post. */
    fun item(source: String, postId: String?): String {
        val id = postId?.takeIf(POST_ID::matches)
        val argument = if (id == null) {
            "null"
        } else {
            StringBuilder().also { JsonText.appendString(it, id) }.toString()
        }
        return "($source)('item', $argument);"
    }

    /** Whether [url]'s origin is one of [origins] (the store script's pages). */
    fun isScriptOrigin(url: String?, origins: Set<String> = ORIGINS): Boolean {
        val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return false
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        val port = if (uri.port == -1) "" else ":${uri.port}"
        return "$scheme://$host$port" in origins
    }

    /** What the item script answered. */
    sealed interface Answer {
        /** The post's compact data [json] for [id], from the page's script or an API answer. */
        class Item(
            val json: String,
            val id: String,
            val fromApi: Boolean,
            val answers: Int,
        ) : Answer {
            override fun toString(): String =
                "Item(fromApi=$fromApi, answers=$answers, chars=${json.length})"
        }

        /** No data for the post; what the page showed instead. */
        data class None(
            val status: Long? = null,
            val check: Boolean = false,
            val answers: Int? = null,
            val noId: Boolean = false,
            val tooLarge: Boolean = false,
        ) : Answer

        /** No answer, or one this app does not understand. */
        data object Unreadable : Answer
    }

    /**
     * The item script's [javascriptResult] as `evaluateJavascript` hands it over: a JSON string
     * literal holding the script's JSON text, or `null`.
     */
    fun parse(javascriptResult: String?): Answer {
        val outer = javascriptResult?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            ?: return Answer.Unreadable
        if (outer.length > MAX_RESULT_CHARS) return Answer.Unreadable
        val text = when (val decoded = BoundedJsonParser.parse(outer)) {
            is JsonValue.Text -> decoded.value
            is JsonValue.Object -> outer
            else -> return Answer.Unreadable
        }
        val answer = BoundedJsonParser.parse(text, maxDepth = 8, maxNodes = 64)
            as? JsonValue.Object ?: return Answer.Unreadable
        if (answer["v"].asLongOrNull != VERSION) return Answer.Unreadable
        val answers = answer["answers"].asLongOrNull?.toInt()?.coerceAtLeast(0)
        if (answer["none"].asBooleanOrNull == true) {
            return Answer.None(
                status = answer["status"].asLongOrNull,
                check = answer["check"].asBooleanOrNull == true,
                answers = answers,
                noId = answer["noId"].asBooleanOrNull == true,
                tooLarge = answer["tooLarge"].asBooleanOrNull == true,
            )
        }
        val id = answer["id"].asStringOrNull?.takeIf(POST_ID::matches)
            ?: return Answer.Unreadable
        val json = (answer["item"] as? JsonValue.Text)?.value?.takeIf(String::isNotBlank)
            ?: return Answer.Unreadable
        val from = answer["from"].asStringOrNull
        if (from != FROM_SCRIPT && from != FROM_API) return Answer.Unreadable
        return Answer.Item(json, id, fromApi = from == FROM_API, answers = answers ?: 0)
    }

    /**
     * P40 step 2: the tab's [javascriptResult] for [postId] as the lookup's tab step, with the
     * tab's TikTok [cookie]. [answered] false: the tab did not answer in time.
     */
    fun tabData(
        javascriptResult: String?,
        postId: String,
        cookie: String?,
        answered: Boolean = true,
    ): TabData {
        if (!answered) return TabData(null, listOf("tab data: no answer within 1 s"))
        return when (val answer = parse(javascriptResult)) {
            is Answer.Item -> {
                val source = if (answer.fromApi) {
                    SitePageDataSource.TAB_API_ANSWER
                } else {
                    SitePageDataSource.TAB_SCRIPT
                }
                val data = SitePageData.of(answer.json, source)?.takeIf { answer.id == postId }
                val line = when {
                    answer.id != postId -> "tab data: another post's data"
                    data == null -> "tab data: the post's data is larger than 64 KB"
                    else -> "tab data: ${source.label} · found"
                }
                TabData(
                    pageData = data,
                    details = listOf(line + answersNote(answer.answers)),
                    cookie = cookie.takeIf { data != null },
                )
            }

            is Answer.None -> TabData(null, listOf("tab data: " + noneNote(answer)))
            Answer.Unreadable -> TabData(null, listOf("tab data: no readable answer"))
        }
    }

    /** What a [Answer.None] says, for Details. */
    fun noneNote(answer: Answer.None): String = buildString {
        append(
            when {
                answer.noId -> "no post id on the page"
                answer.tooLarge -> "the post's data is larger than 64 KB"
                else -> "no data for this post"
            },
        )
        answer.status?.let { append(" · TikTok status $it") }
        if (answer.check) append(" · a check is shown")
        append(answersNote(answer.answers))
    }

    private fun answersNote(answers: Int?): String =
        answers?.let { " · API answers kept: $it" }.orEmpty()

    /** Reads the asset text once; null when it is missing from this build. */
    class Source(private val read: () -> String) {
        constructor(context: Context) : this({
            context.applicationContext.assets.open(ASSET).use { it.readBytes().decodeToString() }
        })

        @Volatile
        private var cached: String? = null

        fun text(): String? = cached ?: runCatching(read).getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.also { cached = it }
    }

    private const val VERSION = 1L
    private const val FROM_SCRIPT = "script"
    private const val FROM_API = "api"

    /** The script's answer is at most 64 KB of item text, escaped twice. */
    private const val MAX_RESULT_CHARS = 512 * 1024
}