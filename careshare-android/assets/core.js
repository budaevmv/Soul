'use strict';
/* =========================================================
   CareShare — ядро: утилиты, хранилище, связь с Android, логика
   ========================================================= */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const uid = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 8);
const pad = n => String(n).padStart(2, '0');
const dstr = d => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
const today = () => dstr(new Date());
const addDays = (s, n) => { const d = toDate(s); d.setDate(d.getDate() + n); return dstr(d); };
const toDate = s => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
const daysBetween = (a, b) => Math.round((toDate(b) - toDate(a)) / 864e5);
const nowTime = () => { const d = new Date(); return pad(d.getHours()) + ':' + pad(d.getMinutes()); };
const tmin = t => { const [h, m] = (t || '0:0').split(':').map(Number); return h * 60 + m; };
const plural = (n, a, b, c) => { n = Math.abs(n); const m10 = n % 10, m100 = n % 100; return m10 === 1 && m100 !== 11 ? a : (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? b : c); };
const MON = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'];
const WD = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'];
const fmtD = s => { if (!s) return ''; const d = toDate(s); return d.getDate() + ' ' + MON[d.getMonth()] + (d.getFullYear() !== new Date().getFullYear() ? ' ' + d.getFullYear() : ''); };
const fmtDS = s => { if (!s) return ''; const t = today(); if (s === t) return 'сегодня'; if (s === addDays(t, 1)) return 'завтра'; if (s === addDays(t, -1)) return 'вчера'; return fmtD(s); };
const fmtDW = s => { const d = toDate(s); return WD[d.getDay()] + ', ' + fmtD(s); };
function ago(t) {
  if (!t) return ''; const m = Math.round((Date.now() - t) / 60000);
  if (m < 1) return 'только что'; if (m < 60) return m + ' мин назад';
  const h = Math.round(m / 60); if (h < 24) return h + ' ч назад';
  const d = Math.round(h / 24); return d === 1 ? 'вчера' : d + ' ' + plural(d, 'день', 'дня', 'дней') + ' назад';
}
const age = dob => { if (!dob) return null; const b = toDate(dob), n = new Date(); let a = n.getFullYear() - b.getFullYear(); if (n.getMonth() < b.getMonth() || (n.getMonth() === b.getMonth() && n.getDate() < b.getDate())) a--; return a; };
const num = v => { if (v === '' || v == null) return null; const n = parseFloat(String(v).replace(',', '.')); return isNaN(n) ? null : n; };
const fmtNum = n => (Math.round(n * 100) / 100).toLocaleString('ru-RU');

/* ---------- иконки ---------- */
const I = {
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V20h5v-6h4v6h5V9.5"/>',
  pill: '<path d="m10.5 20.5-7-7a4.95 4.95 0 1 1 7-7l7 7a4.95 4.95 0 1 1-7 7Z"/><path d="m8.5 8.5 7 7"/>',
  health: '<path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/><path d="M3.22 12H9.5l.5-1 2 4.5 2-7 1.5 3.5h5.27"/>',
  tasks: '<path d="m9 11 3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>',
  users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z"/>',
  cal: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>',
  steth: '<path d="M4.8 2.3A.3.3 0 1 0 5 2H4a2 2 0 0 0-2 2v5a6 6 0 0 0 6 6 6 6 0 0 0 6-6V4a2 2 0 0 0-2-2h-1a.2.2 0 1 0 .3.3"/><path d="M8 15v1a6 6 0 0 0 6 6 6 6 0 0 0 6-6v-4"/><circle cx="20" cy="10" r="2"/>',
  flask: '<path d="M9 3h6M10 3v6.5L4.5 19A1.5 1.5 0 0 0 5.8 21h12.4a1.5 1.5 0 0 0 1.3-2L14 9.5V3"/><path d="M7 15h10"/>',
  activity: '<path d="M22 12h-4l-3 9L9 3l-3 9H2"/>',
  file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M8 13h8M8 17h5"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/>',
  msg: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
  phone: '<path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.8 19.8 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.8 19.8 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.13.96.36 1.9.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.9.34 1.85.57 2.81.7A2 2 0 0 1 22 16.92z"/>',
  mail: '<rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-10 6L2 7"/>',
  wallet: '<path d="M20 12V8H6a2 2 0 0 1 0-4h12v4"/><path d="M4 6v12a2 2 0 0 0 2 2h14v-4"/><path d="M18 12a2 2 0 0 0 0 4h4v-4z"/>',
  bell: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
  alert: '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><path d="M12 9v4M12 17h.01"/>',
  chev: '<path d="m9 18 6-6-6-6"/>',
  back: '<path d="m15 18-6-6 6-6"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>',
  camera: '<path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z"/><circle cx="12" cy="13" r="3"/>',
  share: '<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 13.5 6.8 4M15.4 6.5l-6.8 4"/>',
  print: '<path d="M6 9V2h12v7"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect x="6" y="14" width="12" height="8"/>',
  download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m7 10 5 5 5-5M12 15V3"/>',
  upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m17 8-5-5-5 5M12 3v12"/>',
  clock: '<circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>',
  pin: '<path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/>',
  edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>',
  trash: '<path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  box: '<path d="M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z"/><path d="m3.3 7 8.7 5 8.7-5M12 22V12"/>',
  link: '<path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>',
  smile: '<circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01"/>',
  send: '<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>',
  info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>',
  user: '<circle cx="12" cy="8" r="5"/><path d="M20 21a8 8 0 0 0-16 0"/>',
  refresh: '<path d="M3 12a9 9 0 0 1 15-6.7L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-15 6.7L3 16"/><path d="M8 16H3v5"/>',
  scale: '<path d="m16 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z"/><path d="m2 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z"/><path d="M7 21h10M12 3v18M3 7h2c2 0 5-1 7-2 2 1 5 2 7 2h2"/>',
  heart: '<path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/>',
  drop: '<path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"/>',
  thermo: '<path d="M14 4v10.54a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0Z"/>',
  lungs: '<circle cx="12" cy="12" r="10"/><path d="M12 7v5l3 3"/>',
  sparkle: '<path d="m12 3-1.9 5.8a2 2 0 0 1-1.3 1.3L3 12l5.8 1.9a2 2 0 0 1 1.3 1.3L12 21l1.9-5.8a2 2 0 0 1 1.3-1.3L21 12l-5.8-1.9a2 2 0 0 1-1.3-1.3Z"/>'
};
const ico = (n, cls = '') => `<svg class="i ${cls}" viewBox="0 0 24 24" aria-hidden="true">${I[n] || ''}</svg>`;
const LOGO = '<svg width="40" height="40" viewBox="0 0 26 26" aria-hidden="true"><path d="M13 23S2.5 16.4 2.5 9.3A5.3 5.3 0 0 1 13 6.7a5.3 5.3 0 0 1 10.5 2.6C23.5 16.4 13 23 13 23Z" fill="#C0583A"/><path d="M8 12h3l1.4-2.6 2 5 1.2-2.4H18" stroke="#fff" stroke-width="1.8" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>';

