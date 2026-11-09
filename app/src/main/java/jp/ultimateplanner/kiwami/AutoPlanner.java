package jp.ultimateplanner.kiwami;
import java.util.*;

/** 30分刻みの空き時間計画。固定予定を避け、期限が近い課題から割り当てます。 */
public final class AutoPlanner {
    public static class Block { public final Entry entry;public final long start,end;public final boolean generated;Block(Entry e,long a,long b,boolean g){entry=e;start=a;end=b;generated=g;} }
    public static class Plan {public final List<Block> blocks=new ArrayList<>();public final Map<Long,Integer> unscheduled=new HashMap<>();}
    private AutoPlanner(){}
    private static int hourOf(long time){Calendar c=Calendar.getInstance();c.setTimeInMillis(time);return c.get(Calendar.HOUR_OF_DAY);}
    private static int minuteOf(long time){Calendar c=Calendar.getInstance();c.setTimeInMillis(time);return c.get(Calendar.MINUTE);}
    public static Plan build(List<Entry> entries,Prefs p,long now,int horizonDays){
        Plan result=new Plan();long startDay=TimeUtil.day(now),endDay=TimeUtil.plusDay(startDay,horizonDays);
        for(Entry e:entries){
            if(!e.fixed || e.startMs<=0 || e.endMs<=e.startMs)continue;
            if(e.weekly){
                // Jump directly to the week overlapping the visible planning window.
                // Do not iterate through every week since the original registration.
                long originalDay=TimeUtil.day(e.startMs);
                Calendar cursor=Calendar.getInstance();cursor.setTimeInMillis(originalDay);
                Calendar window=Calendar.getInstance();window.setTimeInMillis(startDay);
                long span=e.endMs-e.startMs;
                while(cursor.getTimeInMillis()+span<startDay && cursor.getTimeInMillis()<endDay){
                    long delta=startDay-cursor.getTimeInMillis()-span;
                    int weeks=(int)Math.max(1,Math.min(52000,delta/(7L*86400000L)));
                    cursor.add(Calendar.WEEK_OF_YEAR,weeks);
                }
                while(cursor.getTimeInMillis()<endDay){
                    long a=TimeUtil.at(cursor.getTimeInMillis(),
                        hourOf(e.startMs),minuteOf(e.startMs));
                    long b=TimeUtil.plusDay(a,0)+span;
                    if(b>startDay && a<endDay)result.blocks.add(new Block(e,a,b,false));
                    cursor.add(Calendar.WEEK_OF_YEAR,1);
                }
            }else if(e.endMs>startDay && e.startMs<endDay)result.blocks.add(new Block(e,e.startMs,e.endMs,false));
        }
        List<Entry> tasks=Priority.tasks(entries,now);
        for(Entry e:tasks){
            int remaining=e.remainingMin();if(remaining<=0)continue;
            if(e.deadlineMs>0 && e.deadlineMs<=now){result.unscheduled.put(e.id,remaining);continue;}
            int limit=Math.max(30,p.chunk());
            for(int d=0;d<horizonDays && remaining>0;d++){
                long day=TimeUtil.plusDay(startDay,d);
                long from=TimeUtil.at(day,p.fromHour(),p.fromMinute());
                long to=TimeUtil.at(day,p.toHour(),p.toMinute());
                if(to<=from)to=TimeUtil.plusDay(to,1);
                long slot=Math.max(from,TimeUtil.roundedQuarter(now));
                // Single-day plans are deliberately bounded to the end of the chosen day.
                while(slot+15*60000L<=to && remaining>0){
                    long deadline=e.deadlineMs>0?e.deadlineMs:Long.MAX_VALUE;
                    if(slot>=deadline)break;
                    long nearest=Math.min(to,deadline);
                    for(Block b:result.blocks)if(b.end>slot && b.start<nearest)nearest=Math.min(nearest,b.start);
                    int possible=(int)((nearest-slot)/60000L);
                    int take=Math.min(Math.min(remaining,limit),possible);
                    take=(take/15)*15;
                    if(take>=15){
                        long blockEnd=slot+take*60000L;
                        result.blocks.add(new Block(e,slot,blockEnd,true));
                        remaining-=take;slot=blockEnd;continue;
                    }
                    long next=slot+15*60000L;
                    for(Block b:result.blocks)if(b.end>slot && b.start<slot+15*60000L)next=Math.max(next,b.end);
                    slot=TimeUtil.roundedQuarter(next);
                }
            }
            if(remaining>0)result.unscheduled.put(e.id,remaining);
        }
        result.blocks.sort(Comparator.comparingLong(b->b.start));return result;
    }
}
