/*
 * VK Master plan §9 (docs/VK_MASTER_PLAN.md): R9 schema 2, additive
 * (extractor-master/recipes/master-recipes-2.schema.json). The shipped schema-1 reader
 * ([RemoteRecipeConfig], v1.0.0) rejects a whole config that names an unknown site or rung, so a
 * config that mentions VK would cost every v1.0.0 phone the four sites' fixes and kill switches.
 * So the publish workflow signs two files from one committed schema-2 config:
 * master-recipes.json (schema 1, VK and V1–V3 left out — [schema1], the same projection the
 * publish helper makes) for v1.0.0, and master-recipes-2.json (schema 2, all five sites) for
 * v1.0.1, which reads it here.
 *
 * Schema 2 is schema 1 plus site `vk` and rungs V1, V2 and V3 ([RUNGS]) — the same top keys,
 * site keys, bounds and rules, checked by the unchanged schema-1 reader itself: this reader checks
 * the frame (size, bounded JSON, top keys, schema 2, the sites and rungs it knows), then hands
 * the schema-1 projection to [RemoteRecipeConfig.parse] with the four sites' bundled recipes, and
 * VK's part, as a one-site schema-1 config, with the bundled VK recipe. Anything either rejects
 * rejects the whole config, and the bundled recipes stay. A config that names `vk` in a build
 * without a VK recipe is rejected as an unknown site (the schema-1 rule). No code is ever loaded,
 * and no value of VK's web client is carried (the owner decides that, plan §2 and §9).
 */
package com.alal.yft.extractor.master.recipes

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue

internal object RemoteRecipeConfig2 {
    const val SCHEMA = 2L
    const val MAX_CHARS = RemoteRecipeConfig.MAX_CHARS

    /** VK's site ID (agent B's `VkRecipe` and `VkLadder.SITE_ID` must say the same). */
    const val VK = "vk"

    val TOP_KEYS = RemoteRecipeConfig.TOP_KEYS

    /** The sites a schema-1 config names ([ContractRecipes.ALL]'s, in its order). */
    val SCHEMA_1_SITES = listOf("vimeo", "x", "facebook", "tiktok", "instagram")
    val SITES = SCHEMA_1_SITES + VK

    /** VK's ladder rungs a config may turn off. */
    val VK_RUNGS = listOf("V1", "V2", "V3")

    /** The rungs a schema-2 config may turn off: schema 1's, then VK's. */
    val RUNGS = RemoteRecipeConfig.RUNGS + VK_RUNGS

    private const val MAX_TEXT = 512

    private class Rejected(val reason: String) : RuntimeException(reason)

    private fun reject(reason: String): Nothing = throw Rejected(reason)

    /** A schema-2 config whose frame passed: its top level, its sites and its rungs off. */
    private class Frame(val root: JsonValue.Object, val sites: JsonValue.Object, val rungsOff: List<String>?)

    /**
     * [text] (schema 2) checked and laid over [bundled] (the four sites' recipes and, in a build
     * with VK's pack, VK's); anything wrong is [RemoteRecipeConfig.Parsed.Invalid]. The recipes
     * come back in [bundled]'s order, a recipe the config does not name as bundled.
     */
    fun parse(text: String, bundled: List<ContractRecipe>): RemoteRecipeConfig.Parsed {
        val frame = try {
            frame(text)
        } catch (rejected: Rejected) {
            return RemoteRecipeConfig.Parsed.Invalid(rejected.reason)
        }
        val vkRecipe = bundled.firstOrNull { it.site == VK }
        val main = when (val got = RemoteRecipeConfig.parse(write(schema1Of(frame)), bundled.filter { it.site != VK })) {
            is RemoteRecipeConfig.Parsed.Valid -> got
            is RemoteRecipeConfig.Parsed.Invalid -> return got
        }
        val vkSite = frame.sites.entries[VK]
        val vk = when {
            vkSite == null -> vkRecipe
            vkRecipe == null -> return RemoteRecipeConfig.Parsed.Invalid("unknown site $VK")
            else -> when (val got = RemoteRecipeConfig.parse(write(vkOnly(frame, vkSite)), listOf(vkRecipe))) {
                is RemoteRecipeConfig.Parsed.Valid -> got.recipes.single()
                is RemoteRecipeConfig.Parsed.Invalid -> return got
            }
        }
        val others = main.recipes.associateBy { it.site }
        val recipes = bundled.map { recipe -> if (recipe.site == VK) vk ?: recipe else others[recipe.site] ?: recipe }
        val off = main.rungsOff + frame.rungsOff.orEmpty().filter { it in VK_RUNGS }
        return RemoteRecipeConfig.Parsed.Valid(main.version, main.expiresEpochDay, recipes, off)
    }

