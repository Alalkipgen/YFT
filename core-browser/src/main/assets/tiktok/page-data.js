/*
 * YFT P40: TikTok's own data for one post, read from a page TikTok already shows (the user's
 * browser tab or YFT's hidden page), the way TikTok's own player gets it.
 *
 * mode "store" runs at document start on www.tiktok.com and m.tiktok.com only. It keeps a
 * compact copy of the posts in TikTok's own same-site /api/ answers (For You's later videos
 * come from /api/recommend/item_list/), read from a clone after TikTok's code got its answer.
 * It never changes, delays or repeats TikTok's requests, swallows its own errors and sends
 * nothing to the app or the network: the copies stay in this page.
 *
 * mode "item" returns, as JSON text, the compact post for one id (the address's when none is
 * given) from (a) the page's __UNIVERSAL_DATA_FOR_REHYDRATION__ script, any __DEFAULT_SCOPE__
 * key, (b) SIGI_STATE, (c) the store; or what the page shows instead (no post, TikTok's own
 * status for the post, a check).
 */
(function (mode, wanted) {
  'use strict';
  var KEY = '__yftTikTokStore';
  var MAX_ITEMS = 200;
  var MAX_CHARS = 65536;
  var MAX_URLS = 6;
  var MAX_GEARS = 16;
  var MAX_ANSWER_CHARS = 8000000;

  function own(value, key) {
    return value !== null && typeof value === 'object' &&
      Object.prototype.hasOwnProperty.call(value, key) ? value[key] : undefined;
  }

  function plain(value) {
    var type = typeof value;
    return type === 'string' || type === 'number' || type === 'boolean' ? value : undefined;
  }

  function first() {
    for (var i = 0; i < arguments.length; i++) {
      if (arguments[i] !== undefined && arguments[i] !== null) return arguments[i];
    }
    return undefined;
  }

  /** An address list as TikTok gives it: a string, a list, or an object with its list. */
  function addresses(value) {
    if (typeof value === 'string') return value || undefined;
    var list = Array.isArray(value) ? value : first(own(value, 'UrlList'), own(value, 'url_list'));
    if (!Array.isArray(list)) return undefined;
    var out = [];
    for (var i = 0; i < list.length && out.length < MAX_URLS; i++) {
      if (typeof list[i] === 'string' && list[i]) out.push(list[i]);
    }
    return out.length ? out : undefined;
  }

  function gear(entry) {
    if (!entry || typeof entry !== 'object') return null;
    var play = first(own(entry, 'PlayAddr'), own(entry, 'play_addr')) || {};
    var urls = addresses(play);
    if (!urls) return null;
    var h265 = own(entry, 'is_bytevc1') === 1 || own(entry, 'is_h265') === 1;
    return {
      GearName: plain(first(own(entry, 'GearName'), own(entry, 'gear_name'))),
      Bitrate: plain(first(own(entry, 'Bitrate'), own(entry, 'bit_rate'))),
      CodecType: plain(first(own(entry, 'CodecType'), h265 ? 'h265' : undefined)),
      DataSize: plain(own(entry, 'DataSize')),
      PlayAddr: {
        UrlList: Array.isArray(urls) ? urls : [urls],
        DataSize: plain(first(own(play, 'DataSize'), own(play, 'data_size'))),
        Width: plain(first(own(play, 'Width'), own(play, 'width'))),
        Height: plain(first(own(play, 'Height'), own(play, 'height'))),
        UrlKey: plain(first(own(play, 'UrlKey'), own(play, 'url_key')))
      }
    };
  }

  /** The compact post: id, desc, author.uniqueId and what its video files need; else null. */
  function compact(item) {
    if (!item || typeof item !== 'object') return null;
    var id = first(own(item, 'id'), own(item, 'aweme_id'));
    if ((typeof id !== 'string' && typeof id !== 'number') || !String(id)) return null;
    var video = own(item, 'video') || {};
    var app = own(item, 'aweme_id') !== undefined;
    var author = own(item, 'author');
    var gears = [];
    var list = first(own(video, 'bitrateInfo'), own(video, 'bit_rate'));
    if (Array.isArray(list)) {
      for (var i = 0; i < list.length && gears.length < MAX_GEARS; i++) {
        var one = gear(list[i]);
        if (one) gears.push(one);
      }
    }
    var duration = plain(own(video, 'duration'));
    if (app && typeof duration === 'number' && duration > 1000) duration = duration / 1000;
    var cover = first(own(video, 'cover'), own(video, 'originCover'), own(video, 'dynamicCover'));
    var compactItem = {
      id: String(id),
      desc: plain(own(item, 'desc')),
      author: {
        uniqueId: plain(typeof author === 'string' ? author :
          first(own(author, 'uniqueId'), own(author, 'unique_id')))
      },
      video: {
        duration: duration,
        width: plain(own(video, 'width')),
        height: plain(own(video, 'height')),
        ratio: plain(own(video, 'ratio')),
        definition: plain(own(video, 'definition')),
        codecType: plain(own(video, 'codecType')),
        bitrate: plain(own(video, 'bitrate')),
        cover: plain(typeof cover === 'string' ? cover : (addresses(cover) || [])[0]),
        playAddr: addresses(first(own(video, 'playAddr'), own(video, 'play_addr'))),
        downloadAddr: addresses(first(own(video, 'downloadAddr'), own(video, 'download_addr'))),
        isDrm: own(video, 'isDrm') === true ? true : undefined,
        bitrateInfo: gears.length ? gears : undefined
      }
    };
    var photos = own(item, 'imagePost');
    if (photos !== undefined && photos !== null) compactItem.imagePost = true;
    return compactItem;
  }

  function idFromAddress() {
    var match = /\/(?:video|photo)\/(\d{5,25})(?:[/?#]|$)/.exec(location.pathname + '/');
    return match ? match[1] : null;
  }

  function hasCheck() {
    try {
      return !!document.querySelector(
        '#captcha-verify-container-main-page, #captcha_container, #tiktok-verify-ele, ' +
        '.captcha-verify-container, .captcha_verify_container, .secsdk-captcha-drag-icon'
      );
    } catch (e) {
      return false;
    }
  }

  function install() {
    if (window[KEY]) return;
    var store = { items: new Map(), seen: 0, answers: 0 };
    try {
      Object.defineProperty(window, KEY, { value: store });
    } catch (e) {
      return;
    }

    function keep(raw) {
      var item = compact(raw);
      if (!item) return 0;
      store.items.delete(item.id);
      store.items.set(item.id, item);
      while (store.items.size > MAX_ITEMS) store.items.delete(store.items.keys().next().value);
      return 1;
    }

    function collect(json) {
      if (!json || typeof json !== 'object') return 0;
      var kept = 0;
      kept += keep(own(own(json, 'itemInfo'), 'itemStruct'));
      kept += keep(own(json, 'itemStruct'));
      var lists = [own(json, 'itemList'), own(json, 'aweme_list'), own(own(json, 'data'), 'itemList')];
      for (var i = 0; i < lists.length; i++) {
        if (!Array.isArray(lists[i])) continue;
        for (var j = 0; j < lists[i].length; j++) kept += keep(lists[i][j]);
      }
      return kept;
    }

    function read(text) {
      try {
        if (typeof text !== 'string' || text.length > MAX_ANSWER_CHARS) return;
        if (!/itemList|itemStruct|aweme_list/.test(text)) return;
        if (collect(JSON.parse(text)) > 0) store.answers += 1;
      } catch (e) {
        // Not JSON, or not a shape this script knows: TikTok's own code is not affected.
      }
    }

    function isApi(url) {
      try {
        var address = new URL(url, location.href);
        var site = location.hostname.split('.').slice(-2).join('.');
        var host = address.hostname;
        var sameSite = host === site || host.slice(-(site.length + 1)) === '.' + site;
        return sameSite && address.protocol === location.protocol &&
          address.pathname.indexOf('/api/') === 0;
      } catch (e) {
        return false;
      }
    }

    function keepNative(wrapper, native) {
      try {
        Object.defineProperty(wrapper, 'toString', {
          value: function () { return Function.prototype.toString.call(native); }
        });
      } catch (e) {
        // Only the description differs; the call is the native one.
      }
      return wrapper;
    }

    var nativeFetch = window.fetch;
    if (typeof nativeFetch === 'function') {
      window.fetch = keepNative(function () {
        var answer = nativeFetch.apply(this, arguments);
        try {
          var input = arguments[0];
          var url = typeof input === 'string' ? input : input && input.url;
          if (url && isApi(String(url)) && answer && typeof answer.then === 'function') {
            answer.then(function (response) {
              try {
                if (!response || !response.ok || response.bodyUsed) return;
                // The clone is taken before TikTok's code reads the body; it is read after.
                var copy = response.clone();
                store.seen += 1;
                setTimeout(function () {
                  try {
                    copy.text().then(read, function () {});
                  } catch (e) {
                    // Ignored: the answer TikTok's code got is unchanged.
                  }
                }, 0);
              } catch (e) {
                // Ignored: the answer TikTok's code got is unchanged.
              }
            }, function () {});
          }
        } catch (e) {
          // Ignored: TikTok's request went out unchanged.
        }
        return answer;
      }, nativeFetch);
    }

    var request = window.XMLHttpRequest && window.XMLHttpRequest.prototype;
    if (request && typeof request.open === 'function' && typeof request.send === 'function') {
      var nativeOpen = request.open;
      var nativeSend = request.send;
      var opened = new WeakMap();
      request.open = keepNative(function (method, url) {
        try {
          opened.set(this, String(url));
        } catch (e) {
          // Ignored.
        }
        return nativeOpen.apply(this, arguments);
      }, nativeOpen);
      request.send = keepNative(function () {
        try {
          var xhr = this;
          var url = opened.get(xhr);
          if (url && isApi(url)) {
            xhr.addEventListener('loadend', function () {
              setTimeout(function () {
                try {
                  if (xhr.status < 200 || xhr.status >= 300) return;
                  store.seen += 1;
                  if (xhr.responseType === '' || xhr.responseType === 'text') {
                    read(xhr.responseText);
                  } else if (xhr.responseType === 'json' && xhr.response) {
                    read(JSON.stringify(xhr.response));
                  }
                } catch (e) {
                  // Ignored: the answer TikTok's code got is unchanged.
                }
              }, 0);
            });
          }
        } catch (e) {
          // Ignored: TikTok's request goes out unchanged.
        }
        return nativeSend.apply(this, arguments);
      }, nativeSend);
    }
  }

  function answer(from, item, store) {
    if (!item) return null;
    var json = JSON.stringify(item);
    if (json.length > MAX_CHARS) return JSON.stringify({ v: 1, none: true, tooLarge: true });
    return JSON.stringify({
      v: 1,
      from: from,
      id: item.id,
      item: json,
      answers: store ? store.answers : 0
    });
  }

  function find(want) {
    var id = want ? String(want) : idFromAddress();
    var store = window[KEY];
    if (!id) return JSON.stringify({ v: 1, none: true, noId: true });
    var status;
    var data = document.getElementById('__UNIVERSAL_DATA_FOR_REHYDRATION__');
    if (data) {
      try {
        var scope = own(JSON.parse(data.textContent || ''), '__DEFAULT_SCOPE__') || {};
        for (var key in scope) {
          if (!Object.prototype.hasOwnProperty.call(scope, key)) continue;
          var detail = scope[key];
          var item = own(own(detail, 'itemInfo'), 'itemStruct');
          if (item && String(own(item, 'id')) === id) {
            var made = answer('script', compact(item), store);
            if (made) return made;
          }
          var code = first(own(detail, 'statusCode'), own(detail, 'statusCodeV2'));
          if (!item && typeof code === 'number' && code !== 0 && idFromAddress() === id &&
              key.indexOf('video') >= 0) {
            status = code;
          }
        }
      } catch (e) {
        // The next source may still have the post.
      }
    }
    var sigi = document.getElementById('SIGI_STATE');
    if (sigi) {
      try {
        var module = own(JSON.parse(sigi.textContent || ''), 'ItemModule');
        var post = own(module, id);
        if (post) {
          var fromSigi = answer('script', compact(post), store);
          if (fromSigi) return fromSigi;
        }
      } catch (e) {
        // The store may still have the post.
      }
    }
    var kept = store && store.items && store.items.get(id);
    if (kept) {
      var fromStore = answer('api', kept, store);
      if (fromStore) return fromStore;
    }
    return JSON.stringify({
      v: 1,
      none: true,
      status: status,
      check: hasCheck() || undefined,
      answers: store ? store.answers : undefined
    });
  }

  try {
    if (mode === 'store') {
      install();
      return null;
    }
    return find(wanted);
  } catch (e) {
    return null;
  }
})