/* ---------- справочники ---------- */
const FORMS = [['tab', 'таблетка'], ['cap', 'капсула'], ['drops', 'капли'], ['inj', 'укол'], ['inh', 'ингаляция'], ['liq', 'сироп / раствор'], ['oint', 'мазь / пластырь'], ['other', 'другое']];
const FOOD = { any: 'не важно', before: 'до еды', with: 'во время еды', after: 'после еды' };
const TASK_CATS = { visit: ['Визит', 'users', 'violet'], shop: ['Покупки', 'box', 'amber'], meds: ['Лекарства', 'pill', 'green'], home: ['Дом', 'home', 'blue'], paper: ['Звонки и бумаги', 'file', 'violet'], ride: ['Поездка', 'pin', 'blue'], other: ['Другое', 'tasks', '']};
const EXP_CATS = { meds: 'Лекарства', medical: 'Медицина', care: 'Сиделка / уход', food: 'Продукты', ride: 'Транспорт', home: 'Дом и быт', other: 'Другое' };
const CLAIM_TYPES = { pa: 'Предварительное одобрение', claim: 'Заявление на оплату', appeal: 'Апелляция по отказу', enroll: 'Оформление страховки / льгот', other: 'Другое' };
const CLAIM_ST = { prep: ['Готовим', 'gray'], sent: ['Подано', 'blue'], review: ['На рассмотрении', 'amber'], ok: ['Одобрено', 'green'], denied: ['Отказ', 'red'], appeal: ['Апелляция', 'amber'] };
const ROLES = { main: 'Основной ухаживающий', helper: 'Помогает', viewer: 'Только просмотр' };
const MOODS = [['great', '😊', 'Отлично'], ['ok', '🙂', 'Хорошо'], ['meh', '😐', 'Так себе'], ['bad', '😟', 'Плохо'], ['alarm', '🚨', 'Тревожно']];
const COLORS = ['#C0583A', '#2F6CAD', '#2E7D56', '#7A4FB0', '#A86A12', '#0E7C86', '#B03A6F', '#5B6B7A'];
const DOCS = [['poa', 'Доверенность (финансовая)'], ['hcp', 'Доверенность на медицинские решения'], ['will', 'Завещание'], ['ad', 'Распоряжение о лечении (advance directive)'], ['ins', 'Страховые карты — копии'], ['id', 'Паспорт / ID — копии'], ['meds', 'Актуальный список лекарств'], ['contacts', 'Контакты врачей и экстренные номера'], ['accounts', 'Доступ к банку и оплате счетов'], ['keys', 'Запасной ключ от квартиры']];
const LAB_PRESETS = {
  'Глюкоза (натощак)': ['ммоль/л', 3.9, 5.5], 'Гликированный гемоглобин (HbA1c)': ['%', 4, 5.6], 'Холестерин общий': ['ммоль/л', 3, 5.2],
  'ЛПНП («плохой» холестерин)': ['ммоль/л', 0, 3], 'ЛПВП («хороший» холестерин)': ['ммоль/л', 1, 3], 'Триглицериды': ['ммоль/л', 0, 1.7],
  'Креатинин': ['мкмоль/л', 53, 115], 'СКФ (функция почек)': ['мл/мин', 60, 200], 'Гемоглобин': ['г/л', 120, 160], 'Лейкоциты': ['×10⁹/л', 4, 9],
  'Тромбоциты': ['×10⁹/л', 150, 400], 'ТТГ': ['мЕд/л', 0.4, 4], 'Калий': ['ммоль/л', 3.5, 5.1], 'Натрий': ['ммоль/л', 136, 145],
  'МНО': ['', 2, 3], 'Витамин D': ['нг/мл', 30, 100], 'Витамин B12': ['пг/мл', 200, 900], 'Ферритин': ['нг/мл', 20, 250], 'АЛТ': ['Ед/л', 0, 40], 'АСТ': ['Ед/л', 0, 40]
};
const VITALS = {
  bp: { n: 'Давление', u: 'мм рт. ст.', icon: 'heart', two: true, chk: (a, b) => a >= 160 || b >= 100 ? 'hi2' : a >= 140 || b >= 90 ? 'hi' : a < 90 || b < 60 ? 'lo' : '' },
  pulse: { n: 'Пульс', u: 'уд/мин', icon: 'activity', chk: a => a > 110 ? 'hi2' : a > 100 ? 'hi' : a < 50 ? 'lo' : '' },
  glucose: { n: 'Сахар крови', u: 'ммоль/л', icon: 'drop', chk: a => a > 13 ? 'hi2' : a > 10 ? 'hi' : a < 3.9 ? 'lo' : '' },
  weight: { n: 'Вес', u: 'кг', icon: 'scale', chk: () => '' },
  temp: { n: 'Температура', u: '°C', icon: 'thermo', chk: a => a >= 38.5 ? 'hi2' : a >= 37.3 ? 'hi' : a < 35.5 ? 'lo' : '' },
  spo2: { n: 'Сатурация (SpO₂)', u: '%', icon: 'lungs', chk: a => a < 90 ? 'lo2' : a < 94 ? 'lo' : '' }
};

