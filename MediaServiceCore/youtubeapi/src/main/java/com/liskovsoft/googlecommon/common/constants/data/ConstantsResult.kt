package com.liskovsoft.googlecommon.common.constants.data

// leanview: only the Google OAuth client (Drive backup) is read; the YouTube Data API key the file also carries is not used
// (owner ruling 2026-11-16: channel titles and icons come from InnerTube, see ChannelMetadataService)
internal data class ConstantsResult(
    val clientId: String?,
    val clientSecret: String?,
)
