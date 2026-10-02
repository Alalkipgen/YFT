# Third-Party Notices

This file lists third-party code that is committed to this repository and shipped inside the
app. Libraries resolved by Gradle (AndroidX, Jetpack Compose, Media3, OkHttp, Hilt, Room,
kotlinx) are declared in `gradle/libs.versions.toml` and remain under their own licenses.

## YouTube player-script solver

Bundled in `app/src/main/assets/youtube-solver/` and used only by the YouTube adapter
([ADR-005](decisions/ADR-005-youtube-owner-override.md)). The two solver files are copied
unmodified from the published wheel by `scripts/update-youtube-solver.sh`; their license headers
are part of the shipped files.

| Component | Version | License | Source |
| --- | --- | --- | --- |
| yt-dlp ejs (`yt.solver.core.min.js`, `yt.solver.lib.min.js`) | 0.8.0 | Unlicense | <https://github.com/yt-dlp/ejs> |
| meriyah (bundled inside `yt.solver.lib.min.js`) | 6.1.4 | ISC, Copyright (c) 2019 and later, KFlash and others | <https://github.com/meriyah/meriyah> |
| astring (bundled inside `yt.solver.lib.min.js`) | 1.9.0 | MIT, Copyright (c) 2015, David Bonnet | <https://github.com/davidbonnet/astring> |

Pinned hashes (SHA-256):

| File | SHA-256 |
| --- | --- |
| `yt_dlp_ejs-0.8.0-py3-none-any.whl` (PyPI) | `79300e5fca7f937a1eeede11f0456862c1b41107ce1d726871e0207424f4bdb4` |
| `yt.solver.core.min.js` | `18da6ce0758b416e7ae645084f4f8801f9f9d59d6c477c05eaa0ff94ebd8cc00` |
| `yt.solver.lib.min.js` | `c55987fe697e5b9ee18830163f7af85327e9bb5c3e674b969d38c8d205eaa577` |

`solver.html`, `solver-page.js` and `solver-worker.js` in the same directory are YFT's own code.

`scripts/youtube-solver-vectors.json` contains public test vectors taken from the yt-dlp ejs test
suite (Unlicense) and is used only by `scripts/verify-youtube-solver.mjs`; it is not shipped.

### meriyah — ISC License

Copyright (c) 2019 and later, KFlash and others.

Permission to use, copy, modify, and/or distribute this software for any purpose with or without
fee is hereby granted, provided that the above copyright notice and this permission notice appear
in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS
SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE
AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE
OF THIS SOFTWARE.

### astring — MIT License

Copyright (c) 2015, David Bonnet <david@bonnet.cc>

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT
OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
