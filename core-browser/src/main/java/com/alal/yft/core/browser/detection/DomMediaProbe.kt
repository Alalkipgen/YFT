package com.alal.yft.core.browser.detection

object DomMediaProbe {
    val script: String = """
        (() => {
          const absolute = value => {
            try { return value ? new URL(value, document.baseURI).href : null; }
            catch (_) { return null; }
          };
          const pageTitle = document.title || null;
          const found = [];
          const add = (url, element, type, poster, duration) => {
            const absoluteUrl = absolute(url);
            if (!absoluteUrl) return;
            found.push({
              url: absoluteUrl,
              element,
              type: type || null,
              title: pageTitle,
              poster: absolute(poster),
              duration: Number.isFinite(duration) && duration > 0 ? duration : null
            });
          };
          document.querySelectorAll('video, audio').forEach(media => {
            add(media.currentSrc, media.tagName.toLowerCase(), media.currentType, media.poster, media.duration);
            add(media.src, media.tagName.toLowerCase(), media.getAttribute('type'), media.poster, media.duration);
          });
          document.querySelectorAll('video source, audio source').forEach(source => {
            const parent = source.parentElement;
            add(source.src, 'source', source.type, parent?.poster, parent?.duration);
          });
          document.querySelectorAll('meta[property="og:video"], meta[property="og:video:url"], meta[name="twitter:player:stream"]').forEach(meta => {
            add(meta.content, 'meta', meta.getAttribute('type'), null, null);
          });
          return JSON.stringify(found);
        })();
    """.trimIndent()
}
