package jp.ultimateplanner.kiwami;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Seven-column, 24-hour weekly time grid. Tapping a block opens the existing editor. */
public class WeeklyGridView extends View {
    public interface BlockClick { void onClick(AutoPlanner.Block block); }
    public interface DayClick { void onClick(long day); }
    private final long monday, selectedDay;
    private final List<AutoPlanner.Block> blocks;
    private final BlockClick blockClick;
    private final DayClick dayClick;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Hit> hits=new ArrayList<>();
    private final int gutter=48, col=112, header=56, hour=65;
    private final float density;
    private float downX,downY;
    private static class Hit { RectF rect; AutoPlanner.Block block; Hit(RectF r,AutoPlanner.Block b){rect=r;block=b;} }

    public WeeklyGridView(Context context,long monday,long selectedDay,List<AutoPlanner.Block> blocks,BlockClick click,DayClick dayClick){
        super(context);this.monday=monday;this.selectedDay=selectedDay;this.blocks=blocks;
        this.blockClick=click;this.dayClick=dayClick;density=getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }
    public int columnWidthDp(){return col;}
    private float dp(float n){return n*density;}
    @Override protected void onMeasure(int widthSpec,int heightSpec){setMeasuredDimension((int)dp(gutter+7*col),(int)dp(header+24*hour+6));}
    private void fill(Canvas c,int color,float l,float t,float r,float b){paint.setColor(color);paint.setStyle(Paint.Style.FILL);c.drawRect(l,t,r,b,paint);}
    private void line(Canvas c,int color,float x,float y,float xx,float yy){paint.setColor(color);paint.setStrokeWidth(dp(.7f));c.drawLine(x,y,xx,yy,paint);}
    private void text(Canvas c,String str,int color,float size,float x,float y,boolean bold){paint.setColor(color);paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(size));paint.setTypeface(bold?android.graphics.Typeface.create("sans-serif-medium",0):android.graphics.Typeface.DEFAULT);c.drawText(str,x,y,paint);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);hits.clear();
        float w=getWidth(),h=getHeight(),head=dp(header),g=dp(gutter),cw=dp(col),hh=dp(hour);
        c.drawColor(Color.WHITE);
        for(int d=0;d<7;d++){
            long day=TimeUtil.plusDay(monday,d);float x=g+d*cw;
            if(day==selectedDay)fill(c,0xfff3efff,x,0,x+cw,h);
            fill(c,day==selectedDay?0xffede4ff:0xfff8f9fc,x,0,x+cw,head);
            text(c,TimeUtil.fmt(day,"E"),0xff697386,12,x+dp(12),dp(22),false);
            text(c,TimeUtil.fmt(day,"M/d"),0xff20253a,16,x+dp(12),dp(44),true);
            line(c,0xffe6e8ef,x,0,x,h);
        }
        for(int t=0;t<=24;t++){
            float y=head+t*hh;line(c,0xffe9ebf0,g,y,w,y);
            if(t<24)text(c,String.format(java.util.Locale.JAPAN,"%02d:00",t),0xff87909e,11,dp(5),y+dp(14),false);
        }
        for(AutoPlanner.Block b:blocks){
            for(int d=0;d<7;d++){
                long start=TimeUtil.plusDay(monday,d),end=TimeUtil.plusDay(start,1);
                if(b.end<=start||b.start>=end)continue;
                long from=Math.max(start,b.start),to=Math.min(end,b.end);
                float top=head+((from-start)/60000f)/60f*hh;
                float bottom=head+((to-start)/60000f)/60f*hh;
                if(bottom-top<dp(6))bottom=top+dp(6);
                float left=g+d*cw+dp(3),right=g+(d+1)*cw-dp(3);
                int color=b.generated?0xff7554d8:Ui.categoryColor(b.entry.category);
                RectF r=new RectF(left,top,right,bottom);
                paint.setColor(color);paint.setStyle(Paint.Style.FILL);c.drawRoundRect(r,dp(6),dp(6),paint);
                c.save();c.clipRect(r);
                text(c,b.entry.title,Color.WHITE,12,left+dp(6),top+dp(17),true);
                if(bottom-top>dp(37))text(c,TimeUtil.time(b.start)+"–"+TimeUtil.time(b.end),Color.WHITE,10,left+dp(6),top+dp(32),false);
                c.restore();hits.add(new Hit(r,b));
            }
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){
            if(Math.abs(e.getX()-downX)>dp(12)||Math.abs(e.getY()-downY)>dp(12))return true;
            for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).rect.contains(e.getX(),e.getY())){blockClick.onClick(hits.get(i).block);return true;}
            if(e.getY()<dp(header)&&e.getX()>=dp(gutter)){
                int d=Math.min(6,Math.max(0,(int)((e.getX()-dp(gutter))/dp(col))));dayClick.onClick(TimeUtil.plusDay(monday,d));
            }
            return true;
        }
        return true;
    }
}
