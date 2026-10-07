package com.alal.yft.core.browser.detection

/**
 * The script the browser runs on a page to list its media elements and media meta tags.
 *
 * P28: its first entry (`element: "facts"`) carries what the page states about its video — the
 * meta tags [PageFactsReader] reads, up to five JSON-LD blocks and the page title — and the text
 * of the scripts that set up its player (at most 200 KB in all), read by
 * [DomProbeResultParser]. Nothing on the page is run or changed.
 */
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
          // P28: what the page states about its video, and its player's setup, read as text.
          try {
            const names = ['og:title', 'twitter:title', 'og:image', 'og:image:secure_url',
              'twitter:image', 'og:video:duration', 'video:duration', 'duration', 'og:site_name'];
            const meta = {};
            document.querySelectorAll('meta[property], meta[name], meta[itemprop]').forEach(tag => {
              const key = (tag.getAttribute('property') || tag.getAttribute('name') ||
                tag.getAttribute('itemprop') || '').trim().toLowerCase();
              const content = (tag.getAttribute('content') || '').trim();
              if (names.includes(key) && content && !(key in meta)) {
                meta[key] = content.slice(0, 2000);
              }
            });
            const ldScripts = document.querySelectorAll('script[type="application/ld+json"]');
            const jsonLd = Array.from(ldScripts)
              .map(script => script.textContent || '')
              .filter(text => text.length <= 30000)
              .slice(0, 5);
            const player = new RegExp([
              'jwplayer', '\\.setup\\s*\\(', 'flashvars', 'mediaDefinitions', 'videojs',
              'flowplayer', 'clappr', 'plyr', '["\']?sources["\']?\\s*:',
              '["\']?playlist["\']?\\s*:', 'video_url'
            ].join('|'), 'i');
            const scripts = [];
            let budget = 200000;
            const keep = text => {
              if (!text || budget <= 0 || !player.test(text.slice(0, 100000))) return;
              const part = text.slice(0, Math.min(100000, budget));
              budget -= part.length;
              scripts.push(part);
            };
            document.querySelectorAll('script:not([src])').forEach(script => {
              const type = (script.getAttribute('type') || '').toLowerCase();
              if (!type.includes('ld+json')) keep(script.textContent);
            });
            document.querySelectorAll('[data-setup]')
              .forEach(node => keep(node.getAttribute('data-setup')));
            const facts = { meta, jsonLd, documentTitle: pageTitle };
            found.unshift({ element: 'facts', facts, scripts });
          } catch (_) {}
          return JSON.stringify(found);
        })();
    """.trimIndent()
}
