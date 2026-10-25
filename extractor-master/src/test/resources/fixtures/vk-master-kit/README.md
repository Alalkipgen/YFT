# VK-Master kit fixtures (agent C, `work/phase-vk-master-kit`)

FIXTURE files for `social/VkClientSource.kt` (VK plan section 8, part 2) and its test,
`VkClientSourceTest`. **Every value here is made up**; nothing was copied from a live answer. The
files were written by hand from the shapes in agent A's recon (`docs/VK_MASTER_CENSUS.md` section 5
and 3.2a on `origin/work/phase-vk-master-census`, read only, never merged).

- Made-up values: client id `100000`, client secret `FIXTUREsecretFIXTURE`, app id `200000` and
  API version `5.000` (A's fixture values); decoy client id `300000`; decoy versions `5.111`,
  `5.222`, `5.333` and `6.000`. Script names carry a `FIXTURE` hash; hosts are `st.vkvideo.ru` and a
  made-up shard `st1-23.vkvideo.ru` (the sharding A saw), plus off-host and plain-http decoys.
- `script/web-client-snippet.js` is an exact copy of A's
  `extractor-master/src/test/resources/fixtures/vk-master/script/web-client-snippet.js` at
  `d333fb18` (copied, not merged). It writes the API version as an assignment (`API_VERSION=`),
  which the strict `API_VERSION:` anchor does not take (the test pins this).

Agent D's wiring (`work/phase-vk-master-wire`, the live check of 2026-10-26, signed out): only
`web/chunks/common` is read. It carries both values: the client id as VK's export
`VK_WEB_VKVIDEO_APP_ID:()=>n` with `n` assigned nearby as hex or digits (the fixture writes
`Ab=0x186a0`, made-up `100000`), and the API version as `API_VERSION:()=>n});let n="5.000"` or
`API_VERSION:()=>"5.000"`. Live, error_monitoring's client id is a hex literal C's anchor did not
take, and core_spa's names are stripped, so both are now decoys the page lists beside it.

Files (shape; what `VkClientSourceTest` checks):

- `page/vkvideo-page.html`: the page lists today's scripts, one of them JSON-escaped, beside
  off-host and plain-http decoys; the common script is read, every other address is skipped.
- `script/common.js`: the live shapes, both values (`100000` from `0x186a0`, `5.000`) beside
  look-alike names (`VK_WEB_APP_ID`, `MIN_API_VERSION`, `apiVersion`, `clientId`) and a made-up
  secret that is never read.
- `script/error-monitoring.js`: C's small chunk with the client block; a decoy now (never read).
- `script/core-spa.js`: C's big chunk with both values; a decoy now (never read).
- `script/web-client-snippet.js`: A's snippet (see above); neither anchor takes it.
