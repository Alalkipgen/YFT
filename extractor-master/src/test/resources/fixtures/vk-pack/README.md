# VK-Master pack fixtures (agent B)

Made-up answers in the shapes of VK's embed page (`video_ext.php`), login.vk.ru's anonymous-token
answer and api.vkvideo.ru's `video.get`, for `Vk*Test.kt` only (JVM, no network).

Every value here is invented: owner `-100000001`/`-100000002`, videos `400000001`/`400000002`,
hosts `vk1-1.vkuser.net`, `vkvd1.okcdn.ru`, `vkvd2.okcdn.ru`, `iv.okcdn.ru` and `cdn.example.test`,
query values `FIXTURE`, titles "Fixture …", and a token string that says "not-real". Nothing was
copied from a live answer; no cookie, token, signed address or response body of a real lookup is
kept.

| File | Shape |
| --- | --- |
| `embed_adult.html` | the embed's adult answer (`video_ext_msg`, `embedErrorCallback(9)`) |
| `embed_not_found.html` | the embed's other message |
| `embed_shell.html` | VK's SPA shell, no player data |
| `embed_files.html` | an embed whose `apiPrefetchCache` lists the video's files under VK's keys, its trailer, pictures and another video's files |
| `token_okay.json` | login.vk.ru's okay answer (a "not-real" value, expiry 1900000000) |
| `token_captcha.json` | login.vk.ru's captcha answer |
| `video_get_mp4.json` | `video.get` with the video's files under VK's keys, a failover host, a trailer and pictures |
| `video_get_renamed.json` | `video.get` with the files under keys of another shape (the shape scan), a preview, a poster, a trailer and another video's file |
| `video_get_live.json` | a broadcast that is not over (live keys only) |
| `video_get_other_video.json` | an entry for another video only |
| `video_get_unknown_host.json` | the video's files on a host that is not VK's video servers |
| `video_get_deleted.json` | `restriction.can_play` 0 with VK's deleted icon |
| `video_get_error_5.json` | VK's API error 5 |
| `video_get_captcha.json` | VK's API error 14 |
