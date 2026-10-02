package com.alal.yft.detection.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SolverPageRoutesTest {
    @Test
    fun `the page, its scripts and the job input are served`() {
        assertEquals(
            SolverPageRoutes.Route.Asset("solver.html", "text/html"),
            route("/yft-solver/solver.html"),
        )
        listOf(
            "solver-page.js",
            "solver-worker.js",
            "yt.solver.lib.min.js",
            "yt.solver.core.min.js",
        ).forEach { name ->
            assertEquals(
                SolverPageRoutes.Route.Asset(name, "text/javascript"),
                route("/yft-solver/$name"),
            )
        }
        assertEquals(SolverPageRoutes.Route.Input, route("/yft-solver/solver-input.json"))
    }

    @Test
    fun `every other address is refused`() {
        listOf(
            SolverPageRoutes.route("http", SolverPageRoutes.HOST, "/yft-solver/solver.html"),
            SolverPageRoutes.route("https", "www.youtube.com", "/yft-solver/solver.html"),
            SolverPageRoutes.route("https", SolverPageRoutes.HOST, null),
            route("/favicon.ico"),
            route("/yft-solver/"),
            route("/yft-solver/../solver.html"),
            route("/yft-solver/sub/solver.html"),
            route("/other/solver.html"),
        ).forEach { refused ->
            assertEquals(SolverPageRoutes.Route.Refused, refused)
        }
    }

    @Test
    fun `the policy admits no other origin`() {
        val policy = SolverPageRoutes.CONTENT_SECURITY_POLICY
        assertTrue(policy.startsWith("default-src 'none';"))
        assertTrue(policy.contains("connect-src 'self'"))
        assertTrue(!policy.contains("https:") && !policy.contains("*"))
        assertEquals(
            "https://appassets.androidplatform.net/yft-solver/solver.html",
            SolverPageRoutes.PAGE_URL,
        )
    }

    private fun route(path: String) = SolverPageRoutes.route("https", SolverPageRoutes.HOST, path)
}
