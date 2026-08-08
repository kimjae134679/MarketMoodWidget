package com.marketmood.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RemoteViews;

public class MarketWidgetProvider extends AppWidgetProvider {
    static final String CONFIG="widget_config";

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids){
        updateWidgets(context,manager,ids);
        MarketUpdater.refreshAsync(context,null);
    }

    @Override public void onAppWidgetOptionsChanged(Context context,AppWidgetManager manager,int id,Bundle opts){ updateOne(context,manager,id); }

    @Override public void onReceive(Context context, Intent intent){
        super.onReceive(context,intent);
        if("com.marketmood.widget.REFRESH".equals(intent.getAction())) MarketUpdater.refreshAsync(context,null);
    }

    public static void updateWidgets(Context context,AppWidgetManager manager,int[] ids){ if(ids==null)return; for(int id:ids) updateOne(context,manager,id); }

    static void updateOne(Context context,AppWidgetManager manager,int id){
        SharedPreferences p=context.getSharedPreferences(CONFIG,Context.MODE_PRIVATE);
        String market=p.getString("market_"+id,"KOSPI");
        boolean dark=p.getBoolean("dark_"+id,true);
        boolean graph=p.getBoolean("graph_"+id,false);
        String symbol="NASDAQ".equals(market)?"^IXIC":"^KS11";
        String name="NASDAQ".equals(market)?"나스닥":"코스피";
        MarketSnapshot s=MarketRepository.load(context,symbol,name);

        Bundle o=manager.getAppWidgetOptions(id);
        int dpW=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,280);
        int dpH=o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,150);
        float den=context.getResources().getDisplayMetrics().density;
        int w=Math.max(280,(int)(dpW*den)); int h=Math.max(160,(int)(dpH*den));

        RemoteViews rv=new RemoteViews(context.getPackageName(),R.layout.widget_market);
        rv.setImageViewBitmap(R.id.widget_image,WidgetRenderer.render(s,w,h,dark,graph));
        Intent open=new Intent(context,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(context,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widget_image,pi);
        manager.updateAppWidget(id,rv);
    }

    @Override public void onDeleted(Context context,int[] ids){
        SharedPreferences.Editor e=context.getSharedPreferences(CONFIG,Context.MODE_PRIVATE).edit();
        for(int id:ids){e.remove("market_"+id).remove("dark_"+id).remove("graph_"+id);}e.apply();
    }
}
