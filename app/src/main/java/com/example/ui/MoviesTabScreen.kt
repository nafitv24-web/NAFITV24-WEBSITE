package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.MediaItem

import com.example.ui.components.NafiLogoLoadingView

@Composable
fun MoviesTabScreen(
    movies: List<MediaItem>,
    favoriteIds: Set<String>,
    isLoading: Boolean = false,
    isTvMode: Boolean = false,
    onSelectMedia: (MediaItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenOfflineDownloads: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember(movies) {
        val unique = movies.map { it.category.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            .distinct()
            .sorted()
        listOf("All", "ডাউনলোডসমূহ") + unique
    }

    // Identify Featured Spotlight Movies (Trending & new movies with posters that slide horizontally to the left)
    val featuredMovies = remember(movies) {
        val withLogos = movies.filter { !it.logoUrl.isNullOrBlank() }
        if (withLogos.isNotEmpty()) withLogos.take(10) else movies.take(8)
    }

    // Group movies by category for the categorized carousels view (guarantee unique IDs to prevent list jank)
    val categorizedMovies = remember(movies) {
        val uniqueCats = movies.map { it.category.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            .distinct()
        uniqueCats.map { cat ->
            val catMovies = movies.filter { it.category.trim().equals(cat, ignoreCase = true) }.distinctBy { it.id }
            cat to catMovies
        }
    }

    // Filtered movies when search query is active or a single category is selected
    val filteredMovies = remember(movies, searchQuery, selectedCategory, favoriteIds) {
        if (selectedCategory == "ডাউনলোডসমূহ") {
            emptyList()
        } else {
            movies.filter { movie ->
                val matchesSearch = if (searchQuery.isBlank()) true else {
                    movie.title.contains(searchQuery, ignoreCase = true) ||
                            movie.category.contains(searchQuery, ignoreCase = true) ||
                            (movie.description != null && movie.description.contains(searchQuery, ignoreCase = true))
                }
                val matchesCategory = when (selectedCategory) {
                    "All" -> true
                    "❤️ Favorites" -> favoriteIds.contains(movie.id)
                    else -> movie.category.trim().equals(selectedCategory.trim(), ignoreCase = true)
                }
                matchesSearch && matchesCategory
            }.distinctBy { it.id }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617))
    ) {
        // TOP SEARCH BAR & DOWNLOAD BUTTON (Matching Screenshot 1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "মুভি ও ওয়েব সিরিজ খুঁজুন (যেমন: Jawan, Toofan, Leo)",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = Color.White
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A)
                ),
                singleLine = true
            )

            // Right Download Square Button
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                modifier = Modifier
                    .size(50.dp)
                    .clickable { onOpenOfflineDownloads() }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Downloads",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // CATEGORY FILTER CHIPS ROW (Horizontal Scroll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                var isCatFocused by remember { mutableStateOf(false) }

                val isDownloadTab = cat == "ডাউনলোডসমূহ"

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        isDownloadTab -> Color(0xFF0F766E)
                        isSelected -> Color(0xFF2563EB)
                        else -> Color(0xFF1E293B)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isCatFocused -> Color(0xFFFFD600)
                            isDownloadTab -> Color(0xFF14B8A6)
                            isSelected -> Color(0xFF3B82F6)
                            else -> Color(0xFF334155)
                        }
                    ),
                    modifier = Modifier
                        .onFocusChanged { isCatFocused = it.isFocused }
                        .focusable()
                        .clickable {
                            if (isDownloadTab) {
                                onOpenOfflineDownloads()
                            } else {
                                selectedCategory = cat
                            }
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        if (isDownloadTab) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = cat,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected || isDownloadTab) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // MAIN CONTENT AREA
        if (isLoading && movies.isEmpty()) {
            NafiLogoLoadingView(
                title = "মুভি ও ওয়েব সিরিজ লোড হচ্ছে...",
                subtitle = "অনুগ্রহ করে অপেক্ষা করুন, মুভি ক্যাটালগ প্রস্তুত হচ্ছে...",
                modifier = Modifier.fillMaxSize()
            )
        } else if (movies.isEmpty() && selectedCategory == "All" && searchQuery.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "কোনো মুভি বা সিরিজ পাওয়া যায়নি",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "মুভি এম৩ইউ অথবা ক্লাউড থেকে লোড করা হচ্ছে না। মেনু থেকে চেক করুন।",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (searchQuery.isBlank() && selectedCategory == "All") {
            // HOME / ALL VIEW: FEATURED BANNER + CATEGORY CAROUSELS (Matching Screenshot 1)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. FEATURED SPOTLIGHT BANNER SLIDER / CAROUSEL (নতুন মুভি স্বয়ংক্রিয়ভাবে বাম দিকে আসা যাওয়া করবে)
                if (featuredMovies.isNotEmpty()) {
                    item {
                        val pagerState = rememberPagerState(pageCount = { featuredMovies.size })

                        // Auto-sliding loop smoothly scrolling to the next movie banner every 6 seconds (pauses during user interaction)
                        LaunchedEffect(pagerState.pageCount) {
                            if (pagerState.pageCount > 1) {
                                while (true) {
                                    kotlinx.coroutines.delay(6000L)
                                    if (!pagerState.isScrollInProgress) {
                                        val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
                                        pagerState.animateScrollToPage(nextPage)
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                            ) { page ->
                                val currentMovie = featuredMovies[page]
                                var isBannerFocused by remember { mutableStateOf(false) }

                                val bannerModifier = if (isTvMode) {
                                    val bannerScale by animateFloatAsState(
                                        targetValue = if (isBannerFocused) 1.02f else 1.0f,
                                        animationSpec = tween(150, easing = FastOutSlowInEasing),
                                        label = "bannerScale_$page"
                                    )
                                    Modifier
                                        .scale(bannerScale)
                                        .fillMaxSize()
                                        .onFocusChanged { isBannerFocused = it.isFocused }
                                        .focusable()
                                        .clickable { onSelectMedia(currentMovie) }
                                } else {
                                    Modifier
                                        .fillMaxSize()
                                        .clickable { onSelectMedia(currentMovie) }
                                }

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isTvMode && isBannerFocused) Color(0xFFFFD600) else Color(0xFF1E293B)
                                    ),
                                    shadowElevation = if (isTvMode && isBannerFocused) 6.dp else 2.dp,
                                    modifier = bannerModifier
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        // Background Banner Image
                                        if (!currentMovie.logoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = currentMovie.logoUrl,
                                                contentDescription = currentMovie.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                                                        )
                                                    )
                                            )
                                        }

                                        // Dark Gradient Overlays for High Contrast
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.Black.copy(alpha = 0.2f),
                                                            Color.Black.copy(alpha = 0.5f),
                                                            Color.Black.copy(alpha = 0.95f)
                                                        )
                                                    )
                                                )
                                        )

                                        // Top-Start Corner Active Badge on Banner
                                        val isBannerActive = com.example.util.ChannelStatusManager.isChannelActive(currentMovie)
                                        if (isBannerActive) {
                                            Surface(
                                                shape = RoundedCornerShape(topStart = 16.dp, bottomEnd = 8.dp),
                                                color = Color(0xFF064E3B).copy(alpha = 0.95f),
                                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.8f)),
                                                modifier = Modifier.align(Alignment.TopStart)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF10B981))
                                                    )
                                                    Text(
                                                        text = "সচল",
                                                        color = Color(0xFF6EE7B7),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // Banner Content
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(14.dp),
                                            verticalArrangement = Arrangement.Bottom
                                        ) {
                                            // Main Title (Marquee effect if long title)
                                            Text(
                                                text = currentMovie.title,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                            )

                                            Spacer(modifier = Modifier.height(2.dp))

                                            // Subtitle / Description
                                            Text(
                                                text = currentMovie.description?.takeIf { it.isNotBlank() }
                                                    ?: "এইচডি কোয়ালিটিতে সম্পূর্ণ মুভি/সিরিজ উপভোগ করুন।",
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 11.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // Sliding Dots Indicator for Carousel
                            if (featuredMovies.size > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(featuredMovies.size) { index ->
                                        val isSelected = pagerState.currentPage == index
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 3.dp)
                                                .height(5.dp)
                                                .width(if (isSelected) 18.dp else 5.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(if (isSelected) Color(0xFF00E5FF) else Color(0xFF475569).copy(alpha = 0.6f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. CATEGORIZED HORIZONTAL CAROUSELS
                items(categorizedMovies, key = { it.first }) { (categoryName, catMovies) ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Section Header: "🎬 NAFI OTT • BANGLA" ----- "সব (15)"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🎬 $categoryName",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                            }

                            Text(
                                text = "সব (${catMovies.size})",
                                color = Color(0xFF00E5FF),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { selectedCategory = categoryName }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            )
                        }

                        // Horizontal Poster Row
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(catMovies, key = { it.id }) { movie ->
                                MoviePosterCard(
                                    movie = movie,
                                    isFav = favoriteIds.contains(movie.id),
                                    isTvMode = isTvMode,
                                    onSelect = { onSelectMedia(movie) },
                                    onToggleFav = { onToggleFavorite(movie.id) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // FILTERED OR SEARCH VIEW (Grid Layout)
            if (filteredMovies.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "কোনো মুভি বা সিরিজ পাওয়া যায়নি",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "অন্য কি-ওয়ার্ড দিয়ে সার্চ করুন অথবা ক্যাটাগরি পরিবর্তন করুন।",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTvMode) 5 else 3),
                    contentPadding = PaddingValues(horizontal = if (isTvMode) 14.dp else 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isTvMode) 12.dp else 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTvMode) 14.dp else 10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredMovies, key = { it.id }) { movie ->
                        MoviePosterCard(
                            movie = movie,
                            isFav = favoriteIds.contains(movie.id),
                            isTvMode = isTvMode,
                            onSelect = { onSelectMedia(movie) },
                            onToggleFav = { onToggleFavorite(movie.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper to determine the movie language for badge display
 * (e.g., "বাংলা", "হিন্দি", "হিন্দি ডাবড", "তামিল", "তেলেগু", "ইংরেজি", etc.)
 * Returns "NFT" when no specific language is confirmed.
 */
fun getMovieLanguageBadge(movie: MediaItem): String {
    // Remove network/playlist tags like "BDIX", "NAFI OTT" before scanning language
    val cleanCategory = movie.category.replace("BDIX", "", ignoreCase = true)
        .replace("NAFI OTT", "", ignoreCase = true)
        .replace("NAFI_OTT", "", ignoreCase = true)
    val cleanTitle = movie.title.replace("BDIX", "", ignoreCase = true)
    val cleanDesc = (movie.description ?: "").replace("BDIX", "", ignoreCase = true)

    val textToScan = "$cleanCategory $cleanTitle $cleanDesc".lowercase()

    return when {
        // Specific Dubbed Tags
        textToScan.contains("bangla dubbed") || textToScan.contains("bangla-dubbed") || textToScan.contains("বাংলা ডাবড") || textToScan.contains("বাংলা ডাবিং") -> "বাংলা ডাবড"
        textToScan.contains("hindi dubbed") || textToScan.contains("hindi-dubbed") || textToScan.contains("dubbed in hindi") || textToScan.contains("হিন্দি ডাবড") || textToScan.contains("হিন্দি ডাবিং") -> "হিন্দি ডাবড"
        textToScan.contains("tamil dubbed") || textToScan.contains("তামিল ডাবড") -> "তামিল ডাবড"
        textToScan.contains("telugu dubbed") || textToScan.contains("তেলেগু ডাবড") -> "তেলেগু ডাবড"
        textToScan.contains("english dubbed") || textToScan.contains("ইংরেজি ডাবড") -> "ইংরেজি ডাবড"

        // Tamil / Telugu / Malayalam / Kannada / South
        textToScan.contains("tamil") || textToScan.contains("তামিল") || textToScan.contains("kollywood") -> "তামিল"
        textToScan.contains("telugu") || textToScan.contains("তেলেগু") || textToScan.contains("tollywood") -> "তেলেগু"
        textToScan.contains("malayalam") || textToScan.contains("মালায়ালাম") || textToScan.contains("mollywood") -> "মালায়ালাম"
        textToScan.contains("kannada") || textToScan.contains("কন্নড়") || textToScan.contains("sandalwood") -> "কন্নড়"
        textToScan.contains("south movie") || textToScan.contains("south indian") || textToScan.contains("সাউথ") -> "সাউথ"

        // Hindi / Bollywood / Hindi Shows
        textToScan.contains("hindi") || textToScan.contains("হিন্দি") || textToScan.contains("bollywood") ||
                textToScan.contains("khatron ke khiladi") || textToScan.contains("kapil show") ||
                textToScan.contains("bigg boss") || textToScan.contains("indian idol") ||
                textToScan.contains("dance deewane") || textToScan.contains("roadies") ||
                textToScan.contains("splitsvilla") || textToScan.contains("anupamaa") ||
                textToScan.contains("tmkoc") || textToScan.contains("taarak mehta") -> "হিন্দি"

        // Bangla / Dhallywood / Tollywood Bangla
        textToScan.contains("bangla") || textToScan.contains("bengali") || textToScan.contains("বাংলা") ||
                textToScan.contains("dhallywood") || textToScan.contains("bangladesh") ||
                textToScan.contains("hoichoi") || textToScan.contains("chorki") ||
                textToScan.contains("kolkata") || textToScan.contains("নাটক") || textToScan.contains("natok") -> "বাংলা"

        // Korean / K-Drama
        textToScan.contains("korean") || textToScan.contains("k-drama") || textToScan.contains("kdrama") ||
                textToScan.contains("k drama") || textToScan.contains("কোরিয়ান") || textToScan.contains("korea") -> "কোরিয়ান"

        // Anime / Japanese
        textToScan.contains("anime") || textToScan.contains("japanese") || textToScan.contains("অ্যানিমে") ||
                textToScan.contains("japan") -> "অ্যানিমে"

        // Turkish
        textToScan.contains("turkish") || textToScan.contains("তুর্কি") || textToScan.contains("turkey") ||
                textToScan.contains("kurulus") || textToScan.contains("ertugrul") || textToScan.contains("osman") -> "তুর্কি"

        // English / Hollywood
        textToScan.contains("english") || textToScan.contains("ইংরেজি") || textToScan.contains("hollywood") ||
                textToScan.contains("marvel") || textToScan.contains("dc comics") || textToScan.contains("hbo") ||
                textToScan.contains("netflix original") -> "ইংরেজি"

        // If no explicit tag or language pattern is matched, show NFT
        else -> "NFT"
    }
}

@Composable
fun MoviePosterCard(
    movie: MediaItem,
    isFav: Boolean,
    isTvMode: Boolean,
    onSelect: () -> Unit,
    onToggleFav: () -> Unit
) {
    var isCardFocused by remember { mutableStateOf(false) }

    val langBadge = remember(movie.id, movie.title, movie.category, movie.description) {
        getMovieLanguageBadge(movie)
    }

    val cardModifier = if (isTvMode) {
        val cardScale by animateFloatAsState(
            targetValue = if (isCardFocused) 1.06f else 1.0f,
            animationSpec = tween(150, easing = FastOutSlowInEasing),
            label = "movieCardScale"
        )
        Modifier
            .scale(cardScale)
            .width(140.dp)
            .onFocusChanged { isCardFocused = it.isFocused }
            .focusable()
            .clickable { onSelect() }
    } else {
        Modifier
            .width(115.dp)
            .clickable { onSelect() }
    }

    Column(
        modifier = cardModifier
    ) {
        // Poster Box (Full poster display with badge & favorite button)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isTvMode && isCardFocused) Color(0xFF1E293B) else Color(0xFF0F172A),
            border = when {
                isTvMode && isCardFocused -> BorderStroke(2.5.dp, Color(0xFFFFD600))
                isFav -> BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.7f))
                else -> BorderStroke(1.dp, Color(0xFF1E293B))
            },
            shadowElevation = if (isTvMode && isCardFocused) 6.dp else 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTvMode) 195.dp else 160.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Poster Image
                if (!movie.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = movie.logoUrl,
                        contentDescription = movie.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Top Left: Language Badge
                Row(
                    modifier = Modifier.align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Top Left Language Badge (বাংলা / হিন্দি / ইংরেজি etc.)
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            bottomEnd = 8.dp,
                            topEnd = 4.dp,
                            bottomStart = 4.dp
                        ),
                        color = when (langBadge) {
                            "বাংলা", "বাংলা ডাবড" -> Color(0xFF059669)
                            "হিন্দি", "হিন্দি ডাবড" -> Color(0xFFD97706)
                            "ইংরেজি" -> Color(0xFF2563EB)
                            "তামিল", "তেলেগু", "মালায়ালাম", "সাউথ" -> Color(0xFFEA580C)
                            "কোরিয়ান" -> Color(0xFF7C3AED)
                            "অ্যানিমে" -> Color(0xFFDB2777)
                            "তুর্কি" -> Color(0xFF0D9488)
                            else -> Color(0xFF6366F1)
                        }
                    ) {
                        Text(
                            text = langBadge,
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Top Right Favorite Button
                IconButton(
                    onClick = onToggleFav,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFav) Color(0xFFEF4444) else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Movie Title situated clearly below the poster card
        Text(
            text = movie.title,
            color = if (isCardFocused) Color(0xFFFFD600) else Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            lineHeight = 15.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )
    }
}