/* ---------- хранилище ---------- */
const DB_KEY = 'careshare.v2';
const COLS = ['members', 'meds', 'appts', 'doctors', 'messages', 'labs', 'vitals', 'tasks', 'log', 'expenses', 'claims', 'docs', 'checkins'];
const LS = { get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }, set(k, v) { try { localStorage.setItem(k, v); return true; } catch (e) { return false; } }, del(k) { try { localStorage.removeItem(k); } catch (e) { } } };
function blank() {
  const d = { v: 2, patient: {}, settings: { currency: '$', sos: '112', remind: true, refillDays: 7 }, doses: {}, docState: {}, del: {}, onboarded: false };
  COLS.forEach(c => d[c] = []); return d;
}
let D = (() => { try { const r = JSON.parse(LS.get(DB_KEY) || 'null'); if (r && r.v === 2) { const b = blank(); Object.keys(b).forEach(k => { if (r[k] == null) r[k] = b[k]; }); r.settings = Object.assign(b.settings, r.settings); return r; } } catch (e) { } return blank(); })();
let ME = LS.get('careshare.me') || '';
const UI = Object.assign({ tab: 'home', health: 'visits', family: 'news', tf: 'all', meds: 'list' }, (() => { try { return JSON.parse(LS.get('careshare.ui') || '{}'); } catch (e) { return {}; } })());
const saveUI = () => LS.set('careshare.ui', JSON.stringify(UI));
const setMe = n => { ME = n; LS.set('careshare.me', n); };
const who = () => ME || 'Кто-то';

function pruneDoses() { const lim = addDays(today(), -90); Object.keys(D.doses).forEach(k => { if (k.slice(0, 10) < lim) delete D.doses[k]; }); }
let rTimer;
function save(silent) {
  pruneDoses();
  if (!LS.set(DB_KEY, JSON.stringify(D))) toast('Не удалось сохранить — память заполнена. Удалите старые фото.');
  clearTimeout(rTimer); rTimer = setTimeout(pushReminders, 400);
  if (!silent) render();
}
function byId(col, id) { return D[col].find(x => x.id === id); }
function upsert(col, obj) {
  obj.u = Date.now();
  if (!obj.id) { obj.id = uid(); obj.c = Date.now(); D[col].push(obj); }
  else { const i = D[col].findIndex(x => x.id === obj.id); if (i >= 0) D[col][i] = obj; else D[col].push(obj); }
  save(); return obj;
}
function removeItem(col, id) { D[col] = D[col].filter(x => x.id !== id); D.del[id] = Date.now(); save(); }
const memberNames = () => D.members.map(m => m.name);
const memberColor = n => { const i = D.members.findIndex(m => m.name === n); return COLORS[(i < 0 ? 7 : i) % COLORS.length]; };
const avatar = (n, cls = '') => `<span class="av ${cls}" style="background:${memberColor(n)}">${esc((n || '?').trim().charAt(0).toUpperCase())}</span>`;
function money(n) { const c = D.settings.currency || '$'; const s = (Math.round(Math.abs(n) * 100) / 100).toLocaleString('ru-RU', { minimumFractionDigits: 0, maximumFractionDigits: 2 }); return (n < 0 ? '−' : '') + (c === '₽' || c === '€' ? s + ' ' + c : c + s); }

/* ---------- фото (IndexedDB) ---------- */
const PH = {
  cache: {}, dbp: null,
  db() { if (!this.dbp) this.dbp = new Promise((res, rej) => { const r = indexedDB.open('careshare-photos', 1); r.onupgradeneeded = () => r.result.createObjectStore('p'); r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error); }); return this.dbp; },
  async put(id, data) { this.cache[id] = data; const db = await this.db(); return new Promise(r => { const t = db.transaction('p', 'readwrite'); t.objectStore('p').put(data, id); t.oncomplete = () => r(true); t.onerror = () => r(false); }); },
  async get(id) { if (this.cache[id]) return this.cache[id]; try { const db = await this.db(); return await new Promise(r => { const q = db.transaction('p').objectStore('p').get(id); q.onsuccess = () => { this.cache[id] = q.result; r(q.result); }; q.onerror = () => r(null); }); } catch (e) { return null; } },
  async del(id) { delete this.cache[id]; try { const db = await this.db(); db.transaction('p', 'readwrite').objectStore('p').delete(id); } catch (e) { } },
  async all() { const db = await this.db(); return new Promise(r => { const out = {}; const q = db.transaction('p').objectStore('p').openCursor(); q.onsuccess = () => { const c = q.result; if (c) { out[c.key] = c.value; c.continue(); } else r(out); }; q.onerror = () => r(out); }); }
};
function compressImage(file) {
  return new Promise((res, rej) => {
    const fr = new FileReader();
    fr.onload = () => { const img = new Image(); img.onload = () => { const M = 1400; let { width: w, height: h } = img; if (w > M || h > M) { const k = M / Math.max(w, h); w = Math.round(w * k); h = Math.round(h * k); } const c = document.createElement('canvas'); c.width = w; c.height = h; c.getContext('2d').drawImage(img, 0, 0, w, h); res(c.toDataURL('image/jpeg', 0.78)); }; img.onerror = rej; img.src = fr.result; };
    fr.onerror = rej; fr.readAsDataURL(file);
  });
}
async function fillPhotos(root = document) { for (const im of $$('img[data-ph]', root)) { if (im.src && !im.src.endsWith('#')) continue; const d = await PH.get(im.dataset.ph); if (d) im.src = d; else im.style.display = 'none'; } }
function pickFile(accept) {
  return new Promise(res => {
    const inp = $('#fileIn'); inp.value = ''; inp.accept = accept;
    inp.onchange = () => res(inp.files && inp.files[0] || null);
    inp.click();
  });
}
async function pickPhoto() { const f = await pickFile('image/*'); if (!f) return null; try { const data = await compressImage(f); const id = 'ph_' + uid(); await PH.put(id, data); return id; } catch (e) { toast('Не удалось открыть изображение'); return null; } }

