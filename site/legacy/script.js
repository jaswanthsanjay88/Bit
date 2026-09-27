/* ==========================================================================
   BIT — Landing Page Client-side Script
   ========================================================================== */

function checkHash() {
  const hash = (window.location.hash || '').toLowerCase();
  if (hash === '#apply' || hash === '#testerform' || hash === '#testermodal' || hash === '#qa') {
    openTesterModal();
  }
}

document.addEventListener('DOMContentLoaded', () => {
  initAnnouncementBanner();
  initPrivacyToast();
  initNavbarScroll();
  initMobileNavigation();
  initCodeCopyButtons();
  initMouseParallax();
  initGitHubApi();
  initIntersectionObserver();
  initLiveRatings();
  initModelStoreCatalog();
  initOilSpringMotion();
  
  // Auto-open tester form if linked directly via hash
  setTimeout(checkHash, 100);
});

window.addEventListener('hashchange', checkHash);

/* ── Sticky Navbar Blur ── */
function initNavbarScroll() {
  const headerStack = document.getElementById('headerStack');
  const navbar = document.getElementById('navbar');

  const handleScroll = () => {
    const isScrolled = window.scrollY > 30;
    if (headerStack) headerStack.classList.toggle('scrolled', isScrolled);
    if (navbar) navbar.classList.toggle('scrolled', isScrolled);
  };

  window.addEventListener('scroll', handleScroll, { passive: true });
  handleScroll();
}

/* ── 3D Hero Mouse Parallax ── */
function initMouseParallax() {
  const stage = document.getElementById('heroStage');
  const phoneLeft = document.getElementById('phoneLeft');
  const phoneRight = document.getElementById('phoneRight');

  if (!stage || !phoneLeft || !phoneRight) return;

  // Respect reduced motion preference
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

  let bounds = stage.getBoundingClientRect();
  
  window.addEventListener('resize', () => {
    bounds = stage.getBoundingClientRect();
  });

  stage.addEventListener('mousemove', (e) => {
    const mouseX = e.clientX - bounds.left - bounds.width / 2;
    const mouseY = e.clientY - bounds.top - bounds.height / 2;

    const rotX = (mouseY / bounds.height) * -16;
    const rotY = (mouseX / bounds.width) * 16;

    phoneLeft.style.transform = `rotateY(${-18 + rotY}deg) rotateX(${12 + rotX}deg) translateZ(30px)`;
    phoneRight.style.transform = `rotateY(${18 + rotY}deg) rotateX(${12 + rotX}deg) translateZ(10px)`;
  });

  stage.addEventListener('mouseleave', () => {
    phoneLeft.style.transform = `rotateY(-18deg) rotateX(12deg) translateZ(20px)`;
    phoneRight.style.transform = `rotateY(18deg) rotateX(12deg) translateZ(0px)`;
  });
}

/* ── Live GitHub API Integration ── */
async function initGitHubApi() {
  const repoOwner = 'jaswanthsanjay88';
  const repoName = 'Bit_Android';

  const starCountEl = document.getElementById('githubStarCount');
  const versionTagEl = document.getElementById('githubVersionTag');
  const downloadSizeEl = document.getElementById('githubDownloadSize');

  try {
    // Fetch Repo Metadata (Stars)
    const repoRes = await fetch(`https://api.github.com/repos/${repoOwner}/${repoName}`);
    if (repoRes.ok) {
      const repoData = await repoRes.json();
      if (starCountEl && repoData.stargazers_count !== undefined) {
        starCountEl.textContent = `★ ${repoData.stargazers_count.toLocaleString()}`;
      }
    }
  } catch (err) {
    console.log('GitHub repo info fetch fallback:', err);
  }

  try {
    // Fetch Latest Release Info
    const releaseRes = await fetch(`https://api.github.com/repos/${repoOwner}/${repoName}/releases/latest`);
    if (releaseRes.ok) {
      const releaseData = await releaseRes.json();
      if (versionTagEl && releaseData.tag_name) {
        versionTagEl.textContent = `${releaseData.tag_name}`;
      }
      if (downloadSizeEl && releaseData.assets && releaseData.assets.length > 0) {
        const universalAsset = releaseData.assets.find(a => a.name.includes('universal')) || releaseData.assets[0];
        const sizeMb = (universalAsset.size / (1024 * 1024)).toFixed(1);
        downloadSizeEl.textContent = `Latest Release (${sizeMb} MB APK)`;
      }
    }
  } catch (err) {
    console.log('GitHub release fetch fallback:', err);
  }
}

