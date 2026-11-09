package jp.ultimateplanner.kiwami;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    private Ui() {}
    static final int BG = Color.rgb(248,249,253), WHITE = Color.WHITE, INK = Color.rgb(28,35,54);
    static final int MUTED = Color.rgb(117,126,146), PURPLE = Color.rgb(96,82,225);
    static final int SOFT_PURPLE = Color.rgb(237,234,255), LINE=Color.rgb(231,234,243);
    static final int RED=Color.rgb(207,79,83), ORANGE=Color.rgb(182,114,35), GREEN=Color.rgb(27,143,112);
    public static int dp(Context c,float v) {return (int)(v*c.getResources().getDisplayMetrics().density+.5f);}
    public static LinearLayout vertical(Context c) {LinearLayout v=new LinearLayout(c);v.setOrientation(1);return v;}
    public static LinearLayout horizontal(Context c) {LinearLayout v=new LinearLayout(c);v.setOrientation(0);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    public static GradientDrawable shape(Context c,int color,int radius) {GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    public static GradientDrawable stroke(Context c,int color,int radius,int outline) {GradientDrawable d=shape(c,color,radius);d.setStroke(dp(c,1),outline);return d;}
    public static void bg(View v,int color,int radius){v.setBackground(shape(v.getContext(),color,radius));}
    public static void padding(View v,int l,int t,int r,int b){Context c=v.getContext();v.setPadding(dp(c,l),dp(c,t),dp(c,r),dp(c,b));}
    public static TextView text(Context c,String value,int size,int color,boolean bold){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("kern");if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    public static TextView text(Context c,String value,int size,int color){return text(c,value,size,color,false);}
    public static TextView button(Context c,String text,boolean filled){TextView b=text(c,text,14,filled?WHITE:PURPLE,true);b.setGravity(Gravity.CENTER);padding(b,16,13,16,13);b.setBackground(shape(c,filled?PURPLE:SOFT_PURPLE,13));b.setClickable(true);b.setFocusable(true);return b;}
    public static LinearLayout card(Context c){LinearLayout v=vertical(c);padding(v,16,16,16,16);v.setBackground(stroke(c,WHITE,18,LINE));v.setElevation(dp(c,1));return v;}
    public static LinearLayout.LayoutParams lp(Context c,int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(c,w),h<0?h:dp(c,h));}
    public static LinearLayout.LayoutParams margin(Context c,int w,int h,int l,int t,int r,int b){LinearLayout.LayoutParams p=lp(c,w,h);p.setMargins(dp(c,l),dp(c,t),dp(c,r),dp(c,b));return p;}
    public static View space(Context c,int h){View v=new View(c);v.setLayoutParams(lp(c,1,h));return v;}
    public static TextView section(Context c,String s){return text(c,s,19,INK,true);}
    public static EditText field(Context c,String hint,String value,int inputType){EditText f=new EditText(c);f.setTextSize(16);f.setTextColor(INK);f.setHintTextColor(MUTED);f.setSingleLine(true);f.setInputType(inputType);f.setPadding(dp(c,14),dp(c,11),dp(c,14),dp(c,11));f.setBackground(stroke(c,WHITE,13,LINE));f.setHint(hint);f.setText(value);return f;}
    public static void fieldLabel(LinearLayout container,String s){container.addView(text(container.getContext(),s,13,MUTED,true),margin(container.getContext(),-1,-2,0,16,0,8));}
    public static void attach(LinearLayout parent,View v,int top){parent.addView(v,margin(parent.getContext(),-1,-2,0,top,0,0));}
    public static void alert(Activity a,String title,String body){new android.app.AlertDialog.Builder(a).setTitle(title).setMessage(body).setPositiveButton("OK",null).show();}
    public static String categories(){return "授業,課題,就活,アルバイト,私用";}
    public static int categoryColor(String category){if("就活".equals(category))return Color.rgb(62,114,212);if("アルバイト".equals(category))return GREEN;if("授業".equals(category))return Color.rgb(125,81,177);if("私用".equals(category))return ORANGE;return PURPLE;}
}
