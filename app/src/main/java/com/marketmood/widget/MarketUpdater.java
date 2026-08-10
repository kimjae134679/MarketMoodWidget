package com.marketmood.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import org.json.JSONObject;

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

            // Yahoo history is retained only for fear/greed calculation and weekly history.
            // Current displayed index + rate must come from Toss whenever Toss has ever succeeded.
            try { kospi = MarketRepository.fetch("^KS11", "코스피"); }
            catch (Exception e) { kospi = MarketRepository.load(app,"^KS11","코스피"); }
            try { kosdaq = MarketRepository.fetch("^KQ11", "코스닥"); }
            catch (Exception e) { kosdaq = MarketRepository.load(app,"^KQ11","코스닥"); }
            try { nasdaq = MarketRepository.fetch("^IXIC", "나스닥"); }
            catch (Exception e) { nasdaq = MarketRepository.load(app,"^IXIC","나스닥"); }

            JSONObject fresh = null;
            try { fresh = TossMarketFetcher.fetchQuotes(app); }
            catch (Exception ignored) {}

            // Missing fresh markets are filled only from the last successful Toss cache.
            // This prevents a temporary WTS parsing/network failure from silently replacing
            // Toss values with Yahoo values, which caused the wrong numbers in v1.7.
            JSONObject toss = TossMarketFetcher.mergeWithCache(app, fresh);
            TossMarketFetcher.apply(kospi, toss.optJSONObject("kospi"));
            TossMarketFetcher.apply(kosdaq, toss.optJSONObject("kosdaq"));
            TossMarketFetcher.apply(nasdaq, toss.optJSONObject("nasdaq"));

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
