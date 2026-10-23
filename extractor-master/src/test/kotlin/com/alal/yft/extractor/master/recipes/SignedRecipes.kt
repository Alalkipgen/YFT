package com.alal.yft.extractor.master.recipes

import com.alal.yft.extractor.api.ExtractorHttpClient
import com.alal.yft.extractor.master.NOW
import com.alal.yft.extractor.master.contract.SiteContracts
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Social plan S4's tests: a recipe config signed with a throwaway key (made per test run, never
 * written anywhere) and applied without a client (nothing is ever asked), and the recipe source a
 * ladder or the engine reads it with.
 */
internal object SignedRecipes {
    /** The build's default address for the config (the recipes branch). */
    const val CONFIG_URL = "https://raw.githubusercontent.com/Alalkipgen/YFT/recipes/master-recipes.json"

    val keys: KeyPair by lazy { keyPair() }

    /** A config payload: [sites] (JSON) and, when given, the rungs off ([rungsOff]). */
    fun payload(
        version: Long = 1,
        expires: String = "2027-12-31",
        sites: String = "{}",
        rungsOff: List<String>? = null,
    ): String {
        val off = rungsOff?.let { names -> ""","rungsOff":[${names.joinToString(",") { "\"$it\"" }}]""" }.orEmpty()
        return """{"schema":1,"version":$version,"expires":"$expires","sites":$sites$off}"""
    }

    /** The build's signature check for [keys]. */
    fun signature(): RemoteRecipeSignature = checkNotNull(RemoteRecipeSignature.of(keys.public.encoded.toHex()))

    /** Recipes with [payload] applied (signed with [keys]); [clock], the time. */
    fun applied(payload: String, clock: () -> Long = { NOW }): RemoteRecipes {
        val remote = RemoteRecipes(null, CONFIG_URL, signature(), ContractRecipes.ALL, clock)
        val outcome = remote.accept(sign(keys, payload))
        check(outcome.endsWith(" applied")) { outcome }
        return remote
    }

    /** A ladder's recipe source (its `contractsOf`) whose signed config turns [rungs] off. */
    fun rungsOff(vararg rungs: String): (ExtractorHttpClient) -> SiteContracts {
        val remote = applied(payload(rungsOff = rungs.toList()))
        return { http -> SiteContracts(http, remote) }
    }

    fun keyPair(curve: String = "secp256r1"): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec(curve)) }.generateKeyPair()

    /** [payload]'s envelope, signed with [keys] (ES256, as the signing script makes it). */
    fun sign(keys: KeyPair, payload: String): String {
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(payload.toByteArray(Charsets.UTF_8))
            sign()
        }
        return """{"alg":"ES256","payload":${quote(payload)},"signature":"${signature.toHex()}"}"""
    }

    fun quote(text: String): String = buildString {
        append('"')
        text.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
        append('"')
    }

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
