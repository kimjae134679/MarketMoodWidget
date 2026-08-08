package com.marketmood.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.Locale;

/** Renders compact widgets close to the original reference: simple cards, little text, no settings UI. */
public class WidgetRenderer {
    private static int C(String s){ return Color.parseColor(s); }

    public static Bitmap render(MarketSnapshot s, int width, int height, boolean dark, boolean changeMode) {
        width = Math.max(width, 160);
        height = Math.max(height, 120);
        Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float min = Math.min(width, height);
        float pad = Math.max(12f, min * 0.075f);
        float radius = Math.max(20f, min * 0.16f);

        int bg = dark ? C("#171A21") : C("#FFFFFF");
        int fg = dark ? C("#F8F8FA") : C("#111217");
        int muted = dark ? C("#AEB1B8") : C("#8F9197");
        int green = C("#248B38");
        int red = C("#D94A4A");
        int blue = C("#2E6DB5");
        int orange = C("#F07A22");

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bg);
        c.drawRoundRect(new RectF(0,0,width,height), radius, radius, p);

        boolean wide = width >= height * 1.38f;
        if (changeMode) {
            if (wide) drawChangeWide(c, s, width, height, pad, fg, muted, red, blue, orange);
            else drawChangeSmall(c, s, width, height, pad, fg, muted, red, blue, orange);
        } else {
            if (wide) drawMoodWide(c, s, width, height, pad, dark, fg, green);
            else drawMoodSmall(c, s, width, height, pad, fg, green);
        }
        return b;
    }

    private static void drawMoodSmall(Canvas c, MarketSnapshot s, int w, int h, float pad, int fg, int green) {
        float title = clamp(w * .105f, 18, 34);
        drawText(c, s.name, pad, pad + title, title, fg, true, Paint.Align.LEFT);
        float sentiment = clamp(w * .115f, 20, 38);
        drawText(c, s.sentiment, w/2f, h*.48f, sentiment, green, true, Paint.Align.CENTER);
        float score = clamp(w * .15f, 27, 52);
        drawText(c, String.format(Locale.KOREA,"%.1f",s.score), w/2f, h*.75f, score, fg, true, Paint.Align.CENTER);
    }

    private static void drawMoodWide(Canvas c, MarketSnapshot s, int w, int h, float pad, boolean dark, int fg, int green) {
        float title = clamp(h * .19f, 19, 34);
        drawText(c, s.name, pad, pad + title, title, fg, true, Paint.Align.LEFT);
        float nameW = measure(s.name, title, true);
        drawText(c, s.sentiment, pad + nameW + title*.32f, pad + title, title*.93f, green, true, Paint.Align.LEFT);
        drawText(c, String.format(Locale.KOREA,"%.1f",s.score), w-pad, pad+title, title, fg, true, Paint.Align.RIGHT);
        float barTop = h*.64f;
        float barH = clamp(h*.09f, 12, 24);
        drawFearBar(c, pad, barTop, w-pad, barTop+barH, s.score, dark);
    }

    private static void drawChangeSmall(Canvas c, MarketSnapshot s, int w, int h, float pad,
                                        int fg, int muted, int red, int blue, int orange) {
        float title = clamp(w*.10f, 18, 32);
        drawText(c, s.name, pad, pad+title, title, fg, true, Paint.Align.LEFT);
        String pct = signedPercent(s.changePct);
        int dirColor = s.changePct > 0 ? red : s.changePct < 0 ? blue : muted;
        float pc = clamp(w*.145f, 26, 48);
        drawText(c, pct, w/2f, h*.50f, pc, dirColor, true, Paint.Align.CENTER);
        float label = clamp(w*.075f, 14, 24);
        drawText(c, direction(s.changePct), w/2f, h*.66f, label, dirColor, true, Paint.Align.CENTER);
        if (s.alert != null && !s.alert.isEmpty()) {
            drawText(c, s.alert, w/2f, h-pad*.65f, clamp(w*.052f, 11, 18), orange, true, Paint.Align.CENTER);
        } else {
            drawText(c, shortDate(s.date), w/2f, h-pad*.65f, clamp(w*.052f, 11, 17), muted, false, Paint.Align.CENTER);
        }
    }

    private static void drawChangeWide(Canvas c, MarketSnapshot s, int w, int h, float pad,
                                       int fg, int muted, int red, int blue, int orange) {
        float title = clamp(h*.18f, 18, 31);
        drawText(c, s.name, pad, pad+title, title, fg, true, Paint.Align.LEFT);
        drawText(c, shortDate(s.date), w-pad, pad+title*.92f, clamp(title*.58f, 12, 18), muted, false, Paint.Align.RIGHT);

        float value = clamp(h*.28f, 30, 50);
        drawText(c, formatValue(s.value), pad, h*.57f, value, fg, true, Paint.Align.LEFT);
        int dirColor = s.changePct > 0 ? red : s.changePct < 0 ? blue : muted;
        drawText(c, signedPercent(s.changePct), w-pad, h*.55f, clamp(h*.19f, 20, 34), dirColor, true, Paint.Align.RIGHT);
        drawText(c, direction(s.changePct), w-pad, h*.72f, clamp(h*.11f, 13, 20), dirColor, true, Paint.Align.RIGHT);

        if (s.alert != null && !s.alert.isEmpty()) {
            drawText(c, s.alert, pad, h-pad*.60f, clamp(h*.105f, 12, 19), orange, true, Paint.Align.LEFT);
        }
    }

    private static void drawFearBar(Canvas c,float l,float t,float r,float b,double score,boolean dark){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        float w=(r-l)/5f;
        int[] cols={C("#D92D2D"),C("#FF8D0A"),C("#FFC43A"),C("#85BF45"),C("#2C8C3A")};
        for(int i=0;i<5;i++){
            p.setColor(cols[i]);
            RectF rr=new RectF(l+i*w,t,l+(i+1)*w,b);
            if(i==0 || i==4) c.drawRoundRect(rr,(b-t)/2f,(b-t)/2f,p); else c.drawRect(rr,p);
        }
        float x=l+(float)(Math.max(0,Math.min(100,score))/100.0)*(r-l);
        p.setColor(dark?Color.WHITE:C("#171717"));
        p.setStrokeWidth(Math.max(3f,(b-t)*.16f));
        c.drawLine(x,t-(b-t)*.45f,x,b+(b-t)*.45f,p);
    }

    private static String formatValue(double v){
        return String.format(Locale.KOREA, v >= 1000 ? "%,.2f" : "%.2f", v);
    }
    private static String signedPercent(double v){
        if(v>0) return "+"+String.format(Locale.KOREA,"%.2f%%",v);
        if(v<0) return String.format(Locale.KOREA,"%.2f%%",v);
        return "0.00%";
    }
    private static String direction(double v){ return v>0?"상승":v<0?"하락":"보합"; }
    private static String shortDate(String d){
        if(d==null) return "";
        if(d.length()>=10) return d.substring(5).replace('-','.');
        return d;
    }
    private static void drawText(Canvas c,String text,float x,float y,float size,int color,boolean bold,Paint.Align align){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setTextSize(size);p.setTextAlign(align);
        p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));c.drawText(text==null?"":text,x,y,p);
    }
    private static float measure(String text,float size,boolean bold){Paint p=new Paint();p.setTextSize(size);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));return p.measureText(text);}
    private static float clamp(float x,float a,float b){return Math.max(a,Math.min(b,x));}
}