/* ---------- связь с Android ---------- */
const N = window.Android || null;
const native = (fn, ...a) => { try { if (N && N[fn]) return N[fn](...a); } catch (e) { console.warn(fn, e); } return null; };
function openLink(url) { if (N && N.openUrl) native('openUrl', url); else window.open(url, '_blank'); }
function shareText(title, text) {
  if (N && N.share) return native('share', title, text);
  if (navigator.share) return navigator.share({ title, text }).catch(() => { });
  if (navigator.clipboard) navigator.clipboard.writeText(text).then(() => toast('Скопировано в буфер обмена'));
}
function downloadText(name, text, type = 'application/json') {
  if (N && N.shareFile) return native('shareFile', name, text);
  const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([text], { type })); a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 4000);
}
function printHtml(html) {
  if (N && N.printHtml) return native('printHtml', html);
  const w = window.open('', '_blank'); if (!w) return toast('Разрешите всплывающие окна'); w.document.write(html); w.document.close(); setTimeout(() => w.print(), 300);
}
function addToCalendar(title, loc, desc, start, end) {
  if (N && N.addToCalendar) return native('addToCalendar', title, loc || '', desc || '', String(start), String(end));
  const f = t => new Date(t).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
  downloadText('visit.ics', `BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nUID:${uid()}@careshare\r\nDTSTART:${f(start)}\r\nDTEND:${f(end)}\r\nSUMMARY:${title}\r\nLOCATION:${loc || ''}\r\nDESCRIPTION:${(desc || '').replace(/\n/g, '\\n')}\r\nEND:VEVENT\r\nEND:VCALENDAR`, 'text/calendar');
}

/* ---------- лекарства ---------- */
const activeMeds = () => D.meds.filter(m => m.active !== false);
const doseKey = (date, medId, time) => date + '_' + medId + '_' + time.replace(':', '');
const getDose = (date, medId, time) => D.doses[doseKey(date, medId, time)];
function setDose(date, med, time, taken, by) {
  const k = doseKey(date, med.id, time); const had = !!D.doses[k];
  if (taken && !had) { D.doses[k] = { by: by || who(), at: Date.now() }; if (med.stock != null) med.stock = Math.max(0, +(med.stock - (med.per || 1)).toFixed(2)); }
  if (!taken && had) { delete D.doses[k]; if (med.stock != null) med.stock = +(med.stock + (med.per || 1)).toFixed(2); }
  med.u = Date.now();
  native('markDose', date, med.id, time, !!taken);
}
function perDay(m) { return (m.per || 1) * (m.times || []).length; }
function daysLeft(m) { if (m.stock == null || !perDay(m)) return null; return Math.floor(m.stock / perDay(m)); }
function rxDays(m) { return m.rx ? daysBetween(today(), m.rx) : null; }
function adherence(date) {
  let exp = 0, got = 0;
  activeMeds().forEach(m => { if (m.c && dstr(new Date(m.c)) > date) return; (m.times || []).forEach(t => { exp++; if (getDose(date, m.id, t)) got++; }); });
  return { exp, got };
}
function todayDoses() {
  const t = today(), out = [];
  activeMeds().forEach(m => (m.times || []).forEach(time => out.push({ med: m, time, dose: getDose(t, m.id, time) })));
  return out.sort((a, b) => tmin(a.time) - tmin(b.time));
}

/* ---------- анализы и показатели ---------- */
function labFlag(l) { if (l.value == null) return ''; if (l.high != null && l.value > l.high) return 'hi'; if (l.low != null && l.value < l.low) return 'lo'; return ''; }
function labGroups() {
  const g = {}; D.labs.forEach(l => (g[l.name] = g[l.name] || []).push(l));
  return Object.entries(g).map(([name, arr]) => { arr.sort((a, b) => a.date < b.date ? -1 : a.date > b.date ? 1 : (a.c || 0) - (b.c || 0)); return { name, arr, last: arr[arr.length - 1] }; })
    .sort((a, b) => (b.last.date || '').localeCompare(a.last.date || ''));
}
function vitalsOf(type) { return D.vitals.filter(v => v.type === type).sort((a, b) => (a.at || 0) - (b.at || 0)); }
const vitalStr = v => v.type === 'bp' ? `${v.v1}/${v.v2}` : fmtNum(v.v1);

/* ---------- задачи ---------- */
function nextRepeat(due, rep) {
  const base = due || today(); const d = toDate(base);
  if (rep === 'day') d.setDate(d.getDate() + 1); else if (rep === 'week') d.setDate(d.getDate() + 7); else if (rep === 'month') d.setMonth(d.getMonth() + 1); else return null;
  return dstr(d);
}
function toggleTask(t) {
  t.done = !t.done; t.doneBy = t.done ? who() : ''; t.doneAt = t.done ? Date.now() : 0;
  if (t.done && t.repeat && t.repeat !== 'none' && !t.spawned) {
    t.spawned = true;
    const n = Object.assign({}, t, { id: null, done: false, doneBy: '', doneAt: 0, spawned: false, due: nextRepeat(t.due, t.repeat) });
    delete n.id; n.id = uid(); n.c = Date.now(); n.u = Date.now(); D.tasks.push(n);
  }
  upsert('tasks', t);
}