/* ── Intersection Observer Scroll Reveal ── */
function initIntersectionObserver() {
  const elements = document.querySelectorAll('.stat-card, .showcase-frame-container, .feature-item, .cta-stage');

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.style.opacity = '1';
        entry.target.style.transform = 'translateY(0)';
        observer.unobserve(entry.target);
      }
    });
  }, {
    threshold: 0.15
  });

  elements.forEach(el => {
    el.style.opacity = '0';
    el.style.transform = 'translateY(24px)';
    el.style.transition = 'opacity 0.6s cubic-bezier(0.16, 1, 0.3, 1), transform 0.6s cubic-bezier(0.16, 1, 0.3, 1)';
    observer.observe(el);
  });
}

/* ── Live Ratings API Fetch & Marquee Population ── */
async function initLiveRatings() {
  const track1 = document.getElementById('marqueeTrack1');
  const track2 = document.getElementById('marqueeTrack2');
  const track3 = document.getElementById('marqueeTrack3');
  if (!track1) return;

  let reviews = [];

  try {
    const res = await fetch('https://api.jaswanthsanjay.me/api/rating');
    if (res.ok) {
      const data = await res.json();
      if (data.success && data.ratings && data.ratings.length > 0) {
        reviews = data.ratings;
      }
    }
  } catch (err) {
    console.log('Using default reviews fallback:', err);
  }

  // Fallback reviews matching the app's features
  if (!reviews || reviews.length === 0) {
    reviews = [
      {
        name: "Alex R.",
        role: "Entrepreneur",
        rating: 5,
        comment: "This on-device stack is a game-changer for my workflow. Zero latency, 100% offline privacy."
      },
      {
        name: "Sarah W.",
        role: "AI Developer",
        rating: 5,
        comment: "GBNF grammar-constrained JSON tool calling directly on Android silicon is unbelievable."
      },
      {
        name: "Michael B.",
        role: "Startup Founder",
        rating: 5,
        comment: "The performance on Snapdragon chips is top tier. Running GGUF models locally with zero cloud dependencies."
      },
      {
        name: "Jessica L.",
        role: "Mobile Architect",
        rating: 5,
        comment: "Whisper STT and Piper TTS running offline in background threads. Incredible work!"
      },
      {
        name: "Chloe K.",
        role: "Product Manager",
        rating: 5,
        comment: "The most privacy-conscious mobile AI application I have used."
      },
      {
        name: "David P.",
        role: "Software Engineer",
        rating: 5,
        comment: "Clean Kotlin + Compose UI paired with native C++ llama.cpp bindings."
      }
    ];
  }

  function renderCard(item) {
    const seed = encodeURIComponent(item.name || 'User');
    const avatarUrl = item.avatar || `https://api.dicebear.com/10.x/notionists/svg?seed=${seed}`;
    const stars = '★'.repeat(item.rating || 5) + '☆'.repeat(5 - (item.rating || 5));

    return `
      <div class="review-card">
        <div class="review-header">
          <img src="${avatarUrl}" alt="${item.name}" class="review-avatar" />
          <div>
            <div class="review-author">${item.name}</div>
            <div class="review-role">${item.role || 'BIT User'}</div>
          </div>
        </div>
        <div class="review-stars">${stars}</div>
        <div class="review-comment">"${item.comment || item.review || item.feedback}"</div>
      </div>
    `;
  }

  const col1Items = [];
  const col2Items = [];
  const col3Items = [];

  reviews.forEach((rev, idx) => {
    if (idx % 3 === 0) col1Items.push(rev);
    else if (idx % 3 === 1) col2Items.push(rev);
    else col3Items.push(rev);
  });

  const buildColumnHtml = (items) => {
    const list = [...items, ...items, ...items];
    return `<div class="flex-col-gap">${list.map(renderCard).join('')}</div>
            <div class="flex-col-gap">${list.map(renderCard).join('')}</div>`;
  };

  if (track1) track1.innerHTML = buildColumnHtml(col1Items.length ? col1Items : reviews);
  if (track2) track2.innerHTML = buildColumnHtml(col2Items.length ? col2Items : reviews);
  if (track3) track3.innerHTML = buildColumnHtml(col3Items.length ? col3Items : reviews);
}

