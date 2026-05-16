// ============================================================
// YouTubeProvider.kt
// Plays YouTube videos ad-free through Invidious API direct
// streams. Falls back to youtube-nocookie.com WebView embed
// if every Invidious instance is unavailable.
// ============================================================

package com.yourplugin.youtube

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.net.URLEncoder

class YouTubeProvider : MainAPI() {

    // ── Provider meta ─────────────────────────────────────────
    override var mainUrl  = "https://www.youtube.com"
    override var name     = "YouTube (No Ads)"
    override val hasMainPage     = true
    override val hasQuickSearch  = false
    override var lang            = "en"
    override val supportedTypes  = setOf(TvType.Others)

    // ── Invidious instances (tried in order, first working wins)
    private val instances = listOf(
        "https://inv.nadeko.net",
        "https://invidious.privacyredirect.com",
        "https://invidious.nerdvpn.de",
        "https://yt.cdaut.de",
        "https://invidious.flokinet.to",
    )

    // Cache the first live instance across requests
    @Volatile private var liveInstance = instances[0]

    // ── Home page sections (mapped to Invidious trending types) ─
    override val mainPage = mainPageOf(
        "trending?type=default" to "🔥 Trending",
        "trending?type=music"   to "🎵 Trending Music",
        "trending?type=gaming"  to "🎮 Trending Gaming",
        "trending?type=movies"  to "🎬 Trending Movies",
    )

    // ── Data-class models for Invidious JSON ──────────────────

    data class InvThumb(
        @JsonProperty("quality") val quality: String?,
        @JsonProperty("url")     val url:     String?,
        @JsonProperty("width")   val width:   Int?,
        @JsonProperty("height")  val height:  Int?,
    )

    data class InvSearchItem(
        @JsonProperty("type")              val type:              String?,
        @JsonProperty("videoId")           val videoId:           String?,
        @JsonProperty("title")             val title:             String?,
        @JsonProperty("author")            val author:            String?,
        @JsonProperty("description")       val description:       String?,
        @JsonProperty("videoThumbnails")   val videoThumbnails:   List<InvThumb>?,
        @JsonProperty("viewCount")         val viewCount:         Long?,
        @JsonProperty("lengthSeconds")     val lengthSeconds:     Int?,
    )

    data class InvFormat(
        @JsonProperty("url")          val url:          String?,
        @JsonProperty("itag")         val itag:         String?,
        @JsonProperty("type")         val type:         String?,
        @JsonProperty("quality")      val quality:      String?,
        @JsonProperty("container")    val container:    String?,
        @JsonProperty("qualityLabel") val qualityLabel: String?,
        @JsonProperty("resolution")   val resolution:   String?,
        @JsonProperty("bitrate")      val bitrate:      Long?,
        @JsonProperty("audioQuality") val audioQuality: String?,
    )

    data class InvVideo(
        @JsonProperty("title")           val title:           String?,
        @JsonProperty("videoId")         val videoId:         String?,
        @JsonProperty("description")     val description:     String?,
        @JsonProperty("author")          val author:          String?,
        @JsonProperty("videoThumbnails") val videoThumbnails: List<InvThumb>?,
        @JsonProperty("adaptiveFormats") val adaptiveFormats: List<InvFormat>?,
        @JsonProperty("formatStreams")   val formatStreams:   List<InvFormat>?,
        @JsonProperty("lengthSeconds")   val lengthSeconds:   Int?,
        @JsonProperty("viewCount")       val viewCount:       Long?,
        @JsonProperty("publishedText")   val publishedText:   String?,
        @JsonProperty("keywords")        val keywords:        List<String>?,
    )

    // Data bundled into the LoadResponse.data field
    data class VideoData(
        @JsonProperty("videoId") val videoId: String,
        @JsonProperty("title")   val title:   String,
    )

    // ── Helpers ────────────────────────────────────────────────

    private fun encode(s: String): String = URLEncoder.encode(s, "utf-8")

