package com.alal.yft.core.browser.policy

/**
 * YFT's own short list of pop-up, pop-under and redirect ad networks (P32), written for YFT from
 * what these networks are known for; not a copy of any filter list. A site's own video ads are
 * not the target (F4): networks that mainly serve players' pre-roll ads are not listed.
 *
 * A listed host and every subdomain of it is blocked as a top-level navigation and as a new
 * window. [Network.blockResources] also empties its scripts, frames and images, so the pop-up
 * code never runs; it is off where the same host also serves other ads a page may need.
 */
internal object AdNetworks {
    data class Network(
        val name: String,
        val reason: String,
        val hosts: List<String>,
        val blockResources: Boolean = true,
    )

    val all: List<Network> = listOf(
        Network(
            "PopAds",
            "pop-under windows opened on the first tap anywhere on a page",
            listOf("popads.net", "popadscdn.net"),
        ),
        Network("PopCash", "pop-under windows on the first tap", listOf("popcash.net")),
        Network(
            "PropellerAds / Monetag",
            "\"onclick\" pop-unders and tab redirects to their landing pages",
            listOf(
                "propellerads.com", "propellerclick.com", "onclickads.net", "onclkds.com",
                "monetag.com",
            ),
        ),
        Network(
            "Adsterra",
            "pop-unders and \"smart link\" redirects of the whole tab",
            listOf("adsterra.com", "adsterratech.com"),
        ),
        Network(
            "Clickadu",
            "pop-unders and redirects on video sites",
            listOf("clickadu.com", "clickadu.net"),
        ),
        Network(
            "HilltopAds",
            "pop-unders and redirects",
            listOf("hilltopads.net", "hilltopads.com"),
        ),
        Network(
            "AdMaven",
            "pop-unders and full-tab redirects",
            listOf("ad-maven.com", "admaven.com"),
        ),
        Network("Galaksion", "pop-unders and redirects", listOf("galaksion.com")),
        Network("PopMyAds", "pop-unders", listOf("popmyads.com")),
        Network("PopTM", "pop-unders", listOf("poptm.com")),
        Network("RichAds", "pop-unders and push-ad landing pages", listOf("richads.com")),
        Network("Adcash", "pop-unders and redirects", listOf("adcash.com")),
        Network("Bidvertiser", "pop-unders and redirects", listOf("bidvertiser.com")),
        Network("Zeropark", "redirect and pop traffic", listOf("zeropark.com")),
        Network("JuicyAds", "pop-unders on video sites", listOf("juicyads.com")),
        Network("PlugRush", "pop-unders and redirects on video sites", listOf("plugrush.com")),
        Network("Ero-Advertising", "pop-unders on video sites", listOf("ero-advertising.com")),
        Network(
            "ExoClick",
            "pop-unders and tab redirects on video sites; it also serves banners, so only its " +
                "pages and windows are blocked",
            listOf("exoclick.com", "exosrv.com", "exdynsrv.com"),
            blockResources = false,
        ),
        Network(
            "TrafficStars",
            "pop-unders and redirects; it also serves players' ads, so only its pages and " +
                "windows are blocked",
            listOf("trafficstars.com", "tsyndicate.com"),
            blockResources = false,
        ),
        Network(
            "Notix",
            "\"press Allow\" pages that ask for notification permission after a redirect",
            listOf("notix.io"),
        ),
    )

    private val byHost: Map<String, Network> =
        all.flatMap { network -> network.hosts.map { it to network } }.toMap()

    /** The network of [host] or of a domain it is under (`a.b.popads.net` → PopAds). */
    fun find(host: String): Network? {
        var name = host
        while (true) {
            byHost[name]?.let { return it }
            val dot = name.indexOf('.')
            if (dot < 0 || dot == name.lastIndex) return null
            name = name.substring(dot + 1)
        }
    }
}
