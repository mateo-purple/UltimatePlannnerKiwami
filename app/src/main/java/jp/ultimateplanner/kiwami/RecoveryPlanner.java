package jp.ultimateplanner.kiwami;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Replan without overwriting external due dates; keep original dates in history. */
public final class RecoveryPlanner {
    private RecoveryPlanner(){}
    public static boolean skipped(Entry e,long day){return e.skippedDays!=null && Arrays.asList(e.skippedDays.split(",")).contains(Long.toString(day));}
    public static boolean missed(Entry e,long now){
        if(e.deleted || e.progress>=100)return false;
        long boundary=TimeUtil.plusDay(TimeUtil.day(now),-21);
        if(e.fixed)return !e.weekly && e.endMs<now && e.endMs>=boundary;
        return e.deadlineMs>0&&e.deadlineMs<now&&e.deadlineMs>=boundary;
    }
    public static void remember(Entry e,String reason){
        try{JSONArray array=e.history==null||e.history.isEmpty()?new JSONArray():new JSONArray(e.history);
            JSONObject event=new JSONObject();event.put("at",System.currentTimeMillis());
            event.put("startMs",e.startMs);event.put("endMs",e.endMs);event.put("deadlineMs",e.deadlineMs);event.put("reason",reason);
            array.put(event);e.history=array.toString();
        }catch(Exception ignored){}
    }
    public static String latestHistory(Entry e){
        try{JSONArray items=new JSONArray(e.history);if(items.length()>0){JSONObject h=items.getJSONObject(items.length()-1);
            long time=h.optLong("startMs",h.optLong("deadlineMs",0));return (time>0?TimeUtil.fmt(time,"M/d H:mm")+"  ":"")+h.optString("reason","");}}
        catch(Exception ignored){}return "";
    }
    public static long nextSlot(Entry e,List<Entry> all,long availableDay){
        long day=Math.max(TimeUtil.day(System.currentTimeMillis()),TimeUtil.day(availableDay));
        Calendar orig=Calendar.getInstance();orig.setTimeInMillis(e.startMs);
        int hour=orig.get(Calendar.HOUR_OF_DAY),minute=orig.get(Calendar.MINUTE);
        long duration=Math.max(15*60000L,e.endMs-e.startMs);
        for(int i=0;i<45;i++){
            long begin=TimeUtil.at(day,hour,minute);long end=begin+duration;
            if(begin<=System.currentTimeMillis()+15*60000L){day=TimeUtil.plusDay(day,1);continue;}
            boolean occupied=false;
            for(AutoPlanner.Block b:AutoPlanner.fixedBlocks(all,day,2)){
                if(b.entry.syncId.equals(e.syncId))continue;
                if(b.start<end && b.end>begin){occupied=true;break;}
            }
            if(!occupied)return begin;
            day=TimeUtil.plusDay(day,1);
        }
        return TimeUtil.at(day,hour,minute);
    }
    public static Entry manual(Entry e,List<Entry> all,long now){
        boolean makeCopy=e.progress>=100 || e.weekly;
        Entry target=e;
        if(makeCopy){target=copy(e);target.weekly=false;target.progress=0;target.source="再計画";}
        remember(target,"未完了・履歴から再登録");
        if(target.fixed){long duration=target.endMs-target.startMs;
            target.startMs=nextSlot(target,all,TimeUtil.plusDay(TimeUtil.day(now),1));target.endMs=target.startMs+Math.max(15*60000L,duration);
        }else{target.resumeAfterMs=Math.max(target.resumeAfterMs,TimeUtil.plusDay(TimeUtil.day(now),1));
            if(target.deadlineMs<=now)target.replanTargetMs=TimeUtil.at(TimeUtil.plusDay(TimeUtil.day(now),2),23,59);
        }
        return target;
    }
    private static Entry copy(Entry e){Entry n=new Entry();n.title=e.title;n.note=e.note;n.url=e.url;n.category=e.category;
        n.source=e.source;n.fixed=e.fixed;n.weekly=false;n.startMs=e.startMs;n.endMs=e.endMs;
        n.deadlineMs=e.deadlineMs;n.estimateMin=e.estimateMin;n.difficulty=e.difficulty;
        return n;}
    public static List<Entry> illness(List<Entry> all,long now,int days){
        long start=TimeUtil.day(now),end=TimeUtil.plusDay(start,days),firstFree=end;
        List<Entry> changes=new ArrayList<>();List<Entry> working=new ArrayList<>(all);
        for(Entry e:all){if(e.deleted||e.progress>=100)continue;
            if(e.fixed){
                if(e.weekly){
                    // A weekly rule stays in place. Skip only occurrences within the sick period.
                    for(AutoPlanner.Block b:AutoPlanner.fixedBlocks(Collections.singletonList(e),start,days)){
                        long occurrenceDay=TimeUtil.day(b.start);
                        if(skipped(e,occurrenceDay))continue;
                        // Include earlier uncompleted occurrences from the same sick day.
                        Entry alternative=copy(e);alternative.title=e.title+"（振替候補）";
                        alternative.startMs=b.start;alternative.endMs=b.end;alternative.source="体調不良・振替候補";
                        remember(alternative,"体調不良による振替候補");
                        long newStart=nextSlot(alternative,working,firstFree);
                        alternative.endMs=newStart+(b.end-b.start);alternative.startMs=newStart;
                        e.skippedDays=(e.skippedDays==null||e.skippedDays.isEmpty()?"":e.skippedDays+",")+occurrenceDay;
                        changes.add(alternative);working.add(alternative);
                    }
                    if(!e.skippedDays.isEmpty() && !changes.contains(e))changes.add(e);
                }else if(e.startMs>=start&&e.startMs<end){
                    remember(e,"体調不良による予定変更");long duration=e.endMs-e.startMs;
                    long moved=nextSlot(e,working,firstFree);e.startMs=moved;e.endMs=moved+Math.max(15*60000L,duration);
                    changes.add(e);
                }
            }else{
                if(e.resumeAfterMs<end){remember(e,"体調不良による作業延期");e.resumeAfterMs=end;
                    if(e.deadlineMs>0 && e.deadlineMs<end)e.replanTargetMs=TimeUtil.at(TimeUtil.plusDay(end,1),23,59);
                    changes.add(e);
                }
            }
        }
        return changes;
    }
}