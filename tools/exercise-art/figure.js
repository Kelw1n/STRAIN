// Генератор иллюстраций упражнений: серый манекен, рабочие мышцы акцентом.
// Позы задаются абсолютными углами сегментов (0° — вправо, 90° — вверх),
// поэтому длины конечностей одинаковы во всех картинках.

const C = {
  bgTop: '#ECECEC', bgBottom: '#D3D3D3',
  near: '#8C8C8C', far: '#6E6E6E', torso: '#7F7F7F', head: '#8C8C8C',
  accent: '#FF6029', accentDeep: '#E2471A',
  prop: '#2E2E2E', propLight: '#5A5A5A', bench: '#A9A9A9', floor: '#BDBDBD',
};

const L = { torso: 27, neck: 4.5, head: 6.4, ua: 13.5, fa: 12.5, th: 18, sh: 17, ft: 6 };
const W = { torso: 14, ua: 6.6, fa: 5.6, th: 9.6, sh: 7, ft: 4.6 };

const rad = d => d * Math.PI / 180;
const step = (p, ang, len) => ({ x: p.x + Math.cos(rad(ang)) * len, y: p.y - Math.sin(rad(ang)) * len });
const lerp = (a, b, t) => ({ x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t });
const angleOf = (a, b) => Math.atan2(-(b.y - a.y), b.x - a.x) * 180 / Math.PI;

function build(s) {
  const hip = { x: s.hip[0], y: s.hip[1] };
  const shoulder = step(hip, s.torso, L.torso);
  const headDir = s.headDir ?? s.torso;
  const neck = step(shoulder, headDir, L.neck);
  const head = step(neck, headDir, L.head);
  const arm = a => { const e = step(shoulder, a[0], L.ua); return { e, h: step(e, a[1], L.fa) }; };
  const leg = l => { const k = step(hip, l[0], L.th); const a = step(k, l[1], L.sh); return { k, a, t: step(a, l[2], L.ft) }; };
  return { hip, shoulder, head, face: s.face ?? 1,
    armN: arm(s.arms[0]), armF: arm(s.arms[1] ?? s.arms[0]),
    legN: leg(s.legs[0]), legF: leg(s.legs[1] ?? s.legs[0]) };
}

const seg = (a, b, w, c) => `<line x1="${a.x.toFixed(2)}" y1="${a.y.toFixed(2)}" x2="${b.x.toFixed(2)}" y2="${b.y.toFixed(2)}" stroke="${c}" stroke-width="${w}" stroke-linecap="round"/>`;
const dot = (p, r, c) => `<circle cx="${p.x.toFixed(2)}" cy="${p.y.toFixed(2)}" r="${r}" fill="${c}"/>`;

// Сужающийся сегмент: две окружности радиусов r1, r2 и касательные между ними.
function taperPath(a, b, r1, r2) {
  const dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy) || 0.001;
  const ux = dx / d, uy = dy / d, nx = -uy, ny = ux;
  const k = (r1 - r2) / d, c = Math.sqrt(Math.max(0, 1 - k * k));
  const p = (o, r, s) => ({ x: o.x + r * (k * ux + s * c * nx), y: o.y + r * (k * uy + s * c * ny) });
  const a1 = p(a, r1, 1), a2 = p(a, r1, -1), b1 = p(b, r2, 1), b2 = p(b, r2, -1);
  const f = v => v.x.toFixed(2) + ' ' + v.y.toFixed(2);
  return `M ${f(a1)} L ${f(b1)} A ${r2} ${r2} 0 0 0 ${f(b2)} L ${f(a2)} A ${r1} ${r1} 0 1 0 ${f(a1)} Z`;
}

let gid = 0;
// Цилиндрическая светотень: градиент поперёк сегмента.
function shaded(a, b, r1, r2, tone, defs) {
  const dx = b.x - a.x, dy = b.y - a.y, d = Math.hypot(dx, dy) || 0.001;
  const nx = -dy / d, ny = dx / d, r = Math.max(r1, r2);
  const m = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
  // свет сверху-слева: светлая сторона та, у которой нормаль смотрит вверх
  const s = ny < 0 ? 1 : -1;
  const id = 'g' + (gid++);
  defs.push(`<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${(m.x + nx * r * s).toFixed(2)}" y1="${(m.y + ny * r * s).toFixed(2)}" x2="${(m.x - nx * r * s).toFixed(2)}" y2="${(m.y - ny * r * s).toFixed(2)}">
    <stop offset="0" stop-color="${tone[0]}"/><stop offset="0.45" stop-color="${tone[1]}"/><stop offset="1" stop-color="${tone[2]}"/></linearGradient>`);
  return `<path d="${taperPath(a, b, r1, r2)}" fill="url(#${id})"/>`;
}

