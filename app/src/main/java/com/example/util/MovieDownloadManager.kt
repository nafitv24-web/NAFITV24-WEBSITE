package com.example.util

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.service.MovieDownloadService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class DownloadState {
    IDLE,
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class MovieDownloadProgress(
    val movieId: String,
    val title: String,
    val progress: Float = 0f, // 0.0 to 1.0
    val progressPercent: Int = 0, // 0 to 100
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedSizeFormatted: String = "0 MB",
    val totalSizeFormatted: String = "0 MB",
    val speedFormatted: String = "0 KB/s",
    val state: DownloadState = DownloadState.IDLE,
    val errorMessage: String? = null
)

data class DownloadedMovie(
    val id: String,
    val title: String,
    val category: String = "Movie",
    val logoUrl: String? = null,
    val year: String? = null,
    val quality: String = "HD",
    val downloadUrl: String = "",
    val localFilePath: String = "",
    val fileSizeBytes: Long = 0L,
    val fileSizeFormatted: String = "0 MB",
    val downloadDateFormatted: String = "",
    val downloadTimestamp: Long = System.currentTimeMillis()
) {
    val fileExists: Boolean get() = localFilePath.isNotBlank() && File(localFilePath).exists()

    fun toMediaItem(): MediaItem {
        val path = localFilePath.trim()
        val playUri = if (path.startsWith("/")) "file://$path" else path
        return MediaItem(
            id = "offline_$id",
            title = "🎬 $title (Offline)",
            category = "📥 ডাউনলোডসমূহ",
            type = MediaType.MOVIE,
            streamUrl = playUri,
            logoUrl = logoUrl,
            description = "অফলাইন লোকাল ফাইল • সাইজ: $fileSizeFormatted • ডাউনলোডের তারিখ: $downloadDateFormatted",
            quality = quality,
            year = year,
            isLive = false
        )
    }
}

object MovieDownloadManager {

    private const val PREFS_NAME = "nafi_movie_downloads_prefs"
    private const val KEY_DOWNLOADED_MOVIES = "key_downloaded_movies_json"
    private const val NOTIF_CHANNEL_ID = "nafi_movie_download_channel"
    private const val NOTIF_CHANNEL_NAME = "মুভি ডাউনলোড নোটিফিকেশন"

    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()
    private val activeProgressMap = ConcurrentHashMap<String, MovieDownloadProgress>()

    private val _downloadsState = MutableStateFlow<Map<String, MovieDownloadProgress>>(emptyMap())
    val downloadsState: StateFlow<Map<String, MovieDownloadProgress>> = _downloadsState.asStateFlow()

    private val _downloadedMoviesFlow = MutableStateFlow<List<DownloadedMovie>>(emptyList())
    val downloadedMoviesFlow: StateFlow<List<DownloadedMovie>> = _downloadedMoviesFlow.asStateFlow()

    // Ultra-optimized High-Throughput HTTP Client for maximum download speed
    private val downloadHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(64, 10, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        createNotificationChannel(context)
        refreshDownloadedMoviesList(context)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "মুভি ডাউনলোডের লাইভ প্রগ্রেস ও সম্পন্ন নোটিফিকেশন"
                enableVibration(false)
                setShowBadge(false)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun getDownloadedMovies(context: Context): List<DownloadedMovie> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_DOWNLOADED_MOVIES, null) ?: return emptyList()
        val list = mutableListOf<DownloadedMovie>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val item = DownloadedMovie(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    category = obj.optString("category", "Movie"),
                    logoUrl = obj.optString("logoUrl").takeIf { it.isNotBlank() },
                    year = obj.optString("year").takeIf { it.isNotBlank() },
                    quality = obj.optString("quality", "HD"),
                    downloadUrl = obj.optString("downloadUrl"),
                    localFilePath = obj.optString("localFilePath"),
                    fileSizeBytes = obj.optLong("fileSizeBytes"),
                    fileSizeFormatted = obj.optString("fileSizeFormatted", "0 MB"),
                    downloadDateFormatted = obj.optString("downloadDateFormatted"),
                    downloadTimestamp = obj.optLong("downloadTimestamp", System.currentTimeMillis())
                )
                // Only include if file actually exists on storage
                if (item.fileExists) {
                    list.add(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.downloadTimestamp }
    }

    fun refreshDownloadedMoviesList(context: Context) {
        val movies = getDownloadedMovies(context)
        _downloadedMoviesFlow.value = movies
    }

    private fun saveDownloadedMovie(context: Context, movie: DownloadedMovie) {
        val currentList = getDownloadedMovies(context).toMutableList()
        currentList.removeAll { it.id == movie.id }
        currentList.add(0, movie)

        val arr = JSONArray()
        for (item in currentList) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("category", item.category)
                put("logoUrl", item.logoUrl ?: "")
                put("year", item.year ?: "")
                put("quality", item.quality)
                put("downloadUrl", item.downloadUrl)
                put("localFilePath", item.localFilePath)
                put("fileSizeBytes", item.fileSizeBytes)
                put("fileSizeFormatted", item.fileSizeFormatted)
                put("downloadDateFormatted", item.downloadDateFormatted)
                put("downloadTimestamp", item.downloadTimestamp)
            }
            arr.put(obj)
        }
        getPrefs(context).edit().putString(KEY_DOWNLOADED_MOVIES, arr.toString()).apply()
        _downloadedMoviesFlow.value = currentList
    }

    fun isMovieDownloaded(context: Context, movieId: String): Boolean {
        val movies = _downloadedMoviesFlow.value.ifEmpty { getDownloadedMovies(context) }
        return movies.any { it.id == movieId && it.fileExists }
    }

    fun getDownloadedMovie(context: Context, movieId: String): DownloadedMovie? {
        val movies = _downloadedMoviesFlow.value.ifEmpty { getDownloadedMovies(context) }
        return movies.firstOrNull { it.id == movieId && it.fileExists }
    }

    fun getDownloadedFile(context: Context, movieId: String): File? {
        val movie = getDownloadedMovie(context, movieId) ?: return null
        if (movie.localFilePath.isBlank()) return null
        val file = File(movie.localFilePath)
        return if (file.exists()) file else null
    }

    fun isMovieDownloading(movieId: String): Boolean {
        val prog = activeProgressMap[movieId]
        return prog?.state == DownloadState.DOWNLOADING || prog?.state == DownloadState.PENDING
    }

    fun getMovieDownloadProgress(movieId: String): MovieDownloadProgress? {
        return activeProgressMap[movieId]
    }

    fun deleteDownloadedMovie(context: Context, movieId: String): Boolean {
        try {
            val currentList = getDownloadedMovies(context).toMutableList()
            val target = currentList.firstOrNull { it.id == movieId }
            if (target != null) {
                val file = File(target.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
                currentList.removeAll { it.id == movieId }

                val arr = JSONArray()
                for (item in currentList) {
                    val obj = JSONObject().apply {
                        put("id", item.id)
                        put("title", item.title)
                        put("category", item.category)
                        put("logoUrl", item.logoUrl ?: "")
                        put("year", item.year ?: "")
                        put("quality", item.quality)
                        put("downloadUrl", item.downloadUrl)
                        put("localFilePath", item.localFilePath)
                        put("fileSizeBytes", item.fileSizeBytes)
                        put("fileSizeFormatted", item.fileSizeFormatted)
                        put("downloadDateFormatted", item.downloadDateFormatted)
                        put("downloadTimestamp", item.downloadTimestamp)
                    }
                    arr.put(obj)
                }
                getPrefs(context).edit().putString(KEY_DOWNLOADED_MOVIES, arr.toString()).apply()
                _downloadedMoviesFlow.value = currentList
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun cancelDownload(movieId: String) {
        activeDownloadJobs[movieId]?.cancel()
        activeDownloadJobs.remove(movieId)
        activeProgressMap[movieId] = MovieDownloadProgress(
            movieId = movieId,
            title = activeProgressMap[movieId]?.title ?: "",
            state = DownloadState.CANCELLED
        )
        _downloadsState.value = HashMap(activeProgressMap)
    }

    /**
     * Start downloading a movie file with maximum speed buffers, multi-connection pooling,
     * background foreground service persistence, and real-time speed monitoring.
     */
    fun startDownload(
        context: Context,
        mediaItem: MediaItem,
        preferredUrl: String? = null,
        onStarted: (() -> Unit)? = null,
        onComplete: ((DownloadedMovie) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val targetUrl = preferredUrl?.trim()?.takeIf { it.isNotBlank() } ?: mediaItem.streamUrl.trim()

        if (targetUrl.isBlank()) {
            val err = "মুভির ডাউনলোড লিংক পাওয়া যায়নি!"
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            onError?.invoke(err)
            return
        }

        if (isMovieDownloading(mediaItem.id)) {
            Toast.makeText(context, "এই মুভিটি ইতিমধ্যে ডাউনলোড হচ্ছে...", Toast.LENGTH_SHORT).show()
            return
        }

        if (isMovieDownloaded(context, mediaItem.id)) {
            Toast.makeText(context, "মুভিটি ইতিমধ্যে আপনার অফলাইন ডিভাইসে ডাউনলোড করা আছে!", Toast.LENGTH_SHORT).show()
            return
        }

        val notifId = (mediaItem.id.hashCode() and 0x7FFFFFFF)
        val initialProgress = MovieDownloadProgress(
            movieId = mediaItem.id,
            title = mediaItem.title,
            state = DownloadState.PENDING
        )
        activeProgressMap[mediaItem.id] = initialProgress
        _downloadsState.value = HashMap(activeProgressMap)

        // Launch Foreground Service to ensure download never dies when user exits app
        MovieDownloadService.startService(context)

        Toast.makeText(context, "📥 '${mediaItem.title}' দ্রুত ডাউনলোড শুরু হচ্ছে...", Toast.LENGTH_SHORT).show()
        onStarted?.invoke()

        val job = coroutineScope.launch {
            var targetFile: File? = null

            try {
                val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: File(context.filesDir, "movies").apply { mkdirs() }
                if (!moviesDir.exists()) moviesDir.mkdirs()

                val safeTitle = mediaItem.title
                    .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    .take(40)

                val ext = when {
                    targetUrl.contains(".mp4", ignoreCase = true) -> ".mp4"
                    targetUrl.contains(".mkv", ignoreCase = true) -> ".mkv"
                    targetUrl.contains(".webm", ignoreCase = true) -> ".webm"
                    targetUrl.contains(".ts", ignoreCase = true) -> ".ts"
                    targetUrl.contains(".mov", ignoreCase = true) -> ".mov"
                    targetUrl.contains(".avi", ignoreCase = true) -> ".avi"
                    else -> ".mp4"
                }

                val fileName = "NAFITV_${mediaItem.id.take(8)}_$safeTitle$ext"
                targetFile = File(moviesDir, fileName)
                if (targetFile.exists()) {
                    targetFile.delete()
                }

                val createRequestBuilder: () -> Request.Builder = {
                    val builder = Request.Builder()
                        .url(targetUrl)
                        .addHeader("User-Agent", mediaItem.userAgent ?: "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .addHeader("Accept", "*/*")
                        .addHeader("Connection", "keep-alive")
                    mediaItem.referrer?.let { builder.addHeader("Referer", it) }
                    mediaItem.cookie?.let { builder.addHeader("Cookie", it) }
                    mediaItem.origin?.let { builder.addHeader("Origin", it) }
                    mediaItem.customHeaders?.forEach { (k, v) -> builder.addHeader(k, v) }
                    builder
                }

                val probeRequest = createRequestBuilder()
                    .addHeader("Accept-Encoding", "identity")
                    .build()

                val response = downloadHttpClient.newCall(probeRequest).execute()
                if (!response.isSuccessful) {
                    throw Exception("সার্ভার রেসপন্স কোড: ${response.code}")
                }

                val body = response.body ?: throw Exception("সার্ভার থেকে কোনো ডাটা পাওয়া যায়নি")
                val totalBytes = body.contentLength()
                val totalFormatted = if (totalBytes > 0) formatBytes(totalBytes) else "অজানা সাইজ"

                val acceptRanges = response.header("Accept-Ranges")
                val supportsRanges = (acceptRanges != null && acceptRanges.contains("bytes", ignoreCase = true)) || (totalBytes > 8 * 1024 * 1024)

                showProgressNotification(context, notifId, mediaItem.title, 0, "ডাউনলোড শুরু হচ্ছে...")

                val totalDownloadedAtomic = AtomicLong(0L)
                var lastUiUpdate = System.currentTimeMillis()
                var lastBytesForSpeed = 0L
                var lastSpeedCalcTime = System.currentTimeMillis()
                var currentSpeed = "0 KB/s"

                val numWorkers = if (supportsRanges && totalBytes >= 8 * 1024 * 1024) 4 else 1

                if (numWorkers > 1) {
                    response.close() // Release probe stream

                    // Pre-allocate file
                    val rafInit = RandomAccessFile(targetFile, "rw")
                    try {
                        rafInit.setLength(totalBytes)
                    } finally {
                        rafInit.close()
                    }

                    val chunkSize = totalBytes / numWorkers
                    val workerJobs = (0 until numWorkers).map { workerIndex ->
                        val startByte = workerIndex * chunkSize
                        val endByte = if (workerIndex == numWorkers - 1) totalBytes - 1 else (workerIndex + 1) * chunkSize - 1

                        async(Dispatchers.IO) {
                            val rangeRequest = createRequestBuilder()
                                .addHeader("Range", "bytes=$startByte-$endByte")
                                .addHeader("Accept-Encoding", "identity")
                                .build()

                            val segResponse = downloadHttpClient.newCall(rangeRequest).execute()
                            if (!segResponse.isSuccessful && segResponse.code != 206) {
                                throw Exception("Segment $workerIndex error: ${segResponse.code}")
                            }

                            val segBody = segResponse.body ?: throw Exception("Segment $workerIndex empty body")
                            val segStream = BufferedInputStream(segBody.byteStream(), 256 * 1024)
                            val raf = RandomAccessFile(targetFile, "rw")
                            raf.seek(startByte)

                            val segBuffer = ByteArray(256 * 1024)
                            var readBytes: Int
                            try {
                                while (segStream.read(segBuffer).also { readBytes = it } != -1) {
                                    if (!isActive) throw CancellationException()
                                    raf.write(segBuffer, 0, readBytes)
                                    totalDownloadedAtomic.addAndGet(readBytes.toLong())
                                }
                            } finally {
                                try { raf.close() } catch (_: Exception) {}
                                try { segStream.close() } catch (_: Exception) {}
                                try { segBody.close() } catch (_: Exception) {}
                            }
                        }
                    }

                    while (workerJobs.any { it.isActive }) {
                        if (!isActive) {
                            workerJobs.forEach { it.cancel() }
                            throw CancellationException("ডাউনলোড বাতিল করা হয়েছে")
                        }

                        val curDownloaded = totalDownloadedAtomic.get()
                        val now = System.currentTimeMillis()
                        if (now - lastSpeedCalcTime >= 800) {
                            val bytesInPeriod = curDownloaded - lastBytesForSpeed
                            val timePeriodSec = (now - lastSpeedCalcTime) / 1000.0
                            val speedBytesPerSec = if (timePeriodSec > 0) (bytesInPeriod / timePeriodSec).toLong() else 0L
                            currentSpeed = formatSpeed(speedBytesPerSec)
                            lastBytesForSpeed = curDownloaded
                            lastSpeedCalcTime = now
                        }

                        if (now - lastUiUpdate >= 250) {
                            lastUiUpdate = now
                            val progressFloat = (curDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            val progressPercent = (progressFloat * 100).toInt()
                            val downloadedFormatted = formatBytes(curDownloaded)

                            val prog = MovieDownloadProgress(
                                movieId = mediaItem.id,
                                title = mediaItem.title,
                                progress = progressFloat,
                                progressPercent = progressPercent,
                                downloadedBytes = curDownloaded,
                                totalBytes = totalBytes,
                                downloadedSizeFormatted = downloadedFormatted,
                                totalSizeFormatted = totalFormatted,
                                speedFormatted = currentSpeed,
                                state = DownloadState.DOWNLOADING
                            )
                            activeProgressMap[mediaItem.id] = prog
                            _downloadsState.value = HashMap(activeProgressMap)

                            showProgressNotification(
                                context = context,
                                notifId = notifId,
                                title = mediaItem.title,
                                progress = progressPercent,
                                statusText = "$downloadedFormatted / $totalFormatted • ⚡ $currentSpeed ($progressPercent%)"
                            )
                        }
                        delay(200)
                    }

                    workerJobs.awaitAll()

                } else {
                    // Ultra-fast 512KB single-stream buffer pipeline
                    val bufferSize = 512 * 1024
                    val inputStream = BufferedInputStream(body.byteStream(), bufferSize)
                    val outputStream = BufferedOutputStream(FileOutputStream(targetFile), bufferSize)

                    val buffer = ByteArray(bufferSize)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    try {
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) {
                                throw CancellationException("ডাউনলোড বাতিল করা হয়েছে")
                            }

                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastSpeedCalcTime >= 800) {
                                val bytesInPeriod = totalBytesRead - lastBytesForSpeed
                                val timePeriodSec = (now - lastSpeedCalcTime) / 1000.0
                                val speedBytesPerSec = if (timePeriodSec > 0) (bytesInPeriod / timePeriodSec).toLong() else 0L
                                currentSpeed = formatSpeed(speedBytesPerSec)
                                lastBytesForSpeed = totalBytesRead
                                lastSpeedCalcTime = now
                            }

                            if (now - lastUiUpdate >= 250 || (totalBytes > 0 && totalBytesRead == totalBytes)) {
                                lastUiUpdate = now
                                val progressFloat = if (totalBytes > 0) (totalBytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                                val progressPercent = (progressFloat * 100).toInt()
                                val downloadedFormatted = formatBytes(totalBytesRead)

                                val prog = MovieDownloadProgress(
                                    movieId = mediaItem.id,
                                    title = mediaItem.title,
                                    progress = progressFloat,
                                    progressPercent = progressPercent,
                                    downloadedBytes = totalBytesRead,
                                    totalBytes = totalBytes,
                                    downloadedSizeFormatted = downloadedFormatted,
                                    totalSizeFormatted = totalFormatted,
                                    speedFormatted = currentSpeed,
                                    state = DownloadState.DOWNLOADING
                                )
                                activeProgressMap[mediaItem.id] = prog
                                _downloadsState.value = HashMap(activeProgressMap)

                                showProgressNotification(
                                    context = context,
                                    notifId = notifId,
                                    title = mediaItem.title,
                                    progress = progressPercent,
                                    statusText = "$downloadedFormatted / $totalFormatted • ⚡ $currentSpeed ($progressPercent%)"
                                )
                            }
                        }
                        outputStream.flush()
                    } finally {
                        try { outputStream.close() } catch (_: Exception) {}
                        try { inputStream.close() } catch (_: Exception) {}
                        try { body.close() } catch (_: Exception) {}
                    }
                }

                // Finalize Downloaded Movie Record
                val actualFileSize = targetFile.length()
                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val downloadedMovie = DownloadedMovie(
                    id = mediaItem.id,
                    title = mediaItem.title,
                    category = mediaItem.category,
                    logoUrl = mediaItem.logoUrl,
                    year = mediaItem.year,
                    quality = mediaItem.quality,
                    downloadUrl = targetUrl,
                    localFilePath = targetFile.absolutePath,
                    fileSizeBytes = actualFileSize,
                    fileSizeFormatted = formatBytes(actualFileSize),
                    downloadDateFormatted = dateFormat.format(Date()),
                    downloadTimestamp = System.currentTimeMillis()
                )

                saveDownloadedMovie(context, downloadedMovie)

                val completedProg = MovieDownloadProgress(
                    movieId = mediaItem.id,
                    title = mediaItem.title,
                    progress = 1f,
                    progressPercent = 100,
                    downloadedBytes = actualFileSize,
                    totalBytes = actualFileSize,
                    downloadedSizeFormatted = formatBytes(actualFileSize),
                    totalSizeFormatted = formatBytes(actualFileSize),
                    speedFormatted = "সম্পন্ন",
                    state = DownloadState.COMPLETED
                )
                activeProgressMap[mediaItem.id] = completedProg
                _downloadsState.value = HashMap(activeProgressMap)

                showCompletedNotification(context, notifId, mediaItem.title, formatBytes(actualFileSize))

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "✅ '${mediaItem.title}' ডাউনলোড সফল হয়েছে!", Toast.LENGTH_LONG).show()
                    onComplete?.invoke(downloadedMovie)
                }

            } catch (e: CancellationException) {
                targetFile?.delete()
                cancelNotification(context, notifId)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "ডাউনলোড বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                targetFile?.delete()

                val failProg = MovieDownloadProgress(
                    movieId = mediaItem.id,
                    title = mediaItem.title,
                    state = DownloadState.FAILED,
                    errorMessage = e.message ?: "ডাউনলোড ব্যর্থ হয়েছে"
                )
                activeProgressMap[mediaItem.id] = failProg
                _downloadsState.value = HashMap(activeProgressMap)

                showFailedNotification(context, notifId, mediaItem.title, e.message)

                withContext(Dispatchers.Main) {
                    val errMsg = "মুভি ডাউনলোডে ত্রুটি: ${e.localizedMessage ?: "নেটওয়ার্ক সমস্যা"}"
                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                    onError?.invoke(errMsg)
                }
            } finally {
                activeDownloadJobs.remove(mediaItem.id)
            }
        }

        activeDownloadJobs[mediaItem.id] = job
    }

    /**
     * Fallback to System DownloadManager / External browser if stream is protected or direct browser download requested
     */
    fun openExternalOrSystemDownload(context: Context, mediaItem: MediaItem, customUrl: String? = null) {
        try {
            val url = customUrl?.trim()?.takeIf { it.isNotBlank() } ?: mediaItem.streamUrl.trim()
            if (url.isBlank()) {
                Toast.makeText(context, "ডাউনলোড লিংক নেই!", Toast.LENGTH_SHORT).show()
                return
            }

            val safeTitle = mediaItem.title.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("NAFI TV 24: ${mediaItem.title}")
                setDescription("মুভি ডাউনলোড হচ্ছে...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "NAFITV_${safeTitle}.mp4")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                mediaItem.userAgent?.let { addRequestHeader("User-Agent", it) }
                mediaItem.referrer?.let { addRequestHeader("Referer", it) }
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                dm.enqueue(request)
                Toast.makeText(context, "📥 সিস্টেম ডাউনলোড ম্যানেজারে যুক্ত হয়েছে...", Toast.LENGTH_SHORT).show()
            } else {
                openBrowserDownload(context, url)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            openBrowserDownload(context, mediaItem.streamUrl)
        }
    }

    fun openBrowserDownload(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "ব্রাউজারে ডাউনলোড লিংক খোলা হচ্ছে...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "ব্রাউজার খোলা সম্ভব হয়নি: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showProgressNotification(
        context: Context,
        notifId: Int,
        title: String,
        progress: Int,
        statusText: String
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("মুভি ডাউনলোড হচ্ছে: $title")
                .setContentText(statusText)
                .setProgress(100, progress, progress <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: Exception) {}
    }

    private fun showCompletedNotification(
        context: Context,
        notifId: Int,
        title: String,
        fileSizeFormatted: String
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("✅ ডাউনলোড সম্পন্ন: $title")
                .setContentText("সাইজ: $fileSizeFormatted • অফলাইনে দেখার জন্য প্রস্তুত")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: Exception) {}
    }

    private fun showFailedNotification(
        context: Context,
        notifId: Int,
        title: String,
        error: String?
    ) {
        try {
            val builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("❌ ডাউনলোড ব্যর্থ: $title")
                .setContentText(error ?: "সার্ভার বা নেটওয়ার্ক সমস্যা")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: Exception) {}
    }

    private fun cancelNotification(context: Context, notifId: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(notifId)
        } catch (_: Exception) {}
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.2f MB/s", mb)
        } else {
            String.format(Locale.US, "%.0f KB/s", kb)
        }
    }

    fun getTotalDownloadedStorageSize(context: Context): String {
        val list = getDownloadedMovies(context)
        val totalBytes = list.sumOf { it.fileSizeBytes }
        return formatBytes(totalBytes)
    }
}
