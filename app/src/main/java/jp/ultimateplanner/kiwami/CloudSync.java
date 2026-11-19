package jp.ultimateplanner.kiwami;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import javax.net.ssl.SSLHandshakeException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Firebase Authentication + Firestore REST bridge; no google-services.json or Firebase SDK required. */
public final class CloudSync {
    public interface ChangeCallback { void changed(int count); }
    public interface Callback { void finished(String message, boolean success); }
    private static final String PREF="ultimateplanner-cloud-v1";
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private static final Handler UI=new Handler(Looper.getMainLooper());
    private CloudSync(){}
    private static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    public static String project(Context c){return FirebaseConfig.PROJECT_ID;}
    public static String apiKey(Context c){return FirebaseConfig.WEB_API_KEY;}
    public static String email(Context c){return prefs(c).getString("email","");}
    public static boolean signedIn(Context c){return !prefs(c).getString("refresh","").isEmpty() && !prefs(c).getString("uid","").isEmpty();}
    public static String accountLabel(Context c){return signedIn(c)?"同期アカウント: "+email(c):"未ログイン。アカウントにログインするとPC・iPhoneと同期できます。";}
    private static void done(Callback callback,String message,boolean ok){if(callback!=null)UI.post(()->callback.finished(message,ok));}
    public static void logout(Context context){prefs(context).edit().remove("refresh").remove("uid").remove("email").apply();}
    private static void validate(String project,String key) throws Exception{
        if(!project.matches("[a-z0-9][a-z0-9-]{3,100}"))throw new Exception("Firebase Project IDを確認してください");
        if(!key.matches("[A-Za-z0-9_-]{10,200}"))throw new Exception("Firebase Web API Keyを確認してください");
    }
    public static void signIn(Context context,String email,String password,boolean register,Callback callback){
        final String project=FirebaseConfig.PROJECT_ID;
        final String key=FirebaseConfig.WEB_API_KEY;
        Context app=context.getApplicationContext();
        if(!BUSY.compareAndSet(false,true)){done(callback,"別の同期処理が実行中です",false);return;}
        WORKER.execute(()->{
            try {
                validate(project,key);
                JSONObject request=new JSONObject();request.put("email",email.trim());request.put("password",password);request.put("returnSecureToken",true);
                String action=register?"signUp":"signInWithPassword";
                JSONObject result=http("POST","https://identitytoolkit.googleapis.com/v1/accounts:"+action+"?key="+enc(key),request.toString(),"application/json",null);
                String uid=result.getString("localId");
                // The on-device SQLite DB remains associated with the first signed-in user.
                // Refusing a different UID is safer than silently uploading someone else's private schedule.
                SharedPreferences p=prefs(app);String previous=p.getString("owner_uid","");String previousProject=p.getString("owner_project","");
                if((!previous.isEmpty()&&!previous.equals(uid))||(!previousProject.isEmpty()&&!previousProject.equals(project)))
                    throw new Exception("この端末の予定は別のアカウントまたはFirebaseプロジェクトに紐づいています。データ混在防止のためログインできません");
                p.edit().putString("project",project).putString("api_key",key).putString("email",result.optString("email",email.trim()))
                    .putString("refresh",result.getString("refreshToken")).putString("uid",uid).putString("owner_uid",uid).putString("owner_project",project).apply();
                int changed=syncInternal(app);
                done(callback,"ログイン・同期が完了しました（"+changed+"件更新）",true);
            } catch(Exception e){done(callback,"接続失敗: "+safeMessage(e),false);}
            finally{BUSY.set(false);}
        });
    }
    /** Automatically called on app resume / periodically. Offline edits stay in SQLite until reconnect. */
    public static void sync(Context context,boolean manual,Callback callback){
        Context app=context.getApplicationContext();
        if(!signedIn(app)){if(manual)done(callback,"先にログインしてください",false);return;}
        if(!BUSY.compareAndSet(false,true)){if(manual)done(callback,"すでに同期中です",false);return;}
        WORKER.execute(()->{
            try{int changed=syncInternal(app);done(callback,"同期しました（"+changed+"件更新）",true);}
            catch(Exception e){done(callback,"同期失敗: "+safeMessage(e),false);}
            finally{BUSY.set(false);}
        });
    }
    /** Sync once on return to foreground, refreshing UI only if persisted records changed. */
    public static void syncIfChanged(Context context, ChangeCallback callback){
        Context app=context.getApplicationContext();
        if(!signedIn(app)||!BUSY.compareAndSet(false,true))return;
        WORKER.execute(()->{
            try{
                int changed=syncInternal(app);
                if(changed>0 && callback!=null)UI.post(()->callback.changed(changed));
            }catch(Exception ignored){ /* Offline: local SQLite remains available. */ }
            finally{BUSY.set(false);}
        });
    }
    private static int syncInternal(Context c) throws Exception {
        SharedPreferences p=prefs(c);String project=FirebaseConfig.PROJECT_ID;String key=FirebaseConfig.WEB_API_KEY;
        String uid=p.getString("uid","");String refresh=p.getString("refresh","");validate(project,key);
        if(uid.isEmpty()||refresh.isEmpty())throw new Exception("ログイン情報がありません");
        String form="grant_type=refresh_token&refresh_token="+enc(refresh);
        JSONObject token=http("POST","https://securetoken.googleapis.com/v1/token?key="+enc(key),form,"application/x-www-form-urlencoded",null);
        String idToken=token.getString("id_token");
        String refreshed=token.optString("refresh_token",refresh);
        p.edit().putString("refresh",refreshed).apply();
        String base="https://firestore.googleapis.com/v1/projects/"+enc(project)+"/databases/(default)/documents/users/"+enc(uid)+"/entries";
        Map<String,Entry> remote=new HashMap<>();String page="";
        for(int batch=0;batch<100;batch++){
            JSONObject response=http("GET",base+"?pageSize=1000"+(page.isEmpty()?"":"&pageToken="+enc(page)),null,null,idToken);
            JSONArray docs=response.optJSONArray("documents");
            if(docs!=null)for(int i=0;i<docs.length();i++){
                JSONObject doc=docs.getJSONObject(i);String name=doc.getString("name");
                String syncId=name.substring(name.lastIndexOf('/')+1);
                remote.put(syncId,fromDoc(doc.getJSONObject("fields"),syncId));
            }
            page=response.optString("nextPageToken","");if(page.isEmpty())break;
        }
        if(!page.isEmpty())throw new Exception("同期対象が多すぎます。時間をおいて再試行してください");
        int changed=0;
        try(ScheduleDatabase db=new ScheduleDatabase(c)){
            List<Entry> local=db.allIncludingDeleted();
            Set<String> localIds=new HashSet<>();List<Entry> uploads=new ArrayList<>();
            for(Entry entry:local){
                localIds.add(entry.syncId);Entry cloud=remote.get(entry.syncId);
                if(cloud==null||entry.updatedMs>cloud.updatedMs)uploads.add(entry);
                else if(cloud.updatedMs>entry.updatedMs){db.upsertRemote(cloud);changed++;}
            }
            for(Map.Entry<String,Entry> item:remote.entrySet())if(!localIds.contains(item.getKey())){db.upsertRemote(item.getValue());changed++;}
            // Commit uses POST (universally supported by Android HttpURLConnection),
            // avoiding PATCH support differences and making each small batch atomic.
            for(int start=0;start<uploads.size();start+=100){
                JSONArray writes=new JSONArray();
                for(int i=start;i<Math.min(start+100,uploads.size());i++){
                    Entry entry=uploads.get(i);
                    JSONObject document=new JSONObject();
                    document.put("name","projects/"+project+"/databases/(default)/documents/users/"+uid+"/entries/"+entry.syncId);
                    document.put("fields",toDoc(entry));
                    writes.put(new JSONObject().put("update",document));
                }
                http("POST","https://firestore.googleapis.com/v1/projects/"+enc(project)+"/databases/(default)/documents:commit",
                    new JSONObject().put("writes",writes).toString(),"application/json",idToken);
                changed+=writes.length();
            }
        }
        if(changed>0)ReminderScheduler.all(c);
        return changed;
    }
    private static JSONObject toDoc(Entry e)throws Exception{
        JSONObject v=new JSONObject();
        putStr(v,"syncId",e.syncId);putStr(v,"title",e.title);putStr(v,"note",e.note);
        putStr(v,"url",e.url);putStr(v,"history",e.history);putStr(v,"skippedDays",e.skippedDays);
        putLong(v,"resumeAfterMs",e.resumeAfterMs);putLong(v,"replanTargetMs",e.replanTargetMs);putStr(v,"category",e.category);putStr(v,"source",e.source);
        putBool(v,"fixed",e.fixed);putBool(v,"weekly",e.weekly);putBool(v,"deleted",e.deleted);
        putLong(v,"startMs",e.startMs);putLong(v,"endMs",e.endMs);putLong(v,"deadlineMs",e.deadlineMs);
        putLong(v,"estimateMin",e.estimateMin);putLong(v,"progress",e.progress);putLong(v,"difficulty",e.difficulty);
        putLong(v,"createdMs",e.createdMs);putLong(v,"updatedMs",e.updatedMs);return v;
    }
    private static void putStr(JSONObject d,String k,String v)throws Exception{d.put(k,new JSONObject().put("stringValue",v==null?"":v));}
    private static void putLong(JSONObject d,String k,long v)throws Exception{d.put(k,new JSONObject().put("integerValue",Long.toString(v)));}
    private static void putBool(JSONObject d,String k,boolean v)throws Exception{d.put(k,new JSONObject().put("booleanValue",v));}
    private static String getStr(JSONObject d,String k,String defaultValue){JSONObject v=d.optJSONObject(k);return v==null?defaultValue:v.optString("stringValue",defaultValue);}
    private static long getLong(JSONObject d,String k,long def){JSONObject v=d.optJSONObject(k);if(v==null)return def;try{return Long.parseLong(v.optString("integerValue",Long.toString(def)));}catch(Exception e){return def;}}
    private static boolean getBool(JSONObject d,String k){JSONObject v=d.optJSONObject(k);return v!=null&&v.optBoolean("booleanValue",false);}
    private static Entry fromDoc(JSONObject f,String id){
        Entry e=new Entry();e.syncId=id;e.title=getStr(f,"title","");e.note=getStr(f,"note","");e.category=getStr(f,"category","課題");
        e.source=getStr(f,"source","同期");e.url=getStr(f,"url","");e.history=getStr(f,"history","");e.skippedDays=getStr(f,"skippedDays","");
        e.resumeAfterMs=getLong(f,"resumeAfterMs",0);e.replanTargetMs=getLong(f,"replanTargetMs",0);e.fixed=getBool(f,"fixed");e.weekly=getBool(f,"weekly");e.deleted=getBool(f,"deleted");
        e.startMs=getLong(f,"startMs",0);e.endMs=getLong(f,"endMs",0);e.deadlineMs=getLong(f,"deadlineMs",0);
        e.estimateMin=(int)getLong(f,"estimateMin",60);e.progress=(int)getLong(f,"progress",0);e.difficulty=(int)getLong(f,"difficulty",3);
        e.createdMs=getLong(f,"createdMs",System.currentTimeMillis());e.updatedMs=getLong(f,"updatedMs",0);return e;
    }
    private static JSONObject http(String method,String url,String body,String contentType,String bearer) throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setRequestMethod(method);connection.setConnectTimeout(12000);connection.setReadTimeout(18000);
        connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept","application/json");
        if(bearer!=null)connection.setRequestProperty("Authorization","Bearer "+bearer);
        if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type",contentType+"; charset=UTF-8");
            try(OutputStream out=connection.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
        int status=connection.getResponseCode();InputStream in=status>=400?connection.getErrorStream():connection.getInputStream();
        String response="";
        if(in!=null)try(InputStream source=in;ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int n;while((n=source.read(bytes))!=-1){if(buffer.size()>4000000)throw new Exception("同期レスポンスが大きすぎます");buffer.write(bytes,0,n);}response=buffer.toString("UTF-8");
        }
        connection.disconnect();
        if(status<200||status>=300){
            String message="HTTP "+status;
            try{JSONObject error=new JSONObject(response).optJSONObject("error");if(error!=null)message+=" "+error.optString("message","");}catch(Exception ignored){}
            throw new Exception(message);
        }
        return response.isEmpty()?new JSONObject():new JSONObject(response);
    }
    private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
    private static String safeMessage(Exception e){
        if(e instanceof UnknownHostException)return "通信先を見つけられません。Wi-Fi・DNS・学内プロキシを確認してください";
        if(e instanceof SocketTimeoutException)return "接続がタイムアウトしました。回線やプロキシ設定を確認してください";
        if(e instanceof ConnectException)return "ネットワークへ接続できません。Wi-Fi・テザリングを確認してください";
        if(e instanceof SSLHandshakeException)return "HTTPS証明書の確認に失敗しました。端末の日時とネットワーク設定を確認してください";
        String message=e.getMessage();
        if(message==null)return e.getClass().getSimpleName();
        if(message.contains("HTTP 407"))return "学内プロキシの認証が必要です（HTTP 407）。Wi-Fiのプロキシ設定を確認してください";
        if(message.contains("HTTP 403")||message.contains("PERMISSION_DENIED"))return "Firestoreへのアクセスが拒否されました。ログインとFirestoreルールを確認してください";
        return message.replace(FirebaseConfig.WEB_API_KEY,"[API KEY]");
    }
}