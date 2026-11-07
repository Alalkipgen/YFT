# Decisions

Active decisions by topic: what, why, and when. They are the owner's unless they say otherwise.
Old records stay in git history. Feature names follow [GLOSSARY.md](GLOSSARY.md).

## Product rule

- **Public videos must download.** For content anyone can watch free, any working method is allowed:
  device client identities, the site's own proof-of-origin tokens, the user's own browser session,
  bot-check workarounds, HLS or SABR, internal or guest APIs, click-through content warnings.
  Why: stricter rules made most YouTube lookups fail. (2026-10-03)
- **Limits that stay.** No DRM circumvention; no paid, members-only, rented or other people's private
  content; age-restricted videos only with the user's own sign-in in YFT's browser; adapters never
  sign in or store passwords; cookies, tokens and signed media addresses are never logged,
  printed, committed or put in fixtures. Why: public videos only. (2026-10-03)
- **Licences.** GPL code (for example NewPipe) is reference only, as YFT is MIT. MIT, Apache-2.0 and
  Unlicense code may be used with a notice in `docs/THIRD_PARTY_NOTICES.md`. (2026-10-03)
- **GitHub only.** Releases go out on GitHub only; Google Play's policies would reject these methods.
  (2026-10-02)
- **No ads, honest data.** No ads, feed, tracking or account. YFT never invents a size, quality,
  codec or support claim. Why: the product's promise. (2026-09-30)

## Architecture

- **Modules.** An app shell, shared core modules and extractor modules; UI features are packages
  in `:app`. Why: a broken site must not break the rest. (2026-09-30)
- **Detection in layers.** Downloads, page tags, observed requests, manifests, small probes and
  adapter hints; a `blob:` address is not a file. Why: one check alone misses media. (2026-09-30)
- **Separate download plans.** Direct, segmented, HLS, DASH and merge plans, each with its engine.
  Why: streams and merges cannot hide behind one URL download. (2026-09-30)
- **No downloaded code.** Solvers are bundled in the APK; only YouTube's own player and BotGuard
  scripts run, in a sandboxed offscreen WebView; recipes are data. Why: safety. (2026-10-02)

## YouTube

- **YouTube adapter on.** Single-video pages only (watch, shorts, embed, live, `youtu.be`), behind
  `YOUTUBE_ADAPTER_ENABLED`. Why: the owner wants YouTube downloads. (2026-10-02)
- **Client chain.** visionOS first: a complete answer ends the lookup (17 KB, not the 166 KB page);
  then the watch page, device clients, embedded player, page client. Why: fewest requests. (2026-10-05)
- **Cookie scope.** The YouTube cookie goes only to `www.youtube.com` page requests; cookies are
  dropped on a redirect to another site. Why: least exposure. (2026-10-02)
