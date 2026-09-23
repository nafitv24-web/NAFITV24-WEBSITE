/**
 * ══════════════════════════════════════════════════════════════════════════════
 * NAFI TV 24 – Web Application Engine
 * Disconnected from Firebase. Powered by direct high-speed endpoints:
 * 1. Live Events: Tapmad BD JSON & Prime Video Sports M3U (DASH/ClearKey)
 * 2. Live TV: FAST TV (BDIX) M3U & NAFI TV24 Categorized Multi-Server JSON
 * 3. Download Our App: Showcase & direct APK download
 * ══════════════════════════════════════════════════════════════════════════════
 */

'use strict';

const FALLBACK_LOGO_SVG = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64"><rect width="64" height="64" rx="14" fill="%23e50914"/><text x="50%" y="54%" text-anchor="middle" dominant-baseline="middle" font-family="sans-serif" font-weight="900" font-size="20" fill="%23ffffff">TV</text></svg>';

const DATA_SOURCES = {
  tapmadEvents: 'https://gist.githubusercontent.com/albatr0ssss/3cff7a26be49b1d352c15f615067e7cd/raw/tapmad_bd.json',
  primeEvents: 'https://raw.githubusercontent.com/srhady/willow-event/refs/heads/main/primevideo_sports.m3u',
  fastTv: 'https://raw.githubusercontent.com/ahan443/FAST-IPTV/refs/heads/main/z.m3u',
  nafiTv: 'https://raw.githubusercontent.com/nafitv24-web/NAFI-TV/refs/heads/main/Update%20Channel.m3u'
};

function escHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

