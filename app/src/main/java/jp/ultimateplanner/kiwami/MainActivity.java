package jp.ultimateplanner.kiwami;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.text.TextUtils;
import java.util.*;

/** Main navigation: overview / weekly agenda / tasks / settings. All screens are native views. */
public class MainActivity extends Activity {
    private final String[] names={"ホーム","予定表","タスク","予定変更","設定"};
    private final String[] symbols={"⌂","▦","☑","↻","⚙"};
    private LinearLayout root,body,bar;
    private int page=1; // Start directly on the usable weekly timetable.
    private long selectedDay=TimeUtil.day(System.currentTimeMillis());
    private ScheduleDatabase database;
    private Prefs prefs;
    private List<Entry> entries=new ArrayList<>();
    private AutoPlanner.Plan plan;
    private boolean firstResume=true;
    private int calendarScrollY=-1; // Preserved when changing weeks.

    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Color.WHITE);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        database=new ScheduleDatabase(this);prefs=new Prefs(this);refresh();
        if(b!=null){page=b.getInt("tab",1);selectedDay=b.getLong("date",selectedDay);}render();requestNotificationPermission();consumeShare(getIntent());}
    @Override public void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);consumeShare(i);}
    @Override public void onSaveInstanceState(Bundle b){b.putInt("tab",page);b.putLong("date",selectedDay);super.onSaveInstanceState(b);}
    @Override protected void onResume(){
        super.onResume();
        if(firstResume){firstResume=false;}
        else if(database!=null){refresh();render();}
        // No periodic polling, and a no-op cloud sync never rebuilds the UI.
        CloudSync.syncIfChanged(this,changed->{if(changed>0 && database!=null && !isFinishing()){
            refresh();render();
        }});
    }
    @Override public void onDestroy(){if(database!=null)database.close();super.onDestroy();}
    private void consumeShare(Intent incoming){if(incoming==null||!Intent.ACTION_SEND.equals(incoming.getAction()))return;
        Intent next=new Intent(incoming);next.setClass(this,ImportActivity.class);next.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(next);setIntent(new Intent(this,MainActivity.class));}
    private void refresh(){
        entries=database.all();
        // The expensive free-slot planner is unnecessary on Settings and Tasks.
        plan=(page==0||page==1)?AutoPlanner.build(entries,prefs,System.currentTimeMillis(),7):new AutoPlanner.Plan();
    }
    private void navigate(int next){page=next;refresh();render();}
    private void addNew(){startActivity(new Intent(this,EditEventActivity.class));}
    private void importNew(){startActivity(new Intent(this,ImportActivity.class).putExtra(ImportActivity.EXTRA_CAPTURE_ON_LAUNCH,true));}
    private void edit(Entry e){startActivity(new Intent(this,EditEventActivity.class).putExtra("id",e.id));}

    private void render(){if(root==null){root=Ui.vertical(this);root.setBackgroundColor(Ui.BG);setContentView(root);}root.removeAllViews();
        LinearLayout toolbar=Ui.horizontal(this);Ui.padding(toolbar,20,15,15,13);toolbar.setBackgroundColor(Color.WHITE);
        TextView title=Ui.text(this,"アルティメットプランナー極み(仮)",16,Ui.INK,true);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView importBtn=topAction("📷");importBtn.setContentDescription("カメラで予定を取り込む");importBtn.setOnClickListener(v->importNew());toolbar.addView(importBtn,Ui.margin(this,42,42,0,0,7,0));
        TextView plus=topAction("＋");plus.setContentDescription("予定を追加する");plus.setTextColor(Color.WHITE);Ui.bg(plus,Ui.PURPLE,14);plus.setOnClickListener(v->addNew());toolbar.addView(plus,Ui.lp(this,42,42));root.addView(toolbar,Ui.lp(this,-1,-2));
        if(page==1){
            // Dedicated time-grid viewport: avoid a vertical ScrollView nested inside another.
            body=Ui.vertical(this);
            Ui.padding(body,14,14,14,0);
            root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            calendar();
        }else{
            ScrollView scroll=new ScrollView(this);
            scroll.setFillViewport(true);
            scroll.setVerticalScrollBarEnabled(false);
            body=Ui.vertical(this);
            Ui.padding(body,20,22,20,30);
            scroll.addView(body);
            root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            if(page==0)home();else if(page==2)tasks();else if(page==3)recovery();else settings();
        }
        bottom();}
    private TextView topAction(String s){TextView v=Ui.text(this,s,24,Ui.INK,true);v.setGravity(Gravity.CENTER);Ui.bg(v,Ui.BG,14);return v;}
    private void bottom(){bar=Ui.horizontal(this);bar.setBackground(Ui.shape(this,Ui.WHITE,0));bar.setElevation(Ui.dp(this,10));Ui.padding(bar,4,8,4,8);
        for(int i=0;i<names.length;i++){final int next=i;boolean chosen=page==i;LinearLayout item=Ui.vertical(this);item.setGravity(Gravity.CENTER);Ui.padding(item,3,8,3,7);
            TextView icon=Ui.text(this,symbols[i],24,chosen?Ui.PURPLE:Ui.MUTED,true);icon.setGravity(Gravity.CENTER);item.addView(icon,Ui.lp(this,-1,26));
            TextView name=Ui.text(this,names[i],11,chosen?Ui.PURPLE:Ui.MUTED,chosen);name.setGravity(Gravity.CENTER);item.addView(name,Ui.margin(this,-1,-2,0,3,0,0));
            if(chosen)Ui.bg(item,Ui.SOFT_PURPLE,14);item.setOnClickListener(v->navigate(next));bar.addView(item,new LinearLayout.LayoutParams(0,-2,1));}
        root.addView(bar,Ui.lp(this,-1,-2));}
    private void heading(String h,String sub){Ui.attach(body,Ui.section(this,h),0);if(sub!=null&&!sub.isEmpty())Ui.attach(body,Ui.text(this,sub,13,Ui.MUTED),6);}

    private void home(){
        heading(TimeUtil.fmt(System.currentTimeMillis(),"M月d日 E曜日"),null);
        LinearLayout summary=Ui.horizontal(this);int fixed=0,task=0,due=0;long today=TimeUtil.day(System.currentTimeMillis());
        for(AutoPlanner.Block b:plan.blocks)if(!b.generated&&TimeUtil.day(b.start)==today)fixed++;
        for(Entry e:entries)if(!e.fixed&&!e.done()){task++;if(e.deadlineMs>0&&e.deadlineMs<TimeUtil.plusDay(today,2))due++;}
        metric(summary,Integer.toString(fixed),"予定",Ui.PURPLE);metric(summary,Integer.toString(task),"未完了",Ui.GREEN);metric(summary,Integer.toString(due),"期限間近",Ui.RED);
        body.addView(summary,Ui.margin(this,-1,-2,0,18,0,0));
        LinearLayout actions=Ui.horizontal(this);TextView schedule=Ui.button(this,"週間予定を見る",true);schedule.setOnClickListener(v->navigate(1));actions.addView(schedule,new LinearLayout.LayoutParams(0,-2,1));
        TextView intake=Ui.button(this,"カメラで登録",false);intake.setTextSize(12);intake.setOnClickListener(v->importNew());actions.addView(intake,Ui.margin(this,0,-2,8,0,0,0));
        ((LinearLayout.LayoutParams)intake.getLayoutParams()).width=0;((LinearLayout.LayoutParams)intake.getLayoutParams()).weight=1;
        body.addView(actions,Ui.lp(this,-1,-2));
        Ui.attach(body,Ui.section(this,"今日の予定"),26);
        List<AutoPlanner.Block> todayBlocks=blocksForDay(today);if(todayBlocks.isEmpty())emptyCard("今日の予定はありません", "＋ から予定を追加できます");else {int shown=0;for(AutoPlanner.Block b:todayBlocks){if(++shown>6)break;blockCard(b);}if(todayBlocks.size()>6)link(body,"残り"+(todayBlocks.size()-6)+"件を見る",()->navigate(1));}
        Ui.attach(body,Ui.section(this,"優先タスク"),26);
        int count=0;for(Entry e:Priority.tasks(entries,System.currentTimeMillis())){if(e.done())continue;taskCard(e);if(++count==3)break;}
        if(count==0)emptyCard("未完了のタスクはありません","課題や就活などを登録しましょう");
        link(body,"すべてのタスクを見る",()->navigate(2));
        if(!plan.unscheduled.isEmpty()){
            int min=0;for(int n:plan.unscheduled.values())min+=n;
            LinearLayout warning=Ui.card(this);Ui.attach(warning,Ui.text(this,"割り当てられない作業があります",14,Ui.RED,true),0);
            Ui.attach(warning,Ui.text(this,TimeUtil.minutes(min)+"が勤務・授業などの予定や期限と重なっています。設定で作業可能時間を見直してください。",13,Ui.MUTED),6);
            body.addView(warning,Ui.margin(this,-1,-2,0,18,0,0));
        }
    }
    private void metric(LinearLayout container,String count,String label,int color){LinearLayout card=Ui.card(this);TextView n=Ui.text(this,count,25,color,true);card.addView(n);Ui.attach(card,Ui.text(this,label,12,Ui.MUTED),3);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(Ui.dp(this,3),0,Ui.dp(this,3),0);container.addView(card,p);}

    private void calendar(){
        long monday=TimeUtil.weekStart(selectedDay);
        long sunday=TimeUtil.plusDay(monday,6);
        heading("週間予定表",TimeUtil.fmt(monday,"M月d日")+" 〜 "+TimeUtil.fmt(sunday,"M月d日"));
        LinearLayout controller=Ui.horizontal(this);
        TextView prev=Ui.button(this,"‹ 前週",false);
        prev.setOnClickListener(v->{selectedDay=TimeUtil.plusDay(selectedDay,-7);render();});
        controller.addView(prev,Ui.lp(this,77,46));
        TextView week=Ui.text(this,TimeUtil.fmt(monday,"M/d")+"〜"+TimeUtil.fmt(sunday,"M/d"),14,Ui.INK,true);
        week.setGravity(Gravity.CENTER);
        controller.addView(week,new LinearLayout.LayoutParams(0,-2,1));
        TextView now=Ui.button(this,"今日",false);
        now.setOnClickListener(v->{selectedDay=TimeUtil.day(System.currentTimeMillis());render();});
        controller.addView(now,Ui.margin(this,-2,-2,0,0,6,0));
        TextView next=Ui.button(this,"翌週 ›",false);
        next.setOnClickListener(v->{selectedDay=TimeUtil.plusDay(selectedDay,7);render();});
        controller.addView(next,Ui.lp(this,77,46));
        body.addView(controller,Ui.margin(this,-1,-2,0,8,0,12));

        // Only expand fixed events here. Running the complete automatic planner a
        // second time for each week switch caused excessive work on the UI thread.
        List<AutoPlanner.Block> visible=AutoPlanner.fixedBlocks(entries,monday,7);
        long end=TimeUtil.plusDay(monday,7);
        for(AutoPlanner.Block block:plan.blocks){
            if(block.generated && block.start<end && block.end>monday)visible.add(block);
        }
        if(visible.isEmpty()){
            TextView hint=Ui.text(this,"予定なし  ·  右上の＋から登録できます",12,Ui.MUTED);
            body.addView(hint,Ui.margin(this,-1,-2,0,0,0,8));
        }
        HorizontalScrollView horizontal=new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(true);
        LinearLayout weekContent=Ui.vertical(this);
        WeeklyHeaderView header=new WeeklyHeaderView(this,monday,selectedDay,day->{selectedDay=day;render();});
        weekContent.addView(header,Ui.lp(this,-2,WeeklyGridView.HEADER_DP));
        ScrollView timeScroll=new ScrollView(this);
        timeScroll.setFillViewport(false);
        timeScroll.setVerticalScrollBarEnabled(true);
        timeScroll.setBackgroundColor(Color.WHITE);
        WeeklyGridView grid=new WeeklyGridView(this,monday,selectedDay,visible,
            block->edit(block.entry),day->{selectedDay=day;render();});
        timeScroll.addView(grid,new android.widget.FrameLayout.LayoutParams(-2,-2));
        timeScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->calendarScrollY=y);
        weekContent.addView(timeScroll,new LinearLayout.LayoutParams(-1,0,1));
        horizontal.addView(weekContent,new android.widget.FrameLayout.LayoutParams(-2,-1));
        body.addView(horizontal,new LinearLayout.LayoutParams(-1,0,1));
        final int desired=calendarScrollY>=0?calendarScrollY:Ui.dp(this,7*WeeklyGridView.HOUR_DP);
        timeScroll.post(()->timeScroll.scrollTo(0,desired));
        horizontal.post(()->{
            int selected=(int)((selectedDay-monday)/(86400000L));
            if(selected>=3)horizontal.scrollTo(Math.max(0,Ui.dp(this,(selected-2)*grid.columnWidthDp())),0);
        });
        TextView guidance=Ui.text(this,"縦：時刻  ／  横：曜日  ／  色付きの予定をタップして編集",11,Ui.MUTED);
        guidance.setGravity(Gravity.CENTER);
        body.addView(guidance,Ui.margin(this,-1,-2,0,7,0,8));
    }
    private List<AutoPlanner.Block> blocksForDay(long day){List<AutoPlanner.Block> l=new ArrayList<>();long end=TimeUtil.plusDay(day,1);
        for(AutoPlanner.Block b:plan.blocks)if(b.start<end&&b.end>day)l.add(b);l.sort(Comparator.comparingLong(a->a.start));return l;}
    private void blockCard(AutoPlanner.Block b){LinearLayout card=Ui.card(this);LinearLayout row=Ui.horizontal(this);
        TextView times=Ui.text(this,TimeUtil.time(b.start)+"–"+TimeUtil.time(b.end),13,Ui.MUTED,true);row.addView(times,Ui.lp(this,104,-2));
        LinearLayout words=Ui.vertical(this);TextView name=Ui.text(this,b.entry.title,15,Ui.INK,true);name.setMaxLines(2);words.addView(name);
        Ui.attach(words,Ui.text(this,b.generated?"自動配置 · "+b.entry.category:b.entry.category+(b.entry.weekly?" · 毎週":""),12,b.generated?Ui.PURPLE:Ui.MUTED),5);
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));card.addView(row);card.setOnClickListener(v->edit(b.entry));
        if(b.generated)card.setBackground(Ui.stroke(this,Ui.SOFT_PURPLE,18,Ui.SOFT_PURPLE));
        body.addView(card,Ui.margin(this,-1,-2,0,10,0,0));}
    private void tasks(){heading("タスク","期限・難易度・残り作業時間から優先順位を計算");
        int total=0,complete=0;for(Entry e:entries)if(!e.fixed){total++;if(e.done())complete++;}
        LinearLayout completion=Ui.card(this);completion.addView(Ui.text(this,"完了  "+complete+" / "+total,19,Ui.INK,true));
        ProgressBar pb=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);pb.setMax(Math.max(total,1));pb.setProgress(complete);pb.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));completion.addView(pb,Ui.margin(this,-1,8,0,15,0,0));
        body.addView(completion,Ui.margin(this,-1,-2,0,18,0,10));
        List<Entry> sorted=Priority.tasks(entries,System.currentTimeMillis());if(sorted.isEmpty())emptyCard("タスクがありません","「＋」で期限や所要時間を登録しましょう");else for(Entry e:sorted)taskCard(e);
    }
    private void taskCard(Entry e){LinearLayout card=Ui.card(this);LinearLayout r=Ui.horizontal(this);
        TextView dot=Ui.text(this,"●",12,Ui.categoryColor(e.category));r.addView(dot,Ui.margin(this,18,-2,0,0,3,0));
        LinearLayout info=Ui.vertical(this);TextView title=Ui.text(this,e.title,16,Ui.INK,true);title.setMaxLines(2);info.addView(title);
        Ui.attach(info,Ui.text(this,e.category+"  •  "+TimeUtil.relativeDeadline(e.deadlineMs)+"  •  "+TimeUtil.minutes(e.remainingMin()),12,Ui.MUTED),7);
        r.addView(info,new LinearLayout.LayoutParams(0,-2,1));card.addView(r);
        LinearLayout bottom=Ui.horizontal(this);TextView priority=Ui.text(this,e.done()?"完了":Priority.label(e,System.currentTimeMillis()),12,e.done()?Ui.GREEN:Ui.PURPLE,true);bottom.addView(priority,new LinearLayout.LayoutParams(0,-2,1));
        TextView perc=Ui.text(this,e.progress+"%",12,Ui.MUTED,true);bottom.addView(perc);card.addView(bottom,Ui.margin(this,-1,-2,0,13,0,5));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(e.progress);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.PURPLE));card.addView(bar,Ui.lp(this,-1,5));
        LinearLayout action=Ui.horizontal(this);TextView edit=Ui.text(this,"詳細・編集",13,Ui.PURPLE,true);edit.setOnClickListener(v->edit(e));action.addView(edit,new LinearLayout.LayoutParams(0,-2,1));
        TextView done=Ui.text(this,e.done()?"↺ 未完了に戻す":"✓ 完了にする",13,Ui.GREEN,true);done.setOnClickListener(v->{e.progress=e.done()?0:100;database.save(e);ReminderScheduler.update(this,e);refresh();render();});action.addView(done);
        card.addView(action,Ui.margin(this,-1,-2,0,14,0,0));body.addView(card,Ui.margin(this,-1,-2,0,9,0,0));}
    private void emptyCard(String title,String subtitle){LinearLayout c=Ui.card(this);Ui.attach(c,Ui.text(this,title,15,Ui.INK,true),0);Ui.attach(c,Ui.text(this,subtitle,13,Ui.MUTED),7);body.addView(c,Ui.margin(this,-1,-2,0,11,0,0));}
    private void link(LinearLayout parent,String s,Runnable r){TextView t=Ui.text(this,s+"  ›",14,Ui.PURPLE,true);t.setOnClickListener(v->r.run());parent.addView(t,Ui.margin(this,-1,42,0,8,0,0));}

    /** Missed appointments, retry history and sick-day deferral. */
    private void recovery(){
        heading("予定変更","未完了の予定を再計画し、体調不良の日は後ろへ移せます");
        LinearLayout sick=Ui.card(this);sick.addView(Ui.text(this,"体調不良で予定をずらす",16,Ui.INK,true));
        Ui.attach(sick,Ui.text(this,"対象期間の未完了予定を後日へ移動します。授業・面接・アルバイトは振替候補として登録し、元の毎週予定は翌週以降そのまま残します。締切そのものは変更しません。",12,Ui.MUTED),7);
        LinearLayout choices=Ui.horizontal(this);
        int[] spans={1,2,3};String[] labels={"今日だけ","2日間","3日間"};
        for(int i=0;i<spans.length;i++){
            final int d=spans[i];TextView option=Ui.button(this,labels[i],false);option.setTextSize(12);
            option.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("体調不良を申請しますか？")
                .setMessage("今日から"+d+"日間の未完了予定を後日に移します。これはアプリ内での変更です。外部の締切や予約は自動変更されません。")
                .setNegativeButton("キャンセル",null).setPositiveButton("予定を変更",(dialog,which)->applyIllness(d)).show());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(Ui.dp(this,2),Ui.dp(this,10),Ui.dp(this,2),0);choices.addView(option,lp);
        }
        sick.addView(choices);body.addView(sick,Ui.margin(this,-1,-2,0,12,0,22));
        Ui.attach(body,Ui.section(this,"直近の未完了・履歴"),0);
        List<Entry> recent=new ArrayList<>();long now=System.currentTimeMillis();
        for(Entry e:entries){
            if(RecoveryPlanner.missed(e,now) || (e.history!=null&&!e.history.isEmpty()))recent.add(e);
        }
        recent.sort((a,b)->Long.compare(Math.max(b.updatedMs,b.deadlineMs),Math.max(a.updatedMs,a.deadlineMs)));
        if(recent.isEmpty()){emptyCard("該当する予定はありません","直近21日間の未完了予定と変更履歴が表示されます");return;}
        int shown=0;
        for(Entry e:recent){if(shown++>=45)break;
            LinearLayout card=Ui.card(this);card.addView(Ui.text(this,e.title,16,Ui.INK,true));
            String when=e.fixed?"予定 "+TimeUtil.fmt(e.startMs,"M/d H:mm"):"締切 "+(e.deadlineMs>0?TimeUtil.fmt(e.deadlineMs,"M/d H:mm"):"未設定");
            Ui.attach(card,Ui.text(this,when+(e.progress>=100?" · 完了済み":" · 未完了"),12,Ui.MUTED),6);
            String last=RecoveryPlanner.latestHistory(e);if(!last.isEmpty())Ui.attach(card,Ui.text(this,"変更履歴: "+last,12,Ui.MUTED),6);
            LinearLayout row=Ui.horizontal(this);
            TextView retry=Ui.button(this,"もう一度組み込む",true);retry.setTextSize(12);
            retry.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("予定を再計画")
                .setMessage("「"+e.title+"」を空き時間へ戻します。元の締切は変更しません。")
                .setNegativeButton("キャンセル",null).setPositiveButton("再計画",(d,w)->{
                    Entry changed=RecoveryPlanner.manual(e,entries,System.currentTimeMillis());database.save(changed);
                    ReminderScheduler.update(this,changed);CloudSync.sync(this,false,null);refresh();render();
                }).show());
            row.addView(retry,new LinearLayout.LayoutParams(0,-2,2));
            TextView editButton=Ui.button(this,"編集",false);editButton.setTextSize(12);editButton.setOnClickListener(v->edit(e));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(Ui.dp(this,6),0,0,0);row.addView(editButton,lp);
            card.addView(row,Ui.margin(this,-1,-2,0,12,0,0));
            LinearLayout secondary=Ui.horizontal(this);
            if(e.progress<100){TextView completed=Ui.text(this,"✓ 完了にする",13,Ui.GREEN,true);
                completed.setOnClickListener(v->{e.progress=100;database.save(e);ReminderScheduler.cancel(this,e);CloudSync.sync(this,false,null);refresh();render();});
                secondary.addView(completed,new LinearLayout.LayoutParams(0,-2,1));}
            if(UrlTools.valid(e.url)){TextView open=Ui.text(this,"関連URLを開く ↗",13,Ui.PURPLE,true);open.setOnClickListener(v->UrlTools.open(this,e.url));secondary.addView(open);}
            if(secondary.getChildCount()>0)card.addView(secondary,Ui.margin(this,-1,-2,0,13,0,0));
            body.addView(card,Ui.margin(this,-1,-2,0,9,0,0));
        }
    }
    private void applyIllness(int days){
        List<Entry> changed=RecoveryPlanner.illness(entries,System.currentTimeMillis(),days);
        for(Entry e:changed){database.save(e);ReminderScheduler.update(this,e);}
        if(!changed.isEmpty())CloudSync.sync(this,false,null);
        Toast.makeText(this,changed.size()+"件を再計画しました",Toast.LENGTH_LONG).show();
        refresh();render();
    }

    private void settings(){heading("設定","自動配置と通知の動作を調整します");
        LinearLayout cloudCard=Ui.card(this);cloudCard.addView(Ui.text(this,"端末間の同期",16,Ui.INK,true));
        Ui.attach(cloudCard,Ui.text(this,CloudSync.accountLabel(this),13,Ui.MUTED),8);
        TextView cloudButton=Ui.button(this,"PC・iPhoneと予定を同期する",true);cloudButton.setOnClickListener(v->startActivity(new Intent(this,CloudSyncActivity.class)));
        cloudCard.addView(cloudButton,Ui.margin(this,-1,-2,0,12,0,0));body.addView(cloudCard,Ui.margin(this,-1,-2,0,10,0,0));
        LinearLayout time=Ui.card(this);time.addView(Ui.text(this,"作業可能な時間帯",16,Ui.INK,true));
        TextView span=Ui.button(this,String.format(Locale.JAPAN,"%02d:%02d  ～  %02d:%02d",prefs.fromHour(),prefs.fromMinute(),prefs.toHour(),prefs.toMinute()),false);
        span.setOnClickListener(v->{new TimePickerDialog(this,(picker,h,m)->{prefs.setFrom(h,m);new TimePickerDialog(this,(p2,hh,mm)->{prefs.setTo(hh,mm);refresh();render();},prefs.toHour(),prefs.toMinute(),true).show();},prefs.fromHour(),prefs.fromMinute(),true).show();});
        time.addView(span,Ui.margin(this,-1,-2,0,13,0,0));
        Ui.attach(time,Ui.text(this,"この時間内で空いている場所にタスクを割り振ります。",12,Ui.MUTED),10);
        body.addView(time,Ui.margin(this,-1,-2,0,18,0,0));
        LinearLayout chunk=Ui.card(this);chunk.addView(Ui.text(this,"1回の集中時間",16,Ui.INK,true));
        LinearLayout choices=Ui.horizontal(this);for(int n:new int[]{30,60,90,120}){
            TextView b=Ui.button(this,n+"分",n==prefs.chunk());b.setTextSize(12);b.setOnClickListener(v->{prefs.setChunk(n);refresh();render();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(Ui.dp(this,2),Ui.dp(this,12),Ui.dp(this,2),0);choices.addView(b,p);
        }chunk.addView(choices);body.addView(chunk,Ui.margin(this,-1,-2,0,12,0,0));
        LinearLayout notice=Ui.card(this);notice.addView(Ui.text(this,"締切の通知",16,Ui.INK,true));
        toggle(notice,"通知を有効にする",prefs.notifications(),v->{prefs.setNotifications(v);if(v)requestNotificationPermission();ReminderScheduler.all(this);refresh();render();});
        toggle(notice,"前日に通知",prefs.remindDay(),v->{prefs.setRemindDay(v);ReminderScheduler.all(this);});
        toggle(notice,"2時間前に通知",prefs.remindHours(),v->{prefs.setRemindHours(v);ReminderScheduler.all(this);});
        Ui.attach(notice,Ui.text(this,"通知時刻はAndroidの省電力設定によって前後することがあります。",12,Ui.MUTED),8);
        body.addView(notice,Ui.margin(this,-1,-2,0,12,0,0));
        LinearLayout about=Ui.card(this);about.addView(Ui.text(this,"取り込みについて",16,Ui.INK,true));
        Ui.attach(about,Ui.text(this,"画像内の日本語を端末上で読み取ります。eシラバスなどの共有テキスト・画像にも対応します。読み取り完了後、予定として自動登録します。誤認識は登録結果から編集・取り消しできます。ログインが必要なページの自動取得には対応していません。",13,Ui.MUTED),10);
        TextView importBtn=Ui.button(this,"カメラで取り込む",false);importBtn.setOnClickListener(v->importNew());about.addView(importBtn,Ui.margin(this,-1,-2,0,15,0,0));body.addView(about,Ui.margin(this,-1,-2,0,12,0,0));
        LinearLayout backupCard=Ui.card(this);backupCard.addView(Ui.text(this,"データのバックアップ",16,Ui.INK,true));
        Ui.attach(backupCard,Ui.text(this,"通信がなくても予定をJSONファイルに保存・復元できます。Web版と共通形式です。",13,Ui.MUTED),10);
        TextView backupButton=Ui.button(this,"JSONの保存・復元",false);
        backupButton.setOnClickListener(v->startActivity(new Intent(this,BackupActivity.class)));
        backupCard.addView(backupButton,Ui.margin(this,-1,-2,0,12,0,0));
        body.addView(backupCard,Ui.margin(this,-1,-2,0,0,0,12));
        LinearLayout danger=Ui.card(this);danger.addView(Ui.text(this,"データ管理",16,Ui.INK,true));
        TextView erase=Ui.text(this,"すべての予定とタスクを削除",14,Ui.RED,true);erase.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("すべて削除しますか？").setMessage("復元できません。登録した予定・タスクを消去します。").setNegativeButton("キャンセル",null).setPositiveButton("削除",(d,w)->{database.clear();ReminderScheduler.all(this);refresh();render();}).show());
        danger.addView(erase,Ui.margin(this,-1,-2,0,17,0,4));body.addView(danger,Ui.margin(this,-1,-2,0,12,0,0));
    }
    private interface BoolHandler{void changed(boolean x);}
    private void toggle(LinearLayout holder,String title,boolean value,BoolHandler handler){LinearLayout row=Ui.horizontal(this);TextView text=Ui.text(this,title,14,Ui.INK);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));Switch s=new Switch(this);s.setChecked(value);s.setOnCheckedChangeListener((b,v)->handler.changed(v));row.addView(s);holder.addView(row,Ui.margin(this,-1,-2,0,15,0,0));}
    void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},93);}
}