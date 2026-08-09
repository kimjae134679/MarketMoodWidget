package com.marketmood.widget;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches the two market-funding indicators shown on the dashboard from KOFIA FreeSIS. */
public final class MarketFundsFetcher {
    private static final String URL_MAIN = "https://freesis.kofia.or.kr/stat/main.do";

    private MarketFundsFetcher() {}

    public static JSONObject fetch() throws Exception {
        String html = get(URL_MAIN);
        String text = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&#37;", "%")
                .replaceAll("\\s+", " ");

        JSONObject out = new JSONObject();
        out.put("credit", parse(text, "신용융자"));
        out.put("deposit", parse(text, "투자자예탁금"));
        return out;
    }

    private static JSONObject parse(String text, String label) throws Exception {
        int at = text.indexOf(label);
        if (at < 0) throw new IllegalStateException(label + " 항목을 찾지 못했습니다.");

        String tail = text.substring(at, Math.min(text.length(), at + 700));
        Pattern p = Pattern.compile("(\\d{2}/\\d{2}).{0,180}?([\\d,]{5,}).{0,100}?([+-]?[\\d,]+).{0,100}?([+-]?\\d+(?:\\.\\d+)?)%", Pattern.DOTALL);
        Matcher m = p.matcher(tail);
        if (!m.find()) throw new IllegalStateException(label + " 값을 해석하지 못했습니다.");

        String date = m.group(1);
        double amountMillion = parseNumber(m.group(2));
        double deltaMillion = parseNumber(m.group(3));
        double changePct = Double.parseDouble(m.group(4));

        JSONObject o = new JSONObject();
        o.put("date", date);
        o.put("valueTrillion", round2(amountMillion / 1_000_000.0));
        o.put("changePct", changePct);
        o.put("changeTrillion", round2(deltaMillion / 1_000_000.0));
        return o;
    }

    private static double parseNumber(String s) {
        return Double.parseDouble(s.replace(",", "").trim());
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(9000);
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MarketMoodWidget/1.6");
        c.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9");
        c.setInstanceFollowRedirects(true);

        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("FreeSIS HTTP " + code);

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
