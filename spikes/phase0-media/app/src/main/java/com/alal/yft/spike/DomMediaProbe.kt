package com.alal.yft.spike

/** Read-only DOM probe. It reports candidates; it does not fetch or mutate page content. */
object DomMediaProbe {
    val script: String = """
        (() => {
          const absolute = value => {
            try { return value ? new URL(value, document.baseURI).href : null; }
            catch (_) { return null; }
          };
          const found = [];
          document.querySelectorAll('video, audio').forEach(media => {
            [media.currentSrc, media.src].forEach(src => {
              const url = absolute(src);
              if (url) found.push({url, tag: media.tagName.toLowerCase(), poster: absolute(media.poster)});
            });
          });
          document.querySelectorAll('video source, audio source').forEach(source => {
            const url = absolute(source.src);
            if (url) found.push({url, tag: source.parentElement?.tagName?.toLowerCase() || 'source', type: source.type || null});
          });
          return JSON.stringify(found);
        })();
    """.trimIndent()
}
