package com.alal.yft.extractor.master.toolkit

/** Bounded regular-expression views of delivered markup. Nothing here runs page script. */
internal object HtmlScan {
    val VIDEO = Regex("""(?is)<video\b([^>]*)>(.*?)</video\s*>""")
    val SOURCE = Regex("""(?is)<source\b([^>]*)>""")
    val META = Regex("""(?is)<meta\b([^>]*)>""")
    val SCRIPT = Regex("""(?is)<script\b([^>]*)>(.*?)</script\s*>""")
    private val ATTRIBUTE = Regex(
        """([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""",
    )

    fun attributes(tag: String): Map<String, String> =
        ATTRIBUTE.findAll(tag).associate { match ->
            match.groupValues[1].lowercase() to
                match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
        }

    /** The balanced `{...}` object right after [assignment], or null when it does not close. */
    fun assignedObject(html: String, assignment: Regex): String? {
        val begin = assignment.find(html)?.range?.last?.plus(1) ?: return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in begin until html.length) {
            val character = html[index]
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = false
                }
            } else {
                when (character) {
                    '"' -> quoted = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return html.substring(begin, index + 1)
                    }
                }
            }
        }
        return null
    }
}