const APP = {
  // ── Global State ──
  S: {
    activeTab: 'events', // 'events' | 'livetv' | 'download'
    events: [],
    channels: [],
    eventFilter: 'all', // 'all' | 'live' | 'prime' | 'tapmad' | 'cricket' | 'football' | 'upcoming'
    tvSourceFilter: 'all', // 'all' | 'fasttv' | 'nafitv'
    tvCatFilter: '',
    tvSearchQuery: '',
    tvViewMode: 'g3', // 'lst' | 'g2' | 'g3'
    visibleTvCount: 60,
    curItem: null,
    currentServerIdx: 0,
    favorites: new Set(),
    recents: [],
    theme: 'dark',
    autoplay: true,
    fontSize: 'md'
  },

  // ── DOM Cache ──
  E: {},

  // ── Video Players ──
  hlsInstance: null,
  shakaPlayer: null,

  // ═══════════════════════════════════════════
  // 1. INITIALIZATION & LIFECYCLE
  // ═══════════════════════════════════════════
  async init() {
    this.cacheElements();
    this.loadPreferences();
    this.bindEvents();
    this.initPlayerControls();

    // Start data load immediately in parallel
    await this.fetchAllData();

    // Remove loading overlay
    const ls = document.getElementById('ls');
    if (ls) {
      ls.classList.add('out');
      setTimeout(() => { ls.style.display = 'none'; }, 500);
    }
  },

  cacheElements() {
    const ids = [
      'view-events', 'view-livetv', 'view-download',
      'tab-btn-events', 'tab-btn-livetv', 'tab-btn-download',
      'bnav-events', 'bnav-livetv', 'bnav-download',
      'm-events', 'm-livetv', 'm-download',
      'm-fav', 'm-rec', 'm-settings', 'm-contact',
      'badge-events-count', 'badge-tv-count',
      'events-total-count', 'tv-total-count',
      'events-grid', 'events-filters',
      'grid', 'cat-tabs', 'tv-source-tabs', 'grid-more-wrap', 'grid-more-btn',
      'player-wrap', 'main-video', 'vctrl-big-play', 'video-loader', 'video-error',
      'pw-name', 'pw-category', 'pw-logo', 'pw-server-select', 'pw-close',
      'vctrl-play', 'vctrl-prog', 'vctrl-prog-fill', 'vctrl-time',
      'vctrl-mute', 'vctrl-vol', 'vctrl-pip', 'vctrl-full',
      'menu-btn', 'menu-ov', 'menu', 'mc-btn',
      'srch-btn', 'srch-ov', 'srch-close', 'srch-in', 'srch-res',
      'set-ov', 'settings', 'tog-theme', 'tog-auto',
      'contact-ov', 'contact-sheet', 'cs-btn',
      'vb-lst', 'vb-g2', 'vb-g3', 'upc-track-home'
    ];

    ids.forEach(id => {
      this.E[id] = document.getElementById(id);
    });
  },

  loadPreferences() {
    try {
      const favs = localStorage.getItem('nafitv_favs');
      if (favs) this.S.favorites = new Set(JSON.parse(favs));
    } catch (e) {}

    try {
      const recs = localStorage.getItem('nafitv_recents');
      if (recs) this.S.recents = JSON.parse(recs);
    } catch (e) {}

    const theme = localStorage.getItem('nafitv_theme') || 'dark';
    this.S.theme = theme;
    if (theme === 'light') document.body.classList.add('lt');

    const fs = localStorage.getItem('nafitv_fs') || 'md';
    this.S.fontSize = fs;
    if (fs === 'sm') document.body.classList.add('fs-sm');
    if (fs === 'lg') document.body.classList.add('fs-lg');
  },

  // ═══════════════════════════════════════════
  // 2. PARALLEL DATA FETCHING (NO FIREBASE)
  // ═══════════════════════════════════════════
  async fetchWithFallback(url) {
    if (!url || typeof url !== 'string') return null;
    const cleanUrl = url.trim();

    // 1. Direct fetch
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 7000);
      const res = await fetch(cleanUrl, { signal: controller.signal, cache: 'no-store' });
      clearTimeout(timer);
      if (res.ok) return await res.text();
    } catch (e) {}

    // 2. CORS Proxy Fallback 1: corsproxy.io
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 8000);
      const proxyUrl = `https://corsproxy.io/?url=${encodeURIComponent(cleanUrl)}`;
      const res = await fetch(proxyUrl, { signal: controller.signal });
      clearTimeout(timer);
      if (res.ok) return await res.text();
    } catch (e) {}

    // 3. CORS Proxy Fallback 2: allorigins.win
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 8000);
      const proxyUrl = `https://api.allorigins.win/raw?url=${encodeURIComponent(cleanUrl)}`;
      const res = await fetch(proxyUrl, { signal: controller.signal });
      clearTimeout(timer);
      if (res.ok) return await res.text();
    } catch (e) {}

    return null;
  },

  async fetchAllData() {
    const results = await Promise.allSettled([
      this.fetchWithFallback(DATA_SOURCES.primeEvents),
      this.fetchWithFallback(DATA_SOURCES.tapmadEvents),
      this.fetchWithFallback(DATA_SOURCES.fastTv),
      this.fetchWithFallback(DATA_SOURCES.nafiTv)
    ]);

    const primeText = results[0].status === 'fulfilled' ? results[0].value : null;
    const tapmadText = results[1].status === 'fulfilled' ? results[1].value : null;
    const fastTvText = results[2].status === 'fulfilled' ? results[2].value : null;
    const nafiTvText = results[3].status === 'fulfilled' ? results[3].value : null;

    // 1. Parse Events
    const primeEvents = this.parsePrimeSportsM3u(primeText);
    const tapmadEvents = this.parseTapmadJson(tapmadText);
    this.S.events = [...primeEvents, ...tapmadEvents];

    // 2. Parse Channels
    const fastChannels = this.parseFastTvM3u(fastTvText);
    const nafiChannels = this.parseNafiTvData(nafiTvText);
    this.S.channels = [...fastChannels, ...nafiChannels];

    // 3. Update Badges
    if (this.E['badge-events-count']) {
      this.E['badge-events-count'].textContent = this.S.events.length;
    }
    if (this.E['badge-tv-count']) {
      this.E['badge-tv-count'].textContent = this.S.channels.length;
    }

    // 4. Render Active Views
    this.renderEvents();
    this.buildTvCategoryTabs();
    this.renderTvChannels();
    this.renderUpcomingCarousel();
  },

  // ═══════════════════════════════════════════
  // 3. PARSERS FOR THE 4 SPECIFIC ENDPOINTS
  // ═══════════════════════════════════════════

  // (A) Prime Video Sports M3U (DASH / MPD with ClearKey)
  parsePrimeSportsM3u(text) {
    if (!text) return [];
    const items = [];
    const lines = text.split(/\r?\n/);
    let curTitle = '';
    let curLogo = '';
    let curCat = 'Prime Sports';
    let curDrmKey = '';

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (line.startsWith('#EXTINF:')) {
        const logoMatch = line.match(/tvg-logo="([^"]+)"/i);
        curLogo = logoMatch ? logoMatch[1] : '';
        const groupMatch = line.match(/group-title="([^"]+)"/i);
        curCat = groupMatch ? groupMatch[1] : 'Prime Sports';
        const commaIdx = line.lastIndexOf(',');
        curTitle = commaIdx !== -1 ? line.substring(commaIdx + 1).trim() : 'Prime Sports Event';
      } else if (line.includes('license_key=')) {
        curDrmKey = line.split('license_key=')[1].trim();
      } else if (line.startsWith('http://') || line.startsWith('https://')) {
        if (curTitle && line) {
          items.push({
            id: 'prime_' + Math.random().toString(36).substring(2, 8),
            title: curTitle,
            category: curCat,
            tournament: 'Prime Video Sports Live',
            logo: curLogo || FALLBACK_LOGO_SVG,
            poster: curLogo || 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=500&fit=crop',
            url: line,
            drmKey: curDrmKey,
            manifestType: 'mpd',
            source: 'prime',
            isLive: true,
            time: 'LIVE NOW',
            servers: [{ name: 'Prime HD Stream (ClearKey)', url: line, drmKey: curDrmKey }]
          });
        }
        curTitle = '';
        curLogo = '';
        curCat = 'Prime Sports';
        curDrmKey = '';
      }
    }
    return items;
  },

  // (B) Tapmad BD Events JSON
  parseTapmadJson(text) {
    if (!text) return [];
    const items = [];
    try {
      const data = JSON.parse(text);
      const matches = data.Matches || (Array.isArray(data) ? data : []);

      matches.forEach(m => {
        const title = m.VideoName || m.title || 'Tapmad Sports Match';
        const cat = m.CategoryName || 'Sports';
        const status = String(m.Status || '').toLowerCase();
        const isLive = status === 'live' || status === '1';
        const dateStr = m.EventStartDate || '';
        const thumb = m.ThumbnailStandard || m.ThumbnailTV || m.poster || 'https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?w=500&fit=crop';
        const streamUrl = m.stream_url || (m.sources && m.sources[0] ? m.sources[0].url : '') || '';

        // Formatted display date/time
        let displayTime = isLive ? 'LIVE NOW' : 'আসন্ন ম্যাচ';
        if (dateStr && !isLive) {
          try {
            const d = new Date(dateStr.replace(' ', 'T'));
            if (!isNaN(d.getTime())) {
              displayTime = d.toLocaleDateString('bn-BD', { month: 'short', day: 'numeric' }) + ' ' +
                            d.toLocaleTimeString('bn-BD', { hour: '2-digit', minute: '2-digit' });
            } else {
              displayTime = dateStr;
            }
          } catch (e) {
            displayTime = dateStr;
          }
        }

        items.push({
          id: 'tapmad_' + (m.EntityId || Math.random().toString(36).substring(2, 7)),
          title: title,
          category: cat,
          tournament: cat,
          logo: thumb,
          poster: thumb,
          url: streamUrl,
          urlSlug: m.UrlSlug || '',
          source: 'tapmad',
          isLive: isLive,
          time: displayTime,
          description: m.Description || '',
          servers: streamUrl
            ? [{ name: 'Tapmad Live Stream', url: streamUrl }]
            : [
                { name: 'সার্ভার ১ (T Sports)', url: 'https://box.bbaria.net:8083/T_Sports/video.m3u8' },
                { name: 'সার্ভার ২ (Sports HD)', url: 'https://d3bq19vx8xhpwy.cloudfront.net/live/myStream/playlist.m3u8' }
              ]
        });
      });
    } catch (e) {
      console.warn('Error parsing Tapmad JSON:', e);
    }
    return items;
  },

  // (C) FAST TV M3U (155 channels)
  parseFastTvM3u(text) {
    if (!text) return [];
    const items = [];
    const lines = text.split(/\r?\n/);
    let curTitle = '';
    let curLogo = '';
    let curCat = 'FAST TV';

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      if (line.startsWith('#EXTINF:')) {
        const logoMatch = line.match(/tvg-logo="([^"]+)"/i);
        curLogo = logoMatch ? logoMatch[1] : '';
        const groupMatch = line.match(/group-title="([^"]+)"/i);
        curCat = groupMatch ? groupMatch[1] : 'FAST TV';
        const commaIdx = line.lastIndexOf(',');
        curTitle = commaIdx !== -1 ? line.substring(commaIdx + 1).trim() : 'FAST Channel';
      } else if (line.startsWith('http://') || line.startsWith('https://')) {
        if (curTitle && line) {
          items.push({
            id: 'fast_' + Math.random().toString(36).substring(2, 8),
            name: curTitle,
            title: curTitle,
            category: this.normalizeCategoryName(curCat),
            rawCategory: curCat,
            logo: curLogo || FALLBACK_LOGO_SVG,
            url: line,
            source: 'fasttv',
            servers: [{ name: 'FAST HD (BDIX)', url: line }]
          });
        }
        curTitle = '';
        curLogo = '';
        curCat = 'FAST TV';
      }
    }
    return items;
  },

  // (D) NAFI TV24 Categorized Multi-Server JSON (386 channels)
  parseNafiTvData(text) {
    if (!text) return [];
    const items = [];
    try {
      const data = JSON.parse(text);
      const categories = data.categories || [];

      categories.forEach(catObj => {
        const catName = catObj.category_name || 'General';
        const normCat = this.normalizeCategoryName(catName);
        const channels = catObj.movies || catObj.channels || [];

        channels.forEach(ch => {
          const title = ch.title || ch.name || 'NAFI Channel';
          const poster = ch.poster || ch.logo || ch.icon || FALLBACK_LOGO_SVG;
          const sources = (ch.sources || ch.servers || []).map((s, idx) => ({
            name: s.server_name || `সার্ভার ${idx + 1}`,
            url: s.url || s.streamUrl
          })).filter(s => s.url);

          const primaryUrl = (sources[0] ? sources[0].url : '') || ch.url || ch.streamUrl || '';
          if (primaryUrl) {
            items.push({
              id: 'nafi_' + Math.random().toString(36).substring(2, 8),
              name: title,
              title: title,
              category: normCat,
              rawCategory: catName,
              logo: poster,
              poster: poster,
              url: primaryUrl,
              source: 'nafitv',
              servers: sources.length > 0 ? sources : [{ name: 'সার্ভার ১ (HD)', url: primaryUrl }]
            });
          }
        });
      });
    } catch (e) {
      console.warn('Error parsing NAFI TV JSON:', e);
    }
    return items;
  },

  normalizeCategoryName(raw) {
    if (!raw) return 'অন্যান্য';
    const s = raw.toLowerCase().trim();
    if (s.includes('bangla') || s.includes('bd') || s.includes('local')) return 'বাংলাদেশী';
    if (s.includes('sport') || s.includes('cricket') || s.includes('football')) return 'খেলাধুলা';
    if (s.includes('movie') || s.includes('cinema') || s.includes('film')) return 'মুভি';
    if (s.includes('news') || s.includes('khabor')) return 'সংবাদ';
    if (s.includes('entertain') || s.includes('natok') || s.includes('serial')) return 'বিনোদন';
    if (s.includes('kid') || s.includes('cartoon')) return 'কিডস';
    if (s.includes('islam') || s.includes('quran') || s.includes('deen')) return 'ইসলামিক';
    if (s.includes('music') || s.includes('song')) return 'মিউজিক';
    if (s.includes('pakistan') || s.includes('india') || s.includes('hindi')) return 'আন্তর্জাতিক';
    return raw;
  },

  // ═══════════════════════════════════════════
  // 4. TAB NAVIGATION & VIEW SWITCHING (3 OPTIONS)
  // ═══════════════════════════════════════════
  switchTab(tab) {
    if (!['events', 'livetv', 'download'].includes(tab)) return;
    this.S.activeTab = tab;

    // 1. Toggle View Sections
    const views = {
      events: this.E['view-events'],
      livetv: this.E['view-livetv'],
      download: this.E['view-download']
    };

    Object.entries(views).forEach(([key, el]) => {
      if (el) {
        if (key === tab) {
          el.style.display = 'block';
          el.classList.add('active');
        } else {
          el.style.display = 'none';
          el.classList.remove('active');
        }
      }
    });

    // 2. Toggle Main Top Tabs
    const topTabBtns = {
      events: this.E['tab-btn-events'],
      livetv: this.E['tab-btn-livetv'],
      download: this.E['tab-btn-download']
    };
    Object.entries(topTabBtns).forEach(([key, btn]) => {
      if (btn) {
        btn.classList.toggle('on', key === tab);
        btn.setAttribute('aria-selected', key === tab ? 'true' : 'false');
      }
    });

    // 3. Toggle Bottom Nav Buttons
    const bnavBtns = {
      events: this.E['bnav-events'],
      livetv: this.E['bnav-livetv'],
      download: this.E['bnav-download']
    };
    Object.entries(bnavBtns).forEach(([key, btn]) => {
      if (btn) btn.classList.toggle('on', key === tab);
    });

    // 4. Toggle Side Drawer Menu Items
    const menuItems = {
      events: this.E['m-events'],
      livetv: this.E['m-livetv'],
      download: this.E['m-download']
    };
    Object.entries(menuItems).forEach(([key, item]) => {
      if (item) item.classList.toggle('on', key === tab);
    });

    // Close menu drawer if open
    this.closeMenu();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  },

  // ═══════════════════════════════════════════
  // 5. VIEW 1: LIVE EVENTS RENDERING
  // ═══════════════════════════════════════════
  renderEvents() {
    const grid = this.E['events-grid'];
    if (!grid) return;

    let list = [...this.S.events];

    // Apply Filter
    const f = this.S.eventFilter;
    if (f === 'live') {
      list = list.filter(e => e.isLive);
    } else if (f === 'prime') {
      list = list.filter(e => e.source === 'prime');
    } else if (f === 'tapmad') {
      list = list.filter(e => e.source === 'tapmad');
    } else if (f === 'cricket') {
      list = list.filter(e => (e.title + e.category).toLowerCase().includes('cricket'));
    } else if (f === 'football') {
      list = list.filter(e => (e.title + e.category).toLowerCase().includes('football'));
    } else if (f === 'upcoming') {
      list = list.filter(e => !e.isLive);
    }

    // Update count
    if (this.E['events-total-count']) {
      this.E['events-total-count'].textContent = `${list.length} টি ইভেন্ট`;
    }

    if (list.length === 0) {
      grid.innerHTML = `
        <div class="events-loader-state">
          <i class="fas fa-trophy" style="font-size: 2.2rem; color: var(--t3);"></i>
          <p style="color: var(--t2); font-weight: 600;">কোনো ইভেন্ট বা ম্যাচ খুঁজে পাওয়া যায়নি।</p>
        </div>
      `;
      return;
    }

    grid.innerHTML = list.map(ev => {
      const isLive = ev.isLive;
      const poster = ev.poster || ev.logo || 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=500&fit=crop';
      const sourceLabel = ev.source === 'prime' ? 'Prime Sports' : 'Tapmad BD';
      const sourceColor = ev.source === 'prime' ? '#0284c7' : '#10b981';

      return `
        <div class="event-card" data-id="${escHtml(ev.id)}" role="listitem">
          <div class="event-banner-wrap">
            <img class="event-banner-img" src="${escHtml(poster)}" alt="${escHtml(ev.title)}" loading="lazy" onerror="this.src='https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=500&fit=crop'" />
            <div class="event-overlay-badge ${isLive ? 'badge-status-live' : 'badge-status-upcoming'}">
              ${isLive ? '<i class="fas fa-circle" style="font-size: 8px;"></i> LIVE' : '<i class="far fa-clock"></i> UPCOMING'}
            </div>
            <div class="event-source-tag" style="border-color:${sourceColor}40;">
              <span style="color:${sourceColor}; font-weight:800;">●</span> ${escHtml(sourceLabel)}
            </div>
          </div>

          <div class="event-body">
            <div class="event-category-title">
              <i class="fas fa-medal"></i>
              <span>${escHtml(ev.tournament || ev.category)}</span>
            </div>

            <div class="event-main-title" title="${escHtml(ev.title)}">
              ${escHtml(ev.title)}
            </div>

            <div class="event-meta-row">
              <span class="event-time-badge">
                <i class="far fa-clock"></i> ${escHtml(ev.time)}
              </span>
              <span style="font-weight:700; color:var(--t3);">
                ${ev.servers ? `${ev.servers.length} সার্ভার` : 'HD Stream'}
              </span>
            </div>

            <div class="event-actions">
              <button class="btn-event-play" data-id="${escHtml(ev.id)}">
                <i class="fas fa-play"></i> <span>এখনই দেখুন</span>
              </button>
            </div>
          </div>
        </div>
      `;
    }).join('');

    // Attach click listeners to cards and play buttons
    grid.querySelectorAll('.event-card').forEach(card => {
      card.addEventListener('click', (e) => {
        const id = card.dataset.id;
        const item = this.S.events.find(x => x.id === id);
        if (item) this.playItem(item);
      });
    });
  },

  // ═══════════════════════════════════════════
  // 6. VIEW 2: LIVE TV RENDERING & FILTERS
  // ═══════════════════════════════════════════
  buildTvCategoryTabs() {
    const tabsContainer = this.E['cat-tabs'];
    if (!tabsContainer) return;

    // Collect unique normalized categories
    const catMap = new Map();
    this.S.channels.forEach(ch => {
      const c = ch.category || 'অন্যান্য';
      catMap.set(c, (catMap.get(c) || 0) + 1);
    });

    const sortedCats = Array.from(catMap.entries()).sort((a, b) => b[1] - a[1]);
    const categories = ['সব চ্যানেল', ...sortedCats.map(x => x[0])];

    tabsContainer.innerHTML = categories.map(c => {
      const isSel = (c === 'সব চ্যানেল' && !this.S.tvCatFilter) || this.S.tvCatFilter === c;
      const count = c === 'সব চ্যানেল' ? this.S.channels.length : (catMap.get(c) || 0);
      return `
        <button class="cat-tab ${isSel ? 'on' : ''}" data-cat="${escHtml(c)}">
          <span>${escHtml(c)}</span>
          <span class="cat-tab-badge">${count}</span>
        </button>
      `;
    }).join('');

    tabsContainer.querySelectorAll('.cat-tab').forEach(btn => {
      btn.addEventListener('click', () => {
        const cat = btn.dataset.cat;
        this.S.tvCatFilter = cat === 'সব চ্যানেল' ? '' : cat;
        this.S.visibleTvCount = 60;
        this.buildTvCategoryTabs();
        this.renderTvChannels();
      });
    });
  },

  renderTvChannels() {
    const grid = this.E['grid'];
    if (!grid) return;

    let list = [...this.S.channels];

    // 1. Source Filter (FAST TV vs NAFI TV24)
    if (this.S.tvSourceFilter === 'fasttv') {
      list = list.filter(c => c.source === 'fasttv');
    } else if (this.S.tvSourceFilter === 'nafitv') {
      list = list.filter(c => c.source === 'nafitv');
    }

    // 2. Category Filter
    if (this.S.tvCatFilter) {
      list = list.filter(c => (c.category || '').trim() === this.S.tvCatFilter);
    }

    // 3. Search Filter
    if (this.S.tvSearchQuery) {
      const q = this.S.tvSearchQuery.toLowerCase();
      list = list.filter(c => c.name.toLowerCase().includes(q) || (c.category || '').toLowerCase().includes(q));
    }

    // Total Count
    if (this.E['tv-total-count']) {
      this.E['tv-total-count'].textContent = `${list.length} টি চ্যানেল`;
    }

    // Empty state
    if (list.length === 0) {
      grid.innerHTML = `
        <div class="empty" style="grid-column: 1 / -1; padding: 40px; text-align: center;">
          <i class="fas fa-satellite-dish" style="font-size: 2.2rem; color: var(--t3); margin-bottom: 10px;"></i>
          <p style="color: var(--t2); font-weight: 600;">কোনো চ্যানেল পাওয়া যায়নি। ফিল্টার পরিবর্তন করুন।</p>
        </div>
      `;
      if (this.E['grid-more-wrap']) this.E['grid-more-wrap'].style.display = 'none';
      return;
    }

    // Apply view layout class
    grid.className = 'channel-grid ' + (this.S.tvViewMode === 'lst' ? 'view-list' : this.S.tvViewMode === 'g2' ? 'view-g2' : 'view-g3');

    // Slice for performance
    const visibleItems = list.slice(0, this.S.visibleTvCount);

    grid.innerHTML = visibleItems.map(c => {
      const logo = c.logo || FALLBACK_LOGO_SVG;
      const isFav = this.S.favorites.has(c.id);
      const isCur = this.S.curItem && this.S.curItem.id === c.id;
      const serverCount = c.servers ? c.servers.length : 1;
      const sourceBadge = c.source === 'fasttv' ? 'FAST' : 'NAFI';

      return `
        <div class="cc ${isCur ? 'active-channel' : ''}" data-id="${escHtml(c.id)}" role="listitem">
          <div class="cc-logo-wrap">
            <img class="cc-logo" src="${escHtml(logo)}" alt="${escHtml(c.name)}" loading="lazy" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
            <div class="cc-source-pill">${sourceBadge}</div>
            <div class="cc-overlay-play"><i class="fas fa-play"></i></div>
          </div>
          <div class="cc-foot">
            <div class="cc-name" title="${escHtml(c.name)}">${escHtml(c.name)}</div>
            <div class="cc-meta">
              <span class="cc-cat">${escHtml(c.category || 'Live TV')}</span>
              ${serverCount > 1 ? `<span class="cc-servers">${serverCount} সার্ভার</span>` : ''}
              <button class="cc-fav ${isFav ? 'on' : ''}" data-fav-id="${escHtml(c.id)}" aria-label="Favorite">
                <i class="${isFav ? 'fas' : 'far'} fa-star"></i>
              </button>
            </div>
          </div>
        </div>
      `;
    }).join('');

    // Pagination Button
    const moreWrap = this.E['grid-more-wrap'];
    if (moreWrap) {
      moreWrap.style.display = list.length > this.S.visibleTvCount ? 'flex' : 'none';
    }

    // Attach Channel Click
    grid.querySelectorAll('.cc').forEach(card => {
      card.addEventListener('click', (e) => {
        if (e.target.closest('.cc-fav')) return; // Fav button handled separately
        const id = card.dataset.id;
        const target = this.S.channels.find(c => c.id === id);
        if (target) this.playItem(target);
      });
    });

    // Attach Favorite Toggle
    grid.querySelectorAll('.cc-fav').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = btn.dataset.favId;
        this.toggleFavorite(id);
      });
    });
  },

  // ═══════════════════════════════════════════
  // 7. VIDEO PLAYER ENGINE (HLS & SHAKA DASH)
  // ═══════════════════════════════════════════
  async initShakaIfNeeded() {
    if (window.shaka && !this.shakaPlayer) {
      shaka.polyfill.installAll();
      if (shaka.Player.isBrowserSupported()) {
        this.shakaPlayer = new shaka.Player(this.E['main-video']);
        this.shakaPlayer.addEventListener('error', (event) => {
          console.error('Shaka DRM Error:', event.detail);
        });
      }
    }
  },

  playItem(item) {
    if (!item) return;
    this.S.curItem = item;
    this.S.currentServerIdx = 0;
    this.addRecent(item.id);

    // Show player wrapper
    if (this.E['player-wrap']) {
      this.E['player-wrap'].classList.add('on');
      this.E['player-wrap'].scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    // Set Info
    const itemName = item.title || item.name || 'Streaming';
    if (this.E['pw-name']) this.E['pw-name'].textContent = itemName;
    if (this.E['pw-category']) this.E['pw-category'].textContent = item.category || 'LIVE HD STREAM';
    if (this.E['pw-logo']) {
      this.E['pw-logo'].src = item.logo || item.poster || FALLBACK_LOGO_SVG;
    }

    // Populate Server Select Dropdown
    const serverSelect = this.E['pw-server-select'];
    const servers = (item.servers && item.servers.length > 0)
      ? item.servers
      : [{ name: 'সার্ভার ১ (HD)', url: item.url }];

    if (serverSelect) {
      serverSelect.innerHTML = servers.map((s, idx) =>
        `<option value="${idx}">${escHtml(s.name || `সার্ভার ${idx + 1}`)}</option>`
      ).join('');
    }

    this.loadStreamSource(servers[0].url, servers[0].drmKey || item.drmKey);
    this.renderTvChannels();
  },

  async loadStreamSource(streamUrl, drmKey = null, isProxyAttempt = false) {
    const video = this.E['main-video'];
    const loader = this.E['video-loader'];
    const errOverlay = this.E['video-error'];

    if (!video || !streamUrl) return;

    if (loader) loader.style.display = 'flex';
    if (errOverlay) errOverlay.style.display = 'none';

    // 1. Destroy active HLS instance if any
    if (this.hlsInstance) {
      this.hlsInstance.destroy();
      this.hlsInstance = null;
    }

    // 2. Unload active Shaka player if any
    if (this.shakaPlayer) {
      try {
        await this.shakaPlayer.unload();
      } catch (e) {}
    }

    const isMpd = streamUrl.includes('.mpd') || drmKey;
    const isHls = streamUrl.includes('.m3u8') || streamUrl.includes('m3u') || !isMpd;

    // ── PATH A: DASH / MPD with ClearKey DRM via Shaka Player ──
    if (isMpd && window.shaka) {
      try {
        await this.initShakaIfNeeded();
        if (this.shakaPlayer) {
          if (drmKey) {
            const [keyId, keyVal] = drmKey.split(':');
            this.shakaPlayer.configure({
              drm: {
                clearKeys: {
                  [keyId.trim()]: keyVal.trim()
                }
              }
            });
          }
          await this.shakaPlayer.load(streamUrl);
          if (loader) loader.style.display = 'none';
          video.play().catch(() => {});
          return;
        }
      } catch (err) {
        console.warn('Shaka play error:', err);
      }
    }

    // ── PATH B: HLS Stream via Hls.js ──
    if (isHls && window.Hls && Hls.isSupported()) {
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
        backBufferLength: 90,
        manifestLoadingMaxRetry: 2
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
              // If not already tried through CORS proxy, retry with proxy
              if (!isProxyAttempt && !streamUrl.includes('corsproxy.io')) {
                const proxyUrl = `https://corsproxy.io/?url=${encodeURIComponent(streamUrl)}`;
                this.loadStreamSource(proxyUrl, drmKey, true);
                return;
              }
              hls.startLoad();
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              hls.recoverMediaError();
              break;
            default:
              this.showPlayerError('স্ট্রিম লোড হতে সমস্যা হচ্ছে। পরবর্তী সার্ভারে সুইচ করুন।');
              break;
          }
        }
      });
      return;
    }

    // ── PATH C: Native Video ──
    video.src = streamUrl;
    video.load();
    video.play().then(() => {
      if (loader) loader.style.display = 'none';
    }).catch(() => {
      if (loader) loader.style.display = 'none';
    });
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
    if (servers.length <= 1) return;

    this.S.currentServerIdx = (this.S.currentServerIdx + 1) % servers.length;
    if (this.E['pw-server-select']) {
      this.E['pw-server-select'].value = this.S.currentServerIdx;
    }
    const s = servers[this.S.currentServerIdx];
    this.loadStreamSource(s.url, s.drmKey || this.S.curItem.drmKey);
  },

  closePlayer() {
    if (this.hlsInstance) {
      this.hlsInstance.destroy();
      this.hlsInstance = null;
    }
    if (this.shakaPlayer) {
      this.shakaPlayer.unload().catch(() => {});
    }
    const video = this.E['main-video'];
    if (video) {
      video.pause();
      video.removeAttribute('src');
      video.load();
    }
    if (this.E['player-wrap']) {
      this.E['player-wrap'].classList.remove('on');
    }
    this.S.curItem = null;
    this.renderTvChannels();
  },

  // ═══════════════════════════════════════════
  // 8. PLAYER CONTROLS (FULL CONTROL BAR)
  // ═══════════════════════════════════════════
  initPlayerControls() {
    const video = this.E['main-video'];
    const playBtn = this.E['vctrl-play'];
    const bigPlayBtn = this.E['vctrl-big-play'];
    const prog = this.E['vctrl-prog'];
    const progFill = this.E['vctrl-prog-fill'];
    const timeDisplay = this.E['vctrl-time'];
    const muteBtn = this.E['vctrl-mute'];
    const vol = this.E['vctrl-vol'];
    const pipBtn = this.E['vctrl-pip'];
    const fullBtn = this.E['vctrl-full'];
    const closeBtn = this.E['pw-close'];
    const serverSelect = this.E['pw-server-select'];
    const retryBtn = document.getElementById('player-err-retry');
    const nextSrvBtn = document.getElementById('player-err-next');

    if (!video) return;

    const togglePlay = () => {
      if (video.paused || video.ended) {
        video.play().catch(() => {});
      } else {
        video.pause();
      }
    };

    if (playBtn) playBtn.addEventListener('click', togglePlay);
    if (bigPlayBtn) bigPlayBtn.addEventListener('click', togglePlay);
    video.addEventListener('click', togglePlay);

    video.addEventListener('play', () => {
      if (playBtn) playBtn.innerHTML = '<i class="fas fa-pause"></i>';
      if (bigPlayBtn) bigPlayBtn.style.display = 'none';
    });

    video.addEventListener('pause', () => {
      if (playBtn) playBtn.innerHTML = '<i class="fas fa-play"></i>';
      if (bigPlayBtn) bigPlayBtn.style.display = 'flex';
    });

    video.addEventListener('timeupdate', () => {
      if (prog && progFill && video.duration && !isNaN(video.duration) && isFinite(video.duration)) {
        const pct = (video.currentTime / video.duration) * 100;
        prog.value = pct;
        progFill.style.width = pct + '%';
        if (timeDisplay) {
          timeDisplay.textContent = `${this.formatTime(video.currentTime)} / ${this.formatTime(video.duration)}`;
        }
      } else if (timeDisplay) {
        timeDisplay.innerHTML = '<span class="live-dot-red"></span> LIVE';
      }
    });

    if (prog) {
      prog.addEventListener('input', () => {
        if (video.duration && !isNaN(video.duration)) {
          video.currentTime = (prog.value / 100) * video.duration;
        }
      });
    }

    if (muteBtn) {
      muteBtn.addEventListener('click', () => {
        video.muted = !video.muted;
        muteBtn.innerHTML = video.muted ? '<i class="fas fa-volume-mute"></i>' : '<i class="fas fa-volume-up"></i>';
      });
    }

    if (vol) {
      vol.addEventListener('input', () => {
        video.volume = vol.value;
        video.muted = (vol.value == 0);
        if (muteBtn) {
          muteBtn.innerHTML = video.muted ? '<i class="fas fa-volume-mute"></i>' : '<i class="fas fa-volume-up"></i>';
        }
      });
    }

    if (pipBtn && document.pictureInPictureEnabled) {
      pipBtn.addEventListener('click', async () => {
        try {
          if (document.pictureInPictureElement) {
            await document.exitPictureInPicture();
          } else {
            await video.requestPictureInPicture();
          }
        } catch (e) {}
      });
    }

    if (fullBtn) {
      fullBtn.addEventListener('click', () => {
        const wrap = this.E['player-wrap'];
        if (!document.fullscreenElement) {
          if (wrap.requestFullscreen) wrap.requestFullscreen();
          else if (video.webkitEnterFullscreen) video.webkitEnterFullscreen();
        } else {
          if (document.exitFullscreen) document.exitFullscreen();
        }
      });
    }

    if (closeBtn) closeBtn.addEventListener('click', () => this.closePlayer());

    if (serverSelect) {
      serverSelect.addEventListener('change', () => {
        const idx = parseInt(serverSelect.value, 10);
        this.S.currentServerIdx = idx;
        if (this.S.curItem && this.S.curItem.servers && this.S.curItem.servers[idx]) {
          const s = this.S.curItem.servers[idx];
          this.loadStreamSource(s.url, s.drmKey || this.S.curItem.drmKey);
        }
      });
    }

    if (retryBtn) {
      retryBtn.addEventListener('click', () => {
        if (this.S.curItem) {
          const s = (this.S.curItem.servers && this.S.curItem.servers[this.S.currentServerIdx])
            ? this.S.curItem.servers[this.S.currentServerIdx]
            : { url: this.S.curItem.url };
          this.loadStreamSource(s.url, s.drmKey || this.S.curItem.drmKey);
        }
      });
    }

    if (nextSrvBtn) {
      nextSrvBtn.addEventListener('click', () => this.nextServer());
    }
  },

  formatTime(secs) {
    if (isNaN(secs) || !isFinite(secs)) return '0:00';
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  },

  // ═══════════════════════════════════════════
  // 9. EVENT LISTENERS & INTERACTION
  // ═══════════════════════════════════════════
  bindEvents() {
    // 1. Top Section Tabs (Live Events, Live TV, Download App)
    if (this.E['tab-btn-events']) {
      this.E['tab-btn-events'].addEventListener('click', () => this.switchTab('events'));
    }
    if (this.E['tab-btn-livetv']) {
      this.E['tab-btn-livetv'].addEventListener('click', () => this.switchTab('livetv'));
    }
    if (this.E['tab-btn-download']) {
      this.E['tab-btn-download'].addEventListener('click', () => this.switchTab('download'));
    }

    // 2. Bottom Nav Buttons
    if (this.E['bnav-events']) {
      this.E['bnav-events'].addEventListener('click', () => this.switchTab('events'));
    }
    if (this.E['bnav-livetv']) {
      this.E['bnav-livetv'].addEventListener('click', () => this.switchTab('livetv'));
    }
    if (this.E['bnav-download']) {
      this.E['bnav-download'].addEventListener('click', () => this.switchTab('download'));
    }

    // 3. Side Drawer Menu Items
    if (this.E['m-events']) {
      this.E['m-events'].addEventListener('click', (e) => { e.preventDefault(); this.switchTab('events'); });
    }
    if (this.E['m-livetv']) {
      this.E['m-livetv'].addEventListener('click', (e) => { e.preventDefault(); this.switchTab('livetv'); });
    }
    if (this.E['m-download']) {
      this.E['m-download'].addEventListener('click', (e) => { e.preventDefault(); this.switchTab('download'); });
    }

    // 4. Favorites & Recent Drawer Items
    if (this.E['m-fav']) {
      this.E['m-fav'].addEventListener('click', (e) => {
        e.preventDefault();
        this.switchTab('livetv');
        this.filterByFavorites();
      });
    }
    if (this.E['m-rec']) {
      this.E['m-rec'].addEventListener('click', (e) => {
        e.preventDefault();
        this.switchTab('livetv');
        this.filterByRecents();
      });
    }

    // 5. Drawer Open/Close
    if (this.E['menu-btn']) this.E['menu-btn'].addEventListener('click', () => this.openMenu());
    if (this.E['mc-btn']) this.E['mc-btn'].addEventListener('click', () => this.closeMenu());
    if (this.E['menu-ov']) this.E['menu-ov'].addEventListener('click', () => this.closeMenu());

    // 6. Settings Modal
    if (this.E['m-settings']) {
      this.E['m-settings'].addEventListener('click', (e) => {
        e.preventDefault();
        this.closeMenu();
        this.openSettings();
      });
    }
    if (this.E['set-ov']) this.E['set-ov'].addEventListener('click', () => this.closeSettings());

    // 7. Contact Modal
    if (this.E['m-contact']) {
      this.E['m-contact'].addEventListener('click', (e) => {
        e.preventDefault();
        this.closeMenu();
        this.openContact();
      });
    }
    if (this.E['contact-ov']) this.E['contact-ov'].addEventListener('click', () => this.closeContact());
    if (this.E['cs-btn']) this.E['cs-btn'].addEventListener('click', () => this.closeContact());

    // 8. Live Events Filters
    const eventsFilters = this.E['events-filters'];
    if (eventsFilters) {
      eventsFilters.querySelectorAll('.sf-btn').forEach(btn => {
        btn.addEventListener('click', () => {
          eventsFilters.querySelectorAll('.sf-btn').forEach(b => b.classList.remove('on'));
          btn.classList.add('on');
          this.S.eventFilter = btn.dataset.efilter || 'all';
          this.renderEvents();
        });
      });
    }

    // 9. TV Source Filter (FAST TV vs NAFI TV24)
    const tvSourceTabs = this.E['tv-source-tabs'];
    if (tvSourceTabs) {
      tvSourceTabs.querySelectorAll('.source-pill').forEach(pill => {
        pill.addEventListener('click', () => {
          tvSourceTabs.querySelectorAll('.source-pill').forEach(p => p.classList.remove('on'));
          pill.classList.add('on');
          this.S.tvSourceFilter = pill.dataset.source || 'all';
          this.S.visibleTvCount = 60;
          this.renderTvChannels();
        });
      });
    }

    // 10. TV View Layout Buttons (List / 2 Col / 3 Col)
    if (this.E['vb-lst']) {
      this.E['vb-lst'].addEventListener('click', () => this.setTvViewMode('lst'));
    }
    if (this.E['vb-g2']) {
      this.E['vb-g2'].addEventListener('click', () => this.setTvViewMode('g2'));
    }
    if (this.E['vb-g3']) {
      this.E['vb-g3'].addEventListener('click', () => this.setTvViewMode('g3'));
    }

    // 11. Pagination Load More
    if (this.E['grid-more-btn']) {
      this.E['grid-more-btn'].addEventListener('click', () => {
        this.S.visibleTvCount += 40;
        this.renderTvChannels();
      });
    }

    // 12. Global Search Overlay
    if (this.E['srch-btn']) this.E['srch-btn'].addEventListener('click', () => this.openSearch());
    if (this.E['srch-close']) this.E['srch-close'].addEventListener('click', () => this.closeSearch());
    if (this.E['srch-in']) {
      this.E['srch-in'].addEventListener('input', (e) => this.handleGlobalSearch(e.target.value));
    }

    // 13. Settings Toggles
    if (this.E['tog-theme']) {
      this.E['tog-theme'].addEventListener('click', () => {
        const isLt = document.body.classList.toggle('lt');
        this.S.theme = isLt ? 'light' : 'dark';
        this.E['tog-theme'].classList.toggle('on', !isLt);
        localStorage.setItem('nafitv_theme', this.S.theme);
      });
    }

    if (this.E['tog-auto']) {
      this.E['tog-auto'].addEventListener('click', () => {
        this.S.autoplay = !this.S.autoplay;
        this.E['tog-auto'].classList.toggle('on', this.S.autoplay);
      });
    }
  },

  setTvViewMode(mode) {
    this.S.tvViewMode = mode;
    ['vb-lst', 'vb-g2', 'vb-g3'].forEach(id => {
      if (this.E[id]) this.E[id].classList.remove('on');
    });
    if (mode === 'lst' && this.E['vb-lst']) this.E['vb-lst'].classList.add('on');
    if (mode === 'g2' && this.E['vb-g2']) this.E['vb-g2'].classList.add('on');
    if (mode === 'g3' && this.E['vb-g3']) this.E['vb-g3'].classList.add('on');
    this.renderTvChannels();
  },

  // ═══════════════════════════════════════════
  // 10. FAVORITES & RECENTS PERSISTENCE
  // ═══════════════════════════════════════════
  toggleFavorite(id) {
    if (this.S.favorites.has(id)) {
      this.S.favorites.delete(id);
    } else {
      this.S.favorites.add(id);
    }
    try {
      localStorage.setItem('nafitv_favs', JSON.stringify(Array.from(this.S.favorites)));
    } catch (e) {}
    this.renderTvChannels();
  },

  addRecent(id) {
    if (!id) return;
    this.S.recents = [id, ...this.S.recents.filter(x => x !== id)].slice(0, 20);
    try {
      localStorage.setItem('nafitv_recents', JSON.stringify(this.S.recents));
    } catch (e) {}
  },

  filterByFavorites() {
    this.S.tvCatFilter = '';
    const favChannels = this.S.channels.filter(c => this.S.favorites.has(c.id));
    if (favChannels.length === 0) {
      alert('আপনার পছন্দের তালিকায় কোনো চ্যানেল যুক্ত করা নেই। চ্যানেলের স্টার (★) বাটনে চাপ দিন।');
      return;
    }
    const grid = this.E['grid'];
    if (!grid) return;
    grid.innerHTML = favChannels.map(c => `
      <div class="cc" data-id="${escHtml(c.id)}">
        <div class="cc-logo-wrap">
          <img class="cc-logo" src="${escHtml(c.logo || FALLBACK_LOGO_SVG)}" alt="${escHtml(c.name)}" />
          <div class="cc-overlay-play"><i class="fas fa-play"></i></div>
        </div>
        <div class="cc-foot">
          <div class="cc-name">${escHtml(c.name)}</div>
          <div class="cc-meta"><span class="cc-cat">${escHtml(c.category)}</span></div>
        </div>
      </div>
    `).join('');

    grid.querySelectorAll('.cc').forEach(card => {
      card.addEventListener('click', () => {
        const target = this.S.channels.find(c => c.id === card.dataset.id);
        if (target) this.playItem(target);
      });
    });
  },

  filterByRecents() {
    const recChannels = this.S.channels.filter(c => this.S.recents.includes(c.id));
    if (recChannels.length === 0) {
      alert('সম্প্রতি কোনো চ্যানেল দেখা হয়নি।');
      return;
    }
    const grid = this.E['grid'];
    if (!grid) return;
    grid.innerHTML = recChannels.map(c => `
      <div class="cc" data-id="${escHtml(c.id)}">
        <div class="cc-logo-wrap">
          <img class="cc-logo" src="${escHtml(c.logo || FALLBACK_LOGO_SVG)}" alt="${escHtml(c.name)}" />
          <div class="cc-overlay-play"><i class="fas fa-play"></i></div>
        </div>
        <div class="cc-foot">
          <div class="cc-name">${escHtml(c.name)}</div>
          <div class="cc-meta"><span class="cc-cat">${escHtml(c.category)}</span></div>
        </div>
      </div>
    `).join('');

    grid.querySelectorAll('.cc').forEach(card => {
      card.addEventListener('click', () => {
        const target = this.S.channels.find(c => c.id === card.dataset.id);
        if (target) this.playItem(target);
      });
    });
  },

  // ═══════════════════════════════════════════
  // 11. TOP CAROUSEL & SEARCH
  // ═══════════════════════════════════════════
  renderUpcomingCarousel() {
    const trackHome = this.E['upc-track-home'];
    if (!trackHome) return;

    const items = [...this.S.events].slice(0, 10);
    if (items.length === 0) return;

    trackHome.innerHTML = items.map(ev => {
      const isLive = ev.isLive;
      const thumb = ev.poster || ev.logo || 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=400&fit=crop';
      return `
        <div class="upc-card ${isLive ? 'upc-live' : ''}" data-id="${escHtml(ev.id)}">
          <div class="upc-thumb-wrap">
            <img class="upc-thumb" src="${escHtml(thumb)}" alt="${escHtml(ev.title)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
            ${isLive ? '<div class="upc-live-badge"><div class="dot"></div>LIVE</div>' : '<div class="upc-upcoming-badge">UPCOMING</div>'}
          </div>
          <div class="upc-info">
            <div class="upc-title">${escHtml(ev.title)}</div>
            <div class="upc-meta">${escHtml(ev.tournament || ev.category)}</div>
            <div class="upc-time"><i class="far fa-clock"></i> ${escHtml(ev.time)}</div>
          </div>
        </div>
      `;
    }).join('');

    trackHome.querySelectorAll('.upc-card').forEach(card => {
      card.addEventListener('click', () => {
        const id = card.dataset.id;
        const item = this.S.events.find(x => x.id === id);
        if (item) this.playItem(item);
      });
    });
  },

  handleGlobalSearch(query) {
    const resBox = this.E['srch-res'];
    if (!resBox) return;

    const q = query.trim().toLowerCase();
    if (!q) {
      resBox.innerHTML = '<div class="empty"><i class="fas fa-search"></i><p>ইভেন্ট বা চ্যানেলের নাম লিখুন...</p></div>';
      return;
    }

    const matchedEvents = this.S.events.filter(e => e.title.toLowerCase().includes(q) || (e.category || '').toLowerCase().includes(q));
    const matchedChannels = this.S.channels.filter(c => c.name.toLowerCase().includes(q) || (c.category || '').toLowerCase().includes(q));

    if (matchedEvents.length === 0 && matchedChannels.length === 0) {
      resBox.innerHTML = `<div class="empty"><i class="fas fa-search"></i><p>"${escHtml(query)}" পাওয়া যায়নি।</p></div>`;
      return;
    }

    let html = '';
    if (matchedEvents.length > 0) {
      html += `<div class="srch-sec-title">🏆 লাইভ ইভেন্ট (${matchedEvents.length})</div>`;
      html += matchedEvents.slice(0, 8).map(ev => `
        <div class="srch-item" data-type="event" data-id="${escHtml(ev.id)}">
          <img class="srch-logo" src="${escHtml(ev.poster || ev.logo || FALLBACK_LOGO_SVG)}" alt="${escHtml(ev.title)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
          <div class="srch-info">
            <div class="srch-name">${escHtml(ev.title)}</div>
            <div class="srch-cat">${escHtml(ev.category)} • ${escHtml(ev.time)}</div>
          </div>
          <button class="srch-play-btn"><i class="fas fa-play"></i></button>
        </div>
      `).join('');
    }

    if (matchedChannels.length > 0) {
      html += `<div class="srch-sec-title">📺 লাইভ টিভি চ্যানেল (${matchedChannels.length})</div>`;
      html += matchedChannels.slice(0, 12).map(c => `
        <div class="srch-item" data-type="channel" data-id="${escHtml(c.id)}">
          <img class="srch-logo" src="${escHtml(c.logo || FALLBACK_LOGO_SVG)}" alt="${escHtml(c.name)}" onerror="this.src='${FALLBACK_LOGO_SVG}'" />
          <div class="srch-info">
            <div class="srch-name">${escHtml(c.name)}</div>
            <div class="srch-cat">${escHtml(c.category)} (${c.source === 'fasttv' ? 'FAST TV' : 'NAFI TV24'})</div>
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
        if (type === 'event') {
          const ev = this.S.events.find(x => x.id === id);
          if (ev) this.playItem(ev);
        } else if (type === 'channel') {
          const ch = this.S.channels.find(x => x.id === id);
          if (ch) this.playItem(ch);
        }
      });
    });
  },

  // ── Modals & Overlays Helpers ──
  openMenu() {
    if (this.E['menu-ov']) this.E['menu-ov'].style.display = 'block';
    if (this.E['menu']) this.E['menu'].classList.add('on');
  },
  closeMenu() {
    if (this.E['menu-ov']) this.E['menu-ov'].style.display = 'none';
    if (this.E['menu']) this.E['menu'].classList.remove('on');
  },
  openSearch() {
    if (this.E['srch-ov']) {
      this.E['srch-ov'].style.display = 'flex';
      if (this.E['srch-in']) {
        this.E['srch-in'].value = '';
        this.E['srch-in'].focus();
      }
    }
  },
  closeSearch() {
    if (this.E['srch-ov']) this.E['srch-ov'].style.display = 'none';
  },
  openSettings() {
    if (this.E['set-ov']) this.E['set-ov'].style.display = 'block';
    if (this.E['settings']) this.E['settings'].classList.add('on');
  },
  closeSettings() {
    if (this.E['set-ov']) this.E['set-ov'].style.display = 'none';
    if (this.E['settings']) this.E['settings'].classList.remove('on');
  },
  openContact() {
    if (this.E['contact-ov']) this.E['contact-ov'].style.display = 'block';
    if (this.E['contact-sheet']) this.E['contact-sheet'].classList.add('on');
  },
  closeContact() {
    if (this.E['contact-ov']) this.E['contact-ov'].style.display = 'none';
    if (this.E['contact-sheet']) this.E['contact-sheet'].classList.remove('on');
  }
};

// ═══════════════════════════════════════════
// 12. BOOTSTRAP APPLICATION
// ═══════════════════════════════════════════
document.addEventListener('DOMContentLoaded', () => {
  APP.init();
});
