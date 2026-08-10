package com.marketmood.widget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the public market ticker rendered by Toss Securities WTS.
 *
 * Important: Toss WTS keeps most market values inside hydration/script payloads.
 * The old implementation removed every <script> block before parsing, which meant
 * the Toss values were usually deleted and the app silently fell back to Yahoo.
 * This parser keeps script contents, normalizes escaped text and extracts the
 * actual Toss ticker values. The last successful Toss quote is cached so a
 * temporary WTS failure never replaces Toss values with a different provider.
 */
public final class TossMarketFetcher {
    private static final String PREF = "toss_market_cache_v2";
    private static final String KEY_QUOTES = "quotes";

    private static final String[] SOURCES = {
            "https://www.tossinvest.com/",
            "https://www.tossinvest.com/?contentType=tics-list",
            "https://www.tossinvest.com/stocks/US20210301016"
    };

    private static final Pattern STRICT = Pattern.compile(
            "([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([+-])\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*\\(\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%\\s*\\)",
            Pattern.DOTALL);

    // Toss discovery sometimes shows only '현재지수 +등락률' without the point change.
    private static final Pattern VALUE_AND_RATE = Pattern.compile(
            "([0-9][0-9,]{2,}(?:\\.[0-9]+)?).{0,420}?([+-]\\s*[0-9]+(?:\\.[0-9]+)?)\\s*%",
            Pattern.DOTALL);

    private TossMarketFetcher() {}

    public static JSONObject fetchQuotes(Context context) throws Exception {
        JSONObject out = new JSONObject();
        Exception last = null;
        for (String source : SOURCES) {
            try {
                parseInto(out, get(source));
                if (out.has("kospi") && out.has("kosdaq") && out.has("nasdaq")) break;
            } catch (Exception e) {
                last = e;
            }
        }

        if (out.length() == 0) {
            if (last != null) throw last;
            throw new IllegalStateException("토스증권 지수 데이터를 찾지 못했습니다.");
        }

        saveCache(context, out);
        return out;
    }

    /** Returns fresh quotes merged over the last successful Toss-only cache. */
    public static JSONObject mergeWithCache(Context context, JSONObject fresh) {
        JSONObject merged = loadCache(context);
        if (fresh == null) return merged;
        try {
            for (String key : new String[]{"kospi", "kosdaq", "nasdaq"}) {
                JSONObject q = fresh.optJSONObject(key);
                if (q != null) merged.put(key, q);
            }
        } catch (Exception ignored) {}
        return merged;
    }

