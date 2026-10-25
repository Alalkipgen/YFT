package com.alal.yft.extractor.master.social

import com.alal.yft.extractor.master.recipes.ContractMedia
import com.alal.yft.extractor.master.recipes.ContractRecipe

/**
 * VK-Master plan §4 (V1, V2), Appendix B step 4: VK's contract, the recipe the engine's contract
 * layer reads VK's rows with. **Not** in `ContractRecipes.ALL`: agent D adds it there when VK's
 * ladder is wired; until then only a test (and D) pass it to `SiteContracts`.
 *
 * It reads the one video's answer [VkReader] writes from main's embed page or a `video.get` answer
 * ([VkReader.Read.answer]): `{"id","title","duration","image","expires","files":[{"key","url",
 * "type","height"}]}` — the video's `{owner}_{id}` (the identity check), its files with their MIME
 * types in VK's order, the title, the length in seconds, the picture and the files' earliest
 * expiry (epoch seconds). The answer's own address (V1's embed page on vk.com or vk.ru, V1's or
 * V2's `video.get` on api.vkvideo.ru) must be one of [ContractRecipe.answerHosts].
 *
 * The endpoint keeps a `{version}` placeholder the contract step never fills, so R8 never asks VK
 * on its own (`SiteContracts` answers "no endpoint for this video"): VK's asks are main's and V2's
 * ([VkLadder]), each under route B's rules. No media host is set (VK's video servers are numbered,
 * which an exact host list cannot name): [VkReader] writes only files on VK's video servers
 * ([VkClient.mediaFile]: never okcdn.ru's image host) and V asks only VK's media hosts
 * ([VkSite.SITE]).
 */
internal object VkRecipe {
    val RECIPE = ContractRecipe(
        site = VkClient.SITE_ID,
        endpoint = "https://${VkClient.API_HOST}${VkClient.VIDEO_GET_PATH}?v={version}&videos={id}",
        answerHosts = setOf(VkClient.API_HOST, VkClient.COM_HOST, VkClient.RU_HOST),
        idPaths = listOf(listOf("id")),
        media = listOf(ContractMedia(path = listOf("files", "*"), mimeField = "type")),
        titlePaths = listOf(listOf("title")),
        durationSecondsPaths = listOf(listOf("duration")),
        thumbnailPaths = listOf(listOf("image")),
        expiryPath = listOf("expires"),
    )
}
