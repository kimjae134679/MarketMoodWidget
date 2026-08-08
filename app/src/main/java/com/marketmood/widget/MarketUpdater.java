package com.marketmood.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MarketUpdater {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    public static void refreshAsync(Context context, Runnable done) {
        Context app = context.getApplicationContext();
        EXEC.execute(() -> {
            MarketSnapshot kospi;
            MarketSnapshot kosdaq;
            MarketSnapshot nasdaq;
            try { kospi = MarketRepository.fetch("^KS11", "코스피"); }
            catch (Exception e) { kospi = MarketRepository.load(app,"^KS11","코스피"); }
            try { kosdaq = MarketRepository.fetch("^KQ11", "코스닥"); }
            catch (Exception e) { kosdaq = MarketRepository.load(app,"^KQ11","코스닥"); }
            try { nasdaq = MarketRepository.fetch("^IXIC", "나스닥"); }
            catch (Exception e) { nasdaq = MarketRepository.load(app,"^IXIC","나스닥"); }

            kospi.alert = KrxAlertFetcher.fetchKospiAlert();
            MarketRepository.save(app, kospi);
            MarketRepository.save(app, kosdaq);
            MarketRepository.save(app, nasdaq);

            try {
                AppWidgetManager manager=AppWidgetManager.getInstance(app);
                FixedWidgetProviders.refreshAllWidgets(app, manager);
                int[] legacy=manager.getAppWidgetIds(new ComponentName(app,MarketWidgetProvider.class));
                MarketWidgetProvider.updateWidgets(app, manager, legacy);
            } catch (Exception ignored) {}
            if(done!=null) done.run();
        });
    }
}
