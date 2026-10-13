// The #how photo story's engine, ported 1:1 from sketch A (the approved template). It builds the stage(s) inside the
// skeleton HowPhotoStory.jsx renders, follows the page scroll (never steers it), and switches between the pinned film
// and the static version. mountHowPhotoStory(root, options) returns destroy(), which removes every listener, observer,
// timer, frame request and node it created, so a remount (React StrictMode in dev) leaves exactly one working film.
// destroy.sample() re-reads the scroll position at once and reports it (the analytics confirm a scene on it).
// QA hooks (read by scripts/how-story-browser-checks.mjs and scripts/verify-how-story-browser.mjs, not styling):
// root[data-how-mode], root[data-how-rest] (the scene at rest, 0 = none), track[data-how-beat|step],
// .world[data-how-pose] ("beat.step" a stage shows), [data-how-scene] static items, [data-how-probe].
import {
  COPY,
  IMAGE_GROUPS,
  INNER,
  PHOTOS,
  PIN_VH,
  STATIC_FIT,
  STEPS,
  TAG,
  TITLE,
  TM,
  COMPACT_VH,
  chainIsFast,
  copyFade,
  copyFor,
  driftAt,
  fitWorld,
  halfInView,
  imageGroupOf,
  imageUrl,
  innerStepOnEnter,
  isStaticMode,
  latchViewportHeight,
  placeOf,
  restPosition,
  sceneAtRest,
  sceneOf,
  stageTooSmall,
  stateAt,
  willPass,
} from "./howPhotoStory.js";

// The projected Korea map (~46 KB of path data) is its own chunk, fetched with the pictures when the section is near.
const loadMapData = () => import("./data/how-photo-map.json").then((module) => module.default ?? module);
// a 1x1 transparent GIF: what a picture that failed to load shows over its preview
const BLANK_PIXEL = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

const PIN_SVG = '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#062b1c" d="M12 2.2c-4.1 0-7.4 3.2-7.4 7.3 0 5.2 6.1 11.3 6.6 11.8.4.4 1.1.4 1.5 0 .5-.5 6.7-6.6 6.7-11.8 0-4.1-3.3-7.3-7.4-7.3z"/><circle cx="12" cy="9.6" r="2.9" fill="#5cedb9"/></svg>';
const CHK_SVG = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5.5 12.6l4.2 4.1 8.8-9.4" fill="none" stroke="#fff" stroke-width="3.2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
const tagHTML = () => `<div class="tag">${PIN_SVG}<span>${TAG}</span></div>`;
const P01_HTML = `<div class="p01"><i class="stem"></i><i class="dot"></i><div class="ptg">${tagHTML()}</div></div>`;

// SVG ids get a per-stage suffix (the static version builds five stages; each mount has its own prefix)
const LOCK_SVG = (u) => `<svg viewBox="0 0 136 166"><defs>
    <linearGradient id="lkb${u}" x1="0" y1="0" x2=".9" y2="1"><stop offset="0" stop-color="#52eab3"/><stop offset=".5" stop-color="#17c985"/><stop offset="1" stop-color="#0a9d67"/></linearGradient>
    <linearGradient id="lks${u}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#ffffff"/><stop offset=".55" stop-color="#dff3ea"/><stop offset="1" stop-color="#f3fbf7"/></linearGradient></defs>
    <g class="lk-sh"><path d="M36 82V54a32 32 0 0 1 64 0v28" fill="none" stroke="#0a9d67" stroke-width="19" stroke-linecap="round"/><path d="M36 82V54a32 32 0 0 1 64 0v28" fill="none" stroke="url(#lks${u})" stroke-width="15" stroke-linecap="round"/></g>
    <rect x="10" y="70" width="116" height="94" rx="28" fill="url(#lkb${u})"/>
    <rect x="22" y="77" width="92" height="26" rx="13" fill="#fff" opacity=".3"/>
    <circle cx="68" cy="113" r="11.5" fill="#fff"/><rect x="62.5" y="114" width="11" height="25" rx="5.5" fill="#fff"/></svg>`;
// the cloud: a back body (behind the photos) and a front lobe (in front of their lower halves). Both use the same
// gradient in the same coordinates so the lobe melts into the body; a thin pale-mint edge gives the silhouette.
const CB = '<rect x="24" y="124" width="352" height="104" rx="52"/><circle cx="108" cy="126" r="64"/><circle cx="204" cy="94" r="88"/><circle cx="300" cy="124" r="64"/>';
const CF = '<rect x="16" y="184" width="368" height="58" rx="29"/><circle cx="72" cy="188" r="32"/><circle cx="146" cy="178" r="38"/><circle cx="224" cy="176" r="40"/><circle cx="300" cy="182" r="36"/><circle cx="352" cy="194" r="26"/>';
const cloudSVG = (shapes, id) => `<svg viewBox="0 0 400 250"><defs><linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="0" y1="8" x2="0" y2="244"><stop class="c0" offset="0" stop-color="#ffffff"/><stop class="c1" offset=".55" stop-color="#f7fbf9"/><stop class="c2" offset="1" stop-color="#e6f4ec"/></linearGradient></defs>
    <g class="edge" fill="none" stroke="#c4ead9" stroke-width="4">${shapes}</g><g fill="url(#${id})">${shapes}</g></svg>`;
