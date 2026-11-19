package jp.ultimateplanner.kiwami;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

public class EditEventActivity extends Activity {
    private ScheduleDatabase db;
    private Entry entry;
    private EditText title,note,estimate,url;
    private Spinner category;
    private LinearLayout taskBox,fixedBox;
    private TextView dateBtn,startBtn,endBtn,progressLabel,difficultyLabel;
    private SeekBar progress,difficulty;
    private CheckBox repeat;
    private boolean fixed;
    private String[] categories={"課題","授業","就活","アルバイト","私用"};

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Ui.BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        db=new ScheduleDatabase(this);long id=getIntent().getLongExtra("id",0);entry=id>0?db.get(id):null;
        if(entry==null){entry=new Entry();entry.title=getIntent().getStringExtra("title")==null?"":getIntent().getStringExtra("title");entry.note=getIntent().getStringExtra("note")==null?"":getIntent().getStringExtra("note");entry.category=getIntent().getStringExtra("category")==null?"課題":getIntent().getStringExtra("category");
            entry.source=getIntent().getStringExtra("source")==null?"手入力":getIntent().getStringExtra("source");
            entry.deadlineMs=getIntent().getLongExtra("deadline",0);entry.startMs=getIntent().getLongExtra("start",0);entry.endMs=getIntent().getLongExtra("end",0);
            entry.fixed=getIntent().getBooleanExtra("fixed",false);
        }
        if(entry.startMs<=0)entry.startMs=TimeUtil.roundedQuarter(System.currentTimeMillis()+3600000L);
        if(entry.endMs<=entry.startMs)entry.endMs=entry.startMs+3600000L;
        fixed=entry.fixed;build();}
    @Override public void onDestroy(){if(db!=null)db.close();super.onDestroy();}
    private void build(){LinearLayout screen=Ui.vertical(this);screen.setBackgroundColor(Ui.BG);setContentView(screen);
        LinearLayout toolbar=Ui.horizontal(this);Ui.padding(toolbar,18,15,18,15);toolbar.setBackgroundColor(Color.WHITE);
        TextView back=Ui.text(this,"‹",30,Ui.INK,true);back.setOnClickListener(v->finish());toolbar.addView(back,Ui.margin(this,38,-2,0,0,9,0));
        TextView header=Ui.text(this,entry.id>0?"予定を編集":"新しく登録",19,Ui.INK,true);toolbar.addView(header);screen.addView(toolbar);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout form=Ui.vertical(this);Ui.padding(form,20,22,20,30);scroll.addView(form);screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout modes=Ui.horizontal(this);TextView taskTab=Ui.button(this,"やること・課題",!fixed);TextView fixedTab=Ui.button(this,"日時が決まった予定",fixed);
        modes.addView(taskTab,new LinearLayout.LayoutParams(0,-2,1));modes.addView(fixedTab,Ui.margin(this,0,-2,9,0,0,0));((LinearLayout.LayoutParams)fixedTab.getLayoutParams()).width=0;((LinearLayout.LayoutParams)fixedTab.getLayoutParams()).weight=1;
        taskTab.setOnClickListener(v->{fixed=false;entry.fixed=false;taskTab.setBackground(Ui.shape(this,Ui.PURPLE,13));taskTab.setTextColor(Color.WHITE);fixedTab.setBackground(Ui.shape(this,Ui.SOFT_PURPLE,13));fixedTab.setTextColor(Ui.PURPLE);syncMode();});
        fixedTab.setOnClickListener(v->{fixed=true;entry.fixed=true;fixedTab.setBackground(Ui.shape(this,Ui.PURPLE,13));fixedTab.setTextColor(Color.WHITE);taskTab.setBackground(Ui.shape(this,Ui.SOFT_PURPLE,13));taskTab.setTextColor(Ui.PURPLE);syncMode();});
        form.addView(modes);
        Ui.fieldLabel(form,"タイトル");title=Ui.field(this,"例：面接、レポート提出",entry.title,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);form.addView(title,Ui.lp(this,-1,-2));
        Ui.fieldLabel(form,"分類");category=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,categories);category.setAdapter(adapter);category.setPadding(Ui.dp(this,8),Ui.dp(this,6),Ui.dp(this,8),Ui.dp(this,6));category.setBackground(Ui.stroke(this,Color.WHITE,13,Ui.LINE));
        for(int n=0;n<categories.length;n++)if(categories[n].equals(entry.category))category.setSelection(n);form.addView(category,Ui.lp(this,-1,50));
        taskBox=Ui.vertical(this);Ui.fieldLabel(taskBox,"締切");dateBtn=Ui.button(this,"",false);dateBtn.setOnClickListener(v->pickDateTime(true));taskBox.addView(dateBtn,Ui.lp(this,-1,-2));
        TextView noDue=Ui.text(this,"期限を設定しない",13,Ui.PURPLE,true);noDue.setOnClickListener(v->{entry.deadlineMs=0;refreshButtons();});Ui.attach(taskBox,noDue,11);
        Ui.fieldLabel(taskBox,"見積作業時間（分）");estimate=Ui.field(this,"例：90",""+entry.estimateMin,InputType.TYPE_CLASS_NUMBER);taskBox.addView(estimate,Ui.lp(this,-1,-2));
        Ui.fieldLabel(taskBox,"進捗");progressLabel=Ui.text(this,entry.progress+"%",14,Ui.INK,true);taskBox.addView(progressLabel);
        progress=new SeekBar(this);progress.setMax(100);progress.setProgress(entry.progress);progress.setProgressTintList(ColorStateList.valueOf(Ui.PURPLE));progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){progressLabel.setText(n+"%");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});taskBox.addView(progress,Ui.lp(this,-1,44));
        Ui.fieldLabel(taskBox,"難易度");difficultyLabel=Ui.text(this,entry.difficulty+" / 5",14,Ui.INK,true);taskBox.addView(difficultyLabel);
        difficulty=new SeekBar(this);difficulty.setMax(4);difficulty.setProgress(Math.max(0,entry.difficulty-1));difficulty.setProgressTintList(ColorStateList.valueOf(Ui.PURPLE));difficulty.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){difficultyLabel.setText((n+1)+" / 5");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});taskBox.addView(difficulty,Ui.lp(this,-1,44));
        form.addView(taskBox,Ui.margin(this,-1,-2,0,5,0,0));
        fixedBox=Ui.vertical(this);Ui.fieldLabel(fixedBox,"開始日時");startBtn=Ui.button(this,"",false);startBtn.setOnClickListener(v->pickDateTime(false));fixedBox.addView(startBtn,Ui.lp(this,-1,-2));
        Ui.fieldLabel(fixedBox,"終了日時");endBtn=Ui.button(this,"",false);endBtn.setOnClickListener(v->pickEndTime());fixedBox.addView(endBtn,Ui.lp(this,-1,-2));
        repeat=new CheckBox(this);repeat.setText("毎週この曜日・時刻に繰り返す（授業・バイトなど）");repeat.setTextColor(Ui.INK);repeat.setTextSize(14);repeat.setChecked(entry.weekly);fixedBox.addView(repeat,Ui.margin(this,-1,-2,0,12,0,0));
        TextView weeklyHint=Ui.text(this,"開始日に選んだ曜日を基準に、翌週以降も自動で予定表に表示します。終了日は設定不要です。",12,Ui.MUTED,false);
        fixedBox.addView(weeklyHint,Ui.margin(this,-1,-2,0,6,0,0));
        form.addView(fixedBox,Ui.lp(this,-1,-2));
        Ui.fieldLabel(form,"関連URL（任意）");url=Ui.field(this,"https://...",entry.url,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);form.addView(url,Ui.lp(this,-1,-2));
        if(entry.url!=null && !entry.url.isEmpty()){
            TextView open=Ui.button(this,"URLを開く ↗",false);
            open.setOnClickListener(v->UrlTools.open(this,entry.url));form.addView(open,Ui.margin(this,-1,-2,0,7,0,0));
        }
        Ui.fieldLabel(form,"メモ");note=Ui.field(this,"備考・提出先・持ち物など",entry.note,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);note.setSingleLine(false);note.setMinLines(3);note.setGravity(Gravity.TOP);form.addView(note,Ui.lp(this,-1,-2));
        LinearLayout actions=Ui.horizontal(this);TextView save=Ui.button(this,"保存する",true);save.setOnClickListener(v->save());actions.addView(save,new LinearLayout.LayoutParams(0,-2,1));
        if(entry.id>0){TextView delete=Ui.button(this,"削除",false);delete.setTextColor(Ui.RED);delete.setOnClickListener(v->delete());actions.addView(delete,Ui.margin(this,-2,-2,10,0,0,0));}
        form.addView(actions,Ui.margin(this,-1,-2,0,28,0,0));
        refreshButtons();syncMode();}
    private void syncMode(){taskBox.setVisibility(fixed?View.GONE:View.VISIBLE);fixedBox.setVisibility(fixed?View.VISIBLE:View.GONE);}
    private void refreshButtons(){dateBtn.setText(entry.deadlineMs>0?TimeUtil.fmt(entry.deadlineMs,"yyyy/MM/dd (E) HH:mm"):"期限を選択する");startBtn.setText(TimeUtil.fmt(entry.startMs,"yyyy/MM/dd (E) HH:mm"));endBtn.setText(TimeUtil.fmt(entry.endMs,"yyyy/MM/dd (E) HH:mm"));}
    private void datePick(long initial,java.util.function.LongConsumer after){Calendar c=Calendar.getInstance();c.setTimeInMillis(initial);
        new DatePickerDialog(this,(picker,y,m,d)->{Calendar date=Calendar.getInstance();date.setTimeInMillis(initial);date.set(y,m,d);new TimePickerDialog(this,(t,h,min)->{date.set(Calendar.HOUR_OF_DAY,h);date.set(Calendar.MINUTE,min);date.set(Calendar.SECOND,0);date.set(Calendar.MILLISECOND,0);after.accept(date.getTimeInMillis());refreshButtons();},c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
    private void pickDateTime(boolean deadline){long initial=entry.deadlineMs>0?entry.deadlineMs:TimeUtil.at(TimeUtil.day(System.currentTimeMillis()+86400000L),23,59);if(deadline)datePick(initial,n->entry.deadlineMs=n);else datePick(entry.startMs,n->{long span=entry.endMs-entry.startMs;entry.startMs=n;entry.endMs=n+Math.max(span,15*60000L);});}
    private void pickEndTime(){datePick(entry.endMs,n->entry.endMs=n);}
    private void save(){String name=title.getText().toString().trim();if(name.isEmpty()){title.setError("タイトルを入力してください");title.requestFocus();return;}
        String link=url.getText().toString().trim();if(!link.isEmpty()&&!UrlTools.valid(link)){url.setError("https:// または http:// で始まるURLを入力してください");return;}
        entry.url=link;entry.title=name;entry.note=note.getText().toString().trim();entry.category=(String)category.getSelectedItem();entry.fixed=fixed;entry.weekly=fixed&&repeat.isChecked();
        if(fixed){if(entry.endMs<=entry.startMs){Ui.alert(this,"日時を確認してください","終了日時は開始日時より後にしてください。");return;}}
        else{int minutes;try{minutes=Integer.parseInt(estimate.getText().toString().trim());}catch(Exception ex){estimate.setError("数字を入力してください");return;}
            if(minutes<=0||minutes>100000){estimate.setError("1～100000分で入力してください");return;}entry.estimateMin=minutes;entry.progress=progress.getProgress();entry.difficulty=difficulty.getProgress()+1;}
        Entry old=entry.id>0?db.get(entry.id):null;if(old!=null)ReminderScheduler.cancel(this,old);db.save(entry);ReminderScheduler.update(this,entry);
        Toast.makeText(this,"保存しました",Toast.LENGTH_SHORT).show();finish();}
    private void delete(){new AlertDialog.Builder(this).setTitle("削除しますか？").setMessage("「"+entry.title+"」を削除します。").setNegativeButton("キャンセル",null).setPositiveButton("削除",(a,b)->{ReminderScheduler.cancel(this,entry);db.delete(entry.id);finish();}).show();}
}