package jp.ultimateplanner.kiwami;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** SQLite remains authoritative for offline UI. A stable sync_id links each row to Firestore. */
public class ScheduleDatabase extends SQLiteOpenHelper {
    private static final String NAME = "ultimateplannerkiwami.db";
    private static final int VERSION = 3;
    public ScheduleDatabase(Context c) { super(c, NAME, null, VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "title TEXT NOT NULL, note TEXT, category TEXT, source TEXT,"+
            "fixed INTEGER NOT NULL DEFAULT 0, weekly INTEGER NOT NULL DEFAULT 0,"+
            "start_ms INTEGER, end_ms INTEGER, deadline_ms INTEGER,"+
            "estimate_min INTEGER, progress INTEGER, difficulty INTEGER, created_ms INTEGER,"+
            "sync_id TEXT, updated_ms INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0,"+
            "url TEXT NOT NULL DEFAULT '', history TEXT NOT NULL DEFAULT '', skipped_days TEXT NOT NULL DEFAULT '',"+
            "resume_after_ms INTEGER NOT NULL DEFAULT 0, replan_target_ms INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE UNIQUE INDEX idx_entries_sync_id ON entries(sync_id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion < 2) {
            db.execSQL("ALTER TABLE entries ADD COLUMN sync_id TEXT");
            db.execSQL("ALTER TABLE entries ADD COLUMN updated_ms INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE entries ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");
            // Preserve every existing event and its local id while assigning a cloud-safe identity.
            try(Cursor c=db.rawQuery("SELECT id,created_ms FROM entries",null)) {
                while(c.moveToNext()) {
                    ContentValues v=new ContentValues();
                    v.put("sync_id",UUID.randomUUID().toString());
                    v.put("updated_ms",Math.max(1,c.getLong(1)));
                    db.update("entries",v,"id=?",new String[]{Long.toString(c.getLong(0))});
                }
            }
            db.execSQL("CREATE UNIQUE INDEX idx_entries_sync_id ON entries(sync_id)");
        }
        if(oldVersion < 3){
            db.execSQL("ALTER TABLE entries ADD COLUMN url TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE entries ADD COLUMN history TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE entries ADD COLUMN skipped_days TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE entries ADD COLUMN resume_after_ms INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE entries ADD COLUMN replan_target_ms INTEGER NOT NULL DEFAULT 0");
        }
    }
    private ContentValues values(Entry e) {
        ContentValues v = new ContentValues();
        v.put("title", e.title); v.put("note", e.note); v.put("url",e.url);
        v.put("history",e.history);v.put("skipped_days",e.skippedDays);
        v.put("resume_after_ms",e.resumeAfterMs);v.put("replan_target_ms",e.replanTargetMs); v.put("category", e.category);
        v.put("source", e.source); v.put("fixed", e.fixed ? 1 : 0);
        v.put("weekly", e.weekly ? 1 : 0); v.put("start_ms",e.startMs);
        v.put("end_ms",e.endMs); v.put("deadline_ms",e.deadlineMs);
        v.put("estimate_min",e.estimateMin); v.put("progress",e.progress);
        v.put("difficulty",e.difficulty); v.put("created_ms",e.createdMs);
        v.put("sync_id",e.syncId);v.put("updated_ms",e.updatedMs);v.put("deleted",e.deleted?1:0);
        return v;
    }
    public long save(Entry e) {
        SQLiteDatabase db = getWritableDatabase();
        if(e.syncId==null||e.syncId.isEmpty())e.syncId=UUID.randomUUID().toString();
        e.updatedMs=Math.max(System.currentTimeMillis(),e.updatedMs+1);
        if (e.id > 0) {
            db.update("entries",values(e),"id=?",new String[]{Long.toString(e.id)});
            return e.id;
        }
        long id = db.insertOrThrow("entries",null,values(e));e.id=id;return id;
    }
    /** Update without changing remote revision timestamps or losing the local SQLite id. */
    public void upsertRemote(Entry e) {
        if(e.syncId==null||e.syncId.isEmpty())return;
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.rawQuery("SELECT id FROM entries WHERE sync_id=?",new String[]{e.syncId})){
            if(c.moveToFirst()) {
                long oldId=c.getLong(0);
                try(Cursor old=db.rawQuery("SELECT updated_ms FROM entries WHERE id=?",new String[]{Long.toString(oldId)})){
                    if(old.moveToFirst() && old.getLong(0)>e.updatedMs)return;
                }
                db.update("entries",values(e),"id=?",new String[]{Long.toString(oldId)});return;
            }
        }
        e.id=db.insertOrThrow("entries",null,values(e));
    }
    private Entry read(Cursor c) {
        Entry e = new Entry();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.title = c.getString(c.getColumnIndexOrThrow("title"));
        e.note = c.getString(c.getColumnIndexOrThrow("note"));
        e.url=c.getString(c.getColumnIndexOrThrow("url"));
        e.history=c.getString(c.getColumnIndexOrThrow("history"));
        e.skippedDays=c.getString(c.getColumnIndexOrThrow("skipped_days"));
        e.resumeAfterMs=c.getLong(c.getColumnIndexOrThrow("resume_after_ms"));
        e.replanTargetMs=c.getLong(c.getColumnIndexOrThrow("replan_target_ms"));
        e.category = c.getString(c.getColumnIndexOrThrow("category"));
        e.source = c.getString(c.getColumnIndexOrThrow("source"));
        e.fixed = c.getInt(c.getColumnIndexOrThrow("fixed")) != 0;
        e.weekly = c.getInt(c.getColumnIndexOrThrow("weekly")) != 0;
        e.startMs = c.getLong(c.getColumnIndexOrThrow("start_ms"));
        e.endMs = c.getLong(c.getColumnIndexOrThrow("end_ms"));
        e.deadlineMs = c.getLong(c.getColumnIndexOrThrow("deadline_ms"));
        e.estimateMin = c.getInt(c.getColumnIndexOrThrow("estimate_min"));
        e.progress = c.getInt(c.getColumnIndexOrThrow("progress"));
        e.difficulty = c.getInt(c.getColumnIndexOrThrow("difficulty"));
        e.createdMs = c.getLong(c.getColumnIndexOrThrow("created_ms"));
        e.syncId=c.getString(c.getColumnIndexOrThrow("sync_id"));
        e.updatedMs=c.getLong(c.getColumnIndexOrThrow("updated_ms"));
        e.deleted=c.getInt(c.getColumnIndexOrThrow("deleted"))!=0;
        return e;
    }
    private List<Entry> query(String sql) {
        List<Entry> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext())result.add(read(c));}
        return result;
    }
    public List<Entry> all() {return query("SELECT * FROM entries WHERE deleted=0 ORDER BY created_ms DESC");}
    public List<Entry> allIncludingDeleted(){return query("SELECT * FROM entries ORDER BY created_ms DESC");}
    public Entry get(long id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM entries WHERE id=? AND deleted=0", new String[]{Long.toString(id)})) {
            return c.moveToFirst()?read(c):null;
        }
    }
    public long findEquivalent(Entry e) {
        try (Cursor c=getReadableDatabase().rawQuery(
            "SELECT id FROM entries WHERE deleted=0 AND title=? AND note=? AND fixed=? AND start_ms=? AND deadline_ms=? AND source=? LIMIT 1",
            new String[]{e.title,e.note,e.fixed?"1":"0",Long.toString(e.startMs),Long.toString(e.deadlineMs),e.source})) {
            return c.moveToFirst()?c.getLong(0):0L;
        }
    }
    /** Tombstones prevent deleted events reappearing on a second device. */
    public void delete(long id){Entry entry=get(id);if(entry!=null){entry.deleted=true;save(entry);}}
    public void clear(){
        long now=System.currentTimeMillis();
        getWritableDatabase().execSQL("UPDATE entries SET deleted=1,updated_ms=MAX(updated_ms+1,?) WHERE deleted=0",new Object[]{now});
    }
}