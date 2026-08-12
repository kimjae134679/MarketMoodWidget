package com.marketmood.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * Home-screen widget renderer.
 *
 * Reference-driven rules:
 *  - 1x1 mood = market / sentiment / score, vertically balanced and centered.
 *  - 2x1 mood = market + sentiment on the left, score on the right, gauge below.
 *  - change widgets = market / current index / percent only. No date, previous close or point move.
 *  - 1x1 change removes decimals from the current index so long values never spill outside.
 *  - all content stays well inside the rounded-corner safe area.
 *  - sidecar / circuit-breaker text appears only when an actual alert exists.
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

    /** 1x1 mood: three visually centered rows with equal center-to-center spacing. */
    private static void drawMoodSmall(Canvas c, MarketSnapshot s, float w, float h,
                                      int fg, int green, int orange) {
        float bw = Math.min(w, 108f);
        float bh = Math.min(h, 118f);
        float x0 = (w - bw) / 2f;
        float y0 = (h - bh) / 2f;
        float safeX = clamp(bw * .18f, 14f, 18f);
        float safeY = clamp(bh * .15f, 14f, 18f);
        float usableW = bw - safeX * 2f;

        String scoreText = String.format(Locale.KOREA, "%.1f", s.score);
        float title = fittedSize(s.name, 18f, 12f, usableW, true);
        float sentiment = fittedSize(s.sentiment, 20f, 13f, usableW, true);
        float score = fittedSize(scoreText, 27f, 18f, usableW, true);

        if (!hasAlert(s)) {
            float top = y0 + safeY + title * .45f;
            float bottom = y0 + bh - safeY - score * .38f;
            float mid = (top + bottom) / 2f;
            drawCenteredText(c, s.name, w/2f, top, title, fg, true);
            drawCenteredText(c, s.sentiment, w/2f, mid, sentiment, green, true);
            drawCenteredText(c, scoreText, w/2f, bottom, score, fg, true);
        } else {
            float top = y0 + safeY + title * .35f;
            float bottom = y0 + bh - safeY - 4f;
            float step = (bottom - top) / 3f;
            drawCenteredText(c, s.name, w/2f, top, title, fg, true);
            drawCenteredText(c, s.sentiment, w/2f, top + step, sentiment, green, true);
            drawCenteredText(c, scoreText, w/2f, top + step*2f, score, fg, true);
            float alert = fittedSize(s.alert, 10f, 7.5f, usableW, true);
            drawCenteredText(c, s.alert, w/2f, bottom, alert, orange, true);
        }
    }

    /** 2x1 mood: follows the supplied reference closely and guarantees no text collision. */
    private static void drawMoodWide(Canvas c, MarketSnapshot s, float w, float h,
                                     boolean dark, int fg, int green, int orange) {
        float bw = Math.min(w, 280f);
        float bh = Math.min(h, 98f);
        float x0 = (w - bw) / 2f;
        float y0 = (h - bh) / 2f;
        float safeX = clamp(bh * .19f, 16f, 20f);
        float safeY = clamp(bh * .14f, 12f, 16f);
        float usableW = bw - safeX * 2f;

        String scoreText = String.format(Locale.KOREA, "%.1f", s.score);
        float score = fittedSize(scoreText, 26f, 15f, usableW * .30f, true);
        float scoreW = measure(scoreText, score, true);
        float gap = clamp(usableW * .055f, 5f, 10f);
        float leftMax = Math.max(30f, usableW - scoreW - gap);

        float titlePref = clamp(bh * .20f, 15f, 20f);
        float sentPref = clamp(bh * .19f, 14f, 19f);
        float groupPrefW = measure(s.name, titlePref, true) + gap + measure(s.sentiment, sentPref, true);
        float scale = groupPrefW > leftMax ? leftMax / groupPrefW : 1f;
        float title = Math.max(9f, titlePref * scale);
        float sentiment = Math.max(9f, sentPref * scale);

        float topCenter = y0 + bh * (hasAlert(s) ? .27f : .31f);
        float left = x0 + safeX;
        drawTextCenteredY(c, s.name, left, topCenter, title, fg, true, Paint.Align.LEFT);
        float nameW = measure(s.name, title, true);
        drawTextCenteredY(c, s.sentiment, left + nameW + gap, topCenter,
                sentiment, green, true, Paint.Align.LEFT);
        drawTextCenteredY(c, scoreText, x0 + bw - safeX, topCenter,
                score, fg, true, Paint.Align.RIGHT);

        float barH = clamp(bh * .105f, 8f, 12f);
        float barCenter = y0 + bh * (hasAlert(s) ? .57f : .70f);
        drawFearBar(c, x0 + safeX, barCenter - barH/2f,
                x0 + bw - safeX, barCenter + barH/2f, s.score, dark);

        if (hasAlert(s)) {
            float alert = fittedSize(s.alert, 12f, 8.5f, usableW, true);
            drawTextCenteredY(c, s.alert, x0 + safeX, y0 + bh*.83f,
                    alert, orange, true, Paint.Align.LEFT);
        }
    }

    /**
     * 1x1 change: exactly three rows. The row centers are equally spaced.
     * Long index decimals are intentionally discarded in this compact size.
     */
    private static void drawChangeSmall(Canvas c, MarketSnapshot s, float w, float h,
                                        int fg, int muted, int red, int blue, int orange) {
        float bw = Math.min(w, 108f);
        float bh = Math.min(h, 118f);
        float x0 = (w - bw) / 2f;
        float y0 = (h - bh) / 2f;
        float safeX = clamp(bw * .19f, 15f, 19f);
        float safeY = clamp(bh * .16f, 15f, 19f);
        float usableW = bw - safeX * 2f;
        int dirColor = s.changePct > 0 ? red : s.changePct < 0 ? blue : muted;

        String valueText = formatWholeValue(s.value);
        String pctText = signedPercent(s.changePct);
        float title = fittedSize(s.name, 18f, 12f, usableW, true);
        float value = fittedSize(valueText, 23f, 14f, usableW, true);
        float pct = fittedSize(pctText, 14f, 10f, usableW, true);

        if (!hasAlert(s)) {
            float top = y0 + safeY + title*.35f;
            float bottom = y0 + bh - safeY - pct*.25f;
            float mid = (top + bottom) / 2f;
            drawCenteredText(c, s.name, w/2f, top, title, fg, true);
            drawCenteredText(c, valueText, w/2f, mid, value, fg, true);
            drawCenteredText(c, pctText, w/2f, bottom, pct, dirColor, true);
        } else {
            float top = y0 + safeY + title*.25f;
            float bottom = y0 + bh - safeY - 2f;
            float step = (bottom - top) / 3f;
            drawCenteredText(c, s.name, w/2f, top, title, fg, true);
            drawCenteredText(c, valueText, w/2f, top+step, value, fg, true);
            drawCenteredText(c, pctText, w/2f, top+step*2f, pct, dirColor, true);
            float alert = fittedSize(s.alert, 9.5f, 7f, usableW, true);
            drawCenteredText(c, s.alert, w/2f, bottom, alert, orange, true);
        }
    }

    /** 2x1 change: same three-row rhythm; decimals are retained because this size has room. */
    private static void drawChangeWide(Canvas c, MarketSnapshot s, float w, float h,
                                       int fg, int muted, int red, int blue, int orange) {
        float bw = Math.min(w, 280f);
        float bh = Math.min(h, 98f);
        float x0 = (w - bw) / 2f;
        float y0 = (h - bh) / 2f;
        float safeX = clamp(bh * .19f, 16f, 20f);
        float safeY = clamp(bh * .15f, 13f, 17f);
        float usableW = bw - safeX * 2f;
        int dirColor = s.changePct > 0 ? red : s.changePct < 0 ? blue : muted;

        String valueText = formatValue(s.value);
        String pctText = signedPercent(s.changePct);
        float title = fittedSize(s.name, 19f, 13f, usableW, true);
        float value = fittedSize(valueText, 27f, 18f, usableW, true);
        float pct = fittedSize(pctText, 15f, 10.5f, usableW, true);

        if (!hasAlert(s)) {
            float top = y0 + safeY + title*.25f;
            float bottom = y0 + bh - safeY - pct*.15f;
            float mid = (top + bottom) / 2f;
            drawCenteredText(c, s.name, w/2f, top, title, fg, true);
            drawCenteredText(c, valueText, w/2f, mid, value, fg, true);
            drawCenteredText(c, pctText, w/2f, bottom, pct, dirColor, true);
        } else {
            float top = y0 + safeY;
            float bottom = y0 + bh - safeY;
            float step = (bottom - top) / 3f;
            drawCenteredText(c, s.name, w/2f, top, title, fg, true);
            drawCenteredText(c, valueText, w/2f, top+step, value, fg, true);
            drawCenteredText(c, pctText, w/2f, top+step*2f, pct, dirColor, true);
            float alert = fittedSize(s.alert, 11f, 8f, usableW, true);
            drawCenteredText(c, s.alert, w/2f, bottom, alert, orange, true);
        }
    }

    private static void drawFearBar(Canvas c,float l,float t,float r,float b,double score,boolean dark){
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    float segment=(r-l)/5f;
    float radius=(b-t)/2f;
    int[] cols={C("#D92D2D"),C("#FF8D0A"),C("#FFC43A"),C("#85BF45"),C("#2C8C3A")};

    int save=c.save();
    android.graphics.Path clip=new android.graphics.Path();
    clip.addRoundRect(new RectF(l,t,r,b),radius,radius,android.graphics.Path.Direction.CW);
    c.clipPath(clip);
    for(int i=0;i<5;i++){
        p.setColor(cols[i]);
        float left=l+i*segment;
        float right=(i==4)?r:l+(i+1)*segment;
        c.drawRect(left,t,right,b,p);
    }
    c.restoreToCount(save);

    float normalized=(float)(Math.max(0,Math.min(100,score))/100.0);
    float x=l+normalized*(r-l);
    p.setColor(dark?Color.WHITE:C("#171717"));
    p.setStrokeCap(Paint.Cap.SQUARE);
    p.setStrokeWidth(Math.max(1.8f,(b-t)*.13f));
    c.drawLine(x,t-(b-t)*.58f,x,b+(b-t)*.58f,p);
}
    private static boolean hasAlert(MarketSnapshot s){
        return s != null && s.alert != null && !s.alert.trim().isEmpty();
    }

    private static String formatValue(double v){
        return String.format(Locale.KOREA, Math.abs(v) >= 1000 ? "%,.2f" : "%.2f", v);
    }

    private static String formatWholeValue(double v){
        return String.format(Locale.KOREA, "%,.0f", v);
    }

    private static String signedPercent(double v){
        if(v>0) return "+"+String.format(Locale.KOREA,"%.2f%%",v);
        if(v<0) return String.format(Locale.KOREA,"%.2f%%",v);
        return "0.00%";
    }

    private static void drawCenteredText(Canvas c,String text,float cx,float cy,float size,
                                         int color,boolean bold){
        drawTextCenteredY(c, text, cx, cy, size, color, bold, Paint.Align.CENTER);
    }

    private static void drawTextCenteredY(Canvas c,String text,float x,float cy,float size,
                                          int color,boolean bold,Paint.Align align){
        Paint p = makePaint(size, color, bold, align);
        Paint.FontMetrics fm = p.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        c.drawText(text==null?"":text, x, baseline, p);
    }

    private static Paint makePaint(float size,int color,boolean bold,Paint.Align align){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTextAlign(align);
        p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));
        return p;
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
