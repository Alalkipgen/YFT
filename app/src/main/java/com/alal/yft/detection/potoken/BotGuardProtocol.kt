package com.alal.yft.detection.potoken

import com.alal.yft.detection.JsonText
import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import com.alal.yft.extractor.api.json.asLongOrNull
import com.alal.yft.extractor.api.json.get
import okio.ByteString.Companion.decodeBase64

/**
 * The wire format of YouTube's attestation service and of YFT's host page, in plain Kotlin.
 *
 * The service is the one YouTube's web player calls: `Create` returns today's BotGuard
 * challenge (an interpreter script and a program for it) and `GenerateIT` trades BotGuard's
 * answer for an integrity token, from which the page mints proof-of-origin tokens. Everything
 * here is text handling, so it is tested on the JVM; the page itself lives in assets.
 */
internal object BotGuardProtocol {
    /** The request key YouTube's web player names for its own attestation. */
    const val REQUEST_KEY: String = "O43z0dpjhgX20SCx4KAo"
    const val CREATE_URL: String = "https://www.youtube.com/api/jnn/v1/Create"
    const val GENERATE_URL: String = "https://www.youtube.com/api/jnn/v1/GenerateIT"
    const val CONTENT_TYPE: String = "application/json+protobuf"
    const val CLIENT_HEADER: String = "grpc-web-javascript/0.1"
    const val CREATE_BODY: String = "[\"$REQUEST_KEY\"]"

    /** The page's first reply, sent once its script is ready. */
    const val LOADED_ID: Int = 0

    /**
     * Tokens are minted only for plain identifiers: a video ID, visitor data or a data-sync
     * identifier. None of these characters can end the page call's string literal.
     */
    val BINDING: Regex = Regex("^[A-Za-z0-9_\\-%=|.+/]{1,512}$")

    /** What the page posts back: web-safe base64, as YouTube's player sends it. */
    val MINTED_TOKEN: Regex = Regex("^[A-Za-z0-9_-]{16,4096}={0,2}$")

    const val MAX_REPLY_CHARS: Int = 16_384

    class Challenge(val interpreter: String, val program: String, val globalName: String) {
        override fun toString(): String =
            "Challenge(interpreter=<${interpreter.length} chars>, " +
                "program=<${program.length} chars>, globalName=$globalName)"
    }

    class Integrity(val token: String, val lifetimeSeconds: Long, val refreshSeconds: Long) {
        override fun toString(): String =
            "Integrity(token=<${token.length} chars>, lifetimeSeconds=$lifetimeSeconds, " +
                "refreshSeconds=$refreshSeconds)"
    }

    /** One page reply. [value] is a session secret for mint replies and never prints. */
    class Reply(val id: Int, val value: String?, val error: String?) {
        override fun toString(): String =
            "Reply(id=$id, value=${value?.let { "<${it.length} chars>" }}, error=$error)"
    }

    /** The key the player script names for its attestation calls; it is not a user secret. */
    fun attestationKey(playerScript: String): String? =
        ATTESTATION_KEY.find(playerScript)?.groupValues?.get(1)

    /**
     * Reads a `Create` answer, in either of the two forms YouTube's player accepts.
     *
     * Only an interpreter delivered inline is accepted: a challenge that names a script
     * address instead would make the page load code from elsewhere, so it counts as changed.
     */
    fun parseChallenge(body: String): Challenge? {
        val root = BoundedJsonParser.parse(body) as? JsonValue.Array ?: return null
        val scrambled = (root[1] as? JsonValue.Text)?.value
        val fields = if (scrambled != null) {
            descramble(scrambled)?.let { BoundedJsonParser.parse(it) }
        } else {
            root[0]
        } as? JsonValue.Array ?: return null
        val interpreter = (fields[1] as? JsonValue.Array)?.items
            ?.firstNotNullOfOrNull { (it as? JsonValue.Text)?.value?.takeIf(String::isNotBlank) }
            ?: return null
        val program = (fields[4] as? JsonValue.Text)?.value?.takeIf(String::isNotBlank)
            ?: return null
        val globalName = (fields[5] as? JsonValue.Text)?.value?.takeIf(GLOBAL_NAME::matches)
            ?: return null
        return Challenge(interpreter, program, globalName)
    }

