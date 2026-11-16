'use strict';
/* =========================================================
   CareShare — экраны
   ========================================================= */
const TABS = [['home', 'Главная', 'home'], ['meds', 'Лекарства', 'pill'], ['health', 'Здоровье', 'health'], ['tasks', 'Задачи', 'tasks'], ['family', 'Семья', 'users']];
const ACT = {};

function render() {
  renderTop(); renderNav();
  const v = $('#view'); v.innerHTML = (VIEWS[UI.tab] || VIEWS.home)(); fillPhotos(v);
  renderFab();
  SHEETS.forEach(s => s.refresh && s.refresh());
}
function go(tab) { UI.tab = tab; saveUI(); render(); window.scrollTo(0, 0); }
function renderTop() {
  const p = D.patient, a = age(p.dob);
  $('#ptAv').textContent = (p.name || '?').charAt(0).toUpperCase();
  $('#ptName').textContent = p.name || 'CareShare';
  $('#ptSub').textContent = p.name ? [a != null ? a + ' ' + plural(a, 'год', 'года', 'лет') : '', 'карточка'].filter(Boolean).join(' · ') : 'Заботимся вместе';
  $('#gearBtn').innerHTML = ico('gear');
}
function renderNav() {
  const al = alerts().filter(a => a.lv === 'red').length, late = D.tasks.filter(x => !x.done && x.due && x.due < today()).length;
  const badge = { home: al, tasks: late };
  $('#tabs').innerHTML = TABS.map(([k, l, i]) => `<button class="${UI.tab === k ? 'on' : ''}" data-act="tab" data-id="${k}"><span class="pill">${ico(i)}</span>${l}${badge[k] ? `<span class="dot">${badge[k]}</span>` : ''}</button>`).join('');
}
function renderFab() {
  const f = $('#fab'); const map = { home: 'Добавить', meds: 'Лекарство', tasks: 'Задача' };
  const hmap = { visits: 'Визит', doctors: 'Врач', labs: 'Анализ', vitals: 'Показатель', docs: 'Документ', insurance: 'Дело' };
  const fmap = { news: null, money: 'Расход', load: 'Задача', people: 'Участник' };
  let label = UI.tab === 'health' ? hmap[UI.health] : UI.tab === 'family' ? fmap[UI.family] : map[UI.tab];
  f.style.display = label === null ? 'none' : '';
  f.innerHTML = ico('plus') + (label && UI.tab !== 'home' ? `<span>${label}</span>` : '');
}
const chips = (items, cur, act) => `<div class="chips">${items.map(([k, l, n]) => `<button class="chip ${cur === k ? 'on' : ''}" data-act="${act}" data-id="${k}">${l}${n ? `<span class="n">${n}</span>` : ''}</button>`).join('')}</div>`;
const empty = (icon, text) => `<div class="empty">${ico(icon)}${text}</div>`;
const sec = (title, right = '') => `<div class="sec"><h3>${title}</h3>${right}</div>`;
const dueTag = d => { if (!d) return ''; const t = today(); if (d < t) return '<span class="tag red">Просрочено</span>'; if (d === t) return '<span class="tag">Сегодня</span>'; if (d === addDays(t, 1)) return '<span class="tag blue">Завтра</span>'; return `<span class="tag gray">${fmtD(d)}</span>`; };

