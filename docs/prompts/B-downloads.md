# Agent B — YouTube progress from the first seconds (P41) → Delete file (P42)

**ရည်ရွယ်ချက်:** (P41) YouTube (live recording ၁ နာရီလို video ကြီးတွေပါ) Download နှိပ်ပြီး **၃ စက္ကန့်
အတွင်း** % နဲ့ speed စရွေ့မယ် (app ထဲနဲ့ notification မှာ)၊ download စုစုပေါင်း အချိန် မနှေးစေရ။ (P42)
Downloads မှာ ပြီးသွားတဲ့ video ရဲ့ ⋮ menu ထဲ **Delete file** — သိမ်းထားတဲ့ ဖိုင်ကိုယ်တိုင် (Download/YFT၊
Library၊ Files app ထဲက) ဖျက်ပြီး list ကပါ ဖယ်မယ် ("Delete this file?" မေးပြီးမှ)။ "Remove from list" က
ဖိုင်ထားခဲ့ချင်ရင် အတွက် ဆက်ရှိမယ်။

**အကြောင်းရင်း:** YouTube file ကို 10 MiB အပိုင်းတွေနဲ့ ၃ ပိုင်းတစ်တွဲ ဆွဲတာ — အပိုင်းတစ်ခုလုံး ပြီးမှ %
တိုး (အပိုင်းထဲမှာ progress မပြ)၊ တွဲထဲက အနှေးဆုံးပိုင်း ပြီးမှ နောက်တွဲ စ၊ length မသိရင် အရင် probe
request တစ်ခု ထပ်လုပ်။ VPN/နှေးတဲ့ network မှာ ပထမ 10 MiB ပြီးဖို့ ကြာလို့ 0% မှာ ကြာကြာ ရပ်နေတာ။ Delete က
record ကိုပဲ ဖျက် ("Remove from list")၊ ဖိုင်က ကျန်။

