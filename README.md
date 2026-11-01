# YFT — Video Downloader

YFT ("Video Downloader") is an ad-free Android app. It finds, previews and downloads the videos and
audio a user can watch: direct files, HTML5 players, HLS and DASH streams, and videos on YouTube,
Facebook, Instagram, TikTok, X, Vimeo and VK. It never breaks DRM.

## Status

- 1.0.1 is the newest release (2026-10-29). 1.0.0 (2026-10-26) was the first stable release.
- 1.0.2 is in work on `work/phase-v102-easy`. Its changes are in [CHANGELOG.md](CHANGELOG.md). The
  steps to release it are in [docs/PLAN.md](docs/PLAN.md).
- YFT is shared through GitHub Releases only.

## Install

YFT needs Android 7.0 (API 24) or newer. It targets Android 15 (API 35).

1. Download `video-downloader-<version>.apk` and `SHA256SUMS` from the repository's Releases page.
2. Check the file with `sha256sum -c SHA256SUMS` (on Windows, `Get-FileHash`).
3. Open the APK on the phone. Allow your browser or file manager to install apps when Android asks.

Later releases signed with the same key install over it. YFT can also update itself in Settings →
About → Updates. Release notes are in [docs/release/](docs/release/).

Test builds: the "Preview APK (test key)" workflow makes "YFT Preview" (`com.alal.yft.preview`) and
"YFT Trial" (`com.alal.yft.trial`). They install beside the release app. Each run signs with a new
test key, so uninstall an older preview first. See [docs/RELEASE.md](docs/RELEASE.md).

## What YFT does

- An in-app browser with a Download button for the page's video, or the video on screen in a feed.
- Home: paste a link, or share one from another app, and its Download sheet opens.
- Site adapters for YouTube, Facebook, Instagram, TikTok, X, Vimeo and VK. The Page video finder
  covers other sites.
- The Download sheet lists real qualities with sizes, plus Audio (M4A and MP3).
- Downloads run in the background with pause, resume, Retry and Fresh link renewal. Separate video
  and sound tracks are merged on the phone.
- The Backup finder (Master) looks again when a site's own lookup finds no video.
- A Library that plays, shares and deletes saved files, a save folder you choose, and in-app update.

What works on each site: [docs/SUPPORT_MATRIX.md](docs/SUPPORT_MATRIX.md).

## Boundaries

YFT does not bypass DRM, payment, private access, age checks or account sign-in. For public videos
the owner allows any method that works: app or device client identities, proof-of-origin tokens,
bot-check workarounds and the user's own browser session ([docs/DECISIONS.md](docs/DECISIONS.md)).
Site support is claimed only when tests back it. A `blob:` address is never a file: YFT finds the
real request, source or manifest behind it.

## Documents

| Document | What it holds |
| --- | --- |
| [AGENTS.md](AGENTS.md) | Rules for agents who work in this repository |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, data flow, main and Master, build flags, security |
| [docs/DESIGN.md](docs/DESIGN.md) | The design system and the rules the screens follow |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Decisions already made |
| [docs/PLAN.md](docs/PLAN.md) | What is open: release steps, owner decisions, known issues, backlog, risks |
| [docs/TESTING.md](docs/TESTING.md) | Test commands, CI workflows, phone tools, runbooks |
| [docs/RELEASE.md](docs/RELEASE.md) | Release identity, signing, preview builds, publishing |
| [docs/SUPPORT_MATRIX.md](docs/SUPPORT_MATRIX.md) | What each site and feature supports |
| [docs/GLOSSARY.md](docs/GLOSSARY.md) | Feature names, and old work labels mapped to them |
| [docs/SESSION_STATE.md](docs/SESSION_STATE.md) | Where the current work stands |
| [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md) | Third-party code and licenses |
| [CHANGELOG.md](CHANGELOG.md) | Changes per version |
| [extractor-master](extractor-master/README.md), [extractor-master-android](extractor-master-android/README.md) | Master's two modules |

## Build and test

You need JDK 17 and Android SDK 35.

```bash
./gradlew lintDebug testDebugUnitTest :core-model:test :extractor-api:test \
  :extractor-generic:test :extractor-sites:test :extractor-master:test :app:assembleDebug
```

`./gradlew :app:assembleRelease` builds `app-release-unsigned.apk` unless release signing is set up
([docs/RELEASE.md](docs/RELEASE.md)). More commands and the CI workflows are in
[docs/TESTING.md](docs/TESTING.md). On a machine with about 4 GiB of RAM and no swap, run the tests
and `:app:assembleRelease` as separate Gradle runs. `spikes/phase0-media/` holds the early
feasibility harness.

## Reference

[AlalDownloader](https://github.com/Alalkipgen/AlalDownloader) (MIT) is a reference for browser
request context, segmented downloads, queueing, pause, resume and recovery. YFT does not depend on
it at run time.

## License

MIT. See [LICENSE](LICENSE).
