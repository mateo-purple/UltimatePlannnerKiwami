package jp.ultimateplanner.kiwami;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    private final SharedPreferences p;
    public Prefs(Context c) { p = c.getSharedPreferences("settings",Context.MODE_PRIVATE); }
    public int fromHour() { return p.getInt("fromHour", 8); }
    public int fromMinute() { return p.getInt("fromMinute", 0); }
    public int toHour() { return p.getInt("toHour", 22); }
    public int toMinute() { return p.getInt("toMinute", 0); }
    public int chunk() { return p.getInt("chunk",90); }
    public boolean notifications() { return p.getBoolean("notifications",true); }
    public boolean remindDay() { return p.getBoolean("remindDay",true); }
    public boolean remindHours() { return p.getBoolean("remindHours",true); }
    public boolean demoAdded() { return p.getBoolean("demoAdded",false); }
    public void setFrom(int h,int m) { p.edit().putInt("fromHour",h).putInt("fromMinute",m).apply(); }
    public void setTo(int h,int m) { p.edit().putInt("toHour",h).putInt("toMinute",m).apply(); }
    public void setChunk(int v) { p.edit().putInt("chunk",v).apply(); }
    public void setNotifications(boolean b) { p.edit().putBoolean("notifications",b).apply(); }
    public void setRemindDay(boolean b) { p.edit().putBoolean("remindDay",b).apply(); }
    public void setRemindHours(boolean b) { p.edit().putBoolean("remindHours",b).apply(); }
    public void setDemoAdded(boolean b) { p.edit().putBoolean("demoAdded",b).apply(); }
}