    /** Pick the best available thumbnail URL. */
    private fun List<InvThumb>?.bestThumb(): String? {
        if (this == null) return null
        return firstOrNull { it.quality == "maxresdefault" }?.url
            ?: firstOrNull { it.quality == "sddefault"     }?.url
            ?: firstOrNull { it.quality == "high"          }?.url
            ?: firstOrNull()?.url
    }

    /** Map a qualityLabel string ("1080p", "720p60", …) to CloudStream quality int. */
    private fun labelToQuality(label: String?): Int = when {
        label == null         -> Qualities.Unknown.value
        "2160" in label       -> Qualities.P2160.value
        "1440" in label       -> Qualities.P1440.value
        "1080" in label       -> Qualities.P1080.value
        "720"  in label       -> Qualities.P720.value
        "480"  in label       -> Qualities.P480.value
        "360"  in label       -> Qualities.P360.value
        "240"  in label       -> Qualities.P240.value
        "144"  in label       -> Qualities.P144.value
        else                  -> Qualities.Unknown.value
    }

    /** Try every Invidious instance until one responds, caches the winner. */
    private suspend fun fetchLiveApi(path: String): String? {
        // Try the cached live instance first for speed
        val ordered = listOf(liveInstance) + (instances - liveInstance)
        for (inst in ordered) {
            try {
                val resp = app.get("$inst/api/v1/$path", timeout = 10_000)
                if (resp.isSuccessful) {
                    liveInstance = inst          // update cache
                    return resp.text
                }
            } catch (_: Exception) { /* try next */ }
        }
        return null
    }

    // ── Main page ─────────────────────────────────────────────

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val raw = fetchLiveApi(request.data) ?: return newHomePageResponse(request.name, emptyList())
        val items = parseJson<List<InvSearchItem>>(raw)