**အချက်အလက်:** Phase 15 · **Agent B** · branch `work/phase-15-downloads` · P41 (Medium, 4–6 နာရီ) → P42
(Easy–Medium, 3–5 နာရီ) · Agent A နဲ့ C နဲ့ **တပြိုင်နက်** လုပ်လို့ရ (ဖိုင်ချင်း မထိ)။ Agent B က နောက်ဆုံး
P44 (merge + Preview #7) ကိုလည်း လုပ်မယ် (`M-merge-preview7.md`)။

**သုံးနည်း:** Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။ P41 ပြီးရင် P42 ကို
ကိုယ်တိုင် ဆက်လုပ်ပြီး `READY FOR MERGE` လို့ပြောပြီး ရပ်ပါမယ်။ Agent ၂ ယောက်ပဲ သုံးရင် P42 ပြီးမှ ဒီ chat
ထဲမှာ `C-ads.md` ကို `BRANCH_OVERRIDE: work/phase-15-downloads` နဲ့ paste လုပ်ပါ။ Default: fast start
(`FAST_START=ON`)၊ Delete မလုပ်ခင် မေး (`DELETE_CONFIRM=ON`)။ Computer မှာ SSH key မရှိရင် agent က key
အသစ်လုပ်ပြီး public key တစ်ကြောင်း ပြပါမယ် — Deploy keys မှာ "Allow write access" နဲ့ ထည့်ပြီး "SSH Done"
လို့ ပြောပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P41](../FIX_ADD_PLAN.md#p41--youtube-progress-and-speed-from-the-first-seconds),
[P42](../FIX_ADD_PLAN.md#p42--downloads-delete-file)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Room, OkHttp, Media3, MediaExtractor/MediaMuxer, LAME via NDK).

Repository: https://github.com/Alalkipgen/YFT
Agent: B — download engines and the Downloads and Library screens (one of three agents at once;
       later the integrator, P44)
Tasks: P41 — YouTube: progress and speed from the first seconds
       P42 — Downloads: Delete file
Branch: work/phase-15-downloads (first start: create it from origin/work/phase-15-integration)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none       (examples: FAST_START=OFF does only P41 steps 1, 2 and 6;
                           DELETE_CONFIRM=OFF deletes without the dialog)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the Result.

COMPUTER, FOLDER AND PUSH
- Work only in your own folder /data/YFT-B (the three agents use /data/YFT-A, /data/YFT-B and
  /data/YFT-C, because they may share one computer). First start, if it is missing:
    git clone https://github.com/Alalkipgen/YFT.git /data/YFT-B
- Push with SSH, using the key whose path is in /data/.ssh/CURRENT_KEY (it pushed on
  2026-10-09). If that file or key is missing, or a push says "Permission denied", make a NEW
  key (never search the computer for other keys):
    mkdir -p /data/.ssh && ssh-keygen -t ed25519 -N "" -f /data/.ssh/yft_b_<date> -C "yft-b-<date>"
    echo /data/.ssh/yft_b_<date> > /data/.ssh/CURRENT_KEY
    ssh-keyscan github.com >> /data/.ssh/known_hosts
  show the owner the one line of the .pub file and wait until he says he added it (GitHub >
  YFT > Settings > Deploy keys, "Allow write access"). Then, in your folder:
    git remote set-url origin git@github.com:Alalkipgen/YFT.git
    git config core.sshCommand "ssh -i $(cat /data/.ssh/CURRENT_KEY) -o UserKnownHostsFile=/data/.ssh/known_hosts -o IdentitiesOnly=yes"
  Never print a private key or a token. A failed push: stop and tell the owner (a local commit
  is not a handoff).
- Never touch another agent's folder or branch. One Gradle build at a time on this computer: if
  pgrep -af "[G]radleDaemon" shows a build you did not start, wait for it; never kill it.

YOUR FILES (docs/FIX_ADD_PLAN.md 0.7) — change nothing else
- core-download/**; core-model/src/main/kotlin/com/alal/yft/core/model/download/**;
  app/src/main/java/com/alal/yft/download/**; app/src/main/java/com/alal/yft/feature/{downloads,
  library}/**; app/src/androidTest/java/com/alal/yft/{download,background,delete}/** (delete is
  new) and app/src/androidTest/assets/{mux,mp4,mp3}/**; the tests beside them.
- Shared docs, your section only: docs/SESSION_STATE.md "## Agent B ...", CHANGELOG.md
  "### Phase 15 — Agent B (P41, P42)", docs/TEST_MATRIX.md "### Agent B — P41, P42".
- Never: extractor-*, core-browser, core-media, app/.../detection, app/.../feature/{browser,home,
  detectedmedia} (Agent A); core-model/.../media, app/.../feature/quickdownload (Agent C); the
  manifest, res/, Gradle files, docs/FIX_ADD_PLAN.md, docs/prompts/**, .github/workflows/**.
  Agents A and C push their own branches at the same time. Need a change outside your files?
  Do not make it: write "Hand-off to <agent>: <file> — <change> — <why>" in your SESSION_STATE
  section and report it.
- Contracts: the public DownloadQueue API, DownloadTask, DownloadProgress, DownloadFailure,
  DownloadFailureReason, AudioVideoMuxStage, DownloadDestination, DownloadEnqueuer and
  DownloadPlanFactory are used by the other agents' code: change them only by adding, with
  defaults; never rename, remove or change a meaning. Keep P27's merge labels and P34's
  notification texts.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   First start: git switch -c work/phase-15-downloads origin/work/phase-15-integration
   Later:       git switch work/phase-15-downloads && git pull --ff-only
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 2 (items 5 and 6), 3 (G6, G7), 4 (R32,
   R33) and tasks P41 and P42 with their "Read first" files, then your section of
   docs/SESSION_STATE.md.
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop only Gradle daemons you started; one Gradle command at a time.
   Starting state before any edit (Agent B scope):
   ./gradlew --no-daemon --continue :core-download:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
4. In your SESSION_STATE section: P41 IN PROGRESS, the start date and your base commit.

WORK P41 (FIX_ADD_PLAN P41, finding R32)
Root cause: DashTransferEngine fetches YouTube's whole-file tracks in 10 MiB ranges
(WholeFileTrack.DEFAULT_MAX_REQUEST_BYTES), 3 at a time in batches (chunked(3) + awaitAll: the
next batch waits for the slowest range); progress only in markCompleted after a whole range
(fd.sync per range); probeLength (1-byte GET, up to 3 attempts) when the length is unknown;
the total % needs both tracks' sizes. So % and speed stay 0 until the first 10 MiB range ends.
1. Measure first: one log line per download (and in failure Details): "start: plan 0.0 s ·
   length 0.4 s (probe | known | from first range) · first byte 0.9 s · first progress 1.0 s"
   (times only, no addresses).
2. Progress inside a range: bytes as written, at most 4 updates a second per download; a
   retried range takes back what it counted (never backwards, never above 100%); the checkpoint
   marks a range done only after its sync, as today.
3. No batch barrier: N workers take the next range as soon as one finishes; same file layout,
   retries, back-off and checkpoint records.
4. FAST_START=ON: the first range of a whole-file track is 1 MiB, the others 10 MiB; YouTube's
   media hosts get 4 ranges at once per track (today 3). The new layout gets its own
   fingerprint; a checkpoint saved with today's layout resumes with today's layout (compute
   both fingerprints; the stored one wins).
5. No separate length probe: known length (clen, contentLength, totalBytes) -> no probe;
   unknown -> the first range's Content-Range gives it.
6. While one track's size is unknown, the row and the notification show bytes and speed
   ("12.3 MB · 1.2 MB/s") instead of a 0% that does not move.
TESTS P41 (JVM, core-download, MockWebServer with a throttled body such as 64 KB/s): progress
above 0 within 1 s and one slow range not holding back the others (both must fail on the old
code); first range 1 MiB; no probe when the length is known; length from the first
Content-Range; an old-layout checkpoint resumes without downloading its done ranges again;
progress never goes back, ends at exactly 100%, <= 4 updates a second; a retried range counted
once. P27's, P34's and P35's tests stay green.
LIVE CHECK P41: none in the sandbox (YouTube answers data-centre networks with a bot check);
the throttled local server is the proof. Report the measured start times before and after.
Checkpoint P41 (FINISH steps 1–3), report P41 in Burmese, then go on with P42 at once.

WORK P42 (FIX_ADD_PLAN P42, finding R33)
Root cause: DownloadAction.DELETE is "Remove from list" and only calls
DownloadQueue.deleteRecord; the file stays. LibraryRepository.delete already deletes YFT's own
MediaStore item or an app-private file.
1. DownloadedFileDeleter (app/.../download/) deletes a saved file by its record's destination:
   YFT's MediaStore item -> ContentResolver.delete; SecurityException -> API 30+
   MediaStore.createDeleteRequest (the screen launches its IntentSender), API 29
   RecoverableSecurityException.userAction; SAF document -> DocumentsContract.deleteDocument;
   app-private file -> File.delete; already gone -> deleted. LibraryRepository.delete uses it.
2. COMPLETED rows get "Delete file" (testTag download-menu-delete-file-<id>) beside "Remove
   from list" (kept). DELETE_CONFIRM=ON: dialog download-delete-file-dialog "Delete this file?"
   — "“<file name>” will be removed from Download/YFT and from this list. This can't be
   undone." — Delete (download-delete-file-confirm) / Cancel (download-delete-file-cancel).
   After a delete: deleteRecord, the Library refreshes, snackbar "File deleted"; a failure ->
   "Could not delete the file" and the row stays.
3. Unfinished rows keep their menus as they are.
TESTS P42: JVM — each destination (fakes), missing file -> deleted, SecurityException -> the
system request (API 30) or the recoverable action (API 29), failure -> row stays; view model:
confirm -> file deleted and record removed (must fail on the old code), cancel -> nothing;
DELETE_CONFIRM=OFF. Instrumented (CI emulator API 34, androidTest .../delete/): save a small
file through the real Download/YFT MediaStore path, Delete file -> a MediaStore query finds
nothing and the record is gone.

Rules: ADR-006 (public videos only); never log or commit cookies, tokens, signed media or image
URLs or keys; Kotlin lines within 100 characters; keep every testTag; app text stays English;
temporary files and backups outside the repository (/data/tmp, /data/bak); never undo work with
git reset --hard, git clean or git stash; do not edit .github/workflows/.
Regression proof (FIX_ADD_PLAN 0.3): back up your files to /data/bak/P41 (P42), put the old
versions back, run the new tests and see them fail, restore with cp, check with cmp, and name
the failing tests in the Result. An instrumented test's JVM twin is its regression proof.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon --continue :core-download:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH (after each task)
1. Your sections only: docs/TEST_MATRIX.md (P41: start times before and after), CHANGELOG.md,
   docs/SESSION_STATE.md (status OWNER CHECK; Result with any "Plan adapted"; validation
   numbers; regression proof; CI links; hand-offs).
2. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P41: <summary>"
3. CI for your pushed commit (FIX_ADD_PLAN 0.3): checkpoint validation, emulator smoke and
   Preview APK. Fix a red run before reporting. (Docs-only pushes start no CI.)
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5, with Level), with the run links and
   the owner check: P41 — a 1-hour YouTube live recording at 720p -> % and speed move within
   about 3 s, in the app and the notification; P42 — Downloads -> ⋮ -> Delete file -> Delete ->
   gone from Downloads, the Library and the Files app; "Remove from list" keeps the file.
After P42: set your SESSION_STATE section to READY FOR MERGE (last code commit, green CI links),
report, and stop. The owner starts P44 (merge, Preview #7) in this chat with M-merge-preview7.md
when A and C are ready.
```