/* ---------- деньги ---------- */
function balances() {
  const names = memberNames(), b = {}; names.forEach(n => b[n] = 0);
  D.expenses.forEach(e => {
    const a = +e.amount || 0; if (!a || !e.payer) return;
    if (e.kind === 'settle') { b[e.payer] = (b[e.payer] || 0) + a; b[e.to] = (b[e.to] || 0) - a; return; }
    const sp = (e.split && e.split.length ? e.split : names); if (!sp.length) return;
    b[e.payer] = (b[e.payer] || 0) + a; sp.forEach(n => b[n] = (b[n] || 0) - a / sp.length);
  });
  return b;
}
function settleSuggest(b) {
  const cr = [], dr = []; Object.entries(b).forEach(([n, v]) => { v = Math.round(v * 100) / 100; if (v > 0.009) cr.push([n, v]); else if (v < -0.009) dr.push([n, -v]); });
  cr.sort((a, b) => b[1] - a[1]); dr.sort((a, b) => b[1] - a[1]); const out = []; let i = 0, j = 0;
  while (i < dr.length && j < cr.length) { const x = Math.min(dr[i][1], cr[j][1]); out.push({ from: dr[i][0], to: cr[j][0], amt: Math.round(x * 100) / 100 }); dr[i][1] -= x; cr[j][1] -= x; if (dr[i][1] < 0.01) i++; if (cr[j][1] < 0.01) j++; }
  return out;
}
function fairness(days = 30) {
  const since = Date.now() - days * 864e5, sinceD = dstr(new Date(since)), r = {};
  const g = n => r[n] = r[n] || { tasks: 0, min: 0, money: 0 };
  memberNames().forEach(g);
  D.tasks.forEach(t => { if (t.done && t.doneAt >= since && t.doneBy) { g(t.doneBy).tasks++; g(t.doneBy).min += +t.minutes || 30; } });
  D.appts.forEach(a => { if (a.who && a.date >= sinceD && a.date <= today()) { g(a.who).min += 120; } });
  D.expenses.forEach(e => { if (e.kind !== 'settle' && e.payer && (e.date || '') >= sinceD) g(e.payer).money += +e.amount || 0; });
  return r;
}

/* ---------- визиты ---------- */
const apptStart = a => { const [y, m, d] = a.date.split('-').map(Number); const [h, mi] = (a.time || '09:00').split(':').map(Number); return new Date(y, m - 1, d, h, mi).getTime(); };
const upcomingAppts = () => D.appts.filter(a => !a.done && apptStart(a) > Date.now() - 2 * 3600e3).sort((a, b) => apptStart(a) - apptStart(b));
const pastAppts = () => D.appts.filter(a => a.done || apptStart(a) <= Date.now() - 2 * 3600e3).sort((a, b) => apptStart(b) - apptStart(a));
const shortName = n => { const p = String(n || '').trim().split(/\s+/); return p.length >= 3 ? `${p[0]} ${p[1][0]}. ${p[2][0]}.` : p.length === 2 ? `${p[0]} ${p[1][0]}.` : p[0] || ''; };
const docName = id => { const d = byId('doctors', id); return d ? (d.spec ? d.spec + ' ' : '') + shortName(d.name) : ''; };
function parseICS(text) {
  const lines = text.replace(/\r\n[ \t]/g, '').replace(/\n[ \t]/g, '').split(/\r?\n/); const evs = []; let cur = null;
  const unesc = s => s.replace(/\\n/gi, '\n').replace(/\\,/g, ',').replace(/\\;/g, ';').replace(/\\\\/g, '\\');
  for (const ln of lines) {
    if (ln === 'BEGIN:VEVENT') cur = {}; else if (ln === 'END:VEVENT') { if (cur && cur.start) evs.push(cur); cur = null; }
    else if (cur) {
      const i = ln.indexOf(':'); if (i < 0) continue; const key = ln.slice(0, i), val = ln.slice(i + 1), name = key.split(';')[0];
      if (name === 'DTSTART') { const m = val.match(/^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})?(Z)?)?/); if (m) { let d; if (m[4] == null) d = new Date(+m[1], m[2] - 1, +m[3], 9, 0); else if (m[7]) d = new Date(Date.UTC(+m[1], m[2] - 1, +m[3], +m[4], +m[5])); else d = new Date(+m[1], m[2] - 1, +m[3], +m[4], +m[5]); cur.start = d; } }
      else if (name === 'SUMMARY') cur.title = unesc(val); else if (name === 'LOCATION') cur.loc = unesc(val); else if (name === 'DESCRIPTION') cur.desc = unesc(val);
    }
  }
  return evs;
}

/* ---------- самочувствие ---------- */
const weekStart = () => { const d = new Date(); d.setDate(d.getDate() - ((d.getDay() + 6) % 7)); return dstr(d); };
const myCheckin = () => D.checkins.find(c => c.who === ME && c.date >= weekStart());
function latestCheckins() { const m = {}; D.checkins.forEach(c => { if (!m[c.who] || c.date > m[c.who].date) m[c.who] = c; }); return m; }