const TONE = { near: ['#B4B4B4', '#959595', '#6E6E6E'], far: ['#8E8E8E', '#777777', '#5A5A5A'] };

// Светящееся пятно мышцы, как у REBRN: яркое ядро и мягкий ореол.
function glowSeg(a, b, w, defs) {
  return `<g filter="url(#glow)">${seg(a, b, w + 3, 'rgba(255,96,41,0.55)')}</g>` + seg(a, b, w, C.accent);
}
function glowDot(p, r) {
  return `<g filter="url(#glow)">${dot(p, r + 1.8, 'rgba(255,96,41,0.55)')}</g>` + dot(p, r, C.accent);
}

// Полоса вдоль туловища со смещением к груди (+1) или к спине (−1).
function torsoBand(f, from, to, side, width) {
  const a = angleOf(f.hip, f.shoulder);
  const off = side * f.face;
  const n = { x: Math.cos(rad(a - 90 * off)) * 3.4, y: -Math.sin(rad(a - 90 * off)) * 3.4 };
  const p1 = lerp(f.shoulder, f.hip, from), p2 = lerp(f.shoulder, f.hip, to);
  return glowSeg({ x: p1.x + n.x, y: p1.y + n.y }, { x: p2.x + n.x, y: p2.y + n.y }, width);
}

// Мышца на конечности: короче сегмента и чуть смещена к середине.
function muscleOn(a, b, w, defs) {
  return glowSeg(lerp(a, b, 0.2), lerp(a, b, 0.72), w);
}

function drawFigure(f, hl) {
  const H = name => hl.includes(name);
  const defs = [];
  let out = '';
  const leg = (g, near) => {
    const t = near ? TONE.near : TONE.far;
    let s = shaded(f.hip, g.k, 5.4, 3.6, t, defs) + shaded(g.k, g.a, 3.6, 2.4, t, defs) + shaded(g.a, g.t, 2.3, 1.7, t, defs);
    if (H('thighs')) s += muscleOn(f.hip, g.k, 5.2, defs);
    if (H('calves')) s += muscleOn(g.k, g.a, 3.6, defs);
    return s;
  };
  const arm = (g, near) => {
    const t = near ? TONE.near : TONE.far;
    let s = shaded(f.shoulder, g.e, 3.7, 2.7, t, defs) + shaded(g.e, g.h, 2.7, 2.0, t, defs);
    if (H('arms')) s += muscleOn(f.shoulder, g.e, 3.4, defs);
    if (H('forearms')) s += muscleOn(g.e, g.h, 2.6, defs);
    return s;
  };
  out += arm(f.armF, false) + leg(f.legF, false);
  // туловище шире в груди, уже в талии
  out += shaded(f.shoulder, f.hip, 7.6, 6.2, TONE.near, defs);
  out += `<circle cx="${f.hip.x.toFixed(2)}" cy="${f.hip.y.toFixed(2)}" r="6.6" fill="#8A8A8A"/>`;
  if (H('glutes')) out += glowDot(f.hip, 5.2);
  if (H('chest')) out += torsoBand(f, 0.12, 0.45, 1, 6.5);
  if (H('abs')) out += torsoBand(f, 0.55, 0.85, 1, 5.5);
  if (H('back')) out += torsoBand(f, 0.1, 0.6, -1, 6.5);
  if (H('lowback')) out += torsoBand(f, 0.62, 0.9, -1, 5.5);
  if (H('traps')) out += glowSeg(f.shoulder, lerp(f.shoulder, f.head, 0.5), 5);
  out += `<circle cx="${f.head.x.toFixed(2)}" cy="${f.head.y.toFixed(2)}" r="5.9" fill="url(#headg)"/>`;
  out += leg(f.legN, true);
  out += `<circle cx="${f.shoulder.x.toFixed(2)}" cy="${f.shoulder.y.toFixed(2)}" r="4.6" fill="#9A9A9A"/>`;
  if (H('delts')) out += glowDot(f.shoulder, 4);
  out += arm(f.armN, true);
  return `<defs>${defs.join('')}</defs>` + out;
}

