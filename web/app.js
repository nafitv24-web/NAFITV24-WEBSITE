/**
 * NAFI TV 24 - Web Edition Engine
 * Direct Firebase Realtime Database Sync, HLS Stream Extractor & Multi-Server Engine
 */

// Default Configurations (Matches Android MediaRepository)
const CONFIG = {
  DEFAULT_RTDB_URL: "https://nafitv24-live-default-rtdb.firebaseio.com/",
  FALLBACK_LIVETV_M3U: "https://raw.githubusercontent.com/nfiptv24-max/NAFITV/refs/heads/main/Nafitv24.m3u",
  FALLBACK_SPORTS_M3U: "https://raw.githubusercontent.com/nfiptv24-max/NAFITV/refs/heads/main/NAFI%20Sports.m3u",
  FALLBACK_TAPMAD_JSON: "https://raw.githubusercontent.com/srhady/tapmad-bd/refs/heads/main/tapmad_bd.json",
  FALLBACK_MOVIES_JSON: "https://raw.githubusercontent.com/nafitv24-web/NAFI-TV/refs/heads/main/movies.json",
  ADMIN_PIN: "40541273"
};

// Built-in Default Channels & Items for 0ms immediate presentation
const DEFAULT_LIVE_TV = [
  {
    id: "tv_tsports_hd",
    title: "T Sports HD",
    category: "Sports",
    type: "LIVE_TV",
    streamUrl: "https://live-tsports.akamaized.net/live/live-tsports/playlist.m3u8",
    servers: [
      { name: "সার্ভার ১ (T Sports Main)", url: "https://live-tsports.akamaized.net/live/live-tsports/playlist.m3u8" },
      { name: "সার্ভার ২ (Backup Live)", url: "https://stream.crichd.vip/live/tsports.m3u8" }
    ],
    logoUrl: "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=300&fit=crop",
    isLive: true,
    quality: "1080p FHD"
  },
  {
    id: "tv_gtv_hd",
    title: "GTV (Gazi Television)",
    category: "Sports",
    type: "LIVE_TV",
    streamUrl: "https://live-gtv.akamaized.net/live/live-gtv/playlist.m3u8",
    servers: [
      { name: "সার্ভার ১ (GTV Live HD)", url: "https://live-gtv.akamaized.net/live/live-gtv/playlist.m3u8" }
    ],
    logoUrl: "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=300&fit=crop",
    isLive: true,
    quality: "HD"
  },
  {
    id: "tv_star_sports_1",
    title: "Star Sports 1 HD",
    category: "Sports",
    type: "LIVE_TV",
    streamUrl: "https://stream.crichd.vip/live/starsports1.m3u8",
    servers: [
      { name: "সার্ভার ১ (Star Sports 1)", url: "https://stream.crichd.vip/live/starsports1.m3u8" }
    ],
    logoUrl: "https://images.unsplash.com/photo-1531415074968-036ba1b575da?w=300&fit=crop",
    isLive: true,
    quality: "FHD"
  },
  {
    id: "tv_sony_ten_1",
    title: "Sony Sports Ten 1 HD",
    category: "Sports",
    type: "LIVE_TV",
    streamUrl: "https://stream.crichd.vip/live/sonyten1.m3u8",
    servers: [
      { name: "সার্ভার ১ (Sony Ten 1 HD)", url: "https://stream.crichd.vip/live/sonyten1.m3u8" }
    ],
    logoUrl: "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=300&fit=crop",
    isLive: true,
    quality: "HD"
  },
  {
    id: "tv_somoy_news",
    title: "Somoy TV Live",
    category: "News",
    type: "LIVE_TV",
    streamUrl: "https://somoynews.akamaized.net/hls/live/2017366/somoy/master.m3u8",
    servers: [
      { name: "সার্ভার ১ (Somoy TV 24/7)", url: "https://somoynews.akamaized.net/hls/live/2017366/somoy/master.m3u8" }
    ],
    logoUrl: "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=300&fit=crop",
    isLive: true,
    quality: "HD"
  },
  {
    id: "tv_jamuna_news",
    title: "Jamuna TV HD",
    category: "News",
    type: "LIVE_TV",
    streamUrl: "https://jamunanews.akamaized.net/live/master.m3u8",
    servers: [
      { name: "সার্ভার ১ (Jamuna TV Live)", url: "https://jamunanews.akamaized.net/live/master.m3u8" }
    ],
    logoUrl: "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=300&fit=crop",
    isLive: true,
    quality: "HD"
  },
  {
    id: "tv_channel_i",
    title: "Channel i HD",
    category: "Bangla",
    type: "LIVE_TV",
    streamUrl: "https://channeli.akamaized.net/live/channeli.m3u8",
    servers: [
      { name: "সার্ভার ১ (Channel i)", url: "https://channeli.akamaized.net/live/channeli.m3u8" }
    ],
    logoUrl: "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=300&fit=crop",
    isLive: true,
    quality: "HD"
  }
];

