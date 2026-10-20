'use strict';

// ---------- Datos ----------

const KEY = 'gym-tracker-v1';
const uid = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4);

function defaultState() {
  const ex = (name, sets, reps, weight) => ({ id: uid(), name, sets, reps, weight });
  const food = (name, kcal, protein, carbs, fat) => ({ id: uid(), name, kcal, protein, carbs, fat });
  return {
    version: 1,
    routines: [
      { id: uid(), name: 'Día A · Empuje', exercises: [
        ex('Press banca', 4, 8, 60), ex('Press militar', 3, 10, 35),
        ex('Fondos', 3, 10, 0), ex('Extensión tríceps polea', 3, 12, 20),
      ] },
      { id: uid(), name: 'Día B · Tirón', exercises: [
        ex('Dominadas', 4, 8, 0), ex('Remo con barra', 4, 8, 50),
        ex('Jalón al pecho', 3, 12, 45), ex('Curl bíceps', 3, 12, 12),
      ] },
      { id: uid(), name: 'Día C · Pierna', exercises: [
        ex('Sentadilla', 4, 6, 80), ex('Peso muerto rumano', 3, 10, 60),
        ex('Prensa', 3, 12, 120), ex('Gemelo de pie', 4, 15, 40),
      ] },
    ],
    sessions: [],
    active: null,
    weights: [],
    foods: [
      food('Desayuno habitual', 450, 30, 50, 12),
      food('Comida habitual', 750, 50, 80, 20),
      food('Cena habitual', 600, 45, 45, 22),
      food('Batido proteína', 120, 24, 3, 1),
      food('Fruta', 90, 1, 22, 0),
      food('Snack', 200, 8, 20, 9),
    ],
    diet: {},
    targets: { kcal: 2400, protein: 160, weightGoal: null, rest: 120 },
  };
}

let state;
try { state = JSON.parse(localStorage.getItem(KEY)) || defaultState(); }
catch { state = defaultState(); }

function save() {
  try { localStorage.setItem(KEY, JSON.stringify(state)); }
  catch { toast('No se pudo guardar'); }
}

// ---------- Utilidades ----------

const $ = (sel, el = document) => el.querySelector(sel);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const num = (v) => { const n = parseFloat(String(v).replace(',', '.')); return Number.isFinite(n) ? n : 0; };
const fmt = (n, d = 1) => (Math.round(n * 10 ** d) / 10 ** d).toLocaleString('es-ES');
const pad = (n) => String(n).padStart(2, '0');
const isoDate = (d = new Date()) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const parseDate = (s) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
const addDays = (s, n) => { const d = parseDate(s); d.setDate(d.getDate() + n); return isoDate(d); };
const niceDate = (s, opts = { weekday: 'short', day: 'numeric', month: 'short' }) =>
  parseDate(s).toLocaleDateString('es-ES', opts);
const clock = (ms) => {
  const t = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(t / 3600), m = Math.floor((t % 3600) / 60), s = t % 60;
  return h ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
};

function toast(msg) {
  document.querySelectorAll('.toast').forEach((t) => t.remove());
  const t = document.createElement('div');
  t.className = 'toast';
  t.textContent = msg;
  document.body.append(t);
  setTimeout(() => t.remove(), 2300);
}

// ---------- Diálogo genérico ----------

const dialog = $('#dialog');
const dialogForm = $('#dialog-form');
let dialogHandlers = {};

function openDialog(html, handlers = {}) {
  dialogForm.innerHTML = html;
  dialogHandlers = handlers;
  dialog.showModal();
}
function closeDialog() { dialog.close(); }

dialogForm.addEventListener('click', (e) => {
  const b = e.target.closest('[data-d]');
  if (!b) return;
  e.preventDefault();
  const fn = dialogHandlers[b.dataset.d];
  if (fn) fn(new FormData(dialogForm), b);
  else closeDialog();
});
dialog.addEventListener('click', (e) => { if (e.target === dialog) closeDialog(); });
// Intro en un campo = Guardar
dialogForm.addEventListener('submit', (e) => {
  e.preventDefault();
  if (dialogHandlers.ok) dialogHandlers.ok(new FormData(dialogForm));
});

// ---------- Navegación ----------

let tab = sessionStorage.getItem('tab') || 'gym';
const TITLES = { gym: 'Entreno', weight: 'Peso', diet: 'Dieta', settings: 'Ajustes' };
const view = $('#view');

document.querySelector('.tabs').addEventListener('click', (e) => {
  const b = e.target.closest('[data-tab]');
  if (!b) return;
  tab = b.dataset.tab;
  sessionStorage.setItem('tab', tab);
  render();
  window.scrollTo(0, 0);
});

