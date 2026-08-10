package com.marketmood.widget;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the public market ticker shown by Toss Securities WTS.
 *
 * This intentionally uses only the public market strip so the app does not need
 * to store a Toss Open API client secret on the device. Historical series still
 * come from MarketRepository; current displayed value/change are overwritten by
 * the Toss quote whenever the public Toss page is reachable.
 */
public final class TossMarketFetcher {
    private static final String[] SOURCES = {
            "https://www.tossinvest.com/",
            // Stock pages include the full market ticker strip, including KOSDAQ.
            "https://www.tossinvest.com/stocks/US20210301016"
    };

    private TossMarketFetcher() {}

    public static JSONObject fetchQuotes() throws Exception {
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
        return out;
    }

    private static void parseInto(JSONObject out, String html) throws Exception {
        String text = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&#37;", "%")
                .replace("&plus;", "+")
                .replace("&minus;", "-")
                .replace("&#43;", "+")
                .replace("&#45;", "-")
                .replaceAll("\\s+", " ");

        putIfFound(out, "kospi", parseOne(text, "코스피"));
        putIfFound(out, "kosdaq", parseOne(text, "코스닥"));
        putIfFound(out, "nasdaq", parseOne(text, "나스닥"));
    }

    private static void putIfFound(JSONObject out, String key, JSONObject quote) throws Exception {
        if (quote != null && !out.has(key)) out.put(key, quote);
    }

    private static JSONObject parseOne(String text, String label) throws Exception {
        int from = text.indexOf(label);
        if (from < 0) return null;

        // Some Toss cards put a short AI/comment phrase between the market name and value.
        // Limit the search window so another market's quote cannot be captured accidentally.
        String tail = text.substring(from, Math.min(text.length(), from + 180));
        Pattern p = Pattern.compile(
                "([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([+-])\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*\\(\\s*([0-9]+(?:\\.[0-9]+)?)%\\s*\\)");
        Matcher m = p.matcher(tail);
        if (!m.find()) return null;

        double value = number(m.group(1));
        double move = number(m.group(3));
        double pct = Double.parseDouble(m.group(4));
        if ("-".equals(m.group(2))) {
            move = -move;
            pct = -pct;
        }

        JSONObject q = new JSONObject();
        q.put("value", value);
        q.put("change", move);
        q.put("changePct", pct);
        q.put("source", "토스증권");
        return q;
    }

    public static void apply(MarketSnapshot s, JSONObject quote) {
        if (s == null || quote == null) return;
        double value = quote.optDouble("value", Double.NaN);
        if (Double.isNaN(value) || value <= 0) return;

        s.value = value;
        s.change = quote.optDouble("change", s.change);
        s.changePct = quote.optDouble("changePct", s.changePct);
        s.fallback = false;

        // Keep the latest visible weekly point consistent with the Toss reference value.
        if (!s.weekly.isEmpty()) {
            int i = s.weekly.size() - 1;
            MarketSnapshot.WeekPoint old = s.weekly.get(i);
            s.weekly.set(i, new MarketSnapshot.WeekPoint(old.date, value));
        }
    }

    private static double number(String s) {
        return Double.parseDouble(s.replace(",", "").trim());
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(9000);
        c.setReadTimeout(12000);
        c.setRequestMethod("GET");
        c.setInstanceFollowRedirects(true);
        // Toss WTS serves its complete public desktop page to a desktop browser UA.
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0 Safari/537.36");
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.7");

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
