package com.marketmood.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * Draws the home-screen widgets as one bitmap.
 *
 * Important design rules:
 *  - draw in logical dp, then scale the Canvas by screen density. This prevents tiny text on xxhdpi/xxxhdpi phones.
 *  - the selected widget variant (1x1 or 2x1) owns the composition. Resizing never swaps to a completely different design.
 *  - normal circuit-breaker/sidecar state is intentionally hidden. Only an actual alert is drawn.
 */
public class WidgetRenderer {
    private static int C(String s){ return Color.parseColor(s); }

    public static Bitmap render(MarketSnapshot s, int widthPx, int heightPx, float density,
                                boolean dark, boolean changeMode, boolean wideVariant) {
        density = Math.max(1f, density);
        widthPx = Math.max(widthPx, Math.round(48f * density));
        heightPx = Math.max(heightPx, Math.round(48f * density));

        Bitmap b = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(density, density);

        float w = widthPx / density;
        float h = heightPx / density;
        float min = Math.min(w, h);
        float radius = clamp(min * 0.18f, 16f, 28f);

        int bg = dark ? C("#171A21") : C("#FFFFFF");
        int fg = dark ? C("#F8F8FA") : C("#111217");
        int muted = dark ? C("#AEB1B8") : C("#8F9197");
        int green = C("#278E3B");
        int red = C("#D84B4B");
        int blue = C("#2E6DB5");
        int orange = C("#F26A16");

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bg);
        c.drawRoundRect(new RectF(0, 0, w, h), radius, radius, p);