function render() {
  $('#title').textContent = TITLES[tab];
  document.querySelectorAll('.tabs button').forEach((b) => b.classList.toggle('on', b.dataset.tab === tab));
  view.innerHTML = ({ gym: renderGym, weight: renderWeight, diet: renderDiet, settings: renderSettings })[tab]();
  tick();
}

// Acciones con data-a="nombre" y data-* como parámetros
const actions = {};
view.addEventListener('click', (e) => {
  const el = e.target.closest('[data-a]');
  if (!el) return;
  const fn = actions[el.dataset.a];
  if (fn) { e.preventDefault(); fn(el.dataset, el); }
});

// ---------- Entreno ----------

function lastSetsFor(name) {
  for (let i = state.sessions.length - 1; i >= 0; i--) {
    const ex = state.sessions[i].exercises.find((x) => x.name.toLowerCase() === name.toLowerCase());
    if (ex && ex.sets.some((s) => s.done)) return ex.sets.filter((s) => s.done);
  }
  return null;
}

function bestKg(name) {
  let best = 0;
  for (const s of state.sessions) for (const ex of s.exercises)
    if (ex.name.toLowerCase() === name.toLowerCase())
      for (const st of ex.sets) if (st.done) best = Math.max(best, st.kg);
  return best;
}

function nextRoutineId() {
  if (!state.routines.length) return null;
  const last = state.sessions[state.sessions.length - 1];
  if (!last) return state.routines[0].id;
  const i = state.routines.findIndex((r) => r.id === last.routineId);
  return i < 0 ? state.routines[0].id : state.routines[(i + 1) % state.routines.length].id;
}

const summarize = (sets) => {
  if (!sets || !sets.length) return '';
  const same = sets.every((s) => s.kg === sets[0].kg && s.reps === sets[0].reps);
  return same ? `${sets.length}×${sets[0].reps} · ${fmt(sets[0].kg)} kg`
    : sets.map((s) => `${fmt(s.kg)}×${s.reps}`).join(', ');
};

const volume = (session) => session.exercises.reduce((v, ex) =>
  v + ex.sets.reduce((a, s) => a + (s.done ? s.kg * s.reps : 0), 0), 0);

function renderGym() {
  if (state.active) return renderSession();
  const next = nextRoutineId();
  let h = '<div class="section"><h2>Empezar</h2></div>';
  if (!state.routines.length) h += '<div class="card empty">Crea una rutina en Ajustes</div>';
  for (const r of state.routines) {
    h += `<button class="routine-btn ${r.id === next ? 'next' : ''}" data-a="start" data-id="${r.id}">
      <div class="play">▶</div>
      <div class="grow"><h3>${esc(r.name)}</h3>
        <div class="muted small ellipsis">${r.id === next ? 'Toca hoy · ' : ''}${r.exercises.map((x) => esc(x.name)).join(', ')}</div>
      </div></button>`;
  }

  const recent = state.sessions.slice(-15).reverse();
  h += '<div class="section"><h2>Historial</h2>';
  if (state.sessions.length) h += '<button class="btn ghost small" data-a="progress">Progreso ›</button>';
  h += '</div>';
  if (!recent.length) h += '<div class="card empty">Aún no hay entrenos</div>';
  else {
    h += '<div class="list">';
    for (const s of recent) {
      h += `<button class="item" data-a="showSession" data-id="${s.id}">
        <div class="grow"><b>${esc(s.routineName)}</b>
          <div class="muted small">${niceDate(s.date)} · ${clock(s.end - s.start)} · ${fmt(volume(s), 0)} kg</div></div>
        <span class="muted">›</span></button>`;
    }
    h += '</div>';
  }
  return h;
}

