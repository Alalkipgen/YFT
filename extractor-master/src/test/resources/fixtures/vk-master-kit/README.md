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

Files (shape; what `VkClientSourceTest` checks):

- `page/vkvideo-page.html`: the page lists today's scripts, one of them JSON-escaped, beside
  off-host and plain-http decoys; the right script is read and the decoys are skipped.
- `script/error-monitoring.js`: the small chunk, the client block and a decoy `clientId` outside
  it; the client id, from one script request.
- `script/common.js`: `API_VERSION:()=>"5.000"` beside look-alike names; the API version only.
- `script/core-spa.js`: the big chunk with both values; the fallback when the page lists neither
  small script.
- `script/web-client-snippet.js`: A's snippet (see above); the client id, and no API version.
