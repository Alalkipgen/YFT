# Phone test notes: the social plan (S1a phone proof; §7.4 A, B and C; the retests of tasks 12–14 and 15–16), 2026-10-21 to 2026-10-23

The owner's phone sessions, logged while they ran and pushed as docs only. The test posts' links,
post IDs and post words stay on the phone and are left out here.
- **Network:** VPN on means the VPN's US exit. Boston was used until 2026-10-22 14:59 UTC; from then,
  New York for TikTok and W. VPN off means the owner's mobile network, where TikTok can't be reached.
  Phone times are local time (UTC+6:30), 12-hour; UTC is given where it was logged.
- **APKs (debug, `yft-d7-*`):**
  - 2026-10-21: `fb-trial` of ef4a546, then 042d57a (run 39608538096).
  - 2026-10-22 morning: `normal` and `social-trial` of 430da9f (run 39751939477).
  - 2026-10-22 from ~14:10 UTC: `social-trial` of 36ec093 (run 39784454576; tasks 9 and 10). The Instagram probe (B2), B3 and C used 36ec093 only.
  - 2026-10-23: `social-trial` of c3e9147 (run 39878500080; tasks 12–14). The whole session used c3e9147 only.
  - 2026-10-23, later (the retest of tasks 15–16): `social-trial` of a87820e (run 39917638495), installed as an Update. The retest used a87820e only.

## Summary of the 2026-10-23 retest (tasks 15–16; `a87820e`)

The owner, 2026-10-23, `yft-d7-social-trial` of a87820e (run 39917638495); no VPN and signed out
unless said; the VPN's New York exit for W's VPN run (decision 25). Recorded by task 17's first
commit (docs only). **All PASS.**

| Test | Result |
| --- | --- |
| 1. F4: Instagram signed in, the rung and its proof on, slot 1 | PASS: the banner's proof line ("I2's rows set aside (the rung's proof)"), "not asked (signed in: the rung is the signed-out query)" and "no row (the proof set I2's rows aside and the rung gave none)" under the trial's words; no row, and no capture of the tab (0 observations). |
| 2. F1: signed out, the rung on, slot 5 | PASS: the banner's ladder line ("after LOGIN_REQUIRED; main's answers read, no new ask") and probe line ("HTTP 200, N bytes; the post's video in the answer: yes; a wall on the page: no; rows 3 (DIRECT)"); on the first Download a row 720p · HD, 720 × 1280. |
| 3. F2: the rung off, slot 5 | PASS: the playback words with Try again; Copy details: main's embed page (about 228k characters) "video without files", I2's embed line `VIDEO_WITHOUT_FILES`. 3b: following the words (play, then Try again) gave a 720p · HD row, 720 × 1280. |
| 4. F3: slot 9; F3b: slot 8 | PASS: slot 9 the sign-in words, the Details scroll to their end, Try again pinned and reachable, Copy details works; slot 8 main's photos-only words and no Try again (I2 `PHOTO_ONLY`, final; the probe "not asked (the answer is final)"). |
| 5. F6: Facebook link 1 | PASS: Master's rows 720p · HD (720 × 720 · MP4) and 360p (400 × 400 · MP4); a download plays with picture and sound; the trial line ends "heights 720,400 vs unknown" (L1 below). |
| 6. W | PASS: no VPN (08:36 UTC) NO CHANGE (TikTok stopped (time) without the VPN: not a baseline); VPN New York (08:40 UTC) NO CHANGE, with the note "Instagram: the baseline of 02:24 UTC (VPN on, signed out) had every ask walled: set aside; this run starts it" (F5); Instagram not walled this time. |

Afterwards both census boxes off; the owner signed out of Instagram.

### Backlog from the retest (not blocking; plan §7.4; L1 and L3 fixed in task 17, L2 for S6)
- **L1:** Facebook's trial line says "heights … vs unknown": main's Facebook rows state no height
  when the line is made (the sheet measures them later), so the line cannot cross-check Master's
  heights.
- **L2:** on the same Instagram post (slot 5) the rung's 720p row was 897 KB against the tab
  capture's 720p at 1.9 MB (both 720 × 1280): the rung may pick a lower bitrate (S6/B+; before the
  rung's default turns on).
- **L3 (cosmetic):** I2's page line says `SHAPE_CHANGED` where main's Details say "no post" for the
  same page.

## Summary of the 2026-10-23 session (§7.4's next phone session after tasks 12–14; `c3e9147`)

The owner, 2026-10-23, `yft-d7-social-trial` of c3e9147 (run 39878500080); no VPN and signed out
unless said; the VPN's New York exit for TikTok and W's VPN run (decision 25). Recorded by task 15
(docs only); its fixes F1–F5 and F3b: task 15's code commits (`MASTER_KEY_SOCIAL_PLAN.md` §7.3).

| Part | Result |
| --- | --- |
| Instagram slot 5, signed out, the rung's box on against off (2 of 2 each) | PASS: on → a row on the first Download; off → no row, the sign-in words. The rung rescues the post (decision 24's goal met). The rung's row came with no banner and "Quality unknown" (F1); with the box off, I2 read the embed page as `SHAPE_CHANGED` where main's Details say "video without files" (F2). |
| Instagram slots 1, 9 and 8, signed out | PASS: slot 1 I2's rows, the banner's trial line and "probe: not asked (I2's rows)"; slot 9 (the age wall) the sign-in words and the words line; slot 8 (photos only) the probe not asked. Slot 8's final answer still offers Try again (F3b). |
| Instagram signed in, the rung's box on | The proof box off: "probe: not asked (I2's rows)", the same item yes, 640 = 640 — PASS. The proof box on: "probe: not asked (signed in: the rung is the signed-out query)" (the signed-in rule works), but Master still gave 1 row with "the same item: no" (the duration yes, 640 = 640), and the banner had no "I2's rows set aside" line (F4). |
| X slots 6, 8 and 7 | PASS: slot 6 3 = 3 on the first lookup (observation (g) fixed); slot 8 main's words; slot 7 (sensitive) the sign-in words, `AGE_LIMITED`. |
| Facebook links 10 and 1 | PASS: link 10's group-post form, the trial's "Master found none" with its step (as B4); link 1, 2 against 3 (as B3). |
| TikTok slots 6 and 1 (VPN New York) | PASS, as B3. Slot 6's final answer (photos only) still offers Try again (F3b). |
| The failure sheet | Long Details and the Try again button under them were cut off: the sheet does not scroll (F3). |
| W | VPN New York, signed out (02:24 UTC): Facebook, TikTok and X NO CHANGE; Instagram BREAKING (6): every Instagram ask on all three slots got the wall (GraphQL 401; the page and the embed page: the login page). No VPN, minutes later: NO CHANGE (Instagram slot 1 rows, I2 612 = 612). Main's Instagram code is unchanged since C1's APK, so the exit's address walled it, not the app; the walled run became Instagram's baseline there (F5). No VPN, Instagram signed in: NO CHANGE against C3. |

### Owner decisions (2026-10-23)
Recorded by task 15 as `MASTER_KEY_SOCIAL_PLAN.md` §8 decisions 27–28 (each with its status there).
- **(1) Task 14's words: accepted as built (decision 27)**, including the agent's addition: the
  playback words also when the site's own answer names the post's video (not only when the tab
  shows one).
- **(2) Instagram's tab-fetch rung: off by default in `social-trial` (decision 28).** The owner
  turns it on in the census ("Instagram's tab-fetch rung (signed out)") for a test; the box keeps
  its state across updates (an app preference; Clear browsing data leaves it).

### For the coding agent (2026-10-23: task 15's fixes)
- **F1:** the rung's rows (Instagram slot 5, signed out: main `LOGIN_REQUIRED`, so no trial
  comparison) showed no banner: neither Instagram's ladder line nor "master: instagram probe: …"
  (task 13's observation (f) is met only when I2 gives rows). The rung's row has no height
  ("Quality unknown").
- **F2:** the rung off (the default, decision 28), slot 5 signed out got the sign-in words: main's
  own Details call the embed page (about 227k chars) "video without files", but I2 read that page
  as "SHAPE_CHANGED (no video read)", then `LOGIN_WALL` → `BROWSER`, so decision 27's rule (the
  playback words when the site's answer names the post's video) never fired. Task 13's
  `VIDEO_WITHOUT_FILES` passes on the synthetic page, not on the real one.