    private static void saveCache(Context context, JSONObject fresh) {
        if (context == null || fresh == null || fresh.length() == 0) return;
        try {
            JSONObject merged = loadCache(context);
            for (String key : new String[]{"kospi", "kosdaq", "nasdaq"}) {
                JSONObject q = fresh.optJSONObject(key);
                if (q != null) merged.put(key, q);
            }
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putString(KEY_QUOTES, merged.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static JSONObject loadCache(Context context) {
        if (context == null) return new JSONObject();
        SharedPreferences p = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = p.getString(KEY_QUOTES, null);
        if (raw == null || raw.isEmpty()) return new JSONObject();
        try { return new JSONObject(raw); }
        catch (Exception ignored) { return new JSONObject(); }
    }

    private static void parseInto(JSONObject out, String html) throws Exception {
        if (html == null || html.isEmpty()) return;

        // Do NOT remove script blocks. Toss places hydrated market data there.
        String normalized = normalize(html);
        String visible = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ");
        visible = normalize(visible);

        // Prefer visible HTML when present, then inspect hydration/script payloads.
        parseAll(out, visible);
        parseAll(out, normalized);
    }

    private static void parseAll(JSONObject out, String text) throws Exception {
        putIfFound(out, "kospi", parseOne(text, new String[]{"코스피", "KOSPI"}, "kospi"));
        putIfFound(out, "kosdaq", parseOne(text, new String[]{"코스닥", "KOSDAQ"}, "kosdaq"));
        putIfFound(out, "nasdaq", parseOne(text, new String[]{"나스닥", "NASDAQ"}, "nasdaq"));
    }

    private static void putIfFound(JSONObject out, String key, JSONObject quote) throws Exception {
        if (quote != null && !out.has(key)) out.put(key, quote);
    }

    private static JSONObject parseOne(String text, String[] aliases, String market) throws Exception {
        if (text == null || text.isEmpty()) return null;

        for (String label : aliases) {
            int searchFrom = 0;
            while (true) {
                int from = indexOfIgnoreCase(text, label, searchFrom);
                if (from < 0) break;
                searchFrom = from + label.length();

                int after = Math.min(text.length(), from + label.length() + 28);
                String immediate = text.substring(from + label.length(), after)
                        .replaceAll("^[\\s\\\"':,._-]+", "");

                // Do not confuse NASDAQ with 'NASDAQ 100 선물'.
                if ("nasdaq".equals(market) && immediate.matches("(?is)^100(?:\\D|$).*$")) continue;

                String tail = text.substring(from, Math.min(text.length(), from + 1400));

                JSONObject strict = strictCandidate(tail, market);
                if (strict != null) return strict;

                JSONObject loose = looseCandidate(tail, market);
                if (loose != null) return loose;
            }
        }
        return null;
    }

    private static JSONObject strictCandidate(String tail, String market) throws Exception {
        Matcher m = STRICT.matcher(tail);
        while (m.find()) {
            double value = number(m.group(1));
            double move = number(m.group(3));
            double pct = Double.parseDouble(m.group(4));
            if ("-".equals(m.group(2))) { move = -move; pct = -pct; }
            if (!plausible(market, value, pct)) continue;

            // Reject accidental number pairings from unrelated cards in the hydration payload.
            double previous = value - move;
            if (previous > 0 && Math.abs(move) > 0.0001) {
                double calculated = move / previous * 100.0;
                if (Math.abs(calculated - pct) > 0.35) continue;
            }
            return quote(value, move, pct);
        }
        return null;
    }

    private static JSONObject looseCandidate(String tail, String market) throws Exception {
        Matcher m = VALUE_AND_RATE.matcher(tail);
        while (m.find()) {
            double value = number(m.group(1));
            double pct = Double.parseDouble(m.group(2).replace(" ", ""));
            if (!plausible(market, value, pct)) continue;

            // Toss's compact discovery ticker can omit absolute point change.
            // Derive it from the displayed rate; widgets only show the rate anyway.
            double denom = 1.0 + pct / 100.0;
            double previous = Math.abs(denom) < 0.00001 ? value : value / denom;
            double move = value - previous;
            return quote(value, move, pct);
        }
        return null;
    }

    private static JSONObject quote(double value, double move, double pct) throws Exception {
        JSONObject q = new JSONObject();
        q.put("value", value);
        q.put("change", move);
        q.put("changePct", pct);
        q.put("source", "토스증권");
        q.put("fetchedAt", System.currentTimeMillis());
        return q;
    }

    private static boolean plausible(String market, double value, double pct) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) return false;
        if (Double.isNaN(pct) || Double.isInfinite(pct) || Math.abs(pct) > 30.0) return false;
        switch (market) {
            case "kospi": return value >= 1000 && value <= 20000;
            case "kosdaq": return value >= 100 && value <= 5000;
            case "nasdaq": return value >= 5000 && value <= 100000;
            default: return true;
        }
    }

    public static boolean apply(MarketSnapshot s, JSONObject quote) {
        if (s == null || quote == null) return false;
        double value = quote.optDouble("value", Double.NaN);
        double pct = quote.optDouble("changePct", Double.NaN);
        if (Double.isNaN(value) || value <= 0 || Double.isNaN(pct)) return false;

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

    private static String normalize(String input) {
        String s = decodeUnicodeEscapes(input);
        s = s.replace("&nbsp;", " ")
                .replace("&#37;", "%")
                .replace("&#x25;", "%")
                .replace("&plus;", "+")
                .replace("&minus;", "-")
                .replace("&#43;", "+")
                .replace("&#45;", "-")
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("\\\"", "\"")
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("\\s+", " ");
        return s;
    }

    private static String decodeUnicodeEscapes(String input) {
        Matcher m = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            char c = (char) Integer.parseInt(m.group(1), 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(c)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static int indexOfIgnoreCase(String text, String needle, int from) {
        return text.toLowerCase(java.util.Locale.ROOT)
                .indexOf(needle.toLowerCase(java.util.Locale.ROOT), Math.max(0, from));
    }

    private static double number(String s) {
        return Double.parseDouble(s.replace(",", "").trim());
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(14000);
        c.setRequestMethod("GET");
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.7");
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("Pragma", "no-cache");

        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("Toss Securities HTTP " + code);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        } finally {
            c.disconnect();
        }
        return sb.toString();
    }
}
