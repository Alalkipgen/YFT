package com.alal.yft.feature.about

/** Third-party code that ships inside the app, with the notice its license asks for. */
data class OpenSourceNotice(
    val id: String,
    val name: String,
    val version: String?,
    val license: String,
    val usage: String,
    val noticeText: String,
    /** An app asset with the full license text, shown after [noticeText]. */
    val noticeAsset: String? = null,
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
        OpenSourceNotice(
            id = "plus-jakarta-sans",
            name = "Plus Jakarta Sans",
            version = "2.071",
            license = "SIL Open Font License 1.1",
            usage = "The app typeface: Latin subsets of four weights.",
            noticeText = PLUS_JAKARTA_SANS_OFL,
        ),
        OpenSourceNotice(
            id = "material-symbols",
            name = "Material Symbols",
            version = "master 737e332",
            license = "Apache License 2.0",
            usage = "Rounded interface icons, as Android vector drawables.",
            noticeText = "Copyright Google LLC.\n\n$APACHE_2",
        ),
        OpenSourceNotice(
            id = "simple-icons",
            name = "Simple Icons",
            version = "16.34.0",
            license = "CC0-1.0",
            usage = "Logos of YouTube, Facebook, TikTok, Instagram and X in Your sites.",
            noticeText = SIMPLE_ICONS_CC0,
        ),
        OpenSourceNotice(
            id = "lame",
            name = "LAME",
            version = "3.100",
            license = "LGPL-2.0-or-later",
            usage = "MP3 encoder: converts M4A audio to MP3 on the phone (libmp3lame.so).",
            noticeText = LAME_LGPL,
            noticeAsset = LAME_LICENSE_ASSET,
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

const val LAME_LICENSE_ASSET = "licenses/LAME-COPYING.txt"

private const val LAME_LGPL =
    "LAME 3.100. Copyright (c) 1999-2017 Mark Taylor, Takehiro Tominaga, Robert Hegemann, " +
        "Gabriel Bouvigne, Alexander Leidinger and the other LAME authors.\n\n" +
        "This library is free software; you can redistribute it and/or modify it under the " +
        "terms of the GNU Library General Public License as published by the Free Software " +
        "Foundation; either version 2 of the License, or (at your option) any later version. " +
        "It is distributed WITHOUT ANY WARRANTY; without even the implied warranty of " +
        "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.\n\n" +
        "YFT uses LAME unmodified, built from source as its own shared library, " +
        "libmp3lame.so, which you may replace with your own build. Source: " +
        "https://lame.sourceforge.io (lame-3.100.tar.gz); the exact files YFT builds are in " +
        "its repository under core-download/src/main/cpp/lame-3.100. The full license text " +
        "follows."

private const val SIMPLE_ICONS_CC0 =
    "Simple Icons by the Simple Icons contributors, dedicated to the public domain under " +
        "CC0 1.0 Universal: <https://creativecommons.org/publicdomain/zero/1.0/>.\n\n" +
        "Site names and logos belong to their owners; YFT is not affiliated with them."

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

private const val PLUS_JAKARTA_SANS_OFL =
    "Copyright 2020 The Plus Jakarta Sans Project Authors " +
        "(https://github.com/tokotype/PlusJakartaSans)\n\n" +
        "This Font Software is licensed under the SIL Open Font License, Version 1.1. This " +
        "license is copied below, and is also available with a FAQ at: " +
        "http://scripts.sil.org/OFL\n\n" +
        "SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007\n\n" +
        "PREAMBLE\n\n" +
        "The goals of the Open Font License (OFL) are to stimulate worldwide development of " +
        "collaborative font projects, to support the font creation efforts of academic and " +
        "linguistic communities, and to provide a free and open framework in which fonts may " +
        "be shared and improved in partnership with others.\n\n" +
        "The OFL allows the licensed fonts to be used, studied, modified and redistributed " +
        "freely as long as they are not sold by themselves. The fonts, including any " +
        "derivative works, can be bundled, embedded, redistributed and/or sold with any " +
        "software provided that any reserved names are not used by derivative works. The " +
        "fonts and derivatives, however, cannot be released under any other type of license. " +
        "The requirement for fonts to remain under this license does not apply to any " +
        "document created using the fonts or their derivatives.\n\n" +
        "DEFINITIONS\n\n" +
        "\"Font Software\" refers to the set of files released by the Copyright Holder(s) " +
        "under this license and clearly marked as such. This may include source files, build " +
        "scripts and documentation.\n\n" +
        "\"Reserved Font Name\" refers to any names specified as such after the copyright " +
        "statement(s).\n\n" +
        "\"Original Version\" refers to the collection of Font Software components as " +
        "distributed by the Copyright Holder(s).\n\n" +
        "\"Modified Version\" refers to any derivative made by adding to, deleting, or " +
        "substituting -- in part or in whole -- any of the components of the Original " +
        "Version, by changing formats or by porting the Font Software to a new environment.\n\n" +
        "\"Author\" refers to any designer, engineer, programmer, technical writer or other " +
        "person who contributed to the Font Software.\n\n" +
        "PERMISSION & CONDITIONS\n\n" +
        "Permission is hereby granted, free of charge, to any person obtaining a copy of the " +
        "Font Software, to use, study, copy, merge, embed, modify, redistribute, and sell " +
        "modified and unmodified copies of the Font Software, subject to the following " +
        "conditions:\n\n" +
        "1) Neither the Font Software nor any of its individual components, in Original or " +
        "Modified Versions, may be sold by itself.\n\n" +
        "2) Original or Modified Versions of the Font Software may be bundled, redistributed " +
        "and/or sold with any software, provided that each copy contains the above copyright " +
        "notice and this license. These can be included either as stand-alone text files, " +
        "human-readable headers or in the appropriate machine-readable metadata fields " +
        "within text or binary files as long as those fields can be easily viewed by the " +
        "user.\n\n" +
        "3) No Modified Version of the Font Software may use the Reserved Font Name(s) " +
        "unless explicit written permission is granted by the corresponding Copyright " +
        "Holder. This restriction only applies to the primary font name as presented to the " +
        "users.\n\n" +
        "4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font Software " +
        "shall not be used to promote, endorse or advertise any Modified Version, except to " +
        "acknowledge the contribution(s) of the Copyright Holder(s) and the Author(s) or " +
        "with their explicit written permission.\n\n" +
        "5) The Font Software, modified or unmodified, in part or in whole, must be " +
        "distributed entirely under this license, and must not be distributed under any " +
        "other license. The requirement for fonts to remain under this license does not " +
        "apply to any document created using the Font Software.\n\n" +
        "TERMINATION\n\n" +
        "This license becomes null and void if any of the above conditions are not met.\n\n" +
        "DISCLAIMER\n\n" +
        "THE FONT SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR " +
        "IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF MERCHANTABILITY, FITNESS " +
        "FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF COPYRIGHT, PATENT, TRADEMARK, OR " +
        "OTHER RIGHT. IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, " +
        "DAMAGES OR OTHER LIABILITY, INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, " +
        "OR CONSEQUENTIAL DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, " +
        "ARISING FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM\n\n" +
        "OTHER DEALINGS IN THE FONT SOFTWARE."
