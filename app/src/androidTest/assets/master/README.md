# Neutral Master playback fixture

`playback-avc.mp4.b64` decodes to a real 10-second, silent H.264 MP4. It repeats the existing
one-second `mux/video-avc.mp4` ten times without re-encoding. A longer timeline avoids repeated
one-second loop boundaries during two-sample playback validation on a software emulator.
The Base64 source is used because the checkpoint connection writes UTF-8 files only. The test
serves the decoded binary, not a data URL; prefix validation still requests bytes 0–511.

Reproduce the decoded fixture from the repository root:

```bash
ffmpeg -stream_loop 9 -i app/src/androidTest/assets/mux/video-avc.mp4 \
  -map 0:v:0 -an -c copy -t 10 -movflags +faststart /tmp/master-playback-avc.mp4
```

No external video, live cookies, signed URL, or downloaded site body is included.