// 05: the map is a stack of flat layers (land, 대한민국, 경상북도, 경주시); only their wrappers fade/scale.
const mapLayers = (MAP) => {
  const VB = `viewBox="0 0 ${MAP.w} ${MAP.h}"`;
  return `
    <div class="ml m-base"><svg ${VB}><path class="out" d="${MAP.out}" fill="none" stroke="#bccbc2" stroke-width="3" stroke-linejoin="round"/><path class="big" d="${MAP.landBig}" fill="#e2e9e4" stroke="#ffffff" stroke-width="1.3" stroke-linejoin="round"/><path class="small" d="${MAP.landSmall}" fill="#d3ddd6"/></svg></div>
    <div class="ml m-kr"><svg ${VB}><path class="out" d="${MAP.out}" fill="none" stroke="#17c985" stroke-width="5" stroke-linejoin="round"/><path class="big" d="${MAP.landBig}" fill="#9adcbc" stroke="#ffffff" stroke-width="1.3" stroke-linejoin="round"/><path class="small" d="${MAP.landSmall}" fill="#86d3ae"/></svg></div>
    <div class="ml m-gb"><svg ${VB}><path class="big" d="${MAP.gbBig}" fill="#25a873" stroke="#ffffff" stroke-width="1.2" stroke-linejoin="round"/><path class="small" d="${MAP.gbSmall}" fill="#1f9a68"/></svg></div>
    <div class="ml m-gj"><svg ${VB}><path class="big" d="${MAP.gj}" fill="#04432e" stroke="#ffffff" stroke-width="1.2" stroke-linejoin="round"/></svg></div>`;
};

const SPRING = "var(--hw-spring)", SETTLE = "cubic-bezier(.3,1.15,.4,1)", EASE_OUT = "cubic-bezier(.3,.9,.3,1)", EASE_IN = "cubic-bezier(.4,0,.6,1)";
const DROP = "cubic-bezier(.55,0,.4,1)";
let MOUNTS = 0;

