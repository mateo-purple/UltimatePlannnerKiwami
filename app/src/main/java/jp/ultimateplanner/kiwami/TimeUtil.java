package jp.ultimateplanner.kiwami;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TimeUtil {
    public static final Locale JA = Locale.JAPAN;
    public static String fmt(long ms,String pattern) { return new SimpleDateFormat(pattern,JA).format(new Date(ms)); }
    public static String time(long ms) { return fmt(ms,"H:mm"); }
    public static String date(long ms) { return fmt(ms,"M月d日 (E)"); }
    public static String month(long ms) { return fmt(ms,"yyyy年 M月"); }
    public static long day(long ms) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ms);
        c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0);
        c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
    public static long plusDay(long ms,int n) { Calendar c=Calendar.getInstance();c.setTimeInMillis(ms);c.add(Calendar.DAY_OF_YEAR,n);return c.getTimeInMillis(); }
    public static long plusWeek(long ms,int n) { Calendar c=Calendar.getInstance();c.setTimeInMillis(ms);c.add(Calendar.WEEK_OF_YEAR,n);return c.getTimeInMillis(); }
    public static long at(long day,int h,int m) { Calendar c=Calendar.getInstance();c.setTimeInMillis(day);c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,m);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis(); }
    public static long weekStart(long ms) {Calendar c=Calendar.getInstance();c.setTimeInMillis(day(ms));int dow=c.get(Calendar.DAY_OF_WEEK); c.add(Calendar.DAY_OF_YEAR,-((dow+5)%7));return c.getTimeInMillis();}
    public static long roundedQuarter(long ms) { Calendar c=Calendar.getInstance();c.setTimeInMillis(ms);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);int m=c.get(Calendar.MINUTE); c.add(Calendar.MINUTE, ((m+14)/15)*15-m);return c.getTimeInMillis(); }
    public static String minutes(int v) { if(v <= 0) return "0分"; return v >= 60 ? (v/60)+"時間"+(v%60==0?"":(v%60)+"分") : v+"分"; }
    public static String relativeDeadline(long ms) { if(ms<=0) return "期限未設定"; long days=(day(ms)-day(System.currentTimeMillis()))/86400000L; if(days<0)return "期限超過"; if(days==0)return "今日が期限"; if(days==1)return "明日が期限"; return "あと"+days+"日"; }
}
