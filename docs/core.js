/** Shared data contract with android/ScheduleDatabase.java and CloudSync.java. All dates are epoch milliseconds. */
export const CATEGORIES = ['課題','授業','就活','アルバイト','私用'];
export const DAYS = ['日','月','火','水','木','金','土'];
export const DATA_FIELDS = ['syncId','title','note','category','source','fixed','weekly','startMs','endMs','deadlineMs','estimateMin','progress','difficulty','createdMs','updatedMs','deleted'];

export function createEntry(override = {}) {
  const now = Date.now();
  const id = typeof crypto !== 'undefined' && crypto.randomUUID ? crypto.randomUUID() : `web-${now}-${Math.random().toString(36).slice(2)}`;
  return {syncId:id,title:'',note:'',category:'課題',source:'Web',fixed:true,weekly:false,startMs:now,endMs:now+3600000,deadlineMs:0,estimateMin:60,progress:0,difficulty:3,createdMs:now,updatedMs:now,deleted:false,...override};
}
export function normalize(raw) {
  const template = createEntry({syncId:String(raw.syncId || raw.id || '')});
  const e = {...template};
  for (const field of DATA_FIELDS) if (Object.prototype.hasOwnProperty.call(raw, field)) e[field] = raw[field];
  for (const f of ['startMs','endMs','deadlineMs','estimateMin','progress','difficulty','createdMs','updatedMs']) e[f] = Number(e[f]) || 0;
  for (const f of ['fixed','weekly','deleted']) e[f] = Boolean(e[f]);
  for (const f of ['syncId','title','note','category','source']) e[f] = String(e[f] ?? '');
  if (!e.syncId) e.syncId = createEntry().syncId;
  return e;
}
export function dayStart(value) { const d = new Date(value); d.setHours(0,0,0,0); return d.getTime(); }
export function addDays(value, amount) { const d = new Date(value); d.setDate(d.getDate()+amount); return d.getTime(); }
export function weekStart(value) { const d = new Date(dayStart(value)); d.setDate(d.getDate() - ((d.getDay()+6)%7)); return d.getTime(); }
export function toLocalInput(value) { if (!value) return '';const d=new Date(value); const p=n=>String(n).padStart(2,'0');return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`; }
export function fromLocalInput(value) {return value ? new Date(value).getTime() : 0;}
export function fmtTime(ms){return new Intl.DateTimeFormat('ja-JP',{hour:'2-digit',minute:'2-digit',hour12:false}).format(ms);}
export function fmtDate(ms){return new Intl.DateTimeFormat('ja-JP',{month:'long',day:'numeric',weekday:'short'}).format(ms);}
export function fmtDay(ms){return new Intl.DateTimeFormat('ja-JP',{month:'numeric',day:'numeric'}).format(ms);}
export function fmtMonth(ms){return new Intl.DateTimeFormat('ja-JP',{year:'numeric',month:'long'}).format(ms);}

/** Returns visible segments for fixed and weekly events, splitting events that pass midnight. */
export function fixedSegments(entries,start,end) {
  const result = [];
  for (const entry of entries) {
    if (entry.deleted || !entry.fixed || !(entry.endMs>entry.startMs)) continue;
    const duration = entry.endMs - entry.startMs;
    const begins=[];
    if(entry.weekly) {
      // Clamp recurrences to the creation week. This avoids showing a class before registration.
      let cursor=entry.startMs;
      if(cursor+duration<start){const weeks=Math.max(0,Math.floor((start-cursor-duration)/(7*86400000))-1);cursor=addDays(cursor,weeks*7);}
      for(let guard=0;cursor<end && guard<100;guard++,cursor=addDays(cursor,7)) if(cursor+duration>start)begins.push(cursor);
    } else begins.push(entry.startMs);
    for(const beginsAt of begins){const endAt=beginsAt+duration;
      for(let d=dayStart(Math.max(beginsAt,start)); d<end && d<endAt;d=addDays(d,1)){
        const next=addDays(d,1);const a=Math.max(beginsAt,d,start),b=Math.min(endAt,next,end);
        if(b>a)result.push({entry,start:a,end:b,generated:false});
      }
    }
  }
  return result.sort((a,b)=>a.start-b.start);
}
export function prioritizedTasks(entries, now=Date.now()) {
  return entries.filter(e=>!e.deleted&&!e.fixed).sort((a,b)=>{
    const score=e=> (e.progress>=100?-10000:0) + (e.deadlineMs ? Math.max(-80,90-Math.floor((e.deadlineMs-now)/86400000)*8) : 0) + (e.difficulty*8) + (100-e.progress)/8;
    return score(b)-score(a) || a.createdMs-b.createdMs;
  });
}
/** Current/future free-space allocation. Generated blocks are never persisted. */
export function planWeek(entries, week, settings={}, now=Date.now()) {
  const limitEnd=addDays(week,7);
  const fixed=fixedSegments(entries,week,limitEnd);
  const result=[...fixed];
  if (limitEnd <= now) return result;
  const hourFrom=Number(settings.fromHour ?? 8),hourTo=Number(settings.toHour ?? 22);
  const chunk=Math.max(15,Number(settings.chunk ?? 60));
  const planningStart=Math.max(week,dayStart(now));
  for(const task of prioritizedTasks(entries,now)){
    if(task.progress>=100)continue;
    let remaining=Math.ceil(Math.max(0,task.estimateMin)*(100-task.progress)/100);
    for(let d=planningStart;d<limitEnd && remaining>=15;d=addDays(d,1)){
      let cursor=new Date(d);cursor.setHours(hourFrom,0,0,0);
      let ending=new Date(d);ending.setHours(hourTo,0,0,0);if(ending<=cursor)ending= new Date(addDays(ending.getTime(),1));
      cursor=new Date(Math.max(cursor.getTime(),Math.ceil(now/900000)*900000));
      let iterations=0;
      while(cursor<ending && remaining>=15 && iterations++<250){
        const at=cursor.getTime(),deadline=task.deadlineMs||Number.POSITIVE_INFINITY;
        if(at>=deadline)break;
        const conflict=result.find(b=>b.start<at+900000 && b.end>at);
        if(conflict){cursor=new Date(Math.ceil(conflict.end/900000)*900000);continue;}
        const next=result.filter(b=>b.start>at).reduce((n,b)=>Math.min(n,b.start),Math.min(ending.getTime(),deadline));
        const minutes=Math.min(remaining,chunk,Math.floor((next-at)/900000)*15);
        if(minutes<15){cursor=new Date(at+900000);continue;}
        const end=at+minutes*60000;
        result.push({entry:task,start:at,end,generated:true});remaining-=minutes;cursor=new Date(end);
      }
    }
  }
  return result.sort((a,b)=>a.start-b.start);
}
/** Local-first revision merge. Equal timestamps prefer the remote copy. */
export function mergeByRevision(local,remote){ const map=new Map(local.map(e=>[e.syncId,e]));const upload=[];
  for(const entry of remote){const r=normalize(entry),l=map.get(r.syncId);if(!l || r.updatedMs>=l.updatedMs)map.set(r.syncId,r);else upload.push(l);}
  const remoteIds=new Set(remote.map(e=>e.syncId));for(const e of local)if(!remoteIds.has(e.syncId))upload.push(e);
  return {entries:[...map.values()],upload};
}