function renderSession() {
  const a = state.active;
  let h = `<div class="card session-head row between">
    <div><h3>${esc(a.routineName)}</h3>
      <div class="small"><span class="timer" data-since="${a.start}"></span>
      <span class="rest" data-rest="${a.lastSetAt || ''}"></span></div></div>
    <button class="btn primary small" data-a="finish">Terminar</button></div>`;

  a.exercises.forEach((ex, ei) => {
    const prev = lastSetsFor(ex.name);
    const best = bestKg(ex.name);
    h += `<div class="card ex-card"><div class="row between">
      <div class="grow"><h3 class="ellipsis">${esc(ex.name)}</h3>
      <div class="muted small">${prev ? 'Última: ' + summarize(prev) : 'Primera vez'}${best ? ` · Récord ${fmt(best)} kg` : ''}</div></div>
      </div>
      <div class="sets"><div class="set-head"><span>#</span><span>kg</span><span>reps</span><span></span></div>`;
    ex.sets.forEach((s, si) => {
      h += `<div class="set ${s.done ? 'done' : ''}">
        <span class="n">${si + 1}</span>
        <input type="number" inputmode="decimal" step="0.5" value="${s.kg}" data-in="kg" data-e="${ei}" data-s="${si}">
        <input type="number" inputmode="numeric" value="${s.reps}" data-in="reps" data-e="${ei}" data-s="${si}">
        <button class="check" data-a="toggleSet" data-e="${ei}" data-s="${si}">✓</button></div>`;
    });
    h += `</div><div class="row" style="margin-top:8px">
      <button class="btn ghost small" data-a="addSet" data-e="${ei}">+ Serie</button>
      ${ex.sets.length > 1 ? `<button class="btn ghost small" data-a="removeSet" data-e="${ei}">− Serie</button>` : ''}
      </div></div>`;
  });

  h += `<button class="btn block" data-a="addExercise">+ Ejercicio extra</button>
    <button class="btn danger block" style="margin-top:8px" data-a="discard">Descartar entreno</button>`;
  return h;
}

view.addEventListener('input', (e) => {
  const el = e.target;
  if (!el.dataset.in || !state.active) return;
  const set = state.active.exercises[el.dataset.e].sets[el.dataset.s];
  set[el.dataset.in] = el.dataset.in === 'kg' ? num(el.value) : Math.round(num(el.value));
  save();
});

actions.start = ({ id }) => {
  const r = state.routines.find((x) => x.id === id);
  if (!r) return;
  state.active = {
    id: uid(), routineId: r.id, routineName: r.name, date: isoDate(), start: Date.now(), lastSetAt: null,
    exercises: r.exercises.map((ex) => {
      const prev = lastSetsFor(ex.name);
      // Mismas series que la rutina (o más si la última vez hiciste extra), con los kg/reps de la última vez
      const n = Math.max(ex.sets, prev ? prev.length : 0);
      const sets = Array.from({ length: n }, (_, i) => {
        const p = prev && (prev[i] || prev[prev.length - 1]);
        return p ? { kg: p.kg, reps: p.reps, done: false } : { kg: ex.weight, reps: ex.reps, done: false };
      });
      return { name: ex.name, sets };
    }),
  };
  save(); render(); window.scrollTo(0, 0);
};

actions.toggleSet = ({ e, s }) => {
  const set = state.active.exercises[e].sets[s];
  set.done = !set.done;
  if (set.done) state.active.lastSetAt = Date.now();
  save(); render();
};

actions.addSet = ({ e }) => {
  const sets = state.active.exercises[e].sets;
  const last = sets[sets.length - 1] || { kg: 0, reps: 10 };
  sets.push({ kg: last.kg, reps: last.reps, done: false });
  save(); render();
};

actions.removeSet = ({ e }) => {
  state.active.exercises[e].sets.pop();
  save(); render();
};

actions.addExercise = () => {
  openDialog(`<h2>Ejercicio extra</h2>
    <label class="field"><span>Nombre</span><input type="text" name="name" required autocomplete="off"></label>
    <div class="actions"><button class="btn" data-d="cancel">Cancelar</button>
    <button class="btn primary" data-d="ok">Añadir</button></div>`, {
    ok(fd) {
      const name = String(fd.get('name')).trim();
      if (!name) return;
      const prev = lastSetsFor(name);
      state.active.exercises.push({
        name,
        sets: prev ? prev.map((s) => ({ kg: s.kg, reps: s.reps, done: false })) : [{ kg: 0, reps: 10, done: false }],
      });
      save(); closeDialog(); render();
    },
  });
};

actions.finish = () => {
  const a = state.active;
  const done = a.exercises.some((ex) => ex.sets.some((s) => s.done));
  if (!done && !confirm('No has marcado ninguna serie. ¿Terminar igualmente?')) return;
  a.end = Date.now();
  delete a.lastSetAt;
  a.exercises = a.exercises.filter((ex) => ex.sets.some((s) => s.done));
  if (a.exercises.length) state.sessions.push(a);
  state.active = null;
  save(); render(); window.scrollTo(0, 0);
  if (done) toast('Entreno guardado 💪');
};

actions.discard = () => {
  if (!confirm('¿Descartar este entreno?')) return;
  state.active = null;
  save(); render();
};

