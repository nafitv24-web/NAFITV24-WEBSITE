package com.example.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.example.data.MediaRepository
import com.example.model.MediaItem
import com.example.model.PlaylistInfo
import com.example.util.ChannelStatusManager
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun PlaylistTabScreen(
    playlists: List<PlaylistInfo>,
    repository: MediaRepository,
    isTvMode: Boolean = false,
    onSelectMedia: (MediaItem, List<MediaItem>) -> Unit,
    onPlaylistsChanged: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<PlaylistInfo?>(null) }
    var playlistChannels by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var channelSearchQuery by remember { mutableStateOf("") }
    var isLoadingChannels by remember { mutableStateOf(false) }
    var showOnlyActive by rememberSaveable { mutableStateOf(ChannelStatusManager.isOnlyActiveEnabled()) }

    val statusTick by ChannelStatusManager.statusUpdateTick.collectAsState()

    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var showXtreamDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(showOnlyActive) {
        ChannelStatusManager.setOnlyActiveEnabled(showOnlyActive)
    }

    LaunchedEffect(selectedPlaylist) {
        val pl = selectedPlaylist
        if (pl != null) {
            isLoadingChannels = true
            channelSearchQuery = ""
            try {
                playlistChannels = repository.fetchPlaylistChannels(pl)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingChannels = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617))
    ) {
        if (selectedPlaylist == null) {
            // ==================== PLAYLISTS HOME VIEW ====================

            // 1. TOP HEADER (Matching Screenshot 2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF0284C7).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "IPTV Playlists",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${playlists.size} টি প্লেলিস্ট সংরক্ষিত",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                    }
                }

                // "+ প্লেলিস্ট যোগ করুন" Button
                Button(
                    onClick = { showAddPlaylistDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "প্লেলিস্ট যোগ করুন",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 2. XTREAM CODES API & M3U SUPPORT BANNER (Matching Screenshot 2)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clickable { showXtreamDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF0D9488).copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF2DD4BF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Xtream Codes API & M3U সাপোর্ট",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "সার্ভার URL, ইউজার ও পাসওয়ার্ড দিয়ে নিমেষেই কানেক্ট করুন",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 3. SEARCH BAR (Matching Screenshot 2)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "প্লেলিস্ট খুঁজুন...",
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
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

            // Filter playlists based on search
            val filteredPlaylists = remember(playlists, searchQuery) {
                if (searchQuery.isBlank()) playlists else {
                    playlists.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                (it.description != null && it.description.contains(searchQuery, ignoreCase = true))
                    }
                }
            }

            // 4. 2-COLUMN GRID OF PLAYLIST CARDS (Matching Screenshot 2)
            if (filteredPlaylists.isEmpty()) {
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
                            imageVector = Icons.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "কোনো প্লেলিস্ট পাওয়া যায়নি",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTvMode) 4 else 2),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(filteredPlaylists, key = { _, pl -> pl.id }) { index, pl ->
                        PlaylistGridCard(
                            playlist = pl,
                            index = index,
                            isTvMode = isTvMode,
                            onClick = { selectedPlaylist = pl }
                        )
                    }
                }
            }
        } else {
            // ==================== INSIDE PLAYLIST CHANNEL LIST VIEW ====================
            val currentPl = selectedPlaylist!!

            // Top bar inside playlist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedPlaylist = null }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF00E5FF)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentPl.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlistChannels.size} চ্যানেল সংরক্ষিত",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            // Channel Search Row with Only Active Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = channelSearchQuery,
                    onValueChange = { channelSearchQuery = it },
                    placeholder = {
                        Text("এই প্লেলিস্টে চ্যানেল খুঁজুন...", color = Color(0xFF64748B), fontSize = 12.sp)
                    },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF00E5FF))
                    },
                    trailingIcon = {
                        if (channelSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { channelSearchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color.White)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
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

                // Only Active Switch Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (showOnlyActive) Color(0xFF065F46).copy(alpha = 0.4f) else Color(0xFF1E293B),
                    border = BorderStroke(
                        1.dp,
                        if (showOnlyActive) Color(0xFF10B981) else Color(0xFF334155)
                    ),
                    modifier = Modifier
                        .clickable { showOnlyActive = !showOnlyActive }
                        .padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (showOnlyActive) Color(0xFF10B981) else Color(0xFF64748B))
                        )
                        Text(
                            text = "Only Active",
                            color = if (showOnlyActive) Color(0xFF34D399) else Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = showOnlyActive,
                            onCheckedChange = { showOnlyActive = it },
                            modifier = Modifier.scale(0.7f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }
                }
            }

            // Enqueue unverified playlist channels for safe background probing
            LaunchedEffect(playlistChannels) {
                if (playlistChannels.isNotEmpty()) {
                    ChannelStatusManager.enqueueChannelsForProbing(playlistChannels)
                }
            }

            val filteredChannels = remember(playlistChannels, channelSearchQuery, showOnlyActive, statusTick) {
                val list = if (channelSearchQuery.isBlank()) playlistChannels else {
                    playlistChannels.filter {
                        it.title.contains(channelSearchQuery, ignoreCase = true) ||
                                it.category.contains(channelSearchQuery, ignoreCase = true)
                    }
                }
                if (showOnlyActive) {
                    list.filter { ChannelStatusManager.isChannelActive(it) }
                } else {
                    list
                }
            }

            if (isLoadingChannels) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF))
                        Text(
                            text = "চ্যানেল লোড হচ্ছে...",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (filteredChannels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "কোনো চ্যানেল পাওয়া যায়নি",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTvMode) 5 else 3),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredChannels, key = { it.id }) { item ->
                        var isCardFocused by remember { mutableStateOf(false) }
                        val isActive = ChannelStatusManager.isChannelActive(item)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCardFocused) Color(0xFF1E293B) else Color(0xFF0F172A),
                            border = BorderStroke(
                                1.dp,
                                when {
                                    isCardFocused -> Color(0xFFFFD600)
                                    isActive -> Color(0xFF10B981).copy(alpha = 0.45f)
                                    else -> Color(0xFFEF4444).copy(alpha = 0.45f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isTvMode) 130.dp else 115.dp)
                                .alpha(if (!isActive) 0.72f else 1.0f)
                                .onFocusChanged { isCardFocused = it.isFocused }
                                .focusable()
                                .clickable { onSelectMedia(item, playlistChannels) }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (!item.logoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = item.logoUrl,
                                            contentDescription = item.title,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Tv,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Top-Start Corner: Active green indicator / Offline red indicator
                                if (isActive) {
                                    Surface(
                                        shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                                        color = Color(0xFF064E3B).copy(alpha = 0.95f),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.8f)),
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF10B981))
                                            )
                                            Text(
                                                text = "সচল",
                                                color = Color(0xFF6EE7B7),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                                        color = Color(0xFF450A0A).copy(alpha = 0.95f),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                                        modifier = Modifier.align(Alignment.TopStart)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFEF4444))
                                            )
                                            Text(
                                                text = "অফলাইন",
                                                color = Color(0xFFFCA5A5),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== DIALOGS ====================

    // Add Playlist Dialog
    if (showAddPlaylistDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("") }
        var newLogo by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddPlaylistDialog = false },
            title = { Text("নতুন M3U প্লেলিস্ট যোগ করুন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("প্লেলিস্টের নাম") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text("M3U / M3U8 লিঙ্ক") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newLogo,
                        onValueChange = { newLogo = it },
                        label = { Text("লোগো URL (ঐচ্ছিক)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank() && newUrl.isNotBlank()) {
                            val newPl = PlaylistInfo(
                                id = "user_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}",
                                title = newTitle.trim(),
                                url = newUrl.trim(),
                                logoUrl = newLogo.trim().ifBlank { null },
                                type = "M3U",
                                isAdmin = false
                            )
                            repository.saveUserPlaylist(newPl)
                            onPlaylistsChanged()
                            showAddPlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("সংরক্ষণ করুন", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPlaylistDialog = false }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    // Xtream Codes API Dialog
    if (showXtreamDialog) {
        var xtreamServer by remember { mutableStateOf("") }
        var xtreamUser by remember { mutableStateOf("") }
        var xtreamPass by remember { mutableStateOf("") }
        var xtreamName by remember { mutableStateOf("") }
        var isConnecting by remember { mutableStateOf(false) }
        var connectionError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isConnecting) showXtreamDialog = false },
            title = { Text("Xtream Codes API কানেক্ট করুন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = xtreamName,
                        onValueChange = { xtreamName = it },
                        label = { Text("প্লেলিস্টের নাম (যেমন: My Xtream)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = xtreamServer,
                        onValueChange = { xtreamServer = it },
                        label = { Text("সার্ভার URL (যেমন: http://domain.com:8080)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = xtreamUser,
                        onValueChange = { xtreamUser = it },
                        label = { Text("ইউজারনেম (Username)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = xtreamPass,
                        onValueChange = { xtreamPass = it },
                        label = { Text("পাসওয়ার্ড (Password)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (connectionError != null) {
                        Text(
                            text = connectionError!!,
                            color = Color(0xFFEF4444),
                            fontSize = 11.5.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (xtreamServer.isNotBlank() && xtreamUser.isNotBlank() && xtreamPass.isNotBlank()) {
                            isConnecting = true
                            connectionError = null
                            scope.launch {
                                val m3uUrl = repository.buildXtreamM3uUrl(xtreamServer, xtreamUser, xtreamPass)
                                val newPl = PlaylistInfo(
                                    id = "xtream_${System.currentTimeMillis()}",
                                    title = if (xtreamName.isNotBlank()) xtreamName.trim() else "Xtream Server",
                                    url = m3uUrl,
                                    serverUrl = xtreamServer.trim(),
                                    username = xtreamUser.trim(),
                                    password = xtreamPass.trim(),
                                    type = "XTREAM",
                                    isAdmin = false
                                )
                                repository.saveUserPlaylist(newPl)
                                onPlaylistsChanged()
                                isConnecting = false
                                showXtreamDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                    enabled = !isConnecting
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text("কানেক্ট করুন", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showXtreamDialog = false }, enabled = !isConnecting) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}

/**
 * Custom Playlist Grid Card:
 * - Only displays the playlist image / thumbnail
 * - Absolutely no extra text, badges, or labels as requested by the user
 */
@Composable
fun PlaylistGridCard(
    playlist: PlaylistInfo,
    index: Int,
    isTvMode: Boolean,
    onClick: () -> Unit
) {
    var isCardFocused by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (isCardFocused) (if (isTvMode) 1.08f else 1.05f) else 1.0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "plCardScale"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isCardFocused) Color(0xFF1E293B) else Color(0xFF0F172A),
        border = BorderStroke(
            if (isCardFocused) 2.5.dp else 1.dp,
            if (isCardFocused) Color(0xFFFFD600) else Color(0xFF1E293B)
        ),
        shadowElevation = if (isCardFocused) 10.dp else 2.dp,
        modifier = Modifier
            .scale(cardScale)
            .fillMaxWidth()
            .height(if (isTvMode) 120.dp else 100.dp)
            .onFocusChanged { isCardFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F172A)),
            contentAlignment = Alignment.Center
        ) {
            if (!playlist.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.logoUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback stylized branding without extra clutter
                val titleLower = playlist.title.lowercase()
                when {
                    titleLower.contains("toffee") -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFEC4899), Color(0xFFDB2777), Color(0xFF9D174D))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tv,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                    titleLower.contains("aksh") || titleLower.contains("go") -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFE11D48), Color(0xFFBE123C), Color(0xFF0284C7))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tv,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlaylistPlay,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
