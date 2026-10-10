/*
 * R9 (MASTER_KEY_PHASE1_PLAN.md): the signed envelope of a Master recipe config.
 *   {"alg": "ES256", "payload": "<the config's JSON text>", "signature": "<hex DER>"}
 * The signature is ECDSA P-256 with SHA-256 over the payload's UTF-8 bytes, made with the owner's
 * private key (scripts/master-recipe-sign.sh); the build carries only the public key (X.509 DER as
 * hex, `yft.masterRecipeKey`). Hex, not Base64: java.util.Base64 is missing below Android 8.
 */
package com.alal.yft.extractor.master.recipes

import com.alal.yft.extractor.api.json.BoundedJsonParser
import com.alal.yft.extractor.api.json.JsonValue
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec

internal class RemoteRecipeSignature private constructor(private val key: PublicKey) {
    /** The config text [envelope] carries, or null unless this key signed exactly that text. */
    fun payload(envelope: String): String? {
        if (envelope.length > MAX_ENVELOPE_CHARS) return null
        val root = BoundedJsonParser.parse(envelope.trim(), maxDepth = 2, maxNodes = 8) as? JsonValue.Object
            ?: return null
        if (root.entries.keys != ENVELOPE_KEYS) return null
        if ((root.entries["alg"] as? JsonValue.Text)?.value != ALG) return null
        val payload = (root.entries["payload"] as? JsonValue.Text)?.value ?: return null
        val signature = hex((root.entries["signature"] as? JsonValue.Text)?.value, MAX_SIGNATURE_BYTES)
            ?: return null
        val signed = runCatching {
            Signature.getInstance(SIGNATURE).run {
                initVerify(key)
                update(payload.toByteArray(Charsets.UTF_8))
                verify(signature)
            }
        }.getOrDefault(false)
        return payload.takeIf { signed }
    }

    companion object {
        const val ALG = "ES256"
        const val MAX_ENVELOPE_CHARS = 160 * 1024
        private const val SIGNATURE = "SHA256withECDSA"
        private const val MAX_SIGNATURE_BYTES = 80
        private const val MAX_KEY_BYTES = 200
        private val ENVELOPE_KEYS = setOf("alg", "payload", "signature")

        /** The build's key; null (no remote config at all) unless it is a P-256 public key. */
        fun of(publicKeyHex: String?): RemoteRecipeSignature? {
            val der = hex(publicKeyHex?.trim(), MAX_KEY_BYTES) ?: return null
            val key = runCatching {
                KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
            }.getOrNull() as? ECPublicKey ?: return null
            if (key.params.curve.field.fieldSize != 256) return null
            return RemoteRecipeSignature(key)
        }

        fun hex(text: String?, maxBytes: Int): ByteArray? {
            if (text.isNullOrEmpty() || text.length % 2 != 0 || text.length > maxBytes * 2) return null
            val out = ByteArray(text.length / 2)
            for (index in out.indices) {
                val high = Character.digit(text[2 * index], 16)
                val low = Character.digit(text[2 * index + 1], 16)
                if (high < 0 || low < 0) return null
                out[index] = (high * 16 + low).toByte()
            }
            return out
        }
    }
}