- **F3:** the failure sheet (`QuickDownloadScreen`'s `Failure()`) does not scroll: long Details
  and the Try again button under them are cut off. Wanted: the content scrolls (Try again always
  reachable) and a "Copy details" button (the Details as shown; words only).
- **F3b:** a final answer (photos only: Instagram slot 8, TikTok slot 6) still offers Try again.
- **F4:** signed in, the rung and its proof on, Instagram slot 1 (one video): I2's rows are set
  aside and the rung is not asked (signed in), yet Master gave 1 row, "the same item: no" (the
  duration yes, 640 = 640); the banner lacks "probe: I2's rows set aside (the rung's proof)".
- **F5 (W):** a run where every ask of a site got the site's login wall while its baseline had data
  (Instagram over a VPN exit) shows BREAKING and then becomes that site's baseline, so the next run
  from a good address shows CHANGED.

## Summary of the 2026-10-22 session (§7.4 A, B and C)

| Part | Result |
| --- | --- |
| A Facebook (census; VPN Boston, no VPN; signed out) | PASS on 430da9f (task 9's bug did not touch Facebook's fbcdn rows): ladder rows on 9 slots, the same video, wrong 0, on both networks. Slots 9 and 10 need playback, as documented. On 36ec093 Facebook was checked through W (C1–C3) and B3's link 1. |
| A X (VPN Boston, no VPN; signed out) | PASS on 36ec093 (task 9 verified): ladder rows 6, the same item 6, wrong 0, on both networks. Slots 7 and 8 need playback and are never "removed". The 10:48 UTC run on 430da9f failed by task 9's bug: the row reader refused X's hosts. |
| A TikTok (signed out) | Main PASS on 36ec093 (task 10 verified: no download service ask). Master's T1 is proven on the New York exit (14:59 UTC): rows 6 of 6, the same video, wrong 0. The Boston exit answered T1's first embed ask with a 429 three times (B0, 10:43 and 14:18 UTC). T2 (cookie V fail) is still untested. |
| A Instagram (signed out, no VPN; signed in, no VPN; signed in, VPN Boston) | PASS on all 3: ladder rows 6, 8 and 8, the same item, wrong 0 (the R8 way got rows on only 6). Signed-in slot 10 gives RESPONSE_CHANGED, the known S2d gap. |
| B1 (`normal` APK) and Facebook links 9 and 10's desktop forms (`social-trial`, 430da9f) | PASS. B1: only the post's own video. Links 9 and 10 with the trial: "Master found none" with its step, as documented. |
| B2 Instagram probe (signed out, no VPN, probe box on) | Slot 5: probe HTTP 200, "the post's video in the answer: yes", so a probe rung would rescue it. Slot 9 (a 21+ reel): 157 bytes, "video: no", so sign-in only. Slot 1: Master's row came on Try again; the probe line can't be seen on a row lookup. No row from the probe, no 429. |
| B3 short regression set (36ec093) | PASS 8 of 8: Facebook 1 and 10, Instagram 1 and 8, TikTok 1 and 6, X 6 and 8. X 6 gave rows only after Try again (observation g). |
| C W's first runs (36ec093, probe box off) | PASS: C1 (VPN New York) "FIRST RUN", C2 (no VPN) "NOT COMPARED", C3 (Instagram signed in) "NO CHANGE" (Facebook and X against C2). Every site that ran stayed within 24 requests, 2 MB and 60 s. Slot 1 got ladder rows on every reachable site, wrong 0, no 429. Without the VPN, TikTok can't be reached on the owner's network: it stops at the 60 s cap and is never a baseline there (§7.4: not a failure). |

### Owner decisions
All four decided on 2026-10-22; recorded by task 11 as `MASTER_KEY_SOCIAL_PLAN.md` §8 decisions 23–26
(each with its status there).
- **(1) TikTok differences accepted (decision 23).** Master's T1 gives 1 row per post with its
  tallest at most 1280 (main gives up to 4 rows and 1920), because Master is only main's fallback.
  Recorded in `docs/SUPPORT_MATRIX.md` (the TikTok backup ladder) as an intended difference.
- **(2) S2d part 2, the probe's rung: yes (decision 24).** Slot 5 would be rescued, slot 9 not
  (sign-in only). Its rules: the plan's §7.3 S2d part 2 (task 13).
- **(3) The New York exit instead of Boston for TikTok's VPN runs and W's VPN run: yes (decision
  25).** Facebook's, X's and Instagram's VPN census runs stay on Boston. TikTok's census baseline
  on New York: the 14:59 UTC census on 36ec093. W's baselines: C1 (VPN on, signed out), C2 and C3
  (VPN off, signed out; TikTok none there), C3 (VPN off, Instagram signed in).
- **(4) S5's rule: yes (decision 26).** Per site 3 runs, each at least 6 h after the previous one,
  the first and the last at least 24 h apart; counted only on an APK of the final code (a change
  to that site's pack, the engine or main restarts that site's count); a run where Master did not
  run (a 429, a cooldown, not reached) does not count. One session may run all four sites.

### For the coding agent (observations, not FAILs)
- (a) Instagram slots 1 and 5: the 1st trial lookup gave no Master row though main had 1. Try again gave I2's rows.
- (b) MasterMainSelection's NeedsPlayback at PLAYBACK_CAPTURE returns a new one-line details list,
  so it drops the engine's lines (ladder, I2 or X1, probe). Seen on Instagram slots 1 and 5 and on
  X slot 8 (no X1 line, which SocialLadderXTest (c) expects).
- (c) Main's signed-out answer for Instagram slot 5 varies between lookups (1 row, then LOGIN_REQUIRED).
- (d) I2 reads the signed-out embed page as SHAPE_CHANGED where main reads "video without files".
- (e) "Play the video on this page, then tap Try again." shows over age and login walls and over X's
  "post not found" page, where there is no video to play. Try again repeats it.
- (f) §7.4 B's "one probe Details line per lookup" can't be seen on a row lookup: the banner shows
  the trial line only, and the sheet's Details show the file's info.
- (g) X slot 6 (B3): the 1st trial lookup ended in NeedsPlayback ("fresh natural playback required")
  with no X1 line, and X1's replay answered only on Try again. This is the same pattern as (a) and (b):
  check whether the social ladder runs before PLAYBACK_CAPTURE's NeedsPlayback on the 1st lookup
  (or whether LookupRecord or MainPages is missing then).
- Also:
  - Facebook's Master rows are 2 vs main's 3, and both "Quality unknown" (B3 link 1), so the S5 note stands.
  - Optional: TikTok without the VPN spends about 35 s more after the first NETWORK failure (the
    desktop variant and the hidden page).

---

# Session log (as logged during the sessions, oldest first)

## First run: S1a (9) ef4a546 (rebuilt from the session summary; the original notes were lost)
VPN Boston 10:52–11:43 UTC; no-VPN subset 11:46–11:53 UTC, 2026-10-21.
- V1/V2 VPN: Master "none" at first, OK after Try again
- V3–V8, V11: OK
- V9 (desktop pfbid → story.php): FAIL, no trial line + a wrong 1:00 item
- V10 (desktop group permalink): FAIL, Master none even after play
- V12/V13: login wall, "No video on screen"
- All trial lines "duration unknown"; Master rows often "Quality unknown" and fewer than main's.

## Re-run: S1a (10) 042d57a, yft-d7-fb-trial (run 39608538096)
Logged 2026-10-21 14:18 UTC (sandbox clock). Phone times are PM, local time (UTC+6:30): 8:44–8:57 PM = 14:14–14:27 UTC.

### VPN Boston: V1–V5 (owner: rows on 1st lookup, no Try again)
Trial line on all 5 (identical): `master: facebook ladder after RESPONSE_CHANGED; replayed 0; new 1; trial: Master's rows 2, main's 3; the same video: yes; duration within 1 s: yes`. Sheet: "Found on this page 1".

| # | Page (mapped by screenshot time) | Result |
|---|---|---|
| V1 | watch/ 8:44 | ✅ |
| V2 | watch/ (gov.sg) 8:45 | ✅ |
| V3 | watch/ (cup) 8:45 | ✅ |
| V4 | reel/11… (Beast Camp) 8:46 | ✅ |
| V5 | reel/1… 8:46 | ✅ |

vs first run: V1/V2 no longer need Try again (fix 4 OK); duration now "yes" (fix 3 OK). Master rows 2 < main 3, as before.
Pending: V6–V13, share/p link, then VPN-off set.

### VPN Boston: V6–V8 (owner: rows on 1st lookup, no Try again)
Trial line on all 3 identical to V1–V5 (Master 2, main 3; same video yes; duration within 1 s yes). Sheet "Found on this page 1".

| # | Page (mapped by screenshot time) | Result |
|---|---|---|
| V6 | watch/ 8:47 | ✅ |
| V7 | watch/ (InfoPico) 8:48 | ✅ |
| V8 | reel/1… (Potensyal) 8:49 | ✅ |

Pending: V9–V13 (9/10 also via share/v), share/p link, then VPN-off set.

### VPN Boston: V9 desktop pfbid (opens story.php) 8:50–8:51
- No trial line (expected: main's post rule needs a numeric ID, so Master never runs; documented limitation).
- Sheet "Found on this page 2" (both with the post's title):
  - 2:13 "Math Made Easy Ep 2", 360p Data saver 3.8 MB, 360×360, 23.98 fps: likely the post's video ✅
  - 1:00 "How did we go from this…" (presidents), 360p 2.3 MB, 360×640, 29.97 fps: WRONG item (b), main's page-video list (same as first run, main's bug)
- Master wrong rows: 0 (no Master rows). Result: documented limitation, no change vs first run.
Pending: V9 via share/v (link on the phone) must give Master rows.

### VPN Boston: V9 via share/v (link on the phone) 8:53 → watch/
Trial line identical to V1–V8 (Master 2, main 3; same video yes; duration within 1 s yes). Sheet "Found on this page 1" (the post's own video, the right one). Rows on 1st lookup, no Try again. ✅ (owner rule met: share link gives Master rows)
Pending: V10 desktop + share/v (link on the phone), V11–V13, share/p, then VPN-off set.

### VPN Boston: V10 desktop group permalink 8:52
Banner/sheet: "Facebook trial: main's rows were set aside and Master found none. Tap Try again." Details:
- facebook trial: main's 3 rows set aside
- Master's answer not used: the page changed during the lookup: the same address again
- ladder: a link main resolves first, no video in main's answer; not asked
- F2 main's pages: page 1: SHAPE_CHANGED (no rows; route: another); next: PLAYBACK
- one browser-assisted capture requested → "Open the page, play its video, and supply a fresh capture."
Result: documented limitation (desktop form; matches agent's link-10 note: page reloads, F3 never matches). Master wrong rows 0. Same as first run. Details step line (fix 4) shows as designed.
Pending: V10 via share/v (link on the phone) must give Master rows.

### VPN Boston: V10 via share/v (link on the phone) 8:55 → reel/2…
Trial line identical to V1–V8 (Master 2, main 3; same video yes; duration within 1 s yes). Sheet "Found on this page 1" (birb "Hello? Yes your uber ride is here", the right one). Rows on 1st lookup, no Try again. ✅ (owner rule met)
Pending: V11–V13, share/p, then VPN-off set.

### VPN Boston: V11 8:56 → watch/ (ESL One Dota 2 VoD)
Trial line identical to V1–V8 (Master 2, main 3; same video yes; duration within 1 s yes). Sheet "Found on this page 1". Rows on 1st lookup, no Try again. ✅
Pending: V12–V13, share/p, then VPN-off set.

### VPN Boston: V12 8:56, V13 8:57
Both redirect to m.facebook.com/login… (FB login wall). No trial line, no sheet. Expected (login wall OK; same as first run). ✅
Pending: share/p link, then VPN-off set.

### No VPN: V1–V5 (owner: rows on 1st lookup, no Try again)
Trial line on all 5 identical to the VPN set (Master 2, main 3; same video yes; duration within 1 s yes). Sheet "Found on this page 1". No VPN icon in the status bar.

| # | Page (mapped by content) | Phone time | Result |
|---|---|---|---|
| V1 | watch/ | 9:01 PM | ✅ |
| V2 | watch/ (gov.sg) | 9:01 PM | ✅ |
| V3 | watch/ (cup) | 9:02 PM | ✅ |
| V4 | reel/11… (Beast Camp) | 9:04 PM | ✅ |
| V5 | reel/1… | 9:02 PM | ✅ |

Pending: no-VPN V6–V8, V11, V9/V10 share/v, share/p, V12–V13; plus share/p with VPN on (not sent yet).

### No VPN: V6–V8 (owner: rows on 1st lookup, no Try again)
Trial line on all 3 identical (Master 2, main 3; same video yes; duration within 1 s yes). Sheet "Found on this page 1". No VPN icon.
- V6 watch/ 9:02 PM ✅
- V7 watch/ (InfoPico) 9:05 PM ✅
- V8 reel/1… (Potensyal) 9:06 PM ✅
Pending: no-VPN V11, V9/V10 share/v, share/p, V12–V13; plus share/p with VPN on.

### No VPN: V9 → story.php 9:06–9:07 PM (asked owner: desktop link or share/v?)
No trial line. Sheet "Found on this page 2": "Video file" MP4 2.3 MB (no duration; = the wrong 1:00 item from the VPN run) + the post's own video, MP4 3.8 MB 2:13 (right one). Same as the VPN desktop result. Post date shows "7 Dec 2023" (the network's locale).
If desktop: documented limitation, OK. If share/v redirected to story.php without VPN: owner rule NOT met → follow up.

### No VPN: V9 via share/v 9:08 PM → watch/
Trial line identical (Master 2, main 3; same video yes; duration within 1 s yes). Sheet "Found on this page 1" (the post's own video, right one). 1st lookup, no Try again. ✅ (owner rule met without VPN). So the 9:06–9:07 story.php run above was the desktop link → documented limitation, OK.
Pending: no-VPN V10 share/v, V11, share/p, V12–V13; plus share/p with VPN on.

### No VPN: V10 desktop 9:09 PM + V10 via share/v 9:10 PM
- Desktop group permalink: "Master found none. Tap Try again."; Details identical to the VPN run (Master's answer not used: the page changed during the lookup: the same address again; F2 page 1 SHAPE_CHANGED → PLAYBACK; browser-assisted capture requested). Documented limitation, OK. Master wrong rows 0.
- share/v → reel/2…: trial line identical (Master 2, main 3; same video yes; duration within 1 s yes). Sheet 1 (birb, right one). 1st lookup, no Try again. ✅ (owner rule met without VPN)
Pending: no-VPN V11, share/p, V12–V13; plus share/p with VPN on.

### No VPN: V11 9:11 PM, V12 9:11 PM, V13 9:12 PM
- V11 watch/ (ESL One Dota 2): trial line identical (Master 2, main 3; same video yes; duration within 1 s yes). Sheet 1. 1st lookup, no Try again. ✅
- V12, V13: m.facebook.com/login… (English (UK)), login wall. Expected. ✅
Pending: share/p link (no VPN and VPN on). Everything else in the re-run is done.

### No VPN: extra share/v link (owner's new link) 9:15 PM
The owner's extra share/v link → reel/… (a reel). Trial line identical (Master 2, main 3; same video yes; duration within 1 s yes). Sheet 1. 1st lookup, no Try again. ✅ (bonus; this is share/v, NOT share/p)
Pending: a real share/p link (no VPN and VPN on).

### share/p: N/A
Owner found no share/p link; FB now gives share/v for video posts (owner's view). Recorded as not tested / N/A.

## Re-run verdict: S1a phone proof PASS ✅ (VPN Boston + no VPN)
- V1–V8, V11: Master rows on the 1st lookup (no Try again), same video yes, duration within 1 s yes, both networks.
- V9/V10 via share/v (+ the owner's extra share/v link): Master rows, both networks (owner rule met).
- V9/V10 desktop forms: documented limitation (no trial / Master none), both networks. V9 desktop shows main's wrong extra item (1:00, 2.3 MB), main's bug, flag-independent.
- V12/V13: login wall, both networks. Master wrong rows: 0 everywhere.
- Note for S5: Master rows 2 vs main 3 on every link (first run also had "Quality unknown" rows) → fix or list in docs/SUPPORT_MATRIX.md before release.

## Instagram census, no VPN, 2026-10-21 15:00 UTC (owner says signed in, but the census saw SIGNED OUT)
Report note: "Instagram: no baseline yet (VPN off, signed out); this run starts it"; every row session:false; GraphQL 401 on all 10 slots.
10 slots, 38 requests, 2.1 MB, 31 s. Main rows on slots 1–4, 6 (embed page) and 7 (page data, item 1 of 3) = 6 of 7 downloadable (same as S0d). Slot 5 reel LOGIN_REQUIRED (not as expected; embed page no data). Slot 8 photo-only NO_MEDIA_FOUND (expected), 9 sign-in-only LOGIN_REQUIRED (expected), 10 removed LOGIN_REQUIRED (ambiguous, no session).
Master: trials "needs playback, refused 1" on 1–4, 6 (no Instagram pack yet); slot 7 trial rows 1 (720), V pass; slot 8 route B embed no data.
→ Valid as Instagram's signed-out, VPN-off baseline. The signed-in census (needed before S2d) is NOT done yet: owner to check the login in the app browser and re-run (no VPN, then VPN Boston).

## Instagram census, no VPN, SIGNED IN, 2026-10-21 15:04 UTC
Note: "Instagram: no baseline yet (VPN off, signed in); this run starts it". 10 slots, 41 requests, 0.8 MB, 20 s.
- Main via app API (authorized) on every slot: rows on slots 1–7 (7 of 7 video slots; slot 5 also OK signed in). V pass 7.
- Slot 8 photo-only: NO_MEDIA_FOUND (expected).
- Slot 9 sign-in-only: NO_MEDIA_FOUND [recoverable], NOT as expected (rows wanted); app API 200 no data.
- Slot 10 removed: RESPONSE_CHANGED [recoverable], NOT as expected (another answer); app API 400, GraphQL 400, page/embed no data.
- Master trials 7: rows 6, V pass 6 (embed page; slot 5 needs playback). Tallest differs from main on some slots (1: 612 vs 640; 4: 1908 vs 1276; 6: 960 vs 1280; 7: 720 vs 640).
- vs signed out (15:00): main 6/7 via embed page, 38 req, 31 s → signed in better and faster.
Pending: the same, signed in, VPN Boston. Inputs for S2d: slots 9 and 10.
- Owner checked slot 9 signed in: it is a PHOTO post (sign-in-only photo). So main's NO_MEDIA_FOUND is right; the slot is mislabeled (census wants a sign-in-only video). Owner to replace slot 9 with a sign-in-only video later (not blocking). Not a main bug.
- Proof (owner screenshot 9:38 PM): slot 9 = the old link (kept on the phone), a single photo (an age-gated ad → sign-in-only). Replacement idea: an age-gated Reel.

## Instagram census 15:13 UTC — VPN Boston, signed in
- Slot 9 replaced by owner: sign-in-only reel (age-gated when signed out).
- 42 requests, 0.8 MB, 44 s (slot 1 main 11.7 s).
- Main rows: slots 1–7 + slot 9 (rows 1, tallest 360, 106.3 s, as expected). V pass 8. Slot 8 photo OK.
- Only "not as expected": slot 10 removed → RESPONSE_CHANGED (app API 400 / GraphQL 400) → S2d input.
- Master trials 8: rows 6, V pass 6 (slots 5, 9 "needs playback", embed no data).
- Signed-in census before S2d: DONE (no VPN 15:04 + VPN 15:13).

## X census 15:52 UTC — VPN Boston, signed out (X's B0, VPN on; "no baseline yet … this run starts it")
APK: the phone's current debug APK (042d57a's). 8 slots, 40 requests, 0.1 MB (63,463 B), 23 s; mobile data; session false on every row.
- Main: slots 1–6 rows, all as expected, V pass 6/6 (206); one ask per lookup (tweet-result GET 200, ~0.5 s; slot 1 2.4 s cold). Rows: 1: 3 (720, 15.5 s); 2: 3 (720, 140.0 s); 3: 3 (720, 324.5 s); 4 GIF: 1; 5 quoted post's video: 3 (1024, 21.4 s); 6 several videos: 3 (720, 6.0 s, item 1 of 2).
- Slots 7 age-limited, 8 removed: NO_MEDIA_FOUND [recoverable], as expected (decision 10); Master route B: needs playback.
- Master trials 6 (replayed 1, new 0, refused 0 each): rows 6, V pass 4. Same as main on 1, 2, 4, 5. Slot 3: rows 4 vs main 3, no duration, V unknown (HLS). Slot 6: rows 8 vs main 3 (item 1 of 2), no duration, V unknown (HLS).
- S2c inputs: (a) an HLS row only with a known duration, MP4 first (slots 3, 6); (b) slot 6: Master's 8 rows look like both videos' rows → only the link's item (/video/N, else the first; decision 11).
X no-VPN half: see below.

## X census 15:56 UTC — no VPN, signed out (X's B0, VPN off; "this run starts it")
8 slots, 40 requests, 0.1 MB (63,081 B), 11 s; mobile data; session false.
- Main: the same answers as 15:52 (slots 1–6 rows, same counts/tallest/durations, V pass 6/6; one tweet-result GET 200 per lookup, ~0.7–1.0 s). Slots 7, 8 NO_MEDIA_FOUND [recoverable] as expected; route B needs playback.
- Master trials 6: rows 6, V pass 4: the same pattern (slot 3 rows 4, slot 6 rows 8, both no duration, V unknown HLS).
→ X's B0 done (VPN on 15:52 + VPN off 15:56). Both S2c inputs confirmed on both networks. Also: slots 7 and 8 both "no post" (different shapes) → Master says needs playback for the removed post too.

## Prompt for the next agent (after S2b; head e08fecd verified, CI green)
Tasks: 4 S2c X (3 owner inputs); 5 S2d IG part 1 + tab-fetch probe (switch off by default); 6a fb-trial -> social-trial (yft-d7-social-trial, all four sites); 6b census Master trial uses site packs when ladder flag on; 7 FB rows 2 vs 3 (fix or SUPPORT_MATRIX) + link 9 desktop trial pinned by JVM test; 8 S3 weekly check W build + runbook section. Not included: S4 (decision 5 open), TikTok cookie test / mutated APK (owner go), tab-fetch rung (after probe).

## Check 2026-10-21 23:50 UTC (owner asked what is pushed)
Pushed since e08fecd: efc3670 (S2c X code, CI APK 10/10 + checkpoint green, artifacts incl. yft-d7-fb-trial now sets aside X too) and 682534a (S2c docs, checkpoint green). Local drift 0/148 (after fetching main 34a4189; shallow clone gave false 148). main unchanged 34a4189. Tasks 5-8 not pushed yet (last push 20:36 UTC). Intended differences for owner OK: widget 404 -> final REMOVED ("no longer available"); deleted vs protected same notice tombstone -> needs playback (today reason); {} -> VALUE_REJECTED; HLS-only -> tab playback; census X trial still R8 until 6b (X proof in browser with fb-trial).

## Check 2026-10-22 ~05:15 UTC (agent report: tasks 5 + 6a pushed)
Verified: dd761c8 (S2d part 1 code; APK 39714684888 10/10, checkpoint 39714691113) + 32afb60 (docs; checkpoint 39714805671) + f6b4bcc (6a social-trial; APK 39716880084 10/10 incl. yft-d7-social-trial, checkpoint 39716884346) all green; main 34a4189; drift 0/157; workflow test OK; only fixture links. Probe: switch off by default (YFT site census -> Instagram tab-fetch probe), evaluateJavascript read-back, no addJavascriptInterface, query data with source yt-dlp 1f1101d0 2026-07-21, not asked when signed in. IG intended diffs: Master keeps 360p row main drops; never final removed. 6b design in handoff-6b.md (no code). Sent revised next-agent prompt: added rules, full phone list, IG wrong-rows = unknown unless duration >1s off.

## Owner OK 2026-10-22: Instagram intended differences accepted (Master keeps the 360p row main drops; removed never final). X ones (404 final; {} VALUE_REJECTED; notice tombstone needs playback) recommended, owner to confirm by keeping the X line in the prompt add-on.

## Check 2026-10-22 ~10:00 UTC (agent report: tasks 0, 6b, 7, 8 pushed)
Verified: head cb1c84d; 8 commits 22b98d1 025fd38 ec4590c 8c193c5 e007536 bf6d344 430da9f cb1c84d; 12 CI runs success, APK runs 39732889432 39738835396 39746327550 39751939477 each 10/10; phone APKs run 39751939477 (social-trial 12414986802, normal 12413839901, expire 2026-11-05); main 34a4189; PR #2 open unmerged; no new tag/release; no main adapter/YouTube/LookupAsk file; drift 0/157; workflow test OK; @Test deltas master 926>927>934>941, app +1 (t0) +1 (t7); fixtures only. Task 0: 404 -> NEEDS_PLAYBACK reason null, tests a-d. 6b wrong rows a-e as asked. Decisions 20-21 in s8. W social-trial only; caps 60s hard, 2MB/24 req soft (checked before each step). Open: FB/TikTok intended diffs, task 7 options, first-W pass, decision 5.

## B1 — yft-d7-normal (430da9f), no VPN, signed out, Facebook link 9's desktop form, 2026-10-22 morning — PASS
- "Found on this page" 1 item; the sheet had only the post's own video (2:13) rows, no other video of the page. Facebook's sign-in popup when signed out is the site's normal one.

## A Facebook — social-trial, VPN Boston, signed out (2026-10-22 10:39 UTC) — PASS
- 13 slots, 73 requests, 5.6 MB, 73 s; main recoverable 0/11, V pass 11
- ladder: Master parts 13, rows 9, same video 9, V pass 9, wrong rows 0 (unknown 0)
- slots 1-8, 11: Master 2 rows vs main 3 (DASH audio row = intended diff), same video yes, duration yes
- slots 9, 10: Master needs playback (F1 no video, F2 PLAYBACK, F3 no tab) = documented intended diff
- slot 12: ambiguous (no session); slot 13: PRIVATE_OR_UNAVAILABLE final, no Master row
- Diff CHANGED (2) vs 2026-10-21 06:01: slot 3 main 1->3, slot 10 main 2->3 = improvements, not regressions
- Next: A TikTok (same config)

## A TikTok — social-trial, VPN Boston, signed out (2026-10-22 10:43 UTC) — main PASS, Master INCOMPLETE (429)
- 9 rows (slot 7 = private slot, no link, same as B0), 39 requests, 1.2 MB, 44 s; main 9/9, rows 6/6, V pass 6, not as expected 0
- Diff: NO CHANGE vs B0 (2026-10-21 05:59 UTC)
- slot 6 photo: NO_MEDIA_FOUND, route B no request (T1 not run (final)) = as expected
- slot 8 trial: T1 embed player GET 429 -> Master stopped 30 min; slots 1-5, 9, 10 "Master not run: stopped (429)"
- ladder: parts 2, rows 0, wrong 0 -> T1/T2 rows not proven yet
- B0 also got 429 on its first embed ask -> 2nd time in a row on the Boston exit
- Next: re-run TikTok in #5 (>= 11:15 UTC); TikTok differences decision waits for that run

## A X — social-trial, VPN Boston, signed out (2026-10-22 10:48 UTC) — main PASS, Master/ladder FAIL (bug)
- 8 rows, 30 requests, 0.0 MB, 10 s; main rows 6/6, V pass 6, not as expected 0; Diff NO CHANGE vs 2026-10-21 15:52 UTC
- slots 7 (age-limited) and 8 (removed): route B needs playback, X3 not run (no tab), no Master row, not "removed" = as expected
- slots 1-6 trials: "failed NETWORK; new 3 (GIF 1), refused 0", each ask "unlabelled GET NETWORK 1-2 ms"; ladder rows 0, wrong 0
- ROOT CAUSE (code at cb1c84d): OkHttpRowReader(client, site = SiteWays.FACEBOOK default) has its own host guard
  `site.mediaHost(url)` -> returns MediaStart.Failed(null) before any network for non-fbcdn.net hosts.
  Both constructions pass no site: app/.../BrowserMasterFallback.kt:597 and app/src/debug/.../SiteCensusActivity.kt:326.
  SocialRowCheck already uses the lookup's site (S2b, MainPages.site) so it lets X rows through, then the reader drops them.
  RouteBLookup.read counts it as a new ask; probeOf(Failed(null)) = NETWORK; SocialRowCheck failure(null) = NETWORK.
- Affects: X (video.twimg.com), TikTok (tiktok.com/tiktokcdn*), Instagram cdninstagram.com rows (fbcdn.net rows ok); FB unaffected.
- OkHttpRowReaderTest has no X/TikTok/IG host case; no other test uses the real reader.
- Recommendation: pause X/TikTok/IG phone tests until fix + new APK.

## A Facebook — social-trial 430da9f, no VPN, signed out (2026-10-22 11:23 UTC) — PASS
- 13 slots, 73 requests, 5.8 MB, 63 s; main recoverable 0/11, V pass 11, ambiguous 1, not as expected 0
- ladder: Master parts 13, rows 9, same video 9, V pass 9, wrong rows 0 (unknown 0)
- slots 1-8, 11: Master 2 rows vs main 3 (DASH audio row = intended diff), same video yes, duration yes, V pass 206; row reads real (206, 49-157 ms)
- slots 9, 10: Master needs playback (F1 no video, F2 PLAYBACK, F3 no tab) = documented intended diff
- slot 12: ambiguous (no session); route B needs playback; slot 13: PRIVATE_OR_UNAVAILABLE final, no Master row
- Diff CHANGED (2) vs 2026-10-21 06:04: slot 3 main 1->3, slot 10 main 2->3 = same as the VPN run, improvements, not regressions
- Next: B2 (no VPN, signed out)

## B2 — social-trial 430da9f, no VPN, signed out, link 9 desktop form (story.php, a Page's post), 2026-10-22 — PASS
- Sheet: "Facebook trial: main's rows were set aside and Master found none. Tap Try again."; no rows; banner + Try again on the page
- Details: facebook trial: main's 3 rows set aside; Master's answer not used: the page changed during the lookup: the same address again; ladder: a link main resolves first, no video in main's answer; not asked; F2 page 1 SHAPE_CHANGED (no rows; route: another); F2 next PLAYBACK; one browser-assisted capture requested
- Reader-free (Master row none) -> stays valid after TASK 9
- Next: B4 FB link 10 (no VPN, signed out)

## B4 FB link 10 — social-trial 430da9f, no VPN, signed out, group post desktop form (facebook.com/groups/.../permalink/...), 2026-10-22 — PASS
- Sheet: "Facebook trial: main's rows were set aside and Master found none. Tap Try again."; no rows
- Details: facebook trial: main's 3 rows set aside; Master's answer not used: the page changed during the lookup: the same address again; ladder: a link main resolves first, no video in main's answer; not asked; F2 page 1 SHAPE_CHANGED (no rows; route: another); F2 next PLAYBACK; one browser-assisted capture requested
- Matches the documented link 10 behaviour (main 3 rows without VPN); reader-free -> stays valid after TASK 9

## Status after the current-APK FB tests (430da9f)
- Done, valid after TASK 9: B1, B2, B4 FB link 10, A main parts (FB VPN/no VPN, TikTok VPN, X VPN)
- After TASK 9's APK: FB regression first (A Run Facebook VPN Boston: diff NO CHANGE, ladder 9/9/9, wrong 0, new 3 refused 0; B4 FB link 1), then A X -> TikTok (cooldown) -> IG, B3, B4 X/IG/TikTok, C W
- Waiting: TASK 9 push (review the diff before the phone run)

## A X — social-trial 36ec093 (run 39784454576), no VPN, signed out (2026-10-22 14:13 UTC) — PASS (task 9 fix verified, no VPN)
- 8 rows, 36 requests, 0.1 MB, 11 s; main rows 6/6, V pass 6, not as expected 0
- ladder: parts 8, rows 6, the same item 6, V pass 6, wrong 0, unknown 0
- slots 1-6 trials: S6 reads "unlabelled GET 206" 69-209 ms on X's host, refused 0, no "GET NETWORK 1-2 ms"; slot 4 GIF duration unknown (normal); slot 6 item 1 of 2, the same item yes
- slots 7 (age-limited), 8 (removed): NO_MEDIA_FOUND [recoverable], route B needs playback, X3 not run (no tab), no Master row, not "removed"
- Diff CHANGED (7) vs 14:05 UTC = an INVALID run (OLD APK 430da9f by mistake + bad mobile network in rain: main NETWORK 20-37 s on slots 1,2,4,8; V fail NETWORK ~5 s on 3,5,6; trials "GET NETWORK 1 ms" = the old bug). All 7 lines are improvements; against the real baseline (2026-10-21 15:56 UTC) main is unchanged.
- The 14:05 run does not count for anything (old APK). Next: A X with VPN Boston.

## A X — social-trial 36ec093, VPN Boston, signed out (2026-10-22 14:16 UTC) — PASS (task 9 fix verified, VPN)
- 8 rows, 36 requests, 0.1 MB, 25 s; main rows 6/6, V pass 6, not as expected 0
- ladder: parts 8, rows 6, the same item 6, V pass 6, wrong 0, unknown 0
- slots 1-6 trials: S6 reads "unlabelled GET 206" 385-866 ms on X's host, refused 0, no "GET NETWORK 1-2 ms"; slot 4 GIF duration unknown; slot 6 item 1 of 2, the same item yes
- slots 7, 8: NO_MEDIA_FOUND [recoverable], route B needs playback, X3 not run (no tab), no Master row, not "removed"
- Diff CHANGED (6) vs 10:48 UTC (the bug run): all 6 = Master trial failed NETWORK -> rows 3/1, V pass, same yes; no main line changed (main = baseline)
- X done on both networks. Next: A TikTok, VPN Boston (429 cooldown long over: last TikTok run ~10:4x UTC).

## A TikTok — social-trial 36ec093, VPN Boston, signed out (2026-10-22 14:18 UTC) — main PASS (task 10 verified); Master NOT PROVEN (429 again)
- 9 rows (slot 7 empty), 39 requests, 1.2 MB, 43 s; main 9/9, not as expected 0, V pass 6; Diff NO CHANGE vs 10:43 UTC
- task 10: no "download service" ask on any slot (every row "not reached: ... download service"); slot 8 GEO_RESTRICTED [final], slot 9 PRIVATE_OR_UNAVAILABLE [final] as before, main asks 2 each (phone + desktop page; before + service 403)
- slot 6 photo: NO_MEDIA_FOUND, hidden page data, route B T1 not run (final) - as expected
- Master: slots 8, 9 trials embed player 400 -> needs playback (T3 no tab); slot 1 T1 embed player GET 429 on its FIRST ask -> masterStop 429 (30 min), slots 2-5, 10 "Master not run: stopped (429)"; slot 1 rungs "T1 run (RATE_LIMITED) · T2 rows · T3 not run" but Master rows 0 (the stop)
- ladder: parts 4, rows 0, wrong 0 -> TikTok T1/T2 rows still unproven; 3rd run in a row with 429 on the first embed ask on the Boston exit (B0, 10:43, 14:18) -> owner decision needed (after IG)
- Next: Instagram (3 sessions: out/no VPN -> in/no VPN -> in/VPN Boston), then TikTok decision.

## A Instagram — social-trial 36ec093, no VPN, signed out (cleared) (2026-10-22 14:22 UTC) — PASS
- 10 rows, 46 requests, 2.0 MB, 30 s; Diff NO CHANGE vs 2026-10-21 15:00 UTC (Master not compared: ladder first time; slot 9 "new link")
- main rows on 1-4, 6, 7; V pass 6; slot 5 reel LOGIN_REQUIRED [browser] not as expected (= baseline, main's known signed-out gap); 8 photo-only NO_MEDIA_FOUND as expected; 9 sign-in-only LOGIN_REQUIRED as expected; 10 removed LOGIN_REQUIRED ambiguous (no session)
- ladder: parts 7, rows 6, the same item 6, V pass 6, wrong 0, unknown 0; S6 reads "unlabelled GET 206" ~70 ms, refused 0 (task 9 fix ok on IG)
- slot 7 carousel item 1 of 3, same yes, duration unknown; slot 8 route B I2 run (PHOTO_ONLY); slots 5, 9, 10 Master not run (browser-required), no Master row, not "removed"
- Next: IG signed in, no VPN.

## 2026-10-22 14:28 UTC — Instagram, no VPN, signed in (social-trial APK 36ec093, census, probe box OFF) — PASS
- Diff: NO CHANGE vs 2026-10-21 15:04 UTC (Master not compared — ladder first time; slot 9 "new link").
- Main: 8 of 8 should-download slots got rows (1–7, 9), all app API GET 200 (authorized); GraphQL/page/embed not reached; V pass 8, fail 0, unknown 0. Requests 37, 0.4 MB, 16 s.
- Slot 8 photo-only: NO_MEDIA_FOUND, as expected. Master route B: I2 PHOTO_ONLY -> NO_MEDIA_FOUND.
- Slot 10 removed: RESPONSE_CHANGED (app API 400, GraphQL 400, page 200 no data, embed 200 no data), not as expected, same as baseline (diff NO CHANGE), so a known main gap and not new. Master route B: I2 NEEDS_PLAYBACK, I3 not run (no tab); rows 0, so not wrong. Observation for later (not blocking): I2 doesn't treat a removed post as final; in the app it would go on to I3's tab.
- Ladder: parts 10, rows 8, same item 8, V pass 8, wrong 0, unknown 0; trials 8 (rows 8, V pass 8). Slot 7 carousel item 1 of 3 = same item. Slot 9 sign-in-only rows 106.3 s (main + Master).
- S6 reads: unlabelled GET 206, 42–76 ms on all 8 trials; refused 0; no GET NETWORK.
- Next: Instagram signed in + VPN Boston.

## 2026-10-22 14:31 UTC — Instagram, VPN Boston, signed in (social-trial 36ec093, census, probe box OFF) — PASS
- Diff: CHANGED (2) vs 2026-10-21 15:13 UTC (Master not compared — ladder first time):
  - slot 9 sign-in-only: main rows tallest 360 -> 718 (same 106.3 s; same as today's no-VPN 14:28). Instagram now serves a higher rendition. An improvement, not a regression.
  - slot 10 removed: RESPONSE_CHANGED [recoverable] -> LOGIN_REQUIRED [browser]. Asks: app API 400, GraphQL 400, page 200 no data he3b0c442 (authorized), embed page 200 "login page" he3b0c442 (unauthorized; at no-VPN 14:28 the embed gave no data h484533f7). So Instagram's embed shows its login page on the Boston exit. Both answers are not as expected: a known gap (S2d input). No Instagram main code change since 2026-10-21 (TASK 9/10 = S6 reader + TikTok only). Master not run (browser-required), so ladder parts 9.
- Main: 8 of 8 should-download slots got rows (1–7, 9), all app API GET 200 (authorized); GraphQL/page/embed not reached; V pass 8, fail 0, unknown 0. 37 requests, 0.4 MB, 27 s (slower on VPN).
- Slot 8 photo-only: NO_MEDIA_FOUND, as expected; route B I2 PHOTO_ONLY.
- Ladder: parts 9, rows 8, same item 8, V pass 8, wrong 0, unknown 0; trials 8 (rows 8, V pass 8).
- S6 reads: unlabelled GET 206, 376–849 ms; refused 0; no GET NETWORK.
- vs the R8 way (2026-10-21 15:04 / 15:13): Master rows 6 (slots 5 and 9 "needs playback"; tallest differed on some slots). The ladder now gets 8/8 with main's tallest on both networks.
- Instagram A done: all 3 sessions PASS (14:22 out/no VPN, 14:28 in/no VPN, 14:31 in/VPN Boston).
- Next: B2 Instagram probe (VPN off, Clear browsing data, probe box ON, IG slots 1, 5, 9 in YFT browser; probe box OFF after). TikTok decision after that (30-min stop ends 14:48 UTC; TikTok without the VPN likely impossible on the owner's network).

## B2 Instagram probe — slot 1 (social-trial 36ec093, VPN off, signed out, probe box ON?) — 2026-10-22 ~14:38 UTC — PARTIAL
- 1st lookup: "Play the video on this page, then tap Try again." Details only: `master: instagram trial: main's 1 rows set aside` + `master: fresh natural playback required`. No ladder line, no probe line.
  - Code (546f11c): that line is MasterMainSelection.select's NeedsPlayback at the PLAYBACK_CAPTURE stage; it returns a new one-line details list, so the engine's earlier lines (ladder I2, probe) are dropped. So the 1st lookup's ladder/probe outcome can't be seen. Why I2 gave no rows here (the census got I2 rows without playback): unknown. Candidates: the probe's ask (checkpoint/429 -> S7 stop or cooling) or the lookup's time budget. Check the 2nd lookup's Details.
- 2nd lookup (Try again): banner `master: instagram ladder after RESPONSE_CHANGED; main's answers read, no new ask; trial: Master's rows 1, main's 1; the same item: yes; duration within 1 s: yes; tallest: Master's 612, main's 612`. Sheet: 720p HD 612x612 MP4 994 KB, M4A + MP3 audio, 0:09. Probe line not shown yet (Details not opened).
- Possible finding (to confirm): the capture stage's NeedsPlayback drops Master's details, so on slots 5/9 (LOGIN_REQUIRED, signed out) the probe line may be lost too.
- Next: the user opens that sheet's Details (2nd lookup) and sends a screenshot of all lines.
- Slot 1 follow-up (9:18 phone time): the success sheet's "Details" = the file's info only (612x612, 0:09, 994 KB, MP4; FPS/codec/bitrate unknown); the banner = the trial line only. Code: `SocialLadder.trialNotice` shows only the first `master: <site>` line with "; trial:"; the sheet shows Master's lines only for a failed lookup (`failureDetails`). So the probe line can't be seen in the app when rows are found. Test-design gap in §7.4 B ("one probe Details line per lookup"): slot 1's probe NOT OBSERVABLE. Not a FAIL: no row came from the probe; Master's row is correct (same item, 612 = 612).
- Slots 5 and 9 (signed out, LOGIN_REQUIRED, browser-required): engine keeps its details on that path (needsAuthorizedPlayback -> continue -> NeedsPlayback(safeDetails + ...)), so the probe line should show in the failure Details. Next: slot 5, screenshot Details before any playback or Try again.

## B2 Instagram probe — slot 5 (reel; VPN off, signed out, probe box ON) — phone 9:09 (~14:39 UTC) — PROBE PASS
- 1st lookup: main Detected 1 row (signed out!) -> trial set it aside -> "Play the video on this page, then tap Try again."; Details only `master: instagram trial: main's 1 rows set aside` + `master: fresh natural playback required` (same as slot 1's 1st lookup; Master's lines dropped by the capture's NeedsPlayback).
- 2nd lookup (Try again): main LOGIN_REQUIRED (session none; GraphQL GET 401; page 200 664,682 chars no post; embed page 200 219,155 chars "video without files"). Ladder after LOGIN_REQUIRED, no new ask: I2 GraphQL LOGIN_WALL (401), page SHAPE_CHANGED (no video read), embed page SHAPE_CHANGED (no video read); I2 LOGIN_WALL -> next BROWSER.
  - **`master: instagram probe: HTTP 200, 351862 bytes; the post's video in the answer: yes; a wall on the page: no`** -> one probe line, no row from it, no cooldown (200). The signed-out query via the tab's own fetch HAS the video main can't get signed out. Evidence for S2d part 2 (the probe's rung), owner's decision.
  - Then `master: one browser-assisted capture requested` -> needs playback (no row). Lines below cut off in the screenshot.
- Observations for the coding agent (not FAILs): (a) on slots 1 and 5 the 1st (trial) lookup: Master gave no row although main had 1, and slot 1's Try again gave I2 rows; cause hidden because (b) MasterMainSelection's NeedsPlayback at PLAYBACK_CAPTURE returns a new one-line details list, dropping the engine's lines (ladder, I2, probe). (c) Main's signed-out answer for slot 5 varies between lookups (1 row, then LOGIN_REQUIRED). (d) I2 reads main's signed-out embed page as SHAPE_CHANGED where main reads "video without files".
- Next: slot 9 the same way; then the probe box OFF.

## B2 Instagram probe — slot 9 (VPN off, signed out, probe box ON) — phone 9:10 (~14:40 UTC) — PROBE LINE SEEN, cut off; link to confirm
- Page: /p/… "Age-restricted content … Log in to continue."; sheet title: the old photo post's. Might be the OLD slot 9 link (the single age-gated photo, replaced 2026-10-21 15:13 by a sign-in-only reel: census slot 9 rows 718, 106.3 s). Ask the owner to open the census's slot 9 link.
- Details: main LOGIN_REQUIRED (session none; GraphQL GET 401; page 200 783,170 chars no post; embed page 200 172,349 chars no post). Ladder after LOGIN_REQUIRED, no new ask: I2 GraphQL LOGIN_WALL (401), page SHAPE_CHANGED (no video read), embed page NEEDS_PLAYBACK (no post data in the page); I2 LOGIN_WALL -> next BROWSER; `master: instagram probe: HTTP 200, 1098…` (rest cut off: video yes/no, wall yes/no). No row; never "removed".
- UX observation (not a FAIL): the final message is "Play the video on this page, then tap Try again." on an age-restricted page with no video to play (main's reason LOGIN_REQUIRED). Signing in is the only thing that helps here; the message should say so. For the coding agent with S2d part 2.
- Next: open the census's slot 9 link, Download -> Details, scroll to the bottom; then the probe box OFF.

## B2 Instagram probe — slot 9 NEW link (census slot 9, /reel/…, VPN off, signed out, probe box ON) — phone 9:25–9:26 (~14:56 UTC) — PROBE PASS
- Page: "People under 21 can't see this content. This account has set limits on who can see their profile and content." (signed out). So the earlier /p/… page was the OLD link (the age-gated photo).
- Details: main LOGIN_REQUIRED (session none; GraphQL GET 401; page 200 572,267 chars no post; embed page 200 172,349 chars no post). Ladder after LOGIN_REQUIRED, no new ask: I2 GraphQL LOGIN_WALL (401), page NEEDS_PLAYBACK (no post data), embed page NEEDS_PLAYBACK (no post data); I2 LOGIN_WALL -> next BROWSER.
  - **`master: instagram probe: HTTP 200, 157 bytes; the post's video in the answer: no; a wall on the page: …`** (the wall part cut off again; not needed). One probe line, no row, no "removed".
- Same UX observation: "Play the video on this page, then tap Try again." over an age wall with no video to play.

## B2 summary (Instagram tab-fetch probe, signed out, no VPN) — DONE
- Slot 1: Master's row right on Try again (same item, 612 = 612); probe line NOT OBSERVABLE in the app (row lookups show only the trial line; the 1st lookup's capture NeedsPlayback dropped Master's lines).
- Slot 5 (reel main can't get signed out): probe HTTP 200, 351,862 bytes, video YES, wall no -> a probe rung (S2d part 2) would rescue it.
- Slot 9 (age-gated reel, 21+): probe HTTP 200, 157 bytes, video NO -> sign-in only; a probe rung can't help.
- No row from the probe, no second probe, no "removed", no 429/checkpoint (all 200). Owner decision later: S2d part 2 (the probe's rung), with evidence 1 of 2 signed-out slots rescued.
- For the coding agent: (a) 1st trial lookup on slots 1 and 5 -> no Master row though main had 1; (b) capture's NeedsPlayback drops the engine's details; (c) main's signed-out answer for slot 5 varies; (d) I2 reads signed-out embed page as SHAPE_CHANGED where main reads "video without files"; (e) "Play the video…" message over age/login walls; (f) §7.4 B's "one probe Details line per lookup" isn't visible on row lookups.
- Next: probe box OFF; TikTok once on another US VPN city (Boston: 3 x 429 on the first embed ask).

## A TikTok — social-trial 36ec093, VPN NEW YORK (not Boston), signed out (2026-10-22 14:59 UTC) — PASS (Master T1 proven)
- 9 rows (slot 7 empty), 56 requests, 1.5 MB, 57 s. Diff NO CHANGE vs 14:18 UTC (Boston): main identical on another US exit, slot 8 GEO_RESTRICTED too. Notes: Master not compared (earlier run 429) on slots 1–5, 10.
- Main: 6/6 rows (1–5, 10), V pass 6, not as expected 0; every row "not reached: … download service" (task 10 again). Slot 6 photo NO_MEDIA_FOUND, as expected (hidden page data); 8 GEO_RESTRICTED [final]; 9 PRIVATE_OR_UNAVAILABLE [final].
- Master (ladder): parts 9, rows 6, same video 6, V pass 6, wrong 0, unknown 0; trials 8 (rows 6, V pass 6); route B 1 (slot 6: T1 not run (final), no request).
  - Slots 1–5, 10: T1 rows (embed player GET 200 data hf511ee28 596–850 ms; S6 unlabelled GET 206 368–2264 ms); T2/T3 not reached. NO 429 on the New York exit -> the 429 (B0, 10:43, 14:18) is the Boston exit's.
  - TikTok difference confirmed (owner's pending "TikTok differences" decision): Master 1 row per post vs main's up to 4; tallest equal on 2, 3, 4, 5, lower on 1 (1276 vs 1920) and 10 desktop-only HD (1280 vs 1920) -> "T1 <= 1280". Same video and duration yes everywhere. T2 (cookie V fail) not reached -> still untested.
  - Slots 8, 9: embed player 400 -> T1 run (TRANSIENT) · T2 run (PLAYBACK) · T3 not run (no tab) -> needs playback, no row, not "removed".
- Deviation from §7.4 ("B0's US VPN exit (Boston)"): New York used because Boston's embed player 429'd 3 times on the first ask; main unchanged (NO CHANGE). Recommend New York for TikTok's later VPN runs and W's VPN run (owner's call).
- Correction: B3 (short regression set) is `yft-d7-social-trial` per MASTER_KEY_SOCIAL_PLAN §7.4 (an earlier message wrongly said `yft-d7-normal`). No APK switch before C.
- B3 status: FB link 10 done (valid after task 9); IG slot 1 done in B2 (same item, 612 = 612). Left: TikTok 6 + 1 (VPN on, now), then VPN off: FB link 1, X 6 + 8, IG 8.

## B3 — TikTok slot 6 (VPN New York, signed out, social-trial) — PASS
- Message: "This TikTok post has no downloadable video." (main's words)
- Details: adapter tiktok NO_MEDIA_FOUND; tab · page script · found · API; answers kept 0; JSON read; post id matches
- `master: tiktok ladder: PHOTO_ONLY (the link: a photo address); next: FINAL; not asked`; master: page reports NO_MEDIA_FOUND
- No Master request made. PASS.

## B3 — TikTok slot 1 (VPN New York, signed out, social-trial, 9:40 phone ≈ 15:10 UTC) — PASS
- Banner: `master: tiktok ladder after RESPONSE_CHANGED; replayed 0; new 1; trial: Master's rows 1, main's 3; the same video: yes; duration within 1 s: yes`
  ("after RESPONSE_CHANGED" = the trial's set-aside of main's rows, per SocialTrialTest's design.)
- Sheet: only the post's rows (thumbnail + title match, 0:43).
  - Video: one row, 720p · HD · 718×1276 · MP4 · 1.7 MB (Master's row; same ≤1280 cap seen in the census)
  - Audio rows: ~1.6 MB (top row, label cut off), MP3 192 kbps ~1008 KB, MP3 128 kbps ~672 KB (all "Slow", made on the phone)
- Found on this page: 1. No other video's rows. PASS.
- Next: VPN off → FB link 1, X slot 6, X slot 8, IG slot 8.

## B3 — Facebook link 1 (no VPN, signed out, social-trial, 9:43 phone ≈ 15:13 UTC) — PASS
- Banner: `master: facebook ladder after RESPONSE_CHANGED; replayed 0; new 1; trial: Master's rows 2, main's 3; the same video: yes; duration within 1 s: yes`
- Sheet: the watch link's video, 0:44; Found on this page 1.
  - Video: Master's 2 rows, both "Quality unknown · As the page plays it · MP4", 1.7 MB and 4.5 MB
  - Audio: M4A "Original sound… The video's own sound" (size unknown); MP3 320 ~1.7 MB, 192 ~1 MB, 128 ~695 KB
- The S5 note still stands: Master rows 2 vs main 3, and both rows "Quality unknown" → fix or list in docs/SUPPORT_MATRIX.md before release.
- Next: X slot 6 (video 1's rows only), X slot 8, IG slot 8 (all no VPN).

## B3 — X slot 6 (no VPN, signed out, social-trial, 9:45 phone ≈ 15:15 UTC) — PASS ⚠️ (rows only after Try again)
- 1st lookup: "Play the video on this page, then tap Try again." Details: `master: x trial: main's 3 rows set aside` / `master: fresh natural playback required`. The sheet had the page title only (no duration). The signed-out page showed the post's text with no video player.
- After Try again: `master: x ladder after RESPONSE_CHANGED; main's answer replayed, no new ask; trial: Master's rows 3, main's 3; the same item: yes; duration within 1 s: yes`
  - Sheet: the post's video, 0:06. Video 720p 720×720 568 KB, 480p 540×540 375 KB, 360p 320×320 176 KB, plus MP3 rows. Found on this page 1.
  - Only video 1's rows: the census bug of 8 rows covering both videos is fixed.
- Observation (g) for the coding agent: same pattern as B2 (a)/(b) on Instagram. The 1st trial lookup ends with MasterMainSelection's NeedsPlayback (PLAYBACK_CAPTURE), with no X1 line; X1's replay only answers on Try again. Check whether the social ladder runs before PLAYBACK_CAPTURE's NeedsPlayback on the 1st lookup (or whether LookupRecord/MainPages is missing then). Also check whether NeedsPlayback drops the ladder's lines.
- Next: X slot 8 (needs playback → main's reason, never "removed"), then IG slot 8.

## B3 — X slot 8 (no VPN, signed out, social-trial, 9:48–9:49 phone ≈ 15:18–15:19 UTC) — PASS
- Page: X's "We're unable to display this post" (title "Post Not Found - X | 404 Error"), with no video player.
- Lookup and Try again gave the same answer: "Play the video on this page, then tap Try again." with Try again.
  - Details: `adapter x: NO_MEDIA_FOUND` / `embed answer GET 200 (292 characters)` / `answer: X shows no post (tombstone)` / `master: fresh natural playback required`
- Never "removed". Main's reason NO_MEDIA_FOUND stays and Try again is offered, matching SocialLadderXTest (c) (task 0).
- Notes for the coding agent:
  - (b) again: there's no X1 line in Details (the test expects X1's needs-playback line), so NeedsPlayback at PLAYBACK_CAPTURE likely drops the ladder's lines.
  - (e) again: "Play the video…" is shown over a page that has no video to play. Try again repeats the same answer, so the user can loop.
- Next: IG slot 8 (photo, signed out, no VPN), then C (W).

## B3 — Instagram slot 8 (no VPN, signed out, social-trial, 9:51 phone ≈ 15:21 UTC) — PASS
- "This post has photos only, no video." (banner + sheet, Try again), a photo post.
- Details: adapter instagram NO_MEDIA_FOUND; session none; GraphQL GET failed (LOGIN_REQUIRED 401); page 200 (~815k chars), photos only;
  `master: instagram ladder after NO_MEDIA_FOUND; main's answers read, no new ask`; I2 main's GraphQL LOGIN_WALL (401); I2 main's page PHOTO_ONLY; `I2: PHOTO_ONLY …; next: FINAL`; `probe: not asked (the answer is final)`; master: page reports NO_MEDIA_FOUND.
- Same on Try again. No new ask.
- The probe line only appears with InstagramTabProbe in context, so the **probe box is still ON** (from B2). Turn it OFF before C.

## B3 verdict: PASS ✅ (8/8: FB 10, FB 1, IG 1, IG 8, TikTok 6, TikTok 1, X 6, X 8)
- Note: X slot 6 gave rows only after Try again (observation g). X slot 8 has no X1 line in Details (b) and shows "Play the video…" with no video on the page (e).
- Next: C1 = W, signed out, probe box OFF, VPN New York (recommended over Boston; owner to confirm).

## Owner confirmation 2026-10-22 (~15:23 UTC): B2 Instagram slot 9 links
- 1st try was by mistake: the OLD photo link (the age-gated photo post). Not counted.
- 2nd try is the one that counts: the census's slot 9 age-restricted reel. Probe HTTP 200, 157 bytes, video NO, so sign-in only.
- The B2 conclusion stands: a probe rung could rescue slot 5 but not slot 9.

## C1 — W first run, social-trial 36ec093, VPN New York, signed out, probe box OFF, 2026-10-22 15:25 UTC — PASS ✅
- Owner's note: the VPN's "New York" location has many IPs, so this exit IP may differ from the 14:59 New York TikTok run. That's fine: the diff baselines by (VPN on/off, session), not by IP or city. A future 429 → note the location (and IP if shown) as the exit's problem.
- Header: `Weekly check W (S3): per site at most 60 s, 2.0 MB and 24 requests…; Master's part on slot 1 only.` Slots: FB 1, 8, 13 · TikTok 1, 6, 9 · IG 1, 8, 10 · X 1, 6, 8.
- Diff: `FIRST RUN — nothing to compare yet`, and every site says "no baseline yet (VPN on, signed out); this run starts it".
  - The screen's first line comes from SiteWeekly.headline = "W (weekly check): " + the same diff headline, with no " · not run/stopped" suffix since every site ran.
- Spend (caps 24 req / 2.0 MB / 60 s): FB 11 / 1.3 MB / 18 s; TikTok 13 / 0.4 / 23; IG 11 / 0.6 / 12; X 9 / 0.0 / 7. All within caps. Whole run 59.5 s.
- Slot 1 ladder on each site: rows, same video/item yes, duration within 1 s yes, wrong 0, V pass 206
  - FB: F1, Master 2 vs main 3
  - TikTok: T1, 1 row tallest 1276 vs main 4 rows tallest 1920
  - IG: I2, 612 = 612
  - X: X1, 3 = 3
- Every other slot: `Master not run: W: main only`.
- No 429, no cooldown, no site "not run". JSON `"mode":"weekly"`, `"vpn":true`.
- Main as expected on every slot:
  - FB: 1 rows, 8 share rows, 13 PRIVATE_OR_UNAVAILABLE [final]
  - TikTok: 1 rows; 6 NO_MEDIA_FOUND; 9 PRIVATE_OR_UNAVAILABLE [final]; no download service asked
  - IG: 1 rows; 8 NO_MEDIA_FOUND; 10 LOGIN_REQUIRED [browser], ambiguous (no session), normal when signed out
  - X: 1 rows; 6 item 1 of 2; 8 NO_MEDIA_FOUND [recoverable], never removed
- Next: C2 = W, VPN OFF, signed out. Expect "W (weekly check): NOT COMPARED — no baseline yet for this run's sites", the same caps, slot 1 ladder rows.

## C2 — W, no VPN (mobile data), signed out, 2026-10-22 15:30 UTC — PASS ✅
- Diff: `NOT COMPARED — no baseline yet for this run's sites` (as predicted).
  - Notes: FB, IG and X "no baseline yet (VPN off, signed out); this run starts it". TikTok: "no baseline yet (VPN off, signed out)" plus "stopped before every slot's main (time): not a baseline".
  - Screen first line, by SiteWeekly.headline: "W (weekly check): NOT COMPARED — no baseline yet for this run's sites · TikTok stopped (time), the rest not run".
- Spend:
  - FB 11 req / 1.4 MB / 13 s; IG 11 / 0.6 / 7; X 9 / 0.0 / 6
  - TikTok `stopped (time): the rest not run`, 2 req / 0 MB / 60 s. Whole run 86 s.
- TikTok without VPN can't be reached on the owner's network (owner: "No VPN = Can't Reach Tiktok").
  - The phone page GET NETWORK took 20.1 s. The desktop page GET NETWORK (variant) took 20.0 s, plus the hidden page. Slot 1 NETWORK [final], "not as expected"; slot 6 not looked up (time); slot 9 not looked up (site stopped).
  - Plan §7.4 C(2) says "TikTok's lines there are what W reads on that network, not a failure". The time cap held (60 s).
  - Note for the coding agent (optional): after the first NETWORK failure on the same host, the desktop variant ask and the hidden page spend about 35 s more. TikTok can never get a no-VPN W baseline on this network.
- Slot 1 ladder: FB F1 (2 vs 3), IG I2 (612 = 612), X X1 (3 = 3). Each had same yes, duration yes, wrong 0, V pass. Other slots `W: main only`.
- Main as expected (same as C1).
  - FB slot 13's mark is "no data" here vs "no post" in C1 (VPN): same shape h0cd5c634, same reason PRIVATE_OR_UNAVAILABLE [final]. The baselines are per network, so there's no diff impact. Just noted.
- JSON `"mode":"weekly"`, `"vpn":false`.
- Next: C3 = W, Instagram signed in, no VPN (other sites signed out). Expect IG "no baseline yet (VPN off, signed in); this run starts it". FB and X will be compared against C2. TikTok stops (time) again.

## C3 — W, no VPN, Instagram signed in (others signed out), 2026-10-22 15:35 UTC — PASS ✅
- Diff: `NO CHANGE — against the run of 2026-10-22 15:30 UTC`. FB and X were compared with C2: W's first real comparison, a bonus, same day.
  - Notes: TikTok "no baseline yet (VPN off, signed out)" plus "stopped before every slot's main (time): not a baseline"; Instagram "no baseline yet (VPN off, signed in); this run starts it".
  - Screen first line: "W (weekly check): NO CHANGE — against the run of 2026-10-22 15:30 UTC · TikTok stopped (time), the rest not run".
- Spend:
  - FB 11 req / 1.4 MB / 11 s; IG 9 / 0.2 / 4; X 9 / 0.0 / 3
  - TikTok stopped (time), 2 / 0 / 60 s, no network without VPN, the same as C2. Whole run 78 s.
- Instagram, signed in (session:true on all 3 rows, so the sign-in took):
  - slot 1: main app API 200, 1 row, tallest 640 (612 signed out). Master I2 1 row, 640, same item yes, duration yes, wrong 0, V pass.
  - slot 8: NO_MEDIA_FOUND [recoverable] (app API no data), as expected.
  - slot 10 (removed): RESPONSE_CHANGED [recoverable], "not as expected (another answer)". Asks: app API 400, GraphQL 400, page no data, embed no data. Same as A's 14:28 signed-in run: the known S2d gap, not new.
- FB slot 1 F1 (2 vs 3) and X slot 1 X1 (3 = 3): same yes, duration yes, wrong 0. Other slots `W: main only`, main as in C2.

## C verdict (W's first runs): PASS ✅ — C1 VPN New York, C2 no VPN, C3 IG signed in
- Every site that could run stayed within its caps (24 req / 2 MB / 60 s). Slot 1 ladder rows on every reachable site, wrong 0. No 429, no cooldown.
- TikTok without VPN can't be reached. It stops at the 60 s cap and is never a baseline on that network; the plan says this is not a failure. TikTok's W is VPN-only in practice.
- Next: owner decisions (1 TikTok differences, 2 S2d probe rung, 3 New York exit, 4 S5 amendment), then the hand-off for the coding agent (observations a–g + notes).

## 2026-10-23 — the retest of §7.4 on `social-trial` c3e9147 (run 39878500080)
Rebuilt from the owner's summary of the session (task 15's brief); the phone's own notes, links
and posts stay on the phone. No VPN and signed out unless said; the VPN's New York exit for TikTok
and W's VPN run. Times: only W's VPN run was logged in UTC (02:24).

### Instagram slot 5, signed out, no VPN — the rung's box ON against OFF, 2 of 2 each — PASS (decision 24's goal met)
- ON: a row on the first Download, both times: the rung rescues the post.
  - No banner on that lookup: main answered `LOGIN_REQUIRED` (no trial comparison), and neither
    Instagram's ladder line nor "master: instagram probe: …" showed (F1). Task 13's observation (f)
    holds only when I2 gives rows.
  - The row: "Quality unknown", no height read (F1).
- OFF: no row, the sign-in words, both times.
  - Main's own Details call the embed page (about 227k chars) "video without files"; I2 read the
    same page as "SHAPE_CHANGED (no video read)", then `LOGIN_WALL` → `BROWSER`, so the playback
    words' rule (the site's answer names the post's video) never fired (F2).

### Instagram slots 1, 9 and 8, signed out, no VPN — PASS
- Slot 1: I2's rows; the banner: the trial line and "probe: not asked (I2's rows)".
- Slot 9 (the age wall): the sign-in words and the words line.
- Slot 8 (photos only): the probe not asked. Its final answer still offers Try again (F3b).

### Instagram signed in, no VPN, the rung's box ON
- The proof box OFF, slot 1: "probe: not asked (I2's rows)", the same item yes, 640 = 640 — PASS.
- The proof box ON, slot 1 (one video): "probe: not asked (signed in: the rung is the signed-out
  query)" — the signed-in rule works. But I2's rows were set aside and the rung was not asked, yet
  Master still gave 1 row, and the banner said "the same item: no" (the duration yes, 640 = 640),
  with no "I2's rows set aside (the rung's proof)" line (F4).

### X, signed out, no VPN — PASS
- Slot 6: 3 = 3 on the first lookup (observation (g) fixed).
- Slot 8: main's words.
- Slot 7 (sensitive): the sign-in words, `AGE_LIMITED`.

### Facebook, signed out, no VPN — PASS
- Link 10's group-post form: the trial's "Master found none" with its step (as B4).
- Link 1: Master's 2 rows against main's 3 (as B3).

### TikTok, VPN New York, signed out — PASS
- Slots 6 and 1, as B3. Slot 6's final answer (photos only) still offers Try again (F3b).

### The failure sheet (any long failure)
- Long Details and the Try again button under them were cut off: the sheet does not scroll (F3).

### W — VPN New York, signed out, 02:24 UTC — Facebook, TikTok and X NO CHANGE; Instagram BREAKING (6)
- Instagram: every ask on all three slots got the wall — GraphQL 401; the page and the embed page:
  the login page.
- Main's Instagram code is unchanged since C1's APK, so the exit's address walled it, not the app.
- The walled run became Instagram's baseline for VPN on, signed out, so the next run from a good
  address would read CHANGED (F5).

### W — no VPN, signed out, minutes later — NO CHANGE
- Instagram slot 1's rows, I2 612 = 612.

### W — no VPN, Instagram signed in — NO CHANGE against C3

### Verdict of 2026-10-23
- PASS: B3's short set, X's first lookup (observation (g)), the rung's rescue of slot 5 (decision
  24), the signed-in rule, task 14's words (decision 27), W's NO CHANGE on New York for Facebook,
  TikTok and X, and without the VPN (signed out; Instagram signed in).
- To fix (task 15): F1–F5 and F3b; then the retest (`MASTER_KEY_SOCIAL_PLAN.md` §7.4, click by click).

## 2026-10-23 — S4's phone session on `social-trial` 2dcd0d1 (run 39948738272)
Rebuilt from the owner's screenshots and messages in the session; the phone's own notes, links and
posts stay on the phone. As an Update; no VPN and signed out unless said; the VPN's New York exit
for TikTok. No census run and no W in steps 1–4. The drill's versions published 13:18–13:49 UTC
(`MASTER_KEY_SOCIAL_PLAN.md` §7.4).

### Step 1 — version 1 (`defaults`) — PASS
- The census screen: `recipes: remote version 1, signed`.

### Step 2 — the kill switch (`drill-rung-off`, version 3) — PASS
- The label went on twice: versions 2 and 3 are both `drill-rung-off` (the workflow's runs
  39960156863 and 39960304422); the step ran on version 3. A label goes on once from now on.
- Instagram slot 5, the rung's box ticked: no row; the playback words with Try again; `master:
  instagram probe: off (config); not run`; no `probe: HTTP …` line.
- L3 seen: a page main's line calls "no post" → I2's `NEEDS_PLAYBACK (no post data in the page, as
  main's rule reads it; …)`, not `SHAPE_CHANGED`.

### Step 3 — a key changed by config (`drill-key-change`, version 4, run 39961695997) — PASS, with O1
- Facebook link 1: Master's rows as before, 720p · HD (720 × 720) and 360p (400 × 400); under the
  trial line `master: facebook contract: 5 files by shape (key table changed)`; the 720p download
  plays with sound, the right video; L1: the trial line ends `vs not stated`.
- O1: the trial line's Master's rows 3 (720, 400, 224) and "duration within 1 s: no"; the sheet
  showed the 2 rows, its found list one video. A bug (task 18): the open read took the plugin
  player's stand-in file (one 400 × 224 frame of 0.03 s) and the video's own page (twice; the
  media check refused it).
- Instagram slot 5, the box still ticked: 720p · HD 720 × 1280 on the first Download; `probe: HTTP
  200 …; rows 3 (DIRECT)`.
- The census line once read `recipes: remote version 4, signed (last check: no answer in time)`:
  a later ask unanswered in time, version 4 kept.

### Step 4 — back to the defaults (`defaults`, version 5, run 39964441458) — PASS
- Facebook link 1: no key table line; `Master's rows 2, main's 3; … duration within 1 s: yes;
  heights 720,400 vs not stated`.
- The rung's box unticked.

### Step 5 — B3's short set (version 5) — PASS
- Facebook link 1: step 4's lookup. Link 10 (the group post): "Facebook trial: main's rows were set
  aside and Master found none. Tap Try again."
- Instagram slot 1: 720p · HD (612 × 612); `Master's rows 1, main's 1; the same item: yes; duration
  within 1 s: yes; tallest: 612 vs 612` (I2; the probe off). Slot 8: "This post has photos only,
  no video.", no Try again.
- X slot 6: `Master's rows 3, main's 3; the same item: yes; duration within 1 s: yes` on the first
  Download (720p, 480p, 360p). Slot 8: main's words ("X shows this post only to signed-in
  viewers…"), no row.
- TikTok, VPN New York: slot 6 "This TikTok post has no downloadable video.", no Try again; slot 1
  720p · HD (718 × 1276), `Master's rows 1, main's 3; the same video: yes; duration within 1 s:
  yes`.

### TikTok census — VPN New York, signed out, 14:15 UTC (version 5) — PASS, not counted for S5
- Wrong rows 0; NO CHANGE against the run of 2026-10-22 14:59 UTC; all 9 slots as expected; the
  report's notes `recipes: remote version 5, signed`.
- Not counted: task 18 changes the engine (decision 26); S5 starts on task 18's APK after the
  re-drill.

### Verdict of S4's phone session
- PASS: steps 1–5 — the signed config, the kill switch, a key changed by config and back, B3's short
  set; L1 and L3 seen.
- O1 fixed by task 18 (`ac0dd2c`); next: the re-drill (`MASTER_KEY_SOCIAL_PLAN.md` §7.4), then S5
  from its first run.
