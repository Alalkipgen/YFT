// A tiny stand-in for a YouTube player build, written for YFT's own-solver tests (it contains no
// YouTube code). It has the shapes the solver relies on: the namespace wrapper, a string table, a
// URL class with a parameter setter and getter, a stream-URL builder that marks its URL with
// ("alr", "yes"), a signatureCipher call site, index-only sig steps and an n scramble. Options
// switch the code style and inject the faults the SelfCheck must catch.

const ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

function swap(list, index) {
  const first = list[0];
  list[0] = list[index % list.length];
  list[index % list.length] = first;
}

/** What the fake player's sig steps make of [input]. */
export function expectedSig(input) {
  const list = input.split("");
  swap(list, 7);
  list.reverse();
  list.splice(0, 3);
  swap(list, 21);
  return list.join("");
}

/** What the fake player's n scramble makes of [input]. */
export function expectedN(input) {
  const list = input.split("");
  for (let index = 0; index < list.length; index += 1) {
    const at = ALPHABET.indexOf(list[index]);
    list[index] = at < 0 ? list[index] : ALPHABET[(at + index * 7 + 3) % 64];
  }
  list.reverse();
  return list.join("").slice(2);
}

/**
 * Source text of a fake player.
 *   style     "es5" (default) | "es6" (class, arrows, let/const) | "seq" (sequences, table access)
 *   nIn       "set" (n applied when set, default) | "build" (applied when the URL string is built)
 *   setter / getter   accessor names (default "set" / "get")
 *   wrapper   "namespace" (default) | "call" | "arrow" | "define" | "three" (unknown shapes)
 *   decoy     true: a second marked builder with different sig steps (a wrong candidate)
 *   throwing  true: a second marked builder that throws with its input in the message
 *   needsSelf true: the n scramble depends on a table read from `self` at the top level
 *   unmarked  true: no ("alr", "yes") marker and no call site (no candidates)
 *   constantN true: the n scramble returns the same value for every input
 *   driftAfter k: the n scramble changes after k calls (state corrupted during a run)
 *   badSig    true: the sig steps add a character the input never had
 *   sortSig   true: the sig steps sort the characters (depends on content, not on positions)
 */