        if (changeMode) {
            if (wideVariant) drawChangeWide(c, s, w, h, fg, muted, red, blue, orange);
            else drawChangeSmall(c, s, w, h, fg, muted, red, blue, orange);
        } else {
            if (wideVariant) drawMoodWide(c, s, w, h, dark, fg, green, orange);
            else drawMoodSmall(c, s, w, h, fg, green, orange);
        }
        return b;
    }

    /** 1x1 mood: same reference hierarchy at every resized size. */
    private static void drawMoodSmall(Canvas c, MarketSnapshot s, float w, float h,
                                      int fg, int green, int orange) {
        float pad = clamp(Math.min(w, h) * .10f, 8f, 14f);
        float title = clamp(Math.min(w, h) * .16f, 14f, 22f);
        float sentiment = clamp(Math.min(w, h) * .18f, 16f, 25f);
        float score = clamp(Math.min(w, h) * .235f, 21f, 32f);

        drawText(c, s.name, pad, pad + title, title, fg, true, Paint.Align.LEFT);

        float centerY = h * (hasAlert(s) ? .47f : .50f);
        drawText(c, s.sentiment, w/2f, centerY, sentiment, green, true, Paint.Align.CENTER);
        drawText(c, String.format(Locale.KOREA,"%.1f",s.score), w/2f,
                centerY + score*1.18f, score, fg, true, Paint.Align.CENTER);

        if (hasAlert(s)) {
            drawText(c, s.alert, w/2f, h-pad*.72f,
                    clamp(Math.min(w,h)*.085f, 9f, 13f), orange, true, Paint.Align.CENTER);
        }
    }

    /** 2x1 mood: composition is fixed even if the launcher resizes it to 2x2 etc. */
    private static void drawMoodWide(Canvas c, MarketSnapshot s, float w, float h,
                                     boolean dark, int fg, int green, int orange) {
        // Keep a wide-card composition and center that block vertically instead of switching layouts.
        float blockH = Math.min(h, Math.max(66f, w * (hasAlert(s) ? .47f : .39f)));
        float y0 = (h - blockH) / 2f;
        float pad = clamp(Math.min(w, blockH) * .075f, 10f, 16f);
        float title = clamp(blockH * .22f, 16f, 24f);
        float score = clamp(blockH * .28f, 20f, 30f);

        float topBase = y0 + pad + Math.max(title, score) * .95f;
        drawText(c, s.name, pad, topBase, title, fg, true, Paint.Align.LEFT);
        float nameW = measure(s.name, title, true);
        drawText(c, s.sentiment, pad + nameW + title*.34f, topBase,
                title*.96f, green, true, Paint.Align.LEFT);
        drawText(c, String.format(Locale.KOREA,"%.1f",s.score), w-pad, topBase,
                score, fg, true, Paint.Align.RIGHT);

        float barH = clamp(blockH * .105f, 9f, 15f);
        float barTop = y0 + blockH * (hasAlert(s) ? .60f : .66f);
        drawFearBar(c, pad, barTop, w-pad, barTop+barH, s.score, dark);

        if (hasAlert(s)) {
            drawText(c, s.alert, pad, y0 + blockH - pad*.28f,
                    clamp(blockH*.115f, 10f, 15f), orange, true, Paint.Align.LEFT);
        }
    }

    /** 1x1 change: previous value -> current value + absolute/percent change, without tiny text. */
    private static void drawChangeSmall(Canvas c, MarketSnapshot s, float w, float h,
                                        int fg, int muted, int red, int blue, int orange) {
        float min = Math.min(w,h);
        float pad = clamp(min*.09f, 8f, 13f);
        float title = clamp(min*.15f, 14f, 21f);
        float tiny = clamp(min*.085f, 9f, 12f);
        float value = clamp(min*.205f, 20f, 29f);
        float change = clamp(min*.115f, 11f, 16f);
        int dirColor = s.changePct > 0 ? red : s.changePct < 0 ? blue : muted;

        drawText(c, s.name, pad, pad+title, title, fg, true, Paint.Align.LEFT);
        drawText(c, shortDate(s.date), w-pad, pad+title*.90f, tiny, muted, false, Paint.Align.RIGHT);

        double prev = previousValue(s);
        drawText(c, "전일 " + formatValue(prev), w/2f, h*.38f,
                tiny, muted, false, Paint.Align.CENTER);
        drawText(c, formatValue(s.value), w/2f, h*.59f,
                value, fg, true, Paint.Align.CENTER);
        drawText(c, signedChange(s.change) + " · " + signedPercent(s.changePct), w/2f, h*.74f,
                change, dirColor, true, Paint.Align.CENTER);
        drawText(c, direction(s.changePct), w/2f, h*.86f,
                clamp(min*.095f, 10f, 13f), dirColor, true, Paint.Align.CENTER);

        if (hasAlert(s)) {
            drawText(c, s.alert, w/2f, h-pad*.35f,
                    clamp(min*.078f, 8f, 11f), orange, true, Paint.Align.CENTER);
        }
    }

    /** 2x1 change: mirrors the original reference card, but also shows previous and absolute change. */
    private static void drawChangeWide(Canvas c, MarketSnapshot s, float w, float h,
                                       int fg, int muted, int red, int blue, int orange) {
        float blockH = Math.min(h, Math.max(70f, w * (hasAlert(s) ? .48f : .41f)));
        float y0 = (h-blockH)/2f;
        float pad = clamp(Math.min(w,blockH)*.075f, 10f, 16f);
        float title = clamp(blockH*.20f, 15f, 22f);
        float tiny = clamp(blockH*.105f, 9f, 13f);
        float value = clamp(blockH*.29f, 23f, 34f);
        float pct = clamp(blockH*.19f, 16f, 24f);
        int dirColor = s.changePct > 0 ? red : s.changePct < 0 ? blue : muted;

        float topBase = y0 + pad + title;
        drawText(c, s.name, pad, topBase, title, fg, true, Paint.Align.LEFT);
        drawText(c, shortDate(s.date), w-pad, y0 + pad + title*.84f,
                tiny, muted, false, Paint.Align.RIGHT);

        float midBase = y0 + blockH*.57f;
        drawText(c, formatValue(s.value), pad, midBase, value, fg, true, Paint.Align.LEFT);
        drawText(c, signedPercent(s.changePct), w-pad, y0 + blockH*.54f,
                pct, dirColor, true, Paint.Align.RIGHT);

        drawText(c, "전일 " + formatValue(previousValue(s)), pad,
                y0 + blockH*.76f, tiny, muted, false, Paint.Align.LEFT);
        drawText(c, signedChange(s.change) + "  " + direction(s.changePct), w-pad,
                y0 + blockH*.76f, clamp(blockH*.115f, 10f, 15f), dirColor, true, Paint.Align.RIGHT);

        if (hasAlert(s)) {
            drawText(c, s.alert, pad, y0 + blockH - pad*.18f,
                    clamp(blockH*.105f, 9f, 13f), orange, true, Paint.Align.LEFT);
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
        p.setStrokeWidth(Math.max(2.2f,(b-t)*.16f));
        c.drawLine(x,t-(b-t)*.55f,x,b+(b-t)*.55f,p);
    }

    private static boolean hasAlert(MarketSnapshot s){
        return s != null && s.alert != null && !s.alert.trim().isEmpty();
    }
    private static double previousValue(MarketSnapshot s){ return s.value - s.change; }
    private static String formatValue(double v){
        return String.format(Locale.KOREA, Math.abs(v) >= 1000 ? "%,.2f" : "%.2f", v);
    }
    private static String signedChange(double v){
        if(v>0) return "+"+formatValue(v);
        if(v<0) return formatValue(v);
        return "0.00";
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
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color); p.setTextSize(size); p.setTextAlign(align);
        p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));
        c.drawText(text==null?"":text,x,y,p);
    }
    private static float measure(String text,float size,boolean bold){
        Paint p=new Paint(); p.setTextSize(size); p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));
        return p.measureText(text==null?"":text);
    }
    private static float clamp(float x,float a,float b){ return Math.max(a,Math.min(b,x)); }
}
