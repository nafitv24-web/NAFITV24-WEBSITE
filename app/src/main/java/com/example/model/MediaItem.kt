package com.example.model

enum class MediaType {
    LIVE_EVENT,
    LIVE_TV,
    MOVIE,
    SERIES
}

data class StreamServer(
    val name: String,
    val url: String
)

typealias MediaServer = StreamServer

data class MediaItem(
    val id: String,
    val title: String,
    val category: String,
    val type: MediaType,
    val streamUrl: String,
    val backupUrl: String? = null,
    val servers: List<StreamServer> = emptyList(),
    val logoUrl: String? = null,
    val description: String? = null,
    val isLive: Boolean = false,
    val eventTime: String? = null,
    val tournament: String? = null,
    val status: String = "Live Now", // "LIVE NOW", "UPCOMING", "Finished"
    val team1: String? = null,
    val team2: String? = null,
    val team1Logo: String? = null,
    val team2Logo: String? = null,
    val matchTimeFormatted: String? = null, // e.g. "06:30 AM, Aug 13"
    val countdownTargetSeconds: Long? = null, // Remaining seconds or timestamp for live ticking countdown
    val score1: String? = null,
    val score2: String? = null,
    val quality: String = "HD",
    val rating: String? = null,
    val year: String? = null,
    val country: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val cookie: String? = null,
    val origin: String? = null,
    val customHeaders: Map<String, String>? = null,
    val drmScheme: String? = null,
    val drmLicenseUrl: String? = null,
    val drmLicenseKey: String? = null,
    val drmHeaders: Map<String, String>? = null,
    val manifestType: String? = null
) {
    val headers: Map<String, String>? get() = customHeaders
    // Helper to get all available server URLs
    fun getAllServers(): List<StreamServer> {
        val list = mutableListOf<StreamServer>()
        if (servers.isNotEmpty()) {
            list.addAll(servers.filter { it.url.isNotBlank() })
        }
        if (streamUrl.isNotBlank() && list.none { it.url.trim().equals(streamUrl.trim(), ignoreCase = true) }) {
            list.add(0, StreamServer("সার্ভার ১", streamUrl.trim()))
        }
        if (!backupUrl.isNullOrBlank() && 
            !backupUrl.trim().equals(streamUrl.trim(), ignoreCase = true) && 
            list.none { it.url.trim().equals(backupUrl.trim(), ignoreCase = true) }
        ) {
            list.add(StreamServer("সার্ভার ২", backupUrl.trim()))
        }

        // Automatic mirror endpoints for Pixeldrain URLs
        val lower = streamUrl.lowercase()
        if (lower.contains("pixeldrain") || lower.contains("pixeldra.in")) {
            val fileId = streamUrl.substringAfter("/api/file/").substringAfter("/u/").substringBefore("?").substringBefore("/")
            if (fileId.isNotBlank()) {
                val s1 = "https://pixeldrain.dev/api/file/$fileId?download"
                val s2 = "https://pixeldrain.com/api/file/$fileId?download"
                val s3 = "https://pixeldrain.com/api/file/$fileId"
                if (list.none { it.url.equals(s1, ignoreCase = true) }) list.add(StreamServer("Pixeldrain Dev", s1))
                if (list.none { it.url.equals(s2, ignoreCase = true) }) list.add(StreamServer("Pixeldrain Com", s2))
                if (list.none { it.url.equals(s3, ignoreCase = true) }) list.add(StreamServer("Pixeldrain Direct", s3))
            }
        }

        val distinctList = list.distinctBy { it.url.trim() }
        if (distinctList.isEmpty()) {
            return if (streamUrl.isNotBlank()) listOf(StreamServer("সার্ভার ১", streamUrl.trim())) else emptyList()
        }
        return distinctList.mapIndexed { index, server ->
            val num = index + 1
            val rawName = server.name.trim()
            val cleanName = if (rawName.isBlank() || rawName.equals(title.trim(), ignoreCase = true) || rawName.matches(Regex("^(?i)(server|সার্ভার)\\s*\\d*.*"))) {
                "সার্ভার $num"
            } else {
                "সার্ভার $num ($rawName)"
            }
            StreamServer(name = cleanName, url = server.url.trim())
        }
    }
}

data class PlaylistInfo(
    val id: String,
    val title: String,
    val url: String,
    val logoUrl: String? = null,
    val description: String? = null,
    val channelCount: Int = 0,
    val serverUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
    val type: String = "M3U", // "XTREAM" or "M3U"
    val isAdmin: Boolean = false,
    val isReadOnly: Boolean = false
) {
    val isProtected: Boolean get() = isAdmin || isReadOnly
}

data class MovieProvider(
    val id: String,
    val name: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val siteUrl: String = "",
    val searchUrl: String? = null,
    val types: List<String> = listOf("Movie", "Series"), // "Movie", "Series", "Anime", "Live"
    val language: String = "Multi",
    val flag: String? = null,
    val authors: String? = null,
    val version: String? = null,
    val status: String = "Ok",
    val size: String? = null,
    val supported: List<String> = listOf("Movie", "TvSeries"),
    val githubUrl: String? = null,
    val isInstalled: Boolean = true,
    val repoId: String? = null,
    val repoName: String? = null,
    val isCustom: Boolean = false,
    val isEnabled: Boolean = true
) {
    val url: String get() = siteUrl
    val logoUrl: String? get() = iconUrl
    val category: String get() = if (types.isNotEmpty()) types.joinToString(", ") else "Movie & Series"
    val enabled: Boolean get() = isEnabled
}

data class CloudStreamRepo(
    val id: String,
    val name: String,
    val repoUrl: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val providers: List<MovieProvider> = emptyList(),
    val isEnabled: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis(),
    val manifestVersion: Int = 1
) {
    val url: String get() = repoUrl
    val logoUrl: String? get() = iconUrl
    val enabled: Boolean get() = isEnabled
}

data class AppUpdateInfo(
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    val isForceUpdate: Boolean = false,
    val minSupportedVersionCode: Int = 1,
    val apkSize: String = "",
    val releaseDate: String = ""
)

data class ActiveUserInfo(
    val id: String,
    val deviceModel: String = "Android Device",
    val brand: String = "Android",
    val appVersion: String = "v2.5.6",
    val versionCode: Int = 28,
    val lastSeen: Long = System.currentTimeMillis(),
    val registeredAt: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true,
    val location: String = "বাংলাদেশ",
    val city: String = "ঢাকা",
    val country: String = "Bangladesh",
    val countryCode: String = "BD",
    val ip: String = "",
    val isp: String = "",
    val networkType: String = "WiFi",
    val currentActivity: String = "ব্রাউজিং"
)

data class LocationTrafficStat(
    val locationName: String,
    val count: Int,
    val percentage: Float,
    val flag: String = "🇧🇩"
)

data class AppUserAnalytics(
    val totalUsers: Int = 1,
    val activeUsers: Int = 1,
    val activeUsersList: List<ActiveUserInfo> = emptyList(),
    val topLocations: List<LocationTrafficStat> = emptyList(),
    val liveLocations: List<LocationTrafficStat> = emptyList(),
    val networkBreakdown: Map<String, Int> = emptyMap(),
    val liveNetworkBreakdown: Map<String, Int> = emptyMap(),
    val lastUpdated: Long = System.currentTimeMillis()
)