export function fakePlayer(options = {}) {
  const o = { style: "es5", nIn: "set", setter: "set", getter: "get", wrapper: "namespace", ...options };
  const S = o.setter;
  const G = o.getter;
  const nOnSet = o.nIn === "set" ? `a==="n"&&(b=nscr(b));` : "";
  const nOnBuild = o.nIn === "build" ? `!this.done&&this.q.n&&(this.q.n=nscr(this.q.n),this.done=!0);` : "";
  const shift = o.needsSelf
    ? "NQ.length-2"
    : o.driftAfter !== undefined
      ? `((self.__fakeCalls=(self.__fakeCalls||0)+1)>${o.driftAfter}?4:3)`
      : "3";
  const sigSteps = o.sortSig
    ? 'a=a[T[6]]("");a.sort();return a[T[7]]("")'
    : `a=a[T[6]]("");K.sw(a,7);K.rv(a);K.sp(a,3);K.sw(a,21);return a[T[7]]("")${o.badSig ? '+"~"' : ""}`;
  const nResult = o.constantN ? '"Qw3rTy0uIoPa5sDf"' : 'b.join("").slice(2)';
  const marker = o.unmarked ? `a.${S}("mark","no")` : `a.${S}("alr","yes")`;
  const lines = [
    "var window=this;",
    "'use strict';",
    "var T,K,A,NQ;",
    `T="alr;yes;${S};${G};url;s;split;join".split(";");`,
    `A="${ALPHABET}";`,
    o.needsSelf ? "NQ=self.navigator.language;" : "NQ=\"en-US\";",
    "K={sw:function(a,b){var c=a[0];a[0]=a[b%a.length];a[b%a.length]=c},rv:function(a){a.reverse()},sp:function(a,b){a.splice(0,b)}};",
    "var dec=function(a){return decodeURIComponent(a)},enc=function(a){return encodeURIComponent(a)};",
  ];
  if (o.style === "es6") {
    lines.push(
      "const VERSION=\"es6\";",
      "let counter=0;",
      `var dsig=a=>{${sigSteps}};`,
      `var nscr=(a)=>{let b=a.split(""),c=A.length;for(let d=0;d<b.length;d++){const e=A.indexOf(b[d]);b[d]=e<0?b[d]:A[(e+d*7+${shift})%c]}b.reverse();return ${nResult}};`,
      "g.Url=class{" +
        "constructor(a,b){this.base=a.split(\"?\")[0];this.q={};this.keys=[];this.done=!1;const c=a.indexOf(\"?\")>=0?a.slice(a.indexOf(\"?\")+1).split(\"&\"):[];for(const d of c){const e=d.split(\"=\");this." + S + "(dec(e[0]),dec(e[1]||\"\"))}}" +
        `${S}(a,b){${nOnSet}this.keys.indexOf(a)<0&&this.keys.push(a);this.q[a]=b}` +
        `${G}(a){return this.q[a]}` +
        `build(){${nOnBuild}const a=[];for(const c of this.keys)a.push(enc(c)+"="+(c==="s"?this.q[c]:enc(this.q[c])));return this.base+"?"+a.join("&")}` +
        "clone(){const a=new g.Url(this.base);for(const b of this.keys)a.q[b]=this.q[b],a.keys.push(b);a.done=this.done;return a}" +
        "};",
      `var zW=(a,b="",c="")=>{a=new g.Url(a,!0);${marker};if(c){c=dsig(dec(c));a[T[2]](b,enc(c))}return a};`,
    );
  } else {
    lines.push(
      `var dsig=function(a){${sigSteps}};`,
      `var nscr=function(a){var b=a.split(""),c=A.length;for(var d=0;d<b.length;d++){var e=A.indexOf(b[d]);b[d]=e<0?b[d]:A[(e+d*7+${shift})%c]}b.reverse();return ${nResult}};`,
      "g.Url=function(a,b){this.base=a.split(\"?\")[0];this.q={};this.keys=[];this.done=!1;var c=a.indexOf(\"?\")>=0?a.slice(a.indexOf(\"?\")+1).split(\"&\"):[];for(var d=0;d<c.length;d++){var e=c[d].split(\"=\");this." + S + "(dec(e[0]),dec(e[1]||\"\"))}};",
      `g.Url.prototype.${S}=function(a,b){${nOnSet}this.keys.indexOf(a)<0&&this.keys.push(a);this.q[a]=b};`,
      `g.Url.prototype.${G}=function(a){return this.q[a]};`,
      `g.Url.prototype.build=function(){${nOnBuild}var a=[];for(var b=0;b<this.keys.length;b++){var c=this.keys[b];a.push(enc(c)+"="+(c==="s"?this.q[c]:enc(this.q[c])))}return this.base+"?"+a.join("&")};`,
      "g.Url.prototype.clone=function(){var a=new g.Url(this.base);for(var b=0;b<this.keys.length;b++)a.q[this.keys[b]]=this.q[this.keys[b]],a.keys.push(this.keys[b]);a.done=this.done;return a};",
      o.style === "seq"
        ? `var zW=function(a,b,c){return b=b===void 0?"":b,c=c===void 0?"":c,a=new g.Url(a,!0),${o.unmarked ? marker : "a[T[2]](T[0],T[1])"},c&&(c=dsig(dec(c)),a[T[2]](b,enc(c))),a};`
        : `var zW=function(a,b,c){b=b===void 0?"":b;c=c===void 0?"":c;a=new g.Url(a,!0);${marker};c&&(c=dsig(dec(c)),a[T[2]](b,enc(c)));return a};`,
    );
  }
  if (o.decoy) {
    lines.push(
      `var zX=function(a,b,c){b=b===void 0?"":b;c=c===void 0?"":c;a=new g.Url(a,!0);a.${S}("alr","yes");c&&(c=dec(c).split("").reverse().join(""),a[T[2]](b,enc(c)));return a};`,
    );
  }
  if (o.throwing) {
    lines.push(
      `var zY=function(a,b,c){a=new g.Url(a,!0);a.${S}("alr","yes");if(c)throw new Error("cannot use "+dec(c));return a};`,
    );
  }
  lines.push(
    "var parseCipher=function(a){var b={};a.split(\"&\").forEach(function(c){var d=c.split(\"=\");b[d[0]]=dec(d[1]||\"\")});return b};",
    o.unmarked
      ? "g.formatUrl=function(d){return new g.Url(d.url||\"\")};"
      : "g.formatUrl=function(d){var Z=parseCipher(d.signatureCipher||\"\");return zW(Z.url||d.url||\"\",Z.sp,Z.s)};",
    "g.audio=window.AudioContext.prototype;",
    "g.boot(window.document);",
    "new g.Url(\"https://example.invalid/\");",
    "if(typeof g.flag===\"undefined\"){g.flag=1}",
    "for(var i=0;i<3;i++)g[\"k\"+i]=i;",
    "g.ready=!0,g.count=3;",
  );
  const body = lines.join("\n");
  switch (o.wrapper) {
    case "call":
      return `(function(){var g={};\n${body}\n}).call(this);\n`;
    case "arrow":
      return `var _yt_player={};((g)=>{\n${body}\n})(_yt_player);\n`;
    case "define":
      return `define(function(g){\n${body}\n});\n`;
    case "three":
      return `var _yt_player={};var other=1;(function(g){\n${body}\n})(_yt_player);\n`;
    default:
      return `var _yt_player={};(function(g){\n${body}\n})(_yt_player);\n`;
  }
}
