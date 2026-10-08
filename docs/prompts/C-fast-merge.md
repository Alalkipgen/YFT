# Agent C — Faster merge for long videos (P35)

**ရည်ရွယ်ချက်:** YouTube live ၁ နာရီ (720p) လို ကြာကြာ video ရဲ့ video နဲ့ audio ပေါင်း (merge) တာကို
၂ မိနစ်ခန့်ကနေ **၁၀–၂၀ စက္ကန့်ခန့်** (ခန့်မှန်း၊ တိုင်းကြည့်ရမယ်) ဖြစ်အောင် လုပ်မယ်။ ဖိုင်ကတော့ အရင်လိုပဲ
ကြည့်လို့ရ၊ ရှေ့/နောက် ရွှေ့လို့ရ။ နည်းလမ်းအသစ် မအောင်မြင်ရင် အခုနည်း (MediaMuxer) နဲ့ အလိုအလျောက်
ပြန်လုပ်မယ် (P35)။

**အကြောင်းရင်း:** အခု merge က sample (frame) တစ်ခုချင်းစီကို MediaExtractor → MediaMuxer နဲ့ ကူးတာ —
၁ နာရီ 720p မှာ sample ~၂၆၀,၀၀၀ ရှိလို့ sample တစ်ခုချင်းစီရဲ့ အလုပ်က အချိန်ကုန်တာပါ (byte ပမာဏ မဟုတ်)။
ပြင်နည်း — MP4/M4A track တွေရဲ့ data ကို အတုံးကြီးတွေနဲ့ တိုက်ရိုက်ကူးပြီး sample table အသစ် ရေး (stream
copy)၊ ပြီးရင် စစ်ဆေး။ WebM (2K/4K) က အခုနည်းအတိုင်း (နည်းနည်း မြန်အောင်ပဲ)။

**အချက်အလက်:** Phase 14 · **Agent C** · branch `work/phase-14-fast-merge` · P35 (**Hard**, 6–10 နာရီ)
· Agent A နဲ့ B နဲ့ **တပြိုင်နက်** လုပ်လို့ရ (ဖိုင်ချင်း မထိ)

