'use strict';

// ---------- Datos ----------

const KEY = 'gym-tracker-v1';
const uid = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4);

// Rutina: { id, name, kind: 'gym' | 'cardio', desc, exercises: [{ id, name, note, sets: [{ kg, reps, rir }] }] }
// Los objetivos de cada serie son texto ("90", "4-8", "1-2", "AMRAP") tal cual los pone el entrenador.
function defaultState() {
  const ex = (name, n, reps, kg, rir = '') =>
    ({ id: uid(), name, note: '', sets: Array.from({ length: n }, () => ({ kg: String(kg), reps: String(reps), rir })) });
  const food = (name, kcal, protein, carbs, fat) => ({ id: uid(), name, kcal, protein, carbs, fat });
  return {
    version: 2,
    routines: [
      { id: uid(), name: 'Día A · Empuje', kind: 'gym', desc: '', exercises: [
        ex('Press banca', 4, '6-8', 60, '1-2'), ex('Press militar', 3, '8-10', 35, '1-2'),
        ex('Fondos', 3, '10-12', 0), ex('Extensión tríceps polea', 3, '12-15', 20),
      ] },
      { id: uid(), name: 'Día B · Tirón', kind: 'gym', desc: '', exercises: [
        ex('Dominadas', 4, 'AMRAP', 0), ex('Remo con barra', 4, '6-8', 50, '1-2'),
        ex('Jalón al pecho', 3, '10-12', 45), ex('Curl bíceps', 3, '10-12', 12),
      ] },
      { id: uid(), name: 'Día C · Pierna', kind: 'gym', desc: '', exercises: [
        ex('Sentadilla', 4, '4-6', 80, '1-2'), ex('Peso muerto rumano', 3, '8-10', 60),
        ex('Prensa', 3, '10-12', 120), ex('Gemelo de pie', 4, '12-15', 40),
      ] },
      { id: uid(), name: 'Cardio', kind: 'cardio', desc: '30 min corriendo', exercises: [] },
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

// Pasa datos de versiones anteriores al formato actual
function migrate(s) {
  if ((s.version || 1) < 2) {
    for (const r of s.routines) {
      r.kind = r.kind || 'gym';
      r.desc = r.desc || '';
      for (const ex of r.exercises) {
        if (typeof ex.sets === 'number') {
          ex.sets = Array.from({ length: ex.sets }, () => ({ kg: ex.weight ? String(ex.weight) : '', reps: String(ex.reps), rir: '' }));
          delete ex.reps; delete ex.weight;
        }
        ex.note = ex.note || '';
      }
    }
    for (const ses of s.sessions) {
      ses.kind = ses.kind || 'gym';
      for (const ex of ses.exercises) for (const st of ex.sets) if (st.rir === undefined) st.rir = '';
    }
    s.active = null;
    s.version = 2;
  }
  return s;
}

let state;
try { state = migrate(JSON.parse(localStorage.getItem(KEY)) || defaultState()); }
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

const same = (a, b) => a.trim().toLowerCase() === b.trim().toLowerCase();
// Número si el texto es un número ("90", "-30", "7,5"); si no (AMRAP...), null
const numOrNull = (v) => (/^\s*-?\d+(?:[.,]\d+)?\s*$/.test(String(v ?? '')) ? num(v) : null);
// 1RM estimado (la misma fórmula que el Excel): kg · (1 + 0,033 · (reps + RIR))
const e1rm = (s) => (s.kg > 0 && s.reps > 0 ? s.kg * (1 + 0.033 * (s.reps + (Number(s.rir) || 0))) : 0);

function lastExercise(name) {
  for (let i = state.sessions.length - 1; i >= 0; i--) {
    const ex = state.sessions[i].exercises.find((x) => same(x.name, name));
    if (ex && ex.sets.some((s) => s.done)) return ex;
  }
  return null;
}
const lastSetsFor = (name) => { const ex = lastExercise(name); return ex ? ex.sets.filter((s) => s.done) : null; };

function records(name) {
  let kg = 0, rm = 0;
  for (const s of state.sessions) for (const ex of s.exercises)
    if (same(ex.name, name)) for (const st of ex.sets) if (st.done) { kg = Math.max(kg, st.kg); rm = Math.max(rm, e1rm(st)); }
  return { kg, rm };
}

const WEEKDAYS = ['domingo', 'lunes', 'martes', 'miercoles', 'jueves', 'viernes', 'sabado'];
const plain = (s) => s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');

// La rutina que toca: la que lleva el día de la semana en el nombre o, si no, la siguiente a la última hecha
function nextRoutineId() {
  if (!state.routines.length) return null;
  const today = WEEKDAYS[new Date().getDay()];
  const byDay = state.routines.find((r) => plain(r.name).includes(today));
  if (byDay) return byDay.id;
  if (state.routines.some((r) => WEEKDAYS.some((d) => plain(r.name).includes(d)))) return null;
  const last = state.sessions[state.sessions.length - 1];
  if (!last) return state.routines[0].id;
  const i = state.routines.findIndex((r) => r.id === last.routineId);
  return i < 0 ? state.routines[0].id : state.routines[(i + 1) % state.routines.length].id;
}

const cardioIcon = (name) => (/muay|box|kick|mma|bjj|judo|karate|taekwondo|lucha/.test(plain(name)) ? '🥊' : '🏃');

const summarize = (sets) => {
  if (!sets || !sets.length) return '';
  const eq = sets.every((s) => s.kg === sets[0].kg && s.reps === sets[0].reps);
  return eq ? `${sets.length}×${sets[0].reps} · ${fmt(sets[0].kg)} kg`
    : sets.map((s) => `${fmt(s.kg)}×${s.reps}`).join(', ');
};
const targetText = (t) => t && [t.kg !== '' ? `${t.kg} kg` : '', t.reps, t.rir !== '' ? `RIR ${t.rir}` : '']
  .filter(Boolean).join(' · ');

const volume = (session) => session.exercises.reduce((v, ex) =>
  v + ex.sets.reduce((a, s) => a + (s.done && s.kg > 0 ? s.kg * s.reps : 0), 0), 0);
const duration = (s) => (s.start && s.end ? clock(s.end - s.start) : '');

function renderGym() {
  if (state.active) return state.active.kind === 'cardio' ? renderCardio() : renderSession();
  const next = nextRoutineId();
  let h = '<div class="section"><h2>Empezar</h2></div>';
  if (!state.routines.length) h += '<div class="card empty">Crea una rutina en Ajustes</div>';
  for (const r of state.routines) {
    const sub = r.kind === 'cardio' ? esc(r.desc) : r.exercises.map((x) => esc(x.name)).join(', ');
    h += `<button class="routine-btn ${r.id === next ? 'next' : ''}" data-a="start" data-id="${r.id}">
      <div class="play ${r.kind === 'cardio' ? 'cardio' : ''}">${r.kind === 'cardio' ? cardioIcon(r.name) : '▶'}</div>
      <div class="grow"><h3>${esc(r.name)}</h3>
        <div class="muted small ellipsis">${r.id === next ? 'Toca hoy · ' : ''}${sub}</div>
      </div></button>`;
  }

  const recent = state.sessions.slice(-15).reverse();
  h += '<div class="section"><h2>Historial</h2>';
  if (state.sessions.some((s) => s.exercises.length)) h += '<button class="btn ghost small" data-a="progress">Progreso ›</button>';
  h += '</div>';
  if (!recent.length) h += '<div class="card empty">Aún no hay entrenos</div>';
  else {
    h += '<div class="list">';
    for (const s of recent) {
      const info = s.kind === 'cardio' ? esc(s.note || '') : `${fmt(volume(s), 0)} kg`;
      h += `<button class="item" data-a="showSession" data-id="${s.id}">
        <div class="grow"><b>${esc(s.routineName)}</b>
          <div class="muted small ellipsis">${[niceDate(s.date), duration(s), info].filter(Boolean).join(' · ')}</div></div>
        <span class="muted">›</span></button>`;
    }
    h += '</div>';
  }
  return h;
}

const sessionHead = (a) => `<div class="card session-head row between">
    <div class="grow"><h3 class="ellipsis">${esc(a.routineName)}</h3>
      <div class="small"><span class="timer" data-since="${a.start}"></span>
      <span class="rest" data-rest="${a.lastSetAt || ''}"></span></div></div>
    <button class="btn primary small" data-a="finish">Terminar</button></div>`;

function renderSession() {
  const a = state.active;
  let h = sessionHead(a);
  a.exercises.forEach((ex, ei) => {
    const prev = lastSetsFor(ex.name);
    const rec = records(ex.name);
    h += `<div class="card ex-card"><h3>${esc(ex.name)}</h3>
      ${ex.note ? `<div class="coach small">${esc(ex.note)}</div>` : ''}
      <div class="muted small">${prev ? 'Última: ' + summarize(prev) : 'Primera vez'}${rec.rm ? ` · 1RM est. ${fmt(rec.rm)} kg` : ''}</div>
      <div class="sets"><div class="set-head"><span>#</span><span>kg</span><span>reps</span><span>RIR</span><span></span></div>`;
    ex.sets.forEach((s, si) => {
      const at = `data-e="${ei}" data-s="${si}"`;
      h += `<div class="set ${s.done ? 'done' : ''}">
        <span class="n">${si + 1}</span>
        <input type="number" inputmode="decimal" step="0.5" value="${s.kg}" data-in="kg" ${at}>
        <input type="number" inputmode="numeric" value="${s.reps}" data-in="reps" ${at}>
        <input type="number" inputmode="numeric" value="${s.rir}" data-in="rir" ${at}>
        <button class="check" data-a="toggleSet" ${at}>✓</button></div>
        ${s.t ? `<div class="target">Objetivo: ${esc(targetText(s.t))}</div>` : ''}`;
    });
    h += `</div><div class="row" style="margin-top:8px">
      <button class="btn ghost small" data-a="addSet" data-e="${ei}">+ Serie</button>
      ${ex.sets.length > 1 ? `<button class="btn ghost small" data-a="removeSet" data-e="${ei}">− Serie</button>` : ''}</div>
      <input type="text" class="cnote" placeholder="Tus notas" value="${esc(ex.cnote || '')}" data-in="cnote" data-e="${ei}">
      </div>`;
  });

  h += `<button class="btn block" data-a="addExercise">+ Ejercicio extra</button>
    <button class="btn danger block" style="margin-top:8px" data-a="discard">Descartar entreno</button>`;
  return h;
}

function renderCardio() {
  const a = state.active;
  const r = state.routines.find((x) => x.id === a.routineId);
  const last = [...state.sessions].reverse().find((s) => s.routineId === a.routineId && s.note);
  return `${sessionHead(a)}
    <div class="card">${r && r.desc ? `<div class="coach">${esc(r.desc)}</div>` : ''}
      ${last ? `<div class="muted small" style="margin-top:6px">Última vez (${niceDate(last.date)}): ${esc(last.note)}</div>` : ''}
      <label class="field" style="margin:12px 0 0"><span>Qué has hecho</span>
      <textarea data-in="note" style="min-height:110px;font-family:inherit;font-size:16px" placeholder="Duración, rounds, intensidad, cómo te has sentido…">${esc(a.note || '')}</textarea></label>
    </div>
    <button class="btn danger block" data-a="discard">Descartar</button>`;
}

view.addEventListener('input', (e) => {
  const el = e.target;
  const a = state.active;
  if (!el.dataset.in || !a) return;
  const f = el.dataset.in;
  if (f === 'note') a.note = el.value;
  else if (f === 'cnote') a.exercises[el.dataset.e].cnote = el.value;
  else {
    const set = a.exercises[el.dataset.e].sets[el.dataset.s];
    if (f === 'kg') set.kg = num(el.value);
    else if (f === 'reps') set.reps = Math.round(num(el.value));
    else set.rir = el.value === '' ? '' : num(el.value);
  }
  save();
});

// Series de un ejercicio al empezar: las del plan, rellenas con lo que hiciste la última vez
function buildSets(ex) {
  const prev = lastSetsFor(ex.name);
  const n = Math.max(ex.sets.length, prev ? prev.length : 0, 1);
  let kgBefore = 0;
  return Array.from({ length: n }, (_, i) => {
    const t = ex.sets[i] || null;
    const p = prev && (prev[i] || prev[prev.length - 1]);
    const tKg = t ? numOrNull(t.kg) : null;
    const s = p ? { kg: p.kg, reps: p.reps, rir: p.rir ?? '' }
      : { kg: tKg ?? kgBefore, reps: t ? parseInt(t.reps, 10) || '' : '', rir: '' };
    kgBefore = s.kg;
    return { ...s, done: false, t: t && { ...t } };
  });
}

actions.start = ({ id }) => {
  const r = state.routines.find((x) => x.id === id);
  if (!r) return;
  state.active = {
    id: uid(), routineId: r.id, routineName: r.name, kind: r.kind, date: isoDate(), start: Date.now(), lastSetAt: null,
    note: '',
    exercises: r.kind === 'cardio' ? [] : r.exercises.map((ex) => ({ name: ex.name, note: ex.note || '', cnote: '', sets: buildSets(ex) })),
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
  const last = sets[sets.length - 1] || { kg: 0, reps: 10, rir: '' };
  sets.push({ kg: last.kg, reps: last.reps, rir: last.rir, done: false, t: null });
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
      state.active.exercises.push({ name, note: '', cnote: '', sets: buildSets({ name, sets: [{ kg: '', reps: '10', rir: '' }] }) });
      save(); closeDialog(); render();
    },
  });
};

actions.finish = () => {
  const a = state.active;
  const cardio = a.kind === 'cardio';
  const done = cardio || a.exercises.some((ex) => ex.sets.some((s) => s.done));
  if (!done && !confirm('No has marcado ninguna serie. ¿Terminar igualmente?')) return;
  a.end = Date.now();
  delete a.lastSetAt;
  a.exercises = a.exercises.filter((ex) => ex.sets.some((s) => s.done));
  if (cardio || a.exercises.length) state.sessions.push(a);
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
  const body = s.kind === 'cardio' ? `<div>${esc(s.note || 'Sin notas')}</div>` : s.exercises.map((ex) => {
    const sets = ex.sets.filter((x) => x.done);
    return `<div><div class="row between small"><b class="grow ellipsis">${esc(ex.name)}</b><span class="muted">${summarize(sets)}</span></div>
      ${ex.cnote ? `<div class="muted small">📝 ${esc(ex.cnote)}</div>` : ''}</div>`;
  }).join('');
  openDialog(`<h2>${esc(s.routineName)}</h2>
    <div class="muted small" style="margin-bottom:12px">${[niceDate(s.date, { weekday: 'long', day: 'numeric', month: 'long' }), duration(s),
      s.kind === 'cardio' ? '' : `${fmt(volume(s), 0)} kg movidos`].filter(Boolean).join(' · ')}</div>
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
      const rm = Math.max(...ex.sets.filter((x) => x.done).map(e1rm), 0);
      if (rm) pts.push({ date: s.date, v: rm });
    }
    const rec = records(name);
    return `${chart(pts, {})}<div class="muted small" style="margin-top:6px">1RM estimado por sesión · Récord ${fmt(rec.kg)} kg · 1RM máx. ${fmt(rec.rm)} kg</div>`;
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
    <div class="grow"><b>${esc(r.name)}</b><div class="muted small">${r.kind === 'cardio' ? esc(r.desc) || 'Cardio / clase' : `${r.exercises.length} ejercicios`}</div></div><span class="muted">›</span></button>`).join('')
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

// Formato de rutina: una línea por ejercicio
//   Press banca | 90x3-5@1-2, 85x4-8@1-2, 85x4-8@1-2 | Pausa en el pecho
// Cada serie es kg x reps @ RIR (todo opcional: "AMRAP", "x10", "-30xAMRAP").
// También vale la forma corta "Press banca 4x8 60".
function setToken(s) {
  const left = s.kg !== '' && s.reps !== '' ? `${s.kg}x${s.reps}` : s.kg !== '' ? s.kg : numOrNull(s.reps) !== null ? `x${s.reps}` : s.reps;
  return left + (s.rir !== '' ? `@${s.rir}` : '');
}
const exLine = (x) => [x.name, x.sets.map(setToken).join(', '), x.note].filter(Boolean).join(' | ');

function parseSet(tok) {
  const [left, rir = ''] = tok.split('@').map((p) => p.trim());
  const m = left.match(/^(-?\d+(?:[.,]\d+)?)?\s*[x×]\s*(.+)$/i);
  if (m) return { kg: m[1] || '', reps: m[2].trim(), rir };
  if (numOrNull(left) !== null) return { kg: left, reps: '', rir };
  return { kg: '', reps: left, rir };
}

function parseExercises(text) {
  return text.split('\n').map((l) => l.trim()).filter(Boolean).map((l) => {
    if (l.includes('|')) {
      const [name, sets = '', ...note] = l.split('|').map((p) => p.trim());
      const parsed = sets.split(',').map((t) => t.trim()).filter(Boolean).map(parseSet);
      return { id: uid(), name, note: note.join(' | '), sets: parsed.length ? parsed : [{ kg: '', reps: '', rir: '' }] };
    }
    const m = l.match(/^(.*?)\s+(\d+)\s*[x×*]\s*(\d+(?:-\d+)?)(?:\s+(-?\d+(?:[.,]\d+)?)\s*(?:kg)?)?\s*$/i);
    if (m) return { id: uid(), name: m[1].trim(), note: '', sets: Array.from({ length: +m[2] }, () => ({ kg: m[4] || '', reps: m[3], rir: '' })) };
    return { id: uid(), name: l, note: '', sets: Array.from({ length: 3 }, () => ({ kg: '', reps: '', rir: '' })) };
  });
}

actions.editRoutine = ({ id }) => {
  const r = state.routines.find((x) => x.id === id);
  const kind = r ? r.kind : 'gym';
  openDialog(`<h2>${r ? 'Editar rutina' : 'Nueva rutina'}</h2>
    <div class="row"><label class="field grow"><span>Nombre</span><input type="text" name="name" value="${esc(r?.name || '')}" autocomplete="off"></label>
    <label class="field" style="width:120px"><span>Tipo</span><select name="kind" id="r-kind">
      <option value="gym" ${kind === 'gym' ? 'selected' : ''}>Pesas</option><option value="cardio" ${kind === 'cardio' ? 'selected' : ''}>Cardio / clase</option></select></label></div>
    <div id="r-gym" ${kind === 'cardio' ? 'hidden' : ''}>
      <label class="field"><span>Ejercicios — uno por línea: nombre | kg x reps @ RIR, … | nota</span>
      <textarea name="ex" placeholder="Press banca | 90x3-5@1-2, 85x4-8@1-2 | Pausa en el pecho&#10;Dominadas | AMRAP, AMRAP&#10;Curl bíceps 3x12 12">${esc(r ? r.exercises.map(exLine).join('\n') : '')}</textarea></label>
      <div class="muted small">Estos son los objetivos. Al empezar, cada serie se rellena con lo que hiciste la última vez.</div>
    </div>
    <label class="field" id="r-cardio" ${kind === 'cardio' ? '' : 'hidden'}><span>Qué toca</span>
      <textarea name="desc" style="min-height:90px;font-family:inherit">${esc(r?.desc || '')}</textarea></label>
    <div class="actions">${r ? '<button class="btn danger left" data-d="del">Borrar</button>' : ''}
      ${r && state.routines.length > 1 ? '<button class="btn" data-d="up">↑</button>' : ''}
      <button class="btn" data-d="cancel">Cancelar</button><button class="btn primary" data-d="ok">Guardar</button></div>`, {
    ok(fd) {
      const data = {
        name: String(fd.get('name')).trim() || 'Rutina',
        kind: fd.get('kind'),
        desc: String(fd.get('desc') || '').trim(),
        exercises: parseExercises(String(fd.get('ex') || '')),
      };
      if (r) Object.assign(r, data);
      else state.routines.push({ id: uid(), ...data });
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
  $('#r-kind').addEventListener('change', (e) => {
    $('#r-gym').hidden = e.target.value === 'cardio';
    $('#r-cardio').hidden = e.target.value !== 'cardio';
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
      state = migrate({ ...defaultState(), ...data });
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
