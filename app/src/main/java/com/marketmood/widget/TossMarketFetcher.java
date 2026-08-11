package com.marketmood.widget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * Toss quote cache/helpers.
 *
 * Raw HTTP responses from www.tossinvest.com do not contain the live ticker: the
 * values are inserted by JavaScript after the WTS page loads. v1.9 therefore no
 * longer tries to guess numbers from raw HTML. TossWebViewFetcher reads the
 * rendered Toss ticker and this class stores only those verified Toss values.
 */
public final class TossMarketFetcher {
    // New namespace intentionally drops the bad v1.7/v1.8 cache.
    private static final String PREF = "toss_market_cache_v3";
    private static final String KEY_QUOTES = "quotes";

    private TossMarketFetcher() {}

    /** Saves only quotes that were read from the rendered Toss Securities WTS. */
    public static void saveFresh(Context context, JSONObject fresh) {
        if (context == null || fresh == null || fresh.length() == 0) return;
        try {
            JSONObject merged = loadCache(context);
            for (String key : new String[]{"kospi", "kosdaq", "nasdaq"}) {
                JSONObject q = fresh.optJSONObject(key);
                if (isValid(q)) merged.put(key, q);
            }
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putString(KEY_QUOTES, merged.toString()).apply();
        } catch (Exception ignored) {}
    }

    /** Fresh rendered values win; missing markets are filled only from old Toss values. */
    public static JSONObject mergeWithCache(Context context, JSONObject fresh) {
        JSONObject merged = loadCache(context);
        if (fresh == null) return merged;
        try {
            for (String key : new String[]{"kospi", "kosdaq", "nasdaq"}) {
                JSONObject q = fresh.optJSONObject(key);
                if (isValid(q)) merged.put(key, q);
            }
        } catch (Exception ignored) {}
        return merged;
    }

    private static JSONObject loadCache(Context context) {
        if (context == null) return new JSONObject();
        SharedPreferences p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = p.getString(KEY_QUOTES, null);
        if (raw == null || raw.isEmpty()) return new JSONObject();
        try { return new JSONObject(raw); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private static boolean isValid(JSONObject quote) {
        if (quote == null) return false;
        double value = quote.optDouble("value", Double.NaN);
        double pct = quote.optDouble("changePct", Double.NaN);
        return !Double.isNaN(value) && value > 0 && !Double.isNaN(pct)
                && Math.abs(pct) <= 30.0
                && "토스증권".equals(quote.optString("source", ""));
    }

    public static boolean apply(MarketSnapshot s, JSONObject quote) {
        if (s == null || !isValid(quote)) return false;
        double value = quote.optDouble("value", Double.NaN);
        double pct = quote.optDouble("changePct", Double.NaN);

        s.value = value;
        s.change = quote.optDouble("change", s.change);
        s.changePct = pct;
        s.fallback = false;

        if (!s.weekly.isEmpty()) {
            int i = s.weekly.size() - 1;
            MarketSnapshot.WeekPoint old = s.weekly.get(i);
            s.weekly.set(i, new MarketSnapshot.WeekPoint(old.date, value));
        }
        return true;
    }
}
