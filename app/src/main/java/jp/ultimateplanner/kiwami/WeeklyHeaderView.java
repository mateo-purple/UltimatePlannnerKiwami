package jp.ultimateplanner.kiwami;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** Sticky day headings, in the same horizontal viewport as the 24-hour grid. */
public final class WeeklyHeaderView extends View {
    private final long monday,selectedDay;
    private final WeeklyGridView.DayClick onDayClick;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final String[] names=new String[7],dates=new String[7];
    private float downX;
    private float dp(float v){return v*density;}
    public WeeklyHeaderView(Context c,long monday,long selectedDay,WeeklyGridView.DayClick onDayClick){
        super(c);this.monday=monday;this.selectedDay=selectedDay;this.onDayClick=onDayClick;
        density=getResources().getDisplayMetrics().density;
        for(int day=0;day<7;day++){
            long date=TimeUtil.plusDay(monday,day);
            names[day]=TimeUtil.fmt(date,"E");dates[day]=TimeUtil.fmt(date,"M/d");
        }
    }
    @Override protected void onMeasure(int w,int h){
        setMeasuredDimension((int)dp(WeeklyGridView.GUTTER_DP+7*WeeklyGridView.COLUMN_DP),
            (int)dp(WeeklyGridView.HEADER_DP));
    }
    private void text(Canvas canvas,String value,int color,float size,float x,float y,boolean bold){
        paint.setColor(color);paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);
        paint.setTextSize(dp(size));paint.setStyle(Paint.Style.FILL);canvas.drawText(value,x,y,paint);
    }
    @Override protected void onDraw(Canvas canvas){
        canvas.drawColor(0xfff4f6fb);
        float gutter=dp(WeeklyGridView.GUTTER_DP),col=dp(WeeklyGridView.COLUMN_DP);
        text(canvas,"時刻",0xff697386,11,dp(5),dp(31),false);
        for(int day=0;day<7;day++){
            float x=gutter+day*col;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(TimeUtil.plusDay(monday,day)==selectedDay?0xffede4ff:0xfff4f6fb);
            canvas.drawRect(x,0,x+col,getHeight(),paint);
            text(canvas,names[day],0xff697386,11,x+dp(7),dp(19),false);
            text(canvas,dates[day],0xff20253a,13,x+dp(7),dp(39),true);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){
            float x=e.getX();
            if(Math.abs(x-downX)<dp(16) && x>=dp(WeeklyGridView.GUTTER_DP)){
                int day=(int)((x-dp(WeeklyGridView.GUTTER_DP))/dp(WeeklyGridView.COLUMN_DP));
                if(day>=0&&day<7){onDayClick.onClick(TimeUtil.plusDay(monday,day));performClick();}
            }
            return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}