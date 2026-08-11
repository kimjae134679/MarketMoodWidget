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

        // Historical closes are prepared off the UI thread because they are used
        // only for fear/greed history and the weekly chart.
        EXEC.execute(() -> {
            final MarketSnapshot kospi = fetchHistory(app, "^KS11", "코스피");
            final MarketSnapshot kosdaq = fetchHistory(app, "^KQ11", "코스닥");
            final MarketSnapshot nasdaq = fetchHistory(app, "^IXIC", "나스닥");

            // The actual number/rate displayed to the user is read from Toss'
            // JavaScript-rendered ticker. Raw HTML does not contain those values.
            TossWebViewFetcher.fetch(app, fresh -> EXEC.execute(() -> {
                if (fresh != null && fresh.length() > 0) {
                    TossMarketFetcher.saveFresh(app, fresh);
                }

                // If a market is missing for a moment, only a previous Toss value
                // may fill it. Never substitute Yahoo and present it as Toss.
                JSONObject toss = TossMarketFetcher.mergeWithCache(app, fresh);
                TossMarketFetcher.apply(kospi, toss.optJSONObject("kospi"));
                TossMarketFetcher.apply(kosdaq, toss.optJSONObject("kosdaq"));
                TossMarketFetcher.apply(nasdaq, toss.optJSONObject("nasdaq"));

                kospi.alert = KrxAlertFetcher.fetchKospiAlert();
                MarketRepository.save(app, kospi);
                MarketRepository.save(app, kosdaq);
                MarketRepository.save(app, nasdaq);

                try {
                    AppWidgetManager manager = AppWidgetManager.getInstance(app);
                    FixedWidgetProviders.refreshAllWidgets(app, manager);
                    int[] legacy = manager.getAppWidgetIds(new ComponentName(app, MarketWidgetProvider.class));
                    MarketWidgetProvider.updateWidgets(app, manager, legacy);
                } catch (Exception ignored) {}

                if (done != null) {
                    try { done.run(); } catch (Exception ignored) {}
                }
            }));
        });
    }

    private static MarketSnapshot fetchHistory(Context app, String symbol, String name) {
        try { return MarketRepository.fetch(symbol, name); }
        catch (Exception e) { return MarketRepository.load(app, symbol, name); }
    }
}