/* ---------- предупреждения ---------- */
function alerts() {
  const out = [], t = today(), nowM = tmin(nowTime()), rd = +D.settings.refillDays || 7;
  todayDoses().forEach(d => { if (!d.dose && nowM - tmin(d.time) > 60) out.push({ lv: 'red', ic: 'pill', t: `Пропущен приём: ${d.med.name}`, m: `${d.time} · не отмечено`, act: 'tab', arg: 'home', key: 'miss' }); });
  activeMeds().forEach(m => {
    const r = rxDays(m);
    if (r != null && r <= 14) out.push({ lv: r <= 3 ? 'red' : 'amber', ic: 'file', t: r < 0 ? `Рецепт истёк: ${m.name}` : `Рецепт заканчивается: ${m.name}`, m: r < 0 ? `${fmtD(m.rx)} — нужен новый рецепт` : r === 0 ? 'Сегодня последний день' : `Через ${r} ${plural(r, 'день', 'дня', 'дней')} · ${fmtD(m.rx)}`, act: 'med', arg: m.id });
    const dl = daysLeft(m);
    if (dl != null && dl <= rd) out.push({ lv: dl <= 2 ? 'red' : 'amber', ic: 'box', t: `Заканчивается: ${m.name}`, m: dl === 0 ? 'Запас закончился' : `Осталось на ${dl} ${plural(dl, 'день', 'дня', 'дней')} (${fmtNum(m.stock)} шт.)`, act: 'med', arg: m.id });
  });
  upcomingAppts().forEach(a => { const d = daysBetween(t, a.date); if (d <= 1) out.push({ lv: 'blue', ic: 'cal', t: `${d === 0 ? 'Сегодня' : 'Завтра'} визит${a.time ? ' в ' + a.time : ''}`, m: [docName(a.doctorId), a.clinic].filter(Boolean).join(' · ') || a.reason || '', act: 'appt', arg: a.id }); });
  labGroups().forEach(g => { const f = labFlag(g.last); if (f && daysBetween(g.last.date, t) <= 60) out.push({ lv: 'amber', ic: 'flask', t: `${g.name}: ${f === 'hi' ? 'выше' : 'ниже'} нормы`, m: `${fmtNum(g.last.value)} ${g.last.unit || ''} · ${fmtDS(g.last.date)}`, act: 'lab', arg: g.name }); });
  Object.keys(VITALS).forEach(k => { const arr = vitalsOf(k); const v = arr[arr.length - 1]; if (!v || Date.now() - v.at > 3 * 864e5) return; const f = VITALS[k].chk(v.v1, v.v2); if (f) out.push({ lv: f.endsWith('2') ? 'red' : 'amber', ic: VITALS[k].icon, t: `${VITALS[k].n}: ${f.startsWith('hi') ? 'высокое' : 'низкое'} значение`, m: `${vitalStr(v)} ${VITALS[k].u} · ${ago(v.at)}`, act: 'vital', arg: k }); });
  const late = D.tasks.filter(x => !x.done && x.due && x.due < t).length;
  if (late) out.push({ lv: 'amber', ic: 'tasks', t: `Просрочено задач: ${late}`, m: 'Распределите или перенесите', act: 'tab', arg: 'tasks' });
  D.claims.forEach(c => { if (['ok', 'denied'].includes(c.status) || !c.deadline) return; const d = daysBetween(t, c.deadline); if (d <= 7) out.push({ lv: d <= 2 ? 'red' : 'amber', ic: 'shield', t: `Срок по страховке: ${c.title}`, m: d < 0 ? 'Срок прошёл' : d === 0 ? 'Сегодня' : `Через ${d} ${plural(d, 'день', 'дня', 'дней')}`, act: 'claim', arg: c.id }); });
  Object.values(latestCheckins()).forEach(c => { if (c.score <= 2 && daysBetween(c.date, t) <= 14) out.push({ lv: 'blue', ic: 'smile', t: c.who === ME ? 'Вам сейчас тяжело' : `${c.who}: тяжело (${c.score}/5)`, m: c.who === ME ? 'Попросите семью взять часть задач' : 'Возьмите часть задач на себя', act: 'tab', arg: 'tasks' }); });
  const rank = { red: 0, amber: 1, blue: 2, green: 3 };
  return out.sort((a, b) => rank[a.lv] - rank[b.lv]);
}

/* ---------- напоминания (уведомления Android) ---------- */
function at10(dateStr, h = 10, m = 0) { const d = toDate(dateStr); d.setHours(h, m, 0, 0); return d.getTime(); }
function nextMorning() { const d = new Date(); if (d.getHours() >= 10) d.setDate(d.getDate() + 1); d.setHours(10, 0, 0, 0); return d.getTime(); }
function reminderSpecs() {
  const s = [], now = Date.now(), t = today(), rd = +D.settings.refillDays || 7;
  activeMeds().forEach(m => (m.times || []).forEach(time => {
    const [h, mi] = time.split(':').map(Number);
    const extra = [m.per > 1 ? m.per + ' шт.' : '', m.food && m.food !== 'any' ? FOOD[m.food] : ''].filter(Boolean).join(' · ');
    s.push({ key: `med:${m.id}:${time}`, kind: 'daily', h, m: mi, title: `💊 ${m.name}${m.dose ? ' ' + m.dose : ''}`, text: `Время приёма ${time}${extra ? ' · ' + extra : ''}`, dose: { medId: m.id, time } });
    const mm = h * 60 + mi + 30;
    s.push({ key: `miss:${m.id}:${time}`, kind: 'daily', h: Math.floor(mm / 60) % 24, m: mm % 60, off: mm >= 1440 ? 1 : 0, title: `Не отмечен приём: ${m.name}`, text: `Приём в ${time} не отмечен. Проверьте, дано ли лекарство.`, check: { medId: m.id, time } });
  }));
  activeMeds().forEach(m => {
    if (m.rx) [14, 3, 0].forEach(d => { const at = at10(addDays(m.rx, -d)); if (at > now) s.push({ key: `rx:${m.id}:${d}`, kind: 'once', at, title: `Рецепт: ${m.name}`, text: d ? `Рецепт истекает через ${d} ${plural(d, 'день', 'дня', 'дней')} (${fmtD(m.rx)}). Запишитесь к врачу за новым.` : 'Рецепт истекает сегодня. Нужен новый рецепт.' }); });
    const dl = daysLeft(m);
    if (dl != null) { let at = at10(addDays(t, dl - rd)); if (at <= now) at = nextMorning(); s.push({ key: `refill:${m.id}`, kind: 'once', at, title: `Пора пополнить: ${m.name}`, text: dl <= 0 ? 'Запас закончился.' : `Осталось примерно на ${dl} ${plural(dl, 'день', 'дня', 'дней')}. Закажите в аптеке.` }); }
  });
  upcomingAppts().forEach(a => {
    const st = apptStart(a); const where = [docName(a.doctorId), a.clinic].filter(Boolean).join(', ');
    (a.remind || [1440, 120]).forEach(off => { const at = st - off * 60000; if (at > now) s.push({ key: `appt:${a.id}:${off}`, kind: 'once', at, title: off >= 1440 ? (off === 1440 ? '📅 Завтра визит к врачу' : `📅 Через ${off / 1440} дн. визит к врачу`) : `📅 Через ${off >= 60 ? off / 60 + ' ч' : off + ' мин'} визит к врачу`, text: `${a.time || ''} ${where}${a.reason ? ' — ' + a.reason : ''}`.trim() }); });
  });
  D.tasks.forEach(x => { if (x.done || !x.due) return; if (x.assignee && ME && x.assignee !== ME) return; const at = at10(x.due, 9); if (at > now) s.push({ key: `task:${x.id}`, kind: 'once', at, title: '✔️ Задача на сегодня', text: x.title }); });
  D.claims.forEach(c => { if (!c.deadline || ['ok', 'denied'].includes(c.status)) return; [3, 0].forEach(d => { const at = at10(addDays(c.deadline, -d)); if (at > now) s.push({ key: `claim:${c.id}:${d}`, kind: 'once', at, title: '🛡 Срок по страховке', text: `${c.title} — ${d ? 'через ' + d + ' дн.' : 'сегодня'}` }); }); });
  if (ME && !myCheckin()) { const d = new Date(); d.setDate(d.getDate() + ((7 - d.getDay()) % 7)); d.setHours(19, 0, 0, 0); if (d.getTime() > now) s.push({ key: 'checkin', kind: 'once', at: d.getTime(), title: 'Как вы сами?', text: 'Отметьте своё самочувствие за неделю — это займёт 5 секунд.' }); }
  return s.slice(0, 400);
}
function pushReminders() {
  if (!N || !N.setReminders) return;
  native('setReminders', JSON.stringify(reminderSpecs()), D.settings.remind !== false);
}
function pullNativeActions() {
  const raw = native('takePending'); if (!raw) return;
  let arr = []; try { arr = JSON.parse(raw); } catch (e) { }
  let ch = false;
  arr.forEach(p => { const m = byId('meds', p.medId); if (m && !getDose(p.date, m.id, p.time)) { setDose(p.date, m, p.time, true, (ME || 'Кто-то') + ' (из уведомления)'); ch = true; } });
  if (ch) save();
}

