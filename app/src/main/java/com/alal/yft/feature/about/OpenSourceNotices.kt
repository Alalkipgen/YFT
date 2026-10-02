package com.alal.yft.feature.about

/** Third-party code that ships inside the app, with the notice its license asks for. */
data class OpenSourceNotice(
    val id: String,
    val name: String,
    val version: String?,
    val license: String,
    val usage: String,
    val noticeText: String,
)

/**
 * Third-party notices shown on the About screen. Bundled entries mirror
 * `docs/THIRD_PARTY_NOTICES.md` (a unit test keeps the two in step); library entries summarise
 * the Gradle dependencies, which all use the Apache License 2.0.
 */
object OpenSourceNotices {
    val bundled: List<OpenSourceNotice> = listOf(
        OpenSourceNotice(
            id = "yt-dlp-ejs",
            name = "yt-dlp ejs",
            version = "0.8.0",
            license = "Unlicense",
            usage = "Player-script solver used only by the YouTube adapter.",
            noticeText = UNLICENSE,
        ),
        OpenSourceNotice(
            id = "meriyah",
            name = "meriyah",
            version = "6.1.4",
            license = "ISC",
            usage = "JavaScript parser bundled inside the solver.",
            noticeText = MERIYAH_ISC,
        ),
        OpenSourceNotice(
            id = "astring",
            name = "astring",
            version = "1.9.0",
            license = "MIT",
            usage = "JavaScript code generator bundled inside the solver.",
            noticeText = ASTRING_MIT,
        ),
    )

    val libraries: List<OpenSourceNotice> = listOf(
        apache("androidx", "AndroidX", "Core, Activity, Lifecycle, Navigation, Room, DataStore."),
        apache("compose", "Jetpack Compose and Material 3", "User interface toolkit."),
        apache("media3", "AndroidX Media3", "ExoPlayer playback, HLS and DASH support."),
        apache("okhttp", "OkHttp", "HTTPS client for probes and downloads."),
        apache("hilt", "Dagger Hilt", "Dependency injection."),
        apache("kotlin", "Kotlin and kotlinx.coroutines", "Language runtime and coroutines."),
    )

    val all: List<OpenSourceNotice> get() = bundled + libraries

    private fun apache(id: String, name: String, usage: String) = OpenSourceNotice(
        id = id,
        name = name,
        version = null,
        license = "Apache License 2.0",
        usage = usage,
        noticeText = APACHE_2,
    )
}

private const val UNLICENSE =
    "This is free and unencumbered software released into the public domain.\n\n" +
        "Anyone is free to copy, modify, publish, use, compile, sell, or distribute this " +
        "software, either in source code form or as a compiled binary, for any purpose, " +
        "commercial or non-commercial, and by any means.\n\n" +
        "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED. " +
        "For more information, please refer to <https://unlicense.org>"

private const val MERIYAH_ISC =
    "ISC License\n\nCopyright (c) 2019 and later, KFlash and others.\n\n" +
        "Permission to use, copy, modify, and/or distribute this software for any purpose with " +
        "or without fee is hereby granted, provided that the above copyright notice and this " +
        "permission notice appear in all copies.\n\n" +
        "THE SOFTWARE IS PROVIDED \"AS IS\" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH " +
        "REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND " +
        "FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR " +
        "CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR " +
        "PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, " +
        "ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE."

private const val ASTRING_MIT =
    "MIT License\n\nCopyright (c) 2015, David Bonnet <david@bonnet.cc>\n\n" +
        "Permission is hereby granted, free of charge, to any person obtaining a copy of this " +
        "software and associated documentation files (the \"Software\"), to deal in the " +
        "Software without restriction, including without limitation the rights to use, copy, " +
        "modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, " +
        "and to permit persons to whom the Software is furnished to do so, subject to the " +
        "following conditions:\n\n" +
        "The above copyright notice and this permission notice shall be included in all " +
        "copies or substantial portions of the Software.\n\n" +
        "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, " +
        "INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A " +
        "PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT " +
        "HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF " +
        "CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE " +
        "OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."

private const val APACHE_2 =
    "Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use these " +
        "files except in compliance with the License. You may obtain a copy of the License at " +
        "https://www.apache.org/licenses/LICENSE-2.0\n\n" +
        "Unless required by applicable law or agreed to in writing, software distributed under " +
        "the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF " +
        "ANY KIND, either express or implied. See the License for the specific language " +
        "governing permissions and limitations under the License."