/* ---------- ГЛАВНАЯ ---------- */
function vHome() {
  const p = D.patient, h = new Date().getHours();
  const greet = (h < 6 ? 'Доброй ночи' : h < 12 ? 'Доброе утро' : h < 18 ? 'Добрый день' : 'Добрый вечер') + (ME ? ', ' + ME : '');
  const ds = todayDoses(), got = ds.filter(d => d.dose).length, pct = ds.length ? got / ds.length : 0, C = 2 * Math.PI * 36;
  const na = upcomingAppts()[0], tasksToday = D.tasks.filter(x => !x.done && x.due && x.due <= today()).length;
  const bpArr = vitalsOf('bp'), bp = bpArr[bpArr.length - 1];
  let html = `<div class="hero"><div class="hi">${greet}</div><h2>${p.name ? `${esc(p.name)} — как дела сегодня` : 'Добро пожаловать'}</h2>
    <div class="stats"><div class="ring"><svg viewBox="0 0 86 86"><circle cx="43" cy="43" r="36" stroke="rgba(255,255,255,.25)" stroke-width="9" fill="none"/><circle cx="43" cy="43" r="36" stroke="#fff" stroke-width="9" fill="none" stroke-linecap="round" stroke-dasharray="${(C * pct).toFixed(1)} ${C.toFixed(1)}"/></svg><div class="v">${got}/${ds.length}<small>лекарства</small></div></div>
    <div class="kv"><div>${ico('cal')}<span class="ell">${na ? `${fmtDS(na.date)}${na.time ? ' ' + na.time : ''} · ${esc(docName(na.doctorId) || na.clinic || na.reason || 'визит')}` : 'Визитов не запланировано'}</span></div>
    <div>${ico('tasks')}<span>${tasksToday ? `Задач на сегодня: ${tasksToday}` : 'На сегодня задач нет'}</span></div>
    <div>${ico('heart')}<span>${bp ? `Давление ${bp.v1}/${bp.v2} · ${ago(bp.at)}` : 'Давление не записано'}</span></div></div></div></div>`;

  const al = alerts();
  if (al.length) {
    const show = UI.allAlerts ? al : al.slice(0, 4);
    html += sec('Требует внимания', al.length > 4 ? `<button class="lnk" data-act="all-alerts">${UI.allAlerts ? 'Свернуть' : 'Все ' + al.length}</button>` : '');
    html += show.map(a => `<button class="alert ${a.lv}" data-act="alert" data-k="${a.act}" data-id="${esc(a.arg)}"><span class="ic">${ico(a.ic)}</span><span class="grow"><div class="t">${esc(a.t)}</div><div class="m">${esc(a.m)}</div></span>${ico('chev', 's')}</button>`).join('');
  }
  if (ME && !myCheckin()) html += `<div class="card" style="margin-top:12px"><div class="b">Как вы сами на этой неделе?</div><div class="small muted" style="margin:2px 0 10px">Уход — это марафон. Отметка видна семье, чтобы вовремя подхватить.</div>
    <div class="scale">${[[1, '😫', 'тяжело'], [2, '😟', ''], [3, '😐', 'норм'], [4, '🙂', ''], [5, '😊', 'отлично']].map(([v, e, l]) => `<button data-act="checkin" data-id="${v}">${e}<small>${l || '&nbsp;'}</small></button>`).join('')}</div></div>`;

  // план на сегодня
  const items = [];
  const nowM = tmin(nowTime());
  ds.forEach(d => items.push({ m: tmin(d.time), html: `<div class="tl"><div class="tm ${!d.dose && nowM - tmin(d.time) > 60 ? 'late' : ''}">${d.time}</div><div class="grow"><div class="b ell">${esc(d.med.name)} <span class="muted small">${esc(d.med.dose || '')}</span></div><div class="small muted">${d.dose ? `${ico('check', 'xs')} ${esc(d.dose.by)} · ${new Date(d.dose.at).toTimeString().slice(0, 5)}` : [d.med.per > 1 ? d.med.per + ' шт.' : '', d.med.food && d.med.food !== 'any' ? FOOD[d.med.food] : ''].filter(Boolean).join(' · ') || 'лекарство'}</div></div><button class="take ${d.dose ? 'on' : ''}" data-act="dose" data-id="${d.med.id}" data-time="${d.time}">${d.dose ? ico('check', 's') + 'Дано' : 'Отметить'}</button></div>` }));
  D.appts.filter(a => a.date === today() && !a.done).forEach(a => items.push({ m: tmin(a.time || '09:00'), html: `<div class="tl" data-act="appt" data-id="${a.id}"><div class="tm">${a.time || '—'}</div><span class="tile date" style="width:36px;height:36px">${ico('steth', 's')}</span><div class="grow"><div class="b ell">Визит: ${esc(docName(a.doctorId) || a.reason || 'врач')}</div><div class="small muted ell">${esc([a.clinic, a.who ? 'идёт ' + a.who : ''].filter(Boolean).join(' · '))}</div></div>${ico('chev', 's')}</div>` }));
  D.tasks.filter(x => !x.done && x.due && x.due <= today()).forEach(x => items.push({ m: 2000, html: `<div class="tl"><div class="tm ${x.due < today() ? 'late' : ''}">${x.due < today() ? 'срок' : 'день'}</div><button class="chk" data-act="task-toggle" data-id="${x.id}">${ico('check')}</button><div class="grow" data-act="task" data-id="${x.id}"><div class="b ell">${esc(x.title)}</div><div class="small muted">${esc(x.assignee || 'Любой')} ${x.due < today() ? '· просрочено с ' + fmtD(x.due) : ''}</div></div></div>` }));
  items.sort((a, b) => a.m - b.m);
  html += sec('План на сегодня', `<small>${fmtDW(today())}</small>`);
  html += `<div class="card flat">${items.length ? items.map(i => i.html).join('') : empty('sparkle', 'На сегодня ничего не запланировано.<br>Добавьте лекарства, визиты или задачи.')}</div>`;

  const ups = upcomingAppts().filter(a => a.date > today()).slice(0, 2);
  if (ups.length) html += sec('Ближайшие визиты', `<button class="lnk" data-act="goto" data-id="health:visits">Все</button>`) + `<div class="card flat">${ups.map(apptRow).join('')}</div>`;

  const posts = D.log.slice().sort((a, b) => b.at - a.at).slice(0, 2);
  html += sec('Новости семьи', `<button class="lnk" data-act="goto" data-id="family:news">Все</button>`);
  html += `<div class="card">${posts.length ? posts.map(postHtml).join('') : empty('msg', 'Пока нет новостей. Расскажите, как прошёл день.')}</div>`;
  html += `<div class="btns" style="margin-top:4px"><button class="btn ghost" data-act="share-summary">${ico('share', 's')}Сводка для семьи</button><button class="btn ghost" data-act="goto" data-id="family:news">${ico('msg', 's')}Написать</button></div>`;
  return html;
}

/* ---------- ЛЕКАРСТВА ---------- */
function vMeds() {
  const meds = activeMeds(), off = D.meds.filter(m => m.active === false);
  let html = `<div class="h1">Лекарства</div><p class="sub">Расписание, запас и сроки рецептов. Напоминания приходят на телефон.</p>`;
  // неделя
  const days = []; for (let i = 6; i >= 0; i--) days.push(addDays(today(), -i));
  html += `<div class="card"><div class="row" style="margin-bottom:10px"><div class="b grow">Приём за 7 дней</div>${(() => { let e = 0, g = 0; days.forEach(d => { const a = adherence(d); e += a.exp; g += a.got; }); return e ? `<span class="tag ${g / e >= .9 ? 'green' : g / e >= .6 ? 'amber' : 'red'}">${Math.round(g / e * 100)}%</span>` : ''; })()}</div>
    <div class="week">${days.map(d => { const a = adherence(d), cls = !a.exp ? '' : a.got >= a.exp ? 'full' : a.got ? 'part' : (d === today() ? '' : 'none'); return `<div><div class="d">${WD[toDate(d).getDay()]}</div><div class="c ${cls}">${a.exp ? a.got + '/' + a.exp : '—'}</div></div>`; }).join('')}</div></div>`;
  if (!meds.length) html += `<div class="card">${empty('pill', 'Добавьте лекарства — приложение будет напоминать о приёме, следить за запасом и сроком рецепта.')}<button class="btn block" data-act="fab">${ico('plus', 's')}Добавить лекарство</button></div>`;
  html += meds.map(medCard).join('');
  if (off.length) html += sec('Отменённые') + `<div class="card flat">${off.map(m => `<button class="li" data-act="med" data-id="${m.id}"><span class="tile" style="background:#F0EBE6;color:var(--muted)">${ico('pill', 's')}</span><div class="grow"><div class="t">${esc(m.name)}</div><div class="m">${esc(m.dose || '')}</div></div>${ico('chev', 's chev')}</button>`).join('')}</div>`;
  return html;
}
function medCard(m) {
  const dl = daysLeft(m), r = rxDays(m), rd = +D.settings.refillDays || 7;
  const stockPct = dl == null ? 0 : Math.min(100, dl / 30 * 100);
  const stockCls = dl == null ? '' : dl <= 2 ? 'red' : dl <= rd ? 'amber' : '';
  const rxCls = r == null ? '' : r <= 3 ? 'red' : r <= 14 ? 'amber' : '';
  return `<div class="card med"><div class="hd"><span class="tile green">${ico('pill')}</span><div class="grow"><div class="nm">${esc(m.name)}</div><div class="small muted">${esc([m.dose, (FORMS.find(f => f[0] === m.form) || [])[1], m.per > 1 ? 'по ' + m.per + ' шт.' : '', m.food && m.food !== 'any' ? FOOD[m.food] : ''].filter(Boolean).join(' · '))}</div></div><button class="icon-btn" style="box-shadow:none;background:var(--surface2)" data-act="med" data-id="${m.id}" aria-label="Изменить">${ico('edit', 's')}</button></div>
  <div class="times">${(m.times || []).map(t => { const d = getDose(today(), m.id, t); return `<button class="tag ${d ? 'green' : 'gray'}" style="border:none;padding:6px 10px;font-size:13px" data-act="dose" data-id="${m.id}" data-time="${t}">${d ? '✓ ' : ''}${t}</button>`; }).join('') || '<span class="small muted">по необходимости</span>'}</div>
  <div class="facts">
    <div class="fact ${stockCls}">Запас<b>${m.stock == null ? 'не указан' : `${fmtNum(m.stock)} шт.${dl != null ? ' · ' + dl + ' ' + plural(dl, 'день', 'дня', 'дней') : ''}`}</b>${dl != null ? `<div class="bar"><i class="${stockCls}" style="width:${stockPct}%"></i></div>` : ''}</div>
    <div class="fact ${rxCls}">Рецепт до<b>${m.rx ? fmtD(m.rx) + (r < 0 ? ' · истёк' : '') : 'не указан'}</b>${m.refills != null && m.refills !== '' ? `<span class="small">продлений: ${m.refills}</span>` : ''}</div>
  </div>
  ${m.doctorId || m.pharmacy ? `<div class="small muted" style="margin-top:10px">${[m.doctorId ? 'Назначил: ' + esc(docName(m.doctorId)) : '', m.pharmacy ? 'Аптека: ' + esc(m.pharmacy) : ''].filter(Boolean).join('<br>')}</div>` : ''}
  <div class="btns" style="margin-top:12px"><button class="btn sm sec2" data-act="refill" data-id="${m.id}">${ico('box', 's')}Пополнить</button>${rxCls ? `<button class="btn sm sec2" data-act="rx-task" data-id="${m.id}">${ico('file', 's')}Задача: рецепт</button>` : ''}${m.doctorId ? `<button class="btn sm ghost" data-act="doctor" data-id="${m.doctorId}">${ico('msg', 's')}Врачу</button>` : ''}</div></div>`;
}

/* ---------- ЗДОРОВЬЕ ---------- */
function vHealth() {
  const hs = [['visits', 'Визиты'], ['doctors', 'Врачи'], ['labs', 'Анализы'], ['vitals', 'Показатели'], ['docs', 'Документы'], ['insurance', 'Страховка']];
  const titles = { visits: ['Визиты к врачам', 'Напоминания, вопросы к врачу и итоги каждого визита.'], doctors: ['Врачи', 'Контакты и переписка с лечащими врачами.'], labs: ['Анализы', 'Результаты, нормы и динамика.'], vitals: ['Показатели', 'Давление, пульс, сахар и другое — с графиками.'], docs: ['Документы', 'Всё важное под рукой и экстренный лист.'], insurance: ['Страховка', 'Полис и дела со страховой: одобрения, заявления, апелляции.'] };
  const [t, s] = titles[UI.health] || titles.visits;
  return `<div class="h1">${t}</div><p class="sub">${s}</p>` + chips(hs, UI.health, 'health') + ({ visits: hVisits, doctors: hDoctors, labs: hLabs, vitals: hVitals, docs: hDocs, insurance: hInsurance }[UI.health] || hVisits)();
}
function apptRow(a) {
  const d = toDate(a.date), dd = daysBetween(today(), a.date);
  return `<button class="li" data-act="appt" data-id="${a.id}"><span class="tile date"><b>${d.getDate()}</b><span>${MON[d.getMonth()]}</span></span><div class="grow"><div class="t ell">${esc(docName(a.doctorId) || a.reason || 'Визит к врачу')}</div><div class="m ell">${esc([a.time, a.clinic, a.who ? 'идёт ' + a.who : ''].filter(Boolean).join(' · '))}</div></div>${!a.done && dd >= 0 && dd <= 1 ? `<span class="tag blue">${dd ? 'завтра' : 'сегодня'}</span>` : a.done ? '<span class="tag green">был</span>' : ''}${ico('chev', 's chev')}</button>`;
}
function hVisits() {
  const up = upcomingAppts(), past = pastAppts().slice(0, 20);
  return `<div class="btns" style="margin-bottom:12px"><button class="btn sm ghost" data-act="ics">${ico('download', 's')}Импорт из приглашения клиники (.ics)</button></div>
  <div class="card flat">${up.length ? up.map(apptRow).join('') : empty('cal', 'Нет запланированных визитов')}</div>
  ${past.length ? sec('Прошедшие') + `<div class="card flat">${past.map(apptRow).join('')}</div>` : ''}`;
}
function hDoctors() {
  return `<div class="note">${ico('info', 's')}<div>Сообщения врачу отправляются через почту, SMS или портал клиники, а вся переписка хранится здесь. Прямое подключение к порталам клиник (MyChart и др.) — в разработке.</div></div>
  <div class="card flat">${D.doctors.length ? D.doctors.map(d => { const ms = D.messages.filter(x => x.doctorId === d.id).sort((a, b) => b.at - a.at)[0]; return `<button class="li" data-act="doctor" data-id="${d.id}"><span class="tile violet">${ico('steth', 's')}</span><div class="grow"><div class="t">${esc(d.name)}</div><div class="m ell">${esc(ms ? (ms.dir === 'in' ? 'Врач: ' : 'Вы: ') + ms.text : [d.spec, d.clinic].filter(Boolean).join(' · '))}</div></div>${ico('chev', 's chev')}</button>`; }).join('') : empty('steth', 'Добавьте лечащих врачей: телефон, почту, портал клиники')}</div>`;
}
function hLabs() {
  const gs = labGroups();
  return `<div class="card flat">${gs.length ? gs.map(g => { const f = labFlag(g.last); return `<button class="li" data-act="lab" data-id="${esc(g.name)}"><span class="tile ${f ? 'amber' : 'blue'}">${ico('flask', 's')}</span><div class="grow"><div class="t">${esc(g.name)}</div><div class="m">${fmtD(g.last.date)} · ${g.arr.length} ${plural(g.arr.length, 'результат', 'результата', 'результатов')}</div></div><div style="text-align:right;width:96px"><div class="b ${f ? 'flag-' + f : ''}">${fmtNum(g.last.value)} <span class="small muted">${esc(g.last.unit || '')}</span></div>${f ? `<span class="tag ${f === 'hi' ? 'red' : 'blue'}">${f === 'hi' ? '↑ выше' : '↓ ниже'}</span>` : '<span class="tag green">норма</span>'}</div></button>`; }).join('') : empty('flask', 'Добавляйте результаты анализов — увидите динамику и отклонения от нормы')}</div>
  <div class="note" style="margin-top:4px">${ico('info', 's')}<div>Автоматическая загрузка результатов из лаборатории и портала клиники — в разработке. Сейчас можно внести значение и прикрепить фото бланка.</div></div>`;
}
function hVitals() {
  return `<div class="grid2">${Object.entries(VITALS).map(([k, V]) => { const a = vitalsOf(k), v = a[a.length - 1], f = v ? V.chk(v.v1, v.v2) : ''; return `<button class="vt" data-act="vital" data-id="${k}"><div class="k">${ico(V.icon, 'xs')}${V.n}</div><div class="val ${f ? (f.startsWith('hi') ? 'flag-hi' : 'flag-lo') : ''}">${v ? vitalStr(v) : '—'}<small>${v ? V.u : ''}</small></div><div class="small muted">${v ? ago(v.at) : 'нет данных'}</div>${spark(a.slice(-12).map(x => x.v1), { color: f ? '#C2412D' : '#C0583A' })}</button>`; }).join('')}</div>`;
}
function hDocs() {
  const n = DOCS.filter(([k]) => D.docState[k]).length;
  return `<div class="card"><div class="row"><span class="tile red">${ico('alert', 's')}</span><div class="grow"><div class="b">Экстренный лист</div><div class="small muted">Аллергии, диагнозы, лекарства, врачи и контакты — одна страница для скорой и больницы.</div></div></div><div class="btns" style="margin-top:12px"><button class="btn sm" data-act="print-emergency">${ico('print', 's')}PDF / печать</button><button class="btn sm ghost" data-act="patient-edit">${ico('edit', 's')}Данные</button></div></div>
  ${sec('Фото документов', `<button class="lnk" data-act="doc-add">Добавить</button>`)}
  ${D.docs.length ? `<div class="photos">${D.docs.map(d => `<button class="ph" data-act="doc" data-id="${d.id}"><img data-ph="${d.photo}" src="" alt=""><span>${esc(d.title)}</span></button>`).join('')}</div>` : `<div class="card">${empty('camera', 'Сфотографируйте страховые карты, паспорт, доверенности — чтобы они всегда были под рукой')}</div>`}
  ${sec('Чек-лист документов', `<small>готово ${n} из ${DOCS.length}</small>`)}
  <div class="card flat">${DOCS.map(([k, t]) => `<div class="li ${D.docState[k] ? 'done' : ''}"><button class="chk ${D.docState[k] ? 'on' : ''}" data-act="doc-check" data-id="${k}">${ico('check')}</button><div class="grow t" style="font-weight:500">${esc(t)}</div></div>`).join('')}</div>`;
}
function hInsurance() {
  const p = D.patient;
  const open = D.claims.filter(c => !['ok', 'denied'].includes(c.status)).sort((a, b) => (a.deadline || '9999').localeCompare(b.deadline || '9999')), closed = D.claims.filter(c => ['ok', 'denied'].includes(c.status));
  const row = c => { const st = CLAIM_ST[c.status] || CLAIM_ST.prep; return `<button class="li" data-act="claim" data-id="${c.id}"><span class="tile blue">${ico('shield', 's')}</span><div class="grow"><div class="t">${esc(c.title)}</div><div class="m">${esc([CLAIM_TYPES[c.type], c.who].filter(Boolean).join(' · '))}</div></div><div style="text-align:right"><span class="tag ${st[1]}">${st[0]}</span>${c.deadline && !['ok', 'denied'].includes(c.status) ? `<div class="small muted" style="margin-top:3px">до ${fmtD(c.deadline)}</div>` : ''}</div></button>`; };
  return `<div class="card"><div class="row"><span class="tile blue">${ico('shield')}</span><div class="grow"><div class="b">${esc(p.insPlan || 'Полис не указан')}</div><div class="small muted">${p.insId ? 'Номер: ' + esc(p.insId) : 'Добавьте данные страховки'}</div></div><button class="icon-btn" style="box-shadow:none;background:var(--surface2)" data-act="patient-edit">${ico('edit', 's')}</button></div>${p.insPhone ? `<button class="btn sm ghost block" style="margin-top:12px" data-act="call" data-id="${esc(p.insPhone)}">${ico('phone', 's')}Позвонить в страховую ${esc(p.insPhone)}</button>` : ''}</div>
  ${sec('Открытые дела')}<div class="card flat">${open.length ? open.map(row).join('') : empty('shield', 'Ведите здесь предварительные одобрения, заявления и апелляции — со сроками и ответственным')}</div>
  ${closed.length ? sec('Завершённые') + `<div class="card flat">${closed.map(row).join('')}</div>` : ''}`;
}

/* ---------- ЗАДАЧИ ---------- */
function taskRow(x) {
  const c = TASK_CATS[x.cat] || TASK_CATS.other;
  return `<div class="li ${x.done ? 'done' : ''}"><button class="chk ${x.done ? 'on' : ''}" data-act="task-toggle" data-id="${x.id}" aria-label="Выполнено">${ico('check')}</button>
  <div class="grow" data-act="task" data-id="${x.id}"><div class="t">${esc(x.title)}</div><div class="m row wrap" style="gap:6px;margin-top:4px">${x.done ? `${avatar(x.doneBy, '')}<span>${esc(x.doneBy || 'кто-то')} · ${ago(x.doneAt)}</span>` : `${x.assignee ? avatar(x.assignee) + `<span>${esc(x.assignee)}</span>` : '<span>Любой</span>'} ${dueTag(x.due)} <span class="tag gray">${c[0]}</span>${x.minutes ? `<span>${ico('clock', 'xs')} ${x.minutes >= 60 ? (x.minutes / 60).toFixed(x.minutes % 60 ? 1 : 0) + ' ч' : x.minutes + ' мин'}</span>` : ''}${x.repeat && x.repeat !== 'none' ? ico('refresh', 'xs') : ''}`}</div></div>
  ${!x.done && !x.assignee && ME ? `<button class="btn sm sec2" data-act="task-take" data-id="${x.id}">Беру</button>` : ''}</div>`;
}
function vTasks() {
  const t = today(), wk = addDays(t, 7);
  let list = D.tasks.slice();
  const f = UI.tf;
  if (f === 'mine') list = list.filter(x => !x.done && x.assignee === ME);
  else if (f === 'free') list = list.filter(x => !x.done && !x.assignee);
  else if (f === 'done') list = list.filter(x => x.done);
  else list = list.filter(x => !x.done);
  const mine = D.tasks.filter(x => !x.done && x.assignee === ME).length, free = D.tasks.filter(x => !x.done && !x.assignee).length;
  let html = `<div class="h1">Задачи</div><p class="sub">Визиты, покупки, звонки и бумаги — распределите, чтобы никто не тянул всё в одиночку.</p>` + chips([['all', 'Открытые'], ['mine', 'Мои', mine], ['free', 'Без исполнителя', free], ['done', 'Выполненные']], f, 'tf');
  if (f === 'done') { list.sort((a, b) => (b.doneAt || 0) - (a.doneAt || 0)); return html + `<div class="card flat">${list.length ? list.slice(0, 50).map(taskRow).join('') : empty('tasks', 'Здесь появятся выполненные задачи')}</div>`; }
  const groups = [['Просрочено', x => x.due && x.due < t], ['Сегодня', x => x.due === t], ['На неделе', x => x.due > t && x.due <= wk], ['Позже', x => x.due > wk], ['Без срока', x => !x.due]];
  let any = false;
  groups.forEach(([g, fn]) => { const arr = list.filter(fn).sort((a, b) => (a.due || '').localeCompare(b.due || '') || (a.c || 0) - (b.c || 0)); if (!arr.length) return; any = true; html += sec(g, `<small>${arr.length}</small>`) + `<div class="card flat">${arr.map(taskRow).join('')}</div>`; });
  if (!any) html += `<div class="card">${empty('tasks', f === 'mine' ? 'У вас нет открытых задач' : 'Открытых задач нет')}</div>`;
  return html;
}