    /**
     * The schema-1 config of a schema-2 [text] — the payload v1.0.0's file carries: the same keys
     * in the same order, `schema` 1, `vk` left out of `sites`, V1–V3 left out of `rungsOff` (and
     * `rungsOff` itself when that leaves it empty); null when [text]'s frame is not schema 2's.
     * The publish helper (scripts/master-recipes-publish.py) makes the same projection.
     */
    fun schema1(text: String): String? = try {
        write(schema1Of(frame(text)))
    } catch (rejected: Rejected) {
        null
    }

    private fun frame(text: String): Frame {
        if (text.length > MAX_CHARS) reject("config larger than $MAX_CHARS characters")
        val root = BoundedJsonParser.parse(text.trim(), maxDepth = 8, maxNodes = 8_000) as? JsonValue.Object
            ?: reject("config is not a bounded JSON object")
        root.entries.keys.firstOrNull { it !in TOP_KEYS }?.let { reject("config: unknown key $it") }
        val schema = (root.entries["schema"] as? JsonValue.Number)?.text?.toLongOrNull()
            ?: reject("schema must be a whole number")
        if (schema != SCHEMA) reject("schema $schema is not $SCHEMA")
        val sites = root.entries["sites"] as? JsonValue.Object ?: reject("sites must be an object")
        sites.entries.keys.firstOrNull { it !in SITES }?.let { reject("unknown site $it") }
        val off = root.entries["rungsOff"]?.let { value ->
            val items = (value as? JsonValue.Array)?.items ?: reject("rungsOff must be a list")
            if (items.size > RUNGS.size) reject("rungsOff has more than ${RUNGS.size} entries")
            val names = items.map { item ->
                (item as? JsonValue.Text)?.value?.takeIf { it.isNotEmpty() && it.length <= MAX_TEXT }
                    ?: reject("rungsOff must be a text of 1-$MAX_TEXT characters")
            }
            names.firstOrNull { it !in RUNGS }?.let { reject("rungsOff: unknown rung $it") }
            if (names.toSet().size != names.size) reject("rungsOff names a rung twice")
            names
        }
        return Frame(root, sites, off)
    }

    private fun schema1Of(frame: Frame): JsonValue.Object {
        val out = LinkedHashMap<String, JsonValue>()
        for ((key, value) in frame.root.entries) {
            when (key) {
                "schema" -> out[key] = JsonValue.Number(RemoteRecipeConfig.SCHEMA.toString())
                "sites" -> out[key] = JsonValue.Object(frame.sites.entries.filterKeys { it != VK })
                "rungsOff" -> {
                    val all = frame.rungsOff.orEmpty()
                    val kept = all.filter { it !in VK_RUNGS }
                    if (kept.isNotEmpty() || all.isEmpty()) out[key] = JsonValue.Array(kept.map(JsonValue::Text))
                }
                else -> out[key] = value
            }
        }
        return JsonValue.Object(out)
    }

    /** VK's part as a one-site schema-1 config, for the schema-1 reader with the VK recipe. */
    private fun vkOnly(frame: Frame, vk: JsonValue): JsonValue.Object {
        val out = LinkedHashMap<String, JsonValue>()
        out["schema"] = JsonValue.Number(RemoteRecipeConfig.SCHEMA.toString())
        frame.root.entries["version"]?.let { out["version"] = it }
        frame.root.entries["expires"]?.let { out["expires"] = it }
        out["sites"] = JsonValue.Object(linkedMapOf(VK to vk))
        return JsonValue.Object(out)
    }

    /** [value] as compact JSON the bounded parser reads back as the same tree (never longer than its source). */
    private fun write(value: JsonValue): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: JsonValue) {
        when (value) {
            JsonValue.Null -> out.append("null")
            is JsonValue.Bool -> out.append(value.value)
            is JsonValue.Number -> out.append(value.text)
            is JsonValue.Text -> quote(out, value.value)
            is JsonValue.Array -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }
            is JsonValue.Object -> {
                out.append('{')
                value.entries.entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) out.append(',')
                    quote(out, key)
                    out.append(':')
                    write(out, item)
                }
                out.append('}')
            }
        }
    }

    private fun quote(out: StringBuilder, text: String) {
        out.append('"')
        for (char in text) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (char < ' ') out.append("\\u").append(char.code.toString(16).padStart(4, '0')) else out.append(char)
            }
        }
        out.append('"')
    }
}
