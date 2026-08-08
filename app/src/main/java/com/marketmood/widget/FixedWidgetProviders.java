package com.marketmood.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.RemoteViews;

/**
 * Fixed widget variants exposed directly in Samsung/Android widget picker.
 * No configuration screen is used: each picker item already has a fixed market/content/size.
 */
public final class FixedWidgetProviders {
    private FixedWidgetProviders() {}

    public static abstract class Base extends AppWidgetProvider {
        protected abstract String symbol();
        protected abstract String marketName();
        protected abstract boolean changeMode();

        @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
            if (ids != null) for (int id : ids) updateOne(context, manager, id);
            MarketUpdater.refreshAsync(context, null);
        }

        @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
            updateOne(context, manager, id);
        }

        @Override public void onReceive(Context context, Intent intent) {
            super.onReceive(context, intent);
            if ("com.marketmood.widget.REFRESH".equals(intent.getAction())) {
                MarketUpdater.refreshAsync(context, null);
            }
        }

        public void updateOne(Context context, AppWidgetManager manager, int id) {
            MarketSnapshot snapshot = MarketRepository.load(context, symbol(), marketName());
            Bundle options = manager.getAppWidgetOptions(id);
            int dpW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 260);
            int dpH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 120);
            float density = context.getResources().getDisplayMetrics().density;
            int width = Math.max(160, Math.round(dpW * density));
            int height = Math.max(120, Math.round(dpH * density));
            boolean dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;

            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_market);
            rv.setImageViewBitmap(R.id.widget_image,
                    WidgetRenderer.render(snapshot, width, height, dark, changeMode()));

            Intent open = new Intent(context, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(context, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.widget_image, pi);
            manager.updateAppWidget(id, rv);
        }
    }

    public static class KospiMoodSmall extends Base {
        protected String symbol(){ return "^KS11"; }
        protected String marketName(){ return "코스피"; }
        protected boolean changeMode(){ return false; }
    }
    public static class KospiMoodWide extends KospiMoodSmall {}
    public static class NasdaqMoodSmall extends Base {
        protected String symbol(){ return "^IXIC"; }
        protected String marketName(){ return "나스닥"; }
        protected boolean changeMode(){ return false; }
    }
    public static class NasdaqMoodWide extends NasdaqMoodSmall {}
    public static class KospiChangeSmall extends Base {
        protected String symbol(){ return "^KS11"; }
        protected String marketName(){ return "코스피"; }
        protected boolean changeMode(){ return true; }
    }
    public static class KospiChangeWide extends KospiChangeSmall {}
    public static class NasdaqChangeSmall extends Base {
        protected String symbol(){ return "^IXIC"; }
        protected String marketName(){ return "나스닥"; }
        protected boolean changeMode(){ return true; }
    }
    public static class NasdaqChangeWide extends NasdaqChangeSmall {}

    @SuppressWarnings("unchecked")
    private static final Class<? extends AppWidgetProvider>[] ALL = new Class[]{
            KospiMoodSmall.class, KospiMoodWide.class,
            NasdaqMoodSmall.class, NasdaqMoodWide.class,
            KospiChangeSmall.class, KospiChangeWide.class,
            NasdaqChangeSmall.class, NasdaqChangeWide.class
    };

    public static void refreshAllWidgets(Context context, AppWidgetManager manager) {
        for (Class<? extends AppWidgetProvider> cls : ALL) {
            try {
                int[] ids = manager.getAppWidgetIds(new ComponentName(context, cls));
                Base provider = (Base) cls.getDeclaredConstructor().newInstance();
                if (ids != null) for (int id : ids) provider.updateOne(context, manager, id);
            } catch (Exception ignored) {}
        }
    }
}