actions.showSession = ({ id }) => {
  const s = state.sessions.find((x) => x.id === id);
  if (!s) return;
  const body = s.exercises.map((ex) =>
    `<div class="row between small"><b class="grow ellipsis">${esc(ex.name)}</b><span class="muted">${summarize(ex.sets.filter((x) => x.done))}</span></div>`
  ).join('');
  openDialog(`<h2>${esc(s.routineName)}</h2>
    <div class="muted small" style="margin-bottom:12px">${niceDate(s.date, { weekday: 'long', day: 'numeric', month: 'long' })} · ${clock(s.end - s.start)} · ${fmt(volume(s), 0)} kg movidos</div>
    <div class="stack">${body}</div>
    <div class="actions"><button class="btn danger left" data-d="del">Borrar</button>
    <button class="btn primary" data-d="close">Cerrar</button></div>`, {
    del() {
      if (!confirm('¿Borrar este entreno?')) return;
      state.sessions = state.sessions.filter((x) => x.id !== id);
      save(); closeDialog(); render();
    },
  });
};

actions.progress = () => {
  const names = [...new Set(state.sessions.flatMap((s) => s.exercises.map((e) => e.name)))].sort();
  const draw = (name) => {
    const pts = [];
    for (const s of state.sessions) {
      const ex = s.exercises.find((e) => e.name === name);
      if (!ex) continue;
      const kg = Math.max(...ex.sets.filter((x) => x.done).map((x) => x.kg), 0);
      pts.push({ date: s.date, v: kg });
    }
    return `${chart(pts, {})}<div class="muted small" style="margin-top:6px">Peso máximo por sesión · Récord ${fmt(bestKg(name))} kg</div>`;
  };
  openDialog(`<h2>Progreso</h2>
    <select name="ex" id="prog-ex">${names.map((n) => `<option>${esc(n)}</option>`).join('')}</select>
    <div id="prog-chart" style="margin-top:12px">${names.length ? draw(names[0]) : ''}</div>
    <div class="actions"><button class="btn primary" data-d="close">Cerrar</button></div>`);
  $('#prog-ex').addEventListener('change', (e) => { $('#prog-chart').innerHTML = draw(e.target.value); });
};

// Temporizadores (duración del entreno y descanso desde la última serie)
function tick() {
  const now = Date.now();
  document.querySelectorAll('[data-since]').forEach((el) => { el.textContent = '⏱ ' + clock(now - Number(el.dataset.since)); });
  document.querySelectorAll('[data-rest]').forEach((el) => {
    const t = Number(el.dataset.rest);
    if (!t) { el.textContent = ''; return; }
    const ms = now - t;
    const goal = (state.targets.rest || 0) * 1000;
    el.textContent = ` · Descanso ${clock(ms)}${goal && ms >= goal ? ' ✓' : ''}`;
  });
}
setInterval(tick, 1000);

// ---------- Gráfica SVG ----------

function chart(points, { avg = false, goal = null, height = 180 } = {}) {
  if (points.length < 2) return '<div class="card empty">Necesitas al menos 2 registros para ver la gráfica</div>';
  const W = 340, H = height, L = 34, R = 8, T = 10, B = 22;
  const xs = points.map((p) => parseDate(p.date).getTime());
  const vals = points.map((p) => p.v).concat(goal != null ? [goal] : []);
  let min = Math.min(...vals), max = Math.max(...vals);
  if (max - min < 1) { min -= 1; max += 1; }
  const padV = (max - min) * 0.1; min -= padV; max += padV;
  const dec = max - min < 6 ? 1 : 0;
  const x0 = xs[0], x1 = xs[xs.length - 1] === x0 ? x0 + 1 : xs[xs.length - 1];
  const X = (t) => L + ((t - x0) / (x1 - x0)) * (W - L - R);
  const Y = (v) => T + (1 - (v - min) / (max - min)) * (H - T - B);

  let g = '';
  for (let i = 0; i <= 3; i++) {
    const v = min + ((max - min) * i) / 3, y = Y(v);
    g += `<line class="grid" x1="${L}" x2="${W - R}" y1="${y}" y2="${y}"/><text class="lbl" x="${L - 4}" y="${y + 3}" text-anchor="end">${fmt(v, dec)}</text>`;
  }
  const fd = (t) => new Date(t).toLocaleDateString('es-ES', { day: 'numeric', month: 'short' });
  g += `<text class="lbl" x="${L}" y="${H - 6}">${fd(x0)}</text><text class="lbl" x="${W - R}" y="${H - 6}" text-anchor="end">${fd(xs[xs.length - 1])}</text>`;
  if (goal != null) g += `<line class="goal" x1="${L}" x2="${W - R}" y1="${Y(goal)}" y2="${Y(goal)}"/>`;

  const path = points.map((p, i) => `${i ? 'L' : 'M'}${X(xs[i]).toFixed(1)},${Y(p.v).toFixed(1)}`).join('');
  let avgPath = '';
  if (avg) {
    // Media móvil de 7 días
    avgPath = points.map((p, i) => {
      const from = xs[i] - 6 * 864e5;
      const win = points.filter((_, j) => xs[j] >= from && xs[j] <= xs[i]);
      const m = win.reduce((a, q) => a + q.v, 0) / win.length;
      return `${i ? 'L' : 'M'}${X(xs[i]).toFixed(1)},${Y(m).toFixed(1)}`;
    }).join('');
  }
  const dots = points.length <= 40 ? points.map((p, i) => `<circle class="dot" cx="${X(xs[i])}" cy="${Y(p.v)}" r="3"/>`).join('') : '';
  return `<svg class="chart" viewBox="0 0 ${W} ${H}">${g}${avg ? `<path class="avg" d="${avgPath}"/>` : ''}<path class="line" d="${path}"/>${dots}</svg>`;
}