- **"YouTube refused".** A 403 with time left starts no fresh lookup, except once after the network
  changed (the app's network monitor, never an IP check). Why: the same address is refused again.
  (2026-10-27)

## Backup finder (Master)

- **Backup and weak-answer fill.** Master answers after main fails, or fills a weak Facebook,
  Instagram or VK browser answer with missing rows. Main's rows stay first. Weak-answer fill and
  Instagram's best-first order are on in the release and both previews, by the owner's choice.
  A weak-answer fill can add requests and time; strong answers stay on main's path.
- **Lookup record.** Each lookup's requests (main's and Master's) are recorded in memory only.
  Why: Master reads what main asked and never asks twice. (2026-10-17)
- **Small ladders.** A small, budgeted ladder per site; each new step on the owner's go. Why: each
  step costs requests. (2026-10-17)
- **No third-party services** in Master. Why: privacy. (2026-10-20)
- **One item per link.** Instagram's `img_index`, X's `/video/N`, else the first item. Why: a link
  names one item. (2026-10-20)
- **Main fixes** found by Master work are exceptions, each its own commit on the owner's go. Why:
  main changes need proof. (2026-10-20)
- **The release carries Master,** with Settings "Backup finder" on by default; off means no Master
  and nothing sent to GitHub raw; About › Privacy names GitHub raw. Why: friends use it. (2026-10-23)
- **Never in the release.** Trials, ladder test switches, census, weekly site check, canaries,
  stream check meter, Instagram tab-fetch step, AV1 merge switch. Why: owner tools. (2026-10-23)
- **Quiet release.** A failure shows one line ("Couldn't get this video."); details only after the
  About version line is tapped 7 times. Why: friends see a clean app. (2026-10-23)
- **VK is the fifth site pack.** Main's VK adapter stays as it is; the VK value heal reads only VK's
  live public script. Why: one engine for every site. (2026-10-25)

## Social sites

- **Instagram `doc_id`.** No self-healing; values are data with source and date. `VALUE_REJECTED`
  goes to the next step. Why: Instagram changed the request, never the value alone. (2026-10-21)
- **Instagram tab-fetch step.** Only on `instagram.com` in the visible tab, after main fails, last,
  behind its own switch (off); every request in the lookup record; no `addJavascriptInterface`;
  never a walled post's answer. Why: the user's own tab. (2026-10-21, off by default 2026-10-23)
- **Instagram rows.** Master may keep the 360p file main drops, only for the link's post and item;
  a removed post is never final from an unclear answer. Why: never a wrong row. (2026-10-22)
- **X answers.** `{}` is a rejected value; a tombstone or the widget's 404 means the tab must play,
  never "removed". Why: a 404 alone does not prove deletion. (2026-10-22)
- **TikTok download service off.** `tikwm.com` is never asked. Why: it rescued no post, its checks
  were weak, and it sees the post and the user's IP. (2026-10-22)
- **TikTok backup rows.** One row per post, at most 1280 tall, is accepted. Why: it shows only where
  main has none. (2026-10-22)
- **X guest-token API.** Not now. Why: X's embed answer already carries the video posts. (2026-10-20)
- **Rows without a stated size** take their file's own picture size; Facebook's Master rows may be
  fewer than main's. Why: names match main's, never a wrong row. (2026-10-23)
- **TikTok in main.** Asked as Chrome; Home uses the browser's own TikTok cookies; the watermarked
  file only when no clean file exists; a hidden TikTok page (at most 15 s) when reading the page
  fails. Why: TikTok answers YFT as it answers the user's browser. (2026-10-09)
- **Ad rule.** A video is the page's own only when the page or an adapter names it or its length
  matches; else no ad is offered. Why: never save an ad as the video. (2026-10-09)

## VK

- **Embed on two hosts.** `vk.ru` first on a `vk.ru` page (with its cookie), else `vk.com`; the
  other host never gets a cookie; at most 3 embed requests in 15 s. Why: cookies stay on their
  host. (2026-10-24)
- **"Video not found."** never means private or sign-in; YFT says the video may be deleted, private
  or need sign-in. Why: VK's answer cannot tell them apart. (2026-10-24)
- **Media hosts.** HTTPS addresses under the item's `files` on hosts ending `.okcdn.ru` only. Why:
  picture and stats hosts are not media. (2026-10-24)
- **Public 18+ videos.** The web client runs only after the embed's adult-content answer, with VK's
  anonymous web token in memory only. Why: they play signed out on vkvideo.ru. (2026-10-25)

## Downloads

- **Fresh link renewal.** A 401, 403, 410 or expired link starts one fresh lookup (VK's 400 only
  after bytes); at most 3 per download; never another quality. Why: long downloads outlive links.
  (2026-10-27)
- **Renewal after the app closed.** A download keeps the app's text about where it came from (its
  page and the facts that name the same file there, and where its bytes are) in its record, and a
  Retry after a restart asks for a fresh link of the same file and reopens the file it was writing,
  so it goes on from the bytes it kept. Why: the owner asked for it, and the facts the old rule
  named as missing (the page's address and the file's place) are the download's own. The text is
  never shown, goes with the download's record, and Clear browsing data removes it, as it removes
  the sources in memory. (2026-11-07)

- **Android 9 has its own CI run.** The save path there (YFT's own storage, no public Downloads
  row) is checked by an instrumented test on an API 28 emulator, not only by unit tests. Why: no
  API 28 device is at hand, and the version's storage rules are the ones that differ. (2026-11-07)
- **The download service keeps its type.** A `dataSync` foreground service on Android 10+ (with
  `mediaProcessing` on Android 15+ while a merge, MP3 conversion or save runs) stays the way
  downloads run; user-initiated data transfer jobs are not used. Why: the service is the supported
  path and already reports Android 15's 6-hour limit as a notice; a job would replace the queue's
  own progress, Pause and Resume for one limit that a single download rarely meets. Revisit only
  with a phone run of a download longer than 6 hours. (2026-11-07)
- **A page's own address is no file.** The request that made the page is never a media candidate,
  also when its address ends in a media extension; only the answer's content type can tell, so it
  goes to the probe. A file opened or pasted directly is the page, so it is probed and kept when the
  answer is media. Why: Wikimedia Commons' `File:Big_Buck_Bunny_4K.webm` page was offered as the
  video and the download failed as an invalid address. The same holds on Home: a pasted link whose
  address ends in `.webm` is probed first, and when the answer is a page (`NotMedia`) the page is
  read like any other page and its own files are offered, instead of the sheet ending at
  "address not supported". A direct file link still answers media and stays the file. (2026-11-07)