export function mountHowPhotoStory(root, { lqip = {}, onState = null, loadMap = loadMapData } = {}) {
  const LQ = lqip;
  const mountId = MOUNTS++;
  let UID = 0;
  let destroyed = false;
  const cleanups = [];
  const q = (selector) => root.querySelector(selector);
  const head = q(".hw-head");
  const copyEl = q(".hw-copy");
  const stageEl = q(".hw-stage");
  const track = q(".hw-track");
  const frameEl = q(".hw-frame");
  const gridEl = q(".hw-grid");
  const staticBox = q(".hw-static");

  // ---------- the map arrives on its own (lazy chunk); every stage builds its map layers when it does ----------
  let MAP = null;
  const mapStages = [];
  let mapRequested = false;
  function requestMap() {
    if (mapRequested) return;
    mapRequested = true;
    loadMap().then((data) => {
      if (destroyed) return;
      const R = data.landBig.match(/M[^M]*/g);
      MAP = { ...data, out: data.outIdx.map((i) => R[i]).join("") };
      // a mistake in building the layers is not swallowed: it surfaces as an unhandled rejection (console, QA)
      mapStages.forEach((fill) => fill());
    }, () => {
      // The chunk did not arrive (offline, or a page left open across a deploy): the 05 stages keep their blurred
      // preview. There is no retry: the browser keeps a failed import() of a module for the life of the page.
    });
  }

  // ---------- images: requested only when the section is near (2 viewports), one group after another in the order the
  // film needs them (the 01 photo, the other 경주 photos, the heap, the gallery), so a slow network gets the first one first.
  // Until a picture arrives its slot shows a tiny blurred preview of it (inline), never an empty white frame.
  let released = 0;
  const imgTag = (name) => `<img data-src="${imageUrl(name)}" alt="" decoding="async" draggable="false" style="background-image:url(${LQ[name.replace("photos/", "")]})">`;
  function hydrate(scope) {
    [...scope.querySelectorAll("img[data-src]")].filter((im) => imageGroupOf(im.dataset.src) < released).forEach((im) => {
      if ("fetchPriority" in im) im.fetchPriority = "low";
      // the heap drops its preview once the real picture is in (not the blank pixel of a failed load)
      if (im.dataset.src.includes("heap")) im.addEventListener("load", () => { if (im.src !== BLANK_PIXEL) im.style.backgroundImage = "none"; }, { once: true });
      // a picture that fails to load keeps its blurred preview (its background): a transparent pixel replaces the failed
      // src, since an <img> left failed or without a src still paints the broken-image icon over it
      im.addEventListener("error", () => { im.src = BLANK_PIXEL; }, { once: true });
      im.src = im.dataset.src;
      im.removeAttribute("data-src");
      if (im.decode) im.decode().catch(() => {}); // decode off the scroll path
    });
  }

  // ---------- a stage: builds the world and moves its objects to the pose of a (beat, step) ----------
  function makeStage(world) {
    const u = `hw${mountId}s${UID++}`;
    const drift = document.createElement("div");
    drift.className = "drift";
    world.appendChild(drift);
    const obj = (cls, inner) => {
      const el = document.createElement("div");
      el.className = "o " + cls;
      el.innerHTML = `<div class="in">${inner}</div>`;
      drift.appendChild(el);
      return el;
    };
    const mkCard = (name, extra, cls) => {
      const pc = document.createElement("div");
      pc.className = "o pc " + cls;
      pc.innerHTML = `<div class="pcy"><div class="pcr"><div class="face front">${imgTag("photos/" + name)}${extra}</div></div></div>`;
      drift.appendChild(pc);
      return { pc, py: pc.firstElementChild, pr: pc.querySelector(".pcr"), fr: pc.querySelector(".front"), chk: pc.querySelector(".bdg.chk") };
    };
    const heap = obj("heap", imgTag("heap"));
    const cloudB = obj("cloud cb", cloudSVG(CB, "clb" + u));
    const phone = obj("phone", `<div class="gal"></div><div class="glow"></div>`);
    const map = obj("map", "");
    const fillMap = () => {
      const mx = (v) => (v * 560 / MAP.w).toFixed(1), my = (v) => (v * 524 / MAP.h).toFixed(1);
      map.firstElementChild.innerHTML = mapLayers(MAP) + `<div class="ring" style="left:${mx(MAP.gjc[0])}px;top:${my(MAP.gjc[1])}px"></div><div class="mtag" style="left:${mx(MAP.tip[0])}px;top:${my(MAP.tip[1])}px">${tagHTML()}<i></i></div>`;
      map.querySelector(".m-gj").style.transformOrigin = `${mx(MAP.gjc[0])}px ${my(MAP.gjc[1])}px`;
      map.classList.add("has-map"); // its ground shadow shows only under a map
    };
    if (MAP) fillMap();
    else mapStages.push(fillMap);
    const cards = PHOTOS.map((n, i) => mkCard(n, (i === 0 ? P01_HTML : "") + `<span class="bdg chk">${CHK_SVG}</span>`, "card"));
    const glass = obj("glass", "");
    const copies = PHOTOS.slice(0, 3).map((n) => mkCard(n, "", "copy"));
    copies.forEach((c) => c.fr.classList.add("rim"));
    const cloudF = obj("cloud cf", cloudSVG(CF, "clf" + u));
    const ptag = obj("ptag", `<span class="pulse"></span>` + tagHTML());
    const lock = obj("lock", LOCK_SVG(u));
    const p01tag = cards[0].pr.querySelector(".p01 .tag");
    hydrate(drift);

    // ---- poses (virtual world 600 wide; origin at its centre) ----
    const SLOT = [[-215, -6, -18], [62, 74, 14], [208, -58, -9], [-58, -112, 21]]; // hidden in the heap (heap units)
    const IN = [[-46, -150, -7], [48, -122, 6], [-40, -26, 5], [44, 0, -4]]; //       inside the phone (phone units)
    let C;
    function configure(tall) {
      C = tall ? {
        tall, card1: 1.28, c1y: -40,
        heap: { y: 120, s: 1.12 }, recede: 90, tag: -226, gs: 0.56,
        gather: [[-124, -58, -4], [124, -50, 3], [-120, 112, 2], [128, 106, -4]],
        ph3: { y: -4, s: 1.2 }, ph4: { y: 132, s: 0.95 }, lockS: 1.3,
        cloud: { y: -288, s: 0.74 }, map: { y: -50, s: 1.1 },
      } : {
        tall, card1: 1.18, c1y: 40,
        heap: { y: 90, s: 0.95 }, recede: 70, tag: -168, gs: 0.54,
        gather: [[-118, -30, -4], [118, -24, 3], [-114, 128, 2], [122, 122, -4]],
        ph3: { y: -6, s: 0.98 }, ph4: { y: 100, s: 0.78 }, lockS: 1.25,
        cloud: { y: -236, s: 0.68 }, map: { y: 4, s: 1.06 },
      };
    }
    configure(false);
    const slot = (i) => { const [x, y, r] = SLOT[i], H = C.heap; return { x: x * H.s, y: H.y + y * H.s, s: 0.11 * H.s, r }; };
    const inPhone = (i, P) => { const [x, y, r] = IN[i]; return { x: x * P.s, y: P.y + y * P.s, s: 0.3 * P.s, r }; };
    const lockY = () => C.ph3.y + 150 * C.ph3.s;

    function cardPose(i, b, k) {
      if (i === 0 && b === 1) return k >= 1 ? { x: 0, y: C.c1y, s: C.card1, r: -2, tx: 5, ty: 7, o: 1 } : { x: 0, y: C.c1y, s: C.card1, r: 2, tx: 9, ty: -13, o: 1 };
      if (i === 0 && b === 0) return { x: 0, y: C.c1y + 80, s: C.card1 * 0.94, r: 2, tx: 9, ty: -13, o: 0 };
      if (b <= 1) return { ...slot(i), y: slot(i).y + 40, o: 0 };
      if (b === 2) return k === 0 ? { ...slot(i), o: 1 } : (() => { const [x, y, r] = C.gather[i]; return { x, y, s: C.gs, r, o: 1, rim: 1 }; })();
      if (b === 3) return { ...inPhone(i, C.ph3), o: 1, rim: 1 };
      if (b === 4) return { ...inPhone(i, C.ph4), o: 1, rim: i < 3 ? 1 : 0, chk: i < 3 };
      const p = inPhone(i, C.ph4);
      return { ...p, y: p.y + 50, o: 0, rim: i < 3 ? 1 : 0 };
    }
    const DOCK = [[-104, 3, -12], [0, -8, 0], [104, 3, 12]]; // tucked into the cloud (cloud units)
    function copyPose(i, b, k) {
      const [x, y, r] = DOCK[i];
      const dock = { x: x * C.cloud.s, y: C.cloud.y + y * C.cloud.s, s: 0.34 * C.cloud.s, r };
      if (b === 4 && k === 1) return { ...dock, o: 1 };
      if (b === 5) return { ...dock, o: 0 };
      return { ...inPhone(i, C.ph4), o: 0 }; // waiting, invisible, exactly on top of its original's 04 spot
    }
    const poseOf = {
      heap: (b, k) => b === 2 ? (k === 0 ? { y: C.heap.y, s: C.heap.s, o: 1 } : { y: C.heap.y + C.recede + 60, s: C.heap.s * 0.82, o: 0 })
        : b < 2 ? { y: C.heap.y + 60, s: C.heap.s * 0.94, o: 0 } : { y: C.heap.y + C.recede + 60, s: C.heap.s * 0.82, o: 0 },
      ptag: (b) => b === 2 ? { y: C.tag, s: 1, o: 1 } : b < 2 ? { y: C.tag - 40, s: 0.7, o: 0 } : { y: C.tag - 30, s: 0.8, o: 0 },
      phone: (b) => b === 3 ? { y: C.ph3.y, s: C.ph3.s, o: 1 } : b === 4 ? { y: C.ph4.y, s: C.ph4.s, o: 1 }
        : b < 3 ? { y: C.ph3.y + 40, s: C.ph3.s * 1.06, o: 0 } : { y: C.ph4.y + 60, s: C.ph4.s * 0.92, o: 0 },
      cloud: (b) => b === 4 ? { y: C.cloud.y, s: C.cloud.s, o: 1 } : b < 4 ? { y: C.cloud.y - 40, s: C.cloud.s * 0.85, o: 0 } : { y: C.cloud.y - 50, s: C.cloud.s * 0.9, o: 0 },
      lock: (b) => b === 3 ? { y: lockY(), s: C.lockS, o: 1 } : b < 3 ? { y: lockY() - 60, s: C.lockS * 0.7, o: 0 } : { y: lockY() + 40, s: C.lockS * 0.8, o: 0 },
      map: (b) => b === 5 ? { y: C.map.y, s: C.map.s, o: 1 } : { y: C.map.y + 60, s: C.map.s * 0.96, o: 0 },
    };
    const simple = [["heap", heap], ["ptag", ptag], ["phone", phone], ["glass", glass], ["cloud", cloudB], ["cloud", cloudF], ["lock", lock], ["map", map]];
    const DIN = { heap: 0, ptag: 0, phone: 40, glass: 40, cloud: 40, map: 20 };

    const xf = (p) => `translate3d(${p.x || 0}px,${p.y || 0}px,0) scale(${p.s ?? 1})`;
    const same = (a, b) => a && a.x === b.x && a.y === b.y && a.s === b.s && a.o === b.o && a.r === b.r && a.tx === b.tx && a.ty === b.ty && a.rim === b.rim && a.chk === b.chk;
    const seen = (name, b) => [b - 1, b, b + 1].some((x) => x >= 0 && x <= 5 && (name === "glass" ? poseOf.phone(x, 1) : poseOf[name](x, 1)).o > 0);
    let cur = null, p01Ready = Infinity;

    // where the 01 tag sits once 01 has played, in world units (computed from the card's pose: no layout read on the
    // beat change). The 01 tag box: centred 124px + half its height above the card centre, scaled 1.12 with the card.
    let tagBox = null;
    function measureTags() {
      const t1 = p01tag, t2 = ptag.querySelector(".tag");
      if (t1.offsetWidth && t2.offsetWidth) tagBox = { w1: t1.offsetWidth, h1: t1.offsetHeight, w2: t2.offsetWidth };
    }
    function tagFrom01() {
      if (!tagBox) return null;
      const p = cardPose(0, 1, 1), th = p.r * Math.PI / 180, d = (124 + tagBox.h1 / 2) * p.s;
      return { x: d * Math.sin(th), y: p.y - d * Math.cos(th), s: tagBox.w1 * 1.12 * p.s / tagBox.w2, o: 1 };
    }

    function set(b, k, instant, fast) {
      if (cur && cur.b === b && cur.k === k && !instant) return;
      const prev = cur || { b, k };
      const I = instant || !cur;
      const F = !I && fast;
      const Tm = F ? TM.fast : TM.slow;
      drift.classList.toggle("fast", !!F);
      const fwd = b > prev.b || (b === prev.b && k > prev.k);
      const jump = !I && b === 2 && k === 1 && prev.b === 2 && prev.k === 0;
      const handoff = !I && fwd && b === 2 && prev.b === 1 && prev.k === 1 && performance.now() >= p01Ready ? tagFrom01() : null;

      // simple objects
      for (const [name, el] of simple) {
        el.classList.toggle("live", seen(name, b));
        const p = name === "glass" ? poseOf.phone(b, k) : poseOf[name](b, k);
        const old = el._p;
        if (same(old, p) && !(name === "ptag" && handoff)) continue;
        let t;
        if (name === "ptag" && handoff) {
          // one 경주 tag: it leaves the 01 photo and flies up to its 02 place
          el.style.transition = "none";
          el.style.transform = xf(handoff);
          el.style.opacity = 1;
          el.getBoundingClientRect();
          t = { d: 0, dur: 700, ease: SPRING, od: 0, odur: 0 };
        } else if (I || !old || (old.o === 0 && p.o === 0)) t = null;
        else if (p.o > old.o) {
          const d = !fwd ? 0 : name === "lock" ? (prev.b === 2 ? Tm.lockIn : 60) : (DIN[name] || 0);
          t = { d, dur: 760, ease: SPRING, od: d, odur: name === "heap" ? Tm.heapIn : name === "map" && !F ? 420 : Tm.objO };
        } else if (name === "heap" && jump) t = { d: Tm.heapD, dur: Tm.heapDur, ease: EASE_OUT, od: Tm.heapD, odur: Tm.heapO };
        else if (p.o < old.o) t = { d: 0, dur: 340, ease: EASE_IN, od: 0, odur: 240 };
        else t = { d: 0, dur: fwd && b === 4 ? 560 : 680, ease: SETTLE, od: 0, odur: 320 };
        el.style.transition = t ? `transform ${t.dur}ms ${t.ease} ${t.d}ms, opacity ${t.odur}ms ease ${t.od}ms` : "none";
        el.style.transform = xf(p);
        el.style.opacity = p.o;
        el._p = p;
      }
      p01tag.classList.toggle("gone", !!handoff);
      if (!(b === 2 && prev.b === 1) && !handoff) p01tag.classList.remove("gone");
      phone.style.setProperty("--gld", I ? "0ms" : fwd && prev.b === 2 ? Tm.gld + "ms" : "300ms");
      phone.classList.toggle("locked", b === 3);
      phone.classList.toggle("keep", b === 4);
      lock.style.setProperty("--lkd", I ? "0ms" : fwd && prev.b === 2 ? Tm.lkd + "ms" : "260ms");
      lock.classList.toggle("closed", b === 3);
      map.classList.toggle("play", b === 5);
      // the call (tag pulse) plays on the way forward from the heap to the gathered photos
      if (jump) drift.classList.add("jumping");
      else if (b !== 2 || k === 0) drift.classList.remove("jumping");

      // cards
      const moveCard = (c, p, t) => {
        if (!t) { c.pc.style.transition = c.py.style.transition = c.pr.style.transition = "none"; }
        else {
          c.pc.style.transition = `transform ${t.dX}ms ${t.eX} ${t.d}ms, opacity ${t.odur}ms ease ${t.od}ms`;
          c.py.style.transition = `transform ${t.dY}ms ${t.eY} ${t.d}ms`;
          c.pr.style.transition = `transform ${t.dR}ms ${t.eR} ${t.d}ms`;
        }
        c.pc.style.transform = `translate3d(${p.x}px,0,0)`;
        c.pc.style.opacity = p.o;
        c.py.style.transform = `translate3d(0,${p.y}px,0)`;
        c.pr.style.transform = `scale(${p.s}) rotateX(${p.tx || 0}deg) rotateY(${p.ty || 0}deg) rotate(${p.r}deg)`;
        c._p = p;
      };
      const T = (o) => ({ d: 0, dX: 700, eX: EASE_OUT, dY: 700, eY: EASE_OUT, dR: 760, eR: SPRING, od: 0, odur: 300, ...o });
      cards.forEach((c, i) => {
        c.pc.classList.toggle("live", b >= 1 && b <= 5);
        const p = cardPose(i, b, k), o = c._p;
        let t = null, rimd = 0;
        if (I || !o || (o.o === 0 && p.o === 0)) t = null;
        else if (jump) { //                                              the jump: y overshoots upward, x eases → an arc
          t = T({ d: Tm.jump[i], dX: Tm.jumpDur, eX: "cubic-bezier(.45,.05,.3,1)", dY: Tm.jumpDur, eY: "cubic-bezier(.25,1.6,.45,1)", dR: Tm.jumpDur });
          rimd = Tm.jump[i] + Tm.rimAfter;
        } else if (b === 1 && prev.b === 1 && i === 0) t = T({ dR: 900, eR: "cubic-bezier(.3,1.25,.4,1)" }); // the card turns to present its tag
        else if (fwd && b === 2 && prev.b <= 1) t = i === 0 ? T({ dX: Tm.drop, eX: DROP, dY: Tm.drop, eY: DROP, dR: Tm.drop, eR: DROP }) // the photo drops into the heap
          : T({ d: Tm.slotD, od: Tm.slotD, odur: 260 });
        else if (fwd && b === 3 && prev.b === 2) t = T({ d: Tm.settleD + i * Tm.settleStep, dX: Tm.settleDur, eX: SETTLE, dY: Tm.settleDur, eY: SETTLE, dR: Tm.settleDur });
        else if (fwd && b === 4 && prev.b === 3) t = T({ dX: 560, eX: SETTLE, dY: 560, eY: SETTLE, dR: 560 });
        else if (p.o > o.o) t = T({ d: fwd ? 150 : 0, od: fwd ? 150 : 0, odur: 380, dX: 800, eX: SPRING, dY: 800, eY: SPRING });
        else if (p.o < o.o) t = T({ dX: 340, eX: EASE_IN, dY: 340, eY: EASE_IN, dR: 340, eR: EASE_IN, odur: 240 });
        else t = T({ dX: 680, eX: SETTLE, dY: 680, eY: SETTLE });
        if (!same(o, p) || !t) moveCard(c, p, t);
        c.fr.style.setProperty("--rimd", (I ? 0 : rimd) + "ms");
        c.fr.classList.toggle("rim", !!p.rim);
        c.chk.style.setProperty("--bd", (!I && fwd && b === 4 && prev.b === 3 ? Tm.chk[i] || 0 : 0) + "ms");
        c.chk.classList.toggle("show", !!p.chk);
      });
      const pin01 = b === 1 && k === 1;
      if (pin01 && !cards[0].pr.classList.contains("pinned")) p01Ready = I ? 0 : performance.now() + Tm.p01;
      cards[0].pr.classList.toggle("pinned", pin01);

      // copies of the three picked photos: they appear on top of their originals, then rise into the cloud.
      // The originals never move out; nothing in the phone disappears.
      copies.forEach((c, i) => {
        c.pc.classList.toggle("live", b >= 3);
        const p = copyPose(i, b, k), o = c._p;
        let t = null;
        if (I || !o || (o.o === 0 && p.o === 0)) t = null;
        else if (fwd && b === 4 && k === 1) { //                         04's inner step: the copies go up
          const d = i * Tm.copyStep;
          t = T({ d, od: Math.max(0, d - 40), odur: 100, dX: Tm.copyDur, eX: "cubic-bezier(.45,.05,.3,1)", dY: Tm.copyDur, eY: "cubic-bezier(.3,1.25,.45,1)", dR: Tm.copyDur });
        } else if (p.o < o.o) t = T({ d: 260, dX: 0, dY: 0, dR: 0, odur: 220 }); // fade, then slip back home unseen
        else t = T({ odur: 320 });
        if (!same(o, p) || !t) moveCard(c, p, t);
      });
      cur = { b, k };
      world.dataset.howPose = `${b}.${k}`; // QA: the pose the stage shows
    }
    function relayout(tall) {
      configure(tall);
      measureTags();
      if (cur) {
        const c = cur; cur = null;
        simple.forEach(([, e]) => { e._p = null; });
        cards.concat(copies).forEach((c2) => { c2._p = null; });
        set(c.b, c.k, true);
      }
    }
    return { set, drift, relayout, measureTags, get state() { return cur; } };
  }

  // ---------- page wiring ----------
  const created = [];
  const beatHTML = ([eb, l1, l2, sub], tag) =>
    `<p class="eb rv" style="--d:100ms"${tag === "h3" ? ' aria-hidden="true"' : ""}>${eb}</p><${tag} class="hd"><span class="rv" style="--d:130ms">${l1}</span> <span class="rv" style="--d:170ms">${l2}</span></${tag}>` +
    (sub ? `<p class="sub rv" style="--d:250ms">${sub}</p>` : "");
  if (LQ.gallery) root.style.setProperty("--lqg", `url(${LQ.gallery})`);
  // 05's map preview (each theme its own) until the lazy map arrives
  if (LQ.map) root.style.setProperty("--lqm", `url(${LQ.map})`);
  if (LQ["map-dark"]) root.style.setProperty("--lqmd", `url(${LQ["map-dark"]})`);

  // 100svh in px (100vh where svh is unknown). On touch devices it is latched per width: an address bar that hides or a
  // webview that resizes itself by a few percent changes nothing; a rotation or a big change re-measures.
  const probe = document.createElement("div");
  probe.style.cssText = `position:absolute;visibility:hidden;pointer-events:none;width:0;top:0;left:0;height:${CSS.supports && CSS.supports("height", "100svh") ? "100svh" : "100vh"}`;
  probe.dataset.howProbe = "";
  root.appendChild(probe);
  created.push(probe);
  const mqTouch = matchMedia("(pointer: coarse), (hover: none)");
  const mqReduce = matchMedia("(prefers-reduced-motion: reduce)");
  const mqPortraitPhone = matchMedia("(max-width: 860px) and (orientation: portrait)");
  let VH = innerHeight, lastW = -1, smallW = -1;
  function measureVH() {
    const ph = probe.getBoundingClientRect().height || innerHeight;
    VH = latchViewportHeight({ touch: mqTouch.matches, width: innerWidth, lastWidth: lastW, vh: VH, measured: ph });
    lastW = innerWidth;
    return VH;
  }
  const observe = (Observer, callback, target, init) => {
    if (!window[Observer]) return null;
    const observer = new window[Observer](callback, init);
    observer.observe(target);
    cleanups.push(() => observer.disconnect());
    return observer;
  };

  // ---------- what the page's analytics and QA read: the mode, the beat, and whether a scene is at rest ----------
  let reported = null;
  let staticScene = 0;
  function report(inView) {
    const pinned = !isStatic && onScreen && sNow >= 0 && sNow <= PIN_VH;
    const next = isStatic
      ? { mode: "static", inView, scene: staticScene, atRest: staticScene > 0 }
      : { mode: "scroll", inView, scene: curB < 0 ? 0 : sceneOf(curB, curK), atRest: sceneAtRest({ b: curB, k: curK, pinned }) };
    if (reported && reported.mode === next.mode && reported.inView === next.inView && reported.scene === next.scene && reported.atRest === next.atRest) return;
    reported = next;
    root.dataset.howRest = next.atRest ? String(next.scene) : "0"; // QA: the scene at rest now (0 = none)
    if (onState) onState({ ...next });
  }
  let sectionInView = false;

  // static version
  let staticBuilt = false, pinnedBuilt = false, isStatic = false, built = false;
  const fits = [];
  function buildStatic() {
    if (staticBuilt) return;
    staticBuilt = true;
    COPY.forEach((c, i) => {
      const item = document.createElement("div");
      item.className = "st-item";
      item.dataset.howScene = String(i + 1);
      item.innerHTML = `<div class="hw-beat on">${beatHTML(c, "h3")}</div><div class="st-fig" aria-hidden="true"><div class="world"></div></div>`;
      staticBox.appendChild(item);
      created.push(item);
      const fig = item.querySelector(".st-fig");
      const st = makeStage(item.querySelector(".world"));
      st.set(i + 1, i === 2 || i === 4 ? 0 : 1, true);
      // refit whenever the box is shown or resized; a hidden box (pinned mode) keeps its last good scale
      fits.push(() => { const w = fig.clientWidth, h = fig.clientHeight; if (w && h) fig.style.setProperty("--k", (STATIC_FIT * Math.min(w / 600, h / 700)).toFixed(4)); });
    });
    observe("ResizeObserver", () => fits.forEach((f) => f()), staticBox);
  }

  // pinned version
  let beats = [], stage = null, world = null, wasTall = null, copyOff = 0;
  let curB = -1, curK = -1, shownCopy = -2, subTimer = 0, ticking = 0, chainFast = false;
  let lastY = scrollY, lastT = 0, vel = 0, lastFo = 1, pend = null, pendRaf = 0, onScreen = false, sNow = -Infinity;
  function buildPinned() {
    if (pinnedBuilt) return;
    pinnedBuilt = true;
    // the film's first screen is the section title, in the same slot and size as the beats' headlines
    const t0 = document.createElement("div");
    t0.className = "hw-beat t0";
    t0.innerHTML = beatHTML([TITLE[0], TITLE[1], TITLE[2], ""], "p");
    copyEl.appendChild(t0);
    beats = [t0, ...COPY.map((c) => {
      const el = document.createElement("div");
      el.className = "hw-beat";
      el.innerHTML = beatHTML(c, "p");
      copyEl.appendChild(el);
      return el;
    })];
    created.push(...beats);
    world = document.createElement("div");
    world.className = "world";
    stageEl.appendChild(world);
    created.push(world);
    stage = makeStage(world);
    // the stage takes what the copy leaves (fonts swapping in, larger text settings): refit when it changes size
    observe("ResizeObserver", () => refresh(), stageEl);
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(() => { if (destroyed) return; stage.measureTags(); refresh(); });
  }
  function layout() {
    if (isStatic || !pinnedBuilt) return undefined;
    track.style.height = ((PIN_VH + 1) * VH).toFixed(1) + "px";
    frameEl.style.height = VH.toFixed(1) + "px";
    const w = stageEl.clientWidth, h = stageEl.clientHeight;
    // text so large that the picture would shrink to a speck: the caller switches to the static version
    if (stageTooSmall({ portraitPhone: mqPortraitPhone.matches, stageHeight: h, vh: VH })) return "small";
    const { tall, worldHeight, scale } = fitWorld(w, h);
    world.style.height = worldHeight + "px";
    world.style.transform = `translate(-50%,-50%) scale(${scale.toFixed(4)})`;
    if (tall !== wasTall) { stage.relayout(tall); wasTall = tall; }
    copyOff = gridEl.offsetTop + copyEl.offsetTop + beats[0].offsetTop;
    return undefined;
  }
  // the copy for a state: the title (0) until 01's copy takes over; -1 = nothing yet (title still below the fold)
  function showCopy(want) {
    if (want === shownCopy) return;
    beats.forEach((el, i) => {
      el.classList.toggle("on", i === want);
      el.classList.toggle("past", want >= 0 && i < want);
    });
    shownCopy = want;
  }
  // copy now; the stage's burst of writes in the next frame (keeps each beat change under one frame's budget)
  function apply(b, k, fast) {
    const first = curB === -1;
    curB = b; curK = k;
    track.dataset.howBeat = String(b);
    track.dataset.howStep = String(k);
    pend = { b, k, fast, first: first || (pend && pend.first) };
    if (!pendRaf) pendRaf = requestAnimationFrame(() => { pendRaf = 0; const p = pend; pend = null; if (p && !isStatic) stage.set(p.b, p.k, p.first, p.fast); });
    report(sectionInView);
  }
  function frameStatic() {
    const r = root.getBoundingClientRect();
    sectionInView = halfInView(r.top, r.bottom, innerHeight);
    // the item at rest: the one at least half in view nearest the middle of the screen
    let best = 0, bd = Infinity;
    if (r.bottom > 0 && r.top < innerHeight) {
      [...staticBox.children].forEach((el, i) => {
        const q2 = el.getBoundingClientRect();
        if (!halfInView(q2.top, q2.bottom, innerHeight)) return;
        const d = Math.abs((q2.top + q2.bottom) / 2 - innerHeight / 2);
        if (d < bd) { bd = d; best = i + 1; }
      });
    }
    staticScene = best;
    report(sectionInView);
  }
  function frame() {
    ticking = 0;
    if (destroyed) return;
    if (isStatic) { frameStatic(); return; }
    if (!pinnedBuilt) return;
    const tr = track.getBoundingClientRect();
    const t = performance.now(), y = scrollY, dt = t - lastT;
    const vNow = dt > 0 && dt < 120 ? (y - lastY) / dt : 0; // px per ms, signed
    vel = dt < 120 ? vel * 0.5 + vNow * 0.5 : vNow;
    lastT = t; lastY = y;
    onScreen = tr.top < innerHeight && tr.bottom > 0;
    sectionInView = halfInView(tr.top, tr.bottom, innerHeight);
    const s = -tr.top / VH;
    sNow = s;
    let { b, k } = stateAt(s);
    if (b !== curB || (!INNER[b] && k !== curK)) {
      const v = Math.abs(vNow) > Math.abs(vel) ? vNow : vel;
      chainFast = chainIsFast({ s, v, vh: VH, b, k, first: curB === -1 });
    }
    // 02 and 04 have an inner step on a timer (the 경주 photos jump out of the heap; the copies go up). Whether it
    // plays short or full is decided when it fires, so a beat entered at the start of a firm fling still lands its point.
    if (INNER[b]) {
      if (curB === b) k = curK; //                       the inner step is on its timer
      else {
        k = innerStepOnEnter({ b, prevB: curB }); //     coming back, or first paint: the rest state
        if (k === 0) arm(b);
      }
    } else clearTimeout(subTimer);
    if (b !== curB || k !== curK) apply(b, k, chainFast);
    // one headline at a time: the title until 01's copy, which comes in with its tag
    showCopy(copyFor({ b, k, titleOnScreen: tr.top + copyOff < innerHeight * 0.85 }));
    // as the frame unpins after 05, the copy fades before it can slide under the header (the map leaves alone)
    const fo = copyFade(s);
    if (fo !== lastFo) { copyEl.style.opacity = fo < 1 ? fo.toFixed(3) : ""; lastFo = fo; }
    // gentle float with scroll inside the active beat (pure function of s)
    const drift = driftAt(s, b);
    if (drift !== null) stage.drift.style.transform = `translate3d(0,${drift.toFixed(2)}px,0)`;
    report(sectionInView);
  }
  const req = () => { if (!ticking && !destroyed) ticking = requestAnimationFrame(frame); };
  function passing(b) { // will the scroll that is going on now carry the reader past beat b?
    const tr = track.getBoundingClientRect(), s = -tr.top / VH;
    const v = performance.now() - lastT > 100 ? 0 : (Math.abs(vel) > 0 ? vel : 0);
    return willPass({ s, v, vh: VH, b });
  }
  function arm(b) {
    clearTimeout(subTimer);
    const key = INNER[b], early = TM.fast[key], late = TM.slow[key];
    subTimer = setTimeout(() => {
      if (destroyed || curB !== b || curK !== 0) return;
      if (chainFast || passing(b)) { apply(b, 1, true); return; }
      subTimer = setTimeout(() => { if (!destroyed && curB === b && curK === 0) apply(b, 1, false); }, late - early);
    }, early);
  }

  // where the reader is (beat 0-5), so a switch between the film and the static version (a rotation) keeps their place.
  // A change that keeps the film on both sides (a tablet rotated, a foldable unfolded, a desktop window made shorter)
  // keeps the exact scroll position s instead: see refresh().
  function place() {
    if (!built) return null;
    if (!isStatic) return onScreen && curB >= 0 ? placeOf(curB, curK) : null;
    const r = root.getBoundingClientRect();
    if (r.bottom < 0 || r.top > innerHeight) return null;
    const els = [head, ...staticBox.children], mid = innerHeight / 2;
    let best = 0, bd = Infinity;
    els.forEach((el, i) => { const q2 = el.getBoundingClientRect(), d = Math.abs((q2.top + q2.bottom) / 2 - mid); if (d < bd) { bd = d; best = i; } });
    return best;
  }
  // The only programmatic scroll in this section: when the viewport changes under the reader (a mode switch, or a
  // rotation that keeps the film), it puts them back where they were, instantly. `where` is a place (beat 0-5) or,
  // when the film stays, { s }: the same scroll position in the film's own units, so the same beat and step.
  function restore(where) {
    if (where == null) return;
    const hdr = parseFloat(getComputedStyle(root).getPropertyValue("--hw-hdr")) || 84;
    if (typeof where === "object") window.scrollTo({ top: scrollY + track.getBoundingClientRect().top + where.s * VH, behavior: "instant" });
    else if (where === 0) root.scrollIntoView({ block: "start", behavior: "instant" }); // the section's own scroll-margin
    else if (isStatic) window.scrollTo({ top: Math.max(0, scrollY + staticBox.children[where - 1].getBoundingClientRect().top - hdr - 12), behavior: "instant" });
    else window.scrollTo({ top: scrollY + track.getBoundingClientRect().top + restPosition(where) * VH, behavior: "instant" });
    // the jump is not the reader's scroll: the next frame starts the velocity estimate afresh
    lastY = scrollY;
    lastT = 0;
  }
  function setMode(stat) {
    if (built && stat === isStatic) return;
    const where = place();
    isStatic = stat;
    root.classList.toggle("is-static", isStatic);
    root.dataset.howMode = isStatic ? "static" : "scroll";
    if (isStatic) { buildStatic(); fits.forEach((f) => f()); }
    else { buildPinned(); wasTall = null; curB = -1; curK = -1; shownCopy = -2; layout(); }
    if (built) restore(where);
    built = true;
    frame();
  }
  function refresh() {
    if (destroyed) return;
    // The track is (PIN_VH + 1) x VH tall, so when VH changes and the film stays (a tablet rotated, a foldable
    // unfolded, a desktop window made shorter), the same scrollY would land in another beat. A reader with the
    // film on screen keeps their s, from the frame coming up to it scrolling away after 05. (On phones the address
    // bar never changes VH: it is latched, so nothing moves.)
    const prevVH = VH;
    const keep = built && !isStatic && onScreen ? { s: sNow } : null;
    measureVH();
    root.style.setProperty("--hw-vh", VH.toFixed(1) + "px");
    root.classList.toggle("hw-compact", VH < COMPACT_VH);
    if (smallW !== -1 && smallW !== innerWidth) smallW = -1;
    // a viewport under 500px tall (a landscape phone) gets the static version; every portrait phone gets the film
    setMode(isStaticMode({ reducedMotion: mqReduce.matches, vh: VH, smallWidthLatched: smallW !== -1 }));
    if (!isStatic && layout() === "small") { smallW = innerWidth; setMode(true); }
    // (a switch to the static version restored the place in setMode)
    if (keep && !isStatic && Math.abs(VH - prevVH) > 1) restore(keep);
    req();
  }
  // Re-reads where the reader is now and reports it (analytics confirm a scene on a fresh frame before counting it).
  function sample() {
    if (destroyed) return;
    if (ticking) cancelAnimationFrame(ticking);
    frame();
  }

  // load the pictures (and the map) only when the section is near, group by group
  function release() {
    if (destroyed) return;
    const group = IMAGE_GROUPS[released++];
    if (!group) return;
    let left = group.length;
    const next = () => { if (--left === 0) release(); };
    group.forEach((n) => { const im = new Image(); if ("fetchPriority" in im) im.fetchPriority = "low"; im.onload = im.onerror = next; im.src = imageUrl(n); });
    if (group[0] === "gallery") root.classList.add("ld");
    hydrate(root);
  }
  const startLoad = () => { requestMap(); if (!released) release(); };
  const io = observe("IntersectionObserver", (es) => { if (es.some((e) => e.isIntersecting)) { io.disconnect(); startLoad(); } }, root, { rootMargin: "200% 0px" });
  if (!io) startLoad();

  if (mqReduce.addEventListener) {
    mqReduce.addEventListener("change", refresh);
    cleanups.push(() => mqReduce.removeEventListener("change", refresh));
  }
  window.addEventListener("scroll", req, { passive: true });
  window.addEventListener("resize", refresh);
  cleanups.push(() => {
    window.removeEventListener("scroll", req);
    window.removeEventListener("resize", refresh);
  });
  refresh();

  function destroy() {
    if (destroyed) return;
    destroyed = true;
    cleanups.forEach((cleanup) => cleanup());
    if (ticking) cancelAnimationFrame(ticking);
    if (pendRaf) cancelAnimationFrame(pendRaf);
    clearTimeout(subTimer);
    created.forEach((node) => node.remove());
    root.classList.remove("is-static", "hw-compact", "ld");
    root.style.removeProperty("--hw-vh");
    root.style.removeProperty("--lqg");
    root.style.removeProperty("--lqm");
    root.style.removeProperty("--lqmd");
    delete root.dataset.howMode;
    delete root.dataset.howRest;
    delete track.dataset.howBeat;
    delete track.dataset.howStep;
    track.style.height = "";
    frameEl.style.height = "";
    copyEl.style.opacity = "";
  }
  destroy.sample = sample;
  return destroy;
}