// ---------- Peso ----------

function weightAt(daysAgo) {
  const target = addDays(isoDate(), -daysAgo);
  const w = [...state.weights].reverse().find((x) => x.date <= target);
  return w ? w.kg : null;
}

function renderWeight() {
  const ws = state.weights;
  const last = ws[ws.length - 1];
  const delta = (d) => {
    const old = weightAt(d);
    if (!last || old == null || ws[0].date > addDays(isoDate(), -d)) return '—';
    const v = last.kg - old;
    return `<span class="${v > 0 ? 'up' : v < 0 ? 'down' : ''}">${v > 0 ? '+' : ''}${fmt(v)}</span>`;
  };
  const todayVal = ws.find((x) => x.date === isoDate());

  let h = `<div class="card"><div class="row">
    <input type="number" id="w-kg" inputmode="decimal" step="0.1" placeholder="Peso kg" value="${todayVal ? todayVal.kg : ''}" style="flex:1;min-width:0">
    <input type="date" id="w-date" value="${isoDate()}" style="width:132px;flex:none">
    <button class="btn primary" data-a="saveWeight">Guardar</button></div></div>
    <div class="stats">
      <div class="stat"><b>${last ? fmt(last.kg) : '—'}</b><span>Actual (kg)</span></div>
      <div class="stat"><b>${delta(7)}</b><span>7 días</span></div>
      <div class="stat"><b>${delta(30)}</b><span>30 días</span></div>
    </div>`;

  const since = addDays(isoDate(), -90);
  const pts = ws.filter((w) => w.date >= since).map((w) => ({ date: w.date, v: w.kg }));
  h += `<div class="section"><h2>Últimos 90 días</h2>${state.targets.weightGoal ? `<span class="muted small">Objetivo ${fmt(state.targets.weightGoal)} kg</span>` : ''}</div>
    <div class="card">${chart(pts, { avg: true, goal: state.targets.weightGoal })}
    <div class="muted small" style="margin-top:4px">Línea discontinua: media de 7 días</div></div>`;

  h += '<div class="section"><h2>Registros</h2></div>';
  if (!ws.length) h += '<div class="card empty">Apunta tu primer peso arriba</div>';
  else {
    h += '<div class="list">';
    ws.slice(-30).reverse().forEach((w) => {
      h += `<div class="item"><div class="grow">${niceDate(w.date)}</div><b>${fmt(w.kg)} kg</b>
        <button class="btn ghost small" data-a="delWeight" data-date="${w.date}">✕</button></div>`;
    });
    h += '</div>';
  }
  return h;
}

actions.saveWeight = () => {
  const date = $('#w-date').value || isoDate();
  const kg = num($('#w-kg').value);
  if (!kg) { toast('Escribe tu peso'); return; }
  state.weights = state.weights.filter((w) => w.date !== date);
  state.weights.push({ date, kg });
  state.weights.sort((a, b) => a.date.localeCompare(b.date));
  save(); render(); toast('Peso guardado');
};

actions.delWeight = ({ date }) => {
  if (!confirm(`¿Borrar el peso del ${niceDate(date)}?`)) return;
  state.weights = state.weights.filter((w) => w.date !== date);
  save(); render();
};

// ---------- Dieta ----------

