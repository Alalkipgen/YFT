package com.alal.yft.extractor.master.android.solver

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnSolverPageRoutesTest {
    @Test
    fun `the page, its scripts and the job are served from the reserved asset host only`() {
        val prefix = "/yft-own-solver/"
        OwnSolverPageRoutes.ASSETS.forEach { (name, mimeType) ->
            assertEquals(
                OwnSolverPageRoutes.Route.Asset(name, mimeType),
                OwnSolverPageRoutes.route("https", OwnSolverPageRoutes.HOST, prefix + name),
            )
        }
        assertEquals(
            OwnSolverPageRoutes.Route.Input,
            OwnSolverPageRoutes.route("https", OwnSolverPageRoutes.HOST, prefix + "own-solver-input.json"),
        )
        assertEquals("https://appassets.androidplatform.net/yft-own-solver/own-solver.html", OwnSolverPageRoutes.PAGE_URL)
    }

    @Test
    fun `everything else is refused, main's ejs files included`() {
        val refused = listOf(
            Triple("http", OwnSolverPageRoutes.HOST, "/yft-own-solver/own-solver.html"),
            Triple("https", "www.youtube.com", "/yft-own-solver/own-solver.html"),
            Triple("https", OwnSolverPageRoutes.HOST, "/yft-solver/solver.html"),
            Triple("https", OwnSolverPageRoutes.HOST, "/yft-own-solver/yt.solver.core.min.js"),
            Triple("https", OwnSolverPageRoutes.HOST, "/yft-own-solver/../youtube-solver/yt.solver.lib.min.js"),
            Triple("https", OwnSolverPageRoutes.HOST, "/yft-own-solver/"),
            Triple("https", OwnSolverPageRoutes.HOST, null),
            Triple(null, OwnSolverPageRoutes.HOST, "/yft-own-solver/own-solver.html"),
        )
        refused.forEach { (scheme, host, path) ->
            assertEquals("$scheme $host $path", OwnSolverPageRoutes.Route.Refused, OwnSolverPageRoutes.route(scheme, host, path))
        }
    }

    @Test
    fun `every routed file is bundled and the page states the same policy`() {
        val folder = File("src/main/assets/${OwnSolverPageRoutes.ASSET_DIRECTORY}")
        OwnSolverPageRoutes.ASSETS.keys.forEach { name ->
            assertTrue(name, File(folder, name).isFile)
        }
        val page = File(folder, "own-solver.html").readText()
        assertTrue(page.contains("content=\"${OwnSolverPageRoutes.CONTENT_SECURITY_POLICY}\""))
        assertTrue(page.contains("src=\"own-solver-page.js\""))
    }
}
