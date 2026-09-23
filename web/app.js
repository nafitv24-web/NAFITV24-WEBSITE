/**
 * NAFI TV 24 – Web Edition Core Application Script
 * Complete integration: HLS Video Player, Firebase RTDB & Playlists JSON Engine,
 * Match Schedule Page, Category Filter, and Multi-Server Fallback.
 */

'use strict';

// ═══════════════════════════════════════════
// 1. CONSTANTS & DEFAULT REPOSITORIES
// ═══════════════════════════════════════════
const DEFAULT_RTDB_URL = 'https://nafitv24-default-rtdb.asia-southeast1.firebasedatabase.app';
const TAPMAD_JSON_URL = 'https://raw.githubusercontent.com/srhady/tapmad-bd/refs/heads/main/tapmad_bd.json';
const LIVE_TV_M3U_URL = 'https://raw.githubusercontent.com/nfiptv24-max/NAFITV/refs/heads/main/Nafitv24.m3u';
const SPORTS_M3U_URL = 'https://raw.githubusercontent.com/nfiptv24-max/NAFITV/refs/heads/main/NAFI%20Sports.m3u';
const MOVIES_JSON_URL = 'https://raw.githubusercontent.com/nafitv24-web/NAFI-TV/refs/heads/main/movies.json';

const FALLBACK_LOGO_SVG = 'favicon.svg';

// Instant verified fallback channels
const BUILT_IN_CHANNELS = [
  {
    id: 'tsports_hd',
    name: 'T Sports HD',
    category: 'Sports',
    logo: 'https://flagcdn.com/w160/bd.png',
    url: 'https://live-tsports.akamaized.net/live/tsports/master.m3u8',
    servers: [
      { name: 'সার্ভার ১ (HLS)', url: 'https://live-tsports.akamaized.net/live/tsports/master.m3u8' },
      { name: 'সার্ভার ২ (HD)', url: 'https://raw.githubusercontent.com/nfiptv24-max/NAFITV/refs/heads/main/streams/tsports.m3u8' }
    ],
    isLive: true,
    pinned: true
  },
  {
    id: 'gtv_live',
    name: 'GTV (Gazi TV)',
    category: 'Sports',
    logo: 'https://flagcdn.com/w160/bd.png',
    url: 'https://live.cholebengal.com/live/gtv/index.m3u8',
    servers: [
      { name: 'সার্ভার ১ (HLS)', url: 'https://live.cholebengal.com/live/gtv/index.m3u8' }
    ],
    isLive: true,
    pinned: true
  },
  {
    id: 'star_sports_1',
    name: 'Star Sports 1 HD',
    category: 'Sports',
    logo: 'https://flagcdn.com/w160/in.png',
    url: 'https://stream.crichd.vip/live/starsports1.m3u8',
    servers: [
      { name: 'সার্ভার ১', url: 'https://stream.crichd.vip/live/starsports1.m3u8' },
      { name: 'সার্ভার ২', url: 'https://live.crichd.tv/live/ss1hd.m3u8' }
    ],
    isLive: true,
    pinned: true
  },
  {
    id: 'sony_sports_ten_1',
    name: 'Sony Sports Ten 1 HD',
    category: 'Sports',
    logo: 'https://flagcdn.com/w160/in.png',
    url: 'https://stream.crichd.vip/live/sonysportsten1.m3u8',
    servers: [
      { name: 'সার্ভার ১', url: 'https://stream.crichd.vip/live/sonysportsten1.m3u8' }
    ],
    isLive: true
  },
  {
    id: 'willow_cricket',
    name: 'Willow Cricket HD',
    category: 'Sports',
    logo: 'https://flagcdn.com/w160/us.png',
    url: 'https://stream.crichd.vip/live/willowusa.m3u8',
    servers: [
      { name: 'সার্ভার ১', url: 'https://stream.crichd.vip/live/willowusa.m3u8' }
    ],
    isLive: true
  },
  {
    id: 'ptv_sports',
    name: 'PTV Sports Live',
    category: 'Sports',
    logo: 'https://flagcdn.com/w160/pk.png',
    url: 'https://stream.crichd.vip/live/ptvsports.m3u8',
    servers: [
      { name: 'সার্ভার ১', url: 'https://stream.crichd.vip/live/ptvsports.m3u8' }
    ],
    isLive: true
  },
  {
    id: 'somoy_tv',
    name: 'Somoy TV (সময় টিভি)',
    category: 'News',
    logo: 'https://flagcdn.com/w160/bd.png',
    url: 'https://live.somoynews.tv/hls/live.m3u8',
    isLive: true
  },
  {
    id: 'jamuna_tv',
    name: 'Jamuna TV HD',
    category: 'News',
    logo: 'https://flagcdn.com/w160/bd.png',
    url: 'https://live.jamuna.tv/hls/stream.m3u8',
    isLive: true
  },
  {
    id: 'dbc_news',
    name: 'DBC News HD',
    category: 'News',
    logo: 'https://flagcdn.com/w160/bd.png',
    url: 'https://dbc.live/hls/dbc.m3u8',
    isLive: true
  },
  {
    id: 'channel_i',
    name: 'Channel i (চ্যানেল আই)',
    category: 'Entertainment',
    logo: 'https://flagcdn.com/w160/bd.png',
    url: 'https://live.channelionline.com/hls/channel-i.m3u8',
    isLive: true
  },
  {
    id: 'deepto_tv',
    name: 'Deepto TV (দীপ্ত টিভি)',
    category: 'Entertainment',
    logo: 'https://flagcdn.com/w160/bd.png',
    url: 'https://live.deeptotv.com/hls/deeptotv.m3u8',
    isLive: true
  },
  {
    id: 'bangla_cinema',
    name: 'Bangla Cinema HD',
    category: 'Movies',
    logo: 'https://flagcdn.com/w160/bd.png',
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
    isLive: false
  }
];

// ═══════════════════════════════════════════
// 2. LOCAL STORAGE HELPER
// ═══════════════════════════════════════════
const Store = {
  get(key, defaultVal = null) {
    try {
      const v = localStorage.getItem(key);
      return v !== null ? JSON.parse(v) : defaultVal;
    } catch {
      return defaultVal;
    }
  },
  set(key, val) {
    try {
      localStorage.setItem(key, JSON.stringify(val));
    } catch (e) {
      console.warn('Store.set error:', key, e);
    }
  },
  getString(key, defaultVal = '') {
    try {
      return localStorage.getItem(key) || defaultVal;
    } catch {
      return defaultVal;
    }
  },
  setString(key, val) {
    try {
      localStorage.setItem(key, val);
    } catch {}
  }
};