let dietDay = isoDate();
const macros = (entries) => entries.reduce((t, e) => ({
  kcal: t.kcal + e.kcal * e.qty, protein: t.protein + e.protein * e.qty,
  carbs: t.carbs + e.carbs * e.qty, fat: t.fat + e.fat * e.qty,
}), { kcal: 0, protein: 0, carbs: 0, fat: 0 });

function renderDiet() {
  const entries = state.diet[dietDay] || [];
  const t = macros(entries);
  const tg = state.targets;
  const bar = (v, max) => `<div class="bar ${max && v > max * 1.05 ? 'over' : ''}"><i style="width:${max ? Math.min(100, (v / max) * 100) : 0}%"></i></div>`;
  const label = dietDay === isoDate() ? 'Hoy' : dietDay === addDays(isoDate(), -1) ? 'Ayer'
    : niceDate(dietDay, { weekday: 'long', day: 'numeric', month: 'short' });

  let h = `<div class="daynav"><button class="btn" data-a="dietDay" data-n="-1">‹</button>
    <b>${label}</b>
    <button class="btn" data-a="dietDay" data-n="1" ${dietDay >= isoDate() ? 'disabled style="opacity:.3"' : ''}>›</button></div>
    <div class="card" style="margin-top:10px">
      <div class="row between"><b>Calorías</b><span><b>${fmt(t.kcal, 0)}</b> <span class="muted">/ ${tg.kcal} kcal</span></span></div>${bar(t.kcal, tg.kcal)}
      <div class="row between" style="margin-top:8px"><b>Proteína</b><span><b>${fmt(t.protein, 0)}</b> <span class="muted">/ ${tg.protein} g</span></span></div>${bar(t.protein, tg.protein)}
      <div class="muted small" style="margin-top:6px">Carbos ${fmt(t.carbs, 0)} g · Grasa ${fmt(t.fat, 0)} g</div>
    </div>`;

  h += '<div class="section"><h2>Añadir (un toque)</h2><button class="btn ghost small" data-a="customFood">+ Otra cosa</button></div>';
  if (!state.foods.length) h += '<div class="card empty">Crea tus comidas habituales en Ajustes</div>';
  h += '<div class="foods">' + state.foods.map((f) =>
    `<button class="food" data-a="addFood" data-id="${f.id}"><b>${esc(f.name)}</b><span>${f.kcal} kcal · ${f.protein} g prot</span></button>`
  ).join('') + '</div>';

  h += `<div class="section"><h2>Comido</h2>${!entries.length && (state.diet[addDays(dietDay, -1)] || []).length
    ? '<button class="btn ghost small" data-a="copyYesterday">Copiar del día anterior</button>' : ''}</div>`;
  if (!entries.length) h += '<div class="card empty">Nada apuntado</div>';
  else {
    h += '<div class="list">';
    entries.forEach((e, i) => {
      h += `<div class="item"><div class="grow"><b class="ellipsis" style="display:block">${esc(e.name)}</b>
        <span class="muted small">${fmt(e.kcal * e.qty, 0)} kcal · ${fmt(e.protein * e.qty, 0)} g prot</span></div>
        <div class="qty"><button data-a="qty" data-i="${i}" data-n="-0.5">−</button><b>${fmt(e.qty)}</b><button data-a="qty" data-i="${i}" data-n="0.5">+</button></div></div>`;
    });
    h += '</div>';
  }
  return h;
}

function pushEntry(e) {
  const list = state.diet[dietDay] || (state.diet[dietDay] = []);
  const same = e.foodId && list.find((x) => x.foodId === e.foodId);
  if (same) same.qty += 1;
  else list.push({ ...e, qty: 1 });
  save(); render();
}

actions.dietDay = ({ n }) => {
  const d = addDays(dietDay, Number(n));
  if (d > isoDate()) return;
  dietDay = d; render();
};

actions.addFood = ({ id }) => {
  const f = state.foods.find((x) => x.id === id);
  if (!f) return;
  pushEntry({ foodId: f.id, name: f.name, kcal: f.kcal, protein: f.protein, carbs: f.carbs, fat: f.fat });
  toast(`+ ${f.name}`);
};

actions.qty = ({ i, n }) => {
  const list = state.diet[dietDay];
  list[i].qty = Math.round((list[i].qty + Number(n)) * 10) / 10;
  if (list[i].qty <= 0) list.splice(i, 1);
  if (!list.length) delete state.diet[dietDay];
  save(); render();
};

actions.copyYesterday = () => {
  state.diet[dietDay] = (state.diet[addDays(dietDay, -1)] || []).map((e) => ({ ...e }));
  save(); render(); toast('Copiado');
};

