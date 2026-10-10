/*
 * R9 (MASTER_KEY_PHASE1_PLAN.md): the data-only recipe config and its schema
 * (extractor-master/recipes/master-recipes.schema.json). A config changes only data a bundled
 * recipe already has:
 *   - `endpoint`: same https host as the bundled one, only the known placeholders;
 *   - `headers`: a fixed list of names, never Cookie, Authorization or User-Agent;
 *   - `agent`, and the key table: ID fields and paths, item lists, media keys, title, duration
 *     and thumbnail paths (each replaces the bundled list);
 *   - `drmPaths`, `noVideoPaths`, `wallPaths`: only added to the bundled ones, so a config can
 *     make Master stop sooner, never later.
 * Hosts, cookie domains, body caps, regular expressions, photo/access answers and stop rules
 * never come from a config. An unknown site or key, a wrong type or a value out of bounds rejects
 * the whole config, and the bundled recipes stay. No code is ever loaded.
 */
package com.alal.yft.extractor.master.recipes

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue

internal object RemoteRecipeConfig {
    const val SCHEMA = 1L
    const val MAX_CHARS = 64 * 1024

    val TOP_KEYS = setOf("schema", "version", "expires", "sites")
    val SITE_KEYS = setOf(
        "endpoint", "headers", "agent", "idPaths", "nodeIdFields", "items", "media", "titlePaths",
        "durationMillisPaths", "durationSecondsPaths", "thumbnailPaths", "drmPaths", "noVideoPaths",
        "wallPaths",
    )
    val MEDIA_KEYS = setOf("path", "url", "mime", "metaPath", "inlineDash", "order", "first", "onlyIfNone", "fields")
    val FIELD_KEYS = setOf("width", "height", "bitrate", "bytes", "codec")
    val HEADERS = listOf(
        "Accept", "Accept-Language", "Referer", "Origin", "Sec-Fetch-Mode", "Sec-Fetch-Site",
        "Sec-Fetch-Dest", "X-Requested-With", "X-IG-App-ID", "X-ASBD-ID",
    )
    val MIMES = setOf("video/mp4", "video/webm", "application/x-mpegURL", "application/dash+xml")
    val PLACEHOLDERS = setOf("id", "hashQuery", "href", "token")

    private const val MAX_TEXT = 512
    private const val MAX_LIST = 16
    private const val MAX_MEDIA = 24
    private const val MAX_SEGMENTS = 12
    private val SEGMENT = Regex("[A-Za-z0-9_*$@:-]{1,64}")
    private val DOTTED = Regex("[A-Za-z0-9_*$@:.-]{1,128}")
    private val WALL = Regex("/[a-z0-9/_-]{1,63}")
    private val DATE = Regex("([0-9]{4})-([0-9]{2})-([0-9]{2})")
    private val HOST = Regex("^https://([a-z0-9.-]+)(?:/|\\?|$)")
    private val PLACEHOLDER = Regex("\\{([A-Za-z]+)\\}")
    private val URL_CHARS = Regex("[A-Za-z0-9._~:/?&=%+,;!*'()\\[\\]{}-]+")

    sealed interface Parsed {
        data class Valid(val version: Long, val expiresEpochDay: Long, val recipes: List<ContractRecipe>) : Parsed
        data class Invalid(val reason: String) : Parsed
    }

    private class Rejected(val reason: String) : RuntimeException(reason)

    private fun reject(reason: String): Nothing = throw Rejected(reason)

    /** [text] checked against the schema and laid over [bundled]; anything wrong is [Parsed.Invalid]. */
    fun parse(text: String, bundled: List<ContractRecipe>): Parsed {
        if (text.length > MAX_CHARS) return Parsed.Invalid("config larger than $MAX_CHARS characters")
        val root = BoundedJsonParser.parse(text.trim(), maxDepth = 8, maxNodes = 8_000) as? JsonValue.Object
            ?: return Parsed.Invalid("config is not a bounded JSON object")
        return try {
            keys(root, TOP_KEYS, "config")
            val schema = whole(root.entries["schema"], "schema")
            if (schema != SCHEMA) reject("schema $schema is not $SCHEMA")
            val version = whole(root.entries["version"], "version")
            if (version < 1) reject("version must be positive")
            val expires = epochDay(text(root.entries["expires"], "expires"))
            val sites = root.entries["sites"] as? JsonValue.Object ?: reject("sites must be an object")
            val overlays = sites.entries.mapValues { (site, value) ->
                val recipe = bundled.firstOrNull { it.site == site } ?: reject("unknown site $site")
                overlay(recipe, value as? JsonValue.Object ?: reject("$site must be an object"))
            }
            Parsed.Valid(version, expires, bundled.map { overlays[it.site] ?: it })
        } catch (rejected: Rejected) {
            Parsed.Invalid(rejected.reason)
        }
    }