// --- Снаряды -------------------------------------------------------------
const P = {
  floor: (y = 89) => `<line x1="6" y1="${y}" x2="94" y2="${y}" stroke="${C.floor}" stroke-width="1.4" stroke-linecap="round"/>`,
  plate: (p, r = 10.5) => `<circle cx="${p.x}" cy="${p.y}" r="${r}" fill="url(#plate)"/>` + `<circle cx="${p.x}" cy="${p.y}" r="${r * 0.78}" fill="none" stroke="#4a4a4a" stroke-width="0.8"/>` + dot(p, r * 0.22, '#9a9a9a') + dot(p, 1.2, C.prop),
  shadow: (cx, w = 34) => `<ellipse cx="${cx}" cy="88.5" rx="${w / 2}" ry="2.6" fill="rgba(0,0,0,0.18)" filter="url(#soft)"/>`,
  dumbbell: p => dot(p, 4.4, C.prop) + dot(p, 2.2, C.propLight),
  kettlebell: p => `<circle cx="${p.x}" cy="${p.y + 4}" r="5.2" fill="${C.prop}"/><path d="M ${p.x - 3} ${p.y + 0.5} Q ${p.x} ${p.y - 5} ${p.x + 3} ${p.y + 0.5}" stroke="${C.prop}" stroke-width="1.8" fill="none"/>`,
  bench: (x1, x2, y, legs = true) => `<rect x="${x1}" y="${y}" width="${x2 - x1}" height="4.5" rx="2" fill="${C.bench}"/>`
    + (legs ? `<rect x="${x1 + 3}" y="${y + 4}" width="2.4" height="${89 - y - 4}" fill="${C.bench}"/><rect x="${x2 - 5.4}" y="${y + 4}" width="2.4" height="${89 - y - 4}" fill="${C.bench}"/>` : ''),
  incline: (cx, cy, len, ang) => {
    const a = step({ x: cx, y: cy }, ang, len);
    return seg({ x: cx, y: cy }, a, 4.5, C.bench) + `<rect x="${cx - 2}" y="${cy}" width="2.4" height="${89 - cy}" fill="${C.bench}"/>`;
  },
  bar: (x1, x2, y) => seg({ x: x1, y }, { x: x2, y }, 2.2, C.prop),
  tower: (x, top = 10) => `<rect x="${x - 3}" y="${top}" width="6" height="${89 - top}" rx="1.5" fill="${C.bench}"/>` + dot({ x, y: top + 3 }, 2.2, C.prop),
  cable: (a, b) => seg(a, b, 0.9, C.prop),
  box: (x, y, w, h) => `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="1.5" fill="${C.bench}"/>`,
};

function svg(inner) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="100" height="100">
  <defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${C.bgTop}"/><stop offset="1" stop-color="${C.bgBottom}"/></linearGradient>
    <radialGradient id="headg" cx="0.36" cy="0.3" r="0.8"><stop offset="0" stop-color="#BDBDBD"/><stop offset="1" stop-color="#6E6E6E"/></radialGradient>
    <radialGradient id="plate" cx="0.38" cy="0.32" r="0.8"><stop offset="0" stop-color="#5c5c5c"/><stop offset="1" stop-color="#262626"/></radialGradient>
    <radialGradient id="vignette" cx="0.5" cy="0.42" r="0.75"><stop offset="0.6" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity="0.10"/></radialGradient>
    <filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="1.6"/></filter>
    <filter id="soft" x="-50%" y="-200%" width="200%" height="500%"><feGaussianBlur stdDeviation="1.4"/></filter></defs>
  <rect width="100" height="100" fill="url(#bg)"/><rect width="100" height="100" fill="url(#vignette)"/>${inner}</svg>`;
}

module.exports = { C, L, build, drawFigure, P, svg, step, dot, seg };