function foodForm(f = {}, title, extra = '') {
  const field = (name, label, type = 'number') =>
    `<label class="field"><span>${label}</span><input type="${type}" name="${name}" value="${esc(f[name] ?? '')}"
      ${type === 'number' ? 'inputmode="decimal"' : 'autocomplete="off"'}></label>`;
  return `<h2>${title}</h2>${field('name', 'Nombre', 'text')}
    <div class="row">${field('kcal', 'kcal')}${field('protein', 'Proteína g')}</div>
    <div class="row">${field('carbs', 'Carbos g')}${field('fat', 'Grasa g')}</div>
    <div class="actions">${extra}<button class="btn" data-d="cancel">Cancelar</button><button class="btn primary" data-d="ok">Guardar</button></div>`;
}
const readFood = (fd) => ({
  name: String(fd.get('name') || '').trim() || 'Comida',
  kcal: Math.round(num(fd.get('kcal'))), protein: num(fd.get('protein')),
  carbs: num(fd.get('carbs')), fat: num(fd.get('fat')),
});

actions.customFood = () => {
  openDialog(foodForm({}, 'Comida puntual'), {
    ok(fd) { closeDialog(); pushEntry(readFood(fd)); },
  });
};

// ---------- Ajustes ----------

function renderSettings() {
  const tg = state.targets;
  let h = '<div class="section"><h2>Rutinas</h2><button class="btn ghost small" data-a="editRoutine">+ Nueva</button></div><div class="list">';
  h += state.routines.map((r) => `<button class="item" data-a="editRoutine" data-id="${r.id}">
    <div class="grow"><b>${esc(r.name)}</b><div class="muted small">${r.exercises.length} ejercicios</div></div><span class="muted">›</span></button>`).join('')
    || '<div class="empty">Sin rutinas</div>';
  h += '</div>';

  h += '<div class="section"><h2>Comidas habituales</h2><button class="btn ghost small" data-a="editFood">+ Nueva</button></div><div class="list">';
  h += state.foods.map((f) => `<button class="item" data-a="editFood" data-id="${f.id}">
    <div class="grow"><b>${esc(f.name)}</b><div class="muted small">${f.kcal} kcal · P ${f.protein} · C ${f.carbs} · G ${f.fat}</div></div><span class="muted">›</span></button>`).join('')
    || '<div class="empty">Sin comidas</div>';
  h += '</div>';

  h += `<div class="section"><h2>Objetivos</h2></div><div class="card">
    <div class="row"><label class="field grow"><span>kcal / día</span><input type="number" inputmode="numeric" data-t="kcal" value="${tg.kcal}"></label>
    <label class="field grow"><span>Proteína g / día</span><input type="number" inputmode="numeric" data-t="protein" value="${tg.protein}"></label></div>
    <div class="row"><label class="field grow"><span>Peso objetivo (kg)</span><input type="number" inputmode="decimal" data-t="weightGoal" value="${tg.weightGoal ?? ''}"></label>
    <label class="field grow"><span>Descanso (seg)</span><input type="number" inputmode="numeric" data-t="rest" value="${tg.rest}"></label></div></div>`;

  h += `<div class="section"><h2>Copia de seguridad</h2></div><div class="card stack">
    <div class="muted small">Tus datos solo están en este iPhone. Exporta una copia de vez en cuando (a Archivos / iCloud Drive).</div>
    <button class="btn block" data-a="exportData">Exportar copia</button>
    <label class="btn block" style="text-align:center">Importar copia<input type="file" accept="application/json,.json" id="import" hidden></label>
    <button class="btn danger block" data-a="resetAll">Borrar todos los datos</button></div>
    <div class="muted small" style="text-align:center;margin-top:12px">${state.sessions.length} entrenos · ${state.weights.length} pesos · ${Object.keys(state.diet).length} días de dieta</div>`;
  return h;
}

view.addEventListener('change', (e) => {
  const el = e.target;
  if (el.dataset.t) {
    const v = el.value === '' ? null : num(el.value);
    state.targets[el.dataset.t] = el.dataset.t === 'weightGoal' ? v : (v ?? 0);
    save(); toast('Guardado');
  }
  if (el.id === 'import' && el.files[0]) importData(el.files[0]);
});