    private fun overlay(recipe: ContractRecipe, site: JsonValue.Object): ContractRecipe {
        val name = recipe.site
        keys(site, SITE_KEYS, name)
        val entries = site.entries
        fun paths(key: String) = entries[key]?.let { paths(it, "$name.$key") }
        var out = recipe
        entries["endpoint"]?.let { out = out.copy(endpoint = endpoint(recipe, text(it, "$name.endpoint"))) }
        entries["headers"]?.let { out = out.copy(headers = recipe.headers + headers(it, name)) }
        entries["agent"]?.let { out = out.copy(agent = printable(text(it, "$name.agent"), "$name.agent")) }
        paths("idPaths")?.let { out = out.copy(idPaths = it) }
        entries["nodeIdFields"]?.let { out = out.copy(nodeIdFields = dotted(it, "$name.nodeIdFields")) }
        paths("items")?.let { out = out.copy(items = it) }
        entries["media"]?.let { out = out.copy(media = media(it, name)) }
        paths("titlePaths")?.let { out = out.copy(titlePaths = it) }
        paths("durationMillisPaths")?.let { out = out.copy(durationMillisPaths = it) }
        paths("durationSecondsPaths")?.let { out = out.copy(durationSecondsPaths = it) }
        paths("thumbnailPaths")?.let { out = out.copy(thumbnailPaths = it) }
        paths("drmPaths")?.let { out = out.copy(drmPaths = (recipe.drmPaths + it).distinct()) }
        paths("noVideoPaths")?.let { out = out.copy(noVideoPaths = (recipe.noVideoPaths + it).distinct()) }
        entries["wallPaths"]?.let { value ->
            val walls = list(value, "$name.wallPaths", MAX_LIST).map { text(it, "$name.wallPaths") }
            if (!walls.all(WALL::matches)) reject("$name.wallPaths must be lowercase paths")
            out = out.copy(wallPaths = (recipe.wallPaths + walls).distinct())
        }
        return out
    }

    /** The bundled endpoint's own https host, known placeholders only, no user part or fragment. */
    private fun endpoint(recipe: ContractRecipe, value: String): String {
        val bundledHost = HOST.find(recipe.endpoint)?.groupValues?.get(1)
        val host = HOST.find(value)?.groupValues?.get(1)
        if (value.length > MAX_TEXT || !URL_CHARS.matches(value)) reject("${recipe.site}.endpoint has bad characters")
        if (host == null || host != bundledHost || host !in recipe.answerHosts) {
            reject("${recipe.site}.endpoint must stay on https://$bundledHost")
        }
        val names = PLACEHOLDER.findAll(value).map { it.groupValues[1] }.toList()
        if (!PLACEHOLDERS.containsAll(names) || value.count { it == '{' } != names.size ||
            value.count { it == '}' } != names.size
        ) {
            reject("${recipe.site}.endpoint uses an unknown placeholder")
        }
        return value
    }

    private fun headers(value: JsonValue, site: String): Map<String, String> {
        val map = value as? JsonValue.Object ?: reject("$site.headers must be an object")
        if (map.entries.size > MAX_LIST) reject("$site.headers has too many entries")
        return map.entries.entries.associate { (key, header) ->
            val name = HEADERS.firstOrNull { it.equals(key, ignoreCase = true) }
                ?: reject("$site.headers: $key is not a recipe header")
            name to printable(text(header, "$site.headers.$key"), "$site.headers.$key")
        }
    }