/* ── Mobile Navigation Drawer Controller ── */
function initMobileNavigation() {
  const menuBtn = document.getElementById('mobileMenuBtn');
  const drawer = document.getElementById('mobileNavDrawer');
  const backdrop = document.getElementById('mobileNavBackdrop');
  if (!menuBtn || !drawer) return;

  function openDrawer() {
    menuBtn.classList.add('active');
    menuBtn.setAttribute('aria-expanded', 'true');
    drawer.classList.add('open');
    drawer.setAttribute('aria-hidden', 'false');
    document.body.style.overflow = 'hidden';
  }

  function closeDrawer() {
    menuBtn.classList.remove('active');
    menuBtn.setAttribute('aria-expanded', 'false');
    drawer.classList.remove('open');
    drawer.setAttribute('aria-hidden', 'true');
    document.body.style.overflow = '';
  }

  menuBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    const isOpen = drawer.classList.contains('open');
    if (isOpen) closeDrawer();
    else openDrawer();
  });

  if (backdrop) {
    backdrop.addEventListener('click', closeDrawer);
  }

  drawer.querySelectorAll('.mobile-nav-link').forEach(link => {
    link.addEventListener('click', closeDrawer);
  });

  window.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && drawer.classList.contains('open')) {
      closeDrawer();
    }
  });
}

/* ── 1-Click Code Copy Interaction ── */
function initCodeCopyButtons() {
  document.querySelectorAll('.code-copy-btn').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.stopPropagation();
      let textToCopy = btn.getAttribute('data-copy');
      if (!textToCopy) {
        const pre = btn.closest('.code-panel')?.querySelector('pre code') || btn.closest('.code-block-wrapper')?.querySelector('pre code');
        if (pre) textToCopy = pre.innerText || pre.textContent;
      }
      if (!textToCopy) return;

      try {
        await navigator.clipboard.writeText(textToCopy);
        const copyTextEl = btn.querySelector('.copy-text');
        const origText = copyTextEl ? copyTextEl.textContent : 'Copy';
        btn.classList.add('copied');
        if (copyTextEl) copyTextEl.textContent = 'Copied!';

        setTimeout(() => {
          btn.classList.remove('copied');
          if (copyTextEl) copyTextEl.textContent = origText;
        }, 1800);
      } catch (err) {
        console.log('Clipboard copy fallback:', err);
      }
    });
  });
}

