package jp.ultimateplanner.kiwami;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
public class UltimatePlannerApp extends Application {
    public static final String CHANNEL="deadline_channel";
    @Override public void onCreate(){super.onCreate();NotificationManager n=getSystemService(NotificationManager.class);if(n!=null){NotificationChannel c=new NotificationChannel(CHANNEL,"締切の通知",NotificationManager.IMPORTANCE_DEFAULT);c.setDescription("課題・就活などの期限前に通知します");n.createNotificationChannel(c);}}
}
