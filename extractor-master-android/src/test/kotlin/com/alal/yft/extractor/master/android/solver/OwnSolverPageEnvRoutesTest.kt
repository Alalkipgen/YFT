package com.alal.yft.extractor.master.android.solver

import com.alal.yft.extractor.master.android.solver.OwnSolverPageEnvRoutes.Route
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnSolverPageEnvRoutesTest {
    private val prefix = "/yft-own-solver/"
    private val host = OwnSolverPageEnvRoutes.HOST
    private val folder = File("src/main/assets/${OwnSolverPageEnvRoutes.ASSET_DIRECTORY}")
    private val sources = File("src/main/kotlin/com/alal/yft/extractor/master/android/solver")

    private fun route(name: String, mainFrame: Boolean) =
        OwnSolverPageEnvRoutes.route("https", host, prefix + name, mainFrame)

    @Test
    fun `the host page is a main frame only, everything else a sub frame or sub resource only`() {
        assertEquals(Route.Asset("page-env.html", "text/html", OwnSolverPageEnvRoutes.HOST_CSP), route("page-env.html", true))
        assertEquals(
            Route.Asset("page-frame.html", "text/html", OwnSolverPageEnvRoutes.FRAME_CSP_HEADER),
            route("page-frame.html", false),
        )
        listOf("page-env.js", "page-frame.js", "meriyah.umd.min.js", "own.solver.core.js").forEach { name ->
            assertEquals(Route.Asset(name, "text/javascript", OwnSolverPageEnvRoutes.FILE_CSP), route(name, false))
            assertEquals(name, Route.Refused, route(name, true))
        }
        assertEquals(Route.Input, route("page-env-input.json", false))
        assertEquals(Route.Refused, route("page-env-input.json", true))
        assertEquals(Route.Refused, route("page-env.html", false))
        assertEquals(Route.Refused, route("page-frame.html", true))
        assertEquals("https://appassets.androidplatform.net/yft-own-solver/page-env.html", OwnSolverPageEnvRoutes.PAGE_URL)
        assertEquals("https://appassets.androidplatform.net/yft-own-solver/page-frame.html", OwnSolverPageEnvRoutes.FRAME_URL)
        assertEquals("https://$host", OwnSolverPageEnvRoutes.ORIGIN)
    }

    @Test
    fun `the worker's files, main's ejs files and every other address are refused`() {
        val names = listOf(
            "own-solver.html", "own-solver-page.js", "own-solver-worker.js", "own-solver-input.json",
            "yt.solver.core.min.js", "yt.solver.lib.min.js", "../youtube-solver/yt.solver.lib.min.js", "", "PAGE-ENV.HTML",
        )
        names.forEach { name ->
            assertEquals(name, Route.Refused, route(name, false))
            assertEquals(name, Route.Refused, route(name, true))
        }
        listOf(
            Triple("http", host, "/yft-own-solver/page-env.html"),
            Triple("https", "www.youtube.com", "/yft-own-solver/page-env.html"),
            Triple("https", host, "/yft-solver/page-env.html"),
            Triple("https", host, null),
            Triple(null, host, "/yft-own-solver/page-env.html"),
        ).forEach { (scheme, other, path) ->
            assertEquals("$scheme $other $path", Route.Refused, OwnSolverPageEnvRoutes.route(scheme, other, path, true))
        }
    }

    @Test
    fun `only the frame's own page may load in the frame, nothing navigates the main frame`() {
        assertTrue(OwnSolverPageEnvRoutes.allowsNavigation(OwnSolverPageEnvRoutes.FRAME_URL, isForMainFrame = false))
        assertFalse(OwnSolverPageEnvRoutes.allowsNavigation(OwnSolverPageEnvRoutes.FRAME_URL, isForMainFrame = true))
        assertFalse(OwnSolverPageEnvRoutes.allowsNavigation(OwnSolverPageEnvRoutes.PAGE_URL, isForMainFrame = true))
        listOf("https://www.youtube.com/", "about:blank", "javascript:void(0)", "data:text/html,x", null).forEach {
            assertFalse("$it", OwnSolverPageEnvRoutes.allowsNavigation(it, isForMainFrame = false))
        }
    }

    @Test
    fun `every routed file is bundled and the pages state the table's policies`() {
        OwnSolverPageEnvRoutes.ASSETS.keys.forEach { name -> assertTrue(name, File(folder, name).isFile) }
        val page = File(folder, "page-env.html").readText()
        assertTrue(page.contains("content=\"${OwnSolverPageEnvRoutes.HOST_CSP}\""))
        assertTrue(page.contains("<script src=\"page-env.js\"></script>"))
        val frame = File(folder, "page-frame.html").readText()
        assertTrue(frame.contains("content=\"${OwnSolverPageEnvRoutes.FRAME_CSP}\""))
        assertTrue(frame.contains("<script src=\"page-frame.js\"></script>"))
        listOf(page, frame).forEach { assertTrue(it.contains("<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\">")) }
        assertTrue(OwnSolverPageEnvRoutes.FRAME_CSP.contains("connect-src 'none'"))
        assertTrue(OwnSolverPageEnvRoutes.FRAME_CSP.contains("script-src ${OwnSolverPageEnvRoutes.ORIGIN}/yft-own-solver/page-frame.js 'unsafe-eval';"))
        assertEquals(
            "${OwnSolverPageEnvRoutes.FRAME_CSP}; sandbox allow-scripts; frame-ancestors https://appassets.androidplatform.net",
            OwnSolverPageEnvRoutes.FRAME_CSP_HEADER,
        )
    }

    @Test
    fun `the frame is sandboxed with scripts only and the reply object is limited to the app's origin`() {
        val hostScript = File(folder, "page-env.js").readText()
        val frameScript = File(folder, "page-frame.js").readText()
        assertTrue(hostScript.contains("frame.setAttribute(\"sandbox\", \"allow-scripts\");"))
        // The sandbox is set before the frame gets an address or a parent.
        val sandboxAt = hostScript.indexOf("frame.setAttribute(\"sandbox\"")
        assertTrue(sandboxAt < hostScript.indexOf("frame.src = ") && sandboxAt < hostScript.indexOf("appendChild(frame)"))
        listOf("page-env.html", "page-frame.html", "page-env.js", "page-frame.js").forEach { name ->
            val text = File(folder, name).readText()
            listOf("allow-same-origin", "allow-top-navigation", "allow-popups", "allow-forms", "allow-modals").forEach {
                assertFalse("$name $it", text.contains(it))
            }
        }
        assertTrue(hostScript.contains("window.${OwnSolverPageEnvRoutes.BRIDGE_NAME};"))
        assertTrue(hostScript.contains("load(\"${OwnSolverPageEnvRoutes.INPUT_NAME}\")"))
        assertTrue(frameScript.contains("var HOST_ORIGIN = \"${OwnSolverPageEnvRoutes.ORIGIN}\";"))
        assertFalse(frameScript.contains(OwnSolverPageEnvRoutes.BRIDGE_NAME))
        assertEquals(setOf("https://appassets.androidplatform.net"), OwnSolverPageEnvRoutes.ALLOWED_ORIGIN_RULES)
    }

    @Test
    fun `the page engine has no JavaScript interface and listens on the app's origin only`() {
        val engine = File(sources, "WebViewPageSolverEngine.kt").readText()
        assertFalse(engine.contains("addJavascriptInterface"))
        assertFalse(engine.contains("JavascriptInterface"))
        assertTrue(engine.contains("OwnSolverPageEnvRoutes.ALLOWED_ORIGIN_RULES"))
        assertTrue(engine.contains("WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)"))
        // The listener is in place before the page loads.
        assertTrue(engine.indexOf("WebViewCompat.addWebMessageListener(") < engine.indexOf("webView.loadUrl("))
    }
}