        val searchResults = items.mapNotNull { item ->
            val id    = item.videoId ?: return@mapNotNull null
            val title = item.title   ?: return@mapNotNull null
            newMovieSearchResponse(
                name  = title,
                url   = "$mainUrl/watch?v=$id",
                type  = TvType.Others,
            ) {
                posterUrl = item.videoThumbnails.bestThumb()
            }
        }
        return newHomePageResponse(request.name, searchResults)
    }

    // ── Search ────────────────────────────────────────────────

    override suspend fun search(query: String): List<SearchResponse> {
        val raw = fetchLiveApi("search?q=${encode(query)}&type=video&page=1") ?: return emptyList()
        return parseJson<List<InvSearchItem>>(raw).mapNotNull { item ->
            val id    = item.videoId ?: return@mapNotNull null
            val title = item.title   ?: return@mapNotNull null
            newMovieSearchResponse(
                name = title,
                url  = "$mainUrl/watch?v=$id",
                type = TvType.Others,
            ) {
                posterUrl = item.videoThumbnails.bestThumb()
            }
        }
    }

    // ── Load (video detail page) ───────────────────────────────

    override suspend fun load(url: String): LoadResponse? {
        // Extract video ID from the YouTube watch URL
        val videoId = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)
            ?.groupValues?.get(1) ?: return null

        val raw  = fetchLiveApi("videos/$videoId") ?: return null
        val info = parseJson<InvVideo>(raw)

        val data = VideoData(videoId = videoId, title = info.title ?: "Unknown").toJson()

        return newMovieLoadResponse(
            name = info.title ?: "Unknown",
            url  = url,
            type = TvType.Others,
            data = data,
        ) {
            posterUrl   = info.videoThumbnails.bestThumb()
            plot        = info.description
            tags        = buildList {
                info.author?.let { add(it) }
                info.keywords?.take(5)?.let { addAll(it) }
            }
            year        = null   // not returned by Invidious for videos
        }
    }

    // ── Load links (actual playback URLs, ad-free!) ────────────

    override suspend fun loadLinks(
        data:             String,
        isCasting:        Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback:         (ExtractorLink) -> Unit,
    ): Boolean {
        // Parse the bundled VideoData
        val videoData = try {
            parseJson<VideoData>(data)
        } catch (_: Exception) {
            // Fallback: treat data directly as a videoId
            VideoData(videoId = data, title = "YouTube")
        }
        val videoId = videoData.videoId

        // ── Strategy 1: Invidious direct streams (NO ads) ──────
        var foundDirect = false

        for (instance in listOf(liveInstance) + (instances - liveInstance)) {
            try {
                val raw  = app.get("$instance/api/v1/videos/$videoId", timeout = 10_000)
                if (!raw.isSuccessful) continue
                val info = parseJson<InvVideo>(raw.text)

                // Combined audio+video streams (easiest for players)
                info.formatStreams?.forEach { fmt ->
                    val streamUrl = fmt.url ?: return@forEach
                    val label     = fmt.qualityLabel ?: fmt.quality ?: "?"
                    callback(
                        newExtractorLink(
                            source = name,
                            name   = "Direct [$label]",
                            url    = streamUrl,
                            type   = ExtractorLinkType.VIDEO,
                        ) {
                            quality  = labelToQuality(label)
                            referer  = "$instance/watch?v=$videoId"
                            isM3u8   = false
                        }
                    )
                    foundDirect = true
                }

                // Adaptive video-only streams (high quality, no audio)
                // Listed with a note so the user knows they're video-only
                info.adaptiveFormats
                    ?.filter { it.type?.startsWith("video/") == true }
                    ?.forEach { fmt ->
                        val streamUrl = fmt.url ?: return@forEach
                        val label     = fmt.qualityLabel ?: fmt.resolution ?: "?"
                        callback(
                            newExtractorLink(
                                source = name,
                                name   = "Video-only [$label]",
                                url    = streamUrl,
                                type   = ExtractorLinkType.VIDEO,
                            ) {
                                quality  = labelToQuality(label)
                                referer  = "$instance/watch?v=$videoId"
                                isM3u8   = false
                            }
                        )
                        foundDirect = true
                    }

                if (foundDirect) {
                    liveInstance = instance   // update cache
                    break
                }
            } catch (e: Exception) {
                logError(e)
            }
        }

        // ── Strategy 2: Invidious HLS stream ──────────────────
        // Some Invidious instances expose an HLS manifest
        if (!foundDirect) {
            try {
                val hlsUrl = "$liveInstance/latest_version?id=$videoId&itag=17"
                callback(
                    newExtractorLink(
                        source = name,
                        name   = "Invidious HLS",
                        url    = "$liveInstance/api/manifest/hls_playlist/$videoId/index.m3u8",
                        type   = ExtractorLinkType.M3U8,
                    ) {
                        quality = Qualities.Unknown.value
                        referer = "$liveInstance/watch?v=$videoId"
                        isM3u8  = true
                    }
                )
                foundDirect = true
            } catch (e: Exception) {
                logError(e)
            }
        }

        // ── Strategy 3: youtube-nocookie.com embed WebView ─────
        // Opens the privacy-enhanced YouTube embed in CloudStream's
        // internal WebView. YouTube ads are blocked by the embed
        // URL parameters and the nocookie domain.
        if (!foundDirect) {
            val embedUrl = buildString {
                append("https://www.youtube-nocookie.com/embed/$videoId")
                append("?autoplay=1")
                append("&modestbranding=1")   // hides YouTube logo
                append("&rel=0")             // no related videos
                append("&iv_load_policy=3")  // no annotations
                append("&fs=0")              // disable fullscreen button (CS handles it)
                append("&cc_load_policy=0")
                append("&origin=https://www.youtube-nocookie.com")
            }
            callback(
                newExtractorLink(
                    source = name,
                    name   = "YouTube Embed (WebView)",
                    url    = embedUrl,
                    type   = ExtractorLinkType.VIDEO,
                ) {
                    quality = Qualities.Unknown.value
                    referer = "https://www.youtube-nocookie.com/"
                }
            )
        }

        return true
    }
}
