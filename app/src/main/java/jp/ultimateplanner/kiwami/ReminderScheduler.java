package jp.ultimateplanner.kiwami;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
public final class ReminderScheduler {
    private ReminderScheduler(){}
    static PendingIntent intent(Context c,Entry e,int index){Intent i=new Intent(c,ReminderReceiver.class);i.setAction("jp.ultimateplanner.kiwami.REMIND");i.putExtra("entryId",e.id);i.putExtra("title",e.title);i.putExtra("index",index);int code=(int)(e.id*3L+index);return PendingIntent.getBroadcast(c,code,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void update(Context c,Entry e){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;Prefs p=new Prefs(c);for(int i=1;i<=2;i++){PendingIntent pi=intent(c,e,i);am.cancel(pi);if(!p.notifications()||e.done()||e.fixed||e.deadlineMs<=0)continue;if(i==1&&!p.remindDay())continue;if(i==2&&!p.remindHours())continue;long at=e.deadlineMs-(i==1?86400000L:7200000L);if(at<=System.currentTimeMillis())continue;am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);}}
    public static void cancel(Context c,Entry e){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am!=null){am.cancel(intent(c,e,1));am.cancel(intent(c,e,2));}}
    public static void all(Context c){ScheduleDatabase db=new ScheduleDatabase(c);for(Entry e:db.all())update(c,e);db.close();}
}
