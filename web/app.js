/**
 * NAFI TV 24 – Web Edition Core Application Script
 * Complete integration: HLS Video Player, Firebase RTDB (Live TV, Movies, Sports, Playlists),
 * Multi-Server Fallback, Category Filters, and 4 Main Section Tabs.
 */

'use strict';

// ═══════════════════════════════════════════
// 1. CONSTANTS & FIREBASE RTDB CONFIG
// ═══════════════════════════════════════════
const DEFAULT_RTDB_URL = 'https://nafitv24-live-default-rtdb.firebaseio.com';
const FALLBACK_LOGO_SVG = 'favicon.svg';

// Built-in verified channels for zero-downtime start
const BUILT_IN_CHANNELS = [
  {
    id: 'tsports_hd',
    name: 'T Sports HD',
    category: 'Sports',
    logo: 'https://flagcdn.com/w160/bd.png',
    url: 'https://live-tsports.akamaized.net/live/tsports/master.m3u8',
    servers: [
      { name: 'সার্ভার ১ (HLS)', url: 'https://live-tsports.akamaized.net/live/tsports/master.m3u8' },
      { name: 'সার্ভার ২ (HD)', url: 'http://103.185.24.134:3001/TSportsHD/tracks-v1a1/mono.m3u8' },
      { name: 'সার্ভার ৩', url: 'https://tvsen5.aynaott.com/TnMn5kZz8aLm/tracks-v1a1/mono.ts.m3u8' }
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
      { name: 'সার্ভার ১ (HD)', url: 'https://stream.crichd.vip/live/starsports1.m3u8' },
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
      { name: 'সার্ভার ১', url: 'https://stream.crichd.vip/live/sonysportsten1.m3u8' },
      { name: 'সার্ভার ২', url: 'https://drk6xq0vhn.gpcdn.net/live/ten_1_hd_720/index.m3u8' }
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
      { name: 'সার্ভার ১', url: 'https://stream.crichd.vip/live/willowusa.m3u8' },
      { name: 'সার্ভার ২', url: 'https://digitalotthub.com/tv/dd/live.php/598.m3u8' }
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
    activeTab: 'livetv', // 'livetv' | 'movies' | 'sports' | 'playlists' | 'schedule'
    channels: [...BUILT_IN_CHANNELS],
    movies: [],
    sports: [],
    playlists: [],
    matches: [],
    customPlaylists: Store.get('custom_playlists', []),
    fav: Store.get('fav_channels', []),
    rec: Store.get('rec_channels', []),
    tvCatFilter: '',
    movieCatFilter: '',
    sportsFilter: 'all',
    scheduleFilter: 'all',
    view: Store.getString('channel_view', 'g3'),
    dark: Store.getString('theme_mode', 'dark') !== 'light',
    autoplay: Store.getString('autoplay', 'true') !== 'false',
    fsz: Store.getString('font_size', 'md'),
    rtdbUrl: Store.getString('rtdb_url', DEFAULT_RTDB_URL),
    gridPage: 1,
    PAGE_SIZE: 36,
    curItem: null,
    currentServerIdx: 0,
    isLocked: false
  },

  E: {},

  init() {
    this.cacheElements();
    this.applyTheme();
    this.applyView(this.S.view);
    this.applyFsz(this.S.fsz);

    // Initial Renders
    this.renderAllViews();

    // Fetch all Firebase Data & Admin Playlists
    this.fetchAllData();

    // Bind event listeners
    this.bindEvents();

    // Hide loader
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
      'pw-bar', 'pw-logo', 'pw-name', 'pw-category', 'pw-server-select',
      'video-loader', 'video-error', 'player-err-msg',
      'btn-player-retry', 'btn-player-next-server',
      'notice', 'notice-txt',
      'grid', 'grid-more-wrap', 'grid-more-btn', 'cat-tabs',
      'movies-grid', 'movies-more-wrap', 'movies-more-btn', 'movie-cat-tabs', 'movie-search-in',
      'sports-grid', 'playlists-grid', 'match-list',
      'badge-tv-count', 'badge-movies-count', 'badge-sports-count', 'badge-playlists-count',
      'tv-total-count', 'movies-total-count', 'sports-total-count', 'playlists-total-count',
      'setting-rtdb-url', 'btn-save-rtdb'
    ];
    ids.forEach(id => {
      this.E[id] = document.getElementById(id);
    });

    if (this.E['setting-rtdb-url']) {
      this.E['setting-rtdb-url'].value = this.S.rtdbUrl;
    }
  },

  // ═══════════════════════════════════════════
  // 4. FETCHING & PARSING ALL FIREBASE DATA
  // ═══════════════════════════════════════════
  async fetchAllData() {
    const rtdbBase = this.S.rtdbUrl.replace(/\/+$/, '');

    // Fetch endpoints in parallel
    const endpoints = [
      `${rtdbBase}/app_config.json`,
      `${rtdbBase}/channels.json`,
      `${rtdbBase}/movies.json`,
      `${rtdbBase}/sports.json`,
      `${rtdbBase}/matches.json`,
      `${rtdbBase}/events.json`,
      `${rtdbBase}/playlists.json`,
      `${rtdbBase}/marquee_news.json`
    ];

    const [
      fbAppConfig,
      fbChannels,
      fbMovies,
      fbSports,
      fbMatches,
      fbEvents,
      fbPlaylists,
      fbMarqueeNews
    ] = await Promise.all(
      endpoints.map(url =>
        fetch(url, { cache: 'no-store' })
          .then(res => res.ok ? res.json() : null)
          .catch(() => null)
      )
    );

    // 1. Marquee News
    if (fbMarqueeNews && fbMarqueeNews.marquee_ticker) {
      if (this.E['notice-txt']) {
        this.E['notice-txt'].textContent = fbMarqueeNews.marquee_ticker;
      }
    }

    const newChannels = [];
    const newMovies = [];
    const newSports = [];
    const newPlaylists = [];
    const newMatches = [];

    // 2. Process Firebase RTDB /channels.json
    if (fbChannels && typeof fbChannels === 'object') {
      this.normalizeItems(fbChannels, 'Bangla TV').forEach(ch => newChannels.push(ch));
    }

    // 3. Process Firebase RTDB /movies.json
    if (fbMovies && typeof fbMovies === 'object') {
      this.normalizeMovies(fbMovies).forEach(m => newMovies.push(m));
    }

    // 4. Process Firebase RTDB /sports.json
    if (fbSports && typeof fbSports === 'object') {
      this.normalizeSports(fbSports).forEach(sp => {
        newSports.push(sp);
        newMatches.push(this.sportToMatch(sp));
      });
    }

    // 5. Process Firebase RTDB /matches.json & /events.json
    if (fbMatches && typeof fbMatches === 'object') {
      this.normalizeMatches(fbMatches).forEach(m => {
        newMatches.push(m);
        if (m.streamUrl) newSports.push(this.matchToSport(m));
      });
    }
    if (fbEvents && typeof fbEvents === 'object') {
      this.normalizeMatches(fbEvents).forEach(m => {
        newMatches.push(m);
        if (m.streamUrl) newSports.push(this.matchToSport(m));
      });
    }

    // 6. Process Firebase RTDB /playlists.json
    if (fbPlaylists && typeof fbPlaylists === 'object') {
      const plArray = Array.isArray(fbPlaylists)
        ? fbPlaylists
        : Object.entries(fbPlaylists).map(([id, p]) => ({ id, ...p }));

      for (const pl of plArray) {
        if (pl && (pl.url || pl.serverUrl)) {
          const plUrl = pl.url || pl.serverUrl;
          const plName = pl.name || pl.title || 'Playlist';
          const plItem = {
            id: pl.id || 'pl_' + Math.random().toString(36).substring(2, 8),
            name: plName,
            description: pl.description || 'NAFI TV Cloud Synced Playlist',
            logo: pl.logo || pl.logoUrl || FALLBACK_LOGO_SVG,
            url: plUrl,
            type: pl.type || (plUrl.includes('.json') ? 'JSON' : 'M3U'),
            channelCount: pl.channelCount || 0
          };
          newPlaylists.push(plItem);

          // Fetch the channels inside this playlist
          try {
            await this.loadPlaylistContent(plUrl, plName, newChannels, newMovies, newSports, plItem);
          } catch (e) {
            console.warn('Error loading playlist:', plName, e);
          }
        }
      }
    }

    // 7. Process Central app_config URLs (Live TV M3U, Movies M3U/JSON, Sports M3U/JSON)
    if (fbAppConfig && typeof fbAppConfig === 'object') {
      // (a) Live TV M3U URLs (newline separated)
      if (fbAppConfig.liveTvM3uUrl || fbAppConfig.liveTvM3u) {
        const liveTvUrls = this.splitUrls(fbAppConfig.liveTvM3uUrl || fbAppConfig.liveTvM3u);
        for (const url of liveTvUrls) {
          const plItem = {
            id: 'cfg_tv_' + Math.random().toString(36).substring(2, 8),
            name: this.extractPlaylistName(url, 'Live TV M3U'),
            description: 'অ্যাডমিন প্যানেল থেকে কনফিগার করা লাইভ টিভি',
            logo: FALLBACK_LOGO_SVG,
            url: url,
            type: url.includes('.json') ? 'JSON' : 'M3U',
            channelCount: 0
          };
          newPlaylists.push(plItem);
          await this.loadPlaylistContent(url, 'Live TV', newChannels, newMovies, newSports, plItem);
        }
      }

      // (b) Movies M3U/JSON URLs (newline separated)
      if (fbAppConfig.moviesM3uUrl || fbAppConfig.moviesM3u) {
        const movieUrls = this.splitUrls(fbAppConfig.moviesM3uUrl || fbAppConfig.moviesM3u);
        for (const url of movieUrls) {
          const plItem = {
            id: 'cfg_mov_' + Math.random().toString(36).substring(2, 8),
            name: this.extractPlaylistName(url, 'Movies Collection'),
            description: 'মুভি ও ওয়েব সিরিজ কালেকশন',
            logo: 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=200&fit=crop',
            url: url,
            type: url.includes('.json') ? 'JSON' : 'M3U',
            channelCount: 0
          };
          newPlaylists.push(plItem);
          await this.loadPlaylistContent(url, 'Movies', newChannels, newMovies, newSports, plItem);
        }
      }

      // (c) Sports M3U/JSON URLs (newline separated)
      if (fbAppConfig.sportsM3uUrl || fbAppConfig.sportsM3u) {
        const sportsUrls = this.splitUrls(fbAppConfig.sportsM3uUrl || fbAppConfig.sportsM3u);
        for (const url of sportsUrls) {
          const plItem = {
            id: 'cfg_sp_' + Math.random().toString(36).substring(2, 8),
            name: this.extractPlaylistName(url, 'Live Sports M3U'),
            description: 'লাইভ স্পোর্টস ও ম্যাচ কালেকশন',
            logo: 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=200&fit=crop',
            url: url,
            type: url.includes('.json') ? 'JSON' : 'M3U',
            channelCount: 0
          };
          newPlaylists.push(plItem);
          await this.loadPlaylistContent(url, 'Sports', newChannels, newMovies, newSports, plItem);
        }
      }

      // (d) Tapmad Sports JSON URL
      if (fbAppConfig.tapmadJsonUrl) {
        const plItem = {
          id: 'cfg_tapmad',
          name: 'Tapmad BD Sports',
          description: 'ট্যাপম্যাড স্পোর্টস লাইভ স্ট্রিমস ও ম্যাচ',
          logo: 'https://flagcdn.com/w160/bd.png',
          url: fbAppConfig.tapmadJsonUrl,
          type: 'JSON',
          channelCount: 0
        };
        newPlaylists.push(plItem);
        await this.loadPlaylistContent(fbAppConfig.tapmadJsonUrl, 'Sports', newChannels, newMovies, newSports, plItem);
      }
    }

    // 8. Custom saved playlists from localStorage
    if (this.S.customPlaylists && this.S.customPlaylists.length > 0) {
      for (const pl of this.S.customPlaylists) {
        if (pl.url) {
          const plItem = {
            id: 'custom_' + Math.random().toString(36).substring(2, 8),
            name: pl.name || 'Custom Playlist',
            description: 'ইউজার কর্তৃক যোগ করা কাস্টম প্লেলিস্ট',
            logo: FALLBACK_LOGO_SVG,
            url: pl.url,
            type: pl.url.includes('.json') ? 'JSON' : 'M3U',
            channelCount: 0
          };
          newPlaylists.push(plItem);
          await this.loadPlaylistContent(pl.url, 'Custom', newChannels, newMovies, newSports, plItem);
        }
      }
    }

    // Deduplicate Channels
    const mergedChannels = [...newChannels, ...BUILT_IN_CHANNELS];
    const uniqueChannels = [];
    const seenCh = new Set();
    mergedChannels.forEach(c => {
      if (c && c.url && !seenCh.has(c.name.toLowerCase().trim())) {
        seenCh.add(c.name.toLowerCase().trim());
        uniqueChannels.push(c);
      }
    });

    // Deduplicate Movies
    const uniqueMovies = [];
    const seenMov = new Set();
    newMovies.forEach(m => {
      const key = (m.title || m.name || '').toLowerCase().trim();
      if (key && !seenMov.has(key)) {
        seenMov.add(key);
        uniqueMovies.push(m);
      }
    });

    // Deduplicate Sports
    const uniqueSports = [];
    const seenSp = new Set();
    newSports.forEach(s => {
      const key = (s.title || s.name || '').toLowerCase().trim();
      if (key && !seenSp.has(key)) {
        seenSp.add(key);
        uniqueSports.push(s);
      }
    });

    // Deduplicate Playlists
    const uniquePlaylists = [];
    const seenPl = new Set();
    newPlaylists.forEach(p => {
      if (p.url && !seenPl.has(p.url)) {
        seenPl.add(p.url);
        uniquePlaylists.push(p);
      }
    });

    // Assign to state
    this.S.channels = uniqueChannels;
    this.S.movies = uniqueMovies;
    this.S.sports = uniqueSports;
    this.S.playlists = uniquePlaylists;
    this.S.matches = newMatches;

    // Update Counts
    this.updateBadges();

    // Re-render UI
    this.renderAllViews();
    this.renderUpcomingCarousel();
  },

  splitUrls(raw) {
    if (!raw || typeof raw !== 'string') return [];
    return raw
      .split(/[\r\n,]+/)
      .map(u => u.trim())
      .filter(u => u.startsWith('http://') || u.startsWith('https://'));
  },

  extractPlaylistName(url, fallback) {
    try {
      const decoded = decodeURIComponent(url);
      const parts = decoded.split('/');
      const last = parts[parts.length - 1];
      if (last && last.length > 3) {
        return last.replace(/\.[^/.]+$/, '').replace(/[_-]/g, ' ');
      }
    } catch {}
    return fallback;
  },

  // ═══════════════════════════════════════════
  // 5. UNIVERSAL PLAYLIST & JSON PARSER
  // ═══════════════════════════════════════════
  async loadPlaylistContent(url, defaultCategory, outChannels, outMovies, outSports, plItemObj) {
    if (!url || typeof url !== 'string') return;
    let addedCount = 0;

    try {
      const res = await fetch(url.trim(), { cache: 'no-store' });
      if (!res.ok) return;
      const text = await res.text();
      const trimmed = text.trim();

      // CASE A: JSON (Categories, Movies, Tapmad, or Footy Live)
      if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
        try {
          const json = JSON.parse(trimmed);

          // 1. Category-based JSON (e.g. Update Channel.m3u or movies.json: { categories: [...] })
          if (json.categories && Array.isArray(json.categories)) {
            json.categories.forEach(catObj => {
              const catName = catObj.category_name || defaultCategory;
              const items = catObj.movies || catObj.channels || [];
              items.forEach(it => {
                const title = it.title || it.name || 'Untitled';
                const poster = it.poster || it.logo || it.icon || '';
                const servers = (it.sources || it.servers || []).map(s => ({
                  name: s.server_name || s.name || 'Server',
                  url: s.url || s.streamUrl
                })).filter(s => s.url);

                const primaryUrl = (servers[0] ? servers[0].url : '') || it.url || it.streamUrl;

                if (primaryUrl) {
                  addedCount++;
                  // Determine if movie or TV channel based on category name or fields
                  const isMovie = defaultCategory === 'Movies' ||
                    catName.toLowerCase().includes('movie') ||
                    catName.toLowerCase().includes('cinema') ||
                    catName.toLowerCase().includes('serial') ||
                    it.year || it.rating;

                  if (isMovie) {
                    outMovies.push({
                      id: 'mov_' + Math.random().toString(36).substring(2, 8),
                      title: title,
                      category: catName,
                      poster: poster,
                      url: primaryUrl,
                      servers: servers,
                      rating: it.rating || 'HD',
                      year: it.year || '',
                      description: it.description || ''
                    });
                  } else {
                    outChannels.push({
                      id: 'ch_' + Math.random().toString(36).substring(2, 8),
                      name: title,
                      category: catName,
                      logo: poster,
                      url: primaryUrl,
                      servers: servers,
                      isLive: true
                    });
                  }
                }
              });
            });
          }

          // 2. Tapmad Sports Matches JSON ({ Matches: [...] })
          else if (json.Matches && Array.isArray(json.Matches)) {
            json.Matches.forEach(tMatch => {
              const title = tMatch.VideoName || 'Live Sports';
              const isLive = String(tMatch.Status).toLowerCase() === 'live';
              const sUrl = tMatch.stream_url || '';
              if (sUrl) {
                addedCount++;
                const spObj = {
                  id: 'tapmad_' + (tMatch.EntityId || Math.random().toString(36).substring(2, 7)),
                  title: title,
                  name: title,
                  category: tMatch.CategoryName || 'Sports',
                  logo: tMatch.ThumbnailStandard || '',
                  poster: tMatch.ThumbnailStandard || '',
                  url: sUrl,
                  servers: [{ name: 'Tapmad Live', url: sUrl }],
                  isLive: isLive,
                  time: tMatch.EventStartDate || 'Live Now',
                  tournament: tMatch.CategoryName || 'Tapmad Sports'
                };
                outSports.push(spObj);
                outChannels.push({
                  id: spObj.id,
                  name: title,
                  category: 'Sports',
                  logo: spObj.logo,
                  url: sUrl,
                  isLive: isLive
                });
              }
            });
          }

          // 3. Footy Live / CricHD Matches JSON ({ matches: [...] })
          else if (json.matches && Array.isArray(json.matches)) {
            json.matches.forEach(m => {
              const mName = m['match name'] || m.title || 'Live Match';
              const channels = m.Channels || [];
              const servers = channels.map(c => ({
                name: c.channel_name || 'Server',
                url: c.url
              })).filter(s => s.url);

              const sUrl = servers[0] ? servers[0].url : (m.streamUrl || '');
              if (sUrl || servers.length > 0) {
                addedCount++;
                const spObj = {
                  id: 'ft_' + Math.random().toString(36).substring(2, 8),
                  title: mName,
                  name: mName,
                  category: m.Category || 'Football',
                  logo: m['Team 1 Logo'] || '',
                  poster: m['Team 1 Logo'] || '',
                  team1: m['Team 1 Name'] || '',
                  team1Logo: m['Team 1 Logo'] || '',
                  team2: m['Team 2 Name'] || '',
                  team2Logo: m['Team 2 Logo'] || '',
                  url: sUrl,
                  servers: servers,
                  isLive: String(m.Status).toLowerCase() === 'live',
                  time: m['Start time'] || 'Live',
                  tournament: m['Tour/Group name'] || 'Live Tournament'
                };
                outSports.push(spObj);
              }
            });
          }

          // 4. Standard Array of Items ([ { name, url, logo, category } ])
          else if (Array.isArray(json)) {
            json.forEach(it => {
              const sUrl = it.url || it.streamUrl || (it.servers && it.servers[0] ? it.servers[0].url : '');
              if (sUrl) {
                addedCount++;
                const itemCat = it.category || defaultCategory;
                const isMov = defaultCategory === 'Movies' || itemCat.toLowerCase().includes('movie');

                if (isMov) {
                  outMovies.push({
                    id: 'm_' + Math.random().toString(36).substring(2, 8),
                    title: it.name || it.title || 'Movie',
                    category: itemCat,
                    poster: it.logo || it.poster || '',
                    url: sUrl,
                    servers: it.servers || [{ name: 'Server 1', url: sUrl }]
                  });
                } else {
                  outChannels.push({
                    id: 'ch_' + Math.random().toString(36).substring(2, 8),
                    name: it.name || it.title || 'Channel',
                    category: itemCat,
                    logo: it.logo || it.icon || '',
                    url: sUrl,
                    servers: it.servers || [{ name: 'Server 1', url: sUrl }],
                    isLive: it.isLive !== false
                  });
                }
              }
            });
          }

          if (plItemObj) plItemObj.channelCount = addedCount;
          return;
        } catch (jsonErr) {
          console.warn('JSON parse error on', url, jsonErr);
        }
      }

      // CASE B: Standard M3U / M3U8 Playlist
      if (trimmed.includes('#EXTM3U') || trimmed.includes('#EXTINF') || trimmed.includes('http')) {
        const lines = trimmed.split(/\r?\n/);
        let curName = '';
        let curLogo = '';
        let curCat = defaultCategory;

        for (let i = 0; i < lines.length; i++) {
          const line = lines[i].trim();
          if (line.startsWith('#EXTINF:')) {
            const logoMatch = line.match(/tvg-logo="([^"]+)"/i);
            curLogo = logoMatch ? logoMatch[1] : '';

            const groupMatch = line.match(/group-title="([^"]+)"/i);
            curCat = groupMatch ? groupMatch[1] : defaultCategory;

            const commaIdx = line.lastIndexOf(',');
            curName = commaIdx !== -1 ? line.substring(commaIdx + 1).trim() : 'Channel';
          } else if (line.startsWith('http://') || line.startsWith('https://')) {
            if (curName && line) {
              addedCount++;
              const isMov = defaultCategory === 'Movies' || curCat.toLowerCase().includes('movie') || curCat.toLowerCase().includes('cinema');

              if (isMov) {
                outMovies.push({
                  id: 'm3u_mov_' + Math.random().toString(36).substring(2, 8),
                  title: curName,
                  category: curCat,
                  poster: curLogo,
                  url: line,
                  servers: [{ name: 'Main Server', url: line }]
                });
              } else {
                outChannels.push({
                  id: 'm3u_' + Math.random().toString(36).substring(2, 8),
                  name: curName,
                  category: curCat,
                  logo: curLogo,
                  url: line,
                  servers: [{ name: 'সার্ভার ১', url: line }],
                  isLive: true
                });
              }
            }
            curName = '';
            curLogo = '';
            curCat = defaultCategory;
          }
        }
      }

      if (plItemObj) plItemObj.channelCount = addedCount;
    } catch (err) {
      console.warn('loadPlaylistContent error on', url, err);
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

  normalizeMovies(objOrArr) {
    const list = Array.isArray(objOrArr) ? objOrArr : Object.entries(objOrArr).map(([k, v]) => ({ id: k, ...v }));
    return list.map(m => {
      const servers = m.servers && Array.isArray(m.servers)
        ? m.servers
        : (m.streamUrl ? [{ name: 'সার্ভার ১', url: m.streamUrl }] : []);

      if (m.backupUrl && !servers.some(s => s.url === m.backupUrl)) {
        servers.push({ name: 'NAFI SERVER', url: m.backupUrl });
      }

      return {
        id: m.id || 'mov_' + Math.random().toString(36).substring(2, 7),
        title: m.title || m.name || 'Movie',
        category: m.category || 'NAFI OTT Movies',
        poster: m.poster || m.logo || m.logoUrl || '',
        url: m.streamUrl || m.url || (servers[0] ? servers[0].url : ''),
        servers: servers,
        rating: m.rating || 'HD',
        year: m.year || ''
      };
    }).filter(m => m.url);
  },

  normalizeSports(objOrArr) {
    const list = Array.isArray(objOrArr) ? objOrArr : Object.entries(objOrArr).map(([k, v]) => ({ id: k, ...v }));
    return list.map(s => {
      const servers = s.servers && Array.isArray(s.servers)
        ? s.servers
        : (s.streamUrl ? [{ name: 'সার্ভার ১', url: s.streamUrl }] : []);

      if (s.backupUrl && !servers.some(x => x.url === s.backupUrl)) {
        servers.push({ name: 'ব্যাকআপ সার্ভার', url: s.backupUrl });
      }

      return {
        id: s.id || 'sp_' + Math.random().toString(36).substring(2, 7),
        title: s.title || s.name || 'Live Sports',
        name: s.name || s.title || 'Live Sports',
        category: s.category || s.sport || 'Cricket',
        tournament: s.tournament || s.title || 'Live Sports',
        poster: s.poster || s.logo || s.logoUrl || '',
        logo: s.logo || s.logoUrl || s.poster || '',
        team1: s.team1 || s.title?.split(' vs ')[0] || '',
        team1Logo: s.team1Logo || '',
        team2: s.team2 || s.title?.split(' vs ')[1] || '',
        team2Logo: s.team2Logo || '',
        score: s.score1 && s.score2 ? `${s.score1} - ${s.score2}` : (s.isLive ? 'LIVE' : 'VS'),
        time: s.matchTimeFormatted || s.eventTime || 'Live Now',
        status: s.status || (s.isLive ? '● Live Now' : 'Upcoming'),
        isLive: s.isLive !== false,
        url: s.streamUrl || s.url || (servers[0] ? servers[0].url : ''),
        servers: servers
      };
    }).filter(s => s.url || (s.servers && s.servers.length > 0));
  },

  normalizeMatches(objOrArr) {
    const list = Array.isArray(objOrArr) ? objOrArr : Object.entries(objOrArr).map(([k, v]) => ({ id: k, ...v }));
    return list.map(m => ({
      id: m.id || 'm_' + Math.random().toString(36).substring(2, 7),
      title: m.title || (m.team1 && m.team2 ? `${m.team1} vs ${m.team2}` : 'Live Match'),
      league: m.tournament || m.league || m.category || 'Sports',
      status: (m.status || (m.isLive ? 'live' : 'upcoming')).toLowerCase().includes('live') ? 'live' : 'upcoming',
      team1: {
        name: m.team1 || 'Team 1',
        logo: m.team1Logo || ''
      },
      team2: {
        name: m.team2 || 'Team 2',
        logo: m.team2Logo || ''
      },
      score: m.score || 'VS',
      time: m.matchTimeFormatted || m.eventTime || m.time || 'Live Now',
      streamUrl: m.streamUrl || m.url || '',
      servers: m.servers || (m.streamUrl ? [{ name: 'Server 1', url: m.streamUrl }] : [])
    }));
  },

  sportToMatch(sp) {
    return {
      id: 'm_' + sp.id,
      title: sp.title,
      league: sp.tournament || sp.category,
      status: sp.isLive ? 'live' : 'upcoming',
      team1: { name: sp.team1 || sp.title, logo: sp.team1Logo || sp.logo },
      team2: { name: sp.team2 || 'Match', logo: sp.team2Logo || sp.logo },
      score: sp.score || (sp.isLive ? 'LIVE' : 'VS'),
      time: sp.time,
      streamUrl: sp.url,
      servers: sp.servers
    };
  },

  matchToSport(m) {
    return {
      id: 'sp_' + m.id,
      title: m.title,
      name: m.title,
      category: m.league,
      tournament: m.league,
      poster: m.team1.logo,
      logo: m.team1.logo,
      team1: m.team1.name,
      team1Logo: m.team1.logo,
      team2: m.team2.name,
      team2Logo: m.team2.logo,
      score: m.score,
      time: m.time,
      isLive: m.status === 'live',
      url: m.streamUrl,
      servers: m.servers
    };
  },

  updateBadges() {
    if (this.E['badge-tv-count']) this.E['badge-tv-count'].textContent = this.S.channels.length;
    if (this.E['badge-movies-count']) this.E['badge-movies-count'].textContent = this.S.movies.length;
    if (this.E['badge-sports-count']) this.E['badge-sports-count'].textContent = this.S.sports.length;
    if (this.E['badge-playlists-count']) this.E['badge-playlists-count'].textContent = this.S.playlists.length;

    if (this.E['tv-total-count']) this.E['tv-total-count'].textContent = `${this.S.channels.length} টি চ্যানেল`;
    if (this.E['movies-total-count']) this.E['movies-total-count'].textContent = `${this.S.movies.length} টি মুভি`;
    if (this.E['sports-total-count']) this.E['sports-total-count'].textContent = `${this.S.sports.length} টি স্পোর্টস স্ট্রিম`;
    if (this.E['playlists-total-count']) this.E['playlists-total-count'].textContent = `${this.S.playlists.length} টি প্লেলিস্ট`;
  },

  // ═══════════════════════════════════════════
  // 6. RENDER ALL 4 MAIN VIEWS + SCHEDULE
  // ═══════════════════════════════════════════
  renderAllViews() {
    this.renderChannels();
    this.buildTvCatTabs();
    this.renderMovies();
    this.buildMovieCatTabs();
    this.renderSports();
    this.renderPlaylists();
    this.renderSchedule();
  },

  switchTab(tabId) {
    this.S.activeTab = tabId;

    // Update top section tab buttons
    document.querySelectorAll('.main-tab-btn').forEach(btn => {
      btn.classList.toggle('on', btn.dataset.tab === tabId);
      btn.setAttribute('aria-selected', btn.dataset.tab === tabId ? 'true' : 'false');
    });

    // Update bottom nav buttons
    document.querySelectorAll('.bnav-btn').forEach(btn => {
      btn.classList.toggle('on', btn.dataset.tab === tabId);
    });

    // Update side menu
    document.querySelectorAll('.mi').forEach(m => {
      if (m.id === `m-${tabId}`) m.classList.add('on');
      else m.classList.remove('on');
    });

    // Hide all tab views, show active one
    const views = ['livetv', 'movies', 'sports', 'playlists', 'schedule'];
    views.forEach(v => {
      const el = document.getElementById(`view-${v}`);
      if (el) el.style.display = (v === tabId) ? 'block' : 'none';
    });

    // Scroll top of view into view if needed
    window.scrollTo({ top: 0, behavior: 'smooth' });
  },

  // ── (A) LIVE TV VIEW ──
  buildTvCatTabs() {
    const tabsContainer = this.E['cat-tabs'];
    if (!tabsContainer) return;

    const categories = ['All', 'Favorites', 'Recent'];
    const seen = new Set(['All', 'Favorites', 'Recent']);

    this.S.channels.forEach(ch => {
      const cat = (ch.category || 'General').trim();
      if (!seen.has(cat)) {
        seen.add(cat);
        categories.push(cat);
      }
    });

    tabsContainer.innerHTML = categories.map(c => {
      const isSel = (c === 'All' && !this.S.tvCatFilter) || this.S.tvCatFilter === c;
      return `
        <button class="cat-tab ${isSel ? 'on' : ''}" data-cat="${escHtml(c)}">
          <span>${escHtml(c)}</span>
        </button>
      `;
    }).join('');

    tabsContainer.querySelectorAll('.cat-tab').forEach(btn => {
      btn.addEventListener('click', () => {
        const cat = btn.dataset.cat;
        this.S.tvCatFilter = cat === 'All' ? '' : cat;
        this.S.gridPage = 1;
        this.buildTvCatTabs();
        this.renderChannels();
      });
    });
  },

  renderChannels() {
    const grid = this.E['grid'];
    if (!grid) return;

    let list = [...this.S.channels];

    if (this.S.tvCatFilter === 'Favorites') {
      list = list.filter(ch => this.S.fav.includes(ch.id));
    } else if (this.S.tvCatFilter === 'Recent') {
      list = this.S.rec.map(id => list.find(ch => ch.id === id)).filter(Boolean);
    } else if (this.S.tvCatFilter) {
      list = list.filter(ch => (ch.category || 'General').trim() === this.S.tvCatFilter);
    }

    list.sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0));

    if (list.length === 0) {
      grid.innerHTML = `
        <div class="empty" style="grid-column: 1 / -1; padding: 40px; text-align: center;">
          <i class="fas fa-tv" style="font-size: 2rem; color: var(--t3); margin-bottom: 10px;"></i>
          <p style="color: var(--t2);">কোনো চ্যানেল পাওয়া যায়নি।</p>
        </div>
      `;
      if (this.E['grid-more-wrap']) this.E['grid-more-wrap'].style.display = 'none';
      return;
    }

    const countToShow = Math.min(this.S.gridPage * this.S.PAGE_SIZE, list.length);
    const slice = list.slice(0, countToShow);

    grid.innerHTML = slice.map(ch => this.createChannelCardHTML(ch)).join('');

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

    grid.querySelectorAll('.cc').forEach(card => {
      card.addEventListener('click', (e) => {
        if (e.target.closest('.fstar')) return;
        const id = card.dataset.id;
        const targetCh = this.S.channels.find(c => c.id === id);
        if (targetCh) this.playItem(targetCh);
      });

      const favBtn = card.querySelector('.fstar');
      if (favBtn) {
        favBtn.addEventListener('click', (e) => {
          e.stopPropagation();
          this.toggleFav(card.dataset.id);
          this.renderChannels();
        });
      }
    });
  },

  createChannelCardHTML(ch) {
    const isPlaying = this.S.curItem && this.S.curItem.id === ch.id;
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
          <button class="fstar ${isFav ? 'on' : ''}" title="পছন্দ তালিকা">
            <i class="fas fa-star"></i>
          </button>
        </div>
      </div>
    `;
  },

  // ── (B) MOVIES & SERIES VIEW ──
  buildMovieCatTabs() {
    const container = this.E['movie-cat-tabs'];
    if (!container) return;

    const categories = ['All'];
    const seen = new Set(['All']);

    this.S.movies.forEach(m => {
      const cat = (m.category || 'General').trim();
      if (!seen.has(cat)) {
        seen.add(cat);
        categories.push(cat);
      }
    });

    container.innerHTML = categories.map(c => {
      const isSel = (c === 'All' && !this.S.movieCatFilter) || this.S.movieCatFilter === c;
      return `
        <button class="cat-tab ${isSel ? 'on' : ''}" data-cat="${escHtml(c)}">
          <span>${escHtml(c)}</span>
        </button>
      `;
    }).join('');

    container.querySelectorAll('.cat-tab').forEach(btn => {
      btn.addEventListener('click', () => {
        const cat = btn.dataset.cat;
        this.S.movieCatFilter = cat === 'All' ? '' : cat;
        this.buildMovieCatTabs();
        this.renderMovies();
      });
    });
  },

  renderMovies(query = '') {
    const grid = this.E['movies-grid'];
    if (!grid) return;

    let list = [...this.S.movies];

    if (this.S.movieCatFilter) {
      list = list.filter(m => (m.category || '').trim() === this.S.movieCatFilter);
    }

    if (query) {
      const q = query.toLowerCase();
      list = list.filter(m => m.title.toLowerCase().includes(q) || (m.category || '').toLowerCase().includes(q));
    }

    if (list.length === 0) {
      grid.innerHTML = `
        <div class="empty" style="grid-column: 1 / -1; padding: 40px; text-align: center;">
          <i class="fas fa-film" style="font-size: 2rem; color: var(--t3); margin-bottom: 10px;"></i>
          <p style="color: var(--t2);">কোনো মুভি পাওয়া যায়নি।</p>
        </div>
      `;
      return;
    }

    grid.innerHTML = list.map(m => {
      const poster = m.poster || 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=300&fit=crop';
      const serverCount = m.servers ? m.servers.length : 1;

      return `
        <div class="movie-card" data-id="${escHtml(m.id)}" role="listitem">
          <div class="movie-poster-w">
            <img class="movie-poster" src="${escHtml(poster)}" loading="lazy" alt="${escHtml(m.title)}" onerror="this.src='https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=300&fit=crop'" />
            <div class="movie-badge-hd">${escHtml(m.rating || 'HD')}</div>
            ${serverCount > 1 ? `<div class="movie-badge-sources">${serverCount} Servers</div>` : ''}
            <div class="movie-overlay-play">
              <div class="movie-play-ico"><i class="fas fa-play"></i></div>
            </div>
          </div>
          <div class="movie-info">
            <div class="movie-title" title="${escHtml(m.title)}">${escHtml(m.title)}</div>
            <div class="movie-cat">${escHtml(m.category)} ${m.year ? `• ${m.year}` : ''}</div>
          </div>
        </div>
      `;
    }).join('');

    grid.querySelectorAll('.movie-card').forEach(card => {
      card.addEventListener('click', () => {
        const id = card.dataset.id;
        const targetMov = this.S.movies.find(m => m.id === id);
        if (targetMov) this.playItem(targetMov);
      });
    });
  },

  // ── (C) SPORTS MATCHES VIEW ──
  renderSports() {
    const grid = this.E['sports-grid'];
    if (!grid) return;

    let list = [...this.S.sports];

    if (this.S.sportsFilter === 'live') {
      list = list.filter(s => s.isLive);
    } else if (this.S.sportsFilter === 'cricket') {
      list = list.filter(s => (s.category || '').toLowerCase().includes('cricket'));
    } else if (this.S.sportsFilter === 'football') {
      list = list.filter(s => (s.category || '').toLowerCase().includes('football'));
    } else if (this.S.sportsFilter === 'upcoming') {
      list = list.filter(s => !s.isLive);
    }

    if (list.length === 0) {
      grid.innerHTML = `
        <div class="empty" style="padding: 40px; text-align: center;">
          <i class="fas fa-trophy" style="font-size: 2rem; color: var(--t3); margin-bottom: 10px;"></i>
          <p style="color: var(--t2);">এই মুহূর্তে কোনো ম্যাচ পাওয়া যায়নি।</p>
        </div>
      `;
      return;
    }

    grid.innerHTML = list.map(sp => {
      const isLive = sp.isLive;
      const team1Logo = sp.team1Logo || sp.logo || FALLBACK_LOGO_SVG;
      const team2Logo = sp.team2Logo || FALLBACK_LOGO_SVG;
      const team1Name = sp.team1 || sp.title.split(' vs ')[0] || sp.title;
      const team2Name = sp.team2 || sp.title.split(' vs ')[1] || 'Match';

      return `
        <div class="sm-card ${isLive ? 'sm-live' : ''}" data-id="${escHtml(sp.id)}">
          <div class="sm-top">
            <div class="sm-tour">
              <i class="fas fa-trophy" style="color: #f59e0b;"></i>
              <span>${escHtml(sp.tournament || sp.category)}</span>
            </div>
            <div class="sm-badge ${isLive ? 'sm-badge-live' : 'sm-badge-upcoming'}">
              ${isLive ? '<i class="fas fa-circle" style="font-size: 8px;"></i> LIVE' : '⏰ UPCOMING'}
            </div>
          </div>

          <div class="sm-teams">
            <div class="sm-team">
              <img class="sm-team-logo" src="${escHtml(team1Logo)}" alt="${escHtml(team1Name)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
              <div class="sm-team-name">${escHtml(team1Name)}</div>
            </div>

            <div class="sm-center">
              <div class="sm-score">${escHtml(sp.score || 'VS')}</div>
              <div class="sm-vs">${isLive ? 'চলমান' : 'VS'}</div>
            </div>

            <div class="sm-team">
              <img class="sm-team-logo" src="${escHtml(team2Logo)}" alt="${escHtml(team2Name)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
              <div class="sm-team-name">${escHtml(team2Name)}</div>
            </div>
          </div>

          <div class="sm-foot">
            <div class="sm-time">
              <i class="far fa-clock"></i>
              <span>${escHtml(sp.time)}</span>
            </div>

            <button class="sm-btn-play">
              <i class="fas fa-play"></i>
              <span>${isLive ? 'সরাসরি দেখুন' : 'ম্যাচ দেখুন'}</span>
            </button>
          </div>
        </div>
      `;
    }).join('');

    grid.querySelectorAll('.sm-card').forEach(card => {
      card.addEventListener('click', () => {
        const id = card.dataset.id;
        const targetSp = this.S.sports.find(s => s.id === id);
        if (targetSp) this.playItem(targetSp);
      });
    });
  },

  // ── (D) PLAYLISTS VIEW ──
  renderPlaylists() {
    const grid = this.E['playlists-grid'];
    if (!grid) return;

    let list = [...this.S.playlists];

    if (list.length === 0) {
      grid.innerHTML = `
        <div class="empty" style="padding: 40px; text-align: center;">
          <i class="fas fa-list-ul" style="font-size: 2rem; color: var(--t3); margin-bottom: 10px;"></i>
          <p style="color: var(--t2);">কোনো প্লেলিস্ট পাওয়া যায়নি।</p>
        </div>
      `;
      return;
    }

    grid.innerHTML = list.map(pl => {
      const count = pl.channelCount ? `${pl.channelCount} টি চ্যানেল/স্ট্রিম` : 'ক্লাউড সিঙ্কড';

      return `
        <div class="pl-card" data-url="${escHtml(pl.url)}" data-name="${escHtml(pl.name)}">
          <div class="pl-head">
            <img class="pl-logo" src="${escHtml(pl.logo)}" alt="${escHtml(pl.name)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
            <div class="pl-head-info">
              <div class="pl-name">${escHtml(pl.name)}</div>
              <span class="pl-type-badge">${escHtml(pl.type || 'M3U')}</span>
            </div>
          </div>

          <div class="pl-desc">${escHtml(pl.description)}</div>

          <div class="pl-foot">
            <div class="pl-count"><i class="fas fa-satellite-dish"></i> ${count}</div>
            <button class="pl-btn-browse">
              <span>চ্যানেল দেখুন</span>
              <i class="fas fa-arrow-right"></i>
            </button>
          </div>
        </div>
      `;
    }).join('');

    grid.querySelectorAll('.pl-card').forEach(card => {
      card.addEventListener('click', () => {
        const plName = card.dataset.name;
        // Filter Live TV by playlist name or switch to live TV
        this.S.tvCatFilter = '';
        this.switchTab('livetv');
      });
    });
  },

  // ── (E) MATCH SCHEDULE VIEW ──
  renderSchedule(filter = 'all') {
    const listEl = this.E['match-list'];
    if (!listEl) return;

    let matches = [...this.S.matches];
    if (filter === 'live') matches = matches.filter(m => m.status === 'live');
    if (filter === 'upcoming') matches = matches.filter(m => m.status === 'upcoming');

    if (matches.length === 0) {
      listEl.innerHTML = `
        <div class="match-empty" style="padding: 40px; text-align: center;">
          <i class="fas fa-futbol" style="font-size: 2rem; color: var(--t3); margin-bottom: 10px;"></i>
          <p style="color: var(--t2);">এই মুহূর্তে কোনো ম্যাচ শিডিউল নেই।</p>
        </div>
      `;
      return;
    }

    listEl.innerHTML = matches.map(m => {
      const isLive = m.status === 'live';
      return `
        <div class="match-card ${isLive ? 'mc-live' : ''}" data-id="${escHtml(m.id)}">
          <div class="mc-header">
            <div class="mc-league">
              <i class="fas fa-trophy" style="color: #f59e0b;"></i>
              <span>${escHtml(m.league)}</span>
            </div>
            <span class="mc-status ${isLive ? 'mcs-live' : 'mcs-upcoming'}">${isLive ? '🔴 LIVE' : '⏰ Upcoming'}</span>
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
              <span>${isLive ? 'সরাসরি দেখুন' : 'শিডিউল'}</span>
            </button>
          </div>
        </div>
      `;
    }).join('');

    listEl.querySelectorAll('.mc-watch-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const stream = btn.dataset.stream;
        const title = btn.dataset.title;
        if (stream) {
          this.playItem({
            id: 'm_' + Math.random().toString(36).substring(2, 7),
            name: title,
            title: title,
            category: 'Sports',
            url: stream
          });
        }
      });
    });
  },

  renderUpcomingCarousel() {
    const trackHome = document.getElementById('upc-track-home');
    if (!trackHome) return;

    const items = [...this.S.sports, ...this.S.matches].slice(0, 10);
    if (items.length === 0) return;

    trackHome.innerHTML = items.map(m => {
      const title = m.title || m.name || 'Match';
      const isLive = m.isLive || m.status === 'live';
      const thumb = m.poster || (m.team1 && m.team1.logo) || m.logo || 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=400&fit=crop';
      const time = m.time || 'Live';
      const league = m.tournament || m.league || m.category || 'Sports';

      return `
        <div class="upc-card ${isLive ? 'upc-live' : ''}" data-stream="${escHtml(m.url || m.streamUrl)}" data-title="${escHtml(title)}">
          <div class="upc-thumb-wrap">
            <img class="upc-thumb" src="${escHtml(thumb)}" alt="${escHtml(title)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
            ${isLive ? '<div class="upc-live-badge"><div class="dot"></div>LIVE</div>' : '<div class="upc-upcoming-badge">UPCOMING</div>'}
          </div>
          <div class="upc-info">
            <div class="upc-title">${escHtml(title)}</div>
            <div class="upc-meta">${escHtml(league)}</div>
            <div class="upc-time"><i class="far fa-clock"></i> ${escHtml(time)}</div>
          </div>
        </div>
      `;
    }).join('');

    trackHome.querySelectorAll('.upc-card').forEach(card => {
      card.addEventListener('click', () => {
        const stream = card.dataset.stream;
        const title = card.dataset.title;
        if (stream) {
          this.playItem({
            id: 'car_' + Math.random().toString(36).substring(2, 7),
            name: title,
            title: title,
            category: 'Sports',
            url: stream
          });
        }
      });
    });
  },

  // ═══════════════════════════════════════════
  // 7. VIDEO PLAYER & MULTI-SERVER ENGINE
  // ═══════════════════════════════════════════
  hlsInstance: null,

  playItem(item) {
    if (!item) return;
    this.S.curItem = item;
    this.S.currentServerIdx = 0;
    this.addRecent(item.id);

    // Show player wrap
    if (this.E['player-wrap']) {
      this.E['player-wrap'].classList.add('on');
      this.E['player-wrap'].scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    // Set UI labels
    const itemName = item.title || item.name || 'Streaming';
    if (this.E['pw-name']) this.E['pw-name'].textContent = itemName;
    if (this.E['pw-category']) this.E['pw-category'].textContent = item.category || 'NAFI TV HD STREAM';
    if (this.E['pw-logo']) {
      this.E['pw-logo'].src = item.logo || item.poster || FALLBACK_LOGO_SVG;
    }

    // Set Multi-Server dropdown
    const serverSelect = this.E['pw-server-select'];
    const servers = (item.servers && item.servers.length > 0)
      ? item.servers
      : [{ name: 'সার্ভার ১ (Main)', url: item.url }];

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
              this.showPlayerError('সার্ভার থেকে স্ট্রিম লোড হতে সমস্যা হচ্ছে। পরবর্তী সার্ভার চেষ্টা করুন।');
              break;
          }
        }
      });
    } else {
      // Native MP4 / MKV / WebM
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
    if (!this.S.curItem) return;
    const servers = this.S.curItem.servers || [{ url: this.S.curItem.url }];
    if (servers.length <= 1) {
      this.loadStreamSource(this.S.curItem.url);
      return;
    }
    this.S.currentServerIdx = (this.S.currentServerIdx + 1) % servers.length;
    if (this.E['pw-server-select']) {
      this.E['pw-server-select'].value = this.S.currentServerIdx;
    }
    this.loadStreamSource(servers[this.S.currentServerIdx].url);
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
    this.S.curItem = null;
    this.renderChannels();
  },

  toggleLock() {
    this.S.isLocked = !this.S.isLocked;
    const pWrap = this.E['player-wrap'];
    const btn = this.E['qtv-lock-btn'];
    if (pWrap) pWrap.classList.toggle('qtv-locked', this.S.isLocked);
    if (btn) {
      btn.innerHTML = this.S.isLocked
        ? '<i class="fas fa-lock" style="color:#ff3b30;"></i>'
        : '<i class="fas fa-lock-open"></i>';
    }
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
  // 8. THEMES & STYLING
  // ═══════════════════════════════════════════
  applyTheme() {
    document.body.classList.toggle('lt', !this.S.dark);
    const thIco = document.getElementById('th-ico');
    const togTheme = this.E['tog-theme'];

    const iconClass = this.S.dark ? 'fas fa-moon' : 'fas fa-sun';
    if (thIco) thIco.className = iconClass;
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
    grid.className = 'channel-grid';
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

  // ═══════════════════════════════════════════
  // 9. EVENT BINDINGS
  // ═══════════════════════════════════════════
  bindEvents() {
    // 4 Main Section Tabs (Top Bar)
    document.querySelectorAll('.main-tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        this.switchTab(btn.dataset.tab);
      });
    });

    // Bottom Navigation
    document.querySelectorAll('.bnav-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        this.switchTab(btn.dataset.tab);
      });
    });

    // Side Drawer Menu items
    const menuMap = {
      'm-livetv': 'livetv',
      'm-movies': 'movies',
      'm-sports': 'sports',
      'm-playlists': 'playlists',
      'm-schedule': 'schedule'
    };
    Object.entries(menuMap).forEach(([btnId, tab]) => {
      const btn = document.getElementById(btnId);
      if (btn) {
        btn.addEventListener('click', (e) => {
          e.preventDefault();
          this.closeMenu();
          this.switchTab(tab);
        });
      }
    });

    // Side Menu
    if (this.E['mo-btn']) this.E['mo-btn'].addEventListener('click', () => this.openMenu());
    if (this.E['mc-btn']) this.E['mc-btn'].addEventListener('click', () => this.closeMenu());
    if (this.E['menu-ov']) this.E['menu-ov'].addEventListener('click', () => this.closeMenu());

    // Favorites & Recent from Menu
    const mFav = document.getElementById('m-fav');
    if (mFav) {
      mFav.addEventListener('click', (e) => {
        e.preventDefault();
        this.closeMenu();
        this.S.tvCatFilter = 'Favorites';
        this.switchTab('livetv');
        this.buildTvCatTabs();
        this.renderChannels();
      });
    }
    const mRec = document.getElementById('m-rec');
    if (mRec) {
      mRec.addEventListener('click', (e) => {
        e.preventDefault();
        this.closeMenu();
        this.S.tvCatFilter = 'Recent';
        this.switchTab('livetv');
        this.buildTvCatTabs();
        this.renderChannels();
      });
    }

    // Refresh Data
    const mRefresh = document.getElementById('m-refresh');
    if (mRefresh) {
      mRefresh.addEventListener('click', (e) => {
        e.preventDefault();
        this.closeMenu();
        this.fetchAllData();
      });
    }

    // Import modal openers
    const mImport = document.getElementById('m-import');
    if (mImport) {
      mImport.addEventListener('click', (e) => {
        e.preventDefault();
        this.closeMenu();
        this.openImportModal();
      });
    }
    const btnOpenImportTab = document.getElementById('btn-open-import-tab');
    if (btnOpenImportTab) {
      btnOpenImportTab.addEventListener('click', () => this.openImportModal());
    }

    // Theme toggles
    const themeBtn = document.getElementById('theme-btn');
    if (themeBtn) themeBtn.addEventListener('click', () => this.toggleTheme());
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
      this.E['srch-in'].addEventListener('input', (e) => this.handleGlobalSearch(e.target.value));
    }

    // Movie mini search input
    if (this.E['movie-search-in']) {
      this.E['movie-search-in'].addEventListener('input', (e) => {
        this.renderMovies(e.target.value.trim());
      });
    }

    // View switchers for channel grid
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
        this.S.currentServerIdx = idx;
        if (this.S.curItem && this.S.curItem.servers && this.S.curItem.servers[idx]) {
          this.loadStreamSource(this.S.curItem.servers[idx].url);
        }
      });
    }
    if (this.E['btn-player-retry']) {
      this.E['btn-player-retry'].addEventListener('click', () => {
        if (this.S.curItem) {
          const servers = this.S.curItem.servers || [{ url: this.S.curItem.url }];
          this.loadStreamSource(servers[this.S.currentServerIdx].url);
        }
      });
    }
    if (this.E['btn-player-next-server']) {
      this.E['btn-player-next-server'].addEventListener('click', () => this.nextServer());
    }

    // Sports Filter Buttons
    document.querySelectorAll('.sf-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.sf-btn').forEach(b => b.classList.remove('on'));
        btn.classList.add('on');
        this.S.sportsFilter = btn.dataset.sfilter;
        this.renderSports();
      });
    });

    // Schedule Filter Buttons
    document.querySelectorAll('.mf-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.mf-btn').forEach(b => b.classList.remove('on'));
        btn.classList.add('on');
        this.renderSchedule(btn.dataset.filter);
      });
    });

    // Import modal events
    if (this.E['btn-close-import']) this.E['btn-close-import'].addEventListener('click', () => this.closeImportModal());
    if (this.E['btn-do-import']) this.E['btn-do-import'].addEventListener('click', () => this.handleCustomImport());

    // Import Presets
    const bindPreset = (id, url) => {
      const el = document.getElementById(id);
      if (el) {
        el.addEventListener('click', async () => {
          if (this.E['import-url-input']) this.E['import-url-input'].value = url;
          await this.handleCustomImport();
        });
      }
    };
    bindPreset('preset-bd-sports', 'https://raw.githubusercontent.com/srhady/tapmad-bd/refs/heads/main/tapmad_bd.json');
    bindPreset('preset-live-tv', 'https://raw.githubusercontent.com/nafitv24-web/NAFI-TV/refs/heads/main/Update%20Channel.m3u');
    bindPreset('preset-nafi-sports', 'https://raw.githubusercontent.com/nafitv24-web/NAFI-TV/refs/heads/main/Sports%20Channel%20NF.m3u');
    bindPreset('preset-movies-json', 'https://raw.githubusercontent.com/nafitv24-web/NAFI-TV/refs/heads/main/movies.json');
  },

  // ═══════════════════════════════════════════
  // 10. MODALS & SEARCH HANDLERS
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
    const newMovies = [];
    const newSports = [];

    if (urlInput && urlInput.value.trim()) {
      const url = urlInput.value.trim();
      const plItem = {
        name: this.extractPlaylistName(url, 'Custom Playlist'),
        url: url
      };
      await this.loadPlaylistContent(url, 'Custom', newChannels, newMovies, newSports, plItem);

      this.S.customPlaylists.push(plItem);
      Store.set('custom_playlists', this.S.customPlaylists);
    } else if (fileInput && fileInput.files && fileInput.files[0]) {
      const file = fileInput.files[0];
      const text = await file.text();
      const fakeUrl = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
      const plItem = { name: file.name.replace(/\.[^/.]+$/, ''), url: fakeUrl };
      await this.loadPlaylistContent(fakeUrl, 'Custom', newChannels, newMovies, newSports, plItem);
    }

    if (newChannels.length > 0 || newMovies.length > 0 || newSports.length > 0) {
      this.S.channels = [...newChannels, ...this.S.channels];
      this.S.movies = [...newMovies, ...this.S.movies];
      this.S.sports = [...newSports, ...this.S.sports];

      this.updateBadges();
      this.renderAllViews();
      this.closeImportModal();
      alert(`সফলভাবে ${newChannels.length + newMovies.length + newSports.length} টি কনটেন্ট যুক্ত হয়েছে!`);
    } else {
      alert('প্লেলিস্ট ফাইল বা লিঙ্কটি সঠিক নয়। অনুগ্রহ করে পুনরায় চেষ্টা করুন।');
    }
  },

  openSearch() {
    if (this.E['srch-ov']) {
      this.E['srch-ov'].classList.add('on');
      if (this.E['srch-in']) {
        this.E['srch-in'].value = '';
        this.E['srch-in'].focus();
      }
      this.handleGlobalSearch('');
    }
  },
  closeSearch() {
    if (this.E['srch-ov']) this.E['srch-ov'].classList.remove('on');
  },

  handleGlobalSearch(query) {
    const resBox = this.E['srch-res'];
    if (!resBox) return;

    const q = query.trim().toLowerCase();
    if (!q) {
      resBox.innerHTML = '<div class="empty"><i class="fas fa-search"></i><p>চ্যানেল, মুভি বা ম্যাচের নাম লিখুন...</p></div>';
      return;
    }

    const matchedChannels = this.S.channels.filter(c => c.name.toLowerCase().includes(q));
    const matchedMovies = this.S.movies.filter(m => m.title.toLowerCase().includes(q));
    const matchedSports = this.S.sports.filter(s => s.title.toLowerCase().includes(q));

    const totalFound = matchedChannels.length + matchedMovies.length + matchedSports.length;

    if (totalFound === 0) {
      resBox.innerHTML = `<div class="empty"><i class="fas fa-search"></i><p>"${escHtml(query)}" নামে কিছুই পাওয়া যায়নি।</p></div>`;
      return;
    }

    let html = '';

    if (matchedChannels.length > 0) {
      html += `<div class="srch-sec-title">📺 টিভি চ্যানেল (${matchedChannels.length})</div>`;
      html += matchedChannels.slice(0, 10).map(c => `
        <div class="srch-item" data-type="channel" data-id="${escHtml(c.id)}">
          <img class="srch-logo" src="${escHtml(c.logo || FALLBACK_LOGO_SVG)}" alt="${escHtml(c.name)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
          <div class="srch-info">
            <div class="srch-name">${escHtml(c.name)}</div>
            <div class="srch-cat">${escHtml(c.category)}</div>
          </div>
          <button class="srch-play-btn"><i class="fas fa-play"></i></button>
        </div>
      `).join('');
    }

    if (matchedMovies.length > 0) {
      html += `<div class="srch-sec-title">🎬 মুভি ও সিরিজ (${matchedMovies.length})</div>`;
      html += matchedMovies.slice(0, 10).map(m => `
        <div class="srch-item" data-type="movie" data-id="${escHtml(m.id)}">
          <img class="srch-logo" src="${escHtml(m.poster || FALLBACK_LOGO_SVG)}" alt="${escHtml(m.title)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
          <div class="srch-info">
            <div class="srch-name">${escHtml(m.title)}</div>
            <div class="srch-cat">${escHtml(m.category)} ${m.year ? `(${m.year})` : ''}</div>
          </div>
          <button class="srch-play-btn"><i class="fas fa-play"></i></button>
        </div>
      `).join('');
    }

    if (matchedSports.length > 0) {
      html += `<div class="srch-sec-title">⚽ স্পোর্টস ও ম্যাচ (${matchedSports.length})</div>`;
      html += matchedSports.slice(0, 10).map(s => `
        <div class="srch-item" data-type="sport" data-id="${escHtml(s.id)}">
          <img class="srch-logo" src="${escHtml(s.logo || FALLBACK_LOGO_SVG)}" alt="${escHtml(s.title)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
          <div class="srch-info">
            <div class="srch-name">${escHtml(s.title)}</div>
            <div class="srch-cat">${escHtml(s.tournament || s.category)}</div>
          </div>
          <button class="srch-play-btn"><i class="fas fa-play"></i></button>
        </div>
      `).join('');
    }

    resBox.innerHTML = html;

    resBox.querySelectorAll('.srch-item').forEach(itemEl => {
      itemEl.addEventListener('click', () => {
        const type = itemEl.dataset.type;
        const id = itemEl.dataset.id;
        this.closeSearch();

        if (type === 'channel') {
          const ch = this.S.channels.find(c => c.id === id);
          if (ch) this.playItem(ch);
        } else if (type === 'movie') {
          const m = this.S.movies.find(x => x.id === id);
          if (m) this.playItem(m);
        } else if (type === 'sport') {
          const s = this.S.sports.find(x => x.id === id);
          if (s) this.playItem(s);
        }
      });
    });
  }
};

// ═══════════════════════════════════════════
// 11. BOOTSTRAP APPLICATION
// ═══════════════════════════════════════════
document.addEventListener('DOMContentLoaded', () => {
  APP.init();
});