// Formato de rutina: una línea por ejercicio, p. ej. "Press banca 4x8 60"
const exLine = (x) => `${x.name} ${x.sets}x${x.reps}${x.weight ? ' ' + fmt(x.weight).replace(',', '.') : ''}`;
function parseExercises(text) {
  return text.split('\n').map((l) => l.trim()).filter(Boolean).map((l) => {
    const m = l.match(/^(.*?)\s+(\d+)\s*[x×*]\s*(\d+)(?:\s+(\d+(?:[.,]\d+)?)\s*(?:kg)?)?\s*$/i);
    return m ? { id: uid(), name: m[1].trim(), sets: +m[2], reps: +m[3], weight: m[4] ? num(m[4]) : 0 }
      : { id: uid(), name: l, sets: 3, reps: 10, weight: 0 };
  });
}

actions.editRoutine = ({ id }) => {
  const r = state.routines.find((x) => x.id === id);
  openDialog(`<h2>${r ? 'Editar rutina' : 'Nueva rutina'}</h2>
    <label class="field"><span>Nombre</span><input type="text" name="name" value="${esc(r?.name || '')}" autocomplete="off"></label>
    <label class="field"><span>Ejercicios — uno por línea: nombre series×reps kg</span>
      <textarea name="ex" placeholder="Press banca 4x8 60&#10;Dominadas 4x8&#10;Curl bíceps 3x12 12">${esc(r ? r.exercises.map(exLine).join('\n') : '')}</textarea></label>
    <div class="muted small">El primer día usa estos kg. Después, cada entreno se rellena con lo que hiciste la última vez.</div>
    <div class="actions">${r ? '<button class="btn danger left" data-d="del">Borrar</button>' : ''}
      ${r && state.routines.length > 1 ? '<button class="btn" data-d="up">↑</button>' : ''}
      <button class="btn" data-d="cancel">Cancelar</button><button class="btn primary" data-d="ok">Guardar</button></div>`, {
    ok(fd) {
      const name = String(fd.get('name')).trim() || 'Rutina';
      const exercises = parseExercises(String(fd.get('ex')));
      if (r) Object.assign(r, { name, exercises });
      else state.routines.push({ id: uid(), name, exercises });
      save(); closeDialog(); render();
    },
    up() {
      const i = state.routines.indexOf(r);
      if (i > 0) [state.routines[i - 1], state.routines[i]] = [state.routines[i], state.routines[i - 1]];
      save(); closeDialog(); render();
    },
    del() {
      if (!confirm(`¿Borrar la rutina "${r.name}"? El historial se mantiene.`)) return;
      state.routines = state.routines.filter((x) => x !== r);
      save(); closeDialog(); render();
    },
  });
};

actions.editFood = ({ id }) => {
  const f = state.foods.find((x) => x.id === id);
  openDialog(foodForm(f || {}, f ? 'Editar comida' : 'Nueva comida',
    f ? '<button class="btn danger left" data-d="del">Borrar</button>' : ''), {
    ok(fd) {
      const data = readFood(fd);
      if (f) Object.assign(f, data);
      else state.foods.push({ id: uid(), ...data });
      save(); closeDialog(); render();
    },
    del() {
      state.foods = state.foods.filter((x) => x !== f);
      save(); closeDialog(); render();
    },
  });
};

actions.exportData = async () => {
  const name = `gym-backup-${isoDate()}.json`;
  const file = new File([JSON.stringify(state, null, 1)], name, { type: 'application/json' });
  if (navigator.canShare && navigator.canShare({ files: [file] })) {
    try { await navigator.share({ files: [file], title: name }); return; }
    catch (err) { if (err.name === 'AbortError') return; }
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(file);
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
};

function importData(file) {
  const reader = new FileReader();
  reader.onload = () => {
    try {
      const data = JSON.parse(reader.result);
      if (!data || !Array.isArray(data.routines) || !Array.isArray(data.sessions)) throw new Error();
      if (!confirm('Esto reemplaza todos tus datos actuales por los de la copia. ¿Seguir?')) return;
      state = { ...defaultState(), ...data };
      save(); render(); toast('Copia importada');
    } catch { toast('Archivo no válido'); }
  };
  reader.readAsText(file);
}

actions.resetAll = () => {
  if (!confirm('¿Borrar TODO? No se puede deshacer.')) return;
  if (!confirm('¿Seguro? Exporta una copia antes si la quieres.')) return;
  state = defaultState();
  save(); render();
};

// ---------- Arranque ----------

render();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});
// Al volver a la app otro día, la dieta salta al día nuevo
let seenToday = isoDate();
document.addEventListener('visibilitychange', () => {
  const today = isoDate();
  if (document.hidden || today === seenToday) return;
  if (dietDay === seenToday) dietDay = today;
  seenToday = today;
  if (!state.active) render();
});