const DEFAULT_MOVIES = [
  {
    id: "mov_toofan_2024",
    title: "Toofan (তুফান)",
    category: "Bangla",
    type: "MOVIE",
    streamUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
    servers: [{ name: "সার্ভার ১ (4K HDR)", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" }],
    logoUrl: "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&fit=crop",
    rating: "9.2",
    year: "2024",
    quality: "4K UHD"
  },
  {
    id: "mov_mohanagar_series",
    title: "Mohanagar (মহানগর)",
    category: "Web Series",
    type: "MOVIE",
    streamUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
    servers: [{ name: "সার্ভার ১ (Full HD)", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4" }],
    logoUrl: "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=500&fit=crop",
    rating: "8.9",
    year: "2023",
    quality: "1080p"
  },
  {
    id: "mov_kalki_2898",
    title: "Kalki 2898 AD",
    category: "Entertainment",
    type: "MOVIE",
    streamUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
    servers: [{ name: "সার্ভার ১ (Dolby Atmos)", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4" }],
    logoUrl: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&fit=crop",
    rating: "8.5",
    year: "2024",
    quality: "4K Ultra"
  },
  {
    id: "mov_jawan_2023",
    title: "Jawan (জওয়ান)",
    category: "Entertainment",
    type: "MOVIE",
    streamUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
    servers: [{ name: "সার্ভার ১ (Hindi 1080p)", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4" }],
    logoUrl: "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=500&fit=crop",
    rating: "8.4",
    year: "2023",
    quality: "1080p"
  },
  {
    id: "mov_panchayat_s3",
    title: "Panchayat (Season 3)",
    category: "Web Series",
    type: "MOVIE",
    streamUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
    servers: [{ name: "সার্ভার ১ (HD)", url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4" }],
    logoUrl: "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=500&fit=crop",
    rating: "9.0",
    year: "2024",
    quality: "HD"
  }
];

// App State Management
const AppState = {
  currentTab: "sports",
  currentCategory: "ALL",
  searchQuery: "",
  currentItem: null,
  currentServerIndex: 0,
  
  sportsList: [],
  liveTvList: [...DEFAULT_LIVE_TV],
  moviesList: [...DEFAULT_MOVIES],
  playlistItems: [],
  
  hlsInstance: null,
  rtdbUrl: localStorage.getItem("nafitv_rtdb_url") || CONFIG.DEFAULT_RTDB_URL
};

// DOM References
const videoPlayer = document.getElementById("main-video-player");
const videoLoader = document.getElementById("video-loader");
const videoError = document.getElementById("video-error");
const errorMessageText = document.getElementById("error-message-text");
const serverSelect = document.getElementById("server-select");

const nowPlayingTitle = document.getElementById("now-playing-title");
const nowPlayingCategory = document.getElementById("now-playing-category");
const nowPlayingQuality = document.getElementById("now-playing-quality");
const nowPlayingStatus = document.getElementById("now-playing-status");
const playerTypeLabel = document.getElementById("player-type-label");

const sportsGrid = document.getElementById("sports-grid");
const livetvGrid = document.getElementById("livetv-grid");
const moviesGrid = document.getElementById("movies-grid");
const playlistGrid = document.getElementById("playlist-grid");

const badgeSports = document.getElementById("badge-sports");
const badgeLiveTv = document.getElementById("badge-livetv");
const badgeMovies = document.getElementById("badge-movies");

const sportsCountLabel = document.getElementById("sports-count-label");
const livetvCountLabel = document.getElementById("livetv-count-label");
const moviesCountLabel = document.getElementById("movies-count-label");

const searchInput = document.getElementById("search-input");
const searchClearBtn = document.getElementById("search-clear");
const marqueeTicker = document.getElementById("marquee-ticker-content");

// -------------------------------------------------------------
// Initialization
// -------------------------------------------------------------
document.addEventListener("DOMContentLoaded", () => {
  setupEventListeners();
  setupNavigationTabs();
  setupCategoryChips();
  
  // Start with default channel in background
  playMediaItem(DEFAULT_LIVE_TV[0], false);
  
  // Fetch Live Data from Firebase RTDB
  fetchFirebaseAllData();
  
  // Poll ticker marquee
  fetchMarqueeTicker();
});

// -------------------------------------------------------------
// Navigation & Tab Switching
// -------------------------------------------------------------
function setupNavigationTabs() {
  const tabs = document.querySelectorAll(".nav-tab");
  tabs.forEach(tab => {
    tab.addEventListener("click", () => {
      const targetTab = tab.getAttribute("data-tab");
      window.switchTab(targetTab);
    });
  });
}

window.switchTab = function(tabName) {
  AppState.currentTab = tabName;
  
  document.querySelectorAll(".nav-tab").forEach(t => {
    t.classList.toggle("active", t.getAttribute("data-tab") === tabName);
  });
  
  document.querySelectorAll(".tab-pane").forEach(pane => {
    pane.classList.toggle("active", pane.id === `pane-${tabName}`);
  });
  
  renderActiveTab();
  window.scrollTo({ top: 0, behavior: 'smooth' });
};

function setupCategoryChips() {
  const chips = document.querySelectorAll(".chip");
  chips.forEach(chip => {
    chip.addEventListener("click", () => {
      chips.forEach(c => c.classList.remove("active"));
      chip.classList.add("active");
      AppState.currentCategory = chip.getAttribute("data-category");
      renderActiveTab();
    });
  });
}

// -------------------------------------------------------------
// Video Player Engine (Hls.js + HTML5 Fallback + Server Switching)
// -------------------------------------------------------------
function playMediaItem(item, autoScroll = true) {
  if (!item) return;
  AppState.currentItem = item;
  AppState.currentServerIndex = 0;
  
  // Update Player UI
  nowPlayingTitle.textContent = item.title || "Unknown Channel";
  nowPlayingCategory.innerHTML = `<i class="fa-solid fa-tag"></i> ${item.category || "General"}`;
  nowPlayingQuality.textContent = item.quality || "HD";
  nowPlayingStatus.textContent = item.isLive ? "ONLINE" : "VOD";
  playerTypeLabel.textContent = item.type === "LIVE_EVENT" ? "LIVE MATCH" : (item.type === "MOVIE" ? "MOVIE / SERIES" : "LIVE CHANNEL");
  
  // Populate Server Dropdown
  const servers = getAllServers(item);
  serverSelect.innerHTML = "";
  servers.forEach((s, idx) => {
    const opt = document.createElement("option");
    opt.value = idx;
    opt.textContent = s.name || `সার্ভার ${idx + 1}`;
    serverSelect.appendChild(opt);
  });
  serverSelect.value = "0";

  loadStreamUrl(servers[0].url);

  // Mark currently playing card in grids
  document.querySelectorAll(".match-card, .channel-card, .movie-card").forEach(c => {
    c.classList.toggle("is-playing", c.dataset.id === item.id);
  });

  if (autoScroll && window.innerWidth < 800) {
    document.getElementById("player-container").scrollIntoView({ behavior: 'smooth' });
  }
}

function getAllServers(item) {
  if (item.servers && item.servers.length > 0) {
    return item.servers;
  }
  const list = [];
  if (item.streamUrl) {
    list.push({ name: "সার্ভার ১ (Main HD)", url: item.streamUrl });
  }
  if (item.backupUrl && item.backupUrl !== item.streamUrl) {
    list.push({ name: "সার্ভার ২ (Backup)", url: item.backupUrl });
  }
  return list.length > 0 ? list : [{ name: "সার্ভার ১", url: item.streamUrl || "" }];
}

function loadStreamUrl(url) {
  if (!url) {
    showPlayerError("স্ট্রীম লিংক অনুপলব্ধ।");
    return;
  }

  showPlayerLoader(true);
  hidePlayerError();

  const isHls = url.includes(".m3u8") || url.includes("hls") || url.includes("live");

  // Destroy previous Hls.js instance if exists
  if (AppState.hlsInstance) {
    AppState.hlsInstance.destroy();
    AppState.hlsInstance = null;
  }

  if (isHls && Hls.isSupported()) {
    const hls = new Hls({
      enableWorker: true,
      lowLatencyMode: true,
      backBufferLength: 90
    });
    AppState.hlsInstance = hls;

    hls.loadSource(url);
    hls.attachMedia(videoPlayer);

    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      showPlayerLoader(false);
      videoPlayer.play().catch(e => console.log("Auto-play blocked, waiting for user click:", e));
    });

    hls.on(Hls.Events.ERROR, (event, data) => {
      console.warn("HLS Error:", data);
      if (data.fatal) {
        switch (data.type) {
          case Hls.ErrorTypes.NETWORK_ERROR:
            tryNextServerAuto();
            break;
          case Hls.ErrorTypes.MEDIA_ERROR:
            hls.recoverMediaError();
            break;
          default:
            hls.destroy();
            tryNextServerAuto();
            break;
        }
      }
    });
  } else {
    // Native HTML5 Video fallback (Safari / MP4)
    videoPlayer.src = url;
    videoPlayer.addEventListener('loadedmetadata', () => {
      showPlayerLoader(false);
      videoPlayer.play().catch(() => {});
    }, { once: true });
    videoPlayer.addEventListener('error', () => {
      tryNextServerAuto();
    }, { once: true });
  }
}

function tryNextServerAuto() {
  if (!AppState.currentItem) {
    showPlayerError("ভিডিও লোড করা সম্ভব হয়নি।");
    return;
  }
  const servers = getAllServers(AppState.currentItem);
  if (AppState.currentServerIndex < servers.length - 1) {
    AppState.currentServerIndex++;
    serverSelect.value = AppState.currentServerIndex.toString();
    console.log(`Switching to backup server ${AppState.currentServerIndex + 1}...`);
    loadStreamUrl(servers[AppState.currentServerIndex].url);
  } else {
    showPlayerError("বর্তমান স্ট্রিমিং সার্ভার বন্ধ বা সাড়া দিচ্ছে না। অনুগ্রহ করে অন্য চ্যানেল বা ম্যাচ নির্বাচন করুন।");
  }
}

function showPlayerLoader(visible) {
  videoLoader.style.display = visible ? "flex" : "none";
}

function showPlayerError(msg) {
  showPlayerLoader(false);
  errorMessageText.textContent = msg;
  videoError.style.display = "flex";
}

function hidePlayerError() {
  videoError.style.display = "none";
}

// -------------------------------------------------------------
// Firebase Realtime Database Sync & Data Loading
// -------------------------------------------------------------
async function fetchFirebaseAllData() {
  const rtdbBase = AppState.rtdbUrl.replace(/\/+$/, "");
  
  // 1. Fetch Sports & Matches
  try {
    const sportsEndpoints = [`${rtdbBase}/sports.json`, `${rtdbBase}/events.json`, `${rtdbBase}/matches.json`];
    let sportsFound = [];
    for (const ep of sportsEndpoints) {
      const res = await fetch(ep).catch(() => null);
      if (res && res.ok) {
        const data = await res.json();
        if (data && typeof data === 'object') {
          const parsed = parseFirebaseCollection(data, "LIVE_EVENT");
          sportsFound = [...sportsFound, ...parsed];
        }
      }
    }
    if (sportsFound.length > 0) {
      AppState.sportsList = deduplicateById(sportsFound);
    } else {
      // Fallback to GitHub raw Sports M3U
      fetchFallbackSports();
    }
  } catch (e) {
    console.error("Error fetching sports:", e);
    fetchFallbackSports();
  }

  // 2. Fetch Live TV Channels
  try {
    const res = await fetch(`${rtdbBase}/channels.json`).catch(() => null);
    if (res && res.ok) {
      const data = await res.json();
      if (data && typeof data === 'object') {
        const parsed = parseFirebaseCollection(data, "LIVE_TV");
        if (parsed.length > 0) {
          AppState.liveTvList = deduplicateById([...parsed, ...DEFAULT_LIVE_TV]);
        }
      }
    }
  } catch (e) {
    console.warn("Using default TV channels:", e);
  }

  // 3. Fetch Movies
  try {
    const res = await fetch(`${rtdbBase}/movies.json`).catch(() => null);
    if (res && res.ok) {
      const data = await res.json();
      if (data && typeof data === 'object') {
        const parsed = parseFirebaseCollection(data, "MOVIE");
        if (parsed.length > 0) {
          AppState.moviesList = deduplicateById([...parsed, ...DEFAULT_MOVIES]);
        }
      }
    }
  } catch (e) {
    console.warn("Using default movies:", e);
  }

  updateBadgesAndCounts();
  renderActiveTab();
}

function parseFirebaseCollection(dataObj, defaultType) {
  const list = [];
  for (const [key, val] of Object.entries(dataObj)) {
    if (!val || typeof val !== 'object') continue;
    if (val.channelCount) continue; // Skip playlist metadata
    
    const item = {
      id: val.id || key,
      title: val.title || val.name || key,
      streamUrl: val.streamUrl || val.url || "",
      backupUrl: val.backupUrl || "",
      logoUrl: val.logoUrl || val.logo || val.poster || "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=300&fit=crop",
      category: val.category || val.sport || "General",
      type: val.type || defaultType,
      tournament: val.tournament || "",
      team1: val.team1 || "",
      team2: val.team2 || "",
      team1Logo: val.team1Logo || "",
      team2Logo: val.team2Logo || "",
      matchTimeFormatted: val.matchTimeFormatted || val.eventTime || "",
      status: val.status || "LIVE",
      isLive: val.isLive !== false,
      score1: val.score1 || "",
      score2: val.score2 || "",
      rating: val.rating || "8.5",
      year: val.year || "2024",
      quality: val.quality || "HD",
      servers: []
    };

    if (val.serversJson) {
      try {
        item.servers = JSON.parse(val.serversJson);
      } catch (_) {}
    } else if (val.servers && Array.isArray(val.servers)) {
      item.servers = val.servers;
    } else {
      item.servers = getAllServers(item);
    }

    list.push(item);
  }
  return list;
}

function deduplicateById(items) {
  const seen = new Set();
  return items.filter(item => {
    if (!item.id || seen.has(item.id)) return false;
    seen.add(item.id);
    return true;
  });
}

// Fallback loader from GitHub M3U
async function fetchFallbackSports() {
  try {
    const res = await fetch(CONFIG.FALLBACK_SPORTS_M3U).catch(() => null);
    if (res && res.ok) {
      const text = await res.text();
      const parsed = parseM3uText(text, "LIVE_EVENT");
      if (parsed.length > 0) {
        AppState.sportsList = parsed;
        updateBadgesAndCounts();
        renderActiveTab();
      }
    }
  } catch (e) {
    console.error("Fallback sports M3U error:", e);
  }
}

// Marquee Ticker
async function fetchMarqueeTicker() {
  const rtdbBase = AppState.rtdbUrl.replace(/\/+$/, "");
  try {
    const res = await fetch(`${rtdbBase}/marquee_news.json`).catch(() => null);
    if (res && res.ok) {
      const data = await res.json();
      if (typeof data === 'string' && data.trim()) {
        marqueeTicker.textContent = data.trim();
      } else if (data && typeof data === 'object') {
        const txt = data.marquee_ticker || data.text;
        if (txt) marqueeTicker.textContent = txt;
      }
    }
  } catch (e) {
    // Keep default ticker
  }
}

// -------------------------------------------------------------
// Grid Rendering (Sports, TV Channels, Movies)
// -------------------------------------------------------------
function renderActiveTab() {
  switch (AppState.currentTab) {
    case "sports":
      renderSportsGrid();
      break;
    case "livetv":
      renderLiveTvGrid();
      break;
    case "movies":
      renderMoviesGrid();
      break;
    case "playlist":
      renderPlaylistGrid();
      break;
  }
}

function filterItems(items) {
  let filtered = items;
  if (AppState.currentCategory !== "ALL") {
    filtered = filtered.filter(item => 
      (item.category && item.category.toLowerCase().includes(AppState.currentCategory.toLowerCase())) ||
      (item.tournament && item.tournament.toLowerCase().includes(AppState.currentCategory.toLowerCase()))
    );
  }
  if (AppState.searchQuery.trim()) {
    const q = AppState.searchQuery.toLowerCase();
    filtered = filtered.filter(item => 
      (item.title && item.title.toLowerCase().includes(q)) ||
      (item.team1 && item.team1.toLowerCase().includes(q)) ||
      (item.team2 && item.team2.toLowerCase().includes(q)) ||
      (item.category && item.category.toLowerCase().includes(q)) ||
      (item.tournament && item.tournament.toLowerCase().includes(q))
    );
  }
  return filtered;
}

// Render Sports Grid
function renderSportsGrid() {
  const items = filterItems(AppState.sportsList);
  sportsCountLabel.textContent = `${items.length} টি ম্যাচ সক্রিয়`;
  
  if (items.length === 0) {
    sportsGrid.innerHTML = `
      <div style="grid-column: 1/-1; text-align: center; padding: 40px; color: #94a3b8;">
        <i class="fa-solid fa-calendar-xmark" style="font-size: 32px; margin-bottom: 10px; color: #64748b;"></i>
        <p>বর্তমানে কোনো লাইভ স্পোর্টস ম্যাচ পাওয়া যায়নি। চ্যানেল ট্যাব থেকে স্পোর্টস টিভি দেখতে পারেন।</p>
      </div>
    `;
    return;
  }

  sportsGrid.innerHTML = items.map(item => {
    const isPlaying = AppState.currentItem && AppState.currentItem.id === item.id;
    const isLive = item.isLive !== false;
    const team1Name = item.team1 || item.title.split(" vs ")[0] || item.title;
    const team2Name = item.team2 || (item.title.split(" vs ")[1] || "TBD");
    const team1Logo = item.team1Logo || item.logoUrl;
    const team2Logo = item.team2Logo || item.logoUrl;

    return `
      <div class="match-card ${isPlaying ? 'is-playing' : ''}" data-id="${item.id}">
        <div class="match-header">
          <span class="match-tournament"><i class="fa-solid fa-trophy"></i> ${item.tournament || item.category || 'Live Sports'}</span>
          <span class="match-status-badge ${isLive ? 'live' : 'upcoming'}">
            <span class="pulse-dot ${isLive ? 'red' : ''}"></span> ${item.status || (isLive ? 'LIVE' : 'UPCOMING')}
          </span>
        </div>

        <div class="match-body">
          <div class="team-block">
            <div class="team-logo-wrap">
              <img src="${team1Logo}" alt="${team1Name}" onerror="this.src='https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=100&fit=crop'" />
            </div>
            <div class="team-name" title="${team1Name}">${team1Name}</div>
            ${item.score1 ? `<div class="team-score">${item.score1}</div>` : ''}
          </div>

          <div class="vs-block">
            <div class="vs-circle">VS</div>
          </div>

          <div class="team-block">
            <div class="team-logo-wrap">
              <img src="${team2Logo}" alt="${team2Name}" onerror="this.src='https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=100&fit=crop'" />
            </div>
            <div class="team-name" title="${team2Name}">${team2Name}</div>
            ${item.score2 ? `<div class="team-score">${item.score2}</div>` : ''}
          </div>
        </div>

        <div class="match-footer">
          <span><i class="fa-regular fa-clock"></i> ${item.matchTimeFormatted || 'সরাসরি সম্প্রচার'}</span>
          <button class="watch-btn"><i class="fa-solid fa-play"></i> খেলা দেখুন</button>
        </div>
      </div>
    `;
  }).join("");

  // Attach card click handlers
  sportsGrid.querySelectorAll(".match-card").forEach(card => {
    card.addEventListener("click", () => {
      const id = card.getAttribute("data-id");
      const match = AppState.sportsList.find(m => m.id === id);
      if (match) playMediaItem(match);
    });
  });
}

// Render Live TV Grid
function renderLiveTvGrid() {
  const items = filterItems(AppState.liveTvList);
  livetvCountLabel.textContent = `${items.length} টি চ্যানেল`;

  livetvGrid.innerHTML = items.map(item => {
    const isPlaying = AppState.currentItem && AppState.currentItem.id === item.id;
    return `
      <div class="channel-card ${isPlaying ? 'is-playing' : ''}" data-id="${item.id}">
        <div class="channel-logo-container">
          <img src="${item.logoUrl}" alt="${item.title}" onerror="this.src='https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=150&fit=crop'" />
        </div>
        <div class="channel-name" title="${item.title}">${item.title}</div>
        <div class="channel-category">${item.category || 'Live TV'}</div>
        <div class="channel-meta">
          <span class="quality-badge">${item.quality || 'HD'}</span>
          <span class="meta-tag status-live" style="font-size: 10px; padding: 1px 6px;">LIVE</span>
        </div>
      </div>
    `;
  }).join("");

  livetvGrid.querySelectorAll(".channel-card").forEach(card => {
    card.addEventListener("click", () => {
      const id = card.getAttribute("data-id");
      const channel = AppState.liveTvList.find(c => c.id === id);
      if (channel) playMediaItem(channel);
    });
  });
}

// Render Movies Grid
function renderMoviesGrid() {
  const items = filterItems(AppState.moviesList);
  moviesCountLabel.textContent = `${items.length} টি মুভি ও সিরিজ`;

  moviesGrid.innerHTML = items.map(item => {
    return `
      <div class="movie-card" data-id="${item.id}">
        <div class="movie-poster-wrap">
          <img src="${item.logoUrl}" alt="${item.title}" onerror="this.src='https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=300&fit=crop'" />
          <div class="movie-rating"><i class="fa-solid fa-star"></i> ${item.rating || '8.5'}</div>
          <div class="movie-quality">${item.quality || 'HD'}</div>
        </div>
        <div class="movie-info">
          <div class="movie-title" title="${item.title}">${item.title}</div>
          <div class="movie-sub">
            <span>${item.category || 'Movie'}</span>
            <span>${item.year || '2024'}</span>
          </div>
        </div>
      </div>
    `;
  }).join("");

  moviesGrid.querySelectorAll(".movie-card").forEach(card => {
    card.addEventListener("click", () => {
      const id = card.getAttribute("data-id");
      const movie = AppState.moviesList.find(m => m.id === id);
      if (movie) playMediaItem(movie);
    });
  });
}

// Render Custom Playlist Grid
function renderPlaylistGrid() {
  if (AppState.playlistItems.length === 0) {
    playlistGrid.innerHTML = `
      <div style="grid-column: 1/-1; text-align: center; padding: 40px; color: #94a3b8;">
        <i class="fa-solid fa-list-ul" style="font-size: 32px; margin-bottom: 10px; color: #64748b;"></i>
        <p>উপরে যেকোনো M3U প্লেলিস্টের লিংক দিয়ে "প্লেলিস্ট লোড করুন" বাটনে ক্লিক করুন।</p>
      </div>
    `;
    return;
  }

  playlistGrid.innerHTML = AppState.playlistItems.map(item => `
    <div class="channel-card" data-id="${item.id}">
      <div class="channel-logo-container">
        <img src="${item.logoUrl}" alt="${item.title}" onerror="this.src='https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=150&fit=crop'" />
      </div>
      <div class="channel-name" title="${item.title}">${item.title}</div>
      <div class="channel-category">${item.category || 'M3U Channel'}</div>
    </div>
  `).join("");

  playlistGrid.querySelectorAll(".channel-card").forEach(card => {
    card.addEventListener("click", () => {
      const id = card.getAttribute("data-id");
      const ch = AppState.playlistItems.find(c => c.id === id);
      if (ch) playMediaItem(ch);
    });
  });
}

function updateBadgesAndCounts() {
  badgeSports.textContent = AppState.sportsList.length;
  badgeLiveTv.textContent = AppState.liveTvList.length;
  badgeMovies.textContent = AppState.moviesList.length;
}

// -------------------------------------------------------------
// M3U Playlist Parser
// -------------------------------------------------------------
function parseM3uText(content, defaultType = "LIVE_TV") {
  const lines = content.split(/\r?\n/);
  const items = [];
  let currentTitle = "";
  let currentLogo = "";
  let currentCategory = "";

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (line.startsWith("#EXTINF:")) {
      const logoMatch = line.match(/tvg-logo="([^"]+)"/i);
      currentLogo = logoMatch ? logoMatch[1] : "";
      
      const groupMatch = line.match(/group-title="([^"]+)"/i);
      currentCategory = groupMatch ? groupMatch[1] : "";
      
      const parts = line.split(",");
      currentTitle = parts[parts.length - 1].trim();
    } else if (line.startsWith("http://") || line.startsWith("https://")) {
      if (currentTitle) {
        items.push({
          id: `m3u_${items.length}_${Math.random().toString(36).substr(2, 5)}`,
          title: currentTitle,
          streamUrl: line,
          logoUrl: currentLogo || "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=150&fit=crop",
          category: currentCategory || "General",
          type: defaultType,
          isLive: true,
          quality: "HD"
        });
      }
      currentTitle = "";
      currentLogo = "";
      currentCategory = "";
    }
  }
  return items;
}

// -------------------------------------------------------------
// Event Listeners & UI Controls
// -------------------------------------------------------------
function setupEventListeners() {
  // Server Switcher Change
  serverSelect.addEventListener("change", (e) => {
    AppState.currentServerIndex = parseInt(e.target.value, 10);
    const servers = getAllServers(AppState.currentItem);
    if (servers[AppState.currentServerIndex]) {
      loadStreamUrl(servers[AppState.currentServerIndex].url);
    }
  });

  // Retry Player Button
  document.getElementById("btn-retry-player").addEventListener("click", () => {
    if (AppState.currentItem) {
      const servers = getAllServers(AppState.currentItem);
      loadStreamUrl(servers[AppState.currentServerIndex].url);
    }
  });

  // Next Server Button
  document.getElementById("btn-next-server").addEventListener("click", () => {
    tryNextServerAuto();
  });

  // PiP Button
  document.getElementById("btn-pip").addEventListener("click", async () => {
    if (document.pictureInPictureElement) {
      await document.exitPictureInPicture().catch(() => {});
    } else if (document.pictureInPictureEnabled && videoPlayer) {
      await videoPlayer.requestPictureInPicture().catch(() => {});
    }
  });

  // Fullscreen Button
  document.getElementById("btn-fullscreen").addEventListener("click", () => {
    if (!document.fullscreenElement) {
      videoPlayer.requestFullscreen().catch(() => {});
    } else {
      document.exitFullscreen().catch(() => {});
    }
  });

  // Copy Stream Link
  document.getElementById("btn-open-external").addEventListener("click", () => {
    if (AppState.currentItem && AppState.currentItem.streamUrl) {
      navigator.clipboard.writeText(AppState.currentItem.streamUrl).then(() => {
        alert("ভিডিও স্ট্রীম লিংক ক্লিপবোর্ডে কপি করা হয়েছে!");
      });
    }
  });

  // Share Stream
  document.getElementById("btn-share-stream").addEventListener("click", () => {
    if (navigator.share && AppState.currentItem) {
      navigator.share({
        title: `NAFI TV 24 - ${AppState.currentItem.title}`,
        text: `Watch ${AppState.currentItem.title} live on NAFI TV 24!`,
        url: window.location.href
      }).catch(() => {});
    } else {
      navigator.clipboard.writeText(window.location.href).then(() => {
        alert("ওয়েবসাইটের লিংক কপি করা হয়েছে!");
      });
    }
  });

  // Search Input
  searchInput.addEventListener("input", (e) => {
    AppState.searchQuery = e.target.value;
    searchClearBtn.style.display = e.target.value ? "block" : "none";
    renderActiveTab();
  });

  searchClearBtn.addEventListener("click", () => {
    searchInput.value = "";
    AppState.searchQuery = "";
    searchClearBtn.style.display = "none";
    renderActiveTab();
  });

  // Refresh Data Button
  document.getElementById("btn-refresh-data").addEventListener("click", () => {
    fetchFirebaseAllData();
    fetchMarqueeTicker();
  });

  // Custom M3U Loader
  document.getElementById("btn-load-custom-m3u").addEventListener("click", async () => {
    const url = document.getElementById("custom-m3u-input").value.trim();
    if (!url) return;
    try {
      const res = await fetch(url);
      const text = await res.text();
      AppState.playlistItems = parseM3uText(text);
      renderPlaylistGrid();
    } catch (e) {
      alert("প্লেলিস্ট লোড করা যায়নি। URL ঠিক আছে কিনা পরীক্ষা করুন।");
    }
  });

  // Preset Playlists
  document.querySelectorAll(".preset-btn").forEach(btn => {
    btn.addEventListener("click", async () => {
      const url = btn.getAttribute("data-url");
      document.getElementById("custom-m3u-input").value = url;
      document.getElementById("btn-load-custom-m3u").click();
    });
  });

  // Admin Modal
  const adminModal = document.getElementById("admin-modal");
  document.getElementById("btn-open-admin").addEventListener("click", () => {
    adminModal.style.display = "flex";
  });
  document.getElementById("btn-close-admin").addEventListener("click", () => {
    adminModal.style.display = "none";
  });

  // Test Firebase Connection in Admin
  document.getElementById("btn-test-firebase").addEventListener("click", async () => {
    const url = document.getElementById("admin-rtdb-url").value.trim().replace(/\/+$/, "");
    const statusBox = document.getElementById("admin-status-message");
    statusBox.style.display = "block";
    statusBox.textContent = "সার্ভার চেক করা হচ্ছে...";
    try {
      const res = await fetch(`${url}/.json?shallow=true`).catch(() => null);
      if (res && res.ok) {
        statusBox.textContent = "✅ Firebase Realtime Database সফলভাবে সংযুক্ত!";
      } else {
        statusBox.textContent = "⚠️ ফায়ারবেস সংযোগ সম্ভব হয়নি বা পারমিশন প্রয়োজন।";
      }
    } catch (e) {
      statusBox.textContent = "❌ সংযোগ ত্রুটি: " + e.message;
    }
  });

  // Save Admin Settings
  document.getElementById("btn-save-settings").addEventListener("click", () => {
    const pin = document.getElementById("admin-pin-input").value.trim();
    if (pin && pin !== CONFIG.ADMIN_PIN) {
      alert("ভুল এডমিন পিন!");
      return;
    }
    const newUrl = document.getElementById("admin-rtdb-url").value.trim();
    if (newUrl) {
      AppState.rtdbUrl = newUrl;
      localStorage.setItem("nafitv_rtdb_url", newUrl);
      fetchFirebaseAllData();
      alert("সেটিংস সফলভাবে সংরক্ষিত হয়েছে!");
      adminModal.style.display = "none";
    }
  });

  // Quick Add Stream to State & Firebase
  document.getElementById("btn-add-stream").addEventListener("click", async () => {
    const title = document.getElementById("new-item-title").value.trim();
    const url = document.getElementById("new-item-url").value.trim();
    const type = document.getElementById("new-item-type").value;
    const category = document.getElementById("new-item-category").value.trim() || "General";
    const logo = document.getElementById("new-item-logo").value.trim() || "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=150&fit=crop";

    if (!title || !url) {
      alert("শিরোনাম এবং স্ট্রিম URL আবশ্যক!");
      return;
    }

    const newItem = {
      id: `web_${Date.now()}`,
      title,
      streamUrl: url,
      logoUrl: logo,
      category,
      type,
      isLive: true,
      quality: "HD",
      servers: [{ name: "সার্ভার ১", url }]
    };

    if (type === "LIVE_EVENT") {
      AppState.sportsList.unshift(newItem);
    } else if (type === "LIVE_TV") {
      AppState.liveTvList.unshift(newItem);
    } else {
      AppState.moviesList.unshift(newItem);
    }

    // Attempt push to Firebase RTDB
    try {
      const rtdbBase = AppState.rtdbUrl.replace(/\/+$/, "");
      const path = type === "LIVE_EVENT" ? "sports" : (type === "LIVE_TV" ? "channels" : "movies");
      await fetch(`${rtdbBase}/${path}/${newItem.id}.json`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(newItem)
      });
    } catch (_) {}

    alert("চ্যানেল/ম্যাচ সফলভাবে যুক্ত হয়েছে!");
    updateBadgesAndCounts();
    renderActiveTab();
    adminModal.style.display = "none";
  });
}
