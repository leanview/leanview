package com.liskovsoft.youtubeapi.channelgroups

import com.liskovsoft.googleapi.youtubedata3.impl.ItemMetadata
import com.liskovsoft.googlecommon.common.converters.gson.WithGson
import com.liskovsoft.googlecommon.common.helpers.DefaultHeaders
import com.liskovsoft.googlecommon.common.helpers.RetrofitHelper
import com.liskovsoft.youtubeapi.browse.v2.BrowseApiHelper
import com.liskovsoft.youtubeapi.common.helpers.AppClient
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/** leanview: the part of the InnerTube browse answer of a channel that names it: `metadata.channelMetadataRenderer`. */
internal data class ChannelMetadataResult(val metadata: Metadata?) {
    data class Metadata(val channelMetadataRenderer: Renderer?)
    data class Renderer(val title: String?, val externalId: String?, val avatar: Avatar?)
    data class Avatar(val thumbnails: List<Thumbnail?>?)
    data class Thumbnail(val url: String?, val width: Int?, val height: Int?)
}

/** leanview: the browse request the channel screen already makes (`BrowseService2.getChannelWeb`, WEB client), read for its metadata. */
@WithGson
internal interface ChannelMetadataApi {
    @Headers(
        "Content-Type: application/json",
        "User-Agent: " + DefaultHeaders.USER_AGENT_WEB,
        "Referer: https://www.youtube.com/"
    )
    @POST("https://www.youtube.com/youtubei/v1/browse")
    fun getChannelMetadata(@Body browseQuery: String?): Call<ChannelMetadataResult?>
}

/**
 * leanview: the title and the icon of channels, from InnerTube (the YouTube calls the app already makes) instead of the YouTube Data
 * API, whose key came from SmartTube's constants.json (owner ruling 2026-11-16: no Data API key). One browse request per channel;
 * a channel whose request fails is left out (the callers then keep what they had: the name of the export, or no icon).
 */
internal object ChannelMetadataService {
    private val api: ChannelMetadataApi by lazy { RetrofitHelper.create(ChannelMetadataApi::class.java) }

    /** The request of one channel; replaced by the tests with recorded answers. */
    @Volatile
    internal var fetch: (channelId: String) -> ChannelMetadataResult? = { channelId ->
        RetrofitHelper.get(api.getChannelMetadata(BrowseApiHelper.getChannelHomeQuery(AppClient.WEB, channelId)))
    }

    /** The metadata of every channel that answered, in the order asked; null when none did. */
    @JvmStatic
    fun getChannelMetadata(vararg channelIds: String): List<ItemMetadata>? {
        val result = channelIds.distinct().mapNotNull { id ->
            val renderer = try {
                fetch(id)?.metadata?.channelMetadataRenderer
            } catch (e: Exception) {
                null
            }
            renderer?.let { ChannelItemMetadata(it.externalId ?: id, it.title, bestAvatar(it.avatar)) }
        }
        return result.ifEmpty { null }
    }

    /** The largest avatar (the channel header gives one, 900 px). */
    private fun bestAvatar(avatar: ChannelMetadataResult.Avatar?): String? =
        avatar?.thumbnails?.filterNotNull()?.filter { it.url != null }?.maxByOrNull { it.width ?: 0 }?.url

    private class ChannelItemMetadata(
        override val channelId: String?,
        override val title: String?,
        override val cardImageUrl: String?
    ) : ItemMetadata {
        override val videoId: String? = null
        override val playlistId: String? = null
        override val channelTitle: String? = title
        override val publishedAt: String? = null
        override val durationIso: String? = null
        override val itemCount: Int? = null
    }
}