/* ---------- обмен данными ---------- */
async function exportData() {
  const photos = await PH.all().catch(() => ({}));
  const payload = { app: 'careshare', v: 2, exported: Date.now(), by: ME, data: D, photos };
  downloadText(`careshare-${today()}.json`, JSON.stringify(payload));
}
async function importData(text) {
  let p; try { p = JSON.parse(text); } catch (e) { return toast('Это не файл CareShare'); }
  if (!p || p.app !== 'careshare' || !p.data) return toast('Это не файл CareShare');
  const inc = p.data;
  COLS.forEach(c => { const map = new Map(D[c].map(x => [x.id, x])); (inc[c] || []).forEach(x => { const cur = map.get(x.id); if (!cur || (x.u || 0) > (cur.u || 0)) map.set(x.id, x); }); D[c] = [...map.values()]; });
  Object.entries(inc.del || {}).forEach(([k, v]) => { if (!D.del[k] || D.del[k] < v) D.del[k] = v; });
  COLS.forEach(c => D[c] = D[c].filter(x => !(D.del[x.id] && D.del[x.id] >= (x.u || 0))));
  Object.entries(inc.doses || {}).forEach(([k, v]) => { if (!D.doses[k]) D.doses[k] = v; });
  Object.entries(inc.docState || {}).forEach(([k, v]) => { if (v) D.docState[k] = v; });
  if (inc.patient && (inc.patient.u || 0) > (D.patient.u || 0)) D.patient = inc.patient;
  D.onboarded = true;
  for (const [k, v] of Object.entries(p.photos || {})) await PH.put(k, v);
  save(); toast(`Данные объединены${p.by ? ' (от ' + p.by + ')' : ''}`);
}

/* ---------- сводка и экстренный лист ---------- */
function summaryText() {
  const t = today(), p = D.patient, ds = todayDoses(), got = ds.filter(d => d.dose).length;
  const L = [`CareShare · ${p.name || 'Близкий'} · ${fmtDW(t)}`];
  if (ds.length) L.push(`💊 Лекарства: ${got} из ${ds.length}${got < ds.length ? ' (не отмечено: ' + ds.filter(d => !d.dose).map(d => d.med.name + ' ' + d.time).join(', ') + ')' : ''}`);
  const doneT = D.tasks.filter(x => x.done && x.doneAt && dstr(new Date(x.doneAt)) === t), openT = D.tasks.filter(x => !x.done && x.due && x.due <= t);
  if (doneT.length || openT.length) L.push(`✔️ Задачи: сделано ${doneT.length}${openT.length ? ', осталось ' + openT.length : ''}`);
  const na = upcomingAppts()[0]; if (na) L.push(`📅 Ближайший визит: ${fmtDS(na.date)}${na.time ? ' ' + na.time : ''} — ${docName(na.doctorId) || na.clinic || na.reason || ''}`);
  Object.keys(VITALS).forEach(k => { const a = vitalsOf(k), v = a[a.length - 1]; if (v && dstr(new Date(v.at)) === t) L.push(`❤️ ${VITALS[k].n}: ${vitalStr(v)} ${VITALS[k].u}`); });
  const post = D.log.slice().sort((a, b) => b.at - a.at)[0]; if (post && Date.now() - post.at < 2 * 864e5) L.push(`📝 ${post.by}: «${post.text.slice(0, 200)}»`);
  const al = alerts().filter(a => a.lv === 'red'); if (al.length) L.push('⚠️ ' + al.map(a => a.t).join('; '));
  return L.join('\n');
}
function emergencyHtml() {
  const p = D.patient, e = esc, a = age(p.dob);
  const meds = activeMeds().map(m => `<tr><td><b>${e(m.name)}</b></td><td>${e(m.dose || '')}</td><td>${e((m.times || []).join(', '))}</td><td>${e(m.notes || '')}</td></tr>`).join('');
  const docs = D.doctors.map(d => `<tr><td><b>${e(d.name)}</b></td><td>${e(d.spec || '')}</td><td>${e(d.phone || '')}</td><td>${e(d.clinic || '')}</td></tr>`).join('');
  const fam = D.members.map(m => `<tr><td><b>${e(m.name)}</b></td><td>${e(m.rel || '')}</td><td>${e(m.phone || '')}</td></tr>`).join('');
  return `<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8"><title>Экстренная информация — ${e(p.name || '')}</title><style>
  body{font-family:Roboto,Arial,sans-serif;color:#111;margin:28px;font-size:13px}h1{font-size:22px;margin:0 0 4px}h2{font-size:15px;margin:18px 0 6px;border-bottom:2px solid #C0583A;padding-bottom:3px;color:#C0583A}
  table{width:100%;border-collapse:collapse}td,th{border:1px solid #ddd;padding:6px 8px;text-align:left;vertical-align:top}th{background:#f6f2ee}
  .al{background:#FBE3DE;border:2px solid #C2412D;padding:10px 12px;border-radius:8px;font-size:15px;margin-top:10px}.g{display:grid;grid-template-columns:1fr 1fr;gap:4px 20px}.muted{color:#666}</style></head><body>
  <h1>Экстренная информация: ${e(p.name || '—')}</h1><div class="muted">Сформировано в CareShare · ${fmtD(today())}</div>
  <div class="g" style="margin-top:12px"><div><b>Дата рождения:</b> ${p.dob ? fmtD(p.dob) + (a != null ? ' (' + a + ' ' + plural(a, 'год', 'года', 'лет') + ')' : '') : '—'}</div><div><b>Группа крови:</b> ${e(p.blood || '—')}</div>
  <div><b>Страховка:</b> ${e(p.insPlan || '—')}</div><div><b>Номер полиса:</b> ${e(p.insId || '—')}</div></div>
  <div class="al"><b>Аллергии:</b> ${e(p.allergies || 'нет данных')}</div>
  <h2>Диагнозы</h2><div style="white-space:pre-wrap">${e(p.conditions || '—')}</div>
  <h2>Лекарства</h2><table><tr><th>Препарат</th><th>Доза</th><th>Время</th><th>Примечания</th></tr>${meds || '<tr><td colspan="4">—</td></tr>'}</table>
  <h2>Врачи</h2><table><tr><th>Врач</th><th>Специальность</th><th>Телефон</th><th>Клиника</th></tr>${docs || '<tr><td colspan="4">—</td></tr>'}</table>
  <h2>Контакты семьи</h2><table><tr><th>Имя</th><th>Кем приходится</th><th>Телефон</th></tr>${fam || '<tr><td colspan="3">—</td></tr>'}</table>
  ${p.emergency ? `<h2>Дополнительные контакты</h2><div style="white-space:pre-wrap">${e(p.emergency)}</div>` : ''}
  ${p.notes ? `<h2>Важно знать</h2><div style="white-space:pre-wrap">${e(p.notes)}</div>` : ''}
  </body></html>`;
}