function escHtml(str) {
  return String(str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ═══════════════════════════════════════════
// 3. MAIN APPLICATION STATE
// ═══════════════════════════════════════════
const APP = {
  S: {
    channels: [...BUILT_IN_CHANNELS],
    matches: [],
    playlists: Store.get('custom_playlists', []),
    fav: Store.get('fav_channels', []),
    rec: Store.get('rec_channels', []),
    cat: 'all',
    catFilter: '',
    view: Store.getString('channel_view', 'g3'),
    dark: Store.getString('theme_mode', 'dark') !== 'light',
    autoplay: Store.getString('autoplay', 'true') !== 'false',
    fsz: Store.getString('font_size', 'md'),
    rtdbUrl: Store.getString('rtdb_url', DEFAULT_RTDB_URL),
    gridPage: 1,
    PAGE_SIZE: 36,
    curCh: null,
    playing: false
  },

  E: {},

  init() {
    this.cacheElements();
    this.applyTheme();
    this.applyView(this.S.view);
    this.applyFsz(this.S.fsz);

    // Initial renders
    this.renderChannels();
    this.buildCatTabs();
    this.renderMatches();

    // Fetch all Firebase & Admin Playlists
    this.fetchAllData();

    // Bind event listeners
    this.bindEvents();

    // Hide initial loader
    setTimeout(() => {
      const ls = document.getElementById('ls');
      if (ls) {
        ls.classList.add('out');
        setTimeout(() => { ls.style.display = 'none'; }, 500);
      }
    }, 600);
  },

  cacheElements() {
    const ids = [
      'nav', 'menu', 'menu-ov', 'mo-btn', 'mc-btn',
      'srch-ov', 'srch-btn', 'srch-close', 'srch-in', 'srch-res',
      'set-ov', 'settings', 'set-btn', 'tog-theme', 'tog-auto',
      'contact-ov', 'contact-sheet',
      'import-ov', 'btn-close-import', 'btn-do-import',
      'import-url-input', 'import-file-input',
      'player-wrap', 'main-video', 'qtv-lock-btn', 'qtv-close-btn',
      'pw-bar', 'pw-logo', 'pw-name', 'pw-server-select',
      'video-loader', 'video-error', 'player-err-msg',
      'btn-player-retry', 'btn-player-next-server',
      'hero', 'hero-play', 'hero-title', 'hero-sub',
      'grid', 'grid-more-wrap', 'grid-more-btn', 'cat-tabs',
      'match-page', 'match-list', 'bottom-nav',
      'bnav-live', 'bnav-match', 'setting-rtdb-url', 'btn-save-rtdb'
    ];
    ids.forEach(id => {
      this.E[id] = document.getElementById(id);
    });

    if (this.E['setting-rtdb-url']) {
      this.E['setting-rtdb-url'].value = this.S.rtdbUrl;
    }
  },

  // ═══════════════════════════════════════════
  // 4. FETCHING & PARSING ALL FIREBASE DATA & PLAYLISTS
  // ═══════════════════════════════════════════
  async fetchAllData() {
    const rtdbBase = this.S.rtdbUrl.replace(/\/+$/, '');

    // 1. Fetch Firebase Channels, Sports, Matches, and Playlists in parallel
    const endpoints = [
      `${rtdbBase}/channels.json`,
      `${rtdbBase}/sports.json`,
      `${rtdbBase}/matches.json`,
      `${rtdbBase}/events.json`,
      `${rtdbBase}/movies.json`,
      `${rtdbBase}/playlists.json`,
      `${rtdbBase}/app_config.json`,
      TAPMAD_JSON_URL,
      MOVIES_JSON_URL
    ];

    const fetchPromises = endpoints.map(url =>
      fetch(url, { cache: 'no-store' })
        .then(res => res.ok ? res.json() : null)
        .catch(() => null)
    );

    const [
      fbChannels,
      fbSports,
      fbMatches,
      fbEvents,
      fbMovies,
      fbPlaylists,
      fbAppConfig,
      tapmadData,
      moviesData
    ] = await Promise.all(fetchPromises);

    const newChannels = [];
    const newMatches = [];

    // 1. Process RTDB channels
    if (fbChannels && typeof fbChannels === 'object') {
      this.normalizeItems(fbChannels, 'TV').forEach(ch => newChannels.push(ch));
    }

    // 2. Process RTDB sports
    if (fbSports && typeof fbSports === 'object') {
      this.normalizeItems(fbSports, 'Sports').forEach(ch => {
        newChannels.push(ch);
        newMatches.push(this.channelToMatch(ch));
      });
    }

    // 3. Process RTDB matches & events
    if (fbMatches && typeof fbMatches === 'object') {
      this.normalizeMatches(fbMatches).forEach(m => newMatches.push(m));
    }
    if (fbEvents && typeof fbEvents === 'object') {
      this.normalizeMatches(fbEvents).forEach(m => newMatches.push(m));
    }

    // 4. Process RTDB movies
    if (fbMovies && typeof fbMovies === 'object') {
      this.normalizeItems(fbMovies, 'Movies').forEach(m => newChannels.push(m));
    }

    // 5. CRITICAL: Process Playlists from Firebase (`/playlists.json`)
    // Admin uploads M3U & JSON playlists to Firebase here!
    if (fbPlaylists && typeof fbPlaylists === 'object') {
      const playlistList = Array.isArray(fbPlaylists)
        ? fbPlaylists
        : Object.entries(fbPlaylists).map(([id, p]) => ({ id, ...p }));

      for (const pl of playlistList) {
        if (pl && pl.url) {
          try {
            await this.loadPlaylistFromUrl(pl.url, pl.title || pl.name || 'Playlist', newChannels, newMatches);
          } catch (err) {
            console.warn('Error loading playlist:', pl.title, err);
          }
        }
      }
    }

    // 6. Process custom app config urls (e.g. if admin configured central M3U urls in app_config)
    if (fbAppConfig && typeof fbAppConfig === 'object') {
      if (fbAppConfig.liveTvM3u) {
        await this.loadPlaylistFromUrl(fbAppConfig.liveTvM3u, 'Live TV', newChannels, newMatches);
      }
      if (fbAppConfig.sportsM3u) {
        await this.loadPlaylistFromUrl(fbAppConfig.sportsM3u, 'Sports', newChannels, newMatches);
      }
    }

    // 7. Process Tapmad Sports Matches JSON
    if (tapmadData && Array.isArray(tapmadData.Matches)) {
      tapmadData.Matches.forEach(tMatch => {
        const title = tMatch.VideoName || 'Live Match';
        const isLive = String(tMatch.Status).toLowerCase() === 'live';
        const streamUrl = tMatch.stream_url || '';

        const mObj = {
          id: 'tapmad_' + (tMatch.EntityId || Math.random()),
          title: title,
          league: tMatch.CategoryName || 'Sports',
          status: isLive ? 'live' : 'upcoming',
          team1: { name: title.split(' vs ')[0] || title, logo: tMatch.ThumbnailStandard || '' },
          team2: { name: title.split(' vs ')[1] || 'Match', logo: tMatch.ThumbnailTV || '' },
          score: isLive ? 'LIVE' : '',
          time: tMatch.EventStartDate || 'Soon',
          streamUrl: streamUrl
        };
        newMatches.push(mObj);

        if (streamUrl) {
          newChannels.push({
            id: mObj.id,
            name: title,
            category: 'Sports',
            logo: tMatch.ThumbnailStandard || '',
            url: streamUrl,
            isLive: isLive
          });
        }
      });
    }

    // 8. Process Movies JSON
    if (moviesData) {
      const movList = Array.isArray(moviesData) ? moviesData : (moviesData.movies || []);
      movList.forEach(m => {
        newChannels.push({
          id: 'mov_' + (m.id || Math.random()),
          name: m.title || m.name || 'Movie',
          category: 'Movies',
          logo: m.poster || m.logo || m.banner || '',
          url: m.streamUrl || m.url || '',
          isLive: false
        });
      });
    }

    // 9. Load user custom saved playlists from localStorage
    if (this.S.playlists && this.S.playlists.length > 0) {
      for (const pl of this.S.playlists) {
        if (pl.url) {
          await this.loadPlaylistFromUrl(pl.url, pl.name || 'Custom', newChannels, newMatches);
        }
      }
    }

    // Merge and deduplicate
    const combinedChannels = [...newChannels, ...BUILT_IN_CHANNELS];
    const uniqueChannels = [];
    const seenIds = new Set();
    combinedChannels.forEach(item => {
      if (item && item.url && !seenIds.has(item.id)) {
        seenIds.add(item.id);
        uniqueChannels.push(item);
      }
    });

    this.S.channels = uniqueChannels;
    this.S.matches = newMatches;

    // Refresh UI
    this.renderChannels();
    this.buildCatTabs();
    this.renderMatches();
    this.renderUpcomingCarousel();
  },

  async loadPlaylistFromUrl(url, defaultCategory, outChannels, outMatches) {
    if (!url || typeof url !== 'string') return;
    try {
      const res = await fetch(url.trim());
      if (!res.ok) return;
      const text = await res.text();
      const trimmed = text.trim();

      // Case A: JSON Playlist
      if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
        try {
          const json = JSON.parse(trimmed);
          if (Array.isArray(json)) {
            json.forEach((item, idx) => {
              if (item && (item.url || item.streamUrl)) {
                outChannels.push({
                  id: 'pl_' + idx + '_' + Math.random().toString(36).substring(2, 7),
                  name: item.name || item.title || `Channel ${idx + 1}`,
                  category: item.category || defaultCategory,
                  logo: item.logo || item.logoUrl || item.icon || '',
                  url: item.url || item.streamUrl,
                  servers: item.servers || []
                });
              }
            });
          } else if (json.channels || json.items || json.Matches) {
            const arr = json.channels || json.items || json.Matches;
            if (Array.isArray(arr)) {
              arr.forEach((item, idx) => {
                const sUrl = item.stream_url || item.url || item.streamUrl;
                if (sUrl) {
                  outChannels.push({
                    id: 'pl_j_' + idx + '_' + Math.random().toString(36).substring(2, 7),
                    name: item.VideoName || item.name || item.title || `Channel ${idx + 1}`,
                    category: item.CategoryName || item.category || defaultCategory,
                    logo: item.ThumbnailStandard || item.logo || '',
                    url: sUrl
                  });
                }
              });
            }
          }
          return;
        } catch {}
      }

      // Case B: M3U / M3U8 Playlist
      if (trimmed.includes('#EXTM3U') || trimmed.includes('#EXTINF')) {
        const lines = trimmed.split(/\r?\n/);
        let curName = '';
        let curLogo = '';
        let curCat = defaultCategory;

        for (let i = 0; i < lines.length; i++) {
          const line = lines[i].trim();
          if (line.startsWith('#EXTINF:')) {
            // Extract logo
            const logoMatch = line.match(/tvg-logo="([^"]+)"/i);
            curLogo = logoMatch ? logoMatch[1] : '';

            // Extract group/category
            const groupMatch = line.match(/group-title="([^"]+)"/i);
            curCat = groupMatch ? groupMatch[1] : defaultCategory;

            // Extract channel title
            const commaIdx = line.lastIndexOf(',');
            curName = commaIdx !== -1 ? line.substring(commaIdx + 1).trim() : 'Channel';
          } else if (line.startsWith('http://') || line.startsWith('https://')) {
            if (curName && line) {
              outChannels.push({
                id: 'm3u_' + Math.random().toString(36).substring(2, 8),
                name: curName,
                category: curCat,
                logo: curLogo,
                url: line
              });
            }
            curName = '';
            curLogo = '';
            curCat = defaultCategory;
          }
        }
      }
    } catch (e) {
      console.warn('loadPlaylistFromUrl error:', url, e);
    }
  },

  normalizeItems(objOrArr, defaultCategory) {
    const list = Array.isArray(objOrArr) ? objOrArr : Object.entries(objOrArr).map(([k, v]) => ({ id: k, ...v }));
    return list.map(item => {
      const servers = item.servers && Array.isArray(item.servers)
        ? item.servers
        : (item.streamUrl ? [{ name: 'সার্ভার ১', url: item.streamUrl }] : []);

      if (item.backupUrl && !servers.some(s => s.url === item.backupUrl)) {
        servers.push({ name: 'সার্ভার ২ (ব্যাকআপ)', url: item.backupUrl });
      }

      return {
        id: item.id || 'ch_' + Math.random().toString(36).substring(2, 7),
        name: item.title || item.name || 'Channel',
        category: item.category || defaultCategory,
        logo: item.logoUrl || item.logo || item.icon || '',
        url: item.streamUrl || item.url || (servers[0] ? servers[0].url : ''),
        servers: servers,
        isLive: item.isLive !== false,
        pinned: item.isPinned || false
      };
    }).filter(ch => ch.url);
  },

  normalizeMatches(objOrArr) {
    const list = Array.isArray(objOrArr) ? objOrArr : Object.entries(objOrArr).map(([k, v]) => ({ id: k, ...v }));
    return list.map(m => ({
      id: m.id || 'match_' + Math.random().toString(36).substring(2, 7),
      title: m.title || (m.team1 && m.team2 ? `${m.team1} vs ${m.team2}` : 'Live Match'),
      league: m.league || m.category || 'Cricket / Football',
      status: (m.status || 'live').toLowerCase(),
      team1: {
        name: m.team1Name || m.team1 || 'Team 1',
        logo: m.team1Logo || m.team1LogoUrl || ''
      },
      team2: {
        name: m.team2Name || m.team2 || 'Team 2',
        logo: m.team2Logo || m.team2LogoUrl || ''
      },
      score: m.score || m.team1Score || 'VS',
      time: m.time || m.matchTime || 'Live Now',
      streamUrl: m.streamUrl || m.url || ''
    }));
  },

  channelToMatch(ch) {
    const parts = ch.name.split(/ vs | v /i);
    return {
      id: 'sp_' + ch.id,
      title: ch.name,
      league: ch.category || 'Sports',
      status: 'live',
      team1: { name: parts[0] || ch.name, logo: ch.logo || '' },
      team2: { name: parts[1] || 'Match', logo: ch.logo || '' },
      score: 'LIVE',
      time: 'চলমান (Live)',
      streamUrl: ch.url
    };
  },

  // ═══════════════════════════════════════════
  // 5. RENDER CHANNELS & CATEGORIES
  // ═══════════════════════════════════════════
  buildCatTabs() {
    const tabsContainer = this.E['cat-tabs'];
    if (!tabsContainer) return;

    const categories = ['All'];
    const seen = new Set(['All']);

    this.S.channels.forEach(ch => {
      const cat = (ch.category || 'General').trim();
      if (!seen.has(cat)) {
        seen.add(cat);
        categories.push(cat);
      }
    });

    tabsContainer.innerHTML = categories.map(c => {
      const isSel = (c === 'All' && !this.S.catFilter) || this.S.catFilter === c;
      const icon = this.getCategoryIcon(c);
      return `
        <button class="cat-tab ${isSel ? 'on' : ''}" data-cat="${escHtml(c)}">
          <i class="fas ${icon}"></i>
          <span>${escHtml(c)}</span>
        </button>
      `;
    }).join('');

    tabsContainer.querySelectorAll('.cat-tab').forEach(btn => {
      btn.addEventListener('click', () => {
        const cat = btn.dataset.cat;
        this.S.catFilter = cat === 'All' ? '' : cat;
        this.S.gridPage = 1;
        this.buildCatTabs();
        this.renderChannels();
      });
    });
  },

  getCategoryIcon(cat) {
    const c = cat.toLowerCase();
    if (c === 'all') return 'fa-th';
    if (c.includes('sport') || c.includes('cricket') || c.includes('football')) return 'fa-futbol';
    if (c.includes('news')) return 'fa-newspaper';
    if (c.includes('movie') || c.includes('cinema')) return 'fa-film';
    if (c.includes('entertain') || c.includes('drama')) return 'fa-tv';
    if (c.includes('music')) return 'fa-music';
    if (c.includes('islam') || c.includes('relig')) return 'fa-mosque';
    if (c.includes('kid')) return 'fa-child';
    return 'fa-satellite-dish';
  },

  renderChannels() {
    const grid = this.E['grid'];
    if (!grid) return;

    let list = [...this.S.channels];

    // Filter by tab / category
    if (this.S.cat === 'fav') {
      list = list.filter(ch => this.S.fav.includes(ch.id));
    } else if (this.S.cat === 'rec') {
      list = this.S.rec.map(id => list.find(ch => ch.id === id)).filter(Boolean);
    } else if (this.S.cat === 'sports') {
      list = list.filter(ch => (ch.category || '').toLowerCase().includes('sport'));
    }

    if (this.S.catFilter) {
      list = list.filter(ch => (ch.category || 'General').trim() === this.S.catFilter);
    }

    // Sort: pinned first
    list.sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0));

    if (list.length === 0) {
      grid.innerHTML = `
        <div class="empty">
          <i class="fas fa-tv"></i>
          <p>কোনো চ্যানেল পাওয়া যায়নি।</p>
        </div>
      `;
      if (this.E['grid-more-wrap']) this.E['grid-more-wrap'].style.display = 'none';
      return;
    }

    const countToShow = Math.min(this.S.gridPage * this.S.PAGE_SIZE, list.length);
    const slice = list.slice(0, countToShow);

    grid.innerHTML = slice.map(ch => this.createChannelCardHTML(ch)).join('');

    // Load more button
    const moreWrap = this.E['grid-more-wrap'];
    const moreBtn = this.E['grid-more-btn'];
    if (moreWrap && moreBtn) {
      if (countToShow < list.length) {
        moreWrap.style.display = 'flex';
        moreBtn.innerHTML = `<i class="fas fa-chevron-down"></i> আরও চ্যানেল লোড করুন (${countToShow}/${list.length})`;
      } else {
        moreWrap.style.display = 'none';
      }
    }

    // Bind card click & favourite toggle
    grid.querySelectorAll('.cc').forEach(card => {
      card.addEventListener('click', (e) => {
        if (e.target.closest('.fstar')) return;
        const id = card.dataset.id;
        const targetCh = this.S.channels.find(c => c.id === id);
        if (targetCh) this.playChannel(targetCh);
      });

      const favBtn = card.querySelector('.fstar');
      if (favBtn) {
        favBtn.addEventListener('click', (e) => {
          e.stopPropagation();
          const id = card.dataset.id;
          this.toggleFav(id);
          this.renderChannels();
        });
      }
    });
  },

  createChannelCardHTML(ch) {
    const isPlaying = this.S.curCh && this.S.curCh.id === ch.id;
    const isFav = this.S.fav.includes(ch.id);
    const logoSrc = ch.logo || FALLBACK_LOGO_SVG;

    return `
      <div class="cc ${ch.pinned ? 'pinned' : ''} ${isPlaying ? 'now' : ''}" data-id="${escHtml(ch.id)}" role="listitem">
        <div class="cc-logo-w">
          ${ch.isLive ? '<div class="bdg bdg-live"><div class="dot"></div>LIVE</div>' : ''}
          ${ch.pinned ? '<div class="bdg bdg-pin">PINNED</div>' : ''}
          <img class="cc-logo" src="${escHtml(logoSrc)}" loading="lazy" alt="${escHtml(ch.name)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
        </div>
        <div class="cc-foot">
          <div class="cc-name" title="${escHtml(ch.name)}">${escHtml(ch.name)}</div>
          <button class="fstar ${isFav ? 'on' : ''}" title="পছন্দের তালিকায় যোগ করুন">
            <i class="fas fa-star"></i>
          </button>
        </div>
      </div>
    `;
  },

  toggleFav(id) {
    const idx = this.S.fav.indexOf(id);
    if (idx > -1) {
      this.S.fav.splice(idx, 1);
    } else {
      this.S.fav.push(id);
    }
    Store.set('fav_channels', this.S.fav);
  },

  addRecent(id) {
    this.S.rec = this.S.rec.filter(x => x !== id);
    this.S.rec.unshift(id);
    if (this.S.rec.length > 20) this.S.rec.pop();
    Store.set('rec_channels', this.S.rec);
  },

  // ═══════════════════════════════════════════
  // 6. VIDEO PLAYER & HLS ENGINE
  // ═══════════════════════════════════════════
  hlsInstance: null,
  currentServerIdx: 0,
  isLocked: false,

  playChannel(ch) {
    if (!ch) return;
    this.S.curCh = ch;
    this.currentServerIdx = 0;
    this.addRecent(ch.id);

    // Show player wrap
    if (this.E['player-wrap']) {
      this.E['player-wrap'].classList.add('on');
      this.E['player-wrap'].scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    // Set UI labels
    if (this.E['pw-name']) this.E['pw-name'].textContent = ch.name;
    if (this.E['pw-logo']) {
      this.E['pw-logo'].src = ch.logo || FALLBACK_LOGO_SVG;
    }

    // Set Multi-Server dropdown
    const serverSelect = this.E['pw-server-select'];
    const servers = (ch.servers && ch.servers.length > 0)
      ? ch.servers
      : [{ name: 'সার্ভার ১ (Main)', url: ch.url }];

    if (serverSelect) {
      serverSelect.innerHTML = servers.map((s, idx) =>
        `<option value="${idx}">${escHtml(s.name || `সার্ভার ${idx + 1}`)}</option>`
      ).join('');
    }

    this.loadStreamSource(servers[0].url);
    this.renderChannels();
  },

  loadStreamSource(streamUrl) {
    const video = this.E['main-video'];
    const loader = this.E['video-loader'];
    const errOverlay = this.E['video-error'];

    if (!video || !streamUrl) return;

    if (loader) loader.style.display = 'flex';
    if (errOverlay) errOverlay.style.display = 'none';

    if (this.hlsInstance) {
      this.hlsInstance.destroy();
      this.hlsInstance = null;
    }

    const isHls = streamUrl.includes('.m3u8') || streamUrl.includes('m3u');

    if (isHls && window.Hls && Hls.isSupported()) {
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
        backBufferLength: 90
      });
      this.hlsInstance = hls;

      hls.loadSource(streamUrl);
      hls.attachMedia(video);

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if (loader) loader.style.display = 'none';
        video.play().catch(() => {});
      });

      hls.on(Hls.Events.ERROR, (event, data) => {
        if (data.fatal) {
          if (loader) loader.style.display = 'none';
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              hls.startLoad();
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              hls.recoverMediaError();
              break;
            default:
              this.showPlayerError('সার্ভার থেকে স্ট্রিম লোড হতে সমস্যা হচ্ছে।');
              break;
          }
        }
      });
    } else {
      // Native MP4 / WebM / Safari HLS
      video.src = streamUrl;
      video.load();
      video.play().then(() => {
        if (loader) loader.style.display = 'none';
      }).catch(() => {
        if (loader) loader.style.display = 'none';
      });
    }

    video.onerror = () => {
      if (loader) loader.style.display = 'none';
      this.showPlayerError('ভিডিও প্লে করতে সমস্যা হচ্ছে। অন্য সার্ভার চেষ্টা করুন।');
    };
  },

  showPlayerError(msg) {
    const errOverlay = this.E['video-error'];
    const errMsg = this.E['player-err-msg'];
    if (errOverlay) errOverlay.style.display = 'flex';
    if (errMsg) errMsg.textContent = msg;
  },

  nextServer() {
    if (!this.S.curCh) return;
    const servers = this.S.curCh.servers || [{ url: this.S.curCh.url }];
    if (servers.length <= 1) {
      this.loadStreamSource(this.S.curCh.url);
      return;
    }
    this.currentServerIdx = (this.currentServerIdx + 1) % servers.length;
    if (this.E['pw-server-select']) {
      this.E['pw-server-select'].value = this.currentServerIdx;
    }
    this.loadStreamSource(servers[this.currentServerIdx].url);
  },

  closePlayer() {
    if (this.hlsInstance) {
      this.hlsInstance.destroy();
      this.hlsInstance = null;
    }
    const video = this.E['main-video'];
    if (video) {
      video.pause();
      video.removeAttribute('src');
      video.load();
    }
    if (this.E['player-wrap']) {
      this.E['player-wrap'].classList.remove('on', 'qtv-locked');
    }
    this.S.curCh = null;
    this.renderChannels();
  },

  toggleLock() {
    this.isLocked = !this.isLocked;
    const pWrap = this.E['player-wrap'];
    const btn = this.E['qtv-lock-btn'];
    if (pWrap) pWrap.classList.toggle('qtv-locked', this.isLocked);
    if (btn) {
      btn.innerHTML = this.isLocked
        ? '<i class="fas fa-lock" style="color:#ff3b30;"></i>'
        : '<i class="fas fa-lock-open"></i>';
    }
  },

  // ═══════════════════════════════════════════
  // 7. MATCH SCHEDULE & UPCOMING CAROUSEL
  // ═══════════════════════════════════════════
  renderMatches(filter = 'all') {
    const listEl = this.E['match-list'];
    if (!listEl) return;

    let matches = [...this.S.matches];
    if (filter === 'live') matches = matches.filter(m => m.status === 'live');
    if (filter === 'upcoming') matches = matches.filter(m => m.status === 'upcoming');
    if (filter === 'ended') matches = matches.filter(m => m.status === 'ended');

    if (matches.length === 0) {
      listEl.innerHTML = `
        <div class="match-empty">
          <i class="fas fa-futbol"></i>
          <p>এই মুহূর্তে কোনো ম্যাচ শিডিউল নেই।</p>
        </div>
      `;
      return;
    }

    listEl.innerHTML = matches.map(m => {
      const isLive = m.status === 'live';
      const statusClass = isLive ? 'mcs-live' : (m.status === 'ended' ? 'mcs-ended' : 'mcs-upcoming');
      const statusLabel = isLive ? '🔴 LIVE' : (m.status === 'ended' ? '✓ Ended' : '⏰ Upcoming');

      return `
        <div class="match-card ${isLive ? 'mc-live' : ''}" data-id="${escHtml(m.id)}">
          <div class="mc-header">
            <div class="mc-league">
              <i class="fas fa-trophy" style="color: #f59e0b;"></i>
              <span>${escHtml(m.league)}</span>
            </div>
            <span class="mc-status ${statusClass}">${statusLabel}</span>
          </div>

          <div class="mc-teams">
            <div class="mc-team">
              <img class="mc-team-logo" src="${escHtml(m.team1.logo || FALLBACK_LOGO_SVG)}" alt="${escHtml(m.team1.name)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
              <div class="mc-team-name">${escHtml(m.team1.name)}</div>
            </div>

            <div class="mc-score-wrap">
              <div class="mc-score">${escHtml(m.score || 'VS')}</div>
              <div class="mc-vs">${isLive ? 'চলমান' : 'VS'}</div>
            </div>

            <div class="mc-team">
              <img class="mc-team-logo" src="${escHtml(m.team2.logo || FALLBACK_LOGO_SVG)}" alt="${escHtml(m.team2.name)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
              <div class="mc-team-name">${escHtml(m.team2.name)}</div>
            </div>
          </div>

          <div class="mc-footer">
            <div class="mc-datetime">
              <i class="far fa-clock"></i>
              <span>${escHtml(m.time)}</span>
            </div>

            <button class="mc-watch-btn ${isLive ? 'btn-live' : 'btn-not-started'}" data-stream="${escHtml(m.streamUrl)}" data-title="${escHtml(m.title)}">
              <i class="fas fa-play"></i>
              <span>${isLive ? 'সরাসরি দেখুন (Watch Live)' : 'শিডিউল'}</span>
            </button>
          </div>
        </div>
      `;
    }).join('');

    // Bind Watch Live buttons
    listEl.querySelectorAll('.mc-watch-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const stream = btn.dataset.stream;
        const title = btn.dataset.title;
        if (stream) {
          this.switchPage('live');
          this.playChannel({
            id: 'm_' + Math.random().toString(36).substring(2, 8),
            name: title || 'Live Match',
            category: 'Sports',
            url: stream
          });
        }
      });
    });
  },

  renderUpcomingCarousel() {
    const trackHome = document.getElementById('upc-track-home');
    const trackMatch = document.getElementById('upc-track-match');

    const liveOrUpcoming = this.S.matches.slice(0, 10);
    if (liveOrUpcoming.length === 0) return;

    const cardsHtml = liveOrUpcoming.map(m => `
      <div class="upc-card ${m.status === 'live' ? 'upc-live' : ''}" data-stream="${escHtml(m.streamUrl)}" data-title="${escHtml(m.title)}">
        <div class="upc-thumb-wrap">
          <img class="upc-thumb" src="${escHtml(m.team1.logo || 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=400&fit=crop')}" alt="${escHtml(m.title)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
          ${m.status === 'live' ? '<div class="upc-live-badge"><div class="dot"></div>LIVE</div>' : '<div class="upc-upcoming-badge">UPCOMING</div>'}
        </div>
        <div class="upc-info">
          <div class="upc-title">${escHtml(m.title)}</div>
          <div class="upc-meta">${escHtml(m.league)}</div>
          <div class="upc-time"><i class="far fa-clock"></i> ${escHtml(m.time)}</div>
          ${m.streamUrl ? '<button class="upc-watch-btn"><i class="fas fa-play"></i> Watch Now</button>' : ''}
        </div>
      </div>
    `).join('');

    if (trackHome) trackHome.innerHTML = cardsHtml;
    if (trackMatch) trackMatch.innerHTML = cardsHtml;

    // Bind click to play
    [trackHome, trackMatch].forEach(track => {
      if (track) {
        track.querySelectorAll('.upc-card').forEach(card => {
          card.addEventListener('click', () => {
            const stream = card.dataset.stream;
            const title = card.dataset.title;
            if (stream) {
              this.switchPage('live');
              this.playChannel({
                id: 'm_' + Math.random().toString(36).substring(2, 8),
                name: title || 'Live Match',
                category: 'Sports',
                url: stream
              });
            }
          });
        });
      }
    });
  },

  // ═══════════════════════════════════════════
  // 8. THEMING & STYLES
  // ═══════════════════════════════════════════
  applyTheme() {
    document.body.classList.toggle('lt', !this.S.dark);
    const thIco = document.getElementById('th-ico');
    const matchThIco = document.getElementById('match-th-ico');
    const togTheme = this.E['tog-theme'];

    const iconClass = this.S.dark ? 'fas fa-moon' : 'fas fa-sun';
    if (thIco) thIco.className = iconClass;
    if (matchThIco) matchThIco.className = iconClass;
    if (togTheme) togTheme.classList.toggle('on', this.S.dark);

    Store.setString('theme_mode', this.S.dark ? 'dark' : 'light');
  },

  toggleTheme() {
    this.S.dark = !this.S.dark;
    this.applyTheme();
  },

  applyView(mode) {
    this.S.view = mode;
    Store.setString('channel_view', mode);

    const grid = this.E['grid'];
    if (!grid) return;
    grid.className = '';
    if (mode === 'lst') grid.classList.add('lst');
    if (mode === 'g2') grid.classList.add('g2');

    ['vb-lst', 'vb-g2', 'vb-g3'].forEach(id => {
      const b = document.getElementById(id);
      if (b) b.classList.remove('on');
    });

    const activeBtn = document.getElementById(mode === 'lst' ? 'vb-lst' : (mode === 'g2' ? 'vb-g2' : 'vb-g3'));
    if (activeBtn) activeBtn.classList.add('on');

    this.renderChannels();
  },

  applyFsz(size) {
    this.S.fsz = size;
    Store.setString('font_size', size);
    document.body.classList.remove('fs-sm', 'fs-lg');
    if (size === 'sm') document.body.classList.add('fs-sm');
    if (size === 'lg') document.body.classList.add('fs-lg');

    document.querySelectorAll('.fsb').forEach(b => {
      b.classList.toggle('on', b.dataset.fs === size);
    });
  },

  switchPage(page) {
    const liveApp = document.getElementById('app');
    const matchPage = document.getElementById('match-page');
    const bnavLive = this.E['bnav-live'];
    const bnavMatch = this.E['bnav-match'];

    if (page === 'match') {
      if (liveApp) liveApp.style.display = 'none';
      if (matchPage) matchPage.style.display = 'block';
      if (bnavLive) bnavLive.classList.remove('on');
      if (bnavMatch) bnavMatch.classList.add('on');
      this.renderMatches();
    } else {
      if (liveApp) liveApp.style.display = 'block';
      if (matchPage) matchPage.style.display = 'none';
      if (bnavLive) bnavLive.classList.add('on');
      if (bnavMatch) bnavMatch.classList.remove('on');
    }
  },

  // ═══════════════════════════════════════════
  // 9. EVENT BINDINGS
  // ═══════════════════════════════════════════
  bindEvents() {
    // Menu
    if (this.E['mo-btn']) this.E['mo-btn'].addEventListener('click', () => this.openMenu());
    if (this.E['mc-btn']) this.E['mc-btn'].addEventListener('click', () => this.closeMenu());
    if (this.E['menu-ov']) this.E['menu-ov'].addEventListener('click', () => this.closeMenu());

    // Menu category items
    const catMap = { 'm-all': 'all', 'm-sports': 'sports', 'm-fav': 'fav', 'm-rec': 'rec' };
    Object.entries(catMap).forEach(([btnId, cat]) => {
      const btn = document.getElementById(btnId);
      if (btn) {
        btn.addEventListener('click', (e) => {
          e.preventDefault();
          this.S.cat = cat;
          this.S.catFilter = '';
          this.S.gridPage = 1;
          this.closeMenu();
          this.renderChannels();
          this.buildCatTabs();
        });
      }
    });

    // Menu import button
    const mImport = document.getElementById('m-import');
    if (mImport) {
      mImport.addEventListener('click', (e) => {
        e.preventDefault();
        this.closeMenu();
        this.openImportModal();
      });
    }

    // Menu refresh button
    const mRefresh = document.getElementById('m-refresh');
    if (mRefresh) {
      mRefresh.addEventListener('click', (e) => {
        e.preventDefault();
        this.closeMenu();
        this.fetchAllData();
      });
    }

    // Theme toggles
    const themeBtn = document.getElementById('theme-btn');
    const matchThemeBtn = document.getElementById('match-theme-btn');
    if (themeBtn) themeBtn.addEventListener('click', () => this.toggleTheme());
    if (matchThemeBtn) matchThemeBtn.addEventListener('click', () => this.toggleTheme());
    if (this.E['tog-theme']) this.E['tog-theme'].addEventListener('click', () => this.toggleTheme());

    // Settings Sheet
    if (this.E['set-btn']) this.E['set-btn'].addEventListener('click', () => this.openSettings());
    if (this.E['set-ov']) this.E['set-ov'].addEventListener('click', () => this.closeSettings());
    const mSettings = document.getElementById('m-settings');
    if (mSettings) {
      mSettings.addEventListener('click', (e) => {
        e.preventDefault();
        this.closeMenu();
        this.openSettings();
      });
    }

    // Font size buttons
    document.querySelectorAll('.fsb').forEach(b => {
      b.addEventListener('click', () => this.applyFsz(b.dataset.fs));
    });

    // Autoplay toggle
    if (this.E['tog-auto']) {
      this.E['tog-auto'].addEventListener('click', () => {
        this.S.autoplay = !this.S.autoplay;
        this.E['tog-auto'].classList.toggle('on', this.S.autoplay);
        Store.setString('autoplay', String(this.S.autoplay));
      });
    }

    // Save RTDB URL in settings
    if (this.E['btn-save-rtdb']) {
      this.E['btn-save-rtdb'].addEventListener('click', () => {
        const input = this.E['setting-rtdb-url'];
        if (input && input.value.trim()) {
          this.S.rtdbUrl = input.value.trim();
          Store.setString('rtdb_url', this.S.rtdbUrl);
          this.closeSettings();
          this.fetchAllData();
        }
      });
    }

    // Contact Sheet
    const mContact = document.getElementById('m-contact');
    if (mContact) {
      mContact.addEventListener('click', (e) => {
        e.preventDefault();
        this.closeMenu();
        this.openContact();
      });
    }
    if (this.E['contact-ov']) this.E['contact-ov'].addEventListener('click', () => this.closeContact());

    // Search overlay
    if (this.E['srch-btn']) this.E['srch-btn'].addEventListener('click', () => this.openSearch());
    if (this.E['srch-close']) this.E['srch-close'].addEventListener('click', () => this.closeSearch());
    if (this.E['srch-in']) {
      this.E['srch-in'].addEventListener('input', (e) => this.handleSearch(e.target.value));
    }

    // View switchers
    ['vb-lst', 'vb-g2', 'vb-g3'].forEach(id => {
      const b = document.getElementById(id);
      if (b) {
        b.addEventListener('click', () => {
          const mode = id === 'vb-lst' ? 'lst' : (id === 'vb-g2' ? 'g2' : 'g3');
          this.applyView(mode);
        });
      }
    });

    // Pagination load more
    if (this.E['grid-more-btn']) {
      this.E['grid-more-btn'].addEventListener('click', () => {
        this.S.gridPage++;
        this.renderChannels();
      });
    }

    // Player lock & close
    if (this.E['qtv-lock-btn']) this.E['qtv-lock-btn'].addEventListener('click', () => this.toggleLock());
    if (this.E['qtv-close-btn']) this.E['qtv-close-btn'].addEventListener('click', () => this.closePlayer());

    // Player server switcher & retry
    if (this.E['pw-server-select']) {
      this.E['pw-server-select'].addEventListener('change', (e) => {
        const idx = parseInt(e.target.value, 10);
        this.currentServerIdx = idx;
        if (this.S.curCh && this.S.curCh.servers && this.S.curCh.servers[idx]) {
          this.loadStreamSource(this.S.curCh.servers[idx].url);
        }
      });
    }
    if (this.E['btn-player-retry']) {
      this.E['btn-player-retry'].addEventListener('click', () => {
        if (this.S.curCh) {
          const servers = this.S.curCh.servers || [{ url: this.S.curCh.url }];
          this.loadStreamSource(servers[this.currentServerIdx].url);
        }
      });
    }
    if (this.E['btn-player-next-server']) {
      this.E['btn-player-next-server'].addEventListener('click', () => this.nextServer());
    }

    // Hero Play Button
    if (this.E['hero-play']) {
      this.E['hero-play'].addEventListener('click', () => {
        const firstCh = this.S.channels[0];
        if (firstCh) this.playChannel(firstCh);
      });
    }

    // Match page filter buttons
    document.querySelectorAll('.mf-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.mf-btn').forEach(b => b.classList.remove('on'));
        btn.classList.add('on');
        this.renderMatches(btn.dataset.filter);
      });
    });

    // Bottom navigation
    if (this.E['bnav-live']) this.E['bnav-live'].addEventListener('click', () => this.switchPage('live'));
    if (this.E['bnav-match']) this.E['bnav-match'].addEventListener('click', () => this.switchPage('match'));

    // Import modal events
    if (this.E['btn-close-import']) this.E['btn-close-import'].addEventListener('click', () => this.closeImportModal());
    if (this.E['btn-do-import']) this.E['btn-do-import'].addEventListener('click', () => this.handleCustomImport());

    // Import Presets
    const bindPreset = (id, url, name) => {
      const el = document.getElementById(id);
      if (el) {
        el.addEventListener('click', async () => {
          if (this.E['import-url-input']) this.E['import-url-input'].value = url;
          await this.handleCustomImport();
        });
      }
    };
    bindPreset('preset-bd-sports', TAPMAD_JSON_URL, 'Tapmad BD Sports');
    bindPreset('preset-live-tv', LIVE_TV_M3U_URL, 'NAFI TV Live');
    bindPreset('preset-nafi-sports', SPORTS_M3U_URL, 'NAFI Sports');
    bindPreset('preset-movies-json', MOVIES_JSON_URL, 'Bangla Movies');
  },

  // ═══════════════════════════════════════════
  // 10. MODAL & SEARCH HANDLERS
  // ═══════════════════════════════════════════
  openMenu() {
    if (this.E['menu']) this.E['menu'].classList.add('on');
    if (this.E['menu-ov']) this.E['menu-ov'].classList.add('on');
  },
  closeMenu() {
    if (this.E['menu']) this.E['menu'].classList.remove('on');
    if (this.E['menu-ov']) this.E['menu-ov'].classList.remove('on');
  },

  openSettings() {
    if (this.E['settings']) this.E['settings'].classList.add('on');
    if (this.E['set-ov']) this.E['set-ov'].classList.add('on');
  },
  closeSettings() {
    if (this.E['settings']) this.E['settings'].classList.remove('on');
    if (this.E['set-ov']) this.E['set-ov'].classList.remove('on');
  },

  openContact() {
    if (this.E['contact-sheet']) this.E['contact-sheet'].classList.add('on');
    if (this.E['contact-ov']) this.E['contact-ov'].classList.add('on');
  },
  closeContact() {
    if (this.E['contact-sheet']) this.E['contact-sheet'].classList.remove('on');
    if (this.E['contact-ov']) this.E['contact-ov'].classList.remove('on');
  },

  openImportModal() {
    if (this.E['import-ov']) this.E['import-ov'].style.display = 'flex';
  },
  closeImportModal() {
    if (this.E['import-ov']) this.E['import-ov'].style.display = 'none';
  },

  async handleCustomImport() {
    const urlInput = this.E['import-url-input'];
    const fileInput = this.E['import-file-input'];

    const newChannels = [];
    const newMatches = [];

    if (urlInput && urlInput.value.trim()) {
      const url = urlInput.value.trim();
      await this.loadPlaylistFromUrl(url, 'Custom Playlist', newChannels, newMatches);

      // Save to custom playlists
      this.S.playlists.push({ name: 'Custom URL', url: url });
      Store.set('custom_playlists', this.S.playlists);
    } else if (fileInput && fileInput.files && fileInput.files[0]) {
      const file = fileInput.files[0];
      const text = await file.text();
      const fakeUrl = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
      await this.loadPlaylistFromUrl(fakeUrl, file.name.replace(/\.[^/.]+$/, ''), newChannels, newMatches);
    }

    if (newChannels.length > 0 || newMatches.length > 0) {
      this.S.channels = [...newChannels, ...this.S.channels];
      this.S.matches = [...newMatches, ...this.S.matches];
      this.renderChannels();
      this.buildCatTabs();
      this.renderMatches();
      this.closeImportModal();
      alert(`সফলভাবে ${newChannels.length} টি চ্যানেল লোড হয়েছে!`);
    } else {
      alert('প্লেলিস্ট ফাইল বা লিঙ্কটি সঠিক নয়। অনুগ্রহ করে পুনরায় চেষ্টা করুন।');
    }
  },

  openSearch() {
    if (this.E['srch-ov']) this.E['srch-ov'].classList.add('on');
    if (this.E['srch-in']) {
      this.E['srch-in'].value = '';
      this.E['srch-in'].focus();
    }
    if (this.E['srch-res']) {
      this.E['srch-res'].innerHTML = '<div class="empty"><i class="fas fa-search"></i><p>চ্যানেল বা ম্যাচের নাম লিখুন...</p></div>';
    }
  },
  closeSearch() {
    if (this.E['srch-ov']) this.E['srch-ov'].classList.remove('on');
  },

  handleSearch(query) {
    const q = (query || '').trim().toLowerCase();
    const resEl = this.E['srch-res'];
    if (!resEl) return;

    if (!q) {
      resEl.innerHTML = '<div class="empty"><i class="fas fa-search"></i><p>চ্যানেল বা ম্যাচের নাম লিখুন...</p></div>';
      return;
    }

    const filtered = this.S.channels.filter(ch =>
      ch.name.toLowerCase().includes(q) || (ch.category && ch.category.toLowerCase().includes(q))
    );

    if (filtered.length === 0) {
      resEl.innerHTML = '<div class="empty"><i class="fas fa-tv"></i><p>কোনো ফলাফল পাওয়া যায়নি।</p></div>';
      return;
    }

    resEl.innerHTML = filtered.map(ch => `
      <div class="cc" data-id="${escHtml(ch.id)}" style="margin-bottom: 8px; display: flex; align-items: center; padding: 10px; background: var(--card); border-radius: 10px;">
        <img src="${escHtml(ch.logo || FALLBACK_LOGO_SVG)}" style="width: 38px; height: 38px; object-fit: contain; margin-right: 12px;" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
        <div style="flex: 1;">
          <div style="font-weight: 700; font-size: 0.86rem; color: #fff;">${escHtml(ch.name)}</div>
          <div style="font-size: 0.72rem; color: var(--t2);">${escHtml(ch.category || 'TV')}</div>
        </div>
        <button class="mc-watch-btn btn-live" style="padding: 6px 12px; font-size: 0.72rem;">
          <i class="fas fa-play"></i> প্লে
        </button>
      </div>
    `).join('');

    resEl.querySelectorAll('.cc').forEach(el => {
      el.addEventListener('click', () => {
        const ch = this.S.channels.find(c => c.id === el.dataset.id);
        if (ch) {
          this.closeSearch();
          this.playChannel(ch);
        }
      });
    });
  }
};

// Start application when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
  APP.init();
});