    fun generateBody(answer: String): String = buildString {
        append("[\"").append(REQUEST_KEY).append("\",")
        JsonText.appendString(this, answer)
        append(']')
    }

    /** Reads a `GenerateIT` answer: the integrity token, its lifetime and refresh margin. */
    fun parseIntegrity(body: String): Integrity? {
        val root = BoundedJsonParser.parse(body) as? JsonValue.Array ?: return null
        val token = (root[0] as? JsonValue.Text)?.value?.takeIf(INTEGRITY_TOKEN::matches)
            ?: return null
        val lifetime = root[1].asLongOrNull?.takeIf { it > 0 } ?: DEFAULT_LIFETIME_SECONDS
        val refresh = root[2].asLongOrNull?.coerceIn(0, lifetime / 2) ?: 0
        return Integrity(token, lifetime, refresh)
    }

    /** The challenge as the page reads it from its own origin. */
    fun challengeDocument(challenge: Challenge): String = buildString {
        append("{\"interpreter\":")
        JsonText.appendString(this, challenge.interpreter)
        append(",\"program\":")
        JsonText.appendString(this, challenge.program)
        append(",\"globalName\":")
        JsonText.appendString(this, challenge.globalName)
        append('}')
    }

    fun snapshotCall(id: Int): String = "window.yftPoToken.snapshot($id)"

    fun createMinterCall(id: Int, integrityToken: String): String {
        require(INTEGRITY_TOKEN.matches(integrityToken)) { "Not an integrity token" }
        return "window.yftPoToken.createMinter($id,\"$integrityToken\")"
    }

    fun mintCall(id: Int, binding: String): String {
        require(BINDING.matches(binding)) { "Not a token binding" }
        return "window.yftPoToken.mint($id,\"$binding\")"
    }

    fun parseReply(text: String?): Reply? {
        if (text == null || text.length > MAX_REPLY_CHARS) return null
        val root = BoundedJsonParser.parse(text, maxDepth = 4, maxNodes = 16) ?: return null
        val id = root["id"].asLongOrNull?.takeIf { it in 0..Int.MAX_VALUE }?.toInt()
            ?: return null
        val value = (root["value"] as? JsonValue.Text)?.value?.takeIf(String::isNotEmpty)
        val error = (root["error"] as? JsonValue.Text)?.value?.takeIf(ERROR_CODE::matches)
        return Reply(id, value, error)
    }

    /** Base64 (standard or web-safe), each byte shifted back by 97, as YouTube's player does. */
    private fun descramble(scrambled: String): String? {
        val bytes = scrambled.decodeBase64()?.toByteArray()?.takeIf { it.isNotEmpty() }
            ?: return null
        for (index in bytes.indices) bytes[index] = (bytes[index] + SCRAMBLE_SHIFT).toByte()
        return bytes.toString(Charsets.UTF_8)
    }

    private const val SCRAMBLE_SHIFT = 97
    private const val DEFAULT_LIFETIME_SECONDS = 3_600L
    private val ATTESTATION_KEY =
        Regex("\"X-Goog-Api-Key\"\\]?\\s*[:=]\\s*\"([A-Za-z0-9_-]{20,60})\"")
    private val GLOBAL_NAME = Regex("^[A-Za-z_$][A-Za-z0-9_$]{0,63}$")
    private val INTEGRITY_TOKEN = Regex("^[A-Za-z0-9_\\-+/.]{16,8192}={0,2}$")
    private val ERROR_CODE = Regex("^[a-z]{1,16}$")
}
