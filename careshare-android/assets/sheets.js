'use strict';
/* =========================================================
   CareShare — окна, формы, действия, запуск
   ========================================================= */
const SHEETS = [];
function sheet({ title, body, foot = '', refresh = null, onClose = null }) {
  const scrim = document.createElement('div'); scrim.className = 'scrim';
  const el = document.createElement('div'); el.className = 'sheet'; el.setAttribute('role', 'dialog');
  el.innerHTML = `<div class="grab"></div><div class="sh"><h2>${title}</h2><button class="icon-btn" data-close aria-label="Закрыть">${ico('x')}</button></div><div class="sb">${typeof body === 'function' ? body() : body}</div>${foot ? `<div class="sf">${foot}</div>` : ''}`;
  document.body.append(scrim, el);
  requestAnimationFrame(() => { scrim.classList.add('on'); el.classList.add('on'); });
  scrim.onclick = () => closeSheet(); el.querySelector('[data-close]').onclick = () => closeSheet();
  const s = { el, scrim, onClose, refresh: refresh ? () => { const b = el.querySelector('.sb'), st = b.scrollTop; b.innerHTML = refresh(); fillPhotos(el); b.scrollTop = st; } : null };
  SHEETS.push(s); document.body.style.overflow = 'hidden'; fillPhotos(el);
  return el;
}
function closeSheet() {
  const s = SHEETS.pop(); if (!s) return;
  s.el.classList.remove('on'); s.scrim.classList.remove('on');
  setTimeout(() => { s.el.remove(); s.scrim.remove(); }, 260);
  if (!SHEETS.length) document.body.style.overflow = '';
  s.onClose && s.onClose();
}
function ask(text, ok = 'Удалить', danger = true) {
  return new Promise(res => {
    let done = false;
    const el = sheet({ title: 'Подтвердите', body: `<p style="margin:0 0 6px;font-size:16px">${text}</p>`, foot: `<div class="btns"><button class="btn ghost" data-no>Отмена</button><button class="btn ${danger ? 'danger' : ''}" data-yes>${ok}</button></div>`, onClose: () => { if (!done) res(false); } });
    el.querySelector('[data-yes]').onclick = () => { done = true; closeSheet(); res(true); };
    el.querySelector('[data-no]').onclick = () => { done = true; closeSheet(); res(false); };
  });
}

