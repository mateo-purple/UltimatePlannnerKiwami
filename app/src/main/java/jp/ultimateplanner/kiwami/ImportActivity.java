package jp.ultimateplanner.kiwami;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.provider.MediaStore;
import java.io.File;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import java.util.*;

/** OCR / Android share sheet → immediate permanent registration, without review or confirmation. */
public class ImportActivity extends Activity {
    public static final String EXTRA_CAPTURE_ON_LAUNCH="capture_on_launch";
    private static final int SELECT_IMAGE=202;
    private static final int TAKE_PHOTO=203;
    private static final String CAPTURE_FILE="camera_capture.jpg";
    private boolean cameraLaunching=false;
    private TextRecognizer recognizer;
    private ScheduleDatabase db;
    private EditText source;
    private LinearLayout results;
    private TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        recognizer=TextRecognition.getClient(new JapaneseTextRecognizerOptions.Builder().build());
        db=new ScheduleDatabase(this);
        build();
        Intent incoming=getIntent();
        if(Intent.ACTION_SEND.equals(incoming.getAction())) {
            if(incoming.getType()!=null&&incoming.getType().startsWith("image/")) {
                Uri uri=Build.VERSION.SDK_INT>=33?incoming.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class):(Uri)incoming.getParcelableExtra(Intent.EXTRA_STREAM);
                if(uri!=null)readImage(uri);
                else status.setText("共有された画像を取得できませんでした");
            } else if(incoming.getStringExtra(Intent.EXTRA_TEXT)!=null) {
                String text=incoming.getStringExtra(Intent.EXTRA_TEXT);
                source.setText(text);
                saveImmediately(text,"共有テキスト");
            }
        } else if(b==null && incoming.getBooleanExtra(EXTRA_CAPTURE_ON_LAUNCH,false)) {
            // A tap on the main screen's import action opens the camera immediately.
            // Shared text/images must not be interrupted by a camera launch.
            launchCamera();
        }
    }

    @Override public void onDestroy() {
        if(recognizer!=null) recognizer.close();
        if(db!=null) db.close();
        super.onDestroy();
    }

    private void build() {
        LinearLayout screen=Ui.vertical(this);
        screen.setBackgroundColor(Ui.BG);
        setContentView(screen);
        LinearLayout toolbar=Ui.horizontal(this);
        Ui.padding(toolbar,20,15,20,15);
        toolbar.setBackgroundColor(Color.WHITE);
        TextView back=Ui.text(this,"‹",30,Ui.INK,true);
        back.setOnClickListener(v->finish());
        toolbar.addView(back,Ui.margin(this,36,-2,0,0,9,0));
        toolbar.addView(Ui.text(this,"予定を取り込む",19,Ui.INK,true));
        screen.addView(toolbar);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content=Ui.vertical(this);
        Ui.padding(content,20,22,20,34);
        scroll.addView(content);
        screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content.addView(Ui.text(this,"カメラで撮影するか、保存した画像・共有した文章を読み取って予定に登録します。",14,Ui.MUTED));

        LinearLayout choice=Ui.horizontal(this);
        TextView camera=Ui.button(this,"カメラで撮影",true);
        camera.setOnClickListener(v->launchCamera());
        choice.addView(camera,new LinearLayout.LayoutParams(0,-2,1));
        TextView photo=Ui.button(this,"保存画像を選ぶ",false);
        photo.setOnClickListener(v->chooseImage());
        LinearLayout.LayoutParams pp=Ui.margin(this,0,-2,10,0,0,0);
        pp.width=0;pp.weight=1;
        choice.addView(photo,pp);
        content.addView(choice,Ui.margin(this,-1,-2,0,10,0,0));
        TextView clipboard=Ui.button(this,"コピーした文章を取り込む",false);
        clipboard.setOnClickListener(v->paste());
        content.addView(clipboard,Ui.margin(this,-1,-2,0,10,0,0));

        status=Ui.text(this,"未登録",13,Ui.MUTED);
        content.addView(status,Ui.margin(this,-1,-2,0,15,0,0));
        Ui.fieldLabel(content,"文章を直接入力する場合");
        source=Ui.field(this,"日付と予定を書いてください", "", InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        source.setGravity(Gravity.TOP);
        source.setMinLines(5);
        source.setSingleLine(false);
        content.addView(source,Ui.lp(this,-1,-2));
        TextView save=Ui.button(this,"文章を登録",true);
        save.setOnClickListener(v->saveImmediately(source.getText().toString(),"直接入力"));
        content.addView(save,Ui.margin(this,-1,-2,0,15,0,0));
        results=Ui.vertical(this);
        content.addView(results,Ui.margin(this,-1,-2,0,18,0,0));
        Ui.attach(content,Ui.text(this,"読み取ったデータは自動保存されます。登録結果から編集や取り消しもできます。eシラバスはスクリーンショット、またはブラウザの共有機能で取り込んでください。",12,Ui.MUTED),22);
    }

    private File captureFile(){return new File(getCacheDir(),CAPTURE_FILE);}

    private Uri captureUri(){return Uri.parse("content://"+getPackageName()+".capture/image");}

    private void launchCamera() {
        if(cameraLaunching)return;
        try {
            File file=captureFile();
            // An old canceled capture must not be mistaken for the new photograph.
            if(file.exists()&&!file.delete())throw new java.io.IOException("古い撮影データを削除できません");
            if(!file.createNewFile())throw new java.io.IOException("撮影画像を準備できません");
            Uri uri=captureUri();
            Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT,uri);
            intent.setClipData(ClipData.newRawUri("撮影画像",uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            cameraLaunching=true;
            status.setText("カメラを起動します…");
            startActivityForResult(intent,TAKE_PHOTO);
        } catch(Exception e) {
            cameraLaunching=false;
            status.setText("カメラを起動できませんでした: "+e.getMessage());
        }
    }

    private void chooseImage() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent,SELECT_IMAGE);
    }

    @Override protected void onActivityResult(int req,int code,Intent data) {
        super.onActivityResult(req,code,data);
        if(req==SELECT_IMAGE&&code==RESULT_OK&&data!=null&&data.getData()!=null)readImage(data.getData());
        if(req==TAKE_PHOTO){
            cameraLaunching=false;
            if(code==RESULT_OK&&captureFile().exists()&&captureFile().length()>0){
                readImage(captureUri(),"カメラ撮影",true);
            } else {
                captureFile().delete();
                status.setText(code==RESULT_OK?"撮影画像を取得できませんでした。もう一度撮影してください。":"撮影をキャンセルしました");
            }
        }
    }

    private void paste() {
        android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cb==null||!cb.hasPrimaryClip()||cb.getPrimaryClip()==null||cb.getPrimaryClip().getItemCount()==0) {
            status.setText("コピーされた内容がありません");return;
        }
        CharSequence contents=cb.getPrimaryClip().getItemAt(0).coerceToText(this);
        String text=contents==null?"":contents.toString();
        source.setText(text);
        saveImmediately(text,"クリップボード");
    }

    private void readImage(Uri uri){readImage(uri,"画像OCR",false);}

    private void readImage(Uri uri,String origin,boolean removeAfterRead) {
        status.setText("画像を読み取っています…");
        try {
            InputImage img=InputImage.fromFilePath(this,uri);
            recognizer.process(img).addOnSuccessListener(result->{
                if(removeAfterRead)captureFile().delete();
                String text=result.getText();
                source.setText(text);
                saveImmediately(text,origin);
            }).addOnFailureListener(e->{
                if(removeAfterRead)captureFile().delete();
                status.setText("画像の読み取りに失敗しました: "+e.getMessage());
            });
        } catch(Exception e) {
            if(removeAfterRead)captureFile().delete();
            status.setText("画像を開けませんでした: "+e.getMessage());
        }
    }

    /** The write happens immediately. No confirmation form and no extra save tap. */
    private void saveImmediately(String raw,String origin) {
        if(raw==null||raw.trim().isEmpty()) {
            status.setText("文字が見つからなかったため、登録は行いませんでした");return;
        }
        if(raw.trim().matches("(?s)^https?://\\S+$")) {
            status.setText("URLだけではログイン先の内容を取得できません。画面を画像として共有してください");
            return;
        }
        List<ImportParser.Suggestion> list=ImportParser.parseMany(raw);
        if(list.isEmpty()) {
            status.setText("予定として読み取れる内容がありませんでした");return;
        }
        int count=0,duplicates=0;
        results.removeAllViews();
        for(ImportParser.Suggestion suggestion:list) {
            Entry entry=new Entry();
            entry.title=suggestion.title;
            entry.note=suggestion.note;
            entry.category=suggestion.category;
            entry.source=origin;
            entry.fixed=suggestion.fixed;
            entry.deadlineMs=suggestion.deadline;
            entry.startMs=suggestion.start;
            entry.endMs=suggestion.end;
            entry.estimateMin=suggestion.estimateMin;
            if(entry.title.trim().isEmpty())continue;
            long existing=db.findEquivalent(entry);
            if(existing>0) {
                entry=db.get(existing);
                duplicates++;
            } else {
                db.save(entry);
                ReminderScheduler.update(this,entry);
                count++;
            }
            if(entry!=null)addSavedCard(entry,existing>0);
        }
        status.setText(count>0 ? count+"件の予定を自動登録しました"+(duplicates>0?"（重複"+duplicates+"件を省略）":"")
                               : duplicates>0 ? "すでに登録済みの予定です" : "登録できる内容がありませんでした");
    }

    private void addSavedCard(Entry entry,boolean duplicate) {
        final long id=entry.id;
        LinearLayout card=Ui.card(this);
        card.addView(Ui.text(this,(duplicate?"登録済み":"✓ 自動登録完了")+"   "+entry.title,16,Ui.INK,true));
        String when=entry.fixed?TimeUtil.fmt(entry.startMs,"M/d H:mm")+"〜"+TimeUtil.fmt(entry.endMs,"H:mm")
            :entry.deadlineMs>0?"締切 "+TimeUtil.fmt(entry.deadlineMs,"M/d H:mm"):"期限未設定";
        Ui.attach(card,Ui.text(this,entry.category+" · "+when,12,Ui.MUTED),8);
        LinearLayout actions=Ui.horizontal(this);
        TextView edit=Ui.button(this,"編集する",false);
        edit.setOnClickListener(v->startActivity(new Intent(this,EditEventActivity.class).putExtra("id",id)));
        actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
        TextView undo=Ui.button(this,"登録を取り消す",false);
        undo.setTextColor(Ui.RED);
        undo.setOnClickListener(v->{
            Entry saved=db.get(id);
            if(saved!=null){ReminderScheduler.cancel(this,saved);db.delete(id);}
            results.removeView(card);
            status.setText("登録を取り消しました");
        });
        LinearLayout.LayoutParams p=Ui.margin(this,0,-2,8,0,0,0);p.width=0;p.weight=1;actions.addView(undo,p);
        card.addView(actions,Ui.margin(this,-1,-2,0,13,0,0));
        results.addView(card,Ui.margin(this,-1,-2,0,0,0,10));
    }
}