    private fun media(value: JsonValue, site: String): List<ContractMedia> =
        list(value, "$site.media", MAX_MEDIA).mapIndexed { index, entry ->
            val label = "$site.media.$index"
            val item = entry as? JsonValue.Object ?: reject("$label must be an object")
            keys(item, MEDIA_KEYS, label)
            val e = item.entries
            ContractMedia(
                path = path(e["path"] ?: reject("$label.path is required"), "$label.path"),
                url = e["url"]?.let { path(it, "$label.url") } ?: listOf("url"),
                mime = e["mime"]?.let { text(it, "$label.mime").takeIf(MIMES::contains) ?: reject("$label.mime") },
                metaPath = e["metaPath"]?.let { path(it, "$label.metaPath") },
                inlineDash = e["inlineDash"]?.let { flag(it, "$label.inlineDash") } ?: false,
                order = e["order"]?.let { order ->
                    ContractOrder.entries.firstOrNull { it.name == text(order, "$label.order") }
                        ?: reject("$label.order")
                } ?: ContractOrder.LISTED,
                first = e["first"]?.let { flag(it, "$label.first") } ?: false,
                onlyIfNone = e["onlyIfNone"]?.let { flag(it, "$label.onlyIfNone") } ?: false,
                fields = e["fields"]?.let { fields(it, "$label.fields") } ?: ContractFields(),
            )
        }

    private fun fields(value: JsonValue, label: String): ContractFields {
        val map = value as? JsonValue.Object ?: reject("$label must be an object")
        keys(map, FIELD_KEYS, label)
        val defaults = ContractFields()
        fun names(key: String, fallback: List<String>) = map.entries[key]?.let { dotted(it, "$label.$key") } ?: fallback
        return ContractFields(
            width = names("width", defaults.width),
            height = names("height", defaults.height),
            bitrate = names("bitrate", defaults.bitrate),
            bytes = names("bytes", defaults.bytes),
            codec = names("codec", defaults.codec),
        )
    }

    private fun keys(map: JsonValue.Object, allowed: Set<String>, label: String) {
        map.entries.keys.firstOrNull { it !in allowed }?.let { reject("$label: unknown key $it") }
    }

    private fun list(value: JsonValue, label: String, max: Int): List<JsonValue> {
        val items = (value as? JsonValue.Array)?.items ?: reject("$label must be a list")
        if (items.size > max) reject("$label has more than $max entries")
        return items
    }

    private fun path(value: JsonValue, label: String): List<String> {
        val segments = list(value, label, MAX_SEGMENTS).map { text(it, label) }
        if (segments.isEmpty() || !segments.all(SEGMENT::matches)) reject("$label is not a key path")
        return segments
    }

    private fun paths(value: JsonValue, label: String): List<List<String>> =
        list(value, label, MAX_LIST).map { path(it, label) }

    private fun dotted(value: JsonValue, label: String): List<String> {
        val names = list(value, label, MAX_LIST).map { text(it, label) }
        if (!names.all(DOTTED::matches)) reject("$label is not a list of field names")
        return names
    }

    private fun text(value: JsonValue?, label: String): String =
        (value as? JsonValue.Text)?.value?.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT }
            ?: reject("$label must be a text of 1-$MAX_TEXT characters")

    private fun printable(value: String, label: String): String =
        value.takeIf { text -> text.all { it in ' '..'~' } } ?: reject("$label must be printable ASCII")

    private fun flag(value: JsonValue, label: String): Boolean =
        (value as? JsonValue.Bool)?.value ?: reject("$label must be true or false")

    private fun whole(value: JsonValue?, label: String): Long {
        val number = (value as? JsonValue.Number)?.text ?: reject("$label must be a whole number")
        return number.toLongOrNull()?.takeIf { it in 0..Int.MAX_VALUE } ?: reject("$label must be a whole number")
    }

    /** `YYYY-MM-DD` as days since 1970-01-01 (java.time is missing below Android 8). */
    private fun epochDay(value: String): Long {
        val (y, m, d) = DATE.matchEntire(value)?.destructured?.let { (y, m, d) ->
            Triple(y.toLong(), m.toInt(), d.toInt())
        } ?: reject("expires must be YYYY-MM-DD")
        val leap = y % 4 == 0L && (y % 100 != 0L || y % 400 == 0L)
        val days = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if (y !in 2000..2999 || m !in 1..12 || d !in 1..days[m - 1]) reject("expires is not a date")
        // Howard Hinnant's days_from_civil.
        val year = if (m <= 2) y - 1 else y
        val era = year / 400
        val yoe = year - era * 400
        val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }
}