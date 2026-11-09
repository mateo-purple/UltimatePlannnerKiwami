import {CATEGORIES,DAYS,createEntry,normalize,dayStart,weekStart,addDays,fmtDay,fmtMonth,fmtTime,fmtDate,toLocalInput,fromLocalInput,planWeek,prioritizedTasks,mergeByRevision} from './core.js';
import {CloudClient} from './sync.js';
const $=id=>document.getElementById(id);
const STORAGE_PREFIX='upk-entries-v2:';
const SETTINGS_KEY='upk-work-settings-v1';
const FIREBASE_KEY='upk-firebase-config-v1';
const ONE_HOUR=3600000;
let owner='guest',entries=readEntries('guest'),activeTab='week',selectedWeek=weekStart(Date.now()),editing=null;
let settings=JSON.parse(localStorage.getItem(SETTINGS_KEY)||'null') || {fromHour:8,toHour:22,chunk:60};
let firebaseConfig=JSON.parse(localStorage.getItem(FIREBASE_KEY)||'null') || {};
let cloud;
let toastTimer;
function readEntries(key){try {const value=JSON.parse(localStorage.getItem(STORAGE_PREFIX+key)||'[]');return Array.isArray(value)?value.map(normalize):[];}catch{return [];}}
function saveEntries(){localStorage.setItem(STORAGE_PREFIX+owner,JSON.stringify(entries));}
function toast(message){const el=$('toast');el.textContent=message;el.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>el.classList.remove('show'),3400);}
function element(tag,className,text){const el=document.createElement(tag);if(className)el.className=className;if(text!==undefined)el.textContent=text;return el;}
function updateStatus(label){$('sync-status').textContent=label;}
function currentEntries(){return entries.filter(e=>!e.deleted);}
function render(){
  const titles={week:['週間予定表','空いている時間がひと目で分かる週間ビュー'],tasks:['タスク管理','期限の近い課題から効率よく進めましょう'],settings:['設定','予定の自動配置と端末間同期']};
  $('page-title').textContent=titles[activeTab][0];$('page-subtitle').textContent=titles[activeTab][1];
  for(const tab of document.querySelectorAll('[data-tab]')){tab.classList.toggle('active',tab.dataset.tab===activeTab);tab.setAttribute('aria-selected',String(tab.dataset.tab===activeTab));}
  for(const view of ['week','tasks','settings'])$(view+'-view').classList.toggle('hidden',view!==activeTab);
  $('add-btn').classList.toggle('hidden',activeTab==='settings');
  if(activeTab==='week')renderCalendar();if(activeTab==='tasks')renderTasks();if(activeTab==='settings')renderSettings();
}
function selectTab(value){activeTab=value;render();}
function saveChange(entry){
  const old=entries.find(e=>e.syncId===entry.syncId);
  const result=normalize({...entry,updatedMs:Math.max(Date.now(),(old?.updatedMs||0)+1)});
  if(old)entries=entries.map(e=>e.syncId===result.syncId?result:e);else entries.push(result);
  saveEntries();render();if(cloud?.user)cloud.push(result).catch(error=>updateStatus('同期保留: '+(error.code||'接続エラー')));
}
function removeEntry(entry){if(!confirm(`「${entry.title}」を削除しますか？`))return;saveChange({...entry,deleted:true});$('event-dialog').close();toast('予定を削除しました');}
function localHour(ms){const d=new Date(ms);return d.getHours()+d.getMinutes()/60;}
function renderCalendar(){
  const today=dayStart(Date.now()),week=selectedWeek;
  $('week-label').textContent=`${fmtMonth(week)} ${new Date(week).getDate()}日〜${fmtDay(addDays(week,6))}`;
  const grid=$('calendar-grid');grid.replaceChildren();
  const days=Array.from({length:7},(_,i)=>addDays(week,i));
  const header=element('div','grid-header');header.append(element('div','time-header'));
  days.forEach((day,i)=>{
    const h=element('button','day-header'+(day===today?' today':''));h.type='button';h.title='この日に予定を追加';
    h.append(element('span','',DAYS[new Date(day).getDay()]+'曜日'),element('b','',String(new Date(day).getDate())));
    h.onclick=()=>openEvent(null,'fixed',day);header.append(h);
    const column=element('div','day-column'+(day===today?' today':''));column.style.left=`calc(var(--time-width) + ${i} * var(--day-width))`;column.style.pointerEvents='none';grid.append(column);
  });
  for(let hour=0;hour<=24;hour++){
    const label=element('div','time-slot',`${String(hour).padStart(2,'0')}:00`);
    label.style.top=`calc(var(--head-height) + ${hour} * var(--hour-height))`;grid.append(label);
  }
  const blocks=planWeek(currentEntries(),week,settings);
  days.forEach((day,i)=>{
    const dayEnd=addDays(day,1);
    let view=blocks.filter(b=>b.start<dayEnd&&b.end>day).map(b=>({entry:b.entry,start:Math.max(day,b.start),end:Math.min(dayEnd,b.end),generated:b.generated}));
    // When two events overlap, place them beside each other instead of completely hiding one.
    view.sort((a,b)=>a.start-b.start||b.end-a.end);
    view.forEach((block,index)=>{
      const overlapping=view.filter(other=>other.start<block.end&&other.end>block.start);
      const column=overlapping.indexOf(block), count=overlapping.length;
      const start=localHour(block.start),duration=Math.max((block.end-block.start)/ONE_HOUR,0.36);
      const button=element('button','event-block'+(block.generated?' generated':''));button.type='button';
      button.dataset.cat=block.entry.category;button.style.setProperty('--column',i);button.style.setProperty('--top',start);button.style.setProperty('--height',duration);
      // Narrow blocks only for actual collisions. CSS cannot infer overlap count.
      if(count>1){button.style.setProperty('--width',1/count);button.style.setProperty('--offset',`calc(var(--day-width) * ${column/count})`);}
      const title=element('strong','',block.entry.title);button.append(title);
      if(duration>=0.55)button.append(element('small','',`${fmtTime(block.start)}–${fmtTime(block.end)}`));
      button.title=`${block.entry.title} ${fmtTime(block.start)}〜${fmtTime(block.end)}${block.entry.weekly?'（毎週）':''}`;
      button.onclick=()=>openEvent(block.entry);grid.append(button);
    });
  });
  grid.append(header);
  if(!$('grid-scroll').dataset.scrolled){$('grid-scroll').dataset.scrolled='1';requestAnimationFrame(()=>{const scroll=$('grid-scroll');scroll.scrollTop=6*54;scroll.scrollLeft=Math.max(0,((today-week)/86400000)*135-190);});}
}
function renderTasks(){const list=$('task-list');list.replaceChildren();const tasks=prioritizedTasks(currentEntries());
  if(!tasks.length){list.append(element('div','empty','まだ課題がありません。「＋ タスクを追加」から登録してください。'));return;}
  for(const task of tasks){const card=element('article','task-card'+(task.progress>=100?' completed':''));
    card.append(element('h3','',task.title));card.append(element('div','task-meta',`${task.category}　·　${task.deadlineMs?`締切 ${fmtDate(task.deadlineMs)} ${fmtTime(task.deadlineMs)}`:'期限なし'}　·　残り ${Math.ceil(task.estimateMin*(100-task.progress)/100)}分`));
    const track=element('div','progress-track');const progress=element('div');progress.style.width=`${task.progress}%`;track.append(progress);card.append(track);
    const actions=element('div','actions');const edit=element('button','','詳細・編集');edit.onclick=()=>openEvent(task);const done=element('button','done',task.progress>=100?'↶ 未完了に戻す':'✓ 完了にする');done.onclick=()=>saveChange({...task,progress:task.progress>=100?0:100});
    actions.append(edit,element('span','task-meta',task.progress+'%'),done);card.append(actions);list.append(card);
  }
}
function renderSettings(){const p=n=>String(n).padStart(2,'0');$('work-from').value=`${p(settings.fromHour)}:00`;$('work-to').value=`${p(settings.toHour)}:00`;$('work-chunk').value=String(settings.chunk);
  $('firebase-project').value=firebaseConfig.projectId||'';$('firebase-key').value=firebaseConfig.apiKey||'';$('firebase-appid').value=firebaseConfig.appId||'';$('firebase-authdomain').value=firebaseConfig.authDomain||'';
}
function openEvent(entry=null,kind='fixed',startDay=selectedWeek){
  editing=entry?entry.syncId:null;
  const now=new Date();now.setMinutes(0,0,0);now.setHours(Math.max(8,now.getHours()+1));
  const begin=entry?.startMs || (dayStart(startDay)===dayStart(Date.now())?Math.max(startDay+9*ONE_HOUR,now.getTime()):dayStart(startDay)+9*ONE_HOUR);
  const value=entry||createEntry({fixed:kind==='fixed',startMs:begin,endMs:begin+ONE_HOUR});
  $('dialog-title').textContent=entry?'予定を編集':'新しく登録';$('event-title').value=value.title;
  $('event-category').value=value.category||'課題';$('event-kind').value=value.fixed?'fixed':'task';
  $('event-start').value=toLocalInput(value.startMs);$('event-end').value=toLocalInput(value.endMs);$('event-weekly').checked=value.weekly;
  $('event-deadline').value=toLocalInput(value.deadlineMs);$('event-estimate').value=value.estimateMin;
  $('event-difficulty').value=String(value.difficulty);$('event-progress').value=value.progress;$('event-note').value=value.note;
  $('delete-event').classList.toggle('hidden',!entry);applyKind();$('event-dialog').showModal();$('event-title').focus();
}
function applyKind(){const fixed=$('event-kind').value==='fixed';$('fixed-fields').classList.toggle('hidden',!fixed);$('task-fields').classList.toggle('hidden',fixed);$('event-start').required=fixed;$('event-end').required=fixed;}
function saveEvent(event){event.preventDefault();const title=$('event-title').value.trim();if(!title){$('event-title').reportValidity();return;}
  const existing=editing?entries.find(e=>e.syncId===editing):null;const e=existing?{...existing}:createEntry();
  e.title=title;e.category=$('event-category').value;e.note=$('event-note').value.trim();e.fixed=$('event-kind').value==='fixed';
  if(e.fixed){e.startMs=fromLocalInput($('event-start').value);e.endMs=fromLocalInput($('event-end').value);e.weekly=$('event-weekly').checked;
    if(!e.startMs || e.endMs<=e.startMs){toast('終了日時は開始日時より後にしてください');return;}}
  else{e.weekly=false;e.deadlineMs=fromLocalInput($('event-deadline').value);e.estimateMin=Number($('event-estimate').value)||60;e.difficulty=Number($('event-difficulty').value);e.progress=Number($('event-progress').value)||0;
    if(e.estimateMin<15||e.estimateMin>100000||e.progress<0||e.progress>100){toast('作業時間・進捗を確認してください');return;}}
  saveChange(e);$('event-dialog').close();toast(existing?'予定を更新しました':'予定を登録しました');
}
function handleRemote(remote){const {entries:merged,upload}=mergeByRevision(entries,remote);entries=merged;saveEntries();render();cloud.pushMany(upload);}
function setCloudUser(user){
  const next=user?'user:'+firebaseConfig.projectId+':'+user.uid:'guest';
  if(user){const original=readEntries(next);const guest=readEntries('guest');const merged=mergeByRevision(original,guest).entries;
    // Guest records are copied once, then removed from the guest workspace.
    if(guest.length){localStorage.removeItem(STORAGE_PREFIX+'guest');localStorage.setItem(STORAGE_PREFIX+next,JSON.stringify(merged));}
  }
  owner=next;entries=readEntries(owner);$('account-button').textContent=user?user.email:'アカウント';render();
  if(user)toast('ログインしました。同期を開始します。');
}
async function initCloud(){cloud=new CloudClient(setCloudUser,handleRemote,updateStatus);
  try{await cloud.initialize(firebaseConfig);}catch(error){updateStatus('Firebase接続に失敗');console.error(error);toast('Firebase設定または通信を確認してください');}
}
function showAccount(){
  const online=Boolean(cloud?.auth);$('account-message').textContent=cloud?.user?`ログイン中: ${cloud.user.email}`:online?'メールとパスワードでログインしてください。':'先に「設定 → クラウド同期」でFirebaseを設定してください。';
  $('auth-fields').classList.toggle('hidden',Boolean(cloud?.user));$('signed-in-fields').classList.toggle('hidden',!cloud?.user);$('account-dialog').showModal();
}
async function login(create){const email=$('auth-email').value.trim(),password=$('auth-password').value;
  if(!email||password.length<6){toast('メールアドレスと6文字以上のパスワードを入力してください');return;}
  try{await cloud.login(email,password,create);$('account-dialog').close();$('auth-password').value='';}
  catch(error){toast('認証できません: '+(error.code||error.message));}
}
function wire(){
  document.querySelectorAll('[data-tab]').forEach(button=>button.onclick=()=>selectTab(button.dataset.tab));
  $('add-btn').onclick=()=>openEvent(null,'fixed',Date.now());$('add-task-btn').onclick=()=>openEvent(null,'task',Date.now());
  $('prev-week').onclick=()=>{selectedWeek=addDays(selectedWeek,-7);render();};$('next-week').onclick=()=>{selectedWeek=addDays(selectedWeek,7);render();};$('today-btn').onclick=()=>{selectedWeek=weekStart(Date.now());render();};
  $('event-kind').onchange=applyKind;$('event-form').addEventListener('submit',saveEvent);
  $('close-dialog').onclick=()=>{$('event-dialog').close();};$('cancel-event').onclick=()=>{$('event-dialog').close();};
  $('delete-event').onclick=()=>{const e=entries.find(e=>e.syncId===editing);if(e)removeEntry(e);};
  $('account-button').onclick=showAccount;$('close-account').onclick=()=>{$('account-dialog').close();};
  $('account-form').onsubmit=e=>{e.preventDefault();login(false);};$('register-btn').onclick=()=>login(true);
  $('signout-btn').onclick=async()=>{await cloud.logout();$('account-dialog').close();toast('ログアウトしました');};
  $('save-settings').onclick=()=>{settings={fromHour:Number($('work-from').value.split(':')[0]),toHour:Number($('work-to').value.split(':')[0]),chunk:Number($('work-chunk').value)};localStorage.setItem(SETTINGS_KEY,JSON.stringify(settings));toast('設定を保存しました');};
  $('save-firebase').onclick=()=>{firebaseConfig={projectId:$('firebase-project').value.trim(),apiKey:$('firebase-key').value.trim(),appId:$('firebase-appid').value.trim(),authDomain:$('firebase-authdomain').value.trim()};localStorage.setItem(FIREBASE_KEY,JSON.stringify(firebaseConfig));location.reload();};
  $('export-btn').onclick=()=>{const blob=new Blob([JSON.stringify({format:'UltimatePlannerKiwami-v2',entries,exportedAt:new Date().toISOString()},null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`ultimate-planner-${new Date().toISOString().slice(0,10)}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),2000);};
  $('import-btn').onclick=()=>$('import-file').click();$('import-file').onchange=async event=>{try{const file=event.target.files[0];if(!file)return;const data=JSON.parse(await file.text());if(!Array.isArray(data.entries))throw Error('entriesがありません');const imported=data.entries.map(normalize);const {entries:merged}=mergeByRevision(entries,imported);entries=merged;saveEntries();render();if(cloud?.user)cloud.pushMany(imported);toast(`${imported.length}件を読み込みました`);}catch(e){toast('読み込み失敗: '+e.message);}event.target.value='';};
  window.addEventListener('online',()=>{if(cloud?.user){updateStatus('再接続中…');render();}else updateStatus('端末内のみ');});
}
wire();render();initCloud();
if('serviceWorker' in navigator && location.protocol==='https:')navigator.serviceWorker.register('./sw.js').catch(console.warn);
