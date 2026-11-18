package jp.ultimateplanner.kiwami;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Offline JSON export/import compatible with docs/app.js (UltimatePlannerKiwami-v2). */
public final class BackupActivity extends Activity {
    private static final int EXPORT_REQUEST=410;
    private static final int IMPORT_REQUEST=411;
    private static final int MAX_BYTES=8*1024*1024;
    private static final int MAX_ENTRIES=20000;
    private TextView status;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG);
        LinearLayout outer=Ui.vertical(this);outer.setBackgroundColor(Ui.BG);setContentView(outer);
        LinearLayout toolbar=Ui.horizontal(this);toolbar.setBackgroundColor(Color.WHITE);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(toolbar,20,14,20,14);
        TextView back=Ui.text(this,"‹",30,Ui.INK,true);back.setOnClickListener(v->finish());
        toolbar.addView(back,Ui.margin(this,40,-2,0,0,12,0));
        toolbar.addView(Ui.text(this,"バックアップ・復元",19,Ui.INK,true));outer.addView(toolbar);
        ScrollView scroll=new ScrollView(this);LinearLayout body=Ui.vertical(this);Ui.padding(body,20,22,20,28);
        scroll.addView(body);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(Ui.text(this,"予定データをJSONファイルに保存・復元できます。通信環境がなくても利用できます。",15,Ui.INK));
        status=Ui.text(this,"端末内の予定をそのまま保持します。",13,Ui.MUTED);
        body.addView(status,Ui.margin(this,-1,-2,0,16,0,18));
        TextView export=Ui.button(this,"JSONバックアップを保存",true);
        export.setOnClickListener(v->exportFile());body.addView(export,Ui.margin(this,-1,-2,0,0,0,12));
        TextView restore=Ui.button(this,"JSONファイルから復元",false);
        restore.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("JSONデータを読み込みますか？")
            .setMessage("既存の予定は消さず、同じ識別IDの予定は更新日時を比較して統合します。外部ファイルの内容を確認してから選択してください。")
            .setNegativeButton("キャンセル",null).setPositiveButton("ファイルを選択",(d,w)->importFile()).show());
        body.addView(restore,Ui.margin(this,-1,-2,0,0,0,12));
        Ui.attach(body,Ui.text(this,"Web版「設定 → データの書き出し」で保存したJSONも読み込めます。JSONには予定の名称・日時・メモなどが含まれるため、公開フォルダには置かないでください。Firebaseと同期中の場合、読み込んだ内容は次回の同期で共有されます。",12,Ui.MUTED),12);
    }

    private void exportFile(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,"UltimatePlannerKiwami_backup_"+
            new SimpleDateFormat("yyyyMMdd",Locale.JAPAN).format(new Date())+".json");
        startActivityForResult(i,EXPORT_REQUEST);
    }
    private void importFile(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","text/json"});
        startActivityForResult(i,IMPORT_REQUEST);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==EXPORT_REQUEST)writeBackup(uri);
        else if(request==IMPORT_REQUEST)readBackup(uri);
    }
    private static JSONObject serialized(Entry e)throws Exception{
        JSONObject o=new JSONObject();
        o.put("syncId",e.syncId);o.put("title",e.title);o.put("note",e.note);
        o.put("category",e.category);o.put("source",e.source);o.put("fixed",e.fixed);
        o.put("weekly",e.weekly);o.put("startMs",e.startMs);o.put("endMs",e.endMs);
        o.put("deadlineMs",e.deadlineMs);o.put("estimateMin",e.estimateMin);
        o.put("progress",e.progress);o.put("difficulty",e.difficulty);
        o.put("createdMs",e.createdMs);o.put("updatedMs",e.updatedMs);o.put("deleted",e.deleted);
        return o;
    }
    private void writeBackup(Uri uri){
        try(ScheduleDatabase db=new ScheduleDatabase(this)){
            List<Entry> items=db.allIncludingDeleted();
            JSONArray arr=new JSONArray();for(Entry e:items)arr.put(serialized(e));
            JSONObject root=new JSONObject();root.put("format","UltimatePlannerKiwami-v2");
            root.put("exportedAt",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.JAPAN).format(new Date()));
            root.put("entries",arr);
            try(OutputStream stream=getContentResolver().openOutputStream(uri,"wt")){
                if(stream==null)throw new IllegalStateException("保存先を開けません");
                stream.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            status.setText(items.size()+"件をバックアップしました。");
        }catch(Exception e){status.setText("保存失敗: "+e.getMessage());}
    }
    private static Entry parsed(JSONObject o){
        Entry e=new Entry();e.syncId=o.optString("syncId","");
        if(e.syncId.isEmpty())e.syncId=UUID.randomUUID().toString();
        e.title=o.optString("title","").trim();e.note=o.optString("note","");
        e.category=o.optString("category","課題");e.source=o.optString("source","JSON復元");
        e.fixed=o.optBoolean("fixed",false);e.weekly=o.optBoolean("weekly",false);
        e.startMs=o.optLong("startMs",0);e.endMs=o.optLong("endMs",0);
        e.deadlineMs=o.optLong("deadlineMs",0);
        e.estimateMin=Math.max(0,Math.min(100000,o.optInt("estimateMin",60)));
        e.progress=Math.max(0,Math.min(100,o.optInt("progress",0)));
        e.difficulty=Math.max(1,Math.min(5,o.optInt("difficulty",3)));
        e.createdMs=o.optLong("createdMs",System.currentTimeMillis());
        e.updatedMs=o.optLong("updatedMs",e.createdMs);
        e.deleted=o.optBoolean("deleted",false);
        return e;
    }
    private void readBackup(Uri uri){
        try(InputStream in=getContentResolver().openInputStream(uri);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            if(in==null)throw new IllegalStateException("ファイルを開けません");
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){if(bytes.size()+n>MAX_BYTES)throw new IllegalArgumentException("8MBを超えるファイルは読み込めません");bytes.write(buffer,0,n);}
            JSONObject json=new JSONObject(bytes.toString("UTF-8"));
            if(!"UltimatePlannerKiwami-v2".equals(json.optString("format")))
                throw new IllegalArgumentException("対応していないJSON形式です");
            JSONArray values=json.optJSONArray("entries");
            if(values==null||values.length()>MAX_ENTRIES)throw new IllegalArgumentException("予定データの形式・件数を確認してください");
            int applied=0;
            try(ScheduleDatabase db=new ScheduleDatabase(this)){
                for(int i=0;i<values.length();i++){
                    JSONObject item=values.optJSONObject(i);if(item==null)continue;
                    Entry e=parsed(item);if(e.title.isEmpty())continue;
                    db.upsertRemote(e);applied++;
                }
            }
            ReminderScheduler.all(this);
            status.setText(applied+"件の予定・タスクを読み込みました。ホームへ戻ると表示が更新されます。");
        }catch(Exception e){status.setText("読み込み失敗: "+e.getMessage());}
    }
}