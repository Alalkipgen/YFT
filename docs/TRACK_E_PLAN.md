# Track E — a file's link dies after its download started: a fresh lookup, every site (v1.0.2)

Status: E0 done (§4.5); the owner's answers 2026-10-27 (§3); E1 done (§4.6: `core-download` and
the new status's app plumbing; CI per checkpoint). Next: E2.
Planning: the owner's go, 2026-10-27, while S5's run 3 waits. Branch
`work/phase-track-e-403` from `main` `a27b2c9c` (PR #8's merge: the code of S5's census APK).
E1's code is on this branch; the app does not renew links until E2 wires a source. **Not for
v1.0.1:** nothing here reaches `main`, and
no APK from this branch reaches the owner's phone, before v1.0.1 is published.

## 0. အကျဉ်းချုပ် (ပိုင်ရှင်အတွက်)

Download စပြီးမှ ဖိုင်ရဲ့ link သေသွားရင် (401၊ 403၊ 410၊ link သက်တမ်းကုန်၊ VK ရဲ့ 400) YFT က site ကို
တစ်ကြိမ် ပြန်မေးပြီး (fresh lookup) **ရွေးထားတဲ့ video နဲ့ quality အတိုင်း** link အသစ်ယူကာ download ကို
ဆက်လုပ်ပါမယ်။ ဖိုင်တူကြောင်း သေချာမှ ရပ်ခဲ့တဲ့နေရာက ဆက်၊ မသေချာရင် 0 ကနေ ပြန်စ။ မရရင် ယနေ့လို
"Link expired" ပဲပြပြီး ဘာကြောင့်လဲဆိုတာ Details မှာ ပြပါမယ်။

- **v1.0.1 ကို မထိပါ:** `main`၊ wire branch၊ `recipes`၊ tag၊ version၊ census APK နဲ့ ဖုန်း — ဘာမှ မပြောင်းပါ။
  S5 run 3 ရဲ့ count ကို မထိခိုက်ပါ။ v1.0.1 publish ပြီးမှ `main` နဲ့ ပေါင်းပါမယ်။
- **Code ထဲမှာ ရှိပြီးသား:** engine တွေမှာ "ဖိုင်တူမှ ဆက်၊ မတူရင် 0 ကစ" စစ်တာ ရှိပြီးသား (Direct: size +
  ETag/Last-Modified၊ HLS/DASH: fingerprint)။ `DownloadQueue.refresh()` လည်း ရှိပြီးသား — **ဒါပေမဲ့ app
  ထဲမှာ ဘယ်နေရာကမှ မခေါ်ပါ။** ဒါကြောင့် အလုပ်အများစုက link အသစ်ကို ဘယ်ကယူမလဲ၊ quality တူကို ဘယ်လိုရှာမလဲ၊
  ဘယ်နှစ်ကြိမ်ထိ ကြိုးစားမလဲ၊ ကြားထဲမှာ ဘာပြမလဲ (UI၊ notification) ဆိုတဲ့ app ဘက်က အလုပ်ပါ။
- **Lookup code ကို မပြင်ပါ** (adapter၊ Master engine၊ ladder၊ recipes၊ YouTube ဖိုင်) — ခေါ်သုံးရုံပါပဲ။
  ဒါကြောင့် decision 26 အရ v1.0.2 အတွက် S5 ကို ထပ်လုပ်စရာ မလိုပါ (E-D7၊ ပိုင်ရှင်က အတည်ပြုရန်)။
- **အဆင့်:** E0 recon + design (docs၊ STOP) → E1 core-download → E2 app → E3 VK 400၊ adapter မရှိတဲ့ page
  နဲ့ trial hook (STOP: ဖုန်းစမ်းရန် အဆင်သင့်) → E4 v1.0.1 ထွက်ပြီးမှ `main` ပေါင်း၊ ဖုန်းစမ်း၊ v1.0.2။
- **ဆုံးဖြတ်ရန် (§3):** default တွေနဲ့ စလို့ရပါတယ်။ E0 report မှာ မြန်မာလို အတိုချုပ် ပြန်မေးပါမယ်။

## 1. Scope

In scope: downloads that are running or paused **in the same app session** on every site — the
seven adapter sites (YouTube, Facebook, Instagram, TikTok, X, VK, Vimeo) through the existing
lookup, and pages without an adapter through P37's quiet page re-read; every plan type (Direct,
HLS, DASH, the merged audio + video download) and the conversions (MP3, M4A from a video's sound);
pause, cancel, delete and network loss during a renewal; two or more downloads at once.

Out of scope (own decisions or backlog): recovery after the app's process died (E-D2), switching
to another quality (E-D4), any change to the lookup itself (adapters, the Master engine, ladders,
recipes, YouTube's modules and solver), live recordings (`LivePieces`/`LiveOriginFile`, unless E0
finds them on the same path at no extra cost), and the Downloads rows' order (§9).

## 2. What the code does today (read on `a27b2c9c`)

1. **The failure.** The engines map 401 → `AUTHENTICATION_REQUIRED`, 403 → `ACCESS_DENIED`,
   410 → `GONE` and a link past its stated expiry → `EXPIRED_URL` (`DirectTransferEngine`,
   `HlsTransferEngine`, `DashTransferEngine`, `LiveOriginFile`, `DirectRangeProbe`); a 400 is a
   generic `HTTP_STATUS`. `GoogleVideoLinkFacts.refusedWithTimeLeft` turns a YouTube 403 with time
   left on the link into `YOUTUBE_REFUSED` (item 7 Track B1).
2. **The queue.** `DownloadQueue.failTask`: a reason in `REFRESH_FAILURES` (`EXPIRED_URL`,
   `AUTHENTICATION_REQUIRED`, `ACCESS_DENIED`, `GONE`, `YOUTUBE_REFUSED`) → `NEEDS_REFRESH` with
   `requiresLinkRefresh`; the runtime (the plan's links, cookies and headers, and the destination
   object) is dropped, the checkpoint kept.
3. **A refresh API without a caller.** `DownloadQueue.refresh(id, plan, …)` exists for all four
   plan types and keeps the checkpoint when the plan type matches (tests: `DownloadQueueTest`
   "refresh after restore reuses checkpoint and can complete", `StreamDownloadQueueTest` "restored
   HLS checkpoint survives link refresh and completes"). Nothing under `app/` calls it.
4. **"The same file" is already checked by the engines.** Direct: `reconcileCheckpoint` keeps the
   bytes only when the total, the ETag, the Last-Modified and the segment layout all match, and
   starts from 0 when the saved checkpoint has neither validator. HLS: the manifest fingerprint
   (chunk addresses without their query, byte ranges); a mismatch clears the chunk files. DASH:
   the representation, the track type and the chunk addresses without their query; a track served
   as one file: the track and its length ("a refreshed address for the same file keeps the chunks
   already downloaded"). Merged: the two tracks' DASH checkpoints. P21's `mustStartOver` /
   `startOver` (a new destination and the dispatcher's workspace discarded) serve storage failures
   only.
5. **What the user sees.** The chip "Link expired" (or "YouTube refused"), the note "Reopen the
   page in the browser to get a fresh link.", the notification "Download failed · <title> — link
   expired", the Failed filter; Retry is offered only for `FAILED`, not for `NEEDS_REFRESH`
   (`DownloadsUiState`). `SiteLookupCache.forgetBrokenDownloads` drops the video's remembered
   answer (P17), so the next lookup asks the site.
6. **Nothing about the source is saved.** `DownloadRecordEntity` keeps no page address and no
   quality, by design (`DownloadQueue`'s doc: links, cookies and headers stay in memory); after the
   process dies every unfinished task becomes `NEEDS_REFRESH`.
7. **Where downloads are queued.** `DownloadEnqueuer.enqueue(asset, variant)` (the download sheet's
   `QuickDownloadViewModel`, on Home and in the browser, and `PreviewViewModel`) builds the plan
   with `DownloadPlanFactory.create`; the sheet also calls `SiteLookupCache.rememberDownload(taskId,
   videoId)`. The sheet already knows how to find the same video again (P29/P37/P45:
   `MediaGroups.refreshed`, never another video) and how to apply a conversion to a resolved file
   (`AudioFromVideo`, `Mp3Variants`).
8. **The lookup.** `SiteAdapterCoordinator.inspect(pageUrl, requestContext, now, fresh = true)`
   asks the site again (Home's order: the page read, then the hidden page); `identity`,
   `sameContent`. Pages without an adapter: P37's `BrowserPageReader.read(pageUrl, userAgent)`.
9. **YouTube.** F8: on YouTube's media hosts the DASH engine asks a whole file's 403'd range once
   more, 3 s later (`mediaHostRefusalWaitMillis` 5 s, the owner's trim of 2026-10-19). B1:
   `YOUTUBE_REFUSED`, its hint and "Reset YouTube session".
10. **VK.** `docs/VK_MASTER_CENSUS.md` §4: a file binds the agent (`srcAg`) and the IP (`srcIp`,
    signed); another agent, another IP or a tampered value → **400**, not 403; links last ~72 h;
    labels `vk: file 400 srcAg`, `vk: file 400 srcIp/sig`, `vk: file 206`.

## 3. Owner decisions (defaults; E0's report asks again, short, in Burmese)

| ID | Question | Default |
| --- | --- | --- |
| E-D1 | Which failures start a fresh lookup | 401, 403, 410 and an expired link, on every site; VK's media hosts' 400 only after that file already gave bytes. No other status (those keep Retry). |
| E-D2 | Also after the app was closed or killed (process death) | OFF for v1.0.2: it needs the page address and the quality saved on the phone (a database migration; Clear browsing data must wipe them). Backlog. |
| E-D3 | How many automatic tries | One per failure; another only after the fresh link wrote bytes; at most 3 per download. Then stop with the reason. |
| E-D4 | The chosen quality is not in the fresh answer | Stop and say so; never another quality without asking. |
| E-D5 | YouTube's "YouTube refused" (a 403 with time left) | No automatic lookup: B1's hint and Reset YouTube session stay as today. |
| E-D6 | Retry on a "Link expired" row in the same app session | ON: Retry runs the same fresh lookup once. |
| E-D7 | v1.0.2's phone gate | E changes no site's pack, not the Master engine and not main's lookup (only the download side: the queue, an engine's gap if E0 finds one, and the app), so no S5 count restarts (decision 26: "a change to a site's pack, the engine or main"); v1.0.2 gets §7's checklist, its previews and the signed install and upgrade (1.0.1 → 1.0.2), as every release. The owner confirms. |
| E-D8 | Pages without an adapter | One quiet page re-read (P37) for the same video; nothing fresh → today's note. |
| E-D9 | A trial-only "Break this download's link" action for the phone test | ON in the trial build only (the release verifier rejects its label elsewhere). |

**The owner's answers (2026-10-27): "Default အတိုင်း၊ E-D5' နဲ့၊ ဖိုင်တိုး OK — E1 စပါ".**

- Every default above, except E-D5, which becomes **E-D5'** (§4.5's proposal) with these limits:
  "the network changed" is read only from the app's own `NetworkMonitor` /
  `NetworkStatusSource.snapshot` (Wi-Fi ↔ mobile data, or it dropped and came back), never an IP
  check; the lookup counts within E-D3's three; refused again → stop exactly as B1; at most one
  `YOUTUBE_REFUSED` renewal per download; F8 unchanged.
- E-D6 as §4.5 clarifies (one lookup per tap, not counted in the three, only while the app has the
  row's source). E-D7 confirmed; also locked: `core-media`'s resolver and parsers
  (`DefaultVariantResolver`, `HlsManifestParser`, `DashManifestParser`).
- §4.5's extra files OK: `QuickDownloadViewModel.kt` and `PreviewViewModel.kt` only to pass the
  source to the enqueue (their `inspect` calls, `SiteLookupCache` and the sheet's options stay;
  P17, P29, P37 and P45's tests stay green); `DownloadNotificationFactory.kt`, `BackgroundWork.kt`,
  `SettingsModule.kt`.
- G2 extended: a "Link expired" row without a renewal source (`None`) also keeps its destination
  (only that: no link, no cookie), so its Cancel and Delete clean the partial file and the
  workspace in this app session (done in E1).
- The rest as §6: a checkpoint after each milestone with CI green; after E3 STOP ("READY FOR PHONE
  TEST (after v1.0.1)"); no APK to the owner before v1.0.1 is published.

## 4. Design (proposed; E0 confirms it against the code or corrects it here)

### 4.1 Who does what

- **core-download decides when and how:** whether a failure qualifies (E-D1 as data — reason,
  status, stage, bytes before it — never a site's code), the task's state, the cap (E-D3), the
  swap of the runtime, resume or start over, the cleanup, the races. It gets one interface the app
  implements (names are E0's): `DownloadLinkRenewal { suspend fun renew(request): Renewal }`,
  `Renewal.Fresh(plan, metadata?)` / `Renewal.Unavailable(reason)` / `Renewal.Declined`. The
  default `None` keeps today's behaviour exactly. The request carries no link: the task id, the
  plan type, the failure (reason, status, stage) and the bytes kept.
- **The app decides where the fresh link comes from:** it remembers each queued task's source,
  asks the site again, finds the same video and quality, builds the plan (same task id, same file
  name) — and declines what is not a link failure for that site (a 400 off VK's media hosts).

### 4.2 The queue

1. A qualifying failure of a task with a renewal source, within the cap → keep the runtime; the
   task shows a renewing state (a new status such as `RENEWING_LINK`, or E0's alternative); the
   renewal runs outside the queue's lock as its own job, within a time limit (E0 proposes one).
   The task keeps its place: it goes on first once its fresh link comes (E1 decides whether it
   holds its download slot meanwhile).
2. `Fresh` → `refreshRuntime` with the new plan (same id, file name and destination); the engine's
   same-file check (§2.4) decides resume or byte 0. Where E0 finds an engine that does not clear
   what it no longer keeps, the queue calls `startOver` first.
3. `Unavailable`, a failed lookup or the time limit → `NEEDS_REFRESH` as today, plus one Details
   line ("Fresh link: <why>"); `Declined` → today's path for that failure.
4. Pause, cancel, delete and network loss cancel the renewal (pause → paused; network back → the
   same try again). A renewal's own failure is never renewed; no loop (E-D3).
5. A new status touches (E0 lists them all): `DownloadTaskStatus`; Room keeps it as text (no
   migration; `restoreLocked` turns it into `NEEDS_REFRESH` like every unfinished status); the
   Downloads chips, filters and badge; `DownloadServiceController` (the service stays up while
   renewing); `DownloadNotificationText`; the policy gate.

### 4.3 The app's renewal source

- At enqueue, in `DownloadEnqueuer` (every path), remember in memory: the page address, the site
  and content identity, whether the lookup used the session, the chosen quality's key, the
  conversion (MP3 kbps, M4A from a video, merge), the stated length and the title. Dropped when the
  task completes, is cancelled or deleted, and by Clear browsing data (`PrivacyCleaners`); its
  `toString` names no address. Nothing on disk (E-D2).
- Renew: adapter sites → `SiteAdapterCoordinator.inspect(…, fresh = true)` with the same kind of
  request context; pages without an adapter → `BrowserPageReader.read` (E-D8). Then the same
  video (`sameContent`, `MediaGroups.refreshed`, its length within 2 s), `VariantResolver`, the
  same quality, the same conversion, `DownloadPlanFactory.create(taskId = the same)`, the file
  name kept; a Direct plan gets fresh metadata from the existing `DirectMetadataProbe`.
- The quality's key: E0 checks per adapter whether `MediaVariant.id` stays the same across two
  lookups of one video; otherwise kind, track type, height (and width), frame rate, container,
  codecs, the merge's sound codec, the MP3 kbps or M4A. No match → `Unavailable(quality gone)`.
- Cost: one normal lookup (the sites' existing request, byte and time budgets and cooldowns
  apply); the download's network policy (Wi-Fi only) applies to the renewal too.

### 4.4 What the user sees (English app text; E2 settles the words)

- Renewing: the chip "Getting a fresh link…"; no "Download failed" notification meanwhile.
- Success: the row goes on; Details "Fresh link (403 at 41 MB): resumed at 41 MB" or "…: started
  over (the file changed)".
- Failure: today's chip and note, plus a Details line ("Fresh link: this quality is no longer
  offered", "Fresh link: the site did not answer (…)"). Never an address, query, cookie, token
  or IP.

### 4.5 E0 result

Read on `a27b2c9c`, docs only. No build or test ran here (this sandbox has no Android SDK; E1's
pushes get CI). No live check (Q2's is optional; the census already has VK's answers).

**§2 checked: true, with these corrections and additions.**

- **2.1** Every HTTP-status failure has `stage = CONNECT` and keeps `httpStatusCode`, the first
  request's and a later range's alike: only the checkpoint's bytes say "in the middle". Also
  404 → `NOT_FOUND`, 416 → `RANGE_NOT_SATISFIABLE` (Direct), 5xx → `SERVER_ERROR` (asked again
  inside the engine), any other → `HTTP_STATUS` with its code (VK's 400 is `HTTP_STATUS` + 400).
  `EXPIRED_URL` is each engine's check of the plan's `expiresAtEpochMs` before any request (Direct,
  HLS, DASH, merge). `SecureDownloadHttp`'s own refusals (an unsafe redirect, too many) fail at
  CONNECT without a status. `refusedWithTimeLeft` (403, CONNECT, `googlevideo.com`, ≥ 1 min left on
  every link and on the plan) turns a 403 in the middle of a file into `YOUTUBE_REFUSED` too.
- **2.2** The engine's failure checkpoint replaces the stored one (`checkpoint =
  result.checkpoint`; see G1). Other reasons → `FAILED` with the runtime kept (Retry asks the same
  link again); an exception → `FAILED` / `NETWORK`.
- **2.3** `refreshRuntime` puts the task at the end of `pending`, not first, and does not check for
  a running job (G4).
- **2.4** Direct also needs a resumable answer (ranges and a total) and segments that fit the
  partial file; a mismatch rewrites every segment from byte 0 (the ranged path sets the length
  again, the plain one calls `resetDestination`); changed validators in a response →
  `INTEGRITY_MISMATCH`. DASH: a whole-file track is known by its track type and exact length (and
  the chunk size; merged plans name the representation "video" / "audio"); the length is known
  up front only on `googlevideo.com` (`clen`), else from a probe or the first range. Merge: a
  finished track (`videoReady` / `audioReady`, its file not empty) is kept with no check against
  the new plan (G3); an unfinished one follows DASH (HLS tracks: `PlaylistTrackTransferRunner`).
  MP3 / M4A: the source is a Direct download into the workspace, resumed by Direct's rules; a
  finished source is kept by its length alone, then converted.
- **2.5** Retry and Resume both call `queue.resume` and start the service; `availableActions` gives
  Retry for `FAILED` only. The notice is `DownloadServiceController.noticeEnded`: a task it saw
  run leaves `FOREGROUND_STATUSES` into `COMPLETED`, `FAILED` or `NEEDS_REFRESH` (G5).
- **2.6** Also: the status is stored by name, an unknown name reads back as `FAILED`
  (`DownloadTaskStore`); no column or check lists the statuses (migration 2→3's list is history).
- **2.7** Home: `HeadlessLinkInspector` → `inspect(url, BrowserRequestContext(url,
  HeadlessIdentity.USER_AGENT, cookie = TikTok's or Instagram's Home session only), fresh)`,
  90 s. The browser: `BrowserViewModel.lookUp` → `inspect(url, the tab's context (its agent, the
  site's cookie), tab = tabData)`, then `BrowserMasterFallback.recover` when main fails —
  browser-only (its capture plays the video in the tab). `PreviewViewModel` (the preview screen)
  enqueues an asset without the lookup's facts. `SheetOption.id` is "s<index>-<variant id>".
- **2.8** `SiteLookupKey.session` = the context has a cookie; `fresh = true` forgets the video's
  entries (with and without the session) first. Without `tab` the order is Home's: the adapter's
  page read, then the hidden page (`TikTokPageEngine`).
- **2.9** F8 confirmed (`MediaHostRefusals`): a whole-file DASH track on `googlevideo.com` asks a
  403'd range once more, 3 s later; refused again, the file fails (`ACCESS_DENIED` 403, so
  `YOUTUBE_REFUSED` with time left).
- **2.10** Confirmed (`docs/VK_MASTER_CENSUS.md` §4).

**Gaps (E1 fixes G1–G4 in `core-download`, E2 fixes G5 in `app`):**

- **G1 — the resume point is lost before the engine reads it.** A failure returned before an
  engine reads the saved state carries its empty checkpoint: the `EXPIRED_URL` pre-check (all four
  engines), `INVALID_URL`, Direct's stated-length mismatch, HLS / DASH failures while fetching the
  playlist or manifest, DASH's length probe or first range of a whole file. `failTask` stores it:
  Direct starts again at 0, HLS / DASH clear their chunks on the next run. It hits §7.3 (Resume
  after expiry) and every renewal after a manifest 403. Fix: on a failure that goes to a renewal,
  an untouched empty checkpoint (no segments; no fingerprint and no chunks; no tracks) never
  replaces the stored one. Safe: each engine checks the files again (Direct the validators and the
  partial length, HLS / DASH each chunk file's length).
- **G2 — no cleanup for a "Link expired" row.** `NEEDS_REFRESH` drops the runtime, so Cancel and
  Delete cannot discard the partial file or the workspace (only `DownloadStorageJanitor`, later).
  With a renewal source the runtime is kept, as `FAILED` keeps it today, so they clean up.
- **G3 — a merge keeps a finished track unchecked.** Fix: kept only when its saved DASH
  fingerprint (track type and length) matches the new plan's track, or one of them is unknown
  (today's rule); else cleared and downloaded again.
- **G4 — `refreshRuntime` re-queues at the end and ignores a running job.** The renewal does not
  use it (below); `refresh()` and its tests stay.
- **G5 — the service and the notice watch `FOREGROUND_STATUSES` only.** A new status outside it
  would stop the service and end the watch, so the final "Download failed" would never come: the
  new status joins that set.

**Answers to §6's questions:**

1. The same-file rules and their cleanup: 2.4 above. Proving tests: Direct "matching validator
   resumes only missing byte ranges", "a resume whose length read fails starts again at byte 0 and
   completes"; HLS "fingerprint ignores signed query changes but preserves chunk identity",
   "retryable failure checkpoint resumes without downloading completed chunk again"; DASH "whole
   file track resumes finished ranges from a refreshed address", "fingerprint ignores renewed
   signed queries", `DashFastStartLayoutTest`'s three layout tests; merge "retry resumes failed
   audio without downloading completed video again"; the queue's two refresh tests (§2.3).
   Missing (E1): after a refresh, Direct with another ETag or total → byte 0, the old bytes
   overwritten; HLS / DASH with another fingerprint → chunks cleared, the file right; a DASH whole
   file of another length; G3; an MP3 / M4A source; G1 per engine.
2. Every HTTP status reaches `failTask` at CONNECT (first request or a later range), so do the
   pre-checks and the redirect refusals; `HTTP_STATUS` keeps its code; F8 ends in `ACCESS_DENIED`
   403. Expected answers (no live check): YouTube 403 (its links are bound to the IP), VK 400 or
   403 (census §4), the others 403 or 410; E3's tests use fakes.
3. `MediaVariant.id` is no quality key: the resolver numbers by position — "direct-0" for every
   file, "hls-stream-N", "hls-audio-N", "dash-<track>-N" by manifest order; only DASH's
   `manifestVariantId` (the representation's id) comes from the site. Each adapter gives one
   candidate per quality, with: YouTube height, width, fps, codecs, length, bitrate, the sound's
   codecs; Facebook and Instagram height, width, fps, codecs, bitrate, the sound; TikTok height,
   width, codecs, bitrate, length; X height, width, bitrate (MP4) or HLS; VK height (MP4) or
   HLS / DASH; Vimeo the rendition's label (in the title) and size, or HLS / DASH. So the key
   (`DownloadQualityKey`) is facts: kind, track type, height, width, frame rate, container, codecs
   and the sound's codecs, the bitrate where a site states it per file (X), Vimeo's label; then the
   conversion (MP3 kbps, M4A, merge). Two matches → the one with the stated length; none →
   `Unavailable` ("this quality is no longer offered"). No fixture has two lookups of one video:
   E2's tests run each adapter's fixture twice with fresh signatures.
4. Enqueue paths: the sheet (`QuickDownloadViewModel.enqueue`, Home's and the browser's,
   `LookupOwner`), knowing the candidate (page address, video id, the request context) and the
   shown page; `PreviewViewModel` the asset only. A renewal without the tab gets the same `session`
   when the browser's cookie store still has the site's cookies (Home's rule for Home's), and the
   adapter's page read without the tab's data: equal for YouTube, VK, Vimeo, X; an Instagram or
   TikTok post only the tab could read may fail → `Unavailable` (today's note). Never Master
   (browser-only, its capture needs the tab).
5. A new status `RENEWING_LINK` (not `PROBING` / `VERIFYING`: nothing sets them, their words are
   "Checking source" / "Verifying file"). It touches: `DownloadTaskStatus`; `DownloadQueue`
   (`failTask`, pause, cancel, delete, `setNetworkAvailable`, resume; restore already turns it into
   `NEEDS_REFRESH`); the store nothing (text); `DownloadsUiState` (active and pausable sets, the
   Active filter, the badge, Pause and Cancel); `DownloadLabels` (`PROGRESS_STATUSES`,
   `progressDetail`); `DownloadsScreen` (the row's `when`); `DownloadsViewModel` (Retry);
   `DownloadNotificationFactory.FOREGROUND_STATUSES` (G5); `DownloadNotificationText.stateText`;
   `BackgroundWork` (the locks while the lookup runs). Unchanged: `DownloadServiceController`'s
   notice set (no notice while renewing), the policy gate (it only calls `setNetworkAvailable`),
   `SiteLookupCache.forgetBrokenDownloads`, `DownloadStorageJanitor`, `TransferRateTracker`,
   `LibraryViewModel`.
6. `TabPageReader.read(pageUrl, userAgent)` needs no tab: one GET of the page with that agent and
   the browser cookie store's cookies for it, then the scanner, one read at a time. The source
   keeps the tab's agent.
7. The trial hook: Downloads ⋮ "Break this download's link" on a running or paused row, only when
   `BuildConfig.SOCIAL_TRIAL && !LocalQuietRelease.current` (as "VK value break").
   `SecureDownloadHttp`, which sends every engine's requests, tags each with
   `DownloadRequestTag(taskId, link)` from a context element the queue sets (no engine changes);
   in the trial build only, an interceptor on the download client answers every request of the
   broken task's current link with its own 403 (no network), so the next link passes. Marker: the
   label joins `TRIAL_TOOL_MARKERS`. On YouTube that 403 has time left: `YOUTUBE_REFUSED`,
   renewed only under E-D5' (below).

**Proposals for §3 (the owner decides):**

- **E-D5 conflicts with §7.2.** A Wi-Fi → mobile switch gives YouTube's 403 with time left
  (`YOUTUBE_REFUSED`), which E-D5's default never renews. **E-D5'**: one fresh lookup on "YouTube
  refused" only when the phone's network changed since the link was found (Wi-Fi ↔ mobile data, or
  it dropped and came back); otherwise exactly B1. F8 untouched.
- **E-D6 clarified:** Retry on a "Link expired" row = one lookup per tap, also after the
  automatic tries and not counted in them; offered only while the app has the row's source (this
  session); after a restart no Retry, as today.
- **E-D1 as data:** the queue asks on `EXPIRED_URL`, 401, 403, 410, `YOUTUBE_REFUSED`, and on a 400
  when this link already gave bytes; the app declines a 400 off VK and `YOUTUBE_REFUSED` without a
  network change. Never 404, 416, 5xx, network, storage, integrity.
- **E-D3 counted** per download in memory: the first failure asks; the next only after the fresh
  link gave bytes; 3 at most; a renewal's own failure ends it.

**Final names and shape:**

- `core-download`: `DownloadLinkRenewal { suspend fun renew(request: LinkRenewalRequest):
  LinkRenewal }`, `DownloadLinkRenewal.None` (always `Declined`: today's behaviour);
  `LinkRenewalRequest` (task id, plan type, the failure's reason, status and stage, the bytes kept,
  the bytes from this link, by the user or not; no link); `LinkRenewal.Fresh(plan, metadata?)`,
  `LinkRenewal.Unavailable(why)` (Details words, no address), `LinkRenewal.Declined`;
  `DownloadTaskStatus.RENEWING_LINK`; `DownloadRequestTag`.
- The queue: the renewal runs in the task's own job (`jobs[id]`), outside the lock. So it keeps its
  slot and goes on first by itself, and Pause, Cancel, Delete and network loss stop it as they stop
  a transfer (network back, Resume or Retry: the renewal first). `Fresh` swaps the runtime in place
  with `refreshRuntime`'s checks (not completed, the same plan type, the checkpoint kept) and runs
  the engine again; its same-file check decides resume or byte 0. Time limit: 90 s per renewal
  (Home's `LinkInspector.TIMEOUT_MILLIS`), the Direct probe included. Details: a failure's line in
  `failure.detail` (stored, sanitized); a success's in memory.
- `app`: `DownloadSources` (memory: one `DownloadSource` per task — page address, site and
  content id, the lookup's owner and agent, the session flag, `DownloadQualityKey`, the
  conversion, the stated length, the title, the network's change count; `toString` without an
  address; dropped on complete, cancel, delete and by Clear browsing data); `SiteLinkRenewal`
  (implements `DownloadLinkRenewal`, wired in `DownloadRuntimeModule` without a cycle through
  `DownloadEnqueuer`); `TrialLinkBreak` (E3).

**Files beyond §5's list (the owner's OK):** `feature/quickdownload/QuickDownloadViewModel.kt` and
`feature/preview/PreviewViewModel.kt` (pass the lookup's owner and video to the enqueue),
`download/DownloadNotificationFactory.kt` (G5), `download/BackgroundWork.kt` (the locks),
`feature/settings/SettingsModule.kt` (the wipe's wiring).

**Tests (JVM unit tests with fakes; CI runs them):**

- E1, `core-download`: (1) `None`: every failure as today (today's tests unchanged). (2) 401, 403,
  410, `EXPIRED_URL`, `YOUTUBE_REFUSED` → `RENEWING_LINK`, asked once, no link in the request; 400
  only with bytes from this link; 404, 416, 5xx, network, storage, integrity never. (3) `Fresh` per
  plan type (Direct, HLS, DASH, merge, MP3, M4A): resume for the same file, byte 0 for another
  (Direct ETag / total; HLS / DASH fingerprint; a DASH whole file's length; G3), the bytes right.
  (4) G1 per engine. (5) `Unavailable` → `NEEDS_REFRESH` with its line, the runtime kept;
  `Declined` → today's path; the time limit. (6) E-D3: refused at 0 bytes → no second ask; after
  bytes → asked again; 3 at most; Retry at the cap, not counted. (7) Pause, Cancel, Delete,
  network loss during a renewal (no file or workspace left after Cancel; a late `Fresh` dropped).
  (8) Two downloads, one renewing; capacity 1: the renewed one goes on first. (9) Restore:
  `RENEWING_LINK` → `NEEDS_REFRESH`. (10) A `Fresh` of another plan type → `NEEDS_REFRESH`.
  (11) `SecureDownloadHttp`'s tag.
- E2, `app`: (1) `DownloadSources`: every enqueue path; dropped on complete, cancel, delete, Clear
  browsing data (`PrivacyCleanersTest`); `toString` without an address. (2) `SiteLinkRenewal`:
  `fresh = true` with Home's or the browser's context (the same session flag); the same video only
  (`sameContent`, `MediaGroups.refreshed`); the same quality per adapter's fixture (all seven); the
  conversion kept; the same task id and file name; Direct's probe; nothing found → `Unavailable`.
  (3) Declines: no source, a 400 off VK, `YOUTUBE_REFUSED` without a network change. (4) Rows:
  "Getting a fresh link…", Pause and Cancel, the Active filter and badge, Retry on a renewable
  "Link expired" row only, the Details lines without an address. (5) No "Download failed" while
  renewing, one when it ends in `NEEDS_REFRESH`; the service stays up; Wi-Fi only → it waits.
- E3: (1) VK: 400 after bytes → renewed, at 0 bytes → `FAILED` as today; 403 → renewed. (2) Pages
  without an adapter: `BrowserPageReader.read` → the same video → `Fresh`; `NoPlayer` / `Failed` →
  `Unavailable`; a pasted file link → no source. (3) The trial hook: the trial build only; the
  broken link's requests only; the verifier rejects its label without `--allow-trial-tools`.
  (4) YouTube: F8's ask-again and B1's hint unchanged; Reset YouTube session unchanged.

### 4.6 E1 result

Built as §4.5 names it; what differs or was decided in the code:

- **The queue.** `DownloadQueue(linkRenewal = DownloadLinkRenewal.None, renewalTimeoutMillis =
  90 000)`. `failTask` decides: a link died (`REFRESH_FAILURES`, or `HTTP_STATUS` 400 after this
  link gave bytes) → G1's kept checkpoint; with a source, a task still `RUNNING` and E-D3 allowing
  it → `RENEWING_LINK`, and the task's own job asks (`renewLink`), outside the lock, then runs the
  engine again with the fresh runtime (link number + 1). A late answer (the task paused, cancelled,
  deleted or offline meanwhile) is dropped. `Fresh` of another task id or plan type → "another
  kind of file"; a Direct `Fresh` without metadata → "the file did not answer"; a Direct
  `Fresh` gives the task its probe's total. Unavailable, a thrown error or the 90 s limit →
  `NEEDS_REFRESH` with "Fresh link: <why>; <the failure's detail>" (sanitized).
- **E-D3 as counted:** at most 3 renewals without a tap; after a fresh link, another only when
  that link gave bytes (`LinkBytes`: the rises of the run's progress and checkpoints); a "YouTube
  refused" once per download (E-D5'; whether the network changed is the app's check, E2). A Retry
  (`resume` of a "Link expired" row that kept its ask) is one lookup, not counted.
- **G1:** only for a link that died: an engine's untouched, empty checkpoint (Direct without
  segments; HLS / DASH without fingerprint and chunks; a merge per track not ready and untouched)
  never replaces the stored one.
- **G2 (extended):** a "Link expired" row keeps a parked runtime: its destination and the plan's
  task id and options (the MP3 / M4A workspace, the merge's track ids), the link replaced by
  `about:blank`, no cookie, page or agent, no metadata. With a source it also keeps its ask, so
  Retry looks up again; without one, Resume stays "Link expired" as today.
- **G3:** a merge's finished track is kept when its file has the length the fresh plan states for
  that track's whole file, or the plan states none (manifest and playlist tracks, a live file);
  else its files and sub-checkpoint are cleared and it downloads again.
- **G4:** the renewal does not use `refreshRuntime`; `refresh` now keeps the counters and gives the
  next link number.
- **Status:** `RENEWING_LINK` is the enum's last value (stored by name; read back after a restart
  as `NEEDS_REFRESH`, like every unfinished status). App plumbing in E1, so the app builds: the
  Downloads sets (occupying, active, pausable), the chip "Getting a fresh link…" (Active tone),
  the notification's line, `FOREGROUND_STATUSES` (G5: the service and the end notice keep
  watching) and `BackgroundWork` (a renewal is network work: the Wi-Fi lock stays).
- **Tests:** `DownloadLinkRenewalTest` (22, the queue with fakes: §4.5's E1 items 1–2, 5–10),
  `FreshLinkSameFileTest` (8, the real engines and a local server: items 3, 4 and 11 — Direct
  ETag / total, HLS / DASH pieces, a DASH whole file's length, G3, MP3 and M4A, G1 per engine,
  the request tag), `RenewingLinkStatusTest` (app, 4). A check with G1 and G3 turned off fails
  four of them. Every existing `core-download` test passes unchanged.

## 5. Files and ownership

- **Track E may change:** `core-download/**` (the queue, the new interface, the engines only where
  E0 finds a gap) and its tests; `core-model/**/download/*` (a status, a failure detail); in
  `app/`: `download/DownloadEnqueuer.kt`, a new renewal source beside it,
  `download/DownloadRuntimeModule.kt` (wiring), `download/DownloadNotificationText.kt`,
  `download/DownloadServiceController.kt`, `feature/downloads/*` (labels, rows, Retry, Details),
  `feature/settings/PrivacyCleaners.kt` (the wipe), the trial hook (E-D9) and
  `scripts/verify-release-debug-actions.py`'s marker; their tests;
  `.github/workflows/minified-check.yml` (this branch added to its branches); this plan; its own
  `docs/SESSION_STATE.md` section.
- **Locked (call only; a needed change is a hand-off line in the report and a STOP):**
  `extractor-sites/**`, `extractor-master/**` (and `-android`), YouTube's modules and own solver,
  the ladders, `recipes/**` and the recipes workflows, `SiteAdapterCoordinator`'s and
  `SiteLookupCache`'s rules, Gradle's version (`gradle.properties`), `CHANGELOG.md`'s `[1.0.1]`,
  `docs/release/**`, `docs/PHONE_TEST_NOTES.md`, `docs/TEST_MATRIX.md`, `docs/SUPPORT_MATRIX.md`,
  every other `SESSION_STATE` section.
- **Shared docs:** this branch's `SESSION_STATE` section sits above `## VK-Master wiring agent (D)`,
  so Git merges it cleanly with that branch's S5 notes. `CHANGELOG.md` (`[1.0.2]`), the matrices
  and the phone notes change only in E4, after `main` (v1.0.1) is merged in.

## 6. Steps

| Step | What | STOP | AI agent time |
| --- | --- | --- | --- |
| E0 | Recon + design, docs only: §2 checked, the questions below answered, §4.5 written | STOP: Burmese report, §3's open decisions | 2–4 h |
| E1 | core-download: the interface, the state, the cap, resume or start over, cleanup, races; all four plan types; unit tests | — (CI green) | 4–6 h |
| E2 | app: the renewal source (adapter sites), the same video and quality, wiring, rows, Retry (E-D6), notifications, Details; unit tests with fakes | — (CI green) | 6–9 h |
| E3 | VK's 400 (E-D1), pages without an adapter (E-D8), the trial hook (E-D9), YouTube's B1/F8 unchanged (tests) | STOP: "READY FOR PHONE TEST (after v1.0.1)" | 3–5 h |
| E4 | Only after the owner says v1.0.1 is published: merge `main` in, `[1.0.2]` in `CHANGELOG.md`, previews, the owner's phone test (§7), a PR with the owner's OK, v1.0.2 by `docs/RELEASE.md` | the owner's OKs | 1–2 h + the phone |

E0's questions:

1. Each engine's same-file rule and its cleanup when the rule fails (a Direct partial file when
   the layout starts again; HLS chunks; DASH; the merged download; the MP3/M4A workspace); the
   tests that prove each; the tests missing.
2. Which failures reach `failTask`, per engine and stage (a 403 while connecting vs in the middle;
   F8's path); whether `HTTP_STATUS` keeps its status code; what each site's dead link answers (at
   most a few public videos per site from the agent's own network, `scripts/live-check.sh`:
   status, host and markers only — optional).
3. Whether `MediaVariant.id` is stable across lookups, per adapter (code and fixtures first).
4. Every enqueue path and what it knows (the page address, the tab's cookies and data); whether a
   renewal without the tab gets an equal answer (`SiteLookupKey.session`).
5. The new status or an alternative, with every place it touches.
6. Whether P37's `BrowserPageReader` works without a tab (agent, cookies).
7. The trial hook: where it sits (Downloads ⋮ in the trial build), how it breaks a link (the next
   request of that task answers 403 in the engine's HTTP layer), its release-verifier marker.

## 7. Phone test (after v1.0.1 is published; never on the census phone before that)

On E3's previews (normal + trial; uninstall the old preview first):

1. Trial: start long downloads — a YouTube merged 1080p, a Facebook or X file, a TikTok, a VK 18+
   video, an HLS page — and "Break this download's link" at about 30 %: "Getting a fresh link…",
   then the download goes on; Details "resumed at …" or "started over"; the file plays, its size
   right.
2. Real: start a download on Wi-Fi, switch to mobile data halfway (a new IP: YouTube's 403, VK's
   400) → it goes on.
3. Pause, then Resume after the link's expiry (or after a break) → a fresh link.
4. Two downloads at once, one broken; Cancel during "Getting a fresh link…" → cancelled, no file.
5. No "Download failed" notification during a renewal; one only when it fails.
6. "YouTube refused" (B1) as today.

## 8. Risks

- Loops and 429s → one lookup per failure, the cap (E-D3), the sites' budgets and cooldowns.
- Wrong bytes appended → resume only on the engines' same-file proof; byte 0 otherwise; tests per
  plan type.
- YouTube regressions → F8 and B1 untouched and tested (E-D5).
- Conflicts with v1.0.1 → own files; shared docs only in E4; the version and `[1.0.1]` untouched.
- CI and APK mix-ups during v1.0.1's release → reports always name the branch and the run; no APK
  to the owner before v1.0.1 is published; code pushes pause while v1.0.1 is built if the owner
  asks.
- Privacy → memory only (E-D2); Details without addresses.
- A lookup file must change after all → a hand-off and a STOP: it would bring S5 back (decision 26).

## 9. Also for v1.0.2, not on this branch

- The Downloads list's rows swap places, flashing, during 2–3 downloads at once:
  `DownloadsUiState.from` orders the active rows by `updatedAtEpochMs`, which every progress write
  moves (the owner's note, 2026-10-27: a stable order, such as when the download was added). Its
  own branch and agent (Appendix B), in parallel with E; it touches that sort only, E that file's
  status sets.

## Appendix A — prompt for the Track E agent

```text
You are the Track E agent for the YFT Android app (repository Alalkipgen/YFT): a file that stops
working after its download started (HTTP 401/403/410, an expired link, VK's 400) gets a fresh
lookup and the download goes on — resumed only when the file is provably the same, else from
byte 0. Every site.

Branch: work/phase-track-e-403 (it exists; base main a27b2c9c, the code of S5's census APK). Work
only there, in your own folder (for example /data/YFT-E), never in a folder where another branch
is checked out:
  git fetch origin
  git checkout -B work/phase-track-e-403 origin/work/phase-track-e-403

Read first, in this order:
1. AGENTS.md — branch, checkpoint (scripts/checkpoint.sh), security and Burmese-report rules. Its
   Phase 15 parts (agents A/B/C, FIX_ADD_PLAN's tasks, P44, P8) are history; this plan replaces
   them for you.
2. docs/TRACK_E_PLAN.md — this plan: scope §1, today's code §2, the owner's decisions §3, the
   design §4, files §5, steps §6. docs/SESSION_STATE.md — your section "Track E agent" only.
3. docs/ARCHITECTURE.md (downloads), docs/decisions/ADR-003-download-engines.md, ADR-006.
4. The code §2 names, and the work it builds on: P17 (SiteLookupCache), P21 (start over),
   P29/P37/P45 (fresh links in the sheet), item 7 Track B1 and F8 (YouTube's 403s),
   docs/VK_MASTER_CENSUS.md §4 (VK's 400s).

Task, in order:
- E0 (docs only): check §2 against the code, answer §6's E0 questions, write §4.5 with the test
  list, checkpoint, then STOP and report to the owner in Burmese, short: what is already there,
  what you will build, §3's decisions still open (with their defaults).
- After the owner's answer (or "defaults OK"): E1, E2, E3 as §6 says — a checkpoint after each
  milestone and every 20–30 minutes, CI green before the next step. Then STOP with "READY FOR
  PHONE TEST (after v1.0.1)" and a Burmese report: branch, commit SHA, CI runs, §7's checklist.
- E4 only when the owner says v1.0.1 is published.

Validation: before the first code edit, verify the existing build (./gradlew --no-daemon
:core-download:testDebugUnitTest :app:testDebugUnitTest when an Android SDK is there; without one,
say so). At your first code checkpoint add work/phase-track-e-403 to the branches of
.github/workflows/minified-check.yml; CI's checkpoint validation, emulator smoke and minified check
are the record. Never report a run you did not make.

Never: push to main, work/phase-vk-master-wire, recipes or another agent's branch; open or merge a
pull request; add a label; run master-recipes-publish or a release workflow; create a tag; change
gradle.properties, CHANGELOG.md's [1.0.1], docs/release/**, docs/PHONE_TEST_NOTES.md,
docs/TEST_MATRIX.md, docs/SUPPORT_MATRIX.md or another SESSION_STATE section; change a locked
lookup file (§5) — a needed change there is a hand-off line and a STOP; give the owner an APK
before he says v1.0.1 is published; save a page address, quality or link on disk (E-D2); print or
commit a cookie, token, signed URL, IP or response body; sign in, solve a captcha or bypass access
or DRM (ADR-006). Live requests only from your own network: public videos, signed out, a few per
site at most, status/host/markers only. Anything outside this task is a backlog line in your
SESSION_STATE section, not an edit.
```

## Appendix B — prompt for the Downloads row-order agent (optional, in parallel)

```text
You are the Downloads row-order agent for the YFT Android app (repository Alalkipgen/YFT).

Branch: make work/phase-downloads-row-order from origin/main (a27b2c9c), in your own folder (for
example /data/YFT-R). Work only there.

Read first: AGENTS.md (branch, checkpoint, security and Burmese-report rules; its Phase 15 parts are
history), docs/TRACK_E_PLAN.md §9 on origin/work/phase-track-e-403, docs/SESSION_STATE.md.

Task: during 2–3 downloads at once the Downloads list's active rows swap places and flash, because
DownloadsUiState.from orders them by updatedAtEpochMs, which every progress write moves. Give the
rows a stable order (the owner's note: such as when the download was added, the newest first),
finished rows below the active ones as today; say in the report where a finished, failed or
retried row goes. Unit tests: progress writes never reorder rows; a new download comes first; ties
are stable. Add your own SESSION_STATE section above "## VK-Master kit agent (C)", checkpoint, CI
green, then STOP and report to the owner in Burmese: branch, commit SHA, CI runs, what changed. It
is merged only after v1.0.1 is published.

Never: push to main or another branch; open a pull request; create a tag; change the version,
CHANGELOG.md's [1.0.1] or the download engine (Track E's); give the owner an APK before he says
v1.0.1 is published.
```
