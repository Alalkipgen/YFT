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

## Fonts and icons

| Component | Version | License | Source |
| --- | --- | --- | --- |
| Plus Jakarta Sans (`app/src/main/res/font/plus_jakarta_sans_*.ttf`) | 2.071 | SIL Open Font License 1.1, Copyright 2020 The Plus Jakarta Sans Project Authors | <https://github.com/tokotype/PlusJakartaSans> |
| Material Symbols (Rounded, `app/src/main/res/drawable/ic_*.xml`) | master 737e332 | Apache License 2.0, Copyright Google LLC | <https://github.com/google/material-design-icons> |

- Plus Jakarta Sans: the Regular, Medium, SemiBold and Bold static TTFs from the upstream
  repository, subset to Latin with fontTools
  (`pyftsubset --unicodes="U+0000-00FF,U+0131,U+0152-0153,U+02BB-02BC,U+02C6,U+02DA,U+02DC,U+0304,U+0308,U+0329,U+2000-206F,U+2074,U+20AC,U+2122,U+2190-2193,U+2212,U+2215,U+FEFF,U+FFFD" --layout-features='kern,liga,calt,tnum,lnum,pnum,case' --name-IDs='*'`).
  The font declares no Reserved Font Name; the license text is shown in the app (About →
  Licenses) and reproduced below.
- Material Symbols: Android vector drawables from `symbols/android/<name>/materialsymbolsrounded/`
  of commit `737e3324305806514d7909874fa1818ae1808232`. Change made: the
  `android:tint="?attr/colorControlNormal"` attribute was removed so the app tints them at draw
  time.

### Plus Jakarta Sans — SIL Open Font License 1.1

```text
Copyright 2020 The Plus Jakarta Sans Project Authors (https://github.com/tokotype/PlusJakartaSans)

This Font Software is licensed under the SIL Open Font License, Version 1.1.
This license is copied below, and is also available with a FAQ at:
http://scripts.sil.org/OFL


-----------------------------------------------------------
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------

PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded,
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

DEFINITIONS
"Font Software" refers to the set of files released by the Copyright
Holder(s) under this license and clearly marked as such. This may
include source files, build scripts and documentation.

"Reserved Font Name" refers to any names specified as such after the
copyright statement(s).

"Original Version" refers to the collection of Font Software components as
distributed by the Copyright Holder(s).

"Modified Version" refers to any derivative made by adding to, deleting,
or substituting -- in part or in whole -- any of the components of the
Original Version, by changing formats or by porting the Font Software to a
new environment.

"Author" refers to any designer, engineer, programmer, technical
writer or other person who contributed to the Font Software.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Original or Modified Versions, may be sold by itself.

2) Original or Modified Versions of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless explicit written permission is granted by the corresponding
Copyright Holder. This restriction only applies to the primary font name as
presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```

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