- **An If-Range validator is quoted.** The ETag a file server states is sent as `If-Range` quoted
  (RFC 7233 §3.2 wants a strong entity-tag, which is quoted), an unquoted value from the server is
  quoted here, a weak (`W/…`) or unusable tag falls back to `Last-Modified` when it is an
  HTTP-date, and otherwise no `If-Range` is sent. A ranged request the server still answers with
  200 is asked once more without `If-Range` before ranges are given up, and the bytes already
  written stay. Why: Wikimedia's upload hosts state the ETag unquoted, so every ranged request of
  the owner's 287 MB WEBM was answered with the whole file (200), YFT treated that as "the server
  ignores ranges", truncated the file and started again from 0% twice. Measured 2026-11-07: the
  same request is 200 unquoted and 206 quoted. (2026-11-07)
- **YouTube fast start.** A 1 MiB first piece, 4 pieces at once per track. Why: progress from the
  first seconds. (2026-10-09)
- **Delete file** asks first. Why: a deleted file cannot come back. (2026-10-09)
- **Downloads order.** Rows keep the order they were added in. Why: rows jumped during progress.
  (2026-10-27)
- **AV1 merges off** in the release (`AV1_MP4_ENABLED` false). Why: not yet proven on the owner's
  phone. (2026-10-31)
- **Quality names.** The nearest standard height names a row, so 640 × 640 stays "720p · HD". Why:
  one rule for every site. (2026-10-31)
- **Sizes are the source's, never YFT's.** A row shows a size only when the source states one, with
  `~` when the source marks it an estimate; otherwise it reads "Size unknown". A progress total
  that is an estimate says "(estimated)". Why: YFT's own bitrate × length guess read as the file's
  size (the ended live showed ~515 MB for a 371 MB file). (2026-11-07)
- **A peak rate is not a size; a stated average is.** A bitrate times a length is a size only when
  the bitrate is the stream's average: an HLS master's `AVERAGE-BANDWIDTH` (RFC 8216) is, so those
  rows keep "~". A DASH `@bandwidth` (the peak of a channel the representation can be delivered
  on), an HLS `BANDWIDTH` (the peak segment rate) and YouTube's rate for a live format are peaks,
  so their rows read "Size unknown". The sheet's own provisional row is filled from the size the
  source states and from nothing else: the guess read ~689 MB beside a download of ~390 MB, and
  ~1.7 times the file on both replays the owner tested. What is measured is still shown: the
  progress total is the bytes of the pieces done times the pieces, under the bar, "(estimated)".
  (2026-11-07)

## Signed recipe config

- **The key.** P-256; the private key only in the secret `YFT_RECIPE_KEY` and the owner's offline
  copy; the public key committed (`extractor-master/recipes/master-recipes.pub.hex`). Why: only the
  workflow can sign. (2026-10-22)
- **Publishing.** Only the recipe publish workflow writes the `recipes` branch, run on `main`. A
  version lasts a year; `defaults` expires 2027-10-23, then apps use bundled recipes. Why: site
  fixes without a new APK. (2026-10-24)

## Testing and release

- **Promotion runs.** Per site three census runs, 6 h apart, first and last 24 h apart, on the final
  code; a change to the site's pack, the engine or main restarts it. Pass: 0 wrong rows, no
  regression. Why: proof over days. (2026-10-23)
- **Promotion runs for 1.0.2.** Facebook, Instagram, VK, X and TikTok all start again at run 1.
  TikTok gets one more run after a 403 or 429; a second one skips TikTok, and 1.0.2 then does not
  count TikTok's backup steps (main's TikTok reader stays as it is). Why: one session runs every
  site, so X and TikTok cost little; TikTok's embed player answered 429 on 2026-10-29 and
  2026-11-01. (2026-11-01)
- **Census lists.** The owner's public posts, kept on the phone only. Why: privacy. (2026-10-20)
- **Census order.** Main first, then the backup route, then the trials; one baseline per site, VPN
  and session; TikTok's VPN runs use New York. Why: like with like. (2026-10-21, New York 2026-10-22)
- **Every release.** Two previews of one release commit (normal and trial) on the owner's phone,
  then the signed install and upgrade. Why: the release's own flags on a real phone. (2026-10-23)
- **In-app update.** Only release-key builds check; HTTPS only, no token or cookie, signed download
  addresses never logged; checks fail closed. Why: never install a wrong APK. (2026-10-24)
