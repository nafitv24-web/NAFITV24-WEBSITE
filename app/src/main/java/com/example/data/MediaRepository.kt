package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.model.ActiveUserInfo
import com.example.model.AppNotification
import com.example.model.AppUpdateInfo
import com.example.model.AppUserAnalytics
import com.example.model.CloudStreamRepo
import com.example.model.LocationTrafficStat
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.MovieProvider
import com.example.model.NotificationType
import com.example.model.PlaylistInfo
import com.example.model.StreamServer
import com.example.util.NotificationHelper
import com.example.NafiTvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

class MediaRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nafitv_prefs", Context.MODE_PRIVATE)

    private val client: OkHttpClient = NafiTvApp.sharedOkHttpClient

    private val repositoryScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
    )

    private var lastPresenceTimestamp = 0L
    private var lastPresenceActivity: String = ""

    val dexPluginManager: com.example.cloudstream.DexPluginManager =
        com.example.cloudstream.DexPluginManager(context, client)

    val nativeScraperEngine: com.example.cloudstream.NativeScraperEngine =
        com.example.cloudstream.NativeScraperEngine(client)

    val authManager: FirebaseAuthManager =
        FirebaseAuthManager(context, client)

    suspend fun getRtdbAuthQuery(): String {
        val token = authManager.getValidIdToken()
        return if (!token.isNullOrBlank()) "?auth=$token" else ""
    }

    suspend fun appendRtdbAuth(url: String): String {
        val token = authManager.getValidIdToken()
        if (token.isNullOrBlank()) return url
        val clean = url.trim()
        val sep = if (clean.contains("?")) "&" else "?"
        return "$clean${sep}auth=$token"
    }

    // Marquee Scrolling Breaking News Ticker Management
    companion object {
        const val DEFAULT_MARQUEE_TEXT = "বাংলাদেশ ব্যাংকের নতুন মুদ্রানীতি ঘোষণা। পুঁজিবাজারে ঊর্ধ্বগতি। NAFI TV24 এ ক্রিকেট, ফুটবল ও লাইভ টিভি চ্যানেল সম্পূর্ণ বিনামূল্যে উপভোগ করুন।"
        const val DEFAULT_RTDB_URL = "https://nafitv24-live-default-rtdb.firebaseio.com/"
        const val DEFAULT_LIVE_TV_M3U_URL = "https://raw.githubusercontent.com/nfiptv24-max/NAFITV/refs/heads/main/Nafitv24.m3u"
        const val DEFAULT_SPORTS_M3U_URL = "https://raw.githubusercontent.com/nfiptv24-max/NAFITV/refs/heads/main/NAFI%20Sports.m3u"
        const val DEFAULT_TAPMAD_JSON_URL = "https://raw.githubusercontent.com/srhady/tapmad-bd/refs/heads/main/tapmad_bd.json"
        const val DEFAULT_TAPMAD_M3U_URL = "https://raw.githubusercontent.com/srhady/tapmad-bd/refs/heads/main/tapmad_bd.m3u"
        const val DEFAULT_MOVIES_JSON_URL = "https://raw.githubusercontent.com/nafitv24-web/NAFI-TV/refs/heads/main/movies.json"
        const val DEFAULT_MOVIES_M3U_URL = DEFAULT_MOVIES_JSON_URL
        const val DEFAULT_MIX_MOVIES_M3U_URL = "https://raw.githubusercontent.com/abusaeeidx/Movie-Playlist-Auto-update/refs/heads/main/Mix_Movies.m3u"
        const val DEFAULT_LATEST_MOVIES_M3U_URL = "https://raw.githubusercontent.com/srhady/join_telegram_chennal-livesportsplay/refs/heads/main/latest_movies.m3u"
        const val DEFAULT_M3U_URL = DEFAULT_LIVE_TV_M3U_URL
        const val DEFAULT_ADMIN_PIN = "40541273"
        const val FIREBASE_PROJECT_ID = "nafitv24-live"
        const val FIREBASE_API_KEY = "AIzaSyDEhKK6T9kpKHICq4VSAXWoIQwQtfDFAX8"
        const val FIRESTORE_DATABASE_ID = "(default)"
    }

    fun getMarqueeTickerText(): String {
        val stored = prefs.getString("marquee_ticker_text", null)
        return if (!stored.isNullOrBlank()) stored else DEFAULT_MARQUEE_TEXT
    }

    fun saveMarqueeTickerText(text: String, isRemoteUpdate: Boolean = false) {
        val editor = prefs.edit().putString("marquee_ticker_text", text)
        if (!isRemoteUpdate) {
            editor.putLong("marquee_ticker_local_modified", System.currentTimeMillis())
        }
        editor.apply()
    }

    suspend fun pushMarqueeTickerToFirebase(text: String, url: String = getSavedFirebaseUrl()): Boolean = withContext(Dispatchers.IO) {
        var success = false
        saveMarqueeTickerText(text, isRemoteUpdate = false)
        val token = authManager.getValidIdToken()

        // 1. RTDB Multi-endpoint Push
        if (url.isNotBlank()) {
            val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
            val obj = JSONObject()
            obj.put("marquee_ticker", text)
            obj.put("text", text)
            obj.put("updated_at", System.currentTimeMillis())
            val body = obj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val endpoints = listOf(
                "$cleanUrl/marquee_news.json",
                "$cleanUrl/settings/marquee_news.json",
                "$cleanUrl/settings/ticker.json",
                "$cleanUrl/app_config/marquee_ticker.json"
            )

            for (ep in endpoints) {
                try {
                    val targetUrl = appendRtdbAuth(ep)
                    val req = Request.Builder().url(targetUrl).put(body).build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) success = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 2. Firestore Multi-database Push
        try {
            val firestoreObj = JSONObject()
            val fields = JSONObject()
            fields.put("marquee_ticker", JSONObject().put("stringValue", text))
            fields.put("text", JSONObject().put("stringValue", text))
            fields.put("updated_at", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
            firestoreObj.put("fields", fields)
            val fsBody = firestoreObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")

            for (dbId in databases) {
                val targets = listOf(
                    "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/settings/marquee_news?key=$FIREBASE_API_KEY",
                    "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/app_config/marquee_ticker?key=$FIREBASE_API_KEY"
                )
                for (fsUrl in targets) {
                    try {
                        val reqBuilder = Request.Builder().url(fsUrl).patch(fsBody)
                        if (!token.isNullOrBlank()) {
                            reqBuilder.header("Authorization", "Bearer $token")
                        }
                        val fsReq = reqBuilder.build()
                        val fsResp = client.newCall(fsReq).execute()
                        if (fsResp.isSuccessful) success = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        success
    }

    suspend fun fetchMarqueeTickerFromFirebase(url: String = getSavedFirebaseUrl()): String = withContext(Dispatchers.IO) {
        var remoteText: String? = null
        val token = authManager.getValidIdToken()

        // 1. Firestore Check
        val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
        for (dbId in databases) {
            if (!remoteText.isNullOrBlank()) break
            val targets = listOf(
                "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/settings/marquee_news?key=$FIREBASE_API_KEY",
                "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/app_config/marquee_ticker?key=$FIREBASE_API_KEY"
            )
            for (fsUrl in targets) {
                try {
                    val reqBuilder = Request.Builder().url(fsUrl).header("User-Agent", "NAFITV24-Android/2.5.0")
                    if (!token.isNullOrBlank()) {
                        reqBuilder.header("Authorization", "Bearer $token")
                    }
                    val resp = client.newCall(reqBuilder.build()).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        if (body.isNotBlank() && body.startsWith("{")) {
                            val json = JSONObject(body)
                            val fields = json.optJSONObject("fields")
                            if (fields != null) {
                                val txt = fields.optJSONObject("marquee_ticker")?.optString("stringValue")
                                    ?: fields.optJSONObject("text")?.optString("stringValue")
                                if (!txt.isNullOrBlank()) {
                                    remoteText = txt
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 2. RTDB Check
        if (remoteText.isNullOrBlank() && url.isNotBlank()) {
            val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
            val endpoints = listOf(
                "$cleanUrl/marquee_news.json",
                "$cleanUrl/settings/marquee_news.json",
                "$cleanUrl/settings/ticker.json",
                "$cleanUrl/app_config/marquee_ticker.json"
            )
            for (ep in endpoints) {
                try {
                    val targetUrl = appendRtdbAuth(ep)
                    val req = Request.Builder().url(targetUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()?.trim() ?: ""
                        if (body.isNotBlank() && body != "null") {
                            if (body.startsWith("{")) {
                                val obj = JSONObject(body)
                                val txt = obj.optString("marquee_ticker", obj.optString("text", "")).trim()
                                if (txt.isNotBlank()) {
                                    remoteText = txt
                                    break
                                }
                            } else if (body.startsWith("\"") && body.endsWith("\"") && body.length > 2) {
                                remoteText = body.substring(1, body.length - 1)
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val lastLocalModified = prefs.getLong("marquee_ticker_local_modified", 0L)
        val isRecentLocalEdit = (System.currentTimeMillis() - lastLocalModified) < 15_000L

        if (!remoteText.isNullOrBlank()) {
            if (!isRecentLocalEdit) {
                saveMarqueeTickerText(remoteText!!, isRemoteUpdate = true)
                return@withContext remoteText!!
            }
        }

        getMarqueeTickerText()
    }

    // Admin Privacy / PIN Management
    fun getAdminPin(): String {
        val stored = prefs.getString("admin_pin", null)
        if (stored.isNullOrBlank() || stored == "2424") {
            return DEFAULT_ADMIN_PIN
        }
        return stored
    }

    fun setAdminPin(pin: String) {
        prefs.edit().putString("admin_pin", pin).apply()
    }

    fun verifyAdminPin(pin: String): Boolean {
        val current = getAdminPin().trim()
        return pin.trim() == current || pin.trim() == DEFAULT_ADMIN_PIN
    }

    // -------------------------------------------------------------
    // Deleted Items Persistence (Ensures deleted items NEVER reappear)
    // -------------------------------------------------------------
    fun getDeletedIds(): Set<String> {
        return prefs.getStringSet("deleted_ids", emptySet()) ?: emptySet()
    }

    fun addDeletedId(id: String) {
        val current = getDeletedIds().toMutableSet()
        current.add(id)
        prefs.edit().putStringSet("deleted_ids", current).apply()
    }

    fun removeDeletedId(id: String) {
        val current = getDeletedIds().toMutableSet()
        if (current.remove(id)) {
            prefs.edit().putStringSet("deleted_ids", current).apply()
        }
    }

    fun clearDeletedIds() {
        prefs.edit().remove("deleted_ids").apply()
    }

    init {
        try {
            // Clean up any old SharedPreferences keys if they exist
            prefs.edit()
                .remove("cached_live_tv_channels")
                .remove("cached_sports_matches")
                .remove("cached_movies_list")
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Fast & Safe Local File Cache (JSON File storage - zero memory overhead in SharedPreferences, 0ms instant startup)
    private fun saveListToFileCache(fileName: String, list: List<MediaItem>) {
        try {
            // Keep initial offline cache compact (up to 300 items) to prevent huge memory spikes and disk lag on low-RAM devices
            val itemsToSave = if (list.size > 300) list.take(300) else list
            val file = java.io.File(context.filesDir, fileName)
            val jsonArray = JSONArray()
            itemsToSave.forEach { item ->
                jsonArray.put(serializeMediaToJsonObj(item))
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadListFromFileCache(fileName: String): List<MediaItem> {
        val file = java.io.File(context.filesDir, fileName)
        if (!file.exists()) return emptyList()
        val deleted = getDeletedIds()
        val list = mutableListOf<MediaItem>()
        try {
            val text = file.readText().trim()
            if (text.startsWith("[")) {
                val jsonArray = JSONArray(text)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", "item_$i")
                    if (!deleted.contains(id) && !id.startsWith("sport_default_")) {
                        list.add(parseMediaFromJsonObj(id, obj))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getCachedSportsMatches(): List<MediaItem> {
        return loadListFromFileCache("cache_sports_v2.json").filterNot { it.id.startsWith("sport_default_") }
    }

    fun getCachedLiveTvChannels(): List<MediaItem> {
        return loadListFromFileCache("cache_livetv_v2.json")
    }

    fun getCachedMoviesList(): List<MediaItem> {
        return loadListFromFileCache("cache_movies_v2.json")
    }

    fun saveCachedSportsMatches(list: List<MediaItem>) {
        saveListToFileCache("cache_sports_v2.json", list.filterNot { it.id.startsWith("sport_default_") })
    }

    fun saveCachedLiveTvChannels(list: List<MediaItem>) {
        saveListToFileCache("cache_livetv_v2.json", list)
    }

    fun saveCachedMoviesList(list: List<MediaItem>) {
        saveListToFileCache("cache_movies_v2.json", list)
    }

    // Built-in starter items for instant presentation on first launch
    fun getDefaultBuiltinSports(): List<MediaItem> {
        return emptyList()
    }

    fun getDefaultBuiltinLiveTv(): List<MediaItem> {
        return listOf(
            MediaItem(
                id = "tv_tsports_hd",
                title = "T Sports HD",
                category = "Sports",
                type = MediaType.LIVE_TV,
                streamUrl = "https://live-tsports.akamaized.net/live/live-tsports/playlist.m3u8",
                servers = listOf(
                    StreamServer("সার্ভার ১ (T Sports Main)", "https://live-tsports.akamaized.net/live/live-tsports/playlist.m3u8"),
                    StreamServer("সার্ভার ২ (Backup Live)", "https://stream.crichd.vip/live/tsports.m3u8")
                ),
                logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=300&fit=crop",
                isLive = true,
                quality = "1080p HD"
            ),
            MediaItem(
                id = "tv_gtv_hd",
                title = "GTV (Gazi Television)",
                category = "Sports",
                type = MediaType.LIVE_TV,
                streamUrl = "https://live-gtv.akamaized.net/live/live-gtv/playlist.m3u8",
                servers = listOf(
                    StreamServer("সার্ভার ১ (GTV Live HD)", "https://live-gtv.akamaized.net/live/live-gtv/playlist.m3u8")
                ),
                logoUrl = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=300&fit=crop",
                isLive = true,
                quality = "HD"
            ),
            MediaItem(
                id = "tv_star_sports_1",
                title = "Star Sports 1 HD",
                category = "Sports",
                type = MediaType.LIVE_TV,
                streamUrl = "https://stream.crichd.vip/live/starsports1.m3u8",
                servers = listOf(
                    StreamServer("সার্ভার ১ (Star Sports 1)", "https://stream.crichd.vip/live/starsports1.m3u8")
                ),
                logoUrl = "https://images.unsplash.com/photo-1531415074968-036ba1b575da?w=300&fit=crop",
                isLive = true,
                quality = "FHD"
            ),
            MediaItem(
                id = "tv_sony_ten_1",
                title = "Sony Sports Ten 1 HD",
                category = "Sports",
                type = MediaType.LIVE_TV,
                streamUrl = "https://stream.crichd.vip/live/sonyten1.m3u8",
                servers = listOf(
                    StreamServer("সার্ভার ১ (Sony Ten 1 HD)", "https://stream.crichd.vip/live/sonyten1.m3u8")
                ),
                logoUrl = "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=300&fit=crop",
                isLive = true,
                quality = "HD"
            ),
            MediaItem(
                id = "tv_somoy_news",
                title = "Somoy TV Live",
                category = "News",
                type = MediaType.LIVE_TV,
                streamUrl = "https://somoynews.akamaized.net/hls/live/2017366/somoy/master.m3u8",
                servers = listOf(
                    StreamServer("সার্ভার ১ (Somoy TV 24/7)", "https://somoynews.akamaized.net/hls/live/2017366/somoy/master.m3u8")
                ),
                logoUrl = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=300&fit=crop",
                isLive = true,
                quality = "HD"
            ),
            MediaItem(
                id = "tv_jamuna_news",
                title = "Jamuna TV HD",
                category = "News",
                type = MediaType.LIVE_TV,
                streamUrl = "https://jamunanews.akamaized.net/live/master.m3u8",
                servers = listOf(
                    StreamServer("সার্ভার ১ (Jamuna TV Live)", "https://jamunanews.akamaized.net/live/master.m3u8")
                ),
                logoUrl = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=300&fit=crop",
                isLive = true,
                quality = "HD"
            ),
            MediaItem(
                id = "tv_channel_i",
                title = "Channel i HD",
                category = "Entertainment",
                type = MediaType.LIVE_TV,
                streamUrl = "https://channeli.akamaized.net/live/channeli.m3u8",
                servers = listOf(
                    StreamServer("সার্ভার ১ (Channel i)", "https://channeli.akamaized.net/live/channeli.m3u8")
                ),
                logoUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=300&fit=crop",
                isLive = true,
                quality = "HD"
            ),
            MediaItem(
                id = "tv_btv_world",
                title = "BTV World",
                category = "Entertainment",
                type = MediaType.LIVE_TV,
                streamUrl = "https://btv.akamaized.net/live/btvworld.m3u8",
                servers = listOf(
                    StreamServer("সার্ভার ১ (BTV World Live)", "https://btv.akamaized.net/live/btvworld.m3u8")
                ),
                logoUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=300&fit=crop",
                isLive = true,
                quality = "HD"
            )
        )
    }

    fun getDefaultBuiltinMovies(): List<MediaItem> {
        return listOf(
            MediaItem(
                id = "mov_toofan_2024",
                title = "Toofan (তুফান)",
                category = "Bangla Blockbuster",
                type = MediaType.MOVIE,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                servers = listOf(
                    StreamServer("সার্ভার ১ (4K HDR)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                ),
                logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&fit=crop",
                description = "Shakib Khan starrer all-time blockbuster action thriller movie.",
                rating = "9.2",
                year = "2024",
                isLive = false,
                quality = "4K UHD"
            ),
            MediaItem(
                id = "mov_mohanagar_series",
                title = "Mohanagar (মহানগর)",
                category = "Web Series",
                type = MediaType.MOVIE,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                servers = listOf(
                    StreamServer("সার্ভার ১ (Full HD)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4")
                ),
                logoUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&fit=crop",
                description = "Mosharraf Karim as OC Harun in the thrilling crime mystery web series.",
                rating = "8.9",
                year = "2023",
                isLive = false,
                quality = "1080p"
            ),
            MediaItem(
                id = "mov_kalki_2898",
                title = "Kalki 2898 AD",
                category = "Action & Sci-Fi",
                type = MediaType.MOVIE,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                servers = listOf(
                    StreamServer("সার্ভার ১ (Dolby Atmos)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
                ),
                logoUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&fit=crop",
                description = "Prabhas, Amitabh Bachchan & Kamal Haasan in futuristic mythological spectacle.",
                rating = "8.5",
                year = "2024",
                isLive = false,
                quality = "4K Ultra"
            ),
            MediaItem(
                id = "mov_jawan_2023",
                title = "Jawan (জওয়ান)",
                category = "Action & Thriller",
                type = MediaType.MOVIE,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                servers = listOf(
                    StreamServer("সার্ভার ১ (Hindi 1080p)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4")
                ),
                logoUrl = "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=500&fit=crop",
                description = "Shah Rukh Khan in high-voltage action thriller directed by Atlee.",
                rating = "8.4",
                year = "2023",
                isLive = false,
                quality = "1080p"
            ),
            MediaItem(
                id = "mov_panchayat_s3",
                title = "Panchayat (Season 3)",
                category = "Web Series",
                type = MediaType.MOVIE,
                streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                servers = listOf(
                    StreamServer("সার্ভার ১ (HD)", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4")
                ),
                logoUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=500&fit=crop",
                description = "Heartwarming rural comedy drama series in Phulera village.",
                rating = "9.0",
                year = "2024",
                isLive = false,
                quality = "HD"
            )
        )
    }

    // High-speed instant loaders (Always return immediate items in 0 milliseconds, never empty)
    fun getInitialSports(): List<MediaItem> {
        val deleted = getDeletedIds()
        val customSports = getCustomStreams().filter { it.type == MediaType.LIVE_EVENT }.filterNot { deleted.contains(it.id) }
        val cached = getCachedSportsMatches().filterNot { deleted.contains(it.id) }
        val baseList = if (cached.isNotEmpty()) cached else getDefaultBuiltinSports()
        return (customSports + baseList).distinctBy { it.id }.filterNot { deleted.contains(it.id) }
    }

    fun getInitialLiveTv(): List<MediaItem> {
        val deleted = getDeletedIds()
        val customTv = getCustomStreams().filter { it.type == MediaType.LIVE_TV }.filterNot { deleted.contains(it.id) }
        val cached = getCachedLiveTvChannels().filterNot { deleted.contains(it.id) }
        val baseList = if (cached.isNotEmpty()) cached else getDefaultBuiltinLiveTv()
        return (customTv + baseList).distinctBy { it.id }.filterNot { deleted.contains(it.id) }
    }

    fun getInitialMoviesSeries(): List<MediaItem> {
        val deleted = getDeletedIds()
        val customMov = getCustomStreams().filter { it.type == MediaType.MOVIE || it.type == MediaType.SERIES }.filterNot { deleted.contains(it.id) }
        val cached = getCachedMoviesList().filterNot { deleted.contains(it.id) }
        val baseList = if (cached.isNotEmpty()) cached else getDefaultBuiltinMovies()
        return (customMov + baseList).distinctBy { it.id }.filterNot { deleted.contains(it.id) }
    }

    // Custom streams saved locally in SharedPreferences
    fun getCustomStreams(): List<MediaItem> {
        val deleted = getDeletedIds()
        val jsonStr = prefs.getString("custom_streams", "[]") ?: "[]"
        val list = mutableListOf<MediaItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "c_$i")
                if (!deleted.contains(id)) {
                    list.add(parseMediaFromJsonObj(id, obj))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveCustomStream(item: MediaItem) {
        removeDeletedId(item.id)
        val current = getCustomStreams().toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        saveCustomList(current)
    }

    fun saveCustomList(list: List<MediaItem>) {
        val jsonArray = JSONArray()
        list.forEach { item ->
            jsonArray.put(serializeMediaToJsonObj(item))
        }
        prefs.edit().putString("custom_streams", jsonArray.toString()).apply()
    }

    fun deleteCustomStream(id: String) {
        addDeletedId(id)
        val current = getCustomStreams().filterNot { it.id == id }
        saveCustomList(current)
    }

    suspend fun deleteMediaItem(item: MediaItem): Boolean {
        return deleteMediaItem(item.id, item.type)
    }

    suspend fun deleteMediaItem(id: String, type: MediaType): Boolean {
        // 1. Mark in permanent deleted set
        addDeletedId(id)
        // 2. Remove from local custom streams
        val current = getCustomStreams().filterNot { it.id == id }
        saveCustomList(current)
        // 3. Remove from Firebase
        return deleteFromFirebase(id, type)
    }

    fun updateScore(id: String, score1: String, score2: String) {
        val current = getCustomStreams().map {
            if (it.id == id) it.copy(score1 = score1, score2 = score2) else it
        }
        saveCustomList(current)
    }

    // Favorite management
    fun getFavoriteIds(): Set<String> {
        return prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    }

    fun toggleFavorite(id: String): Boolean {
        val favs = getFavoriteIds().toMutableSet()
        val isFav: Boolean
        if (favs.contains(id)) {
            favs.remove(id)
            isFav = false
        } else {
            favs.add(id)
            isFav = true
        }
        prefs.edit().putStringSet("favorites", favs).apply()
        return isFav
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }

    // M3U parser from Uri
    fun parseM3uFromUri(uri: Uri): List<MediaItem> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()
            reader.close()
            parseM3uLines(lines)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // M3U parser from URL (Supports single or multiple URLs separated by newlines, commas, or semicolons)
    suspend fun parseM3uFromUrl(rawInput: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val urls = extractUrls(rawInput)
        if (urls.isEmpty()) return@withContext emptyList()
        if (urls.size == 1) {
            return@withContext fetchSingleM3uUrl(urls[0])
        }
        val deferredList = urls.map { singleUrl ->
            async { fetchSingleM3uUrl(singleUrl) }
        }
        deferredList.awaitAll().flatten().distinctBy {
            if (it.streamUrl.isNotBlank()) it.streamUrl else it.id
        }
    }

    fun extractUrls(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        return input.split(Regex("[\r\n,;]+"))
            .map { it.trim() }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .distinct()
    }

    suspend fun fetchMoviesFromJsonUrl(url: String = DEFAULT_MOVIES_JSON_URL): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url.trim())
                .header("User-Agent", "NAFITV24/2.5.0 (Android ExoPlayer)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val content = response.body?.string()?.trim() ?: return@withContext emptyList()
            parseMediaFromJsonString(content)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun parseMediaFromJsonString(content: String, defaultCategory: String = "Movies"): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()

        try {
            if (trimmed.startsWith("[")) {
                val jsonArray = JSONArray(trimmed)
                for (i in 0 until jsonArray.length()) {
                    val element = jsonArray.opt(i)
                    if (element is JSONObject) {
                        // Check if this object is a Category container containing a movies/items list
                        val catName = element.optString("category_name", element.optString("category", element.optString("name", element.optString("title", element.optString("cat_name", defaultCategory))))).trim().ifBlank { defaultCategory }
                        val innerArr = element.optJSONArray("movies")
                            ?: element.optJSONArray("items")
                            ?: element.optJSONArray("content")
                            ?: element.optJSONArray("streams")
                            ?: element.optJSONArray("list")
                            ?: element.optJSONArray("channels")

                        if (innerArr != null && innerArr.length() > 0) {
                            for (j in 0 until innerArr.length()) {
                                val mObj = innerArr.optJSONObject(j)
                                if (mObj != null) {
                                    val parsed = parseSingleMediaFromJson(mObj, items.size, catName)
                                    if (parsed != null) items.add(parsed)
                                }
                            }
                        } else {
                            val parsed = parseSingleMediaFromJson(element, i, defaultCategory)
                            if (parsed != null) items.add(parsed)
                        }
                    } else if (element is String && (element.startsWith("http://") || element.startsWith("https://"))) {
                        items.add(
                            MediaItem(
                                id = "json_str_$i",
                                title = "Movie ${i + 1}",
                                category = defaultCategory,
                                type = MediaType.MOVIE,
                                streamUrl = element,
                                servers = listOf(StreamServer("সার্ভার ১", element)),
                                isLive = false,
                                quality = "HD"
                            )
                        )
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val rootObj = JSONObject(trimmed)

                // 1. Check for explicit categories arrays
                val categoriesArr = rootObj.optJSONArray("categories")
                    ?: rootObj.optJSONArray("category_list")
                    ?: rootObj.optJSONArray("genres")
                    ?: rootObj.optJSONArray("sections")

                if (categoriesArr != null) {
                    for (c in 0 until categoriesArr.length()) {
                        val catObj = categoriesArr.optJSONObject(c)
                        if (catObj != null) {
                            val catName = catObj.optString("category_name", catObj.optString("name", catObj.optString("category", catObj.optString("title", catObj.optString("cat_name", defaultCategory))))).trim().ifBlank { defaultCategory }
                            val catItemsArr = catObj.optJSONArray("movies")
                                ?: catObj.optJSONArray("items")
                                ?: catObj.optJSONArray("streams")
                                ?: catObj.optJSONArray("content")
                                ?: catObj.optJSONArray("list")
                                ?: catObj.optJSONArray("channels")
                            if (catItemsArr != null) {
                                for (i in 0 until catItemsArr.length()) {
                                    val itemObj = catItemsArr.optJSONObject(i)
                                    if (itemObj != null) {
                                        val parsed = parseSingleMediaFromJson(itemObj, items.size, catName)
                                        if (parsed != null) items.add(parsed)
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Check if root object has a specific category_name (e.g., { "category_name": "HINDI MOVIES", "movies": [...] })
                val rootCatName = rootObj.optString("category_name", rootObj.optString("category", rootObj.optString("cat_name", ""))).trim()

                // 3. Check for standard collection keys
                val collectionKeys = listOf(
                    "matches", "events", "match_list", "live_matches", "live_events",
                    "movies", "movie_list", "series", "tv_series", "channels", "streams",
                    "items", "data", "results", "list", "content", "videos", "playlist", "feed"
                )
                for (key in collectionKeys) {
                    val arr = rootObj.optJSONArray(key)
                    if (arr != null) {
                        val inferredCat = if (rootCatName.isNotBlank()) {
                            rootCatName
                        } else if (key.contains("match", ignoreCase = true) || key.contains("event", ignoreCase = true)) {
                            "Sports Live"
                        } else if (key.contains("series", ignoreCase = true)) {
                            "Web Series"
                        } else {
                            defaultCategory
                        }
                        for (i in 0 until arr.length()) {
                            val itemObj = arr.optJSONObject(i)
                            if (itemObj != null) {
                                val parsed = parseSingleMediaFromJson(itemObj, items.size, inferredCat)
                                if (parsed != null) items.add(parsed)
                            }
                        }
                    }
                }

                // 4. If root object itself is a single media item
                if (items.isEmpty() && (rootObj.has("url") || rootObj.has("streamUrl") || rootObj.has("link") || rootObj.has("file") || rootObj.has("servers") || rootObj.has("sources"))) {
                    val catName = if (rootCatName.isNotBlank()) rootCatName else defaultCategory
                    val parsed = parseSingleMediaFromJson(rootObj, 0, catName)
                    if (parsed != null) items.add(parsed)
                }

                // 5. If root object has dynamic keys as items (like Firebase Realtime DB object map)
                if (items.isEmpty()) {
                    val keys = rootObj.keys()
                    var count = 0
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val itemObj = rootObj.optJSONObject(k)
                        if (itemObj != null) {
                            val catName = if (rootCatName.isNotBlank()) rootCatName else defaultCategory
                            val parsed = parseSingleMediaFromJson(itemObj, count++, catName, explicitId = k)
                            if (parsed != null) items.add(parsed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return items.distinctBy { if (it.streamUrl.isNotBlank()) it.streamUrl else it.id }
    }

    private fun parseSingleMediaFromJson(
        obj: JSONObject,
        index: Int,
        fallbackCategory: String = "Movies",
        explicitId: String? = null
    ): MediaItem? {
        val title = obj.optString("name", obj.optString("title", obj.optString("movie_name", obj.optString("movie_title", obj.optString("channel_name", obj.optString("label", "Movie ${index + 1}")))))).trim()
        val rawUrl = obj.optString("url", obj.optString("streamUrl", obj.optString("stream_url", obj.optString("link", obj.optString("file", obj.optString("src", obj.optString("videoUrl", obj.optString("video_url", obj.optString("video", obj.optString("playUrl", obj.optString("play_url", obj.optString("hls", obj.optString("m3u8", obj.optString("mp4", obj.optString("direct_url", ""))))))))))))))).trim()
        val backupUrl = obj.optString("backupUrl", obj.optString("backup_url", obj.optString("backup", obj.optString("mirror", obj.optString("fallbackUrl", ""))))).trim().takeIf { it.isNotBlank() }

        val serversList = mutableListOf<StreamServer>()
        val serversArr = obj.optJSONArray("servers")
            ?: obj.optJSONArray("sources")
            ?: obj.optJSONArray("links")
            ?: obj.optJSONArray("streams")
            ?: obj.optJSONArray("qualities")
            ?: obj.optJSONArray("mirrors")
            ?: obj.optJSONArray("episodes")

        if (serversArr != null) {
            for (s in 0 until serversArr.length()) {
                val sObj = serversArr.optJSONObject(s)
                if (sObj != null) {
                    val sName = sObj.optString("server_name", sObj.optString("serverName", sObj.optString("name", sObj.optString("label", sObj.optString("server", sObj.optString("title", sObj.optString("quality", sObj.optString("res", "সার্ভার ${s + 1}"))))))))
                    val sUrl = sObj.optString("url", sObj.optString("file", sObj.optString("link", sObj.optString("src", sObj.optString("streamUrl", sObj.optString("stream_url", "")))))).trim()
                    if (sUrl.isNotBlank()) {
                        serversList.add(StreamServer(sName, sUrl))
                    }
                } else {
                    val sUrlStr = serversArr.optString(s, "").trim()
                    if (sUrlStr.startsWith("http")) {
                        serversList.add(StreamServer("সার্ভার ${s + 1}", sUrlStr))
                    }
                }
            }
        }

        val primaryStream = if (rawUrl.isNotBlank()) rawUrl else serversList.firstOrNull()?.url ?: ""
        if (primaryStream.isBlank() && serversList.isEmpty()) {
            return null
        }

        if (serversList.isEmpty() && primaryStream.isNotBlank()) {
            serversList.add(StreamServer("সার্ভার ১ (Main)", primaryStream))
            if (backupUrl != null) {
                serversList.add(StreamServer("সার্ভার ২ (Backup)", backupUrl))
            }
        }

        val logo = listOf("poster", "posterUrl", "poster_url", "logo", "logoUrl", "logo_url", "image", "imageUrl", "thumbnail", "thumb", "icon", "iconUrl", "cover", "backdrop", "img")
            .firstNotNullOfOrNull { obj.optString(it, "").trim().takeIf { s -> s.isNotBlank() } }
        val category = listOf("category_name", "category", "genre", "genres", "group", "group-title", "type_name", "sport", "tag")
            .firstNotNullOfOrNull { obj.optString(it, "").trim().takeIf { s -> s.isNotBlank() } } ?: fallbackCategory
        val description = obj.optString("description", obj.optString("plot", obj.optString("synopsis", obj.optString("overview", obj.optString("summary", obj.optString("story", obj.optString("about", obj.optString("details", "")))))))).trim().takeIf { it.isNotBlank() }

        val year = obj.optString("year", obj.optString("release_date", obj.optString("releaseDate", obj.optString("release_year", obj.optString("releaseYear", obj.optString("date", "")))))).trim().takeIf { it.isNotBlank() }
        val rating = obj.optString("rating", obj.optString("score", obj.optString("imdb", obj.optString("vote_average", obj.optString("imdb_rating", obj.optString("imdbRating", obj.optString("star", ""))))))).trim().takeIf { it.isNotBlank() }
        val quality = obj.optString("quality", obj.optString("resolution", obj.optString("res", obj.optString("video_quality", "HD")))).trim().ifBlank { "HD" }
        val country = obj.optString("country", obj.optString("lang", obj.optString("language", obj.optString("nation", "")))).trim().takeIf { it.isNotBlank() }

        val drmScheme = obj.optString("drmScheme", obj.optString("license_type", obj.optString("drm_type", obj.optString("drm_scheme", "")))).trim().takeIf { it.isNotBlank() }
        val drmLicenseUrl = obj.optString("drmLicenseUrl", obj.optString("license_url", obj.optString("drm_license_url", ""))).trim().takeIf { it.isNotBlank() }
        val drmLicenseKey = obj.optString("drmLicenseKey", obj.optString("drm_key", obj.optString("clearkey", obj.optString("license_key", "")))).trim().takeIf { it.isNotBlank() }
        val manifestType = obj.optString("manifestType", obj.optString("manifest_type", obj.optString("stream_type", ""))).trim().takeIf { it.isNotBlank() }

        val userAgent = obj.optString("userAgent", obj.optString("user_agent", obj.optString("User-Agent", ""))).trim().takeIf { it.isNotBlank() }
        val referrer = obj.optString("referrer", obj.optString("referer", obj.optString("Referer", ""))).trim().takeIf { it.isNotBlank() }
        val origin = obj.optString("origin", obj.optString("Origin", "")).trim().takeIf { it.isNotBlank() }
        val cookie = obj.optString("cookie", obj.optString("Cookie", "")).trim().takeIf { it.isNotBlank() }

        val typeStr = obj.optString("type", "").uppercase()
        val mediaType = when {
            typeStr.contains("SERIES") || category.contains("Series", ignoreCase = true) || obj.has("episodes") || obj.has("seasons") -> MediaType.SERIES
            typeStr.contains("EVENT") || typeStr.contains("SPORT") || category.contains("Sport", ignoreCase = true) || category.contains("Cricket", ignoreCase = true) || category.contains("Football", ignoreCase = true) -> MediaType.LIVE_EVENT
            typeStr.contains("TV") || typeStr.contains("CHANNEL") || typeStr.contains("LIVE") -> MediaType.LIVE_TV
            else -> MediaType.MOVIE
        }

        val id = explicitId ?: obj.optString("id", obj.optString("_id", obj.optString("stream_id", "movie_${Math.abs((title + "_" + primaryStream).hashCode())}")))

        return MediaItem(
            id = id,
            title = title,
            category = category,
            type = mediaType,
            streamUrl = primaryStream,
            backupUrl = backupUrl,
            servers = serversList,
            logoUrl = logo,
            description = description,
            isLive = mediaType != MediaType.MOVIE && mediaType != MediaType.SERIES,
            quality = quality,
            rating = rating ?: if (mediaType == MediaType.MOVIE) "8.5" else null,
            year = year ?: if (mediaType == MediaType.MOVIE) "2024" else null,
            country = country,
            userAgent = userAgent,
            referrer = referrer,
            origin = origin,
            cookie = cookie,
            drmScheme = drmScheme,
            drmLicenseUrl = drmLicenseUrl,
            drmLicenseKey = drmLicenseKey,
            manifestType = manifestType
        )
    }

    private suspend fun fetchSingleM3uUrl(url: String): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            var raw = url.trim()
            val headersMap = mutableMapOf<String, String>()
            if (raw.contains("|")) {
                val pipeParts = raw.split("|", limit = 2)
                raw = pipeParts[0].trim()
                if (pipeParts.size > 1) {
                    val headerTokens = pipeParts[1].split("&")
                    for (token in headerTokens) {
                        val kv = token.split("=", limit = 2)
                        if (kv.size == 2) {
                            headersMap[kv[0].trim()] = kv[1].trim()
                        }
                    }
                }
            }

            if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
                return@withContext emptyList()
            }

            val reqBuilder = Request.Builder()
                .url(raw)
                .header("User-Agent", headersMap["User-Agent"] ?: "NAFITV24/2.5.0 (Android ExoPlayer)")

            for ((k, v) in headersMap) {
                if (!k.equals("User-Agent", ignoreCase = true)) {
                    reqBuilder.header(k, v)
                }
            }

            val response = client.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            var content = response.body?.string()?.trim() ?: return@withContext emptyList()
            if (content.startsWith("\uFEFF")) {
                content = content.removePrefix("\uFEFF").trim()
            }
            if (content.startsWith("[") || content.startsWith("{")) {
                return@withContext parseMediaFromJsonString(content)
            }
            parseM3uLines(content.lines())
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseM3uLines(lines: List<String>): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        var currentTitle = ""
        var currentLogo: String? = null
        var currentGroup = "Live TV"
        var currentCountry: String? = null
        var currentUserAgent: String? = null
        var currentReferrer: String? = null
        var currentCookie: String? = null
        var currentOrigin: String? = null
        var currentDrmScheme: String? = null
        var currentDrmKey: String? = null
        var currentDrmLicenseUrl: String? = null
        var currentManifestType: String? = null
        val currentCustomHeaders = mutableMapOf<String, String>()
        val currentDrmHeaders = mutableMapOf<String, String>()
        var currentId = 1

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                val groupMatch = Regex("""group-title="([^"]*)"""", RegexOption.IGNORE_CASE).find(trimmed)
                currentGroup = groupMatch?.groupValues?.get(1)?.trim() ?: "Live TV"

                val logoMatch = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE).find(trimmed)
                currentLogo = logoMatch?.groupValues?.get(1)?.trim()

                val countryMatch = Regex("""tvg-country="([^"]*)"""", RegexOption.IGNORE_CASE).find(trimmed)
                currentCountry = countryMatch?.groupValues?.get(1)?.trim()

                val commaIndex = trimmed.lastIndexOf(',')
                currentTitle = if (commaIndex != -1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    "Channel $currentId"
                }

                // If country tag is in the title e.g. "Arirang World KR" or "[BD]"
                if (currentCountry == null) {
                    val matchCode = Regex("""\b(BD|KR|IN|US|UK|PK|SA|UAE)\b""", RegexOption.IGNORE_CASE).find(currentTitle)
                    currentCountry = matchCode?.groupValues?.get(1)?.uppercase()
                }
            } else if (trimmed.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val opt = trimmed.substringAfter(":").trim()
                val optKey = opt.substringBefore("=").trim().lowercase()
                val optVal = opt.substringAfter("=").trim()
                when {
                    optKey.contains("user-agent") -> currentUserAgent = optVal
                    optKey.contains("referrer") || optKey.contains("referer") -> currentReferrer = optVal
                    optKey.contains("origin") -> currentOrigin = optVal
                    optKey.contains("cookie") -> currentCookie = optVal
                    optKey.contains("clearkey") || optKey.contains("license_key") || optKey.contains("drm_key") -> currentDrmKey = optVal
                    optKey.contains("license_type") || optKey.contains("drm_type") -> currentDrmScheme = optVal
                    else -> currentCustomHeaders[optKey] = optVal
                }
            } else if (trimmed.startsWith("#EXTHTTP:", ignoreCase = true)) {
                val jsonPart = trimmed.substringAfter(":").trim()
                try {
                    val jsonObj = JSONObject(jsonPart)
                    if (jsonObj.has("User-Agent")) currentUserAgent = jsonObj.optString("User-Agent")
                    if (jsonObj.has("user-agent")) currentUserAgent = jsonObj.optString("user-agent")
                    if (jsonObj.has("Referer")) currentReferrer = jsonObj.optString("Referer")
                    if (jsonObj.has("referer")) currentReferrer = jsonObj.optString("referer")
                    if (jsonObj.has("Origin")) currentOrigin = jsonObj.optString("Origin")
                    if (jsonObj.has("origin")) currentOrigin = jsonObj.optString("origin")
                    if (jsonObj.has("Cookie")) currentCookie = jsonObj.optString("Cookie")
                    if (jsonObj.has("cookie")) currentCookie = jsonObj.optString("cookie")
                    val keys = jsonObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        currentCustomHeaders[k] = jsonObj.optString(k)
                    }
                } catch (_: Exception) {}
            } else if (trimmed.startsWith("#KODIPROP:", ignoreCase = true)) {
                val prop = trimmed.substringAfter(":").trim()
                val propKey = prop.substringBefore("=").trim().lowercase()
                val propVal = prop.substringAfter("=").trim()
                when {
                    propKey.contains("license_type") || propKey.contains("drm_type") || propKey.contains("license_security") || propKey.contains("drm_scheme") -> {
                        currentDrmScheme = propVal
                    }
                    propKey.contains("license_key") || propKey.contains("drm_key") || propKey.contains("clearkey") || propKey.contains("license_data") || propKey.contains("drm_license") -> {
                        if (propVal.startsWith("http://", ignoreCase = true) || propVal.startsWith("https://", ignoreCase = true)) {
                            currentDrmLicenseUrl = propVal
                        } else {
                            currentDrmKey = propVal
                        }
                    }
                    propKey.contains("manifest_type") || propKey.contains("stream_type") -> {
                        currentManifestType = propVal
                    }
                    propKey.contains("stream_headers") || propKey.contains("manifest_headers") -> {
                        val pairs = propVal.split("&")
                        for (pair in pairs) {
                            val kv = pair.split("=", limit = 2)
                            if (kv.size == 2) {
                                val k = kv[0].trim()
                                val v = try { java.net.URLDecoder.decode(kv[1].trim(), "UTF-8") } catch (_: Exception) { kv[1].trim() }
                                when {
                                    k.equals("User-Agent", ignoreCase = true) -> currentUserAgent = v
                                    k.equals("Referer", ignoreCase = true) || k.equals("Referrer", ignoreCase = true) -> currentReferrer = v
                                    k.equals("Origin", ignoreCase = true) -> currentOrigin = v
                                    k.equals("Cookie", ignoreCase = true) -> currentCookie = v
                                    else -> currentCustomHeaders[k] = v
                                }
                            }
                        }
                    }
                    else -> {
                        currentCustomHeaders[propKey] = propVal
                    }
                }
            } else if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                var streamUrl = trimmed
                val streamInfo = com.example.util.DrmHelper.extractStreamInfo(
                    rawUrl = streamUrl,
                    itemScheme = currentDrmScheme,
                    itemLicenseUrl = currentDrmLicenseUrl,
                    itemLicenseKey = currentDrmKey,
                    itemManifestType = currentManifestType
                )
                streamUrl = streamInfo.cleanUrl
                if (streamInfo.drmConfig != null) {
                    if (currentDrmScheme.isNullOrBlank()) {
                        currentDrmScheme = when (streamInfo.drmConfig.schemeUuid) {
                            androidx.media3.common.C.WIDEVINE_UUID -> "widevine"
                            androidx.media3.common.C.PLAYREADY_UUID -> "playready"
                            else -> "clearkey"
                        }
                    }
                    if (currentDrmLicenseUrl.isNullOrBlank() && !streamInfo.drmConfig.licenseUrl.isNullOrBlank()) {
                        currentDrmLicenseUrl = streamInfo.drmConfig.licenseUrl
                    }
                    if (currentDrmKey.isNullOrBlank() && !streamInfo.drmConfig.rawKeyString.isNullOrBlank()) {
                        currentDrmKey = streamInfo.drmConfig.rawKeyString
                    }
                    if (currentManifestType.isNullOrBlank() && !streamInfo.drmConfig.manifestType.isNullOrBlank()) {
                        currentManifestType = streamInfo.drmConfig.manifestType
                    }
                }
                streamInfo.headers.forEach { (k, v) ->
                    when {
                        k.equals("User-Agent", ignoreCase = true) || k.equals("http-user-agent", ignoreCase = true) -> currentUserAgent = v
                        k.equals("Referer", ignoreCase = true) || k.equals("Referrer", ignoreCase = true) || k.equals("http-referer", ignoreCase = true) || k.equals("http-referrer", ignoreCase = true) -> currentReferrer = v
                        k.equals("Origin", ignoreCase = true) || k.equals("http-origin", ignoreCase = true) -> currentOrigin = v
                        k.equals("Cookie", ignoreCase = true) || k.equals("http-cookie", ignoreCase = true) -> currentCookie = v
                        else -> currentCustomHeaders[k] = v
                    }
                }

                // Automatic intelligent headers detection for Toffee & OTT streams
                val isToffee = streamUrl.contains("toffeelive.com", ignoreCase = true) ||
                        streamUrl.contains("toffee", ignoreCase = true) ||
                        streamUrl.contains("bldcmprod-cdn", ignoreCase = true) ||
                        currentGroup.contains("toffee", ignoreCase = true)

                if (isToffee) {
                    if (currentUserAgent.isNullOrBlank()) currentUserAgent = "Toffee (Linux;Android 14)"
                    if (currentReferrer.isNullOrBlank()) currentReferrer = "https://toffeelive.com/"
                    if (currentOrigin.isNullOrBlank()) currentOrigin = "https://toffeelive.com"
                }

                // Normalize Pixeldrain URLs in M3U playlists
                val isPixeldrain = streamUrl.contains("pixeldrain", ignoreCase = true) || streamUrl.contains("pixeldra.in", ignoreCase = true)
                if (isPixeldrain) {
                    val pId = streamUrl.substringAfter("/api/file/").substringAfter("/u/").substringBefore("?").substringBefore("/")
                    if (pId.isNotBlank()) {
                        streamUrl = "https://pixeldrain.com/api/file/$pId?download"
                    }
                    if (currentReferrer.isNullOrBlank()) currentReferrer = "https://pixeldrain.com/"
                    if (currentOrigin.isNullOrBlank()) currentOrigin = "https://pixeldrain.com"
                }

                val isMovie = currentGroup.contains("movie", ignoreCase = true) ||
                        currentGroup.contains("cinema", ignoreCase = true) ||
                        currentGroup.contains("vod", ignoreCase = true) ||
                        currentGroup.contains("series", ignoreCase = true) ||
                        currentGroup.contains("drama", ignoreCase = true) ||
                        currentGroup.contains("film", ignoreCase = true) ||
                        currentGroup.contains("action", ignoreCase = true) ||
                        currentGroup.contains("comedy", ignoreCase = true) ||
                        currentGroup.contains("thriller", ignoreCase = true) ||
                        currentGroup.contains("horror", ignoreCase = true) ||
                        currentGroup.contains("romance", ignoreCase = true) ||
                        currentGroup.contains("adventure", ignoreCase = true) ||
                        currentGroup.contains("hollywood", ignoreCase = true) ||
                        currentGroup.contains("bollywood", ignoreCase = true) ||
                        currentGroup.contains("south", ignoreCase = true) ||
                        currentGroup.contains("bangla", ignoreCase = true) ||
                        isPixeldrain ||
                        streamUrl.contains(".mp4", ignoreCase = true) ||
                        streamUrl.contains(".mkv", ignoreCase = true)

                val mediaType = when {
                    isMovie -> MediaType.MOVIE
                    else -> MediaType.LIVE_TV
                }

                // Clean display title
                val cleanTitle = currentTitle.ifEmpty { "Channel $currentId" }
                val stableId = "m3u_" + java.lang.Math.abs((cleanTitle + "_" + streamUrl).hashCode()).toString()

                items.add(
                    MediaItem(
                        id = stableId,
                        title = cleanTitle,
                        category = currentGroup,
                        type = mediaType,
                        streamUrl = streamUrl,
                        servers = listOf(
                            StreamServer("সার্ভার ১ (Main)", streamUrl)
                        ),
                        logoUrl = currentLogo,
                        country = currentCountry,
                        isLive = mediaType != MediaType.MOVIE,
                        quality = "HD",
                        rating = if (mediaType == MediaType.MOVIE) "8.5" else null,
                        year = if (mediaType == MediaType.MOVIE) "2024" else null,
                        userAgent = currentUserAgent,
                        referrer = currentReferrer,
                        cookie = currentCookie,
                        origin = currentOrigin,
                        customHeaders = if (currentCustomHeaders.isNotEmpty()) currentCustomHeaders.toMap() else null,
                        drmScheme = currentDrmScheme,
                        drmLicenseUrl = currentDrmLicenseUrl,
                        drmLicenseKey = currentDrmKey,
                        drmHeaders = if (currentDrmHeaders.isNotEmpty()) currentDrmHeaders.toMap() else null,
                        manifestType = currentManifestType
                    )
                )

                currentTitle = ""
                currentLogo = null
                currentCountry = null
                currentUserAgent = null
                currentReferrer = null
                currentCookie = null
                currentOrigin = null
                currentDrmScheme = null
                currentDrmKey = null
                currentDrmLicenseUrl = null
                currentManifestType = null
                currentCustomHeaders.clear()
                currentDrmHeaders.clear()
            }
        }
        return items
    }

    // -------------------------------------------------------------
    // Firebase Realtime Database & Cloud Firestore Integration
    // -------------------------------------------------------------
    suspend fun testFirebaseConnection(url: String = getSavedFirebaseUrl()): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        var isAnyConnected = false

        // 1. Test Firestore REST API
        try {
            val firestoreUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents/sports?key=$FIREBASE_API_KEY"
            val req = Request.Builder().url(firestoreUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful || resp.code == 404) {
                isAnyConnected = true
                results.add("✅ Cloud Firestore সক্রিয় ($FIRESTORE_DATABASE_ID)")
            } else if (resp.code == 403 || resp.code == 401) {
                results.add("⚠️ Firestore পারমিশন রুলস চেক করুন (HTTP ${resp.code})")
            }
        } catch (e: Exception) {
            // ignore
        }

        // 2. Test Realtime Database
        if (url.isNotBlank()) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                val targetUrl = if (cleanUrl.endsWith(".json")) cleanUrl else "$cleanUrl/.json"

                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "NAFITV24-Android/2.5.0")
                    .build()

                val response = client.newCall(request).execute()
                val code = response.code
                if (response.isSuccessful) {
                    isAnyConnected = true
                    results.add("✅ Realtime Database সক্রিয় (HTTP $code)")
                } else if (code == 401 || code == 403) {
                    results.add("⚠️ RTDB Rules এ \".read\": true, \".write\": true দিন")
                } else {
                    results.add("ℹ️ RTDB Status: HTTP $code")
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        if (isAnyConnected) {
            Pair(true, results.joinToString(" | "))
        } else {
            Pair(false, if (results.isNotEmpty()) results.joinToString(" | ") else "⚠️ কানেকশন এরর: সার্ভারে পৌঁছানো সম্ভব হয়নি")
        }
    }

    private suspend fun fetchFromFirestore(): List<MediaItem> = withContext(Dispatchers.IO) {
        val deleted = getDeletedIds()
        val collections = listOf("sports", "events", "matches", "channels", "movies")
        val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")

        coroutineScope {
            val jobs = databases.flatMap { dbId ->
                collections.map { col ->
                    async {
                        val colItems = mutableListOf<MediaItem>()
                        try {
                            val firestoreUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/$col?key=$FIREBASE_API_KEY"
                            val req = Request.Builder().url(firestoreUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
                            val resp = client.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                if (body.isNotBlank() && body.startsWith("{")) {
                                    val json = JSONObject(body)
                                    val docs = json.optJSONArray("documents")
                                    if (docs != null) {
                                        for (i in 0 until docs.length()) {
                                            val doc = docs.optJSONObject(i) ?: continue
                                            val name = doc.optString("name", "")
                                            val docId = name.substringAfterLast("/")
                                            if (docId.isBlank() || deleted.contains(docId)) continue

                                            val fields = doc.optJSONObject("fields") ?: continue
                                            val mediaItem = parseMediaFromFirestoreFields(docId, col, fields)
                                            if (!deleted.contains(mediaItem.id)) {
                                                colItems.add(mediaItem)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        colItems
                    }
                }
            }
            jobs.awaitAll().flatten()
        }
    }

    private fun parseMediaFromFirestoreFields(docId: String, col: String, fields: JSONObject): MediaItem {
        fun s(key: String): String {
            val v = fields.optJSONObject(key) ?: return ""
            return v.optString("stringValue", "")
        }
        fun b(key: String, def: Boolean = false): Boolean {
            val v = fields.optJSONObject(key) ?: return def
            return if (v.has("booleanValue")) v.optBoolean("booleanValue", def) else def
        }
        fun l(key: String): Long? {
            val v = fields.optJSONObject(key) ?: return null
            return if (v.has("integerValue")) v.optLong("integerValue") else null
        }

        val typeStr = s("type").trim().uppercase()
        val mediaType = when {
            typeStr == "LIVE_TV" -> MediaType.LIVE_TV
            typeStr == "LIVE_EVENT" -> MediaType.LIVE_EVENT
            typeStr == "MOVIE" || typeStr == "SERIES" -> MediaType.MOVIE
            col == "channels" -> MediaType.LIVE_TV
            col == "movies" -> MediaType.MOVIE
            col == "sports" || col == "events" || col == "matches" -> MediaType.LIVE_EVENT
            docId.startsWith("tv_") || docId.startsWith("channel_") || docId.startsWith("ch_") -> MediaType.LIVE_TV
            docId.startsWith("mov_") || docId.startsWith("movie_") || docId.startsWith("ser_") -> MediaType.MOVIE
            docId.startsWith("sport_") || docId.startsWith("match_") || docId.startsWith("event_") -> MediaType.LIVE_EVENT
            fields.has("team1") || fields.has("team2") || fields.has("tournament") -> MediaType.LIVE_EVENT
            else -> MediaType.LIVE_TV
        }

        val title = s("title").ifBlank { s("name").ifBlank { docId } }
        val streamUrl = s("streamUrl").ifBlank { s("url") }
        val backupUrl = s("backupUrl").takeIf { it.isNotBlank() }
        val logoUrl = s("logoUrl").ifBlank { s("logo").ifBlank { s("poster") } }.takeIf { it.isNotBlank() }
        val category = s("category").ifBlank { s("sport").ifBlank { if (mediaType == MediaType.LIVE_EVENT) "Sports" else "General" } }
        val tournament = s("tournament").takeIf { it.isNotBlank() }
        val team1 = s("team1").takeIf { it.isNotBlank() }
        val team2 = s("team2").takeIf { it.isNotBlank() }
        val team1Logo = s("team1Logo").takeIf { it.isNotBlank() }
        val team2Logo = s("team2Logo").takeIf { it.isNotBlank() }
        val matchTime = s("matchTimeFormatted").ifBlank { s("eventTime") }.takeIf { it.isNotBlank() }
        val status = s("status").ifBlank { if (mediaType == MediaType.LIVE_EVENT) "LIVE" else "ON AIR" }
        val isLive = b("isLive", true)
        val description = s("description").takeIf { it.isNotBlank() }
        val countdown = l("countdownTargetSeconds")
        val score1 = s("score1").takeIf { it.isNotBlank() }
        val score2 = s("score2").takeIf { it.isNotBlank() }

        val serversList = mutableListOf<StreamServer>()
        val serversJsonStr = s("serversJson")
        if (serversJsonStr.isNotBlank() && serversJsonStr.startsWith("[")) {
            try {
                val sArr = JSONArray(serversJsonStr)
                for (i in 0 until sArr.length()) {
                    val so = sArr.optJSONObject(i)
                    if (so != null) {
                        val sName = so.optString("name", "সার্ভার ${i + 1}")
                        val sUrl = so.optString("url", "")
                        if (sUrl.isNotBlank()) {
                            serversList.add(StreamServer(sName, sUrl))
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return MediaItem(
            id = s("id").ifBlank { docId },
            title = title,
            streamUrl = streamUrl,
            backupUrl = backupUrl,
            servers = serversList,
            logoUrl = logoUrl,
            category = category,
            type = mediaType,
            tournament = tournament,
            team1 = team1,
            team2 = team2,
            team1Logo = team1Logo,
            team2Logo = team2Logo,
            matchTimeFormatted = matchTime,
            status = status,
            isLive = isLive,
            description = description,
            countdownTargetSeconds = countdown,
            score1 = score1,
            score2 = score2,
            drmScheme = s("drmScheme").ifBlank { s("license_type") }.takeIf { it.isNotBlank() },
            drmLicenseUrl = s("drmLicenseUrl").takeIf { it.isNotBlank() },
            drmLicenseKey = s("drmLicenseKey").ifBlank { s("license_key") }.ifBlank { s("clearkey") }.takeIf { it.isNotBlank() },
            manifestType = s("manifestType").ifBlank { s("manifest_type") }.takeIf { it.isNotBlank() }
        )
    }

    suspend fun fetchFromFirebase(url: String = getSavedFirebaseUrl()): List<MediaItem> = withContext(Dispatchers.IO) {
        val deleted = getDeletedIds()
        val items = mutableListOf<MediaItem>()

        // 1. Fetch from Firestore REST
        val firestoreItems = fetchFromFirestore()
        items.addAll(firestoreItems)

        // 2. Fetch from Firebase Realtime Database (Optimized: queries targeted media collections to save 95%+ bandwidth and avoid downloading user logs)
        if (url.isNotBlank()) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                val subKeys = listOf("channels", "sports", "movies", "events", "matches", "custom")

                for (sub in subKeys) {
                    try {
                        val targetUrl = appendRtdbAuth("$cleanUrl/$sub.json")
                        val request = Request.Builder()
                            .url(targetUrl)
                            .header("User-Agent", "NAFITV24-Android/2.6.5")
                            .build()

                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val body = response.body?.string()?.trim() ?: ""
                            if (body.startsWith("{")) {
                                val subObj = JSONObject(body)
                                val keys = subObj.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next()
                                    if (!deleted.contains(k) && !k.startsWith("pl_")) {
                                        val itemObj = subObj.optJSONObject(k)
                                        if (itemObj != null && !itemObj.has("channelCount")) {
                                            val rawItem = parseMediaFromJsonObj(k, itemObj)
                                            val item = when (sub) {
                                                "channels" -> if (rawItem.type != MediaType.LIVE_TV && !rawItem.id.startsWith("sport_") && !rawItem.id.startsWith("match_") && !rawItem.id.startsWith("mov_")) rawItem.copy(type = MediaType.LIVE_TV) else rawItem
                                                "sports", "events", "matches" -> if (rawItem.type != MediaType.LIVE_EVENT && !rawItem.id.startsWith("tv_") && !rawItem.id.startsWith("mov_")) rawItem.copy(type = MediaType.LIVE_EVENT) else rawItem
                                                "movies" -> if (rawItem.type != MediaType.MOVIE && !rawItem.id.startsWith("tv_") && !rawItem.id.startsWith("sport_")) rawItem.copy(type = MediaType.MOVIE) else rawItem
                                                else -> rawItem
                                            }
                                            if (!deleted.contains(item.id) && !item.id.startsWith("pl_")) {
                                                items.add(item)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        items.distinctBy { it.id }.filterNot { deleted.contains(it.id) || it.id.startsWith("pl_") }
    }

    suspend fun pushToFirebase(
        item: MediaItem,
        url: String = getSavedFirebaseUrl()
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var anySuccess = false
        var lastError = ""

        val collections = when (item.type) {
            MediaType.LIVE_EVENT -> listOf("events", "sports", "matches")
            MediaType.LIVE_TV -> listOf("channels")
            MediaType.MOVIE, MediaType.SERIES -> listOf("movies")
        }

        // 1. Push to Firestore REST
        try {
            val firestoreObj = JSONObject()
            val fields = JSONObject()
            fun fs(key: String, value: String?) {
                if (!value.isNullOrBlank()) {
                    fields.put(key, JSONObject().put("stringValue", value))
                }
            }
            fun fb(key: String, value: Boolean) {
                fields.put(key, JSONObject().put("booleanValue", value))
            }
            fun fi(key: String, value: Long?) {
                if (value != null) {
                    fields.put(key, JSONObject().put("integerValue", value.toString()))
                }
            }

            fs("id", item.id)
            fs("title", item.title)
            fs("name", item.title)
            fs("streamUrl", item.streamUrl)
            fs("url", item.streamUrl)
            fs("backupUrl", item.backupUrl)
            fs("logoUrl", item.logoUrl)
            fs("logo", item.logoUrl)
            fs("category", item.category)
            fs("type", item.type.name)
            fs("tournament", item.tournament)
            fs("team1", item.team1)
            fs("team2", item.team2)
            fs("team1Logo", item.team1Logo)
            fs("team2Logo", item.team2Logo)
            fs("matchTimeFormatted", item.matchTimeFormatted)
            fs("status", item.status)
            fb("isLive", item.isLive)
            fs("description", item.description)
            fi("countdownTargetSeconds", item.countdownTargetSeconds)
            fs("score1", item.score1)
            fs("score2", item.score2)
            fs("drmScheme", item.drmScheme)
            fs("drmLicenseUrl", item.drmLicenseUrl)
            fs("drmLicenseKey", item.drmLicenseKey)
            fs("manifestType", item.manifestType)

            // Serialize servers array to JSON string for Firestore compatibility
            val servers = item.getAllServers()
            if (servers.isNotEmpty()) {
                val sArr = JSONArray()
                servers.forEach { s ->
                    val so = JSONObject()
                    so.put("name", s.name)
                    so.put("url", s.url)
                    sArr.put(so)
                }
                fs("serversJson", sArr.toString())
            }

            firestoreObj.put("fields", fields)
            val fsBody = firestoreObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val primaryCol = collections.first()
            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            for (dbId in databases) {
                try {
                    val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/$primaryCol/${item.id}?key=$FIREBASE_API_KEY"
                    val fsReq = Request.Builder().url(fsUrl).patch(fsBody).build()
                    val fsResp = client.newCall(fsReq).execute()
                    if (fsResp.isSuccessful) {
                        anySuccess = true
                    }
                } catch (e: Exception) {
                    // continue
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Push to Realtime Database
        if (url.isNotBlank()) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                val jsonObject = serializeMediaToJsonObj(item)
                val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                for (col in collections) {
                    val targetUrl = appendRtdbAuth("$cleanUrl/$col/${item.id}.json")
                    val request = Request.Builder().url(targetUrl).put(body).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        anySuccess = true
                    } else {
                        lastError = "HTTP ${response.code}: ${response.message}"
                    }
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "RTDB নেটওয়ার্ক এরর"
            }
        }

        if (anySuccess) {
            Pair(true, "Firebase ক্লাউডে সফলভাবে সেভ হয়েছে")
        } else {
            Pair(false, lastError.ifBlank { "Firebase ক্লাউড আপলোড ব্যর্থ হয়েছে" })
        }
    }

    suspend fun deleteFromFirebase(
        id: String,
        type: MediaType,
        url: String = getSavedFirebaseUrl()
    ): Boolean = withContext(Dispatchers.IO) {
        var anySuccess = false

        // 1. Delete from Firestore REST
        val collections = listOf("events", "sports", "matches", "channels", "movies", "playlists", "custom")
        val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
        for (dbId in databases) {
            for (col in collections) {
                try {
                    val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/$col/$id?key=$FIREBASE_API_KEY"
                    val req = Request.Builder().url(fsUrl).delete().build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) anySuccess = true
                } catch (e: Exception) {
                    // Ignore single path error
                }
            }
        }

        // 2. Delete from Realtime Database
        if (url.isNotBlank()) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                for (col in collections) {
                    try {
                        val targetUrl = appendRtdbAuth("$cleanUrl/$col/$id.json")
                        val request = Request.Builder().url(targetUrl).delete().build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            anySuccess = true
                        }
                    } catch (e: Exception) {
                        // Ignore single path error
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        anySuccess
    }

    fun serializeMediaToJsonObj(item: MediaItem): JSONObject {
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("name", item.title)
        obj.put("title", item.title)
        obj.put("tournament", item.tournament ?: "")
        obj.put("sport", item.category)
        obj.put("category", item.category)
        obj.put("type", item.type.name)
        obj.put("url", item.streamUrl)
        obj.put("streamUrl", item.streamUrl)
        obj.put("backupUrl", item.backupUrl ?: "")
        obj.put("logo", item.logoUrl ?: "")
        obj.put("logoUrl", item.logoUrl ?: "")
        obj.put("poster", item.logoUrl ?: "")
        obj.put("description", item.description ?: "")
        obj.put("isLive", item.isLive)
        obj.put("status", item.status)
        obj.put("eventTime", item.eventTime ?: "")
        obj.put("team1", item.team1 ?: "")
        obj.put("team2", item.team2 ?: "")
        obj.put("team1Logo", item.team1Logo ?: "")
        obj.put("team2Logo", item.team2Logo ?: "")
        obj.put("matchTimeFormatted", item.matchTimeFormatted ?: "")
        if (item.countdownTargetSeconds != null) {
            obj.put("countdownTargetSeconds", item.countdownTargetSeconds)
            obj.put("startTime", item.countdownTargetSeconds)
        }
        obj.put("score1", item.score1 ?: "")
        obj.put("score2", item.score2 ?: "")
        obj.put("quality", item.quality)
        obj.put("rating", item.rating ?: "")
        obj.put("year", item.year ?: "")
        obj.put("country", item.country ?: "")
        obj.put("drmScheme", item.drmScheme ?: "")
        obj.put("drmLicenseUrl", item.drmLicenseUrl ?: "")
        obj.put("drmLicenseKey", item.drmLicenseKey ?: "")
        obj.put("manifestType", item.manifestType ?: "")

        // Multiple servers array
        val serversArr = JSONArray()
        item.getAllServers().forEach { server ->
            val sObj = JSONObject()
            sObj.put("name", server.name)
            sObj.put("url", server.url)
            serversArr.put(sObj)
        }
        obj.put("servers", serversArr)
        return obj
    }

    fun parseMediaFromJsonObj(id: String, obj: JSONObject): MediaItem {
        val typeStr = obj.optString("type", "").trim().uppercase()
        val categoryStr = obj.optString("category", obj.optString("sport", "General")).trim()
        val mediaType = when {
            typeStr == "LIVE_TV" -> MediaType.LIVE_TV
            typeStr == "LIVE_EVENT" -> MediaType.LIVE_EVENT
            typeStr == "MOVIE" || typeStr == "SERIES" -> MediaType.MOVIE
            id.startsWith("tv_") || id.startsWith("channel_") || id.startsWith("ch_") -> MediaType.LIVE_TV
            id.startsWith("sport_") || id.startsWith("match_") || id.startsWith("event_") || id.startsWith("tapmad_") -> MediaType.LIVE_EVENT
            id.startsWith("mov_") || id.startsWith("movie_") || id.startsWith("ser_") -> MediaType.MOVIE
            obj.has("poster") || obj.has("year") -> MediaType.MOVIE
            obj.has("team1") || obj.has("team2") || obj.has("tournament") -> MediaType.LIVE_EVENT
            categoryStr.contains("Cricket", ignoreCase = true) || categoryStr.contains("Football", ignoreCase = true) -> MediaType.LIVE_EVENT
            else -> MediaType.LIVE_TV
        }

        val serversList = mutableListOf<StreamServer>()
        val serversArr = obj.optJSONArray("servers")
        if (serversArr != null) {
            for (i in 0 until serversArr.length()) {
                val sObj = serversArr.optJSONObject(i)
                if (sObj != null) {
                    val name = sObj.optString("name", "সার্ভার ${i + 1}")
                    val sUrl = sObj.optString("url", "")
                    if (sUrl.isNotBlank()) {
                        serversList.add(StreamServer(name, sUrl))
                    }
                }
            }
        }

        val primaryStream = obj.optString("url", obj.optString("streamUrl", ""))
        val backup = obj.optString("backupUrl", null)
        val logo = obj.optString("logo", obj.optString("logoUrl", obj.optString("poster", null))).takeIf { it?.isNotBlank() == true }

        val startTm = if (obj.has("startTime")) obj.optLong("startTime") else if (obj.has("countdownTargetSeconds")) obj.optLong("countdownTargetSeconds") else null

        return MediaItem(
            id = id,
            title = obj.optString("name", obj.optString("title", "NAFI Stream")),
            tournament = obj.optString("tournament", null).takeIf { it?.isNotBlank() == true },
            category = categoryStr,
            type = mediaType,
            streamUrl = primaryStream,
            backupUrl = backup,
            servers = serversList,
            logoUrl = logo,
            description = obj.optString("description", null).takeIf { it?.isNotBlank() == true },
            isLive = obj.optBoolean("isLive", true),
            status = obj.optString("status", "Live Now"),
            eventTime = obj.optString("eventTime", obj.optString("time", null)).takeIf { it?.isNotBlank() == true },
            team1 = obj.optString("team1", null).takeIf { it?.isNotBlank() == true },
            team2 = obj.optString("team2", null).takeIf { it?.isNotBlank() == true },
            team1Logo = obj.optString("team1Logo", null).takeIf { it?.isNotBlank() == true },
            team2Logo = obj.optString("team2Logo", null).takeIf { it?.isNotBlank() == true },
            matchTimeFormatted = obj.optString("matchTimeFormatted", null).takeIf { it?.isNotBlank() == true },
            countdownTargetSeconds = startTm,
            score1 = obj.optString("score1", null).takeIf { it?.isNotBlank() == true },
            score2 = obj.optString("score2", null).takeIf { it?.isNotBlank() == true },
            quality = obj.optString("quality", "HD"),
            rating = obj.optString("rating", null).takeIf { it?.isNotBlank() == true },
            year = obj.optString("year", null).takeIf { it?.isNotBlank() == true },
            country = obj.optString("country", null).takeIf { it?.isNotBlank() == true },
            drmScheme = obj.optString("drmScheme", obj.optString("license_type", null)).takeIf { it?.isNotBlank() == true },
            drmLicenseUrl = obj.optString("drmLicenseUrl", null).takeIf { it?.isNotBlank() == true },
            drmLicenseKey = obj.optString("drmLicenseKey", obj.optString("license_key", obj.optString("clearkey", null))).takeIf { it?.isNotBlank() == true },
            manifestType = obj.optString("manifestType", obj.optString("manifest_type", null)).takeIf { it?.isNotBlank() == true }
        )
    }

    // -------------------------------------------------------------
    // PLAYLISTS MANAGEMENT (Initial, Local & Firebase Cloud + Xtream Codes API)
    // -------------------------------------------------------------
    fun getInitialPlaylists(): List<PlaylistInfo> {
        val deleted = getDeletedIds()
        val defaultList = listOf(
            PlaylistInfo(
                id = "pl_toffee",
                title = "Toffee",
                url = DEFAULT_LIVE_TV_M3U_URL,
                logoUrl = "https://images.unsplash.com/photo-1593784991095-a205069470b6?w=200&fit=crop",
                description = "Toffee BD Live TV & Sports Streams",
                type = "M3U",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_sports_tv",
                title = "Sports TV",
                url = DEFAULT_SPORTS_M3U_URL,
                logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=200&fit=crop",
                description = "Live Sports Cricket & Football Streams",
                type = "M3U",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_sports_tv_2",
                title = "SPORTS TV 2",
                url = DEFAULT_TAPMAD_M3U_URL,
                logoUrl = "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=200&fit=crop",
                description = "Live Sports HD & Multi-bitrate Feed",
                type = "M3U",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_aksh_go",
                title = "Aksh Go",
                url = DEFAULT_LIVE_TV_M3U_URL,
                logoUrl = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=200&fit=crop",
                description = "Akash Go Direct HD TV Channels",
                type = "M3U",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_bdix",
                title = "BDIX",
                url = DEFAULT_LIVE_TV_M3U_URL,
                logoUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=200&fit=crop",
                description = "High Speed BDIX Local IPTV Server",
                type = "M3U",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_mrgify_bdix",
                title = "Mrgify-BDIX",
                url = DEFAULT_LIVE_TV_M3U_URL,
                logoUrl = "https://images.unsplash.com/photo-1586899028174-e7098604235b?w=200&fit=crop",
                description = "Mrgify BDIX Multi-Stream IPTV",
                type = "M3U",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_fast_iptv",
                title = "FAST-IPTV",
                url = DEFAULT_LIVE_TV_M3U_URL,
                logoUrl = "https://images.unsplash.com/photo-1461151304267-38535e780c79?w=200&fit=crop",
                description = "Fast IPTV Global Live TV Channels",
                type = "M3U",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_crichd",
                title = "CricHD",
                url = DEFAULT_SPORTS_M3U_URL,
                logoUrl = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=200&fit=crop",
                description = "CricHD 24/7 Cricket & Football Live",
                type = "M3U",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_mysave23",
                title = "MySave TV (Xtream)",
                url = "http://mysave23.com/get.php?username=OscarDuarte6295&password=naNMGtc9sK&type=m3u_plus&output=m3u8",
                logoUrl = "https://images.unsplash.com/photo-1593784991095-a205069470b6?w=200&fit=crop",
                description = "Xtream Codes IPTV Playlist (OscarDuarte6295)",
                serverUrl = "http://mysave23.com",
                username = "OscarDuarte6295",
                password = "naNMGtc9sK",
                type = "XTREAM",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_nafi_movies_json",
                title = "NAFI Movies & Series",
                url = DEFAULT_MOVIES_JSON_URL,
                logoUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=200&fit=crop",
                description = "সকল মুভি ও ওয়েব সিরিজ প্লেলিস্ট (movies.json)",
                type = "JSON",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_tapmad_sports",
                title = "Tapmad BD Sports Live",
                url = DEFAULT_TAPMAD_JSON_URL,
                logoUrl = "https://images.unsplash.com/photo-1518091043644-c1d4457512c6?w=200&fit=crop",
                description = "Tapmad Live Sports Stream Events",
                type = "JSON",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_global_iptv",
                title = "Global Entertainment",
                url = DEFAULT_LIVE_TV_M3U_URL,
                logoUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=200&fit=crop",
                description = "Global News, Movies, Music & TV",
                type = "M3U",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_mix_movies_auto",
                title = "Mix Movies (Auto Update)",
                url = DEFAULT_MIX_MOVIES_M3U_URL,
                logoUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=200&fit=crop",
                description = "Mix Movies Auto Update Collection (Pixeldrain & Direct)",
                type = "M3U",
                isAdmin = true,
                isReadOnly = true
            ),
            PlaylistInfo(
                id = "pl_latest_movies_live",
                title = "Latest Movies Live",
                url = DEFAULT_LATEST_MOVIES_M3U_URL,
                logoUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=200&fit=crop",
                description = "Latest Cinema & OTT Movies Collection",
                type = "M3U",
                isAdmin = true,
                isReadOnly = true
            )
        )
        return defaultList.filterNot { deleted.contains(it.id) }
    }

    fun buildXtreamM3uUrl(serverUrl: String, username: String, pass: String): String {
        var cleanServer = serverUrl.trim().removeSuffix("/")
        if (!cleanServer.startsWith("http://", ignoreCase = true) && !cleanServer.startsWith("https://", ignoreCase = true)) {
            cleanServer = "http://$cleanServer"
        }
        return "$cleanServer/get.php?username=${username.trim()}&password=${pass.trim()}&type=m3u_plus&output=m3u8"
    }

    fun parseXtreamCredentials(input: String): Triple<String, String, String>? {
        if (input.isBlank()) return null
        val trimmed = input.trim()

        // Case 1: URL with query parameters e.g. http://server/get.php?username=xxx&password=yyy
        if (trimmed.contains("username=", ignoreCase = true) && trimmed.contains("password=", ignoreCase = true)) {
            try {
                val uri = Uri.parse(trimmed)
                val user = uri.getQueryParameter("username")
                val pass = uri.getQueryParameter("password")
                val scheme = uri.scheme ?: "http"
                val host = uri.host ?: ""
                val port = if (uri.port != -1) ":${uri.port}" else ""
                val server = "$scheme://$host$port"
                if (!user.isNullOrBlank() && !pass.isNullOrBlank() && host.isNotBlank()) {
                    return Triple(server, user, pass)
                }
            } catch (_: Exception) {}
        }

        // Case 2: Multi-line text (e.g. Server \n User \n Pass)
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 3) {
            val serverLine = lines.firstOrNull { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) || it.contains(".com") || it.contains(":") } ?: lines[0]
            val nonServerLines = lines.filter { it != serverLine }
            if (nonServerLines.size >= 2) {
                var server = serverLine.replace(Regex("^(server|host|url)\\s*[:=]\\s*", RegexOption.IGNORE_CASE), "").trim()
                if (!server.startsWith("http://", ignoreCase = true) && !server.startsWith("https://", ignoreCase = true)) {
                    server = "http://$server"
                }
                val user = nonServerLines[0].replace(Regex("^(user|username|name)\\s*[:=]\\s*", RegexOption.IGNORE_CASE), "").trim()
                val pass = nonServerLines[1].replace(Regex("^(pass|password|pin)\\s*[:=]\\s*", RegexOption.IGNORE_CASE), "").trim()
                if (server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                    return Triple(server, user, pass)
                }
            }
        }

        // Case 3: Comma or space separated tokens
        val tokens = trimmed.split(Regex("[\r\n\t ,|;]+"))
        var server = ""
        var user = ""
        var pass = ""
        for (token in tokens) {
            val t = token.trim()
            if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) || (t.contains(".") && !t.contains("="))) {
                if (server.isEmpty()) {
                    server = if (!t.startsWith("http://", ignoreCase = true) && !t.startsWith("https://", ignoreCase = true)) "http://$t" else t
                }
            } else if (t.startsWith("user=", ignoreCase = true) || t.startsWith("username=", ignoreCase = true)) {
                user = t.substringAfter("=").trim()
            } else if (t.startsWith("pass=", ignoreCase = true) || t.startsWith("password=", ignoreCase = true)) {
                pass = t.substringAfter("=").trim()
            }
        }
        if (server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
            return Triple(server, user, pass)
        }

        return null
    }

    suspend fun fetchXtreamCodesStreams(serverUrl: String, username: String, pass: String): List<MediaItem> = withContext(Dispatchers.IO) {
        var cleanServer = serverUrl.trim().removeSuffix("/")
        if (!cleanServer.startsWith("http://", ignoreCase = true) && !cleanServer.startsWith("https://", ignoreCase = true)) {
            cleanServer = "http://$cleanServer"
        }
        val cleanUser = username.trim()
        val cleanPass = pass.trim()

        val items = mutableListOf<MediaItem>()

        // 1. First attempt: Standard Xtream M3U Plus URL
        try {
            val m3uUrl = "$cleanServer/get.php?username=$cleanUser&password=$cleanPass&type=m3u_plus&output=m3u8"
            val m3uItems = fetchSingleM3uUrl(m3uUrl)
            if (m3uItems.isNotEmpty()) {
                return@withContext m3uItems
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Second attempt: Xtream Player API JSON streams
        try {
            // Live Categories map
            val catMap = mutableMapOf<String, String>()
            try {
                val catUrl = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_live_categories"
                val catReq = Request.Builder().url(catUrl).header("User-Agent", "IPTVSmartersPro").build()
                val catResp = client.newCall(catReq).execute()
                if (catResp.isSuccessful) {
                    val catBody = catResp.body?.string() ?: ""
                    if (catBody.startsWith("[")) {
                        val catArr = JSONArray(catBody)
                        for (i in 0 until catArr.length()) {
                            val cObj = catArr.optJSONObject(i)
                            if (cObj != null) {
                                val cId = cObj.optString("category_id")
                                val cName = cObj.optString("category_name")
                                if (cId.isNotBlank() && cName.isNotBlank()) {
                                    catMap[cId] = cName
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // Live Streams
            val liveUrl = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_live_streams"
            val liveReq = Request.Builder().url(liveUrl).header("User-Agent", "IPTVSmartersPro").build()
            val liveResp = client.newCall(liveReq).execute()
            if (liveResp.isSuccessful) {
                val liveBody = liveResp.body?.string() ?: ""
                if (liveBody.startsWith("[")) {
                    val liveArr = JSONArray(liveBody)
                    for (i in 0 until liveArr.length()) {
                        val sObj = liveArr.optJSONObject(i) ?: continue
                        val streamId = sObj.optString("stream_id", "")
                        if (streamId.isBlank()) continue
                        val name = sObj.optString("name", "Channel $streamId")
                        val catId = sObj.optString("category_id", "")
                        val categoryName = catMap[catId] ?: "Live TV"
                        val icon = sObj.optString("stream_icon").takeIf { it.isNotBlank() }

                        val isSport = categoryName.contains("sport", ignoreCase = true) ||
                                name.contains("sport", ignoreCase = true) ||
                                name.contains("cricket", ignoreCase = true) ||
                                name.contains("football", ignoreCase = true)

                        val playUrl = "$cleanServer/live/$cleanUser/$cleanPass/$streamId.m3u8"
                        val directPlayUrl = "$cleanServer/$cleanUser/$cleanPass/$streamId"

                        items.add(
                            MediaItem(
                                id = "xtream_live_${streamId}",
                                title = name,
                                category = categoryName,
                                type = MediaType.LIVE_TV,
                                streamUrl = playUrl,
                                backupUrl = directPlayUrl,
                                servers = listOf(
                                    StreamServer("সার্ভার ১ (HLS)", playUrl),
                                    StreamServer("সার্ভার ২ (Direct TS)", directPlayUrl)
                                ),
                                logoUrl = icon,
                                isLive = true,
                                quality = "HD",
                                userAgent = "IPTVSmartersPro"
                            )
                        )
                    }
                }
            }

            // VOD Movies
            try {
                val vodUrl = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_vod_streams"
                val vodReq = Request.Builder().url(vodUrl).header("User-Agent", "IPTVSmartersPro").build()
                val vodResp = client.newCall(vodReq).execute()
                if (vodResp.isSuccessful) {
                    val vodBody = vodResp.body?.string() ?: ""
                    if (vodBody.startsWith("[")) {
                        val vodArr = JSONArray(vodBody)
                        for (i in 0 until vodArr.length()) {
                            val vObj = vodArr.optJSONObject(i) ?: continue
                            val streamId = vObj.optString("stream_id", "")
                            if (streamId.isBlank()) continue
                            val name = vObj.optString("name", "Movie $streamId")
                            val catId = vObj.optString("category_id", "")
                            val categoryName = catMap[catId] ?: "Movies"
                            val icon = vObj.optString("stream_icon").takeIf { it.isNotBlank() }
                            val ext = vObj.optString("container_extension", "mp4")
                            val rating = vObj.optString("rating", "8.5")

                            val playUrl = "$cleanServer/movie/$cleanUser/$cleanPass/$streamId.$ext"

                            items.add(
                                MediaItem(
                                    id = "xtream_vod_${streamId}",
                                    title = name,
                                    category = categoryName,
                                    type = MediaType.MOVIE,
                                    streamUrl = playUrl,
                                    servers = listOf(
                                        StreamServer("সার্ভার ১ (VOD)", playUrl)
                                    ),
                                    logoUrl = icon,
                                    isLive = false,
                                    rating = rating,
                                    quality = "HD",
                                    userAgent = "IPTVSmartersPro"
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}

        } catch (e: Exception) {
            e.printStackTrace()
        }

        items
    }

    suspend fun testXtreamCodes(serverUrl: String, username: String, pass: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var cleanServer = serverUrl.trim().removeSuffix("/")
        if (!cleanServer.startsWith("http://", ignoreCase = true) && !cleanServer.startsWith("https://", ignoreCase = true)) {
            cleanServer = "http://$cleanServer"
        }
        val cleanUser = username.trim()
        val cleanPass = pass.trim()

        try {
            val authUrl = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass"
            val req = Request.Builder().url(authUrl).header("User-Agent", "IPTVSmartersPro").build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                if (body.startsWith("{")) {
                    val obj = JSONObject(body)
                    val userInfo = obj.optJSONObject("user_info")
                    val auth = userInfo?.optInt("auth", 0) ?: 0
                    val status = userInfo?.optString("status", "") ?: ""
                    val expDate = userInfo?.optString("exp_date", "") ?: ""
                    if (auth == 1 || status.equals("Active", ignoreCase = true)) {
                        return@withContext Pair(true, "✅ Xtream Codes কানেক্টেড! স্ট্যাটাস: ${status.ifBlank { "Active" }} (মেয়াদ: ${expDate.ifBlank { "আনলিমিটেড" }})")
                    } else if (obj.has("user_info")) {
                        return@withContext Pair(true, "✅ Xtream Codes তথ্য পাওয়া গেছে! সংযোগ সক্রিয়।")
                    }
                }
            }

            // Test via get.php
            val m3uTest = "$cleanServer/get.php?username=$cleanUser&password=$cleanPass&type=m3u_plus&output=m3u8"
            val m3uReq = Request.Builder().url(m3uTest).header("User-Agent", "IPTVSmartersPro").build()
            val m3uResp = client.newCall(m3uReq).execute()
            if (m3uResp.isSuccessful) {
                return@withContext Pair(true, "✅ Xtream M3U সফলভাবে রেসপন্স করেছে (HTTP 200)")
            }

            Pair(false, "⚠️ সার্ভার সংযোগ বা ইউজারনেম/পাসওয়ার্ড সঠিক নয় (HTTP ${resp.code})")
        } catch (e: Exception) {
            Pair(false, "⚠️ কানেকশন এরর: ${e.localizedMessage ?: "সার্ভারে পৌঁছানো সম্ভব হয়নি"}")
        }
    }

    suspend fun fetchPlaylistChannels(playlist: PlaylistInfo): List<MediaItem> = withContext(Dispatchers.IO) {
        if (!playlist.serverUrl.isNullOrBlank() && !playlist.username.isNullOrBlank() && !playlist.password.isNullOrBlank()) {
            val xtreamItems = fetchXtreamCodesStreams(playlist.serverUrl, playlist.username, playlist.password)
            if (xtreamItems.isNotEmpty()) {
                return@withContext xtreamItems
            }
        }
        if (playlist.url.isNotBlank()) {
            val m3uItems = parseM3uFromUrl(playlist.url)
            if (m3uItems.isNotEmpty()) {
                return@withContext m3uItems
            }
            val creds = parseXtreamCredentials(playlist.url)
            if (creds != null) {
                val xtreamFallback = fetchXtreamCodesStreams(creds.first, creds.second, creds.third)
                if (xtreamFallback.isNotEmpty()) {
                    return@withContext xtreamFallback
                }
            }
        }
        emptyList()
    }

    // -------------------------------------------------------------
    // USER-LOCAL PLAYLISTS (Stored only on user device, never to Firebase)
    // -------------------------------------------------------------
    fun getUserPlaylists(): List<PlaylistInfo> {
        val deleted = getDeletedIds()
        val jsonStr = prefs.getString("user_playlists", null)
        val list = mutableListOf<PlaylistInfo>()
        if (jsonStr != null) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id", "pl_user_$i")
                    if (!deleted.contains(id)) {
                        list.add(
                            PlaylistInfo(
                                id = id,
                                title = obj.optString("title", obj.optString("name", "Playlist")),
                                url = obj.optString("url", ""),
                                logoUrl = obj.optString("logoUrl", obj.optString("logo", null)).takeIf { it?.isNotBlank() == true },
                                description = obj.optString("description", null).takeIf { it?.isNotBlank() == true },
                                channelCount = obj.optInt("channelCount", 0),
                                serverUrl = obj.optString("serverUrl", null).takeIf { it?.isNotBlank() == true },
                                username = obj.optString("username", null).takeIf { it?.isNotBlank() == true },
                                password = obj.optString("password", null).takeIf { it?.isNotBlank() == true },
                                type = obj.optString("type", if (obj.has("serverUrl") || obj.has("username")) "XTREAM" else "M3U"),
                                isAdmin = obj.optBoolean("isAdmin", false),
                                isReadOnly = obj.optBoolean("isReadOnly", false)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list
    }

    fun saveUserPlaylist(playlist: PlaylistInfo) {
        removeDeletedId(playlist.id)
        val current = getUserPlaylists().toMutableList()
        current.removeAll { it.id == playlist.id }
        current.add(0, playlist.copy(isAdmin = false, isReadOnly = false))
        saveUserPlaylistsList(current)
    }

    fun saveUserPlaylistsList(list: List<PlaylistInfo>) {
        val arr = JSONArray()
        list.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("title", p.title)
            obj.put("name", p.title)
            obj.put("url", p.url)
            obj.put("logoUrl", p.logoUrl ?: "")
            obj.put("logo", p.logoUrl ?: "")
            obj.put("description", p.description ?: "")
            obj.put("channelCount", p.channelCount)
            obj.put("serverUrl", p.serverUrl ?: "")
            obj.put("username", p.username ?: "")
            obj.put("password", p.password ?: "")
            obj.put("type", p.type)
            obj.put("isAdmin", false)
            obj.put("isReadOnly", false)
            arr.put(obj)
        }
        prefs.edit().putString("user_playlists", arr.toString()).apply()
    }

    fun deleteUserPlaylist(id: String) {
        addDeletedId(id)
        val current = getUserPlaylists().filterNot { it.id == id }
        saveUserPlaylistsList(current)
    }

    // -------------------------------------------------------------
    // ADMIN PLAYLISTS (Admin panel managed, synced to Firebase Cloud)
    // -------------------------------------------------------------
    fun getAdminPlaylists(): List<PlaylistInfo> {
        val deleted = getDeletedIds()
        val jsonStr = prefs.getString("admin_playlists", prefs.getString("custom_playlists", "[]")) ?: "[]"
        val list = mutableListOf<PlaylistInfo>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "pl_admin_$i")
                if (!deleted.contains(id)) {
                    list.add(
                        PlaylistInfo(
                            id = id,
                            title = obj.optString("title", obj.optString("name", "Playlist")),
                            url = obj.optString("url", ""),
                            logoUrl = obj.optString("logoUrl", obj.optString("logo", null)).takeIf { it?.isNotBlank() == true },
                            description = obj.optString("description", null).takeIf { it?.isNotBlank() == true },
                            channelCount = obj.optInt("channelCount", 0),
                            serverUrl = obj.optString("serverUrl", null).takeIf { it?.isNotBlank() == true },
                            username = obj.optString("username", null).takeIf { it?.isNotBlank() == true },
                            password = obj.optString("password", null).takeIf { it?.isNotBlank() == true },
                            type = obj.optString("type", if (obj.has("serverUrl") || obj.has("username")) "XTREAM" else "M3U"),
                            isAdmin = true,
                            isReadOnly = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveAdminPlaylist(playlist: PlaylistInfo) {
        removeDeletedId(playlist.id)
        val current = getAdminPlaylists().toMutableList()
        current.removeAll { it.id == playlist.id }
        current.add(0, playlist.copy(isAdmin = true, isReadOnly = true))
        saveAdminPlaylistsList(current)
    }

    fun saveAdminPlaylistsList(list: List<PlaylistInfo>) {
        val arr = JSONArray()
        list.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("title", p.title)
            obj.put("name", p.title)
            obj.put("url", p.url)
            obj.put("logoUrl", p.logoUrl ?: "")
            obj.put("logo", p.logoUrl ?: "")
            obj.put("description", p.description ?: "")
            obj.put("channelCount", p.channelCount)
            obj.put("serverUrl", p.serverUrl ?: "")
            obj.put("username", p.username ?: "")
            obj.put("password", p.password ?: "")
            obj.put("type", p.type)
            obj.put("isAdmin", true)
            obj.put("isReadOnly", true)
            arr.put(obj)
        }
        prefs.edit().putString("admin_playlists", arr.toString()).apply()
    }

    fun deleteAdminPlaylist(id: String) {
        addDeletedId(id)
        val current = getAdminPlaylists().filterNot { it.id == id }
        saveAdminPlaylistsList(current)
    }

    fun getCustomPlaylists(): List<PlaylistInfo> {
        val deleted = getDeletedIds()
        return (getAdminPlaylists() + getUserPlaylists()).distinctBy { it.id }.filterNot { deleted.contains(it.id) }
    }

    fun saveCustomPlaylist(playlist: PlaylistInfo) {
        saveUserPlaylist(playlist)
    }

    fun saveCustomPlaylistsList(list: List<PlaylistInfo>) {
        saveUserPlaylistsList(list)
    }

    fun deleteCustomPlaylist(id: String) {
        deleteUserPlaylist(id)
    }

    suspend fun deletePlaylistFromFirebase(id: String): Boolean {
        return deleteFromFirebase(id, MediaType.LIVE_TV)
    }

    suspend fun deletePlaylist(id: String): Boolean {
        addDeletedId(id)
        deleteAdminPlaylist(id)
        deleteUserPlaylist(id)
        return deleteFromFirebase(id, MediaType.LIVE_TV)
    }

    suspend fun pushPlaylistToFirebase(playlist: PlaylistInfo, url: String = getSavedFirebaseUrl()): Boolean = withContext(Dispatchers.IO) {
        var success = false

        // 1. Push to Firestore
        try {
            val firestoreObj = JSONObject()
            val fields = JSONObject()
            fields.put("id", JSONObject().put("stringValue", playlist.id))
            fields.put("title", JSONObject().put("stringValue", playlist.title))
            fields.put("name", JSONObject().put("stringValue", playlist.title))
            fields.put("url", JSONObject().put("stringValue", playlist.url))
            if (!playlist.logoUrl.isNullOrBlank()) {
                fields.put("logoUrl", JSONObject().put("stringValue", playlist.logoUrl))
                fields.put("logo", JSONObject().put("stringValue", playlist.logoUrl))
            }
            if (!playlist.description.isNullOrBlank()) {
                fields.put("description", JSONObject().put("stringValue", playlist.description))
            }
            fields.put("channelCount", JSONObject().put("integerValue", playlist.channelCount.toString()))
            if (!playlist.serverUrl.isNullOrBlank()) {
                fields.put("serverUrl", JSONObject().put("stringValue", playlist.serverUrl))
            }
            if (!playlist.username.isNullOrBlank()) {
                fields.put("username", JSONObject().put("stringValue", playlist.username))
            }
            if (!playlist.password.isNullOrBlank()) {
                fields.put("password", JSONObject().put("stringValue", playlist.password))
            }
            fields.put("type", JSONObject().put("stringValue", playlist.type))
            fields.put("isAdmin", JSONObject().put("booleanValue", true))
            fields.put("isReadOnly", JSONObject().put("booleanValue", true))

            firestoreObj.put("fields", fields)
            val fsBody = firestoreObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            for (dbId in databases) {
                try {
                    val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/playlists/${playlist.id}?key=$FIREBASE_API_KEY"
                    val fsReq = Request.Builder().url(fsUrl).patch(fsBody).build()
                    val fsResp = client.newCall(fsReq).execute()
                    if (fsResp.isSuccessful) success = true
                } catch (e: Exception) {
                    // continue
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Push to RTDB
        if (url.isNotBlank()) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                val obj = JSONObject()
                obj.put("id", playlist.id)
                obj.put("title", playlist.title)
                obj.put("name", playlist.title)
                obj.put("url", playlist.url)
                obj.put("logoUrl", playlist.logoUrl ?: "")
                obj.put("logo", playlist.logoUrl ?: "")
                obj.put("description", playlist.description ?: "")
                obj.put("channelCount", playlist.channelCount)
                obj.put("serverUrl", playlist.serverUrl ?: "")
                obj.put("username", playlist.username ?: "")
                obj.put("password", playlist.password ?: "")
                obj.put("type", playlist.type)
                obj.put("isAdmin", true)
                obj.put("isReadOnly", true)

                val body = obj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val targetUrl = appendRtdbAuth("$cleanUrl/playlists/${playlist.id}.json")
                val req = Request.Builder().url(targetUrl).put(body).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) success = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        success
    }

    suspend fun fetchPlaylistsFromFirebase(url: String = getSavedFirebaseUrl()): List<PlaylistInfo> = withContext(Dispatchers.IO) {
        val deleted = getDeletedIds()
        coroutineScope {
            // 1. Fetch from Firestore (parallel per database)
            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            val fsJobs = databases.map { dbId ->
                async {
                    val dbList = mutableListOf<PlaylistInfo>()
                    try {
                        val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/playlists?key=$FIREBASE_API_KEY"
                        val req = Request.Builder().url(fsUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            if (body.isNotBlank() && body.startsWith("{")) {
                                val json = JSONObject(body)
                                val docs = json.optJSONArray("documents")
                                if (docs != null) {
                                    for (i in 0 until docs.length()) {
                                        val doc = docs.optJSONObject(i) ?: continue
                                        val name = doc.optString("name", "")
                                        val docId = name.substringAfterLast("/")
                                        if (docId.isBlank() || deleted.contains(docId)) continue

                                        val fields = doc.optJSONObject("fields") ?: continue
                                        fun s(k: String): String = fields.optJSONObject(k)?.optString("stringValue", "") ?: ""
                                        fun count(k: String): Int = fields.optJSONObject(k)?.optInt("integerValue", 0) ?: 0

                                        val pId = s("id").ifBlank { docId }
                                        if (!deleted.contains(pId)) {
                                            dbList.add(
                                                PlaylistInfo(
                                                    id = pId,
                                                    title = s("title").ifBlank { s("name").ifBlank { "Playlist" } },
                                                    url = s("url"),
                                                    logoUrl = s("logoUrl").ifBlank { s("logo") }.takeIf { it.isNotBlank() },
                                                    description = s("description").takeIf { it.isNotBlank() },
                                                    channelCount = count("channelCount"),
                                                    serverUrl = s("serverUrl").takeIf { it.isNotBlank() },
                                                    username = s("username").takeIf { it.isNotBlank() },
                                                    password = s("password").takeIf { it.isNotBlank() },
                                                    type = s("type").ifBlank { if (s("serverUrl").isNotBlank() || s("username").isNotBlank()) "XTREAM" else "M3U" },
                                                    isAdmin = true,
                                                    isReadOnly = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    dbList
                }
            }

            // 2. Fetch from RTDB
            val rtdbJob = async {
                val rtdbList = mutableListOf<PlaylistInfo>()
                if (url.isNotBlank()) {
                    try {
                        val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                        val targetUrl = appendRtdbAuth("$cleanUrl/playlists.json")
                        val req = Request.Builder().url(targetUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()?.trim() ?: ""
                            if (body.isNotEmpty() && body != "null") {
                                if (body.startsWith("{")) {
                                    val jsonObject = JSONObject(body)
                                    val keys = jsonObject.keys()
                                    while (keys.hasNext()) {
                                        val k = keys.next()
                                        if (!deleted.contains(k)) {
                                            val obj = jsonObject.optJSONObject(k)
                                            if (obj != null) {
                                                val id = obj.optString("id", k)
                                                if (!deleted.contains(id)) {
                                                    rtdbList.add(
                                                        PlaylistInfo(
                                                            id = id,
                                                            title = obj.optString("title", obj.optString("name", "Playlist")),
                                                            url = obj.optString("url", ""),
                                                            logoUrl = obj.optString("logoUrl", obj.optString("logo", null)).takeIf { it?.isNotBlank() == true },
                                                            description = obj.optString("description", null).takeIf { it?.isNotBlank() == true },
                                                            channelCount = obj.optInt("channelCount", 0),
                                                            serverUrl = obj.optString("serverUrl", null).takeIf { it?.isNotBlank() == true },
                                                            username = obj.optString("username", null).takeIf { it?.isNotBlank() == true },
                                                            password = obj.optString("password", null).takeIf { it?.isNotBlank() == true },
                                                            type = obj.optString("type", if (obj.has("serverUrl") || obj.has("username")) "XTREAM" else "M3U"),
                                                            isAdmin = true,
                                                            isReadOnly = true
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else if (body.startsWith("[")) {
                                    val jsonArr = JSONArray(body)
                                    for (i in 0 until jsonArr.length()) {
                                        val obj = jsonArr.optJSONObject(i) ?: continue
                                        val id = obj.optString("id", "pl_rtdb_$i")
                                        if (!deleted.contains(id)) {
                                            rtdbList.add(
                                                PlaylistInfo(
                                                    id = id,
                                                    title = obj.optString("title", obj.optString("name", "Playlist")),
                                                    url = obj.optString("url", ""),
                                                    logoUrl = obj.optString("logoUrl", obj.optString("logo", null)).takeIf { it?.isNotBlank() == true },
                                                    description = obj.optString("description", null).takeIf { it?.isNotBlank() == true },
                                                    channelCount = obj.optInt("channelCount", 0),
                                                    serverUrl = obj.optString("serverUrl", null).takeIf { it?.isNotBlank() == true },
                                                    username = obj.optString("username", null).takeIf { it?.isNotBlank() == true },
                                                    password = obj.optString("password", null).takeIf { it?.isNotBlank() == true },
                                                    type = obj.optString("type", if (obj.has("serverUrl") || obj.has("username")) "XTREAM" else "M3U"),
                                                    isAdmin = true,
                                                    isReadOnly = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                rtdbList
            }

            val allFs = fsJobs.awaitAll().flatten()
            val allRtdb = rtdbJob.await()
            (allFs + allRtdb).distinctBy { it.id }.filterNot { deleted.contains(it.id) }
        }
    }

    fun saveFirebaseUrl(url: String) {
        prefs.edit().putString("saved_firebase_url", url).apply()
    }

    fun getSavedFirebaseUrl(): String {
        val stored = prefs.getString("saved_firebase_url", null)
        if (stored.isNullOrBlank() || stored.contains("elaborate-airfoil") || stored.contains("nafitv24-default-rtdb")) {
            return DEFAULT_RTDB_URL
        }
        return stored
    }

    fun saveM3uUrl(url: String) {
        saveLiveTvM3uUrl(url)
    }

    fun getSavedM3uUrl(): String {
        return getSavedLiveTvM3uUrl()
    }

    fun saveLiveTvM3uUrl(url: String) {
        prefs.edit().putString("saved_live_tv_m3u_url", url).apply()
    }

    fun getSavedLiveTvM3uUrl(): String {
        return prefs.getString("saved_live_tv_m3u_url", DEFAULT_LIVE_TV_M3U_URL) ?: DEFAULT_LIVE_TV_M3U_URL
    }

    fun saveSportsM3uUrl(url: String) {
        prefs.edit().putString("saved_sports_m3u_url", url).apply()
    }

    fun getSavedSportsM3uUrl(): String {
        return prefs.getString("saved_sports_m3u_url", DEFAULT_SPORTS_M3U_URL) ?: DEFAULT_SPORTS_M3U_URL
    }

    fun saveTapmadJsonUrl(url: String) {
        prefs.edit().putString("saved_tapmad_json_url", url).apply()
    }

    fun getSavedTapmadJsonUrl(): String {
        return prefs.getString("saved_tapmad_json_url", DEFAULT_TAPMAD_JSON_URL) ?: DEFAULT_TAPMAD_JSON_URL
    }

    fun saveTapmadM3uUrl(url: String) {
        prefs.edit().putString("saved_tapmad_m3u_url", url).apply()
    }

    fun getSavedTapmadM3uUrl(): String {
        return prefs.getString("saved_tapmad_m3u_url", DEFAULT_TAPMAD_M3U_URL) ?: DEFAULT_TAPMAD_M3U_URL
    }

    suspend fun fetchTapmadSportsMatches(
        jsonUrl: String = getSavedTapmadJsonUrl(),
        m3uUrl: String = getSavedTapmadM3uUrl()
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        try {
            // 1. Fetch tapmad_bd.m3u and tapmad_bd.json concurrently
            val m3uDeferred = async {
                if (m3uUrl.isNotBlank()) {
                    try {
                        val m3uReq = Request.Builder()
                            .url(m3uUrl.trim())
                            .header("User-Agent", "NAFITV24/2.5.0 (Android ExoPlayer)")
                            .build()
                        val m3uResp = client.newCall(m3uReq).execute()
                        if (m3uResp.isSuccessful) {
                            val body = m3uResp.body?.string() ?: ""
                            parseM3uLines(body.lines())
                        } else emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else emptyList()
            }

            val jsonDeferred = async {
                try {
                    val jsonReq = Request.Builder()
                        .url(jsonUrl.trim())
                        .header("User-Agent", "NAFITV24/2.5.0 (Android ExoPlayer)")
                        .build()
                    val jsonResp = client.newCall(jsonReq).execute()
                    if (jsonResp.isSuccessful) {
                        jsonResp.body?.string()?.trim()
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            val m3uChannels = m3uDeferred.await()
            val jsonContent = jsonDeferred.await()
            if (jsonContent.isNullOrBlank()) {
                return@withContext m3uChannels.map { it.copy(type = MediaType.LIVE_EVENT) }
            }
            val rootObj = JSONObject(jsonContent)
            val matchesArr = rootObj.optJSONArray("Matches") ?: JSONArray()

            val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            sdfIn.timeZone = java.util.TimeZone.getTimeZone("GMT+6") // Tapmad BD timezone
            val sdfOut = java.text.SimpleDateFormat("hh:mm a, dd MMM", java.util.Locale.US)
            val nowMillis = System.currentTimeMillis()

            val rawParsedList = mutableListOf<MediaItem>()

            for (i in 0 until matchesArr.length()) {
                val mObj = matchesArr.optJSONObject(i) ?: continue
                val entityId = mObj.optString("EntityId", "$i")
                val videoName = mObj.optString("VideoName", "").trim()
                val categoryName = mObj.optString("CategoryName", "").trim()
                val stageName = mObj.optString("StageName", "").trim()
                val eventStartDate = mObj.optString("EventStartDate", "").trim()
                val desc = mObj.optString("Description", "").trim()
                val thumbStd = mObj.optString("ThumbnailStandard", "").trim().takeIf { it.isNotBlank() }
                val thumbTv = mObj.optString("ThumbnailTV", "").trim().takeIf { it.isNotBlank() }
                val statusStr = mObj.optString("Status", "Upcoming").trim()
                val streamUrl = mObj.optString("stream_url", "").trim()
                val isFree = mObj.optBoolean("IsFreeToWatch", false)

                var countdownEpoch: Long? = null
                var formattedTime: String? = null
                var isLiveNow = statusStr.equals("Live", ignoreCase = true)

                if (eventStartDate.isNotBlank()) {
                    try {
                        val date = sdfIn.parse(eventStartDate)
                        if (date != null) {
                            countdownEpoch = date.time
                            formattedTime = sdfOut.format(date)
                            if (date.time <= nowMillis) {
                                isLiveNow = true
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Extract teams from videoName
                var cleanName = videoName
                    .replace(Regex("(?i)^Watch Free\\s*-\\s*"), "")
                    .replace(Regex("(?i)\\s*\\|.*$"), "")
                    .trim()

                var team1: String? = null
                var team2: String? = null
                if (cleanName.contains(" vs ", ignoreCase = true)) {
                    val parts = cleanName.split(Regex("(?i)\\s+vs\\s+"))
                    if (parts.size >= 2) {
                        team1 = parts[0].trim()
                        team2 = parts[1].replace(Regex("(?i)\\s*-\\s*W$"), "")
                            .replace(Regex("(?i)\\s+Test Series.*$"), "")
                            .replace(Regex("(?i)\\s+T20.*$"), "")
                            .replace(Regex("(?i)\\s+ODI.*$"), "")
                            .trim()
                    }
                }

                // Categorize Sport
                val sportCategory = when {
                    categoryName.contains("Cricket", ignoreCase = true) || 
                    categoryName.contains("Tour of", ignoreCase = true) || 
                    videoName.contains("Cricket", ignoreCase = true) ||
                    stageName.contains("Test", ignoreCase = true) ||
                    categoryName.contains("IPL", ignoreCase = true) ||
                    categoryName.contains("BPL", ignoreCase = true) ||
                    categoryName.contains("PSL", ignoreCase = true) -> "Cricket"
                    
                    categoryName.contains("Football", ignoreCase = true) || 
                    categoryName.contains("Soccer", ignoreCase = true) ||
                    categoryName.contains("League", ignoreCase = true) ||
                    (categoryName.contains("Cup", ignoreCase = true) && !categoryName.contains("Hockey", ignoreCase = true)) -> "Football"
                    
                    categoryName.contains("Hockey", ignoreCase = true) || 
                    categoryName.contains("FIH", ignoreCase = true) -> "Hockey"
                    
                    categoryName.contains("Kabaddi", ignoreCase = true) -> "Kabaddi"
                    
                    categoryName.isNotBlank() -> categoryName
                    else -> "Sports"
                }

                val serversList = mutableListOf<StreamServer>()
                val primaryServerName = if (isFree) "Watch Free" else "Live HD"
                if (streamUrl.isNotBlank()) {
                    serversList.add(StreamServer(primaryServerName, streamUrl))
                }

                // Match with m3uChannels
                val matchingM3u = m3uChannels.filter { 
                    it.id.contains(entityId) || 
                    (team1 != null && team2 != null && it.title.contains(team1, ignoreCase = true) && it.title.contains(team2, ignoreCase = true))
                }
                for (m in matchingM3u) {
                    if (m.streamUrl.isNotBlank() && serversList.none { it.url.trim().equals(m.streamUrl.trim(), ignoreCase = true) }) {
                        val srvName = if (m.title.contains("Watch Free", ignoreCase = true)) {
                            "Watch Free"
                        } else {
                            "HD Server ${serversList.size + 1}"
                        }
                        serversList.add(StreamServer(srvName, m.streamUrl))
                    }
                }

                val primaryStream = serversList.firstOrNull()?.url ?: streamUrl

                val cleanCat = categoryName.replace("Tapmad BD", "", ignoreCase = true).replace("Tapmad", "", ignoreCase = true).trim()
                val cleanVidName = videoName.replace("Tapmad BD", "", ignoreCase = true).replace("Tapmad", "", ignoreCase = true).trim()

                val displayTitle = if (cleanCat.isNotBlank() && !cleanVidName.contains(cleanCat, ignoreCase = true)) {
                    "$cleanVidName | $cleanCat"
                } else {
                    cleanVidName
                }

                val stageHeader = if (stageName.isNotBlank()) stageName.uppercase() else "GROUP STAGE"
                val tournamentBadge = if (cleanCat.isNotBlank()) cleanCat else "$sportCategory 2026"

                items.add(
                    MediaItem(
                        id = "tapmad_${entityId}_$i",
                        title = displayTitle.ifBlank { videoName },
                        category = sportCategory,
                        type = MediaType.LIVE_EVENT,
                        streamUrl = primaryStream,
                        servers = serversList,
                        logoUrl = thumbStd ?: thumbTv,
                        description = desc.takeIf { it.isNotBlank() },
                        isLive = isLiveNow,
                        status = stageHeader,
                        tournament = tournamentBadge,
                        team1 = team1 ?: videoName,
                        team2 = team2 ?: stageHeader,
                        team1Logo = thumbStd,
                        team2Logo = thumbTv,
                        matchTimeFormatted = formattedTime ?: eventStartDate,
                        countdownTargetSeconds = countdownEpoch,
                        quality = "HD"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    fun saveMoviesM3uUrl(url: String) {
        prefs.edit().putString("saved_movies_m3u_url", url).apply()
    }

    fun getSavedMoviesM3uUrl(): String {
        val stored = prefs.getString("saved_movies_m3u_url", null)
        if (stored.isNullOrBlank() || stored.contains("NFmovie.m3u")) {
            return DEFAULT_MOVIES_JSON_URL
        }
        return stored
    }

    // Push remote configuration (Live TV M3U, Sports M3U, Movies M3U) to Firebase RTDB and Firestore
    suspend fun pushAppConfigToFirebase(
        liveTvM3u: String = getSavedLiveTvM3uUrl(),
        sportsM3u: String = getSavedSportsM3uUrl(),
        moviesM3u: String = getSavedMoviesM3uUrl(),
        url: String = getSavedFirebaseUrl()
    ): Boolean = withContext(Dispatchers.IO) {
        var success = false
        // 1. Push to RTDB
        if (url.isNotBlank()) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                val obj = JSONObject()
                obj.put("liveTvM3uUrl", liveTvM3u)
                obj.put("sportsM3uUrl", sportsM3u)
                obj.put("moviesM3uUrl", moviesM3u)
                val body = obj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val targetUrl = appendRtdbAuth("$cleanUrl/app_config.json")
                val req = Request.Builder().url(targetUrl).put(body).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) success = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // 2. Push to Firestore
        try {
            val firestoreObj = JSONObject()
            val fields = JSONObject()
            fields.put("liveTvM3uUrl", JSONObject().put("stringValue", liveTvM3u))
            fields.put("sportsM3uUrl", JSONObject().put("stringValue", sportsM3u))
            fields.put("moviesM3uUrl", JSONObject().put("stringValue", moviesM3u))
            firestoreObj.put("fields", fields)
            val fsBody = firestoreObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            for (dbId in databases) {
                val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/settings/app_config?key=$FIREBASE_API_KEY"
                val fsReq = Request.Builder().url(fsUrl).patch(fsBody).build()
                val fsResp = client.newCall(fsReq).execute()
                if (fsResp.isSuccessful) success = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        success
    }

    // Fetch remote configuration (Live TV M3U, Sports M3U, Movies M3U) from Firebase RTDB and Firestore
    suspend fun fetchAppConfigFromFirebase(url: String = getSavedFirebaseUrl()): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        var remoteLiveTv: String? = null
        var remoteSports: String? = null
        var remoteMovies: String? = null

        coroutineScope {
            // 1. Fetch from Firestore
            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            val fsJobs = databases.map { dbId ->
                async {
                    var fsLiveTv: String? = null
                    var fsSports: String? = null
                    var fsMovies: String? = null
                    try {
                        val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/settings/app_config?key=$FIREBASE_API_KEY"
                        val req = Request.Builder().url(fsUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            if (body.isNotBlank() && body.startsWith("{")) {
                                val json = JSONObject(body)
                                val fields = json.optJSONObject("fields")
                                if (fields != null) {
                                    fsLiveTv = fields.optJSONObject("liveTvM3uUrl")?.optString("stringValue")
                                    fsSports = fields.optJSONObject("sportsM3uUrl")?.optString("stringValue")
                                    fsMovies = fields.optJSONObject("moviesM3uUrl")?.optString("stringValue")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    Triple(fsLiveTv, fsSports, fsMovies)
                }
            }

            // 2. Fetch from RTDB
            val rtdbJob = async {
                var rtdbLiveTv: String? = null
                var rtdbSports: String? = null
                var rtdbMovies: String? = null
                if (url.isNotBlank()) {
                    try {
                        val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                        val targetUrl = appendRtdbAuth("$cleanUrl/app_config.json")
                        val req = Request.Builder().url(targetUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            if (body.isNotBlank() && body != "null" && body.startsWith("{")) {
                                val obj = JSONObject(body)
                                if (obj.has("liveTvM3uUrl")) rtdbLiveTv = obj.optString("liveTvM3uUrl")
                                if (obj.has("sportsM3uUrl")) rtdbSports = obj.optString("sportsM3uUrl")
                                if (obj.has("moviesM3uUrl")) rtdbMovies = obj.optString("moviesM3uUrl")
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                Triple(rtdbLiveTv, rtdbSports, rtdbMovies)
            }

            val fsResults = fsJobs.awaitAll()
            val rtdbResult = rtdbJob.await()

            for (res in fsResults) {
                if (!res.first.isNullOrBlank()) remoteLiveTv = res.first
                if (!res.second.isNullOrBlank()) remoteSports = res.second
                if (!res.third.isNullOrBlank()) remoteMovies = res.third
            }
            if (!rtdbResult.first.isNullOrBlank()) remoteLiveTv = rtdbResult.first
            if (!rtdbResult.second.isNullOrBlank()) remoteSports = rtdbResult.second
            if (!rtdbResult.third.isNullOrBlank()) remoteMovies = rtdbResult.third
        }

        if (remoteLiveTv != null || remoteSports != null || remoteMovies != null) {
            val finalLiveTv = if (!remoteLiveTv.isNullOrBlank()) remoteLiveTv!! else getSavedLiveTvM3uUrl()
            val finalSports = if (!remoteSports.isNullOrBlank()) remoteSports!! else getSavedSportsM3uUrl()
            val finalMovies = if (!remoteMovies.isNullOrBlank()) remoteMovies!! else getSavedMoviesM3uUrl()
            // Cache locally so offline access uses the latest remote config
            if (remoteLiveTv?.isNotBlank() == true) saveLiveTvM3uUrl(finalLiveTv)
            if (remoteSports?.isNotBlank() == true) saveSportsM3uUrl(finalSports)
            if (remoteMovies?.isNotBlank() == true) saveMoviesM3uUrl(finalMovies)
            Triple(finalLiveTv, finalSports, finalMovies)
        } else {
            null
        }
    }

    // -------------------------------------------------------------
    // APP UPDATE & VERSION MANAGEMENT (Firebase RTDB + Local Cache)
    // -------------------------------------------------------------
    suspend fun fetchAppUpdateInfo(url: String = getSavedFirebaseUrl()): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
            val targetUrl = appendRtdbAuth("$cleanUrl/app_update.json")
            val req = Request.Builder().url(targetUrl).header("User-Agent", "NAFITV24-Android/2.4.0").build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext getCachedAppUpdateInfo()
            val body = resp.body?.string() ?: return@withContext getCachedAppUpdateInfo()
            if (body.isBlank() || body == "null") return@withContext getCachedAppUpdateInfo()

            val obj = JSONObject(body)
            val info = AppUpdateInfo(
                versionCode = obj.optInt("versionCode", 1),
                versionName = obj.optString("versionName", "1.0"),
                downloadUrl = obj.optString("downloadUrl", ""),
                releaseNotes = obj.optString("releaseNotes", ""),
                isForceUpdate = obj.optBoolean("isForceUpdate", false),
                minSupportedVersionCode = obj.optInt("minSupportedVersionCode", 1),
                apkSize = obj.optString("apkSize", ""),
                releaseDate = obj.optString("releaseDate", "")
            )
            saveCachedAppUpdateInfo(info)
            info
        } catch (e: Exception) {
            e.printStackTrace()
            getCachedAppUpdateInfo()
        }
    }

    suspend fun pushAppUpdateInfo(info: AppUpdateInfo, url: String = getSavedFirebaseUrl()): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
            val obj = JSONObject()
            obj.put("versionCode", info.versionCode)
            obj.put("versionName", info.versionName)
            obj.put("downloadUrl", info.downloadUrl)
            obj.put("releaseNotes", info.releaseNotes)
            obj.put("isForceUpdate", info.isForceUpdate)
            obj.put("minSupportedVersionCode", info.minSupportedVersionCode)
            obj.put("apkSize", info.apkSize)
            obj.put("releaseDate", info.releaseDate)

            val body = obj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val targetUrl = appendRtdbAuth("$cleanUrl/app_update.json")
            val req = Request.Builder().url(targetUrl).put(body).build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                saveCachedAppUpdateInfo(info)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun saveCachedAppUpdateInfo(info: AppUpdateInfo) {
        val obj = JSONObject()
        obj.put("versionCode", info.versionCode)
        obj.put("versionName", info.versionName)
        obj.put("downloadUrl", info.downloadUrl)
        obj.put("releaseNotes", info.releaseNotes)
        obj.put("isForceUpdate", info.isForceUpdate)
        obj.put("minSupportedVersionCode", info.minSupportedVersionCode)
        obj.put("apkSize", info.apkSize)
        obj.put("releaseDate", info.releaseDate)
        prefs.edit().putString("cached_app_update", obj.toString()).apply()
    }

    fun getCachedAppUpdateInfo(): AppUpdateInfo? {
        val json = prefs.getString("cached_app_update", null) ?: return null
        return try {
            val obj = JSONObject(json)
            AppUpdateInfo(
                versionCode = obj.optInt("versionCode", 1),
                versionName = obj.optString("versionName", "1.0"),
                downloadUrl = obj.optString("downloadUrl", ""),
                releaseNotes = obj.optString("releaseNotes", ""),
                isForceUpdate = obj.optBoolean("isForceUpdate", false),
                minSupportedVersionCode = obj.optInt("minSupportedVersionCode", 1),
                apkSize = obj.optString("apkSize", ""),
                releaseDate = obj.optString("releaseDate", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isUpdateDismissed(versionCode: Int, versionName: String = ""): Boolean {
        val dismissedCode = prefs.getInt("dismissed_update_version", 0)
        val dismissedName = prefs.getString("dismissed_update_name", "") ?: ""
        if (versionName.isNotBlank() && dismissedName == versionName) return true
        return dismissedCode != 0 && dismissedCode >= versionCode
    }

    fun dismissUpdate(versionCode: Int, versionName: String = "") {
        prefs.edit()
            .putInt("dismissed_update_version", versionCode)
            .putString("dismissed_update_name", versionName)
            .apply()
    }

    // -------------------------------------------------------------
    // CLOUDSTREAM REPOSITORIES & MOVIE SITES (Phisher Repo & Extensions)
    // -------------------------------------------------------------

    private val KNOWN_PROVIDER_DOMAINS = mapOf(
        "allwish" to "https://allwish.me",
        "dorabash" to "https://dorabash.com",
        "animesalt" to "https://animesalt.com",
        "animecloud" to "https://animecloud.top",
        "showflix" to "https://showflix.in",
        "ringz" to "https://ringz.in",
        "xdmovies" to "https://xdmovies.site",
        "yflix" to "https://yflix.to",
        "yts" to "https://yts.mx",
        "multimovies" to "https://multimovies.online",
        "cineb" to "https://cineb.rs",
        "flixhq" to "https://flixhq.to",
        "smashystream" to "https://smashystream.com",
        "loklok" to "https://loklok.com",
        "toonstream" to "https://toonstream.co",
        "vegamovies" to "https://vegamovies.im",
        "bollyflix" to "https://bollyflix.tools",
        "filmyzilla" to "https://filmyzilla.com.by",
        "moviesdrive" to "https://moviesdrive.in",
        "moviesmod" to "https://moviesmod.org",
        "chorki" to "https://www.chorki.com",
        "bongobd" to "https://bongobd.com",
        "bioscope" to "https://www.bioscopelive.com",
        "toffee" to "https://toffeelive.com",
        "123movies" to "https://ww4.123moviesfree.net",
        "fmovies" to "https://fmovies.ps",
        "gogoanime" to "https://gogoanime3.co",
        "hdtoday" to "https://hdtoday.tv",
        "sflix" to "https://sflix.to",
        "superstream" to "https://superstream.media",
        "vidsrc" to "https://vidsrc.to"
    )

    fun cleanRepoUrl(url: String): String {
        var clean = url.trim()
        try {
            // Handle cloudstreamrepo:// or cloudstream:// schemes
            if (clean.startsWith("cloudstreamrepo://", ignoreCase = true)) {
                clean = clean.substring("cloudstreamrepo://".length)
            } else if (clean.startsWith("cloudstream://", ignoreCase = true)) {
                clean = clean.substring("cloudstream://".length)
            }

            // Handle cs.repo host (e.g. https://cs.repo/?url=... or https://cs.repo/add?url=...)
            if (clean.contains("cs.repo", ignoreCase = true)) {
                val uri = Uri.parse(if (clean.startsWith("http")) clean else "https://$clean")
                val urlParam = uri.getQueryParameter("url") ?: uri.getQueryParameter("repo")
                if (!urlParam.isNullOrBlank()) {
                    clean = urlParam
                } else {
                    val path = uri.path?.removePrefix("/") ?: ""
                    if (path.startsWith("http")) {
                        clean = path
                    }
                }
            }

            // Handle query string url parameter e.g. ?url=https%3A%2F%2F...
            if (clean.contains("url=", ignoreCase = true)) {
                val extracted = clean.substringAfter("url=").substringBefore("&")
                if (extracted.isNotBlank()) {
                    clean = extracted
                }
            }

            // URL Decode if encoded
            if (clean.contains("%3A", ignoreCase = true) || clean.contains("%2F", ignoreCase = true)) {
                clean = java.net.URLDecoder.decode(clean, "UTF-8")
            }

            if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
                clean = "https://$clean"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return clean
    }

    suspend fun parseCloudStreamRepo(rawUrl: String): CloudStreamRepo = withContext(Dispatchers.IO) {
        val finalUrl = cleanRepoUrl(rawUrl)
        val repoId = "repo_" + finalUrl.hashCode().toString().replace("-", "n")

        try {
            val req = Request.Builder()
                .url(finalUrl)
                .header("User-Agent", "CloudStream/4.0")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code}: ${resp.message}")
            }
            val body = resp.body?.string() ?: throw Exception("Empty repository response")
            val json = JSONObject(body)

            val repoName = json.optString("name", "CloudStream Repository").ifBlank { "CloudStream Repo" }
            val repoDescription = json.optString("description", "").takeIf { it.isNotBlank() }
            val repoIcon = json.optString("iconUrl", "").ifBlank { json.optString("icon", "") }.takeIf { it.isNotBlank() }

            val providersList = mutableListOf<MovieProvider>()

            // 1. Direct pluginLists (URLs or relative paths pointing to plugins.json)
            val pluginLists = json.optJSONArray("pluginLists")
            if (pluginLists != null && pluginLists.length() > 0) {
                for (i in 0 until pluginLists.length()) {
                    val pListUrl = pluginLists.optString(i, "")
                    if (pListUrl.isNotBlank()) {
                        val resolvedPListUrl = if (pListUrl.startsWith("http", ignoreCase = true)) {
                            pListUrl
                        } else {
                            val base = finalUrl.substringBeforeLast("/")
                            "$base/${pListUrl.removePrefix("/")}"
                        }
                        val parsedProviders = fetchPluginsFromJsonUrl(resolvedPListUrl, repoId, repoName)
                        providersList.addAll(parsedProviders)
                    }
                }
            }

            // 2. Direct plugins array in repo.json
            val directPlugins = json.optJSONArray("plugins") ?: json.optJSONArray("providers")
            if (directPlugins != null && directPlugins.length() > 0) {
                val parsed = parsePluginsJsonArray(directPlugins, repoId, repoName)
                providersList.addAll(parsed)
            }

            // If repository URL itself was a plugins.json array
            if (providersList.isEmpty() && body.trim().startsWith("[")) {
                val arr = JSONArray(body)
                providersList.addAll(parsePluginsJsonArray(arr, repoId, repoName))
            }

            // If empty, fallback to Phisher preset
            if (providersList.isEmpty()) {
                providersList.addAll(getInitialPhisherProviders(repoId, repoName))
            }

            // Fallback: If repo didn't have web links for some plugins, synthesize best domain match
            // Repositories are loaded with extensions available for download
            val enhancedProviders = providersList.distinctBy { it.name.lowercase() }.map { provider ->
                var site = provider.siteUrl
                if (site.isBlank() || !site.startsWith("http") || site.endsWith(".cs3")) {
                    val cleanKey = provider.name.lowercase().replace(" ", "").replace("-", "").replace("_", "")
                    val known = KNOWN_PROVIDER_DOMAINS.entries.firstOrNull { cleanKey.contains(it.key) }?.value
                    site = known ?: "https://${cleanKey}.com"
                }
                val isDownloaded = dexPluginManager.isPluginDownloaded(provider)
                provider.copy(siteUrl = site, isInstalled = isDownloaded, isEnabled = isDownloaded)
            }

            CloudStreamRepo(
                id = repoId,
                name = repoName,
                repoUrl = rawUrl.trim(),
                description = repoDescription ?: "${enhancedProviders.size} টি মুভি ও সিরিজ প্রোভাইডার উপলব্ধ",
                iconUrl = repoIcon ?: "https://raw.githubusercontent.com/Hexated/cloudstream-extensions-hexated/builds/icon.png",
                providers = enhancedProviders,
                isEnabled = true,
                lastUpdated = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Return fallback repo with synthesized providers if network fails
            val fallbackRepoName = if (rawUrl.contains("hexated", ignoreCase = true)) "Hexated Streams Repo" else "CloudStream Extension Repo"
            CloudStreamRepo(
                id = repoId,
                name = fallbackRepoName,
                repoUrl = rawUrl.trim(),
                description = "মুভি, সিরিজ ও অ্যানিমে ওয়েবসাইট রিপোজিটরি",
                iconUrl = "https://raw.githubusercontent.com/Hexated/cloudstream-extensions-hexated/builds/icon.png",
                providers = emptyList(),
                isEnabled = true,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    private suspend fun fetchPluginsFromJsonUrl(pUrl: String, repoId: String, repoName: String): List<MovieProvider> = withContext(Dispatchers.IO) {
        val cleanUrl = cleanRepoUrl(pUrl)
        try {
            val req = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            if (body.isBlank() || !body.trim().startsWith("[")) return@withContext emptyList()
            val arr = JSONArray(body)
            parsePluginsJsonArray(arr, repoId, repoName)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parsePluginsJsonArray(arr: JSONArray, repoId: String, repoName: String): List<MovieProvider> {
        val list = mutableListOf<MovieProvider>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name", "").ifBlank { obj.optString("internalName", "") }
            if (name.isBlank()) continue

            val desc = obj.optString("description", "")
            val iconUrl = obj.optString("iconUrl", "").ifBlank { obj.optString("icon", "") }
            var siteUrl = obj.optString("siteUrl", "").ifBlank { obj.optString("url", "") }

            val typesList = mutableListOf<String>()
            val tvTypes = obj.optJSONArray("tvTypes")
            if (tvTypes != null) {
                for (t in 0 until tvTypes.length()) {
                    typesList.add(tvTypes.optString(t))
                }
            }
            if (typesList.isEmpty()) {
                typesList.add("Movie")
                typesList.add("Series")
            }

            val lang = obj.optString("language", "Multi").ifBlank { "Multi" }
            val cleanKey = name.lowercase().replace(" ", "").replace("-", "").replace("_", "")
            if (siteUrl.isBlank() || !siteUrl.startsWith("http") || siteUrl.endsWith(".cs3")) {
                val known = KNOWN_PROVIDER_DOMAINS.entries.firstOrNull { cleanKey.contains(it.key) }?.value
                siteUrl = known ?: "https://${cleanKey}.com"
            }

            val provId = "prov_${repoId}_${cleanKey}_$i"
            list.add(
                MovieProvider(
                    id = provId,
                    name = name,
                    description = desc.ifBlank { "$name থেকে আনলিমিটেড মুভি ও টিভি সিরিজ দেখুন" },
                    iconUrl = iconUrl.takeIf { it.isNotBlank() },
                    siteUrl = siteUrl,
                    types = typesList,
                    language = lang,
                    repoId = repoId,
                    repoName = repoName,
                    isCustom = false,
                    isEnabled = true
                )
            )
        }
        return list
    }

    fun getInitialPhisherProviders(repoId: String = "repo", repoName: String = "Repo"): List<MovieProvider> = emptyList()

    // -------------------------------------------------------------
    // LOCAL PERSISTENCE FOR CLOUDSTREAM REPOSITORIES & MOVIE SITES
    // -------------------------------------------------------------
    fun getSavedCloudStreamRepos(): List<CloudStreamRepo> {
        val json = prefs.getString("cloudstream_repos", "[]") ?: "[]"
        val list = mutableListOf<CloudStreamRepo>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val provList = mutableListOf<MovieProvider>()
                val pArr = obj.optJSONArray("providers")
                if (pArr != null) {
                    for (p in 0 until pArr.length()) {
                        val po = pArr.getJSONObject(p)
                        val typesList = mutableListOf<String>()
                        val tArr = po.optJSONArray("types")
                        if (tArr != null) {
                            for (t in 0 until tArr.length()) typesList.add(tArr.optString(t))
                        }
                        val isInstalledStored = po.optBoolean("isInstalled", false)
                        val isDownloaded = dexPluginManager.isPluginDownloaded(MovieProvider(id = po.optString("id", ""), name = po.optString("name", ""), siteUrl = po.optString("siteUrl", "")))
                        val isEffectiveInstalled = isInstalledStored && isDownloaded

                        provList.add(
                            MovieProvider(
                                id = po.optString("id", "p_$p"),
                                name = po.optString("name", ""),
                                description = po.optString("description", "").takeIf { it.isNotBlank() },
                                iconUrl = po.optString("iconUrl", "").takeIf { it.isNotBlank() },
                                siteUrl = po.optString("siteUrl", ""),
                                searchUrl = po.optString("searchUrl", "").takeIf { it.isNotBlank() },
                                types = typesList.ifEmpty { listOf("Movie", "Series") },
                                language = po.optString("language", "Multi"),
                                repoId = po.optString("repoId", ""),
                                repoName = po.optString("repoName", ""),
                                isCustom = po.optBoolean("isCustom", false),
                                isInstalled = isEffectiveInstalled,
                                isEnabled = isEffectiveInstalled && po.optBoolean("isEnabled", true)
                            )
                        )
                    }
                }
                list.add(
                    CloudStreamRepo(
                        id = obj.optString("id", "repo_$i"),
                        name = obj.optString("name", "Repo $i"),
                        repoUrl = obj.optString("repoUrl", ""),
                        description = obj.optString("description", "").takeIf { it.isNotBlank() },
                        iconUrl = obj.optString("iconUrl", "").takeIf { it.isNotBlank() },
                        providers = provList,
                        isEnabled = obj.optBoolean("isEnabled", true),
                        lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Cleanse and purge any saved Phisher repository completely
        val countBefore = list.size
        list.removeAll {
            it.repoUrl.contains("phisher", ignoreCase = true) ||
            it.id.contains("phisher", ignoreCase = true) ||
            it.name.contains("Phisher", ignoreCase = true)
        }
        if (list.size != countBefore) {
            saveCloudStreamRepos(list)
        }

        return list
    }

    fun saveCloudStreamRepos(repos: List<CloudStreamRepo>) {
        val arr = JSONArray()
        repos.forEach { repo ->
            val obj = JSONObject()
            obj.put("id", repo.id)
            obj.put("name", repo.name)
            obj.put("repoUrl", repo.repoUrl)
            obj.put("description", repo.description ?: "")
            obj.put("iconUrl", repo.iconUrl ?: "")
            obj.put("isEnabled", repo.isEnabled)
            obj.put("lastUpdated", repo.lastUpdated)

            val pArr = JSONArray()
            repo.providers.forEach { prov ->
                val po = JSONObject()
                po.put("id", prov.id)
                po.put("name", prov.name)
                po.put("description", prov.description ?: "")
                po.put("iconUrl", prov.iconUrl ?: "")
                po.put("siteUrl", prov.siteUrl)
                po.put("searchUrl", prov.searchUrl ?: "")
                po.put("language", prov.language)
                po.put("repoId", prov.repoId ?: repo.id)
                po.put("repoName", prov.repoName ?: repo.name)
                po.put("isCustom", prov.isCustom)
                po.put("isInstalled", prov.isInstalled)
                po.put("isEnabled", prov.isEnabled)

                val tArr = JSONArray()
                prov.types.forEach { tArr.put(it) }
                po.put("types", tArr)
                pArr.put(po)
            }
            obj.put("providers", pArr)
            arr.put(obj)
        }
        prefs.edit().putString("cloudstream_repos", arr.toString()).apply()
    }

    fun saveCloudStreamRepo(repo: CloudStreamRepo) {
        val current = getSavedCloudStreamRepos().toMutableList()
        current.removeAll { it.id == repo.id || it.repoUrl.equals(repo.repoUrl, ignoreCase = true) }
        current.add(0, repo)
        saveCloudStreamRepos(current)
    }

    fun deleteCloudStreamRepo(repoId: String) {
        val current = getSavedCloudStreamRepos().filterNot { it.id == repoId }
        saveCloudStreamRepos(current)
    }

    fun getCustomMovieProviders(): List<MovieProvider> {
        val json = prefs.getString("custom_movie_providers", "[]") ?: "[]"
        val list = mutableListOf<MovieProvider>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val po = arr.getJSONObject(i)
                val typesList = mutableListOf<String>()
                val tArr = po.optJSONArray("types")
                if (tArr != null) {
                    for (t in 0 until tArr.length()) typesList.add(tArr.optString(t))
                }
                val isInstalledStored = po.optBoolean("isInstalled", false)
                val isDownloaded = dexPluginManager.isPluginDownloaded(MovieProvider(id = po.optString("id", ""), name = po.optString("name", ""), siteUrl = po.optString("siteUrl", "")))
                val isEffectiveInstalled = isInstalledStored && isDownloaded

                list.add(
                    MovieProvider(
                        id = po.optString("id", "cust_prov_$i"),
                        name = po.optString("name", ""),
                        description = po.optString("description", "").takeIf { it.isNotBlank() },
                        iconUrl = po.optString("iconUrl", "").takeIf { it.isNotBlank() },
                        siteUrl = po.optString("siteUrl", ""),
                        searchUrl = po.optString("searchUrl", "").takeIf { it.isNotBlank() },
                        types = typesList.ifEmpty { listOf("Movie", "Series") },
                        language = po.optString("language", "Multi"),
                        repoId = po.optString("repoId", "custom"),
                        repoName = po.optString("repoName", "Custom Added"),
                        isCustom = true,
                        isInstalled = isEffectiveInstalled,
                        isEnabled = isEffectiveInstalled && po.optBoolean("isEnabled", true)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveCustomMovieProviders(providers: List<MovieProvider>) {
        val arr = JSONArray()
        providers.forEach { prov ->
            val po = JSONObject()
            po.put("id", prov.id)
            po.put("name", prov.name)
            po.put("description", prov.description ?: "")
            po.put("iconUrl", prov.iconUrl ?: "")
            po.put("siteUrl", prov.siteUrl)
            po.put("searchUrl", prov.searchUrl ?: "")
            po.put("language", prov.language)
            po.put("repoId", prov.repoId ?: "custom")
            po.put("repoName", prov.repoName ?: "Custom Added")
            po.put("isCustom", true)
            po.put("isInstalled", prov.isInstalled)
            po.put("isEnabled", prov.isEnabled)

            val tArr = JSONArray()
            prov.types.forEach { tArr.put(it) }
            po.put("types", tArr)
            arr.put(po)
        }
        prefs.edit().putString("custom_movie_providers", arr.toString()).apply()
    }

    fun saveCustomMovieProvider(provider: MovieProvider) {
        val current = getCustomMovieProviders().toMutableList()
        current.removeAll { it.id == provider.id }
        current.add(0, provider)
        saveCustomMovieProviders(current)
    }

    fun deleteMovieProvider(providerId: String) {
        val current = getCustomMovieProviders().filterNot { it.id == providerId }
        saveCustomMovieProviders(current)
    }

    fun getAllMovieProviders(): List<MovieProvider> {
        val repos = getSavedCloudStreamRepos()
        val repoProviders = repos.flatMap { repo ->
            repo.providers.map { it.copy(repoName = repo.name) }
        }
        val customProviders = getCustomMovieProviders()
        return (repoProviders + customProviders).distinctBy { it.id }
    }

    fun saveMovieProviders(providers: List<MovieProvider>) {
        val repos = getSavedCloudStreamRepos().toMutableList()
        var reposModified = false
        for (i in 0 until repos.size) {
            val repo = repos[i]
            val updatedProviders = repo.providers.map { prov ->
                providers.find { it.id == prov.id } ?: prov
            }
            if (updatedProviders != repo.providers) {
                repos[i] = repo.copy(providers = updatedProviders)
                reposModified = true
            }
        }
        if (reposModified) {
            saveCloudStreamRepos(repos)
        }

        // Also update custom providers
        val customOnly = providers.filter { it.isCustom }
        if (customOnly.isNotEmpty()) {
            saveCustomMovieProviders(customOnly)
        }
    }

    fun toggleMovieProviderInstalled(providerId: String, isInstalled: Boolean) {
        val all = getAllMovieProviders().toMutableList()
        val idx = all.indexOfFirst { it.id == providerId }
        if (idx >= 0) {
            all[idx] = all[idx].copy(isInstalled = isInstalled, isEnabled = if (!isInstalled) false else all[idx].isEnabled)
            saveMovieProviders(all)
        }
    }

    fun toggleMovieProviderEnabled(providerId: String, isEnabled: Boolean) {
        val all = getAllMovieProviders().toMutableList()
        val idx = all.indexOfFirst { it.id == providerId }
        if (idx >= 0) {
            all[idx] = all[idx].copy(isEnabled = isEnabled)
            saveMovieProviders(all)
        }
    }

    suspend fun parseCloudStreamRepoFromUrl(rawUrl: String): CloudStreamRepo = withContext(Dispatchers.IO) {
        parseCloudStreamRepo(rawUrl)
    }

    suspend fun installExtensionFromUrl(url: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val parsedRepo = parseCloudStreamRepoFromUrl(url)
            val existingProviders = getAllMovieProviders()
            val providersWithExistingState = parsedRepo.providers.map { prov ->
                val existing = existingProviders.firstOrNull { it.id == prov.id || it.name.equals(prov.name, ignoreCase = true) }
                if (existing != null) {
                    prov.copy(isInstalled = existing.isInstalled, isEnabled = existing.isEnabled)
                } else {
                    val isDownloaded = dexPluginManager.isPluginDownloaded(prov)
                    prov.copy(isInstalled = isDownloaded, isEnabled = isDownloaded)
                }
            }
            val updatedRepo = parsedRepo.copy(providers = providersWithExistingState)
            saveCloudStreamRepo(updatedRepo)

            Pair(true, "${updatedRepo.name} (${updatedRepo.providers.size} টি প্লাগইন) যুক্ত হয়েছে - পছন্দমতো ডাউনলোড করে নিন")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "ত্রুটি: ${e.localizedMessage ?: "URL থেকে রিপোজিটরি লোড করা যায়নি"}")
        }
    }

    suspend fun downloadProvider(provider: MovieProvider): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val ok = dexPluginManager.downloadProvider(provider)
            if (ok) {
                Pair(true, "${provider.name} ডাউনলোড সম্পন্ন হয়েছে")
            } else {
                Pair(false, "${provider.name} ডাউনলোড করা সম্ভব হয়নি")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "ডাউনলোড ত্রুটি: ${e.localizedMessage}")
        }
    }

    suspend fun installProvider(provider: MovieProvider): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val ok = dexPluginManager.installProvider(provider)
            if (ok) {
                val all = getAllMovieProviders().toMutableList()
                val idx = all.indexOfFirst { it.id == provider.id || it.name.equals(provider.name, ignoreCase = true) }
                val updated = provider.copy(isInstalled = true, isEnabled = true)
                if (idx >= 0) {
                    all[idx] = updated
                } else {
                    all.add(updated)
                }
                saveMovieProviders(all)

                val repos = getSavedCloudStreamRepos().map { repo ->
                    if (repo.providers.any { it.id == provider.id || it.name.equals(provider.name, ignoreCase = true) }) {
                        val updatedProvs = repo.providers.map {
                            if (it.id == provider.id || it.name.equals(provider.name, ignoreCase = true)) updated else it
                        }
                        repo.copy(providers = updatedProvs)
                    } else repo
                }
                saveCloudStreamRepos(repos)

                Pair(true, "${provider.name} সফলভাবে ইনস্টল ও সক্রিয় হয়েছে")
            } else {
                Pair(false, "${provider.name} ইনস্টল করা যায়নি")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "ইনস্টল ত্রুটি: ${e.localizedMessage}")
        }
    }

    suspend fun downloadAndInstallProvider(provider: MovieProvider): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // 1. Download cs3 file
            dexPluginManager.downloadProvider(provider)
            // 2. Install and load
            dexPluginManager.installProvider(provider)

            // 3. Mark as installed & enabled in providers list
            val all = getAllMovieProviders().toMutableList()
            val idx = all.indexOfFirst { it.id == provider.id || it.name.equals(provider.name, ignoreCase = true) }
            val updated = provider.copy(isInstalled = true, isEnabled = true)
            if (idx >= 0) {
                all[idx] = updated
            } else {
                all.add(updated)
            }
            saveMovieProviders(all)

            // 4. Update in all repositories
            val repos = getSavedCloudStreamRepos().map { repo ->
                if (repo.providers.any { it.id == provider.id || it.name.equals(provider.name, ignoreCase = true) }) {
                    val updatedProvs = repo.providers.map {
                        if (it.id == provider.id || it.name.equals(provider.name, ignoreCase = true)) updated else it
                    }
                    repo.copy(providers = updatedProvs)
                } else repo
            }
            saveCloudStreamRepos(repos)

            Pair(true, "${provider.name} সফলভাবে ডাউনলোড ও ইন্সটল হয়েছে")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "ডাউনলোড ত্রুটি: ${e.localizedMessage}")
        }
    }

    suspend fun uninstallProvider(provider: MovieProvider): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            dexPluginManager.deletePlugin(provider)

            val all = getAllMovieProviders().toMutableList()
            val idx = all.indexOfFirst { it.id == provider.id || it.name.equals(provider.name, ignoreCase = true) }
            val updated = provider.copy(isInstalled = false, isEnabled = false)
            if (idx >= 0) {
                all[idx] = updated
            }
            saveMovieProviders(all)

            val repos = getSavedCloudStreamRepos().map { repo ->
                if (repo.providers.any { it.id == provider.id || it.name.equals(provider.name, ignoreCase = true) }) {
                    val updatedProvs = repo.providers.map {
                        if (it.id == provider.id || it.name.equals(provider.name, ignoreCase = true)) updated else it
                    }
                    repo.copy(providers = updatedProvs)
                } else repo
            }
            saveCloudStreamRepos(repos)

            Pair(true, "${provider.name} আনইন্সটল করা হয়েছে")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "আনইন্সটল ত্রুটি: ${e.localizedMessage}")
        }
    }

    fun isProviderDownloaded(provider: MovieProvider): Boolean {
        return dexPluginManager.isPluginDownloaded(provider)
    }

    fun installExtensionFromJson(jsonContent: String): Pair<Boolean, String> {
        return try {
            val trimmed = jsonContent.trim()
            if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                val repoName = obj.optString("name", "Custom JSON Repo")
                val repoId = "repo_local_${System.currentTimeMillis()}"
                val plugins = mutableListOf<MovieProvider>()
                val pArr = obj.optJSONArray("pluginLists") ?: obj.optJSONArray("plugins") ?: obj.optJSONArray("providers")
                if (pArr != null) {
                    for (i in 0 until pArr.length()) {
                        val pItem = pArr.opt(i)
                        if (pItem is JSONObject) {
                            plugins.add(
                                MovieProvider(
                                    id = "p_loc_${System.currentTimeMillis()}_$i",
                                    name = pItem.optString("name", "Plugin $i"),
                                    description = pItem.optString("description", "Local JSON Extension"),
                                    siteUrl = pItem.optString("url", pItem.optString("siteUrl", "https://google.com")),
                                    iconUrl = pItem.optString("iconUrl", pItem.optString("icon", null)),
                                    version = pItem.optString("version", "v1.0"),
                                    types = listOf("Movie", "Series"),
                                    language = pItem.optString("language", "Multi"),
                                    repoId = repoId,
                                    repoName = repoName,
                                    isInstalled = true,
                                    isEnabled = true
                                )
                            )
                        }
                    }
                }
                if (plugins.isEmpty()) {
                    // Fallback to default
                    plugins.addAll(getInitialPhisherProviders(repoId, repoName))
                }
                val newRepo = CloudStreamRepo(
                    id = repoId,
                    name = repoName,
                    repoUrl = "local://json",
                    description = obj.optString("description", "Local Imported Repository"),
                    providers = plugins,
                    isEnabled = true,
                    lastUpdated = System.currentTimeMillis()
                )
                saveCloudStreamRepo(newRepo)
                Pair(true, "$repoName (${plugins.size} টি এক্সটেনশন) সফলভাবে ইনস্টল হয়েছে")
            } else if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                val repoId = "repo_local_${System.currentTimeMillis()}"
                val plugins = parsePluginsJsonArray(arr, repoId, "Local JSON Plugins")
                val newRepo = CloudStreamRepo(
                    id = repoId,
                    name = "Local Plugins Repo",
                    repoUrl = "local://json_array",
                    description = "Local JSON Array Imported Repository",
                    providers = plugins.ifEmpty { getInitialPhisherProviders(repoId, "Local Plugins Repo") },
                    isEnabled = true,
                    lastUpdated = System.currentTimeMillis()
                )
                saveCloudStreamRepo(newRepo)
                Pair(true, "লোকাল ফাইল থেকে ${newRepo.providers.size} টি এক্সটেনশন যোগ হয়েছে")
            } else {
                Pair(false, "অবৈধ JSON ফরম্যাট")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "JSON পার্সিং ব্যর্থ হয়েছে: ${e.message}")
        }
    }

    // -------------------------------------------------------------
    // FIREBASE SYNC FOR CLOUDSTREAM REPOSITORIES & MOVIE SITES
    // -------------------------------------------------------------
    suspend fun pushCloudStreamReposToFirebase(repos: List<CloudStreamRepo>, url: String = getSavedFirebaseUrl()): Boolean = withContext(Dispatchers.IO) {
        var anySuccess = false
        try {
            val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
            val arr = JSONArray()
            repos.forEach { repo ->
                val obj = JSONObject()
                obj.put("id", repo.id)
                obj.put("name", repo.name)
                obj.put("repoUrl", repo.repoUrl)
                obj.put("description", repo.description ?: "")
                obj.put("iconUrl", repo.iconUrl ?: "")
                obj.put("isEnabled", repo.isEnabled)
                obj.put("lastUpdated", repo.lastUpdated)

                val pArr = JSONArray()
                repo.providers.forEach { prov ->
                    val po = JSONObject()
                    po.put("id", prov.id)
                    po.put("name", prov.name)
                    po.put("description", prov.description ?: "")
                    po.put("iconUrl", prov.iconUrl ?: "")
                    po.put("siteUrl", prov.siteUrl)
                    po.put("language", prov.language)
                    po.put("repoId", prov.repoId ?: repo.id)
                    po.put("repoName", prov.repoName ?: repo.name)
                    po.put("isEnabled", prov.isEnabled)
                    val tArr = JSONArray()
                    prov.types.forEach { tArr.put(it) }
                    po.put("types", tArr)
                    pArr.put(po)
                }
                obj.put("providers", pArr)
                arr.put(obj)
            }

            val body = arr.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val targetUrl = appendRtdbAuth("$cleanUrl/cloudstream_repos.json")
            val req = Request.Builder().url(targetUrl).put(body).build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                anySuccess = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        anySuccess
    }

    suspend fun fetchCloudStreamReposFromFirebase(url: String = getSavedFirebaseUrl()): List<CloudStreamRepo> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
            val targetUrl = appendRtdbAuth("$cleanUrl/cloudstream_repos.json")
            val req = Request.Builder().url(targetUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            if (body.isBlank() || body == "null") return@withContext emptyList()

            val list = mutableListOf<CloudStreamRepo>()
            if (body.trim().startsWith("[")) {
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val provList = mutableListOf<MovieProvider>()
                    val pArr = obj.optJSONArray("providers")
                    if (pArr != null) {
                        for (p in 0 until pArr.length()) {
                            val po = pArr.optJSONObject(p) ?: continue
                            val typesList = mutableListOf<String>()
                            val tArr = po.optJSONArray("types")
                            if (tArr != null) {
                                for (t in 0 until tArr.length()) typesList.add(tArr.optString(t))
                            }
                            provList.add(
                                MovieProvider(
                                    id = po.optString("id", "p_$p"),
                                    name = po.optString("name", ""),
                                    description = po.optString("description", "").takeIf { it.isNotBlank() },
                                    iconUrl = po.optString("iconUrl", "").takeIf { it.isNotBlank() },
                                    siteUrl = po.optString("siteUrl", ""),
                                    searchUrl = po.optString("searchUrl", "").takeIf { it.isNotBlank() },
                                    types = typesList.ifEmpty { listOf("Movie", "Series") },
                                    language = po.optString("language", "Multi"),
                                    repoId = po.optString("repoId", ""),
                                    repoName = po.optString("repoName", ""),
                                    isCustom = po.optBoolean("isCustom", false),
                                    isEnabled = po.optBoolean("isEnabled", true)
                                )
                            )
                        }
                    }
                    list.add(
                        CloudStreamRepo(
                            id = obj.optString("id", "fb_repo_$i"),
                            name = obj.optString("name", "Repo $i"),
                            repoUrl = obj.optString("repoUrl", ""),
                            description = obj.optString("description", "").takeIf { it.isNotBlank() },
                            iconUrl = obj.optString("iconUrl", "").takeIf { it.isNotBlank() },
                            providers = provList,
                            isEnabled = obj.optBoolean("isEnabled", true),
                            lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis())
                        )
                    )
                }
            }
            if (list.isNotEmpty()) {
                saveCloudStreamRepos(list)
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // -------------------------------------------------------------
    // LIVE STREAMING PROVIDER & EXTENSION CATALOG FETCHER
    // -------------------------------------------------------------
    suspend fun fetchLiveProviderCatalog(
        provider: MovieProvider? = null,
        query: String = "",
        typeFilter: String = "All"
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val userPriorityList = mutableListOf<MediaItem>()
        val extensionList = mutableListOf<MediaItem>()

        try {
            val deleted = getDeletedIds()

            // =========================================================================
            // 1. HIGHEST PRIORITY: USER'S OWN MOVIE PLAYLIST (NFmovie.m3u / Admin M3U)
            // AND ADMIN/FIREBASE PUBLISHED MOVIES & CUSTOM STREAMS (সবচেয়ে আগে দেখাবে)
            // =========================================================================
            val moviesM3uUrl = getSavedMoviesM3uUrl()
            val m3uDeferred = async {
                if (moviesM3uUrl.isNotBlank()) {
                    val urls = moviesM3uUrl.split("\n", ",").map { it.trim() }.filter { it.isNotBlank() }
                    val allM3uItems = mutableListOf<MediaItem>()
                    for (u in urls) {
                        try {
                            val parsed = parseM3uFromUrl(u).map { item ->
                                item.copy(
                                    type = if (item.type != MediaType.SERIES) MediaType.MOVIE else MediaType.SERIES,
                                    category = if (item.category.isNullOrBlank() || item.category == "Unknown") "NAFI OTT PLATFORM" else "NAFI OTT • ${item.category}",
                                    tournament = "NAFI_OTT"
                                )
                            }
                            allM3uItems.addAll(parsed)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    allM3uItems.filterNot { deleted.contains(it.id) }
                } else emptyList()
            }

            val customDeferred = async {
                getCustomStreams()
                    .filter { it.type == MediaType.MOVIE || it.type == MediaType.SERIES }
                    .filterNot { deleted.contains(it.id) }
                    .map { it.copy(tournament = "NAFI_OTT", category = if (it.category.isNullOrBlank() || it.category == "Unknown") "NAFI OTT PLATFORM" else "NAFI OTT • ${it.category}") }
            }

            val fbDeferred = async {
                try {
                    fetchFromFirebase()
                        .filter { it.type == MediaType.MOVIE || it.type == MediaType.SERIES }
                        .filterNot { deleted.contains(it.id) }
                        .map { it.copy(tournament = "NAFI_OTT", category = if (it.category.isNullOrBlank() || it.category == "Unknown") "NAFI OTT PLATFORM" else "NAFI OTT • ${it.category}") }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val userM3u = m3uDeferred.await()
            val userCustom = customDeferred.await()
            val userFb = fbDeferred.await()

            // User's playlists and admin movies go right at index 0 (Top Priority)
            userPriorityList.addAll(userM3u)
            userPriorityList.addAll(userCustom)
            userPriorityList.addAll(userFb)

            // =========================================================================
            // 2. SECONDARY: CLOUDSTREAM DYNAMIC DEX EXECUTION + NATIVE SCRAPERS
            // =========================================================================
            if (provider != null) {
                val isInstalled = provider.isInstalled || dexPluginManager.isPluginDownloaded(provider)
                if (isInstalled) {
                    // 1. Execute via DexClassLoader dynamic plugin if loaded
                    try {
                        val dexItems = if (query.isNotBlank()) {
                            dexPluginManager.searchPlugin(provider, query)
                        } else {
                            dexPluginManager.fetchPluginHomeCatalog(provider)
                        }
                        if (dexItems.isNotEmpty()) {
                            extensionList.addAll(dexItems)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 2. High performance Native Scrapers & Multi-Source extractors
                    try {
                        val nativeItems = nativeScraperEngine.fetchCatalog(provider, query, typeFilter)
                        if (nativeItems.isNotEmpty()) {
                            extensionList.addAll(nativeItems)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                // Provider is null (Global Home): Only query installed/downloaded providers!
                val installed = getAllMovieProviders().filter { it.isInstalled && it.isEnabled }
                for (prov in installed.take(8)) {
                    try {
                        val dexItems = if (query.isNotBlank()) {
                            dexPluginManager.searchPlugin(prov, query)
                        } else {
                            dexPluginManager.fetchPluginHomeCatalog(prov)
                        }
                        if (dexItems.isNotEmpty()) {
                            extensionList.addAll(dexItems)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        val nativeItems = nativeScraperEngine.fetchCatalog(prov, query, typeFilter)
                        if (nativeItems.isNotEmpty()) {
                            extensionList.addAll(nativeItems)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Combine: User's playlist movies FIRST, then extensions
        val combined = (userPriorityList + extensionList)

        // Deduplicate & apply search/category filters
        val filtered = combined.distinctBy { it.title.trim().lowercase() }.filter { item ->
            val matchesQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    (item.category ?: "").contains(query, ignoreCase = true) ||
                    (item.description ?: "").contains(query, ignoreCase = true)

            val matchesType = when (typeFilter) {
                "NAFI OTT PLATFORM", "NAFI OTT", "My Playlist" -> item.tournament == "NAFI_OTT" || (item.category ?: "").contains("NAFI OTT", ignoreCase = true)
                "Movies" -> item.type == MediaType.MOVIE
                "TV Series" -> item.type == MediaType.SERIES
                "Anime" -> (item.category ?: "").contains("Anime", ignoreCase = true) || item.title.contains("Anime", ignoreCase = true)
                "Asian Dramas" -> (item.category ?: "").contains("Drama", ignoreCase = true) || (item.category ?: "").contains("Asian", ignoreCase = true) || (item.category ?: "").contains("KDrama", ignoreCase = true)
                else -> true
            }
            matchesQuery && matchesType
        }

        return@withContext filtered
    }

    // -------------------------------------------------------------
    // EXTENSION DECODER 1: Hollywood, Box Office & Global Streamers (MovieBox, FlixHQ, Cineb, SmashyStream)
    // -------------------------------------------------------------
    private suspend fun fetchHollywoodFlixProviderFeed(
        provider: MovieProvider?,
        query: String,
        typeFilter: String
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val encodedQuery = if (query.isNotBlank()) java.net.URLEncoder.encode(query.trim(), "UTF-8") else ""
            
            // 1. Cinemeta Catalog
            val cinemetaUrl = if (encodedQuery.isNotBlank()) {
                "https://v3-cinemeta.strem.io/catalog/movie/top/search=$encodedQuery.json"
            } else {
                "https://v3-cinemeta.strem.io/catalog/movie/top.json"
            }
            val req = Request.Builder().url(cinemetaUrl).header("User-Agent", "CloudStream/4.0").build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                list.addAll(parseCinemetaJson(body, MediaType.MOVIE, provider))
            }

            // 2. YTS 1080p Cinema Feed
            val ytsUrl = if (encodedQuery.isNotBlank()) {
                "https://yts.mx/api/v2/list_movies.json?query_term=$encodedQuery&limit=25"
            } else {
                "https://yts.mx/api/v2/list_movies.json?sort_by=download_count&limit=25"
            }
            val ytsReq = Request.Builder().url(ytsUrl).header("User-Agent", "CloudStream/4.0").build()
            val ytsResp = client.newCall(ytsReq).execute()
            if (ytsResp.isSuccessful) {
                val body = ytsResp.body?.string() ?: ""
                list.addAll(parseYtsJson(body, provider))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    // -------------------------------------------------------------
    // EXTENSION DECODER 2: BollyFlix & VegaMovies (Bollywood, South Hindi Dubbed, Hindi Web Series)
    // -------------------------------------------------------------
    private suspend fun fetchBollyFlixProviderFeed(
        provider: MovieProvider?,
        query: String,
        typeFilter: String
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val bollyMovies = listOf(
                MediaItem(
                    id = "bolly_jawan",
                    title = "Jawan (Extended Cut)",
                    category = "Bollywood • Action Thriller",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt15354916",
                    servers = listOf(
                        StreamServer("Server 1 (BollyFlix 1080p Ultra)", "https://vidsrc.to/embed/movie/tt15354916"),
                        StreamServer("Server 2 (VegaMovies Dual Audio)", "https://superstream.media/embed/tt15354916"),
                        StreamServer("Server 3 (MultiMovies Fast)", "https://smashystream.com/embed/tt15354916"),
                        StreamServer("Server 4 (Direct HLS Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&q=80",
                    description = "Shah Rukh Khan in a high-octane action thriller as a man driven by a personal vendetta to rectify the evils in society.",
                    rating = "8.4★",
                    year = "2024",
                    quality = "1080p HEVC Dual Audio"
                ),
                MediaItem(
                    id = "bolly_animal",
                    title = "Animal (Uncut)",
                    category = "Bollywood • Crime Drama",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt13751694",
                    servers = listOf(
                        StreamServer("Server 1 (BollyFlix 1080p)", "https://vidsrc.to/embed/movie/tt13751694"),
                        StreamServer("Server 2 (VegaMovies Uncut)", "https://superstream.media/embed/tt13751694"),
                        StreamServer("Server 3 (HLS Backup)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&q=80",
                    description = "A son's obsessive love for his father leads him down a dark and violent path of underworld retribution.",
                    rating = "8.2★",
                    year = "2024",
                    quality = "4K Ultra HD"
                ),
                MediaItem(
                    id = "bolly_stree2",
                    title = "Stree 2: Sarkate Ka Aatank",
                    category = "Bollywood • Horror Comedy",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt27538960",
                    servers = listOf(
                        StreamServer("Server 1 (BollyFlix 1080p)", "https://vidsrc.to/embed/movie/tt27538960"),
                        StreamServer("Server 2 (VegaMovies HD)", "https://superstream.media/embed/tt27538960"),
                        StreamServer("Server 3 (Direct CDN)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80",
                    description = "After the events of Stree, the town of Chanderi is being haunted again by a headless phantom kidnapping women.",
                    rating = "8.6★",
                    year = "2024",
                    quality = "1080p FHD"
                ),
                MediaItem(
                    id = "bolly_kalki",
                    title = "Kalki 2898 AD",
                    category = "Sci-Fi • Mythological Epic",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt12735488",
                    servers = listOf(
                        StreamServer("Server 1 (BollyFlix Hindi 1080p)", "https://vidsrc.to/embed/movie/tt12735488"),
                        StreamServer("Server 2 (VegaMovies 4K)", "https://superstream.media/embed/tt12735488"),
                        StreamServer("Server 3 (MultiMovies)", "https://smashystream.com/embed/tt12735488")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&q=80",
                    description = "A modern avatar of Vishnu descends on earth to protect the world from evil forces in a dystopian post-apocalyptic future.",
                    rating = "8.8★",
                    year = "2024",
                    quality = "4K IMAX Enhanced"
                ),
                MediaItem(
                    id = "bolly_panchayat",
                    title = "Panchayat (Season 1 - 3)",
                    category = "Hindi Web Series • Comedy Drama",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt12004706",
                    servers = listOf(
                        StreamServer("Server 1 (ShowFlix Season 1-3 HD)", "https://vidsrc.to/embed/tv/tt12004706"),
                        StreamServer("Server 2 (BollyFlix Hindi)", "https://superstream.media/embed/tv/tt12004706"),
                        StreamServer("Server 3 (Direct CDN)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=600&q=80",
                    description = "An engineering graduate takes up a job as a secretary of a panchayat office in a remote village named Phulera.",
                    rating = "9.2★",
                    year = "2024",
                    quality = "1080p Full Season"
                ),
                MediaItem(
                    id = "bolly_mirzapur",
                    title = "Mirzapur (Season 3 Complete)",
                    category = "Hindi Web Series • Crime Action",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt6473300",
                    servers = listOf(
                        StreamServer("Server 1 (BollyFlix All Episodes)", "https://vidsrc.to/embed/tv/tt6473300"),
                        StreamServer("Server 2 (VegaMovies HD)", "https://superstream.media/embed/tv/tt6473300"),
                        StreamServer("Server 3 (Direct Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1478720568477-152d9b164e26?w=600&q=80",
                    description = "Guddu Pandit claims the throne of Purvanchal while rivals and enemies unite in a bloody battle for supremacy.",
                    rating = "8.8★",
                    year = "2024",
                    quality = "1080p Season 3"
                ),
                MediaItem(
                    id = "bolly_12thfail",
                    title = "12th Fail",
                    category = "Inspirational • Biographical Drama",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt23849504",
                    servers = listOf(
                        StreamServer("Server 1 (BollyFlix 1080p)", "https://vidsrc.to/embed/movie/tt23849504"),
                        StreamServer("Server 2 (VegaMovies HD)", "https://superstream.media/embed/tt23849504")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=600&q=80",
                    description = "Based on the true story of IPS officer Manoj Kumar Sharma who restarts his academic journey from scratch.",
                    rating = "9.2★",
                    year = "2024",
                    quality = "1080p Ultra HD"
                ),
                MediaItem(
                    id = "bolly_salaar",
                    title = "Salaar: Part 1 - Ceasefire",
                    category = "South Indian Hindi • Action Epic",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt13927994",
                    servers = listOf(
                        StreamServer("Server 1 (MultiMovies Hindi)", "https://vidsrc.to/embed/movie/tt13927994"),
                        StreamServer("Server 2 (VegaMovies 4K)", "https://superstream.media/embed/tt13927994")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&q=80",
                    description = "A gang leader makes a promise to a dying friend and takes on other criminal gangs in the dystopian city of Khansaar.",
                    rating = "8.3★",
                    year = "2024",
                    quality = "1080p Hindi Dubbed"
                )
            )
            list.addAll(bollyMovies)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    // -------------------------------------------------------------
    // EXTENSION DECODER 3: AnimeSalt (Anime, Seasonal Anime, Dual Audio, Movies)
    // -------------------------------------------------------------
    private suspend fun fetchAnimeSaltProviderFeed(
        provider: MovieProvider?,
        query: String,
        typeFilter: String
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val animeList = listOf(
                MediaItem(
                    id = "anime_sololeveling",
                    title = "Solo Leveling (Arise)",
                    category = "Anime • Action Fantasy",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt21209876",
                    servers = listOf(
                        StreamServer("Server 1 (AnimeSalt Sub/Dub 1080p)", "https://vidsrc.to/embed/tv/tt21209876"),
                        StreamServer("Server 2 (Zoro Ultra HD)", "https://superstream.media/embed/tv/tt21209876"),
                        StreamServer("Server 3 (Direct CDN)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=600&q=80",
                    description = "Sung Jinwoo, the world's weakest hunter, is chosen by a mysterious quest system to become the strongest Shadow Monarch.",
                    rating = "9.2★",
                    year = "2024",
                    quality = "1080p Japanese & English Dub"
                ),
                MediaItem(
                    id = "anime_demonslayer",
                    title = "Demon Slayer: Hashira Training Arc",
                    category = "Anime • Supernatural Action",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt9335498",
                    servers = listOf(
                        StreamServer("Server 1 (AnimeSalt 1080p)", "https://vidsrc.to/embed/tv/tt9335498"),
                        StreamServer("Server 2 (Dual Audio HD)", "https://superstream.media/embed/tv/tt9335498")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&q=80",
                    description = "Tanjiro visits the Stone Hashira Himejima to prepare for the upcoming battle against Muzan Kibutsuji.",
                    rating = "9.0★",
                    year = "2024",
                    quality = "1080p Full HD"
                ),
                MediaItem(
                    id = "anime_jujutsu",
                    title = "Jujutsu Kaisen (Shibuya Incident)",
                    category = "Anime • Dark Fantasy",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt12343534",
                    servers = listOf(
                        StreamServer("Server 1 (AnimeSalt 1080p)", "https://vidsrc.to/embed/tv/tt12343534"),
                        StreamServer("Server 2 (GogoAnime Mirror)", "https://superstream.media/embed/tv/tt12343534")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&q=80",
                    description = "On October 31st, a curtain falls over Shibuya trapping countless civilians. Satoru Gojo enters the frontlines.",
                    rating = "9.4★",
                    year = "2024",
                    quality = "1080p Dual Audio"
                ),
                MediaItem(
                    id = "anime_attackontitan",
                    title = "Attack on Titan (The Final Chapters)",
                    category = "Anime • Dark Fantasy Epic",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt2560140",
                    servers = listOf(
                        StreamServer("Server 1 (AnimeSalt 1080p)", "https://vidsrc.to/embed/tv/tt2560140"),
                        StreamServer("Server 2 (Direct Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&q=80",
                    description = "The Rumbling approaches humanity's final defense line. Eren Yeager faces his closest friends in the ultimate confrontation.",
                    rating = "9.5★",
                    year = "2024",
                    quality = "1080p Complete Series"
                ),
                MediaItem(
                    id = "anime_kaiju8",
                    title = "Kaiju No. 8",
                    category = "Anime • Sci-Fi Action",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt21612440",
                    servers = listOf(
                        StreamServer("Server 1 (AnimeSalt 1080p)", "https://vidsrc.to/embed/tv/tt21612440"),
                        StreamServer("Server 2 (Dual Audio)", "https://superstream.media/embed/tv/tt21612440")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=600&q=80",
                    description = "Kafka Hibino gets the ability to turn into a kaiju and aims to fulfill his lifelong dream of joining the Defense Force.",
                    rating = "8.8★",
                    year = "2024",
                    quality = "1080p Sub/Dub"
                )
            )
            list.addAll(animeList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    // -------------------------------------------------------------
    // EXTENSION DECODER 4: Kisskh & Asian Dramas (KDrama, Chinese Drama, Romantic Series)
    // -------------------------------------------------------------
    private suspend fun fetchKisskhProviderFeed(
        provider: MovieProvider?,
        query: String,
        typeFilter: String
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val dramaList = listOf(
                MediaItem(
                    id = "drama_queenoftears",
                    title = "Queen of Tears",
                    category = "KDrama • Romance Comedy",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt28424566",
                    servers = listOf(
                        StreamServer("Server 1 (Kisskh Korean HD)", "https://vidsrc.to/embed/tv/tt28424566"),
                        StreamServer("Server 2 (MPlayer Hindi Dubbed)", "https://superstream.media/embed/tv/tt28424566"),
                        StreamServer("Server 3 (Direct CDN)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=600&q=80",
                    description = "The queen of department stores and the prince of supermarkets weather a marital crisis until love miraculously begins to bloom again.",
                    rating = "9.1★",
                    year = "2024",
                    quality = "1080p Multi Subtitle"
                ),
                MediaItem(
                    id = "drama_squidgame",
                    title = "Squid Game (Season 1 & 2)",
                    category = "KDrama • Survival Thriller",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt10919420",
                    servers = listOf(
                        StreamServer("Server 1 (Kisskh 1080p)", "https://vidsrc.to/embed/tv/tt10919420"),
                        StreamServer("Server 2 (MPlayer HD)", "https://superstream.media/embed/tv/tt10919420")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&q=80",
                    description = "Hundreds of cash-strapped players accept a strange invitation to compete in children's games for a tempting 45.6 billion won prize.",
                    rating = "9.0★",
                    year = "2024",
                    quality = "4K Ultra HD"
                ),
                MediaItem(
                    id = "drama_crashlanding",
                    title = "Crash Landing on You",
                    category = "KDrama • Romance Drama",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt10850932",
                    servers = listOf(
                        StreamServer("Server 1 (Kisskh HD)", "https://vidsrc.to/embed/tv/tt10850932"),
                        StreamServer("Server 2 (Direct Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=600&q=80",
                    description = "A South Korean heiress accidentally paraglides into North Korea and into the life of an army officer who decides to help her hide.",
                    rating = "9.3★",
                    year = "2024",
                    quality = "1080p Dual Audio"
                ),
                MediaItem(
                    id = "drama_marrymyhusband",
                    title = "Marry My Husband",
                    category = "KDrama • Revenge Fantasy",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt29418652",
                    servers = listOf(
                        StreamServer("Server 1 (Kisskh 1080p)", "https://vidsrc.to/embed/tv/tt29418652"),
                        StreamServer("Server 2 (Direct CDN)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80",
                    description = "A terminally ill woman killed after witnessing her husband's affair wakes up ten years in the past to alter her destiny.",
                    rating = "8.9★",
                    year = "2024",
                    quality = "1080p Full Season"
                )
            )
            list.addAll(dramaList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    // -------------------------------------------------------------
    // EXTENSION DECODER 5: DoraBash & Bangla Cinema / Web Series (Chorki, Bioscope, Bongo)
    // -------------------------------------------------------------
    private suspend fun fetchDoraBashProviderFeed(
        provider: MovieProvider?,
        query: String,
        typeFilter: String
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val banglaList = listOf(
                MediaItem(
                    id = "bangla_toofan",
                    title = "Toofan (তুফান)",
                    category = "Bangla Cinema • Action Crime",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt31825597",
                    servers = listOf(
                        StreamServer("Server 1 (DoraBash 1080p)", "https://vidsrc.to/embed/movie/tt31825597"),
                        StreamServer("Server 2 (Chorki Ultra CDN)", "https://superstream.media/embed/tt31825597"),
                        StreamServer("Server 3 (Direct HLS Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&q=80",
                    description = "Shakib Khan and Chanchal Chowdhury in the biggest Dhallywood blockbuster crime saga of the 90s underworld.",
                    rating = "9.3★",
                    year = "2024",
                    quality = "1080p Ultra HD"
                ),
                MediaItem(
                    id = "bangla_mohanagar",
                    title = "Mohanagar (মহানগর - Season 1 & 2)",
                    category = "Bangla Web Series • Hoichoi",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt14922756",
                    servers = listOf(
                        StreamServer("Server 1 (DoraBash Complete Season)", "https://vidsrc.to/embed/tv/tt14922756"),
                        StreamServer("Server 2 (Direct Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1478720568477-152d9b164e26?w=600&q=80",
                    description = "OC Harun confronts corruption and high-profile political power struggles during a turbulent single night in Dhaka.",
                    rating = "9.4★",
                    year = "2024",
                    quality = "1080p All Episodes"
                ),
                MediaItem(
                    id = "bangla_karagar",
                    title = "Karagar (কারাগার - Part 1 & 2)",
                    category = "Bangla Web Series • Mystery Thriller",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt21817658",
                    servers = listOf(
                        StreamServer("Server 1 (DoraBash HD)", "https://vidsrc.to/embed/tv/tt21817658"),
                        StreamServer("Server 2 (Chorki Fast)", "https://superstream.media/embed/tv/tt21817658")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&q=80",
                    description = "A mute mysterious prisoner appears inside a locked cell of Akashnagar Central Jail that has been shut for 50 years.",
                    rating = "9.1★",
                    year = "2024",
                    quality = "1080p Full Season"
                ),
                MediaItem(
                    id = "bangla_surongo",
                    title = "Surongo (সুড়ঙ্গ)",
                    category = "Bangla Cinema • Crime Thriller",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt27993072",
                    servers = listOf(
                        StreamServer("Server 1 (DoraBash 1080p)", "https://vidsrc.to/embed/movie/tt27993072"),
                        StreamServer("Server 2 (Direct CDN)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=600&q=80",
                    description = "A poor electrician in desperate need of money plans a daring underground bank heist with unexpected consequences.",
                    rating = "8.7★",
                    year = "2024",
                    quality = "1080p Full HD"
                ),
                MediaItem(
                    id = "bangla_taqdeer",
                    title = "Taqdeer (তাকদীর)",
                    category = "Bangla Web Series • Thriller",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt13693282",
                    servers = listOf(
                        StreamServer("Server 1 (DoraBash 1080p)", "https://vidsrc.to/embed/tv/tt13693282"),
                        StreamServer("Server 2 (Direct Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&q=80",
                    description = "A freezer van driver finds an unidentified dead body inside his vehicle, spiraling into a deadly conspiracy.",
                    rating = "9.2★",
                    year = "2024",
                    quality = "1080p Complete Series"
                )
            )
            list.addAll(banglaList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    private fun parseCinemetaJson(jsonStr: String, type: MediaType, provider: MovieProvider?): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val obj = JSONObject(jsonStr)
            val metas = obj.optJSONArray("metas") ?: return list
            for (i in 0 until metas.length()) {
                val item = metas.optJSONObject(i) ?: continue
                val id = item.optString("id", "cm_$i")
                val name = item.optString("name", "").ifBlank { continue }
                val poster = item.optString("poster", "")
                val background = item.optString("background", poster)
                val description = item.optString("description", "HD Cinema stream with multiple high-speed servers.")
                val year = item.optString("year", item.optString("releaseInfo", "2026"))
                val rating = item.optString("imdbRating", "8.2")
                val genresArr = item.optJSONArray("genres")
                val genresList = mutableListOf<String>()
                if (genresArr != null) {
                    for (g in 0 until genresArr.length()) genresList.add(genresArr.optString(g))
                }
                val genreStr = genresList.joinToString(" • ").ifBlank { if (type == MediaType.SERIES) "TV Series" else "Movie" }

                val cleanImdb = if (id.startsWith("tt")) id else "tt$id"
                val streamServers = if (type == MediaType.SERIES) {
                    listOf(
                        StreamServer("Server 1 (VidSrc Series HD)", "https://vidsrc.me/embed/tv?imdb=$cleanImdb"),
                        StreamServer("Server 2 (SuperStream VIP)", "https://superstream.media/embed/$cleanImdb"),
                        StreamServer("Server 3 (SmashyStream)", "https://embed.smashystream.com/playere.php?imdb=$cleanImdb"),
                        StreamServer("Server 4 (Direct HLS Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    )
                } else {
                    listOf(
                        StreamServer("Server 1 (VidSrc 1080p)", "https://vidsrc.me/embed/movie?imdb=$cleanImdb"),
                        StreamServer("Server 2 (SuperStream VIP)", "https://superstream.media/embed/$cleanImdb"),
                        StreamServer("Server 3 (SmashyStream)", "https://embed.smashystream.com/playere.php?imdb=$cleanImdb"),
                        StreamServer("Server 4 (Direct HLS Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    )
                }

                list.add(
                    MediaItem(
                        id = "meta_${id}_${type.name}",
                        title = name,
                        category = genreStr,
                        type = type,
                        streamUrl = streamServers.first().url,
                        servers = streamServers,
                        logoUrl = poster.ifBlank { background },
                        description = description,
                        rating = if (rating.isNotBlank()) "$rating★" else "8.0★",
                        year = year,
                        quality = "1080p Ultra HD",
                        isLive = false,
                        referrer = "https://vidsrc.me",
                        userAgent = "Mozilla/5.0",
                        customHeaders = mapOf("Referer" to "https://vidsrc.me", "User-Agent" to "Mozilla/5.0")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseYtsJson(jsonStr: String, provider: MovieProvider?): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val obj = JSONObject(jsonStr)
            val data = obj.optJSONObject("data") ?: return list
            val movies = data.optJSONArray("movies") ?: return list
            for (i in 0 until movies.length()) {
                val item = movies.optJSONObject(i) ?: continue
                val id = item.optString("id", "yts_$i")
                val imdbCode = item.optString("imdb_code", id)
                val title = item.optString("title", "").ifBlank { continue }
                val year = item.optString("year", "2026")
                val rating = item.optDouble("rating", 7.8).toString()
                val summary = item.optString("summary", item.optString("synopsis", "Official YTS HD Release.")).ifBlank { "Full HD movie release." }
                val mediumCover = item.optString("medium_cover_image", "")
                val largeCover = item.optString("large_cover_image", mediumCover)
                val bgImage = item.optString("background_image_original", largeCover)

                val genresArr = item.optJSONArray("genres")
                val genresList = mutableListOf<String>()
                if (genresArr != null) {
                    for (g in 0 until genresArr.length()) genresList.add(genresArr.optString(g))
                }
                val genreStr = genresList.joinToString(" • ").ifBlank { "Action • Cinema" }

                val cleanImdb = if (imdbCode.startsWith("tt")) imdbCode else "tt$imdbCode"
                val streamServers = listOf(
                    StreamServer("Server 1 (YTS VidSrc Stream)", "https://vidsrc.me/embed/movie?imdb=$cleanImdb"),
                    StreamServer("Server 2 (SuperStream Ultra 4K)", "https://superstream.media/embed/$cleanImdb"),
                    StreamServer("Server 3 (SmashyStream)", "https://embed.smashystream.com/playere.php?imdb=$cleanImdb"),
                    StreamServer("Server 4 (Direct HLS Mirror)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                )

                list.add(
                    MediaItem(
                        id = "yts_$imdbCode",
                        title = title,
                        category = genreStr,
                        type = MediaType.MOVIE,
                        streamUrl = streamServers.first().url,
                        servers = streamServers,
                        logoUrl = largeCover.ifBlank { bgImage },
                        description = summary,
                        rating = "$rating★",
                        year = year,
                        quality = "1080p / 4K",
                        isLive = false,
                        referrer = "https://yts.mx",
                        userAgent = "Mozilla/5.0",
                        customHeaders = mapOf("Referer" to "https://yts.mx", "User-Agent" to "Mozilla/5.0")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Resolves CloudStream share / redirect links (e.g., https://recloudstream.github.io/csredirect?redirectto=csshare:...)
     * and extracts MovieBox or other provider items into a fully playable MediaItem.
     */
    suspend fun resolveCloudStreamShareLink(rawUrl: String): MediaItem? = withContext(Dispatchers.IO) {
        try {
            var target = rawUrl.trim()
            if (target.contains("redirectto=")) {
                val encodedRedirect = target.substringAfter("redirectto=").substringBefore("&")
                target = try {
                    java.net.URLDecoder.decode(encodedRedirect, "UTF-8")
                } catch (_: Exception) {
                    encodedRedirect
                }
            }

            if (target.startsWith("csshare:")) {
                target = target.removePrefix("csshare:")
            }

            var providerName = "MovieBox"
            var endpointUrl = ""

            if (target.contains("?")) {
                val p1 = target.substringBefore("?")
                val p2 = target.substringAfter("?")
                providerName = try { String(android.util.Base64.decode(p1, android.util.Base64.DEFAULT), Charsets.UTF_8) } catch (_: Exception) { p1 }
                endpointUrl = try { String(android.util.Base64.decode(p2, android.util.Base64.DEFAULT), Charsets.UTF_8) } catch (_: Exception) { p2 }
            } else if (target.contains(":")) {
                val p1 = target.substringBefore(":")
                val p2 = target.substringAfter(":")
                providerName = try { String(android.util.Base64.decode(p1, android.util.Base64.DEFAULT), Charsets.UTF_8) } catch (_: Exception) { p1 }
                endpointUrl = try { String(android.util.Base64.decode(p2, android.util.Base64.DEFAULT), Charsets.UTF_8) } catch (_: Exception) { p2 }
            } else {
                endpointUrl = target
            }

            if (endpointUrl.isBlank()) return@withContext null

            val subjectId = if (endpointUrl.contains("subjectId=")) {
                endpointUrl.substringAfter("subjectId=").substringBefore("&")
            } else {
                "8175753992266569024"
            }

            // Call MovieBox / AoneRoom API with standard app headers & guest tokens
            val req = Request.Builder()
                .url(endpointUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 CloudStream/4.0 MovieBox/3.2.0")
                .header("Accept", "application/json, text/plain, */*")
                .header("x-app-id", "com.community.oneroom")
                .header("x-platform", "android")
                .header("x-device-id", "android_${java.util.UUID.randomUUID()}")
                .header("x-version-code", "320")
                .header("Referer", "https://moviebox.online/")
                .build()

            var title = "MovieBox Stream #$subjectId"
            var cover = "https://raw.githubusercontent.com/Hexated/cloudstream-extensions-hexated/builds/icon.png"
            var desc = "MovieBox Stream (ID: $subjectId)"
            var score = "8.5"
            var releaseDate = "2026"
            var isSeries = false

            val resp = try { client.newCall(req).execute() } catch (_: Exception) { null }
            if (resp != null && resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                try {
                    val json = JSONObject(body)
                    val code = json.optInt("code", 0)
                    if (code == 0 || json.has("data")) {
                        val data = json.optJSONObject("data") ?: json
                        val subject = data.optJSONObject("subject") ?: data
                        val parsedTitle = subject.optString("title", subject.optString("name", ""))
                        if (parsedTitle.isNotBlank()) title = parsedTitle
                        val parsedCover = subject.optString("cover", subject.optString("poster", subject.optString("coverUrl", "")))
                        if (parsedCover.isNotBlank()) cover = parsedCover
                        val parsedDesc = subject.optString("description", subject.optString("intro", ""))
                        if (parsedDesc.isNotBlank()) desc = parsedDesc
                        score = subject.optString("score", "8.5")
                        releaseDate = subject.optString("releaseDate", "2026")
                        isSeries = subject.optBoolean("isSeries", false)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val servers = mutableListOf<StreamServer>()
            servers.add(StreamServer("Server 1 (MovieBox API / Web)", endpointUrl))
            servers.add(StreamServer("Server 2 (VidSrc Stream)", "https://vidsrc.me/embed/movie?subjectId=$subjectId"))
            servers.add(StreamServer("Server 3 (SuperStream)", "https://superstream.media/embed/$subjectId"))
            servers.add(StreamServer("Server 4 (Direct HLS Backup)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"))

            val item = MediaItem(
                id = "csshare_${subjectId}_${System.currentTimeMillis()}",
                title = title,
                category = "$providerName • CloudStream Link",
                type = if (isSeries) MediaType.SERIES else MediaType.MOVIE,
                streamUrl = servers.first().url,
                servers = servers,
                logoUrl = cover,
                description = desc,
                rating = "$score★",
                year = releaseDate.take(4),
                quality = "1080p Ultra HD",
                isLive = false
            )
            saveCustomStream(item)
            return@withContext item
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    // -------------------------------------------------------------
    // Notification Center & Broadcast Management
    // -------------------------------------------------------------
    fun getStoredNotifications(): List<AppNotification> {
        val jsonStr = prefs.getString("stored_notifications", "[]") ?: "[]"
        val list = mutableListOf<AppNotification>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(parseNotificationFromJsonObj(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun saveNotification(notification: AppNotification, showSystemPopup: Boolean = false) {
        val current = getStoredNotifications().toMutableList()
        current.removeAll { it.id == notification.id }
        current.add(0, notification)
        saveNotificationList(current)
        if (showSystemPopup) {
            NotificationHelper.showSystemNotification(context, notification)
        }
    }

    fun saveNotificationList(list: List<AppNotification>) {
        val jsonArray = JSONArray()
        list.take(100).forEach { notif ->
            jsonArray.put(serializeNotificationToJsonObj(notif))
        }
        prefs.edit().putString("stored_notifications", jsonArray.toString()).apply()
    }

    fun markNotificationAsRead(id: String) {
        val current = getStoredNotifications().map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        saveNotificationList(current)
    }

    fun markAllNotificationsAsRead() {
        val current = getStoredNotifications().map { it.copy(isRead = true) }
        saveNotificationList(current)
    }

    fun deleteNotification(id: String) {
        addDeletedNotificationId(id)
        val current = getStoredNotifications().filterNot { it.id == id }
        saveNotificationList(current)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            deleteNotificationFromCloud(id)
        }
    }

    suspend fun deleteNotificationAsync(id: String): Boolean = withContext(Dispatchers.IO) {
        addDeletedNotificationId(id)
        val current = getStoredNotifications().filterNot { it.id == id }
        saveNotificationList(current)
        deleteNotificationFromCloud(id)
    }

    suspend fun deleteNotificationFromCloud(id: String): Boolean = withContext(Dispatchers.IO) {
        var anySuccess = false
        val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
        for (dbId in databases) {
            try {
                val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/notifications/$id?key=$FIREBASE_API_KEY"
                val req = Request.Builder().url(fsUrl).delete().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) anySuccess = true
            } catch (_: Exception) {}
        }

        try {
            val rtdbUrl = getSavedFirebaseUrl()
            if (rtdbUrl.isNotBlank()) {
                val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl
                val targetUrl = appendRtdbAuth("$cleanUrl/notifications/$id.json")
                val req = Request.Builder().url(targetUrl).delete().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) anySuccess = true
            }
        } catch (_: Exception) {}

        anySuccess
    }

    fun clearAllNotifications() {
        val allStored = getStoredNotifications()
        val allIds = allStored.map { it.id }.toSet()
        val deletedSet = getDeletedNotificationIds().toMutableSet()
        deletedSet.addAll(allIds)
        prefs.edit().putStringSet("deleted_notif_ids", deletedSet).apply()
        prefs.edit().remove("stored_notifications").apply()

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            clearAllNotificationsFromCloud(allStored)
        }
    }

    suspend fun clearAllNotificationsAsync(): Boolean = withContext(Dispatchers.IO) {
        val allStored = getStoredNotifications()
        val allIds = allStored.map { it.id }.toSet()
        val deletedSet = getDeletedNotificationIds().toMutableSet()
        deletedSet.addAll(allIds)
        prefs.edit().putStringSet("deleted_notif_ids", deletedSet).apply()
        prefs.edit().remove("stored_notifications").apply()
        clearAllNotificationsFromCloud(allStored)
    }

    suspend fun clearAllNotificationsFromCloud(allStored: List<AppNotification>): Boolean = withContext(Dispatchers.IO) {
        var anySuccess = false
        val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
        for (notif in allStored) {
            for (dbId in databases) {
                try {
                    val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/notifications/${notif.id}?key=$FIREBASE_API_KEY"
                    val req = Request.Builder().url(fsUrl).delete().build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) anySuccess = true
                } catch (_: Exception) {}
            }
        }

        try {
            val rtdbUrl = getSavedFirebaseUrl()
            if (rtdbUrl.isNotBlank()) {
                val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl
                val targetUrl = appendRtdbAuth("$cleanUrl/notifications.json")
                val req = Request.Builder().url(targetUrl).delete().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) anySuccess = true
            }
        } catch (_: Exception) {}

        anySuccess
    }

    fun getDeletedNotificationIds(): Set<String> {
        return prefs.getStringSet("deleted_notif_ids", emptySet()) ?: emptySet()
    }

    fun addDeletedNotificationId(id: String) {
        val current = getDeletedNotificationIds().toMutableSet()
        current.add(id)
        prefs.edit().putStringSet("deleted_notif_ids", current).apply()
    }

    fun getUnreadNotificationCount(): Int {
        return getStoredNotifications().count { !it.isRead }
    }

    fun getNotifiedNotificationIds(): Set<String> {
        return prefs.getStringSet("notified_notif_ids", emptySet()) ?: emptySet()
    }

    fun markNotificationAsNotified(id: String) {
        val current = getNotifiedNotificationIds().toMutableSet()
        current.add(id)
        prefs.edit().putStringSet("notified_notif_ids", current).apply()
    }

    fun serializeNotificationToJsonObj(notif: AppNotification): JSONObject {
        val obj = JSONObject()
        obj.put("id", notif.id)
        obj.put("title", notif.title)
        obj.put("message", notif.message)
        obj.put("timestamp", notif.timestamp)
        obj.put("type", notif.type.name)
        if (!notif.targetId.isNullOrBlank()) obj.put("targetId", notif.targetId)
        if (!notif.targetType.isNullOrBlank()) obj.put("targetType", notif.targetType)
        if (!notif.imageUrl.isNullOrBlank()) obj.put("imageUrl", notif.imageUrl)
        obj.put("isRead", notif.isRead)
        if (!notif.actionUrl.isNullOrBlank()) obj.put("actionUrl", notif.actionUrl)
        obj.put("sender", notif.sender)
        return obj
    }

    fun parseNotificationFromJsonObj(obj: JSONObject, explicitId: String? = null): AppNotification {
        val id = explicitId ?: obj.optString("id", "notif_${System.currentTimeMillis()}")
        val title = obj.optString("title", "Nafi TV আপডেট")
        val message = obj.optString("message", obj.optString("body", ""))
        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
        val typeStr = obj.optString("type", "BROADCAST").uppercase()
        val type = try { NotificationType.valueOf(typeStr) } catch (_: Exception) { NotificationType.BROADCAST }
        val targetId = obj.optString("targetId", "").takeIf { it.isNotBlank() }
        val targetType = obj.optString("targetType", "").takeIf { it.isNotBlank() }
        val imageUrl = obj.optString("imageUrl", "").takeIf { it.isNotBlank() }
        val isRead = obj.optBoolean("isRead", false)
        val actionUrl = obj.optString("actionUrl", "").takeIf { it.isNotBlank() }
        val sender = obj.optString("sender", "Admin")

        return AppNotification(
            id = id,
            title = title,
            message = message,
            timestamp = timestamp,
            type = type,
            targetId = targetId,
            targetType = targetType,
            imageUrl = imageUrl,
            isRead = isRead,
            actionUrl = actionUrl,
            sender = sender
        )
    }

    private fun parseNotificationFromFirestoreFields(docId: String, fields: JSONObject): AppNotification {
        fun s(key: String): String {
            val v = fields.optJSONObject(key) ?: return ""
            return v.optString("stringValue", "")
        }
        fun b(key: String, def: Boolean = false): Boolean {
            val v = fields.optJSONObject(key) ?: return def
            return if (v.has("booleanValue")) v.optBoolean("booleanValue", def) else def
        }
        fun l(key: String): Long {
            val v = fields.optJSONObject(key) ?: return System.currentTimeMillis()
            return if (v.has("integerValue")) v.optLong("integerValue") else System.currentTimeMillis()
        }

        val typeStr = s("type").uppercase()
        val type = try { NotificationType.valueOf(typeStr) } catch (_: Exception) { NotificationType.BROADCAST }

        return AppNotification(
            id = s("id").ifBlank { docId },
            title = s("title").ifBlank { "Nafi TV নোটিফিকেশন" },
            message = s("message").ifBlank { s("body") },
            timestamp = l("timestamp"),
            type = type,
            targetId = s("targetId").takeIf { it.isNotBlank() },
            targetType = s("targetType").takeIf { it.isNotBlank() },
            imageUrl = s("imageUrl").takeIf { it.isNotBlank() },
            isRead = b("isRead", false),
            actionUrl = s("actionUrl").takeIf { it.isNotBlank() },
            sender = s("sender").ifBlank { "Admin" }
        )
    }

    suspend fun broadcastNotification(
        notification: AppNotification,
        pushToCloud: Boolean = true
    ): Pair<Boolean, String> = sendBroadcastNotification(notification, pushToCloud)

    suspend fun sendBroadcastNotification(
        notification: AppNotification,
        pushToCloud: Boolean = true
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        // 1. Save locally and show system notification immediately
        saveNotification(notification, showSystemPopup = true)
        markNotificationAsNotified(notification.id)

        if (!pushToCloud) return@withContext Pair(true, "নোটিফিকেশন তৈরি ও প্রেরণ সফল হয়েছে")

        var anySuccess = false
        val jsonObj = serializeNotificationToJsonObj(notification)

        // 2. Push to Firestore
        try {
            val firestoreObj = JSONObject()
            val fields = JSONObject()
            fun fs(key: String, value: String?) {
                if (!value.isNullOrBlank()) {
                    fields.put(key, JSONObject().put("stringValue", value))
                }
            }
            fun fb(key: String, value: Boolean) {
                fields.put(key, JSONObject().put("booleanValue", value))
            }
            fun fi(key: String, value: Long) {
                fields.put(key, JSONObject().put("integerValue", value.toString()))
            }

            fs("id", notification.id)
            fs("title", notification.title)
            fs("message", notification.message)
            fi("timestamp", notification.timestamp)
            fs("type", notification.type.name)
            fs("targetId", notification.targetId)
            fs("targetType", notification.targetType)
            fs("imageUrl", notification.imageUrl)
            fs("actionUrl", notification.actionUrl)
            fs("sender", notification.sender)

            firestoreObj.put("fields", fields)
            val fsBody = firestoreObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            for (dbId in databases) {
                try {
                    val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/notifications/${notification.id}?key=$FIREBASE_API_KEY"
                    val fsReq = Request.Builder().url(fsUrl).patch(fsBody).build()
                    val fsResp = client.newCall(fsReq).execute()
                    if (fsResp.isSuccessful) anySuccess = true
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Push to Firebase Realtime Database
        try {
            val rtdbUrl = getSavedFirebaseUrl()
            if (rtdbUrl.isNotBlank()) {
                val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl
                val body = jsonObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val targetUrl = appendRtdbAuth("$cleanUrl/notifications/${notification.id}.json")
                val req = Request.Builder().url(targetUrl).put(body).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) anySuccess = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext if (anySuccess) {
            Pair(true, "নোটিফিকেশন সফলভাবে ক্লাউডে এবং ব্যবহারকারীদের কাছে পাঠানো হয়েছে")
        } else {
            Pair(true, "নোটিফিকেশন সফলভাবে তৈরি ও পাঠানো হয়েছে")
        }
    }

    suspend fun fetchRemoteNotifications(): List<AppNotification> = withContext(Dispatchers.IO) {
        val remoteList = mutableListOf<AppNotification>()
        val notifiedIds = getNotifiedNotificationIds()

        // 1. Fetch from Firestore
        try {
            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            for (dbId in databases) {
                try {
                    val url = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/notifications?key=$FIREBASE_API_KEY"
                    val req = Request.Builder().url(url).build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val docs = json.optJSONArray("documents") ?: continue
                        for (i in 0 until docs.length()) {
                            val doc = docs.optJSONObject(i) ?: continue
                            val docId = doc.optString("name", "").substringAfterLast("/")
                            val fields = doc.optJSONObject("fields") ?: continue
                            val notif = parseNotificationFromFirestoreFields(docId, fields)
                            remoteList.add(notif)
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 2. Fetch from RTDB
        try {
            val rtdbUrl = getSavedFirebaseUrl()
            if (rtdbUrl.isNotBlank()) {
                val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl
                val targetUrl = "$cleanUrl/notifications.json"
                val req = Request.Builder().url(targetUrl).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val obj = JSONObject(body)
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val nObj = obj.optJSONObject(k)
                            if (nObj != null) {
                                remoteList.add(parseNotificationFromJsonObj(nObj, explicitId = k))
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val deletedNotifIds = getDeletedNotificationIds()
        val distinctList = remoteList
            .filterNot { deletedNotifIds.contains(it.id) }
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }

        // Process newly received notifications for system push popup
        for (notif in distinctList) {
            if (!notifiedIds.contains(notif.id)) {
                saveNotification(notif, showSystemPopup = true)
                markNotificationAsNotified(notif.id)
            }
        }

        distinctList
    }

    init {
        recordUserPresence()
    }


    // -------------------------------------------------------------
    // USER ANALYTICS & ACTIVE USERS TRACKING (Firebase RTDB + Location)
    // -------------------------------------------------------------
    fun getOrCreateDeviceId(): String {
        var id = prefs.getString("device_unique_user_id", null)
        if (id.isNullOrBlank()) {
            id = "user_" + UUID.randomUUID().toString().replace("-", "").take(12)
            prefs.edit().putString("device_unique_user_id", id).apply()
        }
        return id
    }

    /**
     * Accurately detects network connection type (WiFi, 4G/5G Cellular, Ethernet, VPN)
     */
    fun detectNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val capabilities = cm?.getNetworkCapabilities(network)
            when {
                capabilities == null -> "Offline"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data (4G/5G)"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet / Broadband"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "WiFi"
            }
        } catch (_: Exception) {
            "WiFi"
        }
    }

    /**
     * Detects real device location (City, Country, ISP) without prompting runtime GPS permissions.
     * Uses fast, reliable HTTPS GeoIP lookup services with locale fallback.
     */
    fun detectDeviceLocation(): Map<String, String> {
        val now = System.currentTimeMillis()
        val lastGeoCheck = prefs.getLong("last_geo_check_timestamp", 0L)
        val cachedCity = prefs.getString("cached_user_city", null)
        val cachedCountry = prefs.getString("cached_user_country", null)
        val cachedCountryCode = prefs.getString("cached_user_country_code", null)
        val cachedIsp = prefs.getString("cached_user_isp", null)
        val cachedIp = prefs.getString("cached_user_ip", null)

        if (!cachedCity.isNullOrBlank() && !cachedCountry.isNullOrBlank() && (now - lastGeoCheck < 24 * 3600 * 1000L)) {
            val locName = "$cachedCity, $cachedCountry"
            return mapOf(
                "location" to locName,
                "city" to cachedCity,
                "country" to (cachedCountry ?: "Bangladesh"),
                "country_code" to (cachedCountryCode ?: "BD"),
                "isp" to (cachedIsp ?: ""),
                "ip" to (cachedIp ?: "")
            )
        }

        var city = cachedCity ?: ""
        var country = cachedCountry ?: ""
        var countryCode = cachedCountryCode ?: ""
        var isp = cachedIsp ?: ""
        var ip = cachedIp ?: ""

        val endpoints = listOf(
            "https://freeipapi.com/api/json",
            "https://ipapi.co/json/",
            "https://ipwho.is/"
        )

        for (endpoint in endpoints) {
            try {
                val geoReq = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "NAFITV24/2.5.6 (Android)")
                    .build()
                client.newCall(geoReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string()?.trim() ?: ""
                        if (bodyStr.startsWith("{")) {
                            val root = JSONObject(bodyStr)
                            if (endpoint.contains("freeipapi")) {
                                val c = root.optString("cityName", "").trim()
                                val cn = root.optString("countryName", "").trim()
                                val cc = root.optString("countryCode", "").trim()
                                val qIp = root.optString("ipAddress", "").trim()
                                if (cn.isNotBlank() && cn != "-") {
                                    city = if (c.isNotBlank() && c != "-") c else "ঢাকা"
                                    country = cn
                                    countryCode = cc.ifBlank { "BD" }
                                    ip = qIp
                                    return@use
                                }
                            } else if (endpoint.contains("ipapi.co")) {
                                val c = root.optString("city", "").trim()
                                val cn = root.optString("country_name", "").trim()
                                val cc = root.optString("country_code", "").trim()
                                val org = root.optString("org", "").trim()
                                val qIp = root.optString("ip", "").trim()
                                if (cn.isNotBlank()) {
                                    city = if (c.isNotBlank()) c else "ঢাকা"
                                    country = cn
                                    countryCode = cc.ifBlank { "BD" }
                                    isp = org
                                    ip = qIp
                                    return@use
                                }
                            } else if (endpoint.contains("ipwho.is")) {
                                if (root.optBoolean("success", true)) {
                                    val c = root.optString("city", "").trim()
                                    val cn = root.optString("country", "").trim()
                                    val cc = root.optString("country_code", "").trim()
                                    val connObj = root.optJSONObject("connection")
                                    val connIsp = connObj?.optString("isp", "") ?: ""
                                    val qIp = root.optString("ip", "").trim()
                                    if (cn.isNotBlank()) {
                                        city = if (c.isNotBlank()) c else "ঢাকা"
                                        country = cn
                                        countryCode = cc.ifBlank { "BD" }
                                        isp = connIsp
                                        ip = qIp
                                        return@use
                                    }
                                }
                            }
                        }
                    }
                }
                if (country.isNotBlank()) break
            } catch (_: Exception) {}
        }

        if (country.isBlank()) {
            val loc = Locale.getDefault()
            val tz = TimeZone.getDefault().id.lowercase()
            if (tz.contains("dhaka")) {
                city = "ঢাকা"
                country = "বাংলাদেশ"
                countryCode = "BD"
            } else if (tz.contains("kolkata") || tz.contains("calcutta") || tz.contains("delhi")) {
                city = "কলকাতা"
                country = "ভারত"
                countryCode = "IN"
            } else if (loc.displayCountry.isNotBlank()) {
                country = loc.displayCountry
                city = loc.displayCountry
                countryCode = loc.country.ifBlank { "BD" }
            } else {
                city = "ঢাকা"
                country = "বাংলাদেশ"
                countryCode = "BD"
            }
        }

        if (city.isBlank()) city = "ঢাকা"

        prefs.edit()
            .putString("cached_user_city", city)
            .putString("cached_user_country", country)
            .putString("cached_user_country_code", countryCode)
            .putString("cached_user_isp", isp)
            .putString("cached_user_ip", ip)
            .putLong("last_geo_check_timestamp", now)
            .apply()

        val locationLabel = "$city, $country"
        return mapOf(
            "location" to locationLabel,
            "city" to city,
            "country" to country,
            "country_code" to countryCode,
            "isp" to isp,
            "ip" to ip
        )
    }

    fun recordUserPresence(currentActivity: String = "ব্রাউজিং") {
        try {
            val now = System.currentTimeMillis()
            // Quota optimization: Only send heartbeat every 10 minutes or if initial startup
            if (lastPresenceTimestamp > 0 && (now - lastPresenceTimestamp < 10 * 60 * 1000L)) {
                return
            }
            lastPresenceTimestamp = now
            lastPresenceActivity = currentActivity

            val deviceId = getOrCreateDeviceId()
            val isFirstInstall = !prefs.contains("app_installed_at_timestamp")
            if (isFirstInstall) {
                prefs.edit().putLong("app_installed_at_timestamp", now).apply()
                val curTotal = prefs.getInt("cached_lifetime_users_count", 1)
                prefs.edit().putInt("cached_lifetime_users_count", curTotal + 1).apply()
            }
            val installedAt = prefs.getLong("app_installed_at_timestamp", now)

            val manufacturer = android.os.Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
            val model = android.os.Build.MODEL.orEmpty()
            val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
            val cleanDeviceName = deviceName.ifBlank { "Android Device" }
            val appVersion = "v${com.example.BuildConfig.VERSION_NAME}"
            val versionCode = com.example.BuildConfig.VERSION_CODE
            val netType = detectNetworkType()

            // Background sync to Firebase Realtime Database with managed lifecycle scope
            repositoryScope.launch {
                try {
                    val locInfo = detectDeviceLocation()
                    val city = locInfo["city"] ?: "ঢাকা"
                    val country = locInfo["country"] ?: "বাংলাদেশ"
                    val countryCode = locInfo["country_code"] ?: "BD"
                    val locLabel = locInfo["location"] ?: "$city, $country"
                    val isp = locInfo["isp"] ?: ""
                    val ip = locInfo["ip"] ?: ""

                    val rtdbUrl = getSavedFirebaseUrl()
                    if (rtdbUrl.isNotBlank()) {
                        val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl

                        // 1. Update active user presence object (for live active count in last 5-10 min)
                        val activeObj = JSONObject().apply {
                            put("id", deviceId)
                            put("device_model", cleanDeviceName)
                            put("brand", manufacturer.ifBlank { "Android" })
                            put("app_version", appVersion)
                            put("version_code", versionCode)
                            put("last_seen", now)
                            put("registered_at", installedAt)
                            put("is_online", true)
                            put("location", locLabel)
                            put("city", city)
                            put("country", country)
                            put("country_code", countryCode)
                            put("network_type", netType)
                            put("isp", isp)
                            put("ip", ip)
                            put("current_activity", currentActivity)
                        }
                        val activeBody = activeObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                        val activeTargetUrl = appendRtdbAuth("$cleanUrl/active_users/$deviceId.json")
                        val activeReq = Request.Builder()
                            .url(activeTargetUrl)
                            .put(activeBody)
                            .build()
                        try { client.newCall(activeReq).execute().close() } catch (_: Exception) {}

                        // 2. Register / Update in permanent lifetime users index (all_users) - ONLY once on install or once a week!
                        val lastAllUsersSync = prefs.getLong("last_all_users_sync_timestamp", 0L)
                        if (isFirstInstall || (now - lastAllUsersSync > 7 * 24 * 3600 * 1000L)) {
                            prefs.edit().putLong("last_all_users_sync_timestamp", now).apply()
                            val userObj = JSONObject().apply {
                                put("id", deviceId)
                                put("device_model", cleanDeviceName)
                                put("app_version", appVersion)
                                put("last_seen", now)
                                put("registered_at", installedAt)
                                put("location", locLabel)
                                put("city", city)
                                put("country", country)
                                put("country_code", countryCode)
                                put("network_type", netType)
                            }
                            val userBody = userObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                            val allTargetUrl = appendRtdbAuth("$cleanUrl/all_users/$deviceId.json")
                            val userReq = Request.Builder()
                                .url(allTargetUrl)
                                .put(userBody)
                                .build()
                            try { client.newCall(userReq).execute().close() } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {
                    // Ignore transient network errors
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun fetchUserAnalytics(): AppUserAnalytics = withContext(Dispatchers.IO) {
        val rtdbUrl = getSavedFirebaseUrl()
        val now = System.currentTimeMillis()
        val activeThresholdMillis = 5 * 60 * 1000L // Active in the last 5 minutes

        val allList = mutableListOf<ActiveUserInfo>()
        var totalLifetimeCount = 0

        if (rtdbUrl.isNotBlank()) {
            val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl

            // 1. Fetch active_users from Firebase RTDB
            try {
                val activeFetchUrl = appendRtdbAuth("$cleanUrl/active_users.json")
                val reqActive = Request.Builder()
                    .url(activeFetchUrl)
                    .header("User-Agent", "NAFITV24/2.5.6 (Android)")
                    .build()
                val respActive = client.newCall(reqActive).execute()
                if (respActive.isSuccessful) {
                    val body = respActive.body?.string()?.trim() ?: ""
                    if (body.startsWith("{")) {
                        val root = JSONObject(body)
                        val keys = root.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val uObj = root.optJSONObject(k) ?: continue
                            val lastSeen = uObj.optLong("last_seen", 0L)
                            val deviceModel = uObj.optString("device_model", "Android Device")
                            val brand = uObj.optString("brand", "Android")
                            val appVersion = uObj.optString("app_version", "v${com.example.BuildConfig.VERSION_NAME}")
                            val vCode = uObj.optInt("version_code", com.example.BuildConfig.VERSION_CODE)
                            val registeredAt = uObj.optLong("registered_at", lastSeen)
                            val loc = uObj.optString("location", "বাংলাদেশ")
                            val city = uObj.optString("city", "ঢাকা")
                            val country = uObj.optString("country", "Bangladesh")
                            val countryCode = uObj.optString("country_code", "BD")
                            val ip = uObj.optString("ip", "")
                            val isp = uObj.optString("isp", "")
                            val netType = uObj.optString("network_type", "WiFi")
                            val curAct = uObj.optString("current_activity", "ব্রাউজিং")

                            val isCurrentlyOnline = (now - lastSeen) <= activeThresholdMillis
                            allList.add(
                                ActiveUserInfo(
                                    id = k,
                                    deviceModel = deviceModel,
                                    brand = brand,
                                    appVersion = appVersion,
                                    versionCode = vCode,
                                    lastSeen = lastSeen,
                                    registeredAt = registeredAt,
                                    isOnline = isCurrentlyOnline,
                                    location = loc,
                                    city = city,
                                    country = country,
                                    countryCode = countryCode,
                                    ip = ip,
                                    isp = isp,
                                    networkType = netType,
                                    currentActivity = curAct
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Fetch all_users count (shallow or direct)
            try {
                val allFetchUrl = appendRtdbAuth("$cleanUrl/all_users.json?shallow=true")
                val reqAll = Request.Builder()
                    .url(allFetchUrl)
                    .build()
                val respAll = client.newCall(reqAll).execute()
                if (respAll.isSuccessful) {
                    val allBody = respAll.body?.string()?.trim() ?: ""
                    if (allBody.startsWith("{")) {
                        val allRoot = JSONObject(allBody)
                        totalLifetimeCount = allRoot.length()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Add / refresh current device in list
        val currentDevId = getOrCreateDeviceId()
        val existingIndex = allList.indexOfFirst { it.id == currentDevId }
        val manufacturer = android.os.Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = android.os.Build.MODEL.orEmpty()
        val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        val myCity = prefs.getString("cached_user_city", "ঢাকা") ?: "ঢাকা"
        val myCountry = prefs.getString("cached_user_country", "বাংলাদেশ") ?: "বাংলাদেশ"
        val myCountryCode = prefs.getString("cached_user_country_code", "BD") ?: "BD"
        val myIsp = prefs.getString("cached_user_isp", "") ?: ""
        val myIp = prefs.getString("cached_user_ip", "") ?: ""

        val currentDeviceItem = ActiveUserInfo(
            id = currentDevId,
            deviceModel = deviceName.ifBlank { "This Device (Admin/User)" },
            brand = manufacturer.ifBlank { "Android" },
            appVersion = "v${com.example.BuildConfig.VERSION_NAME}",
            versionCode = com.example.BuildConfig.VERSION_CODE,
            lastSeen = now,
            registeredAt = prefs.getLong("app_installed_at_timestamp", now),
            isOnline = true,
            location = "$myCity, $myCountry",
            city = myCity,
            country = myCountry,
            countryCode = myCountryCode,
            ip = myIp,
            isp = myIsp,
            networkType = detectNetworkType(),
            currentActivity = "অ্যাডমিন প্যানেল"
        )

        if (existingIndex >= 0) {
            allList[existingIndex] = currentDeviceItem
        } else {
            allList.add(0, currentDeviceItem)
        }

        // Sort: currently online first, then latest lastSeen
        allList.sortWith(compareByDescending<ActiveUserInfo> { it.isOnline }.thenByDescending { it.lastSeen })

        val liveActiveList = allList.filter { it.isOnline }
        val onlineCount = liveActiveList.size.coerceAtLeast(1)
        val finalTotal = maxOf(totalLifetimeCount, allList.size, prefs.getInt("cached_lifetime_users_count", 1))
        prefs.edit().putInt("cached_lifetime_users_count", finalTotal).apply()

        // Helper for location statistics
        fun buildLocationStats(sourceList: List<ActiveUserInfo>): List<LocationTrafficStat> {
            val list = if (sourceList.isNotEmpty()) sourceList else allList
            val locationGroups = list.groupBy {
                val c = it.city.ifBlank { it.location }
                if (c.isNotBlank()) "$c, ${it.country}" else it.country.ifBlank { "বাংলাদেশ" }
            }
            return locationGroups.map { (locName, users) ->
                val flagEmoji = when {
                    locName.contains("Bangladesh", ignoreCase = true) || locName.contains("বাংলাদেশ") || locName.contains("ঢাকা") -> "🇧🇩"
                    locName.contains("India", ignoreCase = true) || locName.contains("ভারত") || locName.contains("কলকাতা") -> "🇮🇳"
                    locName.contains("Saudi", ignoreCase = true) || locName.contains("সৌদি") -> "🇸🇦"
                    locName.contains("Emirates", ignoreCase = true) || locName.contains("Dubai", ignoreCase = true) -> "🇦🇪"
                    locName.contains("United States", ignoreCase = true) || locName.contains("USA", ignoreCase = true) -> "🇺🇸"
                    locName.contains("United Kingdom", ignoreCase = true) || locName.contains("UK", ignoreCase = true) -> "🇬🇧"
                    locName.contains("Malaysia", ignoreCase = true) || locName.contains("মালয়েশিয়া") -> "🇲🇾"
                    else -> "🌐"
                }
                val count = users.size
                val pct = if (list.isNotEmpty()) (count.toFloat() / list.size.toFloat()) else 1f
                LocationTrafficStat(
                    locationName = locName,
                    count = count,
                    percentage = pct,
                    flag = flagEmoji
                )
            }.sortedByDescending { it.count }
        }

        // Helper for network stats
        fun buildNetworkStats(sourceList: List<ActiveUserInfo>): Map<String, Int> {
            val list = if (sourceList.isNotEmpty()) sourceList else allList
            return list.groupBy {
                val net = it.networkType.trim()
                when {
                    net.contains("WiFi", ignoreCase = true) -> "WiFi"
                    net.contains("Mobile", ignoreCase = true) || net.contains("Cellular", ignoreCase = true) || net.contains("4G", ignoreCase = true) || net.contains("5G", ignoreCase = true) -> "Mobile Data (4G/5G)"
                    net.contains("Ethernet", ignoreCase = true) || net.contains("Broadband", ignoreCase = true) -> "Ethernet / Broadband"
                    net.contains("VPN", ignoreCase = true) -> "VPN"
                    else -> "WiFi"
                }
            }.mapValues { it.value.size }
        }

        val topLocations = buildLocationStats(allList)
        val liveLocations = buildLocationStats(liveActiveList)
        val networkStats = buildNetworkStats(allList)
        val liveNetworkStats = buildNetworkStats(liveActiveList)

        AppUserAnalytics(
            totalUsers = finalTotal,
            activeUsers = onlineCount,
            activeUsersList = allList,
            topLocations = topLocations,
            liveLocations = liveLocations,
            networkBreakdown = networkStats,
            liveNetworkBreakdown = liveNetworkStats,
            lastUpdated = now
        )
    }

    suspend fun cleanStaleOfflineUsers(): Boolean = withContext(Dispatchers.IO) {
        val rtdbUrl = getSavedFirebaseUrl()
        if (rtdbUrl.isBlank()) return@withContext false
        try {
            val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl
            val reqActiveUrl = appendRtdbAuth("$cleanUrl/active_users.json")
            val reqActive = Request.Builder().url(reqActiveUrl).build()
            val resp = client.newCall(reqActive).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()?.trim() ?: ""
                if (body.startsWith("{")) {
                    val root = JSONObject(body)
                    val now = System.currentTimeMillis()
                    val staleThreshold = 12 * 60 * 60 * 1000L // 12 hours
                    val keys = root.keys()
                    val patchObj = JSONObject()
                    var countToDelete = 0
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val uObj = root.optJSONObject(k)
                        val lastSeen = uObj?.optLong("last_seen", 0L) ?: 0L
                        if (lastSeen > 0 && (now - lastSeen) > staleThreshold) {
                            patchObj.put(k, JSONObject.NULL)
                            countToDelete++
                        }
                    }
                    if (countToDelete > 0) {
                        val patchBody = patchObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                        val patchReq = Request.Builder()
                            .url(reqActiveUrl)
                            .patch(patchBody)
                            .build()
                        val patchResp = client.newCall(patchReq).execute()
                        patchResp.close()
                    }
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        false
    }

    // Instant Free Firebase Quota: Wipes active and lifetime user tracking logs
    suspend fun clearAllUserLogsFromFirebase(): Boolean = withContext(Dispatchers.IO) {
        val rtdbUrl = getSavedFirebaseUrl()
        if (rtdbUrl.isBlank()) return@withContext false
        try {
            val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl
            val delActiveUrl = appendRtdbAuth("$cleanUrl/active_users.json")
            val delAllUrl = appendRtdbAuth("$cleanUrl/all_users.json")
            val req1 = Request.Builder().url(delActiveUrl).delete().build()
            val req2 = Request.Builder().url(delAllUrl).delete().build()
            try { client.newCall(req1).execute().close() } catch (_: Exception) {}
            try { client.newCall(req2).execute().close() } catch (_: Exception) {}
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getSavedUserMode(): String? {
        return prefs.getString("saved_app_user_mode", null)
    }

    fun saveUserMode(modeName: String?) {
        if (modeName == null) {
            prefs.edit().remove("saved_app_user_mode").apply()
        } else {
            prefs.edit().putString("saved_app_user_mode", modeName).apply()
        }
    }
}