/* ── Curated Model Catalog Fetch, Search & Filter ── */
async function initModelStoreCatalog() {
  const modelsGrid = document.getElementById('modelsGrid');
  const searchInput = document.getElementById('modelSearchInput');
  const searchClear = document.getElementById('modelSearchClear');
  const filterPills = document.querySelectorAll('.model-filter-pills .filter-pill');
  if (!modelsGrid) return;

  let allModels = [];
  let currentCategory = 'all';
  let searchQuery = '';

  function renderModels(filtered) {
    if (!filtered || filtered.length === 0) {
      modelsGrid.innerHTML = `
        <div class="model-catalog-empty">
          <p>No tested models match "${searchQuery || currentCategory}".</p>
          <button type="button" class="btn-ghost" id="modelResetFilters">Clear search & filters</button>
        </div>
      `;
      const resetBtn = document.getElementById('modelResetFilters');
      if (resetBtn) {
        resetBtn.addEventListener('click', () => {
          if (searchInput) {
            searchInput.value = '';
            if (searchClear) searchClear.style.display = 'none';
          }
          searchQuery = '';
          currentCategory = 'all';
          filterPills.forEach(p => p.classList.toggle('active', p.getAttribute('data-filter') === 'all'));
          renderModels(allModels);
        });
      }
      return;
    }

    modelsGrid.innerHTML = filtered.map(model => {
      const typeLower = (model.type || 'gguf').toLowerCase();
      const badgeClass = `badge-${typeLower}`;
      const iconUrl = model.iconUrl || (model.icon ? `https://raw.githubusercontent.com/lobehub/lobe-icons/main/packages/static-png/light/${model.icon}.png` : '');
      const iconHtml = iconUrl ? `<img src="${iconUrl}" alt="${model.name}" class="model-brand-icon" onerror="this.style.display='none'" />` : `<div class="model-brand-icon"></div>`;

      const ramHtml = model.minRamGb ? `<span class="meta-chip meta-chip-ram">${model.minRamGb} GB RAM</span>` : '';
      const tagsHtml = (model.tags || []).slice(0, 3).map(t => `<span class="meta-chip">${t}</span>`).join('');

      return `
        <div class="model-card-site">
          <div>
            <div class="model-header">
              ${iconHtml}
              <div class="model-title-box">
                <div class="model-name">${model.name}</div>
              </div>
              <span class="model-type-badge ${badgeClass}">${model.type}</span>
            </div>
            <div class="model-desc">${model.description}</div>
            <div class="model-meta-row">
              ${ramHtml}
              ${tagsHtml}
            </div>
          </div>
          <div class="model-card-footer">
            <span class="model-size">${model.size}</span>
            <a href="${model.url}" target="_blank" rel="noopener noreferrer" class="btn-download-sm">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              Download
            </a>
          </div>
        </div>
      `;
    }).join('');
  }

  function applyFilters() {
    let filtered = allModels;

    // Filter by category
    if (currentCategory !== 'all') {
      filtered = filtered.filter(m => {
        const type = (m.type || '').toUpperCase();
        const tags = (m.tags || []).map(t => t.toLowerCase());
        const desc = (m.description || '').toLowerCase();
        const name = (m.name || '').toLowerCase();

        if (currentCategory === 'llm') {
          return type === 'GGUF' && !tags.includes('vision') && !tags.includes('embedding') && !desc.includes('vision');
        } else if (currentCategory === 'vision') {
          return type === 'VISION' || tags.includes('vision') || desc.includes('vision') || name.includes('vision') || name.includes('minicpm') || name.includes('moondream');
        } else if (currentCategory === 'audio') {
          return type === 'SHERPA_ONNX' || type === 'VITS' || tags.includes('speech') || tags.includes('tts') || tags.includes('stt') || desc.includes('whisper') || desc.includes('piper');
        } else if (currentCategory === 'embedding') {
          return type === 'EMBEDDING' || tags.includes('embedding') || desc.includes('embedding') || desc.includes('rag');
        }
        return true;
      });
    }

    // Filter by search query
    if (searchQuery.trim().length > 0) {
      const q = searchQuery.toLowerCase().trim();
      filtered = filtered.filter(m => {
        const name = (m.name || '').toLowerCase();
        const desc = (m.description || '').toLowerCase();
        const tags = (m.tags || []).map(t => t.toLowerCase()).join(' ');
        const type = (m.type || '').toLowerCase();
        return name.includes(q) || desc.includes(q) || tags.includes(q) || type.includes(q);
      });
    }

    renderModels(filtered);
  }

  // Setup search input listeners
  if (searchInput) {
    let searchDebounce = null;
    searchInput.addEventListener('input', (e) => {
      searchQuery = e.target.value;
      if (searchClear) searchClear.style.display = searchQuery ? 'block' : 'none';
      clearTimeout(searchDebounce);
      searchDebounce = setTimeout(applyFilters, 120);
    });

    if (searchClear) {
      searchClear.addEventListener('click', () => {
        searchInput.value = '';
        searchQuery = '';
        searchClear.style.display = 'none';
        applyFilters();
      });
    }
  }

  // Setup category filter listeners
  filterPills.forEach(pill => {
    pill.addEventListener('click', () => {
      filterPills.forEach(p => {
        p.classList.remove('active');
        p.setAttribute('aria-selected', 'false');
      });
      pill.classList.add('active');
      pill.setAttribute('aria-selected', 'true');
      currentCategory = pill.getAttribute('data-filter') || 'all';
      applyFilters();
    });
  });

  try {
    const res = await fetch('./api/models.json');
    if (res.ok) {
      const data = await res.json();
      allModels = data.models || [];
    } else {
      const fallbackRes = await fetch('/api/models');
      if (fallbackRes.ok) {
        const data = await fallbackRes.json();
        allModels = data.models || data;
      }
    }

    if (allModels && allModels.length > 0) {
      applyFilters();
    }
  } catch (err) {
    console.log('Model Catalog fetch error:', err);
  }
}

