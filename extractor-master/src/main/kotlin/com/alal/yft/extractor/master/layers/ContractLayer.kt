package com.alal.yft.extractor.master.layers

import com.alal.yft.core.model.media.CandidateSource
import com.alal.yft.core.model.media.MediaCandidate
import com.alal.yft.core.model.media.PageMediaRole
import com.alal.yft.extractor.master.MasterRequest
import com.alal.yft.extractor.master.PageSnapshot
import com.alal.yft.extractor.master.recipes.ContractRecipes
import com.alal.yft.extractor.master.toolkit.CandidateFactory
import com.alal.yft.extractor.master.toolkit.HtmlScan

/**
 * L2: HTML5 `<video>`/`<source>` and OpenGraph video tags outside scripts; R8: a site
 * endpoint's answer the engine asked for ([PageSnapshot.contract]) is read with that site's
 * key table ([ContractRecipes], [ContractReader]).
 */
internal class ContractLayer : MasterLayer {
    override val id = LayerId.L2_CONTRACT

    override fun collect(request: MasterRequest, snapshot: PageSnapshot): Evidence {
        snapshot.contract?.let { answer ->
            val recipe = ContractRecipes.of(answer.site) ?: return Evidence(emptyList())
            return ContractReader(request, recipe, answer).read()
        }
        val body = snapshot.html ?: return Evidence(emptyList())
        val factory = CandidateFactory(request)
        val found = mutableListOf<MediaCandidate>()
        val markup = HtmlScan.SCRIPT.replace(body, "")
        val videos = HtmlScan.VIDEO.findAll(markup).toList()
        videos.forEachIndexed { index, match ->
            val key = "master:html-player:$index"
            val role = PageMediaRole.MAIN.takeIf { videos.size == 1 }
            (listOf(match.groupValues[1]) + HtmlScan.SOURCE.findAll(match.groupValues[2])
                .map { it.groupValues[1] }.toList()).forEach { tag ->
                val attrs = HtmlScan.attributes(tag)
                factory.candidate(
                    attrs["src"], attrs["type"], request.expectedContentId, role,
                    CandidateSource.DOM, key = key,
                )?.let(found::add)
            }
        }
        HtmlScan.META.findAll(markup).forEach { match ->
            val attrs = HtmlScan.attributes(match.groupValues[1])
            if (attrs["property"]?.lowercase() in OG_VIDEO) {
                factory.candidate(
                    attrs["content"], id = request.expectedContentId,
                    source = CandidateSource.DOM, key = "master:opengraph",
                )?.let(found::add)
            }
        }
        return Evidence(found)
    }

    private companion object {
        val OG_VIDEO = setOf("og:video", "og:video:url", "og:video:secure_url")
    }
}