/* ---------- графики ---------- */
function spark(vals, opt = {}) {
  vals = vals.filter(v => v != null); if (vals.length < 2) return '';
  const w = 120, h = 36, lo = Math.min(...vals), hi = Math.max(...vals), r = hi - lo || 1;
  const pts = vals.map((v, i) => `${(i / (vals.length - 1) * (w - 6) + 3).toFixed(1)},${(h - 4 - (v - lo) / r * (h - 8)).toFixed(1)}`).join(' ');
  return `<svg class="sp" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none"><polyline points="${pts}" fill="none" stroke="${opt.color || '#C0583A'}" stroke-width="2.2" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke"/></svg>`;
}
function chart(series, opt = {}) {
  // series: [{pts:[{x:ms,y}], color}]; opt: {low, high}
  const all = series.flatMap(s => s.pts); if (!all.length) return '';
  const W = 340, H = 170, L = 34, R = 10, T = 12, B = 24;
  let ys = all.map(p => p.y); if (opt.low != null) ys.push(opt.low); if (opt.high != null) ys.push(opt.high);
  let lo = Math.min(...ys), hi = Math.max(...ys); const pd = (hi - lo) * 0.12 || 1; lo -= pd; hi += pd;
  const xs = all.map(p => p.x); let x0 = Math.min(...xs), x1 = Math.max(...xs); if (x1 === x0) { x0 -= 864e5; x1 += 864e5; }
  const X = x => L + (x - x0) / (x1 - x0) * (W - L - R), Y = y => T + (1 - (y - lo) / (hi - lo)) * (H - T - B);
  let g = '';
  if (opt.low != null || opt.high != null) { const a = Y(opt.high != null ? opt.high : hi), b = Y(opt.low != null ? opt.low : lo); g += `<rect x="${L}" y="${a}" width="${W - L - R}" height="${Math.max(0, b - a)}" fill="#E2F2E9"/>`; }
  for (let i = 0; i <= 3; i++) { const v = lo + (hi - lo) * i / 3, y = Y(v); g += `<line x1="${L}" x2="${W - R}" y1="${y}" y2="${y}" stroke="#ECE3DA"/><text x="${L - 5}" y="${y + 4}" font-size="10" text-anchor="end" fill="#7C7069">${fmtNum(+v.toFixed(v < 10 ? 1 : 0))}</text>`; }
  const fd = x => { const d = new Date(x); return d.getDate() + ' ' + MON[d.getMonth()]; };
  g += `<text x="${L}" y="${H - 6}" font-size="10" fill="#7C7069">${fd(x0)}</text><text x="${W - R}" y="${H - 6}" font-size="10" text-anchor="end" fill="#7C7069">${fd(x1)}</text>`;
  series.forEach(s => {
    const p = s.pts.slice().sort((a, b) => a.x - b.x);
    g += `<polyline points="${p.map(q => X(q.x).toFixed(1) + ',' + Y(q.y).toFixed(1)).join(' ')}" fill="none" stroke="${s.color}" stroke-width="2.4" stroke-linejoin="round"/>`;
    p.forEach(q => { const bad = (opt.high != null && q.y > opt.high) || (opt.low != null && q.y < opt.low); g += `<circle cx="${X(q.x).toFixed(1)}" cy="${Y(q.y).toFixed(1)}" r="4" fill="${bad ? '#C2412D' : s.color}" stroke="#fff" stroke-width="1.5"/>`; });
  });
  return `<svg class="chart" viewBox="0 0 ${W} ${H}">${g}</svg>`;
}

/* ---------- уведомления в приложении ---------- */
let tt; function toast(m) { const t = $('#toast'); t.textContent = m; t.classList.add('on'); clearTimeout(tt); tt = setTimeout(() => t.classList.remove('on'), 2600); }