/* ---------- СЕМЬЯ ---------- */
function vFamily() {
  const t = { news: ['Новости', 'Одно место для «как мама сегодня?» — вместо пяти чатов.'], money: ['Общие расходы', 'Кто сколько потратил и кто кому должен.'], load: ['Нагрузка', 'Как распределяется забота за последние 30 дней.'], people: ['Семья', 'Участники, роли и самочувствие ухаживающих.'] }[UI.family] || ['', ''];
  return `<div class="h1">${t[0]}</div><p class="sub">${t[1]}</p>` + chips([['news', 'Новости'], ['money', 'Расходы'], ['load', 'Нагрузка'], ['people', 'Участники']], UI.family, 'family') + ({ news: fNews, money: fMoney, load: fLoad, people: fPeople }[UI.family] || fNews)();
}
function postHtml(l) {
  const md = MOODS.find(m => m[0] === l.mood);
  return `<div class="post"><div class="row">${avatar(l.by)}<div class="grow"><b>${esc(l.by)}</b> <span class="small muted">· ${ago(l.at)}</span></div>${md ? `<span title="${md[2]}" style="font-size:20px">${md[1]}</span>` : ''}${UI.tab === 'family' ? `<button class="icon-btn" style="width:32px;height:32px;box-shadow:none;background:none" data-act="post-del" data-id="${l.id}" aria-label="Удалить">${ico('x', 's')}</button>` : ''}</div><p>${esc(l.text)}</p>${l.photo ? `<img data-ph="${l.photo}" src="" alt="" data-act="photo" data-id="${l.photo}">` : ''}</div>`;
}
let draft = { mood: '', photo: '' };
function fNews() {
  const posts = D.log.slice().sort((a, b) => b.at - a.at);
  return `<div class="card"><textarea class="inp" id="postText" maxlength="2000" placeholder="Как прошёл день? Например: заезжала днём, пообедала, немного устала, спрашивала про огород.">${esc(draft.text || '')}</textarea>
    <div class="moods" style="margin:10px 0">${MOODS.map(([k, e, l]) => `<button class="mood ${draft.mood === k ? 'on' : ''}" data-act="mood" data-id="${k}">${e} ${l}</button>`).join('')}</div>
    ${draft.photo ? `<div class="phbox" style="margin-bottom:10px"><img data-ph="${draft.photo}" src="" alt=""><button class="btn sm ghost" data-act="post-photo-x">Убрать фото</button></div>` : ''}
    <div class="btns"><button class="btn ghost" style="flex:0 0 auto" data-act="post-photo" aria-label="Фото">${ico('camera', 's')}</button><button class="btn" data-act="post">${ico('send', 's')}Опубликовать</button></div></div>
  <div class="card">${posts.length ? posts.slice(0, 60).map(postHtml).join('') : empty('msg', 'Новостей пока нет. Будьте первым!')}</div>`;
}
function fMoney() {
  const b = balances(), sug = settleSuggest(b), names = memberNames();
  const exp = D.expenses.slice().sort((a, b) => (b.date || '').localeCompare(a.date || '') || (b.c || 0) - (a.c || 0));
  const m0 = today().slice(0, 7), month = D.expenses.filter(e => e.kind !== 'settle' && (e.date || '').startsWith(m0));
  const tot = month.reduce((s, e) => s + (+e.amount || 0), 0), byCat = {}; month.forEach(e => byCat[e.cat || 'other'] = (byCat[e.cat || 'other'] || 0) + (+e.amount || 0));
  const my = ME && b[ME] != null ? b[ME] : null;
  let html = `<div class="card"><div class="small muted">Ваш баланс</div><div style="font-size:30px;font-weight:800;letter-spacing:-.02em" class="amt ${my > 0.009 ? 'pos' : my < -0.009 ? 'neg' : ''}">${my == null ? '—' : Math.abs(my) < 0.01 ? 'Всё ровно' : money(my)}</div><div class="small muted">${my == null ? 'Выберите себя в настройках' : my > 0.009 ? 'Вам должны' : my < -0.009 ? 'Вы должны' : 'Никто никому не должен'}</div></div>`;
  if (names.length) html += sec('Кто кому должен') + `<div class="card flat">${sug.length ? sug.map(s => `<div class="bal">${avatar(s.from)}<div class="grow"><b>${esc(s.from)}</b> → <b>${esc(s.to)}</b><div class="small muted">чтобы выровнять расходы</div></div><div style="text-align:right"><div class="amt">${money(s.amt)}</div><button class="btn sm sec2" style="margin-top:4px" data-act="settle" data-from="${esc(s.from)}" data-to="${esc(s.to)}" data-amt="${s.amt}">Оплачено</button></div></div>`).join('') : `<div class="empty">${ico('check')}Все в расчёте</div>`}</div>`;
  html += sec('Этот месяц', `<small>${money(tot)}</small>`) + `<div class="card">${Object.keys(byCat).length ? Object.entries(byCat).sort((a, b) => b[1] - a[1]).map(([k, v]) => `<div class="fair" style="grid-template-columns:120px 1fr 70px"><span class="ell">${EXP_CATS[k] || k}</span><div class="track"><i style="width:${v / tot * 100}%;background:var(--brand)"></i></div><span class="v">${money(v)}</span></div>`).join('') : '<div class="empty">Расходов в этом месяце нет</div>'}</div>`;
  html += sec('Все операции') + `<div class="card flat">${exp.length ? exp.slice(0, 80).map(e => e.kind === 'settle' ? `<div class="li"><span class="tile green">${ico('check', 's')}</span><div class="grow"><div class="t">${esc(e.payer)} → ${esc(e.to)}</div><div class="m">Возврат долга · ${fmtD(e.date)}</div></div><span class="amt">${money(e.amount)}</span><button class="icon-btn" style="width:32px;height:32px;box-shadow:none;background:none" data-act="exp-del" data-id="${e.id}">${ico('x', 's')}</button></div>` : `<button class="li" data-act="expense" data-id="${e.id}">${e.photo ? `<img class="thumb" style="width:42px;height:42px" data-ph="${e.photo}" src="" alt="">` : `<span class="tile amber">${ico('wallet', 's')}</span>`}<div class="grow"><div class="t ell">${esc(e.title)}</div><div class="m">${esc(e.payer)} · ${EXP_CATS[e.cat] || ''} · ${fmtD(e.date)}</div></div><span class="amt">${money(e.amount)}</span></button>`).join('') : empty('wallet', 'Записывайте траты на лекарства, сиделку, продукты — приложение посчитает, кто кому должен')}</div>`;
  return html;
}
function fLoad() {
  const r = fairness(30), names = Object.keys(r);
  if (!names.length) return `<div class="card">${empty('users', 'Добавьте членов семьи, чтобы видеть распределение нагрузки')}</div>`;
  const tot = { tasks: 0, min: 0, money: 0 }; names.forEach(n => { tot.tasks += r[n].tasks; tot.min += r[n].min; tot.money += r[n].money; });
  const block = (title, key, fmt) => { const mx = Math.max(1, ...names.map(n => r[n][key])); return `<div class="card"><div class="row" style="margin-bottom:4px"><div class="b grow">${title}</div><span class="small muted">всего ${fmt(tot[key])}</span></div>${names.sort((a, b) => r[b][key] - r[a][key]).map(n => `<div class="fair"><span class="row" style="gap:6px;min-width:0">${avatar(n)}<span class="ell">${esc(n)}</span></span><div class="track"><i style="width:${r[n][key] / mx * 100}%;background:${memberColor(n)}"></i></div><span class="v">${fmt(r[n][key])}</span></div>`).join('')}</div>`; };
  let ins = '';
  if (names.length > 1 && tot.tasks >= 3) { const top = names.slice().sort((a, b) => r[b].tasks - r[a].tasks)[0], p = Math.round(r[top].tasks / tot.tasks * 100); if (p >= 55) ins = `<div class="note amber">${ico('alert', 's')}<div><b>${esc(top)}</b> выполняет ${p}% всех задач. Возьмите часть дел на себя — например, задачи «Без исполнителя».</div></div>`; }
  if (!ins && tot.tasks >= 3) ins = `<div class="note">${ico('check', 's')}<div>Нагрузка распределяется довольно равномерно. Так держать!</div></div>`;
  const ci = latestCheckins();
  return ins + block('Выполнено задач', 'tasks', v => v) + block('Время на уход', 'min', v => v >= 60 ? Math.round(v / 60) + ' ч' : v + ' мин') + block('Потрачено денег', 'money', v => money(v)) +
    sec('Самочувствие ухаживающих') + `<div class="card flat">${Object.values(ci).length ? Object.values(ci).sort((a, b) => a.score - b.score).map(c => `<div class="li">${avatar(c.who)}<div class="grow"><div class="t">${esc(c.who)}</div><div class="m">${fmtDS(c.date)}${c.note ? ' · ' + esc(c.note) : ''}</div></div><span style="font-size:24px">${['', '😫', '😟', '😐', '🙂', '😊'][c.score]}</span></div>`).join('') : empty('smile', 'Пока никто не отмечал самочувствие')}</div>`;
}
function fPeople() {
  return `<div class="card flat">${D.members.length ? D.members.map(m => `<button class="li" data-act="member" data-id="${m.id}">${avatar(m.name, 'lg')}<div class="grow"><div class="t">${esc(m.name)}${m.name === ME ? ' <span class="tag">это вы</span>' : ''}</div><div class="m">${esc([m.rel, ROLES[m.role]].filter(Boolean).join(' · '))}</div></div>${m.phone ? `<span class="icon-btn" style="box-shadow:none;background:var(--green-s);color:var(--green)" data-act="call" data-id="${esc(m.phone)}">${ico('phone', 's')}</span>` : ''}</button>`).join('') : empty('users', 'Добавьте себя, братьев, сестёр и всех, кто помогает')}</div>
  ${sec('Общий доступ')}
  <div class="note">${ico('info', 's')}<div>В этой версии данные хранятся на телефоне. Чтобы поделиться с семьёй, отправьте файл данных — родственник откроет его в CareShare, и данные объединятся. Синхронизация в реальном времени появится с облачной версией.</div></div>
  <div class="btns"><button class="btn ghost" data-act="export">${ico('upload', 's')}Отправить данные</button><button class="btn ghost" data-act="import">${ico('download', 's')}Загрузить файл</button></div>`;
}

const VIEWS = { home: vHome, meds: vMeds, health: vHealth, tasks: vTasks, family: vFamily };
