package jp.ultimateplanner.kiwami;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
public class ReminderReceiver extends android.content.BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        long id=i.getLongExtra("entryId",0);int index=i.getIntExtra("index",1);String title=i.getStringExtra("title");if(title==null)return;
        ScheduleDatabase db=new ScheduleDatabase(c);
        Entry e=db.get(id);db.close();
        if(e==null||e.done()||e.fixed)return;
        Intent open=new Intent(c,MainActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(c,(int)id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=new Notification.Builder(c,UltimatePlannerApp.CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(index==1?"明日が締切です":"締切まであと2時間").setContentText(title).setAutoCancel(true).setContentIntent(pi);
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify((int)(id*3+index),b.build());
    }
}