/* ---------- конструктор форм ---------- */
const photoBox = (f, val) => `<div class="phbox" data-k="${f.k}"><input type="hidden" name="${f.k}" value="${esc(val)}">${val ? `<img data-ph="${esc(val)}" src="" alt="">` : ''}<button type="button" class="btn sm ghost" data-photo>${ico('camera', 's')}${val ? 'Заменить' : 'Добавить фото'}</button>${val ? '<button type="button" class="btn sm ghost" data-photox>Убрать</button>' : ''}</div>`;
function fieldHtml(f, v) {
  const id = 'f_' + f.k, val = v == null ? (f.def != null ? f.def : '') : v, ph = f.ph ? ` placeholder="${esc(f.ph)}"` : '';
  let inp = '';
  switch (f.type) {
    case 'textarea': inp = `<textarea class="inp" id="${id}" name="${f.k}"${ph} maxlength="${f.max || 2000}">${esc(val)}</textarea>`; break;
    case 'select': inp = `<select class="inp" id="${id}" name="${f.k}">${(typeof f.options === 'function' ? f.options() : f.options).map(([k, l]) => `<option value="${esc(k)}" ${String(k) === String(val) ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select>`; break;
    case 'pick': case 'multi': { const opts = typeof f.options === 'function' ? f.options() : f.options, sel = f.type === 'multi' ? (Array.isArray(val) ? val : []) : [val]; inp = `<div class="pick" data-k="${f.k}" data-multi="${f.type === 'multi' ? 1 : ''}">${opts.map(([k, l]) => `<button type="button" data-v="${esc(k)}" class="${sel.map(String).includes(String(k)) ? 'on' : ''}">${l}</button>`).join('') || '<span class="small muted">Сначала добавьте членов семьи</span>'}</div>`; break; }
    case 'toggle': inp = `<label class="tgl"><span>${f.text || ''}</span><span class="sw"><input type="checkbox" name="${f.k}" ${val ? 'checked' : ''}><span></span></span></label>`; break;
    case 'photo': inp = photoBox(f, val); break;
    case 'html': return `<div class="fld">${f.html}</div>`;
    case 'number': inp = `<input class="inp" id="${id}" name="${f.k}" type="text" inputmode="decimal" value="${esc(val)}"${ph}>`; break;
    default: inp = `<input class="inp" id="${id}" name="${f.k}" type="${f.type || 'text'}" value="${esc(val)}"${ph}${f.list ? ` list="${f.list}"` : ''}${f.type === 'tel' ? ' inputmode="tel"' : ''} maxlength="${f.max || 200}">`;
  }
  return `<div class="fld">${f.label && f.type !== 'toggle' ? `<label for="${id}">${f.label}${f.req ? ' *' : ''}</label>` : ''}${inp}${f.help ? `<div class="help">${f.help}</div>` : ''}</div>`;
}
function form(o) {
  const v = o.values || {};
  let body = '', i = 0; const fs = o.fields.filter(Boolean);
  while (i < fs.length) { if (fs[i].half && fs[i + 1] && fs[i + 1].half) { body += `<div class="fgrid">${fieldHtml(fs[i], v[fs[i].k])}${fieldHtml(fs[i + 1], v[fs[i + 1].k])}</div>`; i += 2; } else { body += fieldHtml(fs[i], v[fs[i].k]); i++; } }
  if (o.extra) body += o.extra;
  const foot = `<div class="btns">${o.onDelete ? `<button class="btn danger" style="flex:0 0 auto" data-fdel aria-label="Удалить">${ico('trash', 's')}</button>` : ''}<button class="btn" data-fsave>${o.saveText || 'Сохранить'}</button></div>`;
  const el = sheet({ title: o.title, body: `<form onsubmit="return false">${body}</form>`, foot });
  el.addEventListener('click', async e => {
    const pb = e.target.closest('.pick button'); if (pb) { const box = pb.parentElement; if (!box.dataset.multi) $$('button', box).forEach(b => b.classList.remove('on')); pb.classList.toggle('on'); if (o.onPick) o.onPick(box.dataset.k, pb.dataset.v, el); return; }
    const ph = e.target.closest('[data-photo]'); if (ph) { const box = ph.closest('.phbox'); const id = await pickPhoto(); if (id) { const f = fs.find(x => x.k === box.dataset.k); box.outerHTML = photoBox(f, id); fillPhotos(el); } return; }
    const px = e.target.closest('[data-photox]'); if (px) { const box = px.closest('.phbox'); const f = fs.find(x => x.k === box.dataset.k); box.outerHTML = photoBox(f, ''); }
  });
  if (o.onInput) el.addEventListener('input', e => o.onInput(e.target, el));
  el.querySelector('[data-fsave]').onclick = async () => {
    const out = {};
    for (const f of fs) {
      if (f.type === 'html') continue;
      if (f.type === 'pick' || f.type === 'multi') { const on = $$(`.pick[data-k="${f.k}"] button.on`, el).map(b => b.dataset.v); out[f.k] = f.type === 'multi' ? on : (on[0] || ''); }
      else if (f.type === 'toggle') out[f.k] = !!el.querySelector(`[name="${f.k}"]`).checked;
      else { const x = el.querySelector(`[name="${f.k}"]`); out[f.k] = x ? x.value.trim() : ''; }
      if (f.type === 'number') out[f.k] = num(out[f.k]);
      if (f.req && (out[f.k] === '' || out[f.k] == null || (Array.isArray(out[f.k]) && !out[f.k].length))) { toast(`Заполните поле «${f.label}»`); const x = el.querySelector(`[name="${f.k}"]`); x && x.focus && x.focus(); return; }
    }
    const r = await o.onSave(out); if (r !== false) closeSheet();
  };
  if (o.onDelete) el.querySelector('[data-fdel]').onclick = async () => { if (await ask(o.delText || 'Удалить безвозвратно?')) { closeSheet(); o.onDelete(); } };
  return el;
}
const memberOpts = (any = 'Любой') => [['', any], ...memberNames().map(n => [n, n])];
const doctorOpts = () => [['', '—'], ...D.doctors.map(d => [d.id, d.name + (d.spec ? ' · ' + d.spec : '')])];

/* ---------- формы сущностей ---------- */
function medForm(id) {
  const m = id ? byId('meds', id) : null;
  form({
    title: m ? 'Лекарство' : 'Новое лекарство', values: m ? Object.assign({}, m, { times: (m.times || []).join(', '), active: m.active !== false }) : { per: 1, food: 'any', form: 'tab', active: true },
    fields: [
      { k: 'name', label: 'Название', req: true, ph: 'Метформин' },
      { k: 'dose', label: 'Дозировка', ph: '500 мг', half: true }, { k: 'form', label: 'Форма', type: 'select', options: FORMS, half: true },
      { k: 'times', label: 'Время приёма', ph: '08:00, 20:00', help: 'Через запятую. Пусто — «по необходимости», без напоминаний.' },
      { k: 'per', label: 'Штук за приём', type: 'number', half: true }, { k: 'food', label: 'Приём пищи', type: 'select', options: Object.entries(FOOD), half: true },
      { k: 'stock', label: 'Сейчас в наличии, шт.', type: 'number', ph: '30', help: 'Уменьшается при каждой отметке приёма — придёт напоминание пополнить.' },
      { k: 'rx', label: 'Рецепт действует до', type: 'date', half: true }, { k: 'refills', label: 'Осталось продлений', type: 'number', half: true },
      { k: 'doctorId', label: 'Кто назначил', type: 'select', options: doctorOpts }, { k: 'pharmacy', label: 'Аптека', ph: 'Аптека на Ленина, 5' },
      { k: 'notes', label: 'Заметки', type: 'textarea', ph: 'Не сочетать с грейпфрутом; измерить давление до приёма…' },
      m ? { k: 'active', type: 'toggle', text: 'Принимает сейчас' } : null
    ],
    onSave: v => {
      const times = String(v.times || '').split(/[,;\s]+/).filter(Boolean).map(s => { const x = s.match(/^(\d{1,2})(?:[:.]?(\d{2}))?$/); if (!x) return null; const h = +x[1], mi = +(x[2] || 0); return h < 24 && mi < 60 ? pad(h) + ':' + pad(mi) : null; });
      if (times.includes(null)) { toast('Время укажите так: 08:00, 20:00'); return false; }
      const o = Object.assign(m || {}, v, { times: [...new Set(times)].sort(), per: v.per || 1, active: m ? v.active : true });
      upsert('meds', o); toast(m ? 'Сохранено' : 'Лекарство добавлено');
    },
    onDelete: m ? () => removeItem('meds', m.id) : null, delText: 'Удалить лекарство и его историю? Если его просто отменили — снимите галочку «Принимает сейчас».'
  });
}
function apptForm(id, pre = {}) {
  const a = id ? byId('appts', id) : null;
  const R = [['1440,120', 'За день и за 2 часа'], ['4320,1440,120', 'За 3 дня, за день и за 2 часа'], ['1440', 'За день'], ['120', 'За 2 часа'], ['60', 'За час'], ['', 'Без напоминаний']];
  form({
    title: a ? 'Визит' : 'Новый визит', values: a ? Object.assign({}, a, { remind: (a.remind || []).join(',') }) : Object.assign({ remind: '1440,120', date: addDays(today(), 1), time: '10:00' }, pre),
    fields: [
      { k: 'date', label: 'Дата', type: 'date', req: true, half: true }, { k: 'time', label: 'Время', type: 'time', half: true },
      { k: 'doctorId', label: 'Врач', type: 'select', options: doctorOpts, help: D.doctors.length ? '' : 'Врачей можно добавить во вкладке «Здоровье → Врачи»' },
      { k: 'reason', label: 'Цель визита', ph: 'Плановый осмотр, ЭКГ, коррекция дозы…' },
      { k: 'clinic', label: 'Клиника', ph: 'Городская поликлиника №3', half: true }, { k: 'address', label: 'Адрес', ph: 'ул. Ленина, 10, каб. 204', half: true },
      { k: 'who', label: 'Кто сопровождает', type: 'select', options: () => memberOpts('Пока никто') },
      { k: 'remind', label: 'Напомнить', type: 'select', options: R },
      { k: 'questions', label: 'Вопросы к врачу', type: 'textarea', ph: 'Можно ли снизить дозу? Почему отёки к вечеру?' },
      a ? { k: 'notes', label: 'Итоги визита', type: 'textarea', ph: 'Что сказал врач, новые назначения, когда прийти снова' } : null,
      a ? { k: 'done', type: 'toggle', text: 'Визит состоялся' } : null
    ],
    onSave: v => { const o = Object.assign(a || {}, v, { remind: v.remind ? v.remind.split(',').map(Number) : [] }); upsert('appts', o); toast(a ? 'Сохранено' : 'Визит добавлен — напомним заранее'); },
    onDelete: a ? () => removeItem('appts', a.id) : null
  });
}
function doctorForm(id) {
  const d = id ? byId('doctors', id) : null;
  form({
    title: d ? 'Врач' : 'Новый врач', values: d || {},
    fields: [{ k: 'name', label: 'Имя врача', req: true, ph: 'Смирнова Елена Андреевна' }, { k: 'spec', label: 'Специальность', ph: 'Терапевт', half: true }, { k: 'clinic', label: 'Клиника', ph: 'Поликлиника №3', half: true },
    { k: 'phone', label: 'Телефон', type: 'tel', ph: '+7 900 000-00-00', half: true }, { k: 'email', label: 'Почта', type: 'email', ph: 'doctor@clinic.ru', half: true },
    { k: 'portal', label: 'Портал клиники / сайт', type: 'url', ph: 'https://…', help: 'Ссылка на личный кабинет пациента (например, MyChart)' }, { k: 'address', label: 'Адрес', ph: 'ул. Ленина, 10' }, { k: 'notes', label: 'Заметки', type: 'textarea', ph: 'Принимает по вторникам; записываться через регистратуру' }],
    onSave: v => { upsert('doctors', Object.assign(d || {}, v)); toast('Сохранено'); },
    onDelete: d ? () => removeItem('doctors', d.id) : null
  });
}
function labForm(id, pre = {}) {
  const l = id ? D.labs.find(x => x.id === id) : null;
  form({
    title: l ? 'Результат анализа' : 'Новый анализ', values: l || Object.assign({ date: today() }, pre),
    fields: [{ k: 'name', label: 'Анализ', req: true, list: 'labPresets', ph: 'Начните вводить: Глюкоза, Холестерин…' }, { k: 'value', label: 'Значение', type: 'number', req: true, half: true }, { k: 'unit', label: 'Единицы', half: true },
    { k: 'low', label: 'Норма от', type: 'number', half: true }, { k: 'high', label: 'Норма до', type: 'number', half: true },
    { k: 'date', label: 'Дата', type: 'date', half: true }, { k: 'doctorId', label: 'Назначил', type: 'select', options: doctorOpts, half: true }, { k: 'note', label: 'Комментарий', type: 'textarea', ph: 'Сдавали натощак' }, { k: 'photo', label: 'Фото бланка', type: 'photo' }],
    extra: `<datalist id="labPresets">${Object.keys(LAB_PRESETS).map(k => `<option value="${esc(k)}">`).join('')}</datalist>`,
    onInput: (t, el) => { if (t.name === 'name' && LAB_PRESETS[t.value]) { const [u, lo, hi] = LAB_PRESETS[t.value]; el.querySelector('[name=unit]').value = u; el.querySelector('[name=low]').value = lo; el.querySelector('[name=high]').value = hi; } },
    onSave: v => { upsert('labs', Object.assign(l || {}, v)); const f = labFlag(v); toast(f ? `Сохранено: ${f === 'hi' ? 'выше' : 'ниже'} нормы` : 'Сохранено: в норме'); },
    onDelete: l ? () => removeItem('labs', l.id) : null
  });
}
function vitalForm(type, id) {
  const x = id ? D.vitals.find(v => v.id === id) : null; const d = x ? new Date(x.at) : new Date();
  form({
    title: x ? 'Запись' : 'Записать показатель', values: x ? Object.assign({}, x, { date: dstr(d), time: pad(d.getHours()) + ':' + pad(d.getMinutes()) }) : { type: type || 'bp', date: today(), time: nowTime() },
    fields: [{ k: 'type', label: 'Показатель', type: 'pick', options: Object.entries(VITALS).map(([k, V]) => [k, V.n]) }, { k: 'v1', label: 'Значение', type: 'number', req: true, half: true, help: 'Для давления — верхнее' }, { k: 'v2', label: 'Нижнее (для давления)', type: 'number', half: true },
    { k: 'date', label: 'Дата', type: 'date', half: true }, { k: 'time', label: 'Время', type: 'time', half: true }, { k: 'note', label: 'Заметка', ph: 'После прогулки, до лекарств…' }],
    onPick: (k, val, el) => { if (k === 'type') { el.querySelector('[name=v2]').closest('.fld').style.visibility = val === 'bp' ? '' : 'hidden'; el.querySelector('[name=v1]').closest('.fld').querySelector('.help').style.display = val === 'bp' ? '' : 'none'; } },
    onSave: v => {
      if (v.type === 'bp' && v.v2 == null) { toast('Укажите нижнее давление'); return false; }
      const [y, mo, dd] = v.date.split('-').map(Number), [h, mi] = (v.time || '12:00').split(':').map(Number);
      const o = Object.assign(x || {}, { type: v.type || 'bp', v1: v.v1, v2: v.type === 'bp' ? v.v2 : null, note: v.note, at: new Date(y, mo - 1, dd, h, mi).getTime(), by: x ? x.by : who() });
      upsert('vitals', o); const f = VITALS[o.type].chk(o.v1, o.v2); toast(f ? '⚠️ Значение вне нормы' : 'Записано');
    },
    onDelete: x ? () => removeItem('vitals', x.id) : null
  });
  const el = SHEETS[SHEETS.length - 1].el, t = (x ? x.type : type || 'bp');
  if (t !== 'bp') { el.querySelector('[name=v2]').closest('.fld').style.visibility = 'hidden'; el.querySelector('[name=v1]').closest('.fld').querySelector('.help').style.display = 'none'; }
}
function taskForm(id, pre = {}) {
  const t = id ? byId('tasks', id) : null;
  form({
    title: t ? 'Задача' : 'Новая задача', values: t || Object.assign({ cat: 'other', repeat: 'none' }, pre),
    fields: [{ k: 'title', label: 'Что сделать', req: true, ph: 'Отвезти маму к кардиологу' }, { k: 'due', label: 'Срок', type: 'date', half: true }, { k: 'minutes', label: 'Займёт, минут', type: 'number', ph: '60', half: true },
    { k: 'assignee', label: 'Кто делает', type: 'pick', options: () => memberOpts() }, { k: 'cat', label: 'Категория', type: 'select', options: Object.entries(TASK_CATS).map(([k, v]) => [k, v[0]]), half: true },
    { k: 'repeat', label: 'Повторять', type: 'select', options: [['none', 'Не повторять'], ['day', 'Каждый день'], ['week', 'Каждую неделю'], ['month', 'Каждый месяц']], half: true }, { k: 'notes', label: 'Подробности', type: 'textarea', ph: 'Адрес, что взять с собой…' }],
    onSave: v => { const o = Object.assign(t || { done: false, by: who() }, v); upsert('tasks', o); toast(t ? 'Сохранено' : 'Задача добавлена'); },
    onDelete: t ? () => removeItem('tasks', t.id) : null
  });
}
function expenseForm(id) {
  const e = id ? byId('expenses', id) : null;
  form({
    title: e ? 'Расход' : 'Новый расход', values: e || { date: today(), payer: ME, cat: 'meds', split: memberNames() },
    fields: [{ k: 'title', label: 'На что', req: true, ph: 'Лекарства в аптеке' }, { k: 'amount', label: `Сумма, ${D.settings.currency}`, type: 'number', req: true, half: true }, { k: 'date', label: 'Дата', type: 'date', half: true },
    { k: 'cat', label: 'Категория', type: 'select', options: Object.entries(EXP_CATS) }, { k: 'payer', label: 'Кто заплатил', type: 'pick', options: () => memberNames().map(n => [n, n]), req: true },
    { k: 'split', label: 'Делим между', type: 'multi', options: () => memberNames().map(n => [n, n]), help: 'Сумма делится поровну между отмеченными' }, { k: 'photo', label: 'Чек', type: 'photo' }],
    onSave: v => { if (!(v.amount > 0)) { toast('Укажите сумму'); return false; } upsert('expenses', Object.assign(e || {}, v)); toast('Расход записан'); },
    onDelete: e ? () => removeItem('expenses', e.id) : null
  });
}
function claimForm(id) {
  const c = id ? byId('claims', id) : null;
  form({
    title: c ? 'Страховое дело' : 'Новое дело', values: c || { type: 'pa', status: 'prep', who: ME },
    fields: [{ k: 'title', label: 'Название', req: true, ph: 'МРТ коленного сустава' }, { k: 'type', label: 'Тип', type: 'select', options: Object.entries(CLAIM_TYPES) }, { k: 'status', label: 'Статус', type: 'pick', options: Object.entries(CLAIM_ST).map(([k, v]) => [k, v[0]]) },
    { k: 'deadline', label: 'Срок / ответ ожидается', type: 'date', half: true }, { k: 'ref', label: 'Номер дела', half: true }, { k: 'who', label: 'Кто ведёт', type: 'select', options: () => memberOpts('Никто') }, { k: 'notes', label: 'Заметки', type: 'textarea', ph: 'Кому звонили, что сказали, какие документы нужны' }],
    onSave: v => { upsert('claims', Object.assign(c || {}, v)); toast('Сохранено'); },
    onDelete: c ? () => removeItem('claims', c.id) : null
  });
}
function memberForm(id) {
  const m = id ? byId('members', id) : null;
  form({
    title: m ? 'Участник' : 'Новый участник', values: m || { role: 'helper' },
    fields: [{ k: 'name', label: 'Имя', req: true, ph: 'Анна' }, { k: 'rel', label: 'Кем приходится', ph: 'Дочь', half: true }, { k: 'phone', label: 'Телефон', type: 'tel', half: true }, { k: 'role', label: 'Роль', type: 'pick', options: Object.entries(ROLES) }],
    onSave: v => {
      if (D.members.some(x => x.name.toLowerCase() === v.name.toLowerCase() && x !== m)) { toast('Такое имя уже есть'); return false; }
      const old = m && m.name; const o = upsert('members', Object.assign(m || {}, v));
      if (old && old !== v.name) { // переименование
        D.tasks.forEach(t => { if (t.assignee === old) t.assignee = v.name; if (t.doneBy === old) t.doneBy = v.name; });
        D.expenses.forEach(e => { if (e.payer === old) e.payer = v.name; if (e.to === old) e.to = v.name; if (e.split) e.split = e.split.map(n => n === old ? v.name : n); });
        D.log.forEach(l => { if (l.by === old) l.by = v.name; }); D.checkins.forEach(c => { if (c.who === old) c.who = v.name; }); D.appts.forEach(a => { if (a.who === old) a.who = v.name; });
        if (ME === old) setMe(v.name); save();
      }
      if (!ME) setMe(o.name); toast('Сохранено');
    },
    onDelete: m ? () => removeItem('members', m.id) : null
  });
}
function patientForm() {
  form({
    title: 'О ком заботимся', values: D.patient,
    fields: [{ k: 'name', label: 'Как называем в приложении', req: true, ph: 'Мама' }, { k: 'fullName', label: 'Полное имя', ph: 'Иванова Галина Петровна' }, { k: 'dob', label: 'Дата рождения', type: 'date', half: true }, { k: 'blood', label: 'Группа крови', ph: 'A(II) Rh+', half: true },
    { k: 'allergies', label: 'Аллергии', ph: 'Пенициллин, йод' }, { k: 'conditions', label: 'Диагнозы', type: 'textarea', ph: 'Сахарный диабет 2 типа; гипертония' },
    { k: 'insPlan', label: 'Страховка / полис', ph: 'Medicare Advantage / ОМС', half: true }, { k: 'insId', label: 'Номер полиса', half: true }, { k: 'insPhone', label: 'Телефон страховой', type: 'tel' },
    { k: 'emergency', label: 'Экстренные контакты', type: 'textarea', ph: 'Соседка Нина: +7 900 …' }, { k: 'notes', label: 'Важно знать', type: 'textarea', ph: 'Плохо слышит на левое ухо; успокаивает музыка; ходит с тростью' }],
    onSave: v => { D.patient = Object.assign(D.patient, v, { u: Date.now() }); save(); toast('Сохранено'); }
  });
}

/* ---------- карточки (детали) ---------- */
function apptSheet(id) {
  const body = () => {
    const a = byId('appts', id); if (!a) { setTimeout(closeSheet); return ''; }
    const d = byId('doctors', a.doctorId), st = apptStart(a);
    return `<div class="card"><div class="row"><span class="tile date"><b>${toDate(a.date).getDate()}</b><span>${MON[toDate(a.date).getMonth()]}</span></span><div class="grow"><div class="b" style="font-size:17px">${esc(d ? d.name : a.reason || 'Визит')}</div><div class="small muted">${fmtDW(a.date)}${a.time ? ', ' + a.time : ''}${d && d.spec ? ' · ' + esc(d.spec) : ''}</div></div>${a.done ? '<span class="tag green">состоялся</span>' : ''}</div>
      <dl class="kv" style="margin-top:14px">${a.reason ? `<dt>Цель</dt><dd>${esc(a.reason)}</dd>` : ''}${a.clinic ? `<dt>Клиника</dt><dd>${esc(a.clinic)}</dd>` : ''}${a.address ? `<dt>Адрес</dt><dd>${esc(a.address)}</dd>` : ''}<dt>Сопровождает</dt><dd>${esc(a.who || 'пока никто')}</dd><dt>Напоминания</dt><dd>${(a.remind || []).length ? a.remind.map(m => m >= 1440 ? 'за ' + m / 1440 + ' дн.' : 'за ' + (m >= 60 ? m / 60 + ' ч' : m + ' мин')).join(', ') : 'нет'}</dd></dl></div>
      <div class="btns" style="margin-bottom:12px">${a.address || a.clinic ? `<button class="btn sm ghost" data-act="map" data-id="${esc(a.address || a.clinic)}">${ico('pin', 's')}Маршрут</button>` : ''}${d && d.phone ? `<button class="btn sm ghost" data-act="call" data-id="${esc(d.phone)}">${ico('phone', 's')}Позвонить</button>` : ''}<button class="btn sm ghost" data-act="appt-cal" data-id="${a.id}">${ico('cal', 's')}В календарь</button></div>
      ${!a.who && !a.done && memberNames().length ? `<button class="btn block sec2" style="margin-bottom:12px" data-act="appt-go" data-id="${a.id}">${ico('user', 's')}Я провожу${ME ? ' (' + esc(ME) + ')' : ''}</button>` : ''}
      ${sec('Вопросы к врачу')}<div class="card">${a.questions ? `<div style="white-space:pre-wrap">${esc(a.questions)}</div>` : '<div class="muted small">Запишите заранее, что спросить — чтобы ничего не забыть на приёме.</div>'}</div>
      ${sec('Итоги визита')}<div class="card">${a.notes ? `<div style="white-space:pre-wrap">${esc(a.notes)}</div>` : `<div class="muted small">${st < Date.now() ? 'Запишите, что сказал врач — семья увидит.' : 'Заполните после визита.'}</div>`}</div>
      <div class="btns"><button class="btn ghost" data-act="appt-edit" data-id="${a.id}">${ico('edit', 's')}Изменить</button>${!a.done ? `<button class="btn green" data-act="appt-done" data-id="${a.id}">${ico('check', 's')}Состоялся</button>` : `<button class="btn ghost" data-act="appt-follow" data-id="${a.id}">${ico('tasks', 's')}Задача по итогам</button>`}</div>`;
  };
  sheet({ title: 'Визит к врачу', body, refresh: body });
}
function doctorSheet(id) {
  const body = () => {
    const d = byId('doctors', id); if (!d) { setTimeout(closeSheet); return ''; }
    const ms = D.messages.filter(x => x.doctorId === id).sort((a, b) => a.at - b.at);
    const ups = upcomingAppts().filter(a => a.doctorId === id);
    return `<div class="card"><div class="row"><span class="tile violet">${ico('steth')}</span><div class="grow"><div class="b" style="font-size:17px">${esc(d.name)}</div><div class="small muted">${esc([d.spec, d.clinic].filter(Boolean).join(' · '))}</div></div><button class="icon-btn" style="box-shadow:none;background:var(--surface2)" data-act="doctor-edit" data-id="${d.id}">${ico('edit', 's')}</button></div>
      <div class="btns" style="margin-top:12px">${d.phone ? `<button class="btn sm ghost" data-act="call" data-id="${esc(d.phone)}">${ico('phone', 's')}Звонок</button>` : ''}${d.portal ? `<button class="btn sm ghost" data-act="url" data-id="${esc(d.portal)}">${ico('link', 's')}Портал</button>` : ''}<button class="btn sm ghost" data-act="appt-new" data-id="${d.id}">${ico('cal', 's')}Визит</button></div>
      ${ups.length ? `<div class="small" style="margin-top:10px">Ближайший визит: <b>${fmtDS(ups[0].date)} ${ups[0].time || ''}</b></div>` : ''}${d.notes ? `<div class="small muted" style="margin-top:8px;white-space:pre-wrap">${esc(d.notes)}</div>` : ''}</div>
      ${sec('Переписка', `<button class="lnk" data-act="msg-in" data-id="${d.id}">+ ответ врача</button>`)}
      <div class="card"><div class="chat">${ms.length ? ms.map(m => `<div class="bub ${m.dir}">${esc(m.text)}<span class="mt">${m.dir === 'out' ? esc(m.by) + (m.via ? ' · ' + m.via : '') : 'Врач'} · ${new Date(m.at).toLocaleString('ru-RU', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}</span></div>`).join('') : '<div class="muted small" style="text-align:center;padding:10px">Сообщений пока нет. Напишите вопрос — отправьте его удобным способом, а копия сохранится здесь для всей семьи.</div>'}</div></div>
      <div class="card"><textarea class="inp" id="msgText" placeholder="Например: Добрый день! У мамы отёки к вечеру после повышения дозы. Нужно ли что-то изменить?"></textarea>
      <div class="small muted" style="margin:8px 2px">Отправить через:</div><div class="btns">${d.email ? `<button class="btn sm" data-act="msg-send" data-id="${d.id}" data-via="email">${ico('mail', 's')}Почту</button>` : ''}${d.phone ? `<button class="btn sm" data-act="msg-send" data-id="${d.id}" data-via="sms">${ico('msg', 's')}SMS</button>` : ''}${d.portal ? `<button class="btn sm" data-act="msg-send" data-id="${d.id}" data-via="portal">${ico('link', 's')}Портал</button>` : ''}<button class="btn sm ghost" data-act="msg-send" data-id="${d.id}" data-via="">Только сохранить</button></div>
      ${!d.email && !d.phone && !d.portal ? '<div class="help" style="margin-top:8px">Добавьте врачу почту, телефон или ссылку на портал, чтобы отправлять сообщения.</div>' : ''}</div>`;
  };
  sheet({ title: 'Врач', body, refresh: body });
}
function labSheet(name) {
  const body = () => {
    const arr = D.labs.filter(l => l.name === name).sort((a, b) => a.date.localeCompare(b.date)); if (!arr.length) { setTimeout(closeSheet); return ''; }
    const last = arr[arr.length - 1], f = labFlag(last);
    return `<div class="card"><div class="row"><div class="grow"><div class="small muted">Последний результат · ${fmtD(last.date)}</div><div style="font-size:30px;font-weight:800" class="${f ? 'flag-' + f : ''}">${fmtNum(last.value)} <span style="font-size:15px" class="muted">${esc(last.unit || '')}</span></div><div class="small muted">Норма: ${last.low != null ? fmtNum(last.low) : '…'} – ${last.high != null ? fmtNum(last.high) : '…'} ${esc(last.unit || '')}</div></div>${f ? `<span class="tag ${f === 'hi' ? 'red' : 'blue'}">${f === 'hi' ? 'выше нормы' : 'ниже нормы'}</span>` : '<span class="tag green">в норме</span>'}</div>
      ${arr.length > 1 ? `<div style="margin-top:12px">${chart([{ pts: arr.map(l => ({ x: toDate(l.date).getTime(), y: l.value })), color: '#2F6CAD' }], { low: last.low, high: last.high })}</div>` : ''}</div>
      ${sec('История')}<div class="card flat">${arr.slice().reverse().map(l => { const ff = labFlag(l); return `<button class="li" data-act="lab-edit" data-id="${l.id}"><div class="grow"><div class="t">${fmtD(l.date)}</div>${l.note ? `<div class="m">${esc(l.note)}</div>` : ''}</div>${l.photo ? `<img class="thumb" style="width:40px;height:40px" data-ph="${l.photo}" src="" alt="">` : ''}<b class="${ff ? 'flag-' + ff : ''}">${fmtNum(l.value)} ${esc(l.unit || '')}</b>${ico('chev', 's chev')}</button>`; }).join('')}</div>
      <button class="btn block" data-act="lab-add" data-id="${esc(name)}">${ico('plus', 's')}Новый результат</button>`;
  };
  sheet({ title: esc(name), body, refresh: body });
}
function vitalSheet(type) {
  const V = VITALS[type];
  const body = () => {
    const arr = vitalsOf(type); const last = arr[arr.length - 1];
    const series = type === 'bp' ? [{ pts: arr.slice(-30).map(v => ({ x: v.at, y: v.v1 })), color: '#C0583A' }, { pts: arr.slice(-30).map(v => ({ x: v.at, y: v.v2 })), color: '#2F6CAD' }] : [{ pts: arr.slice(-30).map(v => ({ x: v.at, y: v.v1 })), color: '#C0583A' }];
    const rng = { bp: {}, pulse: { low: 50, high: 100 }, glucose: { low: 3.9, high: 10 }, temp: { low: 35.5, high: 37.2 }, spo2: { low: 94, high: 100 }, weight: {} }[type];
    return `<div class="card">${last ? `<div class="small muted">Последнее · ${ago(last.at)}${last.by ? ' · ' + esc(last.by) : ''}</div><div style="font-size:30px;font-weight:800">${vitalStr(last)} <span style="font-size:15px" class="muted">${V.u}</span></div>${type === 'bp' ? '<div class="small muted">Норма для большинства пожилых — ниже 140/90. Цель обсудите с врачом.</div>' : ''}${arr.length > 1 ? `<div style="margin-top:12px">${chart(series, rng)}</div>` : ''}` : empty(V.icon, 'Записей пока нет')}</div>
      ${arr.length ? sec('Записи') + `<div class="card flat">${arr.slice().reverse().slice(0, 60).map(v => { const f = V.chk(v.v1, v.v2); return `<button class="li" data-act="vital-edit" data-id="${v.id}"><div class="grow"><div class="t">${new Date(v.at).toLocaleString('ru-RU', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}</div><div class="m">${esc([v.by, v.note].filter(Boolean).join(' · '))}</div></div><b class="${f ? (f.startsWith('hi') ? 'flag-hi' : 'flag-lo') : ''}">${vitalStr(v)}</b>${ico('chev', 's chev')}</button>`; }).join('')}</div>` : ''}
      <button class="btn block" data-act="vital-add" data-id="${type}">${ico('plus', 's')}Записать</button>`;
  };
  sheet({ title: V.n, body, refresh: body });
}
function patientSheet() {
  const body = () => {
    const p = D.patient, a = age(p.dob);
    return `<div class="card"><div class="row"><span class="av lg" style="background:var(--brand)">${esc((p.name || '?').charAt(0))}</span><div class="grow"><div class="b" style="font-size:19px">${esc(p.name || 'Не указано')}</div><div class="small muted">${esc(p.fullName || '')}${p.dob ? ' · ' + fmtD(p.dob) + (a != null ? ` (${a} ${plural(a, 'год', 'года', 'лет')})` : '') : ''}</div></div></div>
      <dl class="kv" style="margin-top:14px"><dt>Аллергии</dt><dd style="color:${p.allergies ? 'var(--red)' : ''}">${esc(p.allergies || '—')}</dd><dt>Диагнозы</dt><dd>${esc(p.conditions || '—')}</dd><dt>Группа крови</dt><dd>${esc(p.blood || '—')}</dd><dt>Страховка</dt><dd>${esc([p.insPlan, p.insId].filter(Boolean).join(' · ') || '—')}</dd>${p.emergency ? `<dt>Экстренно</dt><dd>${esc(p.emergency)}</dd>` : ''}${p.notes ? `<dt>Важно</dt><dd>${esc(p.notes)}</dd>` : ''}</dl></div>
      <div class="btns"><button class="btn ghost" data-act="patient-edit">${ico('edit', 's')}Изменить</button><button class="btn" data-act="print-emergency">${ico('print', 's')}Экстренный лист</button></div>`;
  };
  sheet({ title: 'Карточка', body, refresh: body });
}
function sosSheet() {
  const p = D.patient, docs = D.doctors.filter(d => d.phone), fam = D.members.filter(m => m.phone && m.name !== ME);
  sheet({
    title: 'Экстренная помощь', body: `<button class="btn block" style="background:var(--red);min-height:64px;font-size:19px;margin-bottom:10px" data-act="call" data-id="${esc(D.settings.sos || '112')}">${ico('phone')}Вызвать скорую · ${esc(D.settings.sos || '112')}</button>
    ${fam.map(m => `<button class="btn block ghost" style="margin-bottom:8px;justify-content:flex-start" data-act="call" data-id="${esc(m.phone)}">${avatar(m.name)}Позвонить: ${esc(m.name)}</button>`).join('')}
    ${docs.slice(0, 3).map(d => `<button class="btn block ghost" style="margin-bottom:8px;justify-content:flex-start" data-act="call" data-id="${esc(d.phone)}">${ico('steth', 's')}${esc(d.name)}${d.spec ? ' · ' + esc(d.spec) : ''}</button>`).join('')}
    <div class="card" style="margin-top:6px"><div class="b" style="margin-bottom:8px">Покажите врачу скорой</div><dl class="kv"><dt>Пациент</dt><dd>${esc(p.fullName || p.name || '—')}${p.dob ? ', ' + fmtD(p.dob) : ''}</dd><dt>Аллергии</dt><dd style="color:var(--red)">${esc(p.allergies || 'нет данных')}</dd><dt>Диагнозы</dt><dd>${esc(p.conditions || '—')}</dd><dt>Группа крови</dt><dd>${esc(p.blood || '—')}</dd><dt>Лекарства</dt><dd>${activeMeds().map(m => esc(m.name + (m.dose ? ' ' + m.dose : ''))).join('\n') || '—'}</dd></dl></div>
    <button class="btn block ghost" data-act="print-emergency">${ico('print', 's')}Экстренный лист (PDF)</button>`
  });
}
function settingsSheet() {
  const body = () => {
    const ns = native('notifState'), ex = native('exactAllowed');
    return `<div class="card flat">
      <div class="li"><span class="tile">${ico('user', 's')}</span><div class="grow"><div class="t">Кто вы</div><div class="m">Ваши отметки подписываются этим именем</div></div><select class="inp" style="width:140px;min-height:42px" id="meSel">${['', ...memberNames()].map(n => `<option value="${esc(n)}" ${n === ME ? 'selected' : ''}>${esc(n || 'Выбрать…')}</option>`).join('')}</select></div>
      <button class="li" data-act="patient"><span class="tile">${ico('heart', 's')}</span><div class="grow"><div class="t">О ком заботимся</div><div class="m">${esc(D.patient.name || 'не указано')}</div></div>${ico('chev', 's chev')}</button></div>
    ${sec('Напоминания')}<div class="card flat">
      <div class="li"><span class="tile green">${ico('bell', 's')}</span><div class="grow"><div class="t">Уведомления на телефоне</div><div class="m">${ns === 'denied' ? 'Запрещены в системе' : 'Лекарства, визиты, рецепты, задачи'}</div></div><label class="sw"><input type="checkbox" id="remSw" ${D.settings.remind !== false ? 'checked' : ''}><span></span></label></div>
      ${ns === 'denied' ? `<button class="li" data-act="notif-req"><span class="tile red">${ico('alert', 's')}</span><div class="grow"><div class="t">Разрешить уведомления</div><div class="m">Без этого напоминания не придут</div></div>${ico('chev', 's chev')}</button>` : ''}
      ${ex === false ? `<button class="li" data-act="exact"><span class="tile amber">${ico('clock', 's')}</span><div class="grow"><div class="t">Точное время напоминаний</div><div class="m">Разрешите «Будильники и напоминания»</div></div>${ico('chev', 's chev')}</button>` : ''}
      ${N ? `<button class="li" data-act="battery"><span class="tile amber">${ico('alert', 's')}</span><div class="grow"><div class="t">Работа в фоне (Huawei, Xiaomi)</div><div class="m">Отключите экономию батареи для CareShare, иначе система может блокировать напоминания</div></div>${ico('chev', 's chev')}</button>
      <button class="li" data-act="test-notif"><span class="tile blue">${ico('bell', 's')}</span><div class="grow"><div class="t">Проверить уведомление</div><div class="m">Придёт через 5 секунд</div></div>${ico('chev', 's chev')}</button>` : ''}
      <div class="li"><span class="tile">${ico('box', 's')}</span><div class="grow"><div class="t">Предупреждать о запасе</div><div class="m">За сколько дней до окончания лекарства</div></div><select class="inp" style="width:100px;min-height:42px" id="refSel">${[3, 5, 7, 10, 14].map(n => `<option ${+D.settings.refillDays === n ? 'selected' : ''}>${n}</option>`).join('')}</select></div></div>
    ${sec('Общие')}<div class="card flat">
      <div class="li"><span class="tile amber">${ico('wallet', 's')}</span><div class="grow"><div class="t">Валюта</div></div><select class="inp" style="width:100px;min-height:42px" id="curSel">${['$', '₽', '€', '₸', '£'].map(c => `<option ${D.settings.currency === c ? 'selected' : ''}>${c}</option>`).join('')}</select></div>
      <div class="li"><span class="tile red">${ico('phone', 's')}</span><div class="grow"><div class="t">Номер скорой</div><div class="m">Для кнопки SOS</div></div><input class="inp" style="width:100px;min-height:42px" id="sosIn" inputmode="tel" value="${esc(D.settings.sos || '112')}"></div></div>
    ${sec('Данные')}<div class="card flat">
      <button class="li" data-act="export"><span class="tile blue">${ico('upload', 's')}</span><div class="grow"><div class="t">Отправить данные семье</div><div class="m">Файл, который можно открыть в CareShare</div></div>${ico('chev', 's chev')}</button>
      <button class="li" data-act="import"><span class="tile blue">${ico('download', 's')}</span><div class="grow"><div class="t">Загрузить файл от семьи</div><div class="m">Данные объединятся с вашими</div></div>${ico('chev', 's chev')}</button>
      <button class="li" data-act="print-emergency"><span class="tile red">${ico('print', 's')}</span><div class="grow"><div class="t">Экстренный лист (PDF)</div></div>${ico('chev', 's chev')}</button>
      <button class="li" data-act="demo"><span class="tile violet">${ico('sparkle', 's')}</span><div class="grow"><div class="t">Загрузить пример</div><div class="m">Заменит текущие данные демонстрационными</div></div>${ico('chev', 's chev')}</button>
      <button class="li" data-act="reset"><span class="tile red">${ico('trash', 's')}</span><div class="grow"><div class="t" style="color:var(--red)">Удалить все данные</div></div></button></div>
    ${sec('О приложении')}<div class="card"><div class="row">${LOGO}<div><div class="b">CareShare 1.0</div><div class="small muted">Ознакомительная версия. Данные хранятся только на этом телефоне.</div></div></div>
      <div class="small" style="margin-top:12px"><b>В разработке:</b> синхронизация семьи в реальном времени · подключение порталов клиник (MyChart и др.) и автоматическая загрузка анализов и визитов · ИИ-расшифровка разговора с врачом · уведомление всей семье о пропущенном приёме.</div></div>`;
  };
  const el = sheet({ title: 'Настройки', body, refresh: body });
  el.addEventListener('change', e => {
    const t = e.target;
    if (t.id === 'meSel') { setMe(t.value); render(); pushReminders(); }
    else if (t.id === 'remSw') { D.settings.remind = t.checked; save(true); toast(t.checked ? 'Напоминания включены' : 'Напоминания выключены'); if (t.checked) native('requestNotif'); }
    else if (t.id === 'refSel') { D.settings.refillDays = +t.value; save(); }
    else if (t.id === 'curSel') { D.settings.currency = t.value; save(); }
    else if (t.id === 'sosIn') { D.settings.sos = t.value.trim() || '112'; save(true); }
  });
}
function quickAdd() {
  const items = [['med', 'pill', 'Лекарство', 'green'], ['appt', 'cal', 'Визит', 'blue'], ['task', 'tasks', 'Задача', ''], ['vital', 'activity', 'Давление и др.', 'red'], ['lab', 'flask', 'Анализ', 'blue'], ['expense', 'wallet', 'Расход', 'amber'], ['post', 'msg', 'Новость', 'violet'], ['doc', 'camera', 'Фото документа', '']];
  const el = sheet({ title: 'Добавить', body: `<div class="grid2">${items.map(([k, i, l, c]) => `<button class="vt" data-q="${k}" style="display:flex;align-items:center;gap:10px"><span class="tile ${c}">${ico(i, 's')}</span><b style="font-size:15px">${l}</b></button>`).join('')}</div>` });
  el.addEventListener('click', e => { const b = e.target.closest('[data-q]'); if (!b) return; closeSheet(); const k = b.dataset.q; setTimeout(() => ({ med: () => medForm(), appt: () => apptForm(), task: () => taskForm(), vital: () => vitalForm('bp'), lab: () => labForm(), expense: () => expenseForm(), post: () => { UI.family = 'news'; go('family'); setTimeout(() => $('#postText') && $('#postText').focus(), 100); }, doc: () => docAdd() }[k])(), 200); });
}
async function docAdd() {
  const photo = await pickPhoto(); if (!photo) return;
  form({ title: 'Документ', values: { photo }, fields: [{ k: 'title', label: 'Название', req: true, ph: 'Страховая карта, лицевая сторона' }, { k: 'photo', label: 'Фото', type: 'photo' }], onSave: v => { if (!v.photo) { toast('Добавьте фото'); return false; } upsert('docs', v); toast('Документ сохранён'); } });
}
function photoSheet(phId, title = 'Фото', onDelete) {
  const el = sheet({ title: esc(title), body: `<img data-ph="${phId}" src="" alt="" style="width:100%;border-radius:16px">`, foot: onDelete ? `<button class="btn danger block" data-pdel>${ico('trash', 's')}Удалить</button>` : '' });
  if (onDelete) el.querySelector('[data-pdel]').onclick = async () => { if (await ask('Удалить документ?')) { closeSheet(); onDelete(); } };
}

/* ---------- действия ---------- */
Object.assign(ACT, {
  tab: d => go(d.id),
  goto: d => { const [t, s] = d.id.split(':'); if (t === 'health') UI.health = s; if (t === 'family') UI.family = s; go(t); },
  health: d => { UI.health = d.id; saveUI(); render(); },
  family: d => { UI.family = d.id; saveUI(); render(); },
  tf: d => { UI.tf = d.id; saveUI(); render(); },
  'all-alerts': () => { UI.allAlerts = !UI.allAlerts; render(); },
  alert: d => ({ tab: () => go(d.id), med: () => medForm(d.id), appt: () => apptSheet(d.id), lab: () => labSheet(d.id), vital: () => vitalSheet(d.id), claim: () => claimForm(d.id) }[d.k] || (() => { }))(),
  fab: () => {
    if (UI.tab === 'home') return quickAdd(); if (UI.tab === 'meds') return medForm(); if (UI.tab === 'tasks') return taskForm(UI.tf === 'mine' ? { assignee: ME } : {});
    if (UI.tab === 'health') return ({ visits: () => apptForm(), doctors: () => doctorForm(), labs: () => labForm(), vitals: () => vitalForm('bp'), docs: docAdd, insurance: () => claimForm() }[UI.health] || quickAdd)();
    if (UI.tab === 'family') return ({ money: () => expenseForm(), load: () => taskForm(), people: () => memberForm() }[UI.family] || quickAdd)();
  },
  dose: d => { const m = byId('meds', d.id); if (!m) return; const on = !getDose(today(), m.id, d.time); setDose(today(), m, d.time, on); save(); toast(on ? `✓ ${m.name} — отмечено` : 'Отметка снята'); },
  med: d => medForm(d.id),
  refill: d => { const m = byId('meds', d.id); form({ title: 'Пополнить: ' + esc(m.name), fields: [{ k: 'add', label: 'Сколько штук докупили', type: 'number', req: true, ph: '30' }, { k: 'rx', label: 'Новый срок рецепта (если продлили)', type: 'date' }], values: { rx: m.rx || '' }, onSave: v => { m.stock = +((m.stock || 0) + (v.add || 0)).toFixed(2); if (v.rx) m.rx = v.rx; upsert('meds', m); toast(`Запас: ${fmtNum(m.stock)} шт.`); } }); },
  'rx-task': d => { const m = byId('meds', d.id); taskForm(null, { title: `Получить новый рецепт: ${m.name}`, cat: 'paper', due: m.rx && m.rx > today() ? addDays(m.rx, -3) : today(), assignee: ME }); },
  appt: d => apptSheet(d.id), 'appt-edit': d => apptForm(d.id), 'appt-new': d => apptForm(null, { doctorId: d.id }),
  'appt-done': d => { const a = byId('appts', d.id); apptForm(a.id); setTimeout(() => { const top = SHEETS[SHEETS.length - 1].el, t = $('[name=done]', top); if (t) t.checked = true; const n = $('[name=notes]', top); n && n.focus(); }, 300); },
  'appt-go': d => { const a = byId('appts', d.id); a.who = ME || memberNames()[0]; upsert('appts', a); toast('Спасибо! Вы сопровождаете'); },
  'appt-cal': d => { const a = byId('appts', d.id); const st = apptStart(a); addToCalendar('Визит: ' + (docName(a.doctorId) || a.reason || 'врач'), [a.clinic, a.address].filter(Boolean).join(', '), [a.reason, a.questions ? 'Вопросы:\n' + a.questions : ''].filter(Boolean).join('\n'), st, st + 3600e3); },
  'appt-follow': d => { const a = byId('appts', d.id); taskForm(null, { title: 'По итогам визита: ', cat: 'meds', notes: a.notes || '' }); },
  map: d => openLink('geo:0,0?q=' + encodeURIComponent(d.id)),
  call: (d, el, e) => { e && e.stopPropagation(); openLink('tel:' + d.id.replace(/[^\d+]/g, '')); },
  url: d => openLink(/^https?:/.test(d.id) ? d.id : 'https://' + d.id),
  ics: async () => { const f = await pickFile('.ics,text/calendar'); if (!f) return; const evs = parseICS(await f.text()); if (!evs.length) return toast('В файле не найдено визитов'); evs.forEach(ev => { const dd = ev.start; D.appts.push({ id: uid(), c: Date.now(), u: Date.now(), date: dstr(dd), time: pad(dd.getHours()) + ':' + pad(dd.getMinutes()), reason: ev.title || 'Визит', clinic: ev.loc || '', questions: '', notes: ev.desc ? 'Из приглашения: ' + ev.desc : '', remind: [1440, 120] }); }); save(); toast(`Добавлено визитов: ${evs.length}`); },
  doctor: d => doctorSheet(d.id), 'doctor-edit': d => doctorForm(d.id),
  'msg-send': d => {
    const t = $('#msgText'); const text = t && t.value.trim(); if (!text) return toast('Напишите сообщение'); const doc = byId('doctors', d.id), via = d.via;
    const sig = `\n\n— ${ME || ''}${D.patient.fullName ? ', по поводу пациента ' + D.patient.fullName : ''}`.replace(/— ,/, '—');
    if (via === 'email') { if (N && N.email) native('email', doc.email, 'Вопрос по пациенту ' + (D.patient.fullName || D.patient.name || ''), text + sig); else openLink(`mailto:${doc.email}?subject=${encodeURIComponent('Вопрос врачу')}&body=${encodeURIComponent(text + sig)}`); }
    if (via === 'sms') { if (N && N.sms) native('sms', doc.phone, text); else openLink(`sms:${doc.phone}?body=${encodeURIComponent(text)}`); }
    if (via === 'portal') { shareText('Сообщение врачу', text); setTimeout(() => openLink(doc.portal), 600); }
    upsert('messages', { doctorId: d.id, dir: 'out', text, at: Date.now(), by: who(), via: { email: 'почта', sms: 'SMS', portal: 'портал' }[via] || '' });
    toast(via ? 'Копия сохранена в переписке' : 'Сохранено');
  },
  'msg-in': d => form({ title: 'Ответ врача', fields: [{ k: 'text', label: 'Что ответил врач', type: 'textarea', req: true, ph: 'Перепишите или вставьте ответ из почты / портала' }], onSave: v => { upsert('messages', { doctorId: d.id, dir: 'in', text: v.text, at: Date.now(), by: who() }); toast('Ответ добавлен'); } }),
  lab: d => labSheet(d.id), 'lab-edit': d => labForm(d.id),
  'lab-add': d => { const arr = D.labs.filter(l => l.name === d.id); const l = arr[arr.length - 1] || {}; labForm(null, { name: d.id, unit: l.unit, low: l.low, high: l.high }); },
  vital: d => vitalSheet(d.id), 'vital-add': d => vitalForm(d.id), 'vital-edit': d => { const v = D.vitals.find(x => x.id === d.id); vitalForm(v.type, v.id); },
  'print-emergency': () => { if (!D.patient.name) { toast('Сначала заполните карточку'); return patientForm(); } printHtml(emergencyHtml()); },
  'patient-edit': () => patientForm(), patient: () => patientSheet(),
  'doc-add': () => docAdd(), doc: d => { const x = byId('docs', d.id); photoSheet(x.photo, x.title, () => { PH.del(x.photo); removeItem('docs', x.id); }); },
  'doc-check': d => { D.docState[d.id] = !D.docState[d.id]; save(); },
  claim: d => claimForm(d.id),
  task: d => taskForm(d.id),
  'task-toggle': d => { const t = byId('tasks', d.id); if (!t) return; toggleTask(t); toast(t.done ? (t.repeat && t.repeat !== 'none' ? 'Готово! Следующая создана' : 'Готово!') : 'Снова в работе'); },
  'task-take': (d, el, e) => { e && e.stopPropagation(); const t = byId('tasks', d.id); t.assignee = ME; upsert('tasks', t); toast('Задача ваша'); },
  mood: d => { draft.text = ($('#postText') || {}).value || ''; draft.mood = draft.mood === d.id ? '' : d.id; render(); },
  'post-photo': async () => { draft.text = ($('#postText') || {}).value || ''; const id = await pickPhoto(); if (id) { draft.photo = id; render(); } },
  'post-photo-x': () => { draft.text = ($('#postText') || {}).value || ''; draft.photo = ''; render(); },
  post: () => { const text = ($('#postText') || {}).value.trim(); if (!text && !draft.photo) return toast('Напишите пару слов'); upsert('log', { text, mood: draft.mood, photo: draft.photo, by: who(), at: Date.now() }); draft = { mood: '', photo: '' }; render(); toast('Опубликовано'); },
  'post-del': async d => { if (await ask('Удалить запись?')) removeItem('log', d.id); },
  photo: d => photoSheet(d.id),
  checkin: d => { form({ title: 'Ваше самочувствие', values: { score: d.id }, fields: [{ k: 'score', label: 'Как вы?', type: 'pick', options: [['1', '😫 Очень тяжело'], ['2', '😟 Тяжело'], ['3', '😐 Нормально'], ['4', '🙂 Хорошо'], ['5', '😊 Отлично']] }, { k: 'note', label: 'Чем помочь? (необязательно)', type: 'textarea', ph: 'Не высыпаюсь, нужна замена в четверг…' }], saveText: 'Отметить', onSave: v => { upsert('checkins', { who: ME, date: today(), score: +v.score || +d.id, note: v.note }); toast(+v.score <= 2 ? 'Семья увидит, что вам нужна поддержка' : 'Спасибо!'); } }); },
  expense: d => expenseForm(d.id),
  'exp-del': async d => { if (await ask('Удалить запись о возврате?')) removeItem('expenses', d.id); },
  settle: async d => { if (await ask(`${d.from} перевёл(а) ${d.to} ${money(+d.amt)}?`, 'Да, оплачено', false)) { upsert('expenses', { kind: 'settle', payer: d.from, to: d.to, amount: +d.amt, date: today(), title: 'Возврат' }); toast('Записано'); } },
  member: d => memberForm(d.id),
  'share-summary': () => shareText('Сводка CareShare', summaryText()),
  sos: () => sosSheet(), settings: () => settingsSheet(),
  export: () => exportData(),
  import: async () => { const f = await pickFile('.json,application/json,*/*'); if (f) importData(await f.text()); },
  'notif-req': () => native('requestNotif'), exact: () => native('openExactSettings'), battery: () => native('openBatterySettings'),
  'test-notif': () => { native('testNotify'); toast('Уведомление придёт через 5 секунд'); },
  demo: async () => { if (await ask('Заменить текущие данные примером?', 'Загрузить', false)) { seedDemo(); SHEETS.slice().forEach(() => closeSheet()); toast('Пример загружен'); } },
  reset: async () => { if (await ask('Удалить все данные на этом телефоне? Это нельзя отменить.')) { D = blank(); setMe(''); save(true); SHEETS.slice().forEach(() => closeSheet()); render(); onboarding(); } }
});
document.addEventListener('click', e => {
  const el = e.target.closest('[data-act]'); if (!el) return;
  const fn = ACT[el.dataset.act]; if (fn) { e.preventDefault(); fn(el.dataset, el, e); }
});
document.addEventListener('input', e => { if (e.target.id === 'postText') draft.text = e.target.value; });
window.csBack = function () { if (SHEETS.length) { closeSheet(); return true; } if ($('.ob')) return false; if (UI.tab !== 'home') { go('home'); return true; } return false; };
window.csResume = function () { pullNativeActions(); render(); };

/* ---------- первый запуск ---------- */
function onboarding() {
  const ob = document.createElement('div'); ob.className = 'ob';
  ob.innerHTML = `<div class="in"><div class="logo">${LOGO}</div><h1>Заботьтесь о близком — вместе</h1><p class="muted" style="margin:0 0 10px;font-size:17px">Лекарства, врачи, анализы, задачи и расходы семьи в одном приложении.</p>
    ${[['pill', 'Напоминания о лекарствах', 'Запас, срок рецепта и отметка «дано» прямо из уведомления'], ['steth', 'Врачи и визиты', 'Вопросы к врачу, итоги визитов, анализы с нормами'], ['users', 'Честно и вместе', 'Кто что делает, общие расходы и кто кому должен'], ['alert', 'SOS и экстренный лист', 'Всё важное для скорой — в одно касание']].map(([i, t, s]) => `<div class="feat"><span class="tile">${ico(i, 's')}</span><div><b>${t}</b><span>${s}</span></div></div>`).join('')}
    <div style="flex:1;min-height:20px"></div>
    <div class="card" id="obForm"><div class="fld"><label>О ком вы заботитесь?</label><input class="inp" id="obP" placeholder="Мама, папа, бабушка…" maxlength="40"></div><div class="fld"><label>Как вас зовут?</label><input class="inp" id="obMe" placeholder="Анна" maxlength="40"></div>
    <button class="btn block" id="obGo">Начать</button></div>
    <button class="btn block ghost" id="obDemo">${ico('sparkle', 's')}Посмотреть на примере</button></div>`;
  document.body.append(ob);
  $('#obGo').onclick = () => {
    const p = $('#obP').value.trim(), m = $('#obMe').value.trim(); if (!p) return toast('Укажите, о ком заботитесь'); if (!m) return toast('Укажите ваше имя');
    D.patient = { name: p, u: Date.now() }; D.members.push({ id: uid(), c: Date.now(), u: Date.now(), name: m, role: 'main' }); setMe(m); D.onboarded = true; save(); ob.remove(); native('requestNotif');
  };
  $('#obDemo').onclick = () => { seedDemo(); ob.remove(); native('requestNotif'); toast('Это пример — меняйте что угодно'); };
}

/* ---------- демо-данные ---------- */
function seedDemo() {
  D = blank(); const t = today(), now = Date.now(), A = (o) => Object.assign({ id: uid(), c: now - 40 * 864e5, u: now }, o);
  D.onboarded = true;
  D.patient = { name: 'Мама', fullName: 'Иванова Галина Петровна', dob: '1944-03-12', blood: 'A(II) Rh+', allergies: 'Пенициллин, йод', conditions: 'Сахарный диабет 2 типа\nГипертония 2 степени\nОстеоартроз коленных суставов', insPlan: 'Medicare Advantage', insId: '1EG4-TE5-MK72', insPhone: '+1 800 633 4227', emergency: 'Соседка Нина Сергеевна (кв. 12): +7 900 111-22-33', notes: 'Плохо слышит на левое ухо. Ходит с тростью. Успокаивает старая эстрада.', u: now };
  D.members = [A({ name: 'Анна', rel: 'Дочь', role: 'main', phone: '+7 900 000-00-01' }), A({ name: 'Иван', rel: 'Сын', role: 'helper', phone: '+7 900 000-00-02' }), A({ name: 'Ольга', rel: 'Внучка', role: 'helper', phone: '+7 900 000-00-03' })];
  const dT = A({ name: 'Смирнова Елена Андреевна', spec: 'Терапевт', clinic: 'Городская поликлиника №3', phone: '+7 900 555-01-01', email: 'smirnova@clinic3.example', portal: 'https://www.mychart.org', address: 'ул. Ленина, 10, каб. 204' });
  const dC = A({ name: 'Орлов Дмитрий Викторович', spec: 'Кардиолог', clinic: 'Кардиоцентр', phone: '+7 900 555-02-02', email: 'orlov@cardio.example', address: 'пр. Мира, 45' });
  const dE = A({ name: 'Ким Марина Олеговна', spec: 'Эндокринолог', clinic: 'Городская поликлиника №3', phone: '+7 900 555-03-03' });
  D.doctors = [dT, dC, dE];
  const m1 = A({ name: 'Метформин', dose: '500 мг', form: 'tab', times: ['08:00', '20:00'], per: 1, food: 'with', stock: 18, rx: addDays(t, 9), refills: 0, doctorId: dE.id, pharmacy: 'Аптека «Здоровье», ул. Ленина, 5' });
  const m2 = A({ name: 'Лозартан', dose: '50 мг', form: 'tab', times: ['08:00'], per: 1, food: 'any', stock: 41, rx: addDays(t, 75), refills: 2, doctorId: dC.id });
  const m3 = A({ name: 'Аторвастатин', dose: '20 мг', form: 'tab', times: ['21:00'], per: 1, food: 'any', stock: 5, rx: addDays(t, 40), refills: 1, doctorId: dC.id, notes: 'Не запивать грейпфрутовым соком' });
  const m4 = A({ name: 'Кальций + D3', dose: '1 таб.', form: 'tab', times: ['13:00'], per: 1, food: 'after', stock: 52 });
  D.meds = [m1, m2, m3, m4];
  const cur = tmin(nowTime());
  for (let i = 6; i >= 0; i--) { const day = addDays(t, -i); D.meds.forEach(m => m.times.forEach(tm => { if (i === 0 && tmin(tm) > cur - 30) return; if (i > 0 && Math.random() < 0.08) return; const by = ['Анна', 'Анна', 'Иван', 'Ольга'][Math.floor(Math.random() * 4)]; D.doses[doseKey(day, m.id, tm)] = { by, at: toDate(day).getTime() + tmin(tm) * 60000 + 300000 }; })); }
  D.appts = [A({ date: addDays(t, 1), time: '10:30', doctorId: dC.id, clinic: 'Кардиоцентр', address: 'пр. Мира, 45, 3 этаж', reason: 'Контроль давления, ЭКГ', who: 'Иван', remind: [1440, 120], questions: '1. Отёки ног к вечеру — связано ли с лозартаном?\n2. Можно ли снизить дозу при давлении 130/80?\n3. Нужен ли холтер?' }),
    A({ date: addDays(t, 12), time: '14:00', doctorId: dE.id, clinic: 'Городская поликлиника №3', reason: 'Коррекция терапии диабета, продлить рецепт на метформин', remind: [1440, 120] }),
    A({ date: addDays(t, -10), time: '09:00', doctorId: dT.id, clinic: 'Городская поликлиника №3', reason: 'Плановый осмотр', who: 'Анна', done: true, notes: 'Давление в целом в норме. Назначены анализы: HbA1c, липидный профиль, креатинин. Кальций + D3 по 1 таб. после обеда. Следующий визит к эндокринологу.' })];
  D.messages = [A({ doctorId: dT.id, dir: 'out', text: 'Добрый день, Елена Андреевна! Пришли анализы: глюкоза 7,8 натощак. Нужно ли что-то менять до визита к эндокринологу?', at: now - 2 * 864e5, by: 'Анна', via: 'почта' }), A({ doctorId: dT.id, dir: 'in', text: 'Здравствуйте! Пока продолжайте метформин как есть, контролируйте сахар утром 3 дня подряд и принесите записи эндокринологу.', at: now - 1.6 * 864e5, by: 'Анна' })];
  const lab = (name, vals) => { const [u, lo, hi] = LAB_PRESETS[name]; vals.forEach(([d, v]) => D.labs.push(A({ name, value: v, unit: u, low: lo, high: hi, date: addDays(t, d), doctorId: dT.id }))); };
  lab('Глюкоза (натощак)', [[-180, 6.4], [-120, 6.9], [-60, 7.1], [-3, 7.8]]); lab('Гликированный гемоглобин (HbA1c)', [[-180, 6.8], [-90, 7.0], [-3, 7.3]]);
  lab('Холестерин общий', [[-180, 6.1], [-3, 5.4]]); lab('Креатинин', [[-180, 92], [-3, 98]]); lab('Гемоглобин', [[-3, 128]]);
  for (let i = 13; i >= 0; i--) { const at = now - i * 864e5 - 3 * 3600e3; D.vitals.push(A({ type: 'bp', v1: 128 + Math.round(Math.random() * 14) + (i === 0 ? 30 : 0), v2: 78 + Math.round(Math.random() * 8) + (i === 0 ? 14 : 0), at, by: i % 3 ? 'Анна' : 'Ольга' })); if (i % 2 === 0) D.vitals.push(A({ type: 'glucose', v1: +(6.4 + Math.random() * 1.6).toFixed(1), at: at - 1800e3, by: 'Анна' })); if (i % 3 === 0) D.vitals.push(A({ type: 'pulse', v1: 68 + Math.round(Math.random() * 12), at, by: 'Анна' })); }
  D.vitals.push(A({ type: 'weight', v1: 71.4, at: now - 20 * 864e5 }), A({ type: 'weight', v1: 70.8, at: now - 6 * 864e5 }), A({ type: 'temp', v1: 36.6, at: now - 864e5 }), A({ type: 'spo2', v1: 96, at: now - 864e5 }));
  const T = (title, o) => D.tasks.push(A(Object.assign({ title, cat: 'other', done: false, by: 'Анна' }, o)));
  T('Купить продукты на неделю', { cat: 'shop', due: t, assignee: 'Ольга', minutes: 60, repeat: 'week' });
  T('Позвонить в страховую по поводу МРТ', { cat: 'paper', due: addDays(t, -1), minutes: 30 });
  T('Отвезти к кардиологу', { cat: 'ride', due: addDays(t, 1), assignee: 'Иван', minutes: 180 });
  T('Купить аторвастатин', { cat: 'meds', due: addDays(t, 2), minutes: 30 });
  T('Оплатить коммунальные услуги', { cat: 'home', due: addDays(t, 5), assignee: 'Иван', minutes: 20, repeat: 'month' });
  T('Разобрать аптечку, выбросить просроченное', { cat: 'meds', minutes: 40 });
  [['Уборка в квартире', 'home', 'Анна', 3, 120], ['Сопроводить на анализы', 'visit', 'Анна', 4, 150], ['Купить лекарства', 'meds', 'Анна', 6, 40], ['Помыть окна', 'home', 'Ольга', 8, 90], ['Съездить в МФЦ за справкой', 'paper', 'Анна', 11, 120], ['Продукты', 'shop', 'Иван', 13, 60], ['Сопроводить к терапевту', 'visit', 'Анна', 10, 150], ['Приготовить еду на 3 дня', 'home', 'Анна', 15, 120], ['Продукты', 'shop', 'Ольга', 16, 60], ['Починить кран', 'home', 'Иван', 20, 60]]
    .forEach(([title, cat, by, d, min]) => T(title, { cat, done: true, doneBy: by, doneAt: now - d * 864e5, minutes: min, due: addDays(t, -d) }));
  D.log = [A({ text: 'Заехала после работы. Пообедала хорошо, давление вечером 165/98 — дала лекарства по расписанию, утром перемеряем. Немного жаловалась на колено.', mood: 'meh', by: 'Анна', at: now - 3 * 3600e3 }), A({ text: 'Гуляли полчаса во дворе, настроение отличное, вспоминала дачу 🙂', mood: 'great', by: 'Ольга', at: now - 1.2 * 864e5 }), A({ text: 'Привёз продукты и лекарства на неделю. Чек в расходах.', mood: 'ok', by: 'Иван', at: now - 3 * 864e5 })];
  const E = (title, amount, payer, cat, d) => D.expenses.push(A({ title, amount, payer, cat, date: addDays(t, -d), split: ['Анна', 'Иван', 'Ольга'] }));
  E('Лекарства на месяц', 84.5, 'Анна', 'meds', 2); E('Продукты', 62, 'Иван', 'food', 3); E('Сиделка (2 дня)', 240, 'Анна', 'care', 6); E('Такси в поликлинику', 18, 'Анна', 'ride', 10); E('Тонометр новый', 45, 'Ольга', 'medical', 12); E('Продукты', 55, 'Ольга', 'food', 9);
  D.claims = [A({ title: 'МРТ коленного сустава', type: 'pa', status: 'review', deadline: addDays(t, 4), ref: 'PA-2026-118734', who: 'Анна', notes: 'Направление от терапевта отправлено 05.11. Звонить в страховую, если нет ответа к сроку.' }), A({ title: 'Возврат за тонометр', type: 'claim', status: 'ok', who: 'Ольга' })];
  D.checkins = [A({ who: 'Анна', date: addDays(t, -2), score: 2, note: 'Не высыпаюсь, на мне почти всё' }), A({ who: 'Иван', date: addDays(t, -3), score: 4 })];
  D.docState = { ins: true, id: true, meds: true, contacts: true, keys: true };
  setMe('Анна'); save();
}

/* ---------- запуск ---------- */
(function init() {
  render();
  if (!D.onboarded && !D.patient.name) onboarding();
  pullNativeActions();
  Object.keys(D.doses).forEach(k => { if (k.startsWith(today())) { const [, medId, hm] = k.split('_'); native('markDose', today(), medId, hm.slice(0, 2) + ':' + hm.slice(2), true); } });
  pushReminders();
  setInterval(() => { if (!SHEETS.length && document.visibilityState === 'visible' && !(document.activeElement && /INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName))) render(); }, 60000);
})();
