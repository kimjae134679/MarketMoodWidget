package com.marketmood.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.List;
import java.util.Locale;

public class WidgetRenderer {
    private static int C(String s){ return Color.parseColor(s); }

    public static Bitmap render(MarketSnapshot s, int width, int height, boolean dark, boolean graph) {
        width=Math.max(width,260); height=Math.max(height,160);
        Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(b);
        float scale=Math.min(width/600f,height/320f);
        float pad=Math.max(18,28*scale);
        float radius=Math.max(24,40*scale);
        int bg=dark?C("#171A21"):Color.WHITE;
        int fg=dark?Color.WHITE:C("#111217");
        int green=C("#248B38");
        int blue=C("#2869C7");
        int red=C("#D64646");
        int orange=C("#FF7A1A");

        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bg); c.drawRoundRect(new RectF(0,0,width,height),radius,radius,p);

        boolean vertical = height > width*1.05f;
        if(vertical){
            float title=clamp(width*0.12f,22,48);
            drawText(c,s.name,pad,pad+title,title,fg,true,Paint.Align.LEFT);
            float label=clamp(width*0.11f,22,44);
            float score=clamp(width*0.16f,30,64);
            drawText(c,s.sentiment,width/2f,height*0.46f,label,green,true,Paint.Align.CENTER);
            drawText(c,String.format(Locale.KOREA,"%.1f",s.score),width/2f,height*0.70f,score,fg,true,Paint.Align.CENTER);
            float pc=clamp(width*0.075f,16,28);
            String pct=(s.changePct>=0?"▲ +":"▼ ")+String.format(Locale.KOREA,"%.2f%%",Math.abs(s.changePct));
            drawText(c,pct,width/2f,height*0.84f,pc,s.changePct>=0?red:blue,true,Paint.Align.CENTER);
            if(s.alert!=null&&!s.alert.isEmpty()) drawText(c,s.alert,width/2f,height-14*scale,clamp(width*0.06f,14,23),orange,true,Paint.Align.CENTER);
            return b;
        }

        float title=clamp(height*0.16f,24,54);
        drawText(c,s.name,pad,pad+title,title,fg,true,Paint.Align.LEFT);
        float sentimentX=pad + measure(s.name,title,true) + 16*scale;
        drawText(c,s.sentiment,sentimentX,pad+title,title*0.9f,green,true,Paint.Align.LEFT);
        drawText(c,String.format(Locale.KOREA,"%.1f",s.score),width-pad,pad+title,title,fg,true,Paint.Align.RIGHT);

        String pct=(s.changePct>=0?"▲ +":"▼ ")+String.format(Locale.KOREA,"%.2f%%",Math.abs(s.changePct));
        float tiny=clamp(height*0.095f,16,30);
        drawText(c,(s.date==null?"":s.date)+"   "+pct,width-pad,pad+title+tiny*1.4f,tiny,s.changePct>=0?red:blue,true,Paint.Align.RIGHT);

        float top=height*0.56f;
        float bottom=height-(s.alert!=null&&!s.alert.isEmpty()?height*0.19f:pad);
        if(graph && s.weekly.size()>1) drawSparkline(c,s.weekly,pad,top,width-pad,bottom,dark?C("#68C96A"):C("#2E9842"));
        else drawFearBar(c,pad,top,width-pad,top+Math.max(18,28*scale),s.score,dark);

        if(s.alert!=null&&!s.alert.isEmpty()) drawText(c,s.alert,pad,height-pad*0.55f,clamp(height*0.10f,17,30),orange,true,Paint.Align.LEFT);
        return b;
    }

    private static void drawFearBar(Canvas c,float l,float t,float r,float b,double score,boolean dark){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        float w=(r-l)/5f;
        int[] cols={C("#D92D2D"),C("#FF8D0A"),C("#FFC43A"),C("#85BF45"),C("#2C8C3A")};
        for(int i=0;i<5;i++){
            p.setColor(cols[i]);
            RectF rr=new RectF(l+i*w,t,l+(i+1)*w,b);
            if(i==0||i==4) c.drawRoundRect(rr,(b-t)/2f,(b-t)/2f,p); else c.drawRect(rr,p);
        }
        float x=l+(float)(Math.max(0,Math.min(100,score))/100.0)*(r-l);
        p.setColor(dark?Color.WHITE:C("#171717")); p.setStrokeWidth(Math.max(4,(b-t)*0.18f));
        c.drawLine(x,t-8,x,b+8,p);
    }

    private static void drawSparkline(Canvas c,List<MarketSnapshot.WeekPoint> pts,float l,float t,float r,float b,int color){
        double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;
        for(MarketSnapshot.WeekPoint x:pts){min=Math.min(min,x.value);max=Math.max(max,x.value);} if(max<=min)max=min+1;
        Path path=new Path();
        for(int i=0;i<pts.size();i++){
            float x=l+(r-l)*i/(pts.size()-1f);
            float y=b-(float)((pts.get(i).value-min)/(max-min))*(b-t);
            if(i==0)path.moveTo(x,y); else path.lineTo(x,y);
        }
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(4,(b-t)*0.035f)); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setColor(color); c.drawPath(path,p);
        MarketSnapshot.WeekPoint last=pts.get(pts.size()-1); float x=r; float y=b-(float)((last.value-min)/(max-min))*(b-t);
        p.setStyle(Paint.Style.FILL); c.drawCircle(x,y,Math.max(5,(b-t)*0.05f),p);
    }

    private static void drawText(Canvas c,String text,float x,float y,float size,int color,boolean bold,Paint.Align align){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color); p.setTextSize(size); p.setTextAlign(align); p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL)); c.drawText(text==null?"":text,x,y,p);
    }
    private static float measure(String text,float size,boolean bold){ Paint p=new Paint();p.setTextSize(size);p.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));return p.measureText(text); }
    private static float clamp(float x,float a,float b){return Math.max(a,Math.min(b,x));}
}
