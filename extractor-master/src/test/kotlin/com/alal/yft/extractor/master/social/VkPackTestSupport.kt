package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.fixture

/**
 * The VK pack's made-up fixtures (`fixtures/vk-pack/`, [VkPackFixtureHygieneTest]) as the tests read
 * them. Every query value there is `FIXTURE` (agent A's rule, docs/VK_MASTER_CENSUS.md §8), so a
 * test that reads an expiry puts its own number in: [vkPack] gives [name]'s text with every file's
 * `expires` [VK_PACK_EXPIRES].
 */
internal fun vkPack(name: String): String =
    fixture("vk-pack/$name").replace("expires=FIXTURE", "expires=$VK_PACK_EXPIRES")

/** The tests' own file expiry (epoch seconds, made up: in 2030). */
internal const val VK_PACK_EXPIRES = 1_900_000_000L

/** The tests' own anonymous token (made up, of VK's shape, says "not-real"). */
internal const val VK_PACK_TOKEN = "fixture.anonymous.token.not-real.0123456789"

/**
 * login.vk.ru's okay answer, built here: a token answer is never a fixture (A's rule, as main's
 * tests build theirs), with the made-up [value] and its [expires] (epoch seconds).
 */
internal fun vkToken(value: String = VK_PACK_TOKEN, expires: Long = VK_PACK_EXPIRES): String =
    """{"type":"okay","data":{"access_token":"$value","expires":$expires}}"""
