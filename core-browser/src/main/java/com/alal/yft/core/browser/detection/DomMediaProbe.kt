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
          const page = location.href.split('#')[0];
          const boxWords = /thumb|preview|teaser|mediabook/i;
          // P24: a clip inside a link to another page or a thumbnail box is a preview.
          const inThumbnail = media => {
            let node = media;
            for (let depth = 0; node && depth < 6; depth++, node = node.parentElement) {
              const link = node.tagName === 'A' && node.href;
              if (link && node.href.split('#')[0] !== page) return true;
              const names = typeof node.className === 'string' ? node.className : '';
              if (boxWords.test(names) || boxWords.test(node.id || '')) return true;
            }
            return false;
          };
          const flagsOf = media => media ? {
            muted: !!media.muted || media.hasAttribute('muted'),
            loop: !!media.loop,
            autoplay: !!media.autoplay,
            thumbnail: inThumbnail(media)
          } : {};
          const add = (url, element, type, poster, duration, flags) => {
            const absoluteUrl = absolute(url);
            if (!absoluteUrl) return;
            found.push(Object.assign({
              url: absoluteUrl,
              element,
              type: type || null,
              title: pageTitle,
              poster: absolute(poster),
              duration: Number.isFinite(duration) && duration > 0 ? duration : null
            }, flags || {}));
          };
          document.querySelectorAll('video, audio').forEach(media => {
            const flags = flagsOf(media);
            const element = media.tagName.toLowerCase();
            add(media.currentSrc, element, media.currentType, media.poster, media.duration, flags);
            const declared = media.getAttribute('type');
            add(media.src, element, declared, media.poster, media.duration, flags);
          });
          document.querySelectorAll('video source, audio source').forEach(source => {
            const parent = source.parentElement;
            const flags = flagsOf(parent);
            add(source.src, 'source', source.type, parent?.poster, parent?.duration, flags);
          });
          document.querySelectorAll('meta[property="og:video"], meta[property="og:video:url"], meta[name="twitter:player:stream"]').forEach(meta => {
            add(meta.content, 'meta', meta.getAttribute('type'), null, null);
          });
          return JSON.stringify(found);
        })();
    """.trimIndent()
}