/* ── Announcement Banner & Responsive Header Offset ── */
function initAnnouncementBanner() {
  const headerStack = document.getElementById('headerStack');
  const banner = document.getElementById('announcementBanner');

  function updateBodyPadding() {
    if (headerStack) {
      const h = headerStack.offsetHeight || 0;
      document.body.style.paddingTop = h + 'px';
    }
  }

  if (banner && sessionStorage.getItem('bit_announcement_dismissed') === 'true') {
    banner.style.display = 'none';
    updateBodyPadding();
    return;
  }

  updateBodyPadding();

  if (headerStack && window.ResizeObserver) {
    const observer = new ResizeObserver(() => updateBodyPadding());
    observer.observe(headerStack);
  } else {
    window.addEventListener('resize', updateBodyPadding, { passive: true });
  }

  window.dismissAnnouncementBanner = function() {
    if (banner) banner.style.display = 'none';
    sessionStorage.setItem('bit_announcement_dismissed', 'true');
    updateBodyPadding();
  };
}

/* ── Privacy Toast Persistence & Dismissal ── */
function initPrivacyToast() {
  const toast = document.getElementById('privacyToast');
  if (!toast) return;

  if (localStorage.getItem('bit_privacy_toast_dismissed_v20260806') === 'true') {
    toast.style.display = 'none';
    return;
  }

  window.dismissPrivacyToast = function() {
    toast.classList.add('toast-dismissed');
    localStorage.setItem('bit_privacy_toast_dismissed_v20260806', 'true');
    setTimeout(() => {
      toast.style.display = 'none';
    }, 400);
  };
}

/* ── Helper to Open & Close QA Tester Modal ── */
window.openTesterModal = function() {
  const testerModal = document.getElementById('testerModal');
  const testerForm = document.getElementById('testerForm');
  const successMsg = document.getElementById('successMsg');
  if (!testerModal) return;

  if (successMsg) successMsg.style.display = 'none';
  if (testerForm) {
    testerForm.style.display = 'flex';
  }

  if (typeof testerModal.showModal === 'function') {
    try {
      if (!testerModal.open) {
        testerModal.showModal();
      }
    } catch (e) {
      testerModal.setAttribute('open', 'true');
    }
  } else {
    testerModal.setAttribute('open', 'true');
  }
};

window.closeTesterModal = function() {
  const testerModal = document.getElementById('testerModal');
  if (!testerModal) return;
  if (typeof testerModal.close === 'function') {
    try {
      testerModal.close();
    } catch (e) {
      testerModal.removeAttribute('open');
    }
  } else {
    testerModal.removeAttribute('open');
  }
};

/* ── Oil Motion: Spring Physics & Magnetic Micro-interactions ── */
function initOilSpringMotion() {
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

  // 1. Magnetic Buttons with Spring Physics
  const magneticButtons = document.querySelectorAll('.btn-pill, .btn-ghost, .announcement-btn');
  magneticButtons.forEach((btn) => {
    btn.addEventListener('mousemove', (e) => {
      const rect = btn.getBoundingClientRect();
      const x = e.clientX - rect.left - rect.width / 2;
      const y = e.clientY - rect.top - rect.height / 2;

      btn.style.transform = `translate(${x * 0.18}px, ${y * 0.18}px) scale(1.02)`;
      btn.style.transition = 'transform 0.1s ease-out';
    });

    btn.addEventListener('mouseleave', () => {
      btn.style.transform = 'translate(0px, 0px) scale(1)';
      btn.style.transition = 'transform 0.45s cubic-bezier(0.16, 1, 0.3, 1)';
    });
  });

  // 2. Spring 3D Tilt for Story and Pillar Cards
  const tiltCards = document.querySelectorAll('.story-card, .pillar-card, .product-card');
  tiltCards.forEach((card) => {
    card.addEventListener('mousemove', (e) => {
      const rect = card.getBoundingClientRect();
      const x = e.clientX - rect.left - rect.width / 2;
      const y = e.clientY - rect.top - rect.height / 2;

      const rotX = (y / (rect.height / 2)) * -4;
      const rotY = (x / (rect.width / 2)) * 4;

      card.style.transform = `perspective(1000px) rotateX(${rotX}deg) rotateY(${rotY}deg) translateY(-4px)`;
      card.style.transition = 'transform 0.1s ease-out';
    });

    card.addEventListener('mouseleave', () => {
      card.style.transform = 'perspective(1000px) rotateX(0deg) rotateY(0deg) translateY(0px)';
      card.style.transition = 'transform 0.5s cubic-bezier(0.16, 1, 0.3, 1)';
    });
  });
}