**သုံးနည်း:** Agent chat အသစ်ထဲ အောက်က code block တစ်ခုလုံးကို paste လုပ်ပါ။ ပြီးရင် `READY FOR MERGE`
လို့ပြောပြီး ရပ်ပါမယ်။ Agent ၂ ယောက်ပဲ သုံးရင် Agent A ရဲ့ chat ထဲမှာ (P34 ပြီးမှ)
`BRANCH_OVERRIDE: work/phase-14-background` နဲ့ paste လုပ်ပါ။ Stream copy မလိုချင်ရင်
`OWNER ANSWERS: FAST_MERGE=OFF` (အခုနည်းကိုပဲ နည်းနည်း မြန်အောင်)။ Computer မှာ SSH key မရှိရင် agent က
key အသစ်လုပ်ပြီး public key တစ်ကြောင်း ပြပါမယ် — Deploy keys မှာ "Allow write access" နဲ့ ထည့်ပြီး
"SSH Done" လို့ ပြောပါ။
အသေးစိတ်: [`docs/FIX_ADD_PLAN.md` › P35](../FIX_ADD_PLAN.md#p35--faster-merge-for-long-videos)

```text
You are a coding agent on YFT, an ad-free Android video downloader (Kotlin, Jetpack Compose,
Material 3, Hilt, Room, OkHttp, Media3, MediaExtractor/MediaMuxer, LAME via NDK).

Repository: https://github.com/Alalkipgen/YFT
Agent: C — download engines: the merge in core-download (one of three agents working at once)
Task: P35 — Faster merge for long videos
Branch: work/phase-14-fast-merge (first start: create it from origin/work/phase-14-integration)
BRANCH_OVERRIDE: none     (two-agent mode: work/phase-14-background, after Agent A's P34)
ALLOW_PUSH: true          (checkpoint pushes to this branch only)
ALLOW_MERGE_MAIN: false
ALLOW_RELEASE: false
OWNER ANSWERS: none       (example: FAST_MERGE=OFF keeps today's MediaMuxer path and does only
                           steps 1, 2 and 6)

The repository is the source of truth; do not rely on chat history. If the plan and the code
disagree, the verified code wins: adapt, and write "Plan adapted: ..." in the Result.

COMPUTER, FOLDER AND PUSH
- Work only in your own folder /data/YFT-C (the three agents use /data/YFT-A, /data/YFT-B and
  /data/YFT-C, because they may share one computer). First start, if it is missing:
    git clone https://github.com/Alalkipgen/YFT.git /data/YFT-C
- Push with SSH. If /data/.ssh/id_ed25519 is missing, make a NEW key (never search the
  computer for old keys):
    mkdir -p /data/.ssh && ssh-keygen -t ed25519 -N "" -f /data/.ssh/id_ed25519 -C "yft-c-<date>"
    ssh-keyscan github.com >> /data/.ssh/known_hosts
  show the owner the one line of /data/.ssh/id_ed25519.pub and wait until he says he added it
  (GitHub > YFT > Settings > Deploy keys, "Allow write access"). Then, in your folder:
    git remote set-url origin git@github.com:Alalkipgen/YFT.git
    git config core.sshCommand "ssh -i /data/.ssh/id_ed25519 -o UserKnownHostsFile=/data/.ssh/known_hosts -o IdentitiesOnly=yes"
  Never print a private key or a token. A failed push: stop and tell the owner (a local commit
  is not a handoff).
- Never touch another agent's folder or branch. One Gradle build at a time on this computer: if
  pgrep -af "[G]radleDaemon" shows a build you did not start, wait for it; never kill it.
- Two-agent mode (BRANCH_OVERRIDE set): keep working in Agent A's folder /data/YFT-A on that
  branch; you still write only the "## Agent C ..." sections of the shared docs.

YOUR FILES (docs/FIX_ADD_PLAN.md 0.7) — change nothing else
- core-download/**; core-model/src/main/kotlin/com/alal/yft/core/model/download/**;
  app/src/androidTest/java/com/alal/yft/download/** and
  app/src/androidTest/assets/{mux,mp4,mp3}/**; the tests beside them.
- Shared docs, your section only: docs/SESSION_STATE.md "## Agent C ...", CHANGELOG.md
  "### Phase 14 — Agent C (P35)", docs/TEST_MATRIX.md "### Agent C — P35".
- Never: app/.../download/**, app/.../feature/{downloads,settings}/**, the manifest (Agent A);
  core-browser, core-media, extractor-*, app/.../detection and app/.../feature/{browser,
  quickdownload,home,detectedmedia}/** (Agent B); Gradle files, res/, docs/FIX_ADD_PLAN.md,
  docs/prompts/**, .github/workflows/**. Agents A and B push their own branches at the same
  time. Need a change outside your files? Do not make it: write "Hand-off to <agent>: <file> —
  <change> — <why>" in your SESSION_STATE section and report it.
- Contracts: the public DownloadQueue API, DownloadTask, DownloadProgress, DownloadFailure,
  DownloadFailureReason, AudioVideoMuxStage and DownloadDestination are used by A's service and
  B's sheet: change them only by adding, with defaults; never rename, remove or change a
  meaning. The merge keeps its stages (MUXING, SAVING) and P27's labels.

START
1. Follow AGENTS.md: git fetch --all --prune; git status; git log -5 --oneline.
   First start: git switch -c work/phase-14-fast-merge origin/work/phase-14-integration
   Later:       git switch work/phase-14-fast-merge && git pull --ff-only
   (With BRANCH_OVERRIDE: switch to that branch and pull it instead.)
2. Read docs/FIX_ADD_PLAN.md sections 0 (with 0.7), 2 (item 2), 3 (G2), 4 (R23, R24) and task
   P35 with its "Read first" files, then your section of docs/SESSION_STATE.md.
3. Environment (FIX_ADD_PLAN 0.3): JDK 17, Android SDK 35, NDK 27.3.13750724, CMake 3.22.1.
   Notion sandbox: `source /data/yft-env.sh` first (after a reset recreate it and install what
   is missing); stop only Gradle daemons you started; one Gradle command at a time.
   Starting state before any edit (Agent C scope):
   ./gradlew --no-daemon --continue :core-download:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
4. In your SESSION_STATE section: P35 IN PROGRESS, the start date and your base commit.

WORK P35 (FIX_ADD_PLAN P35, finding R23)
Root cause: P27 merges sample by sample (AndroidLocalMuxer.mux -> copySamples, MediaExtractor ->
MediaMuxer, ~8 JNI calls per sample; ~95 us per sample on the CI emulator, reading 1/3, muxer
2/3). A 1-hour 720p recording has ~260,000 samples, so the per-sample work decides the time
(~2 minutes on the owner's phone).
1. Measure first (CI emulator; the same log line on the phone): P27's path split into reading,
   writing and file work, per sample, for P27's 20-minute input (LongFragmentedMp4.kt) and a
   1-hour-sized input (repeat its fragments to ~260,000 samples). Record.
2. Quick wins on today's path (kept for WebM and as the fallback): read a sample's time, flags
   and size once; one reusable buffer sized to the largest sample; progress at most every
   250 ms. Measure again.
3. FAST_MERGE=ON (default): stream copy for an MP4 video track + MP4/M4A audio track (codecs the
   merge accepts today; AV1 stays off):
   a. Read both inputs in plain Kotlin over FileChannel (no Android API; JVM-testable): init
      (ftyp; moov > trak > tkhd, mdhd timescale, hdlr, stsd kept byte for byte, edts/elst,
      mvex/trex defaults) and samples — fragmented (moof > traf > tfhd, tfdt, trun: offset,
      size, duration, composition offset, sync flag per sample) or plain (stsz/stz2, stco/co64,
      stsc, stts, ctts, stss). Skip unknown boxes. Encryption (encv, enca, senc, pssh: DRM stays
      out), more than one track per file or broken sizes -> "not supported" -> today's path.
   b. Write a progressive MP4 straight into the destination (P27's file descriptor, or today's
      temporary file): ftyp; one mdat (64-bit size above 4 GiB) with ~1 s chunks per track,
      alternating video/audio, copied in large blocks (FileChannel.transferTo/transferFrom or
      1–4 MiB buffers; whole contiguous fragments at once); then moov (mvhd; per track tkhd,
      edts/elst with the start offset between tracks and the first composition offset as
      MediaMuxer writes them; mdia: mdhd with the input timescale, hdlr, minf with vmhd/smhd,
      dinf, stbl = input stsd + new stts, ctts (version 1 for negative offsets), stss (video),
      stsc, stsz, stco or co64). All sizes are known before writing: no seeks.
   c. Check with MediaExtractor: two tracks with the inputs' formats, the same sample counts,
      durations within one frame, first, last and a few random samples byte-equal to the
      inputs'. A failed check deletes the output and runs today's path once (reason in the log
      and in a later failure's detail).
   d. Progress: bytes copied / total -> "Merging audio and video · N%" (same MUXING stage).
4. Space check as in P27; track files deleted only after a good merge.
5. WebM (VP9/Opus, 2K/4K) keeps today's MediaMuxer path with the quick wins.
6. One log line per merge: path (stream copy or today's), sample counts, time per phase, and the
   merge thread's CPU time beside the wall time (a big gap = the phone paused YFT, R24); no
   addresses.
TESTS P35
- JVM (core-download/src/test, plain Kotlin): small fragmented and plain MP4 inputs built in the
  test -> written stts/ctts/stss/stsc/stsz/stco match the samples; offsets above 4 GiB -> co64
  and a 64-bit mdat (virtual source, no real 4 GiB file); chunk order alternates by time;
  encrypted or odd input -> "not supported" -> today's path once; MP4 + M4A uses the stream copy
  (must fail on the old code); progress rises to 100%.
- Instrumented (CI emulator, app/src/androidTest/.../download/): stream copy vs today's path on
  P27's 20-minute input and the 1-hour-sized input -> same tracks, same sample count, every
  sample byte-equal, times within one tick, plays in Media3 ExoPlayer (prepare, duration, seek
  to the middle); print both times; assert the stream copy is >= 3x faster on the long input.
LIVE CHECK P35: none in the sandbox (YouTube answers data-centre networks with a bot check);
the CI emulator tests and the owner's phone are the proof.

Rules: ADR-006 (public videos only; no DRM, paywall, private or age-gate bypass — an encrypted
track is never copied); never log or commit cookies, tokens, signed media or image URLs or
keys; Kotlin lines within 100 characters; keep every testTag; app text stays English; temporary
files and backups outside the repository (/data/tmp, /data/bak); never undo work with git
reset --hard, git clean or git stash; do not edit .github/workflows/.
Regression proof (FIX_ADD_PLAN 0.3): back up your files to /data/bak/P35, put the old versions
back, run the new tests and see them fail, restore with cp, check with cmp, and name the
failing tests in the Result. An instrumented test's JVM twin is its regression proof.

VALIDATE (report only what you ran)
   ./gradlew --no-daemon --continue :core-download:testDebugUnitTest :core-model:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin
   New Kotlin lines within 100 characters (must print nothing):
   git diff -U0 origin/main -- '*.kt' '*.kts' | grep '^+[^+]' | LC_ALL=C.UTF-8 awk 'length > 101'

FINISH
1. Your sections only: docs/TEST_MATRIX.md (measured times before and after), CHANGELOG.md,
   docs/SESSION_STATE.md (status OWNER CHECK; Result with any "Plan adapted"; validation
   numbers; regression proof; measured times; CI links; hand-offs).
2. Checkpoint (Notion sandbox: start the command with `source /data/yft-env.sh && `):
   CHECKPOINT_TEST_COMMAND="<the validation above>" bash scripts/checkpoint.sh "P35: <summary>"
3. CI for your pushed commit (FIX_ADD_PLAN 0.3): checkpoint validation, emulator smoke and
   Preview APK. Fix a red run before reporting. (Docs-only pushes start no CI.)
4. Report to the owner in Burmese, short (FIX_ADD_PLAN 0.5, with Level), with the run links, the
   measured times and the owner check: the same 1-hour YouTube live recording at 720p ->
   "Merging … %" ends in about 10–20 s after the tracks (was ~2 minutes); it plays and seeks in
   the Library and another player; a 1080p video and a 2K/4K WebM still save.
After P35: set your SESSION_STATE section to READY FOR MERGE (last code commit, green CI links),
report, and stop.
```
