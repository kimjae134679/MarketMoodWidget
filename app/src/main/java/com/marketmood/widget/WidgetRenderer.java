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
 * Design rules:
 *  - Keep 1x1 and 2x1 compositions stable when a launcher resizes them.
 *  - Keep all important text inside a generous rounded-corner safe area.
 *  - Change widgets show only: market, current value, absolute move and percent move.
 *    Date / "전일" / previous close are intentionally not shown.
 *  - Circuit-breaker / sidecar text is hidden during normal operation and appears only when alerted.
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
        float radius = clamp(min * 0.22f, 18f, 30f);

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

    /** 1x1 mood. The content block is capped so resizing never makes typography explode. */
    private static void drawMoodSmall(Canvas c, MarketSnapshot s, float w, float h,
                                      int fg, int green, int orange) {
        float bw = Math.min(w, 108f);
        float bh = Math.min(h, 118f);
        float x0 = (w - bw) / 2f;
        float y0 = (h - bh) / 2f;
        float safeX = clamp(bw * .18f, 14f, 18f);
        float safeY = clamp(bh * .12f, 12f, 16f);
        float usableW = bw - safeX * 2f;

        float title = fittedSize(s.name, 19f, 13f, usableW, true);
        drawText(c, s.name, x0 + safeX, y0 + safeY + title, title, fg, true, Paint.Align.LEFT);

        float sentimentY = y0 + bh * (hasAlert(s) ? .45f : .47f);
        float sentiment = fittedSize(s.sentiment, 22f, 15f, usableW, true);
        drawText(c, s.sentiment, w / 2f, sentimentY, sentiment, green, true, Paint.Align.CENTER);

        String scoreText = String.format(Locale.KOREA, "%.1f", s.score);
        float score = fittedSize(scoreText, 29f, 20f, usableW, true);
        drawText(c, scoreText, w / 2f, y0 + bh * (hasAlert(s) ? .68f : .73f),
                score, fg, true, Paint.Align.CENTER);

        if (hasAlert(s)) {
            float alert = fittedSize(s.alert, 11f, 8f, usableW, true);
            drawText(c, s.alert, w / 2f, y0 + bh * .87f,
                    alert, orange, true, Paint.Align.CENTER);
        }
    }

    /** 2x1 mood. Same layout even when resized taller; only the block is vertically centered. */
    private static void drawMoodWide(Canvas c, MarketSnapshot s, float w, float h,
                                     boolean dark, int fg, int green, int orange) {
        float bw = Math.min(w, 280f);
        float bh = Math.min(h, 98f);
        float x0 = (w - bw) / 2f;
        float y0 = (h - bh) / 2f;
        float safeX = clamp(bh * .18f, 15f, 19f);
        float safeY = clamp(bh * .12f, 11f, 15f);
        float usableW = bw - safeX * 2f;

        String scoreText = String.format(Locale.KOREA, "%.1f", s.score);
        float score = fittedSize(scoreText, 28f, 20f, usableW * .30f, true);
        float title = fittedSize(s.name, 21f, 15f, usableW * .38f, true);
        float sentiment = fittedSize(s.sentiment, 20f, 14f, usableW * .26f, true);

        float topBase = y0 + safeY + Math.max(title, score);
        drawText(c, s.name, x0 + safeX, topBase, title, fg, true, Paint.Align.LEFT);
        float nameW = measure(s.name, title, true);
        drawText(c, s.sentiment, x0 + safeX + nameW + 7f, topBase,
                sentiment, green, true, Paint.Align.LEFT);
        drawText(c, scoreText, x0 + bw - safeX, topBase,
                score, fg, true, Paint.Align.RIGHT);

        float barH = clamp(bh * .11f, 9f, 13f);
        float barTop = y0 + bh * (hasAlert(s) ? .59f : .65f);
        drawFearBar(c, x0 + safeX, barTop, x0 + bw - safeX, barTop + barH, s.score, dark);

        if (hasAlert(s)) {
            float alert = fittedSize(s.alert, 13f, 9f, usableW, true);
            drawText(c, s.alert, x0 + safeX, y0 + bh * .88f,
                    alert, orange, true, Paint.Align.LEFT);
        }
    }

    /**
     * 1x1 change: intentionally minimal.
     * market / current value / absolute move + percent move.
     */
    private static void drawChangeSmall(Canvas c, MarketSnapshot s, float w, float h,
                                        int fg, int muted, int red, int blue, int orange) {
        float bw = Math.min(w, 108f);
        float bh = Math.min(h, 118f);
        float x0 = (w - bw) / 2f;
        float y0 = (h - bh) / 2f;
        float safeX = clamp(bw * .18f, 14f, 18f);
        float safeY = clamp(bh * .12f, 12f, 16f);
        float usableW = bw - safeX * 2f;
        int dirColor = s.changePct > 0 ? red : s.changePct < 0 ? blue : muted;

        float title = fittedSize(s.name, 19f, 13f, usableW, true);
        drawText(c, s.name, x0 + safeX, y0 + safeY + title,
                title, fg, true, Paint.Align.LEFT);

        String valueText = formatValue(s.value);
        float value = fittedSize(valueText, 25f, 13f, usableW, true);
        drawText(c, valueText, w / 2f, y0 + bh * .52f,
                value, fg, true, Paint.Align.CENTER);

        String moveText = signedChange(s.change) + "  " + signedPercent(s.changePct);
        float move = fittedSize(moveText, 14.5f, 9f, usableW, true);
        drawText(c, moveText, w / 2f, y0 + bh * (hasAlert(s) ? .70f : .75f),
                move, dirColor, true, Paint.Align.CENTER);

        if (hasAlert(s)) {
            float alert = fittedSize(s.alert, 10.5f, 8f, usableW, true);
            drawText(c, s.alert, w / 2f, y0 + bh * .87f,
                    alert, orange, true, Paint.Align.CENTER);
        }
    }

    /** 2x1 change: same information hierarchy as 1x1, with larger text and more breathing room. */
    private static void drawChangeWide(Canvas c, MarketSnapshot s, float w, float h,
                                       int fg, int muted, int red, int blue, int orange) {
        float bw = Math.min(w, 280f);
        float bh = Math.min(h, 98f);
        float x0 = (w - bw) / 2f;
        float y0 = (h - bh) / 2f;
        float safeX = clamp(bh * .18f, 15f, 19f);
        float safeY = clamp(bh * .12f, 11f, 15f);
        float usableW = bw - safeX * 2f;
        int dirColor = s.changePct > 0 ? red : s.changePct < 0 ? blue : muted;

        float title = fittedSize(s.name, 21f, 15f, usableW, true);
        drawText(c, s.name, x0 + safeX, y0 + safeY + title,
                title, fg, true, Paint.Align.LEFT);

        String valueText = formatValue(s.value);
        float value = fittedSize(valueText, 31f, 20f, usableW, true);
        drawText(c, valueText, w / 2f, y0 + bh * .57f,
                value, fg, true, Paint.Align.CENTER);

        String moveText = signedChange(s.change) + "  " + signedPercent(s.changePct);
        float move = fittedSize(moveText, 18f, 12f, usableW, true);
        drawText(c, moveText, w / 2f, y0 + bh * (hasAlert(s) ? .75f : .82f),
                move, dirColor, true, Paint.Align.CENTER);

        if (hasAlert(s)) {
            float alert = fittedSize(s.alert, 12f, 9f, usableW, true);
            drawText(c, s.alert, w / 2f, y0 + bh * .90f,
                    alert, orange, true, Paint.Align.CENTER);
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

    private static void drawText(Canvas c,String text,float x,float y,float size,int color,boolean bold,Paint.Align align){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTextAlign(align);
        p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));
        c.drawText(text==null?"":text,x,y,p);
    }

    private static float fittedSize(String text, float preferred, float minimum,
                                    float maxWidth, boolean bold) {
        if (text == null || text.isEmpty() || maxWidth <= 1f) return minimum;
        float measured = measure(text, preferred, bold);
        if (measured <= maxWidth) return preferred;
        return Math.max(minimum, preferred * (maxWidth / measured));
    }

    private static float measure(String text,float size,boolean bold){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));
        return p.measureText(text==null?"":text);
    }

    private static float clamp(float x,float a,float b){ return Math.max(a,Math.min(b,x)); }
}
