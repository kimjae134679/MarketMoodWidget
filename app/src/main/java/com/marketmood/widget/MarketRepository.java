package com.marketmood.widget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MarketRepository {
    private static final String PREF = "market_cache";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static MarketSnapshot fetch(String symbol, String name) throws Exception {
        String enc = URLEncoder.encode(symbol, StandardCharsets.UTF_8.toString());
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + enc + "?range=2y&interval=1d&includePrePost=false";
        JSONObject root = new JSONObject(httpGet(url));
        JSONObject result = root.getJSONObject("chart").getJSONArray("result").getJSONObject(0);
        JSONObject meta = result.getJSONObject("meta");
        ZoneId zone = safeZone(meta.optString("exchangeTimezoneName", "UTC"));
        JSONArray ts = result.getJSONArray("timestamp");
        JSONArray closes = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close");

        List<Double> vals = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < Math.min(ts.length(), closes.length()); i++) {
            if (closes.isNull(i)) continue;
            double c = closes.optDouble(i, Double.NaN);
            if (Double.isNaN(c) || c <= 0) continue;
            LocalDate d = Instant.ofEpochSecond(ts.getLong(i)).atZone(zone).toLocalDate();
            vals.add(c);
            dates.add(d);
        }
        if (vals.size() < 2) throw new IllegalStateException("Not enough market data");

        MarketSnapshot s = new MarketSnapshot();
        s.symbol = symbol;
        s.name = name;
        int last = vals.size() - 1;
        s.value = vals.get(last);
        s.change = vals.get(last) - vals.get(last - 1);
        s.changePct = vals.get(last - 1) == 0 ? 0 : s.change / vals.get(last - 1) * 100.0;
        s.date = dates.get(last).format(DAY);
        s.score = calculateSentiment(vals);
        s.prevDayScore = calculateSentimentAt(vals, Math.max(0, last-1));
        s.weekAgoScore = calculateSentimentAt(vals, Math.max(0, last-5));
        s.monthAgoScore = calculateSentimentAt(vals, Math.max(0, last-21));
        s.yearAgoScore = calculateSentimentAt(vals, Math.max(0, last-252));
        s.sentiment = sentimentLabel(s.score);
        fillWeekly(s, dates, vals);
        return s;
    }

    public static JSONObject fetchAtDate(String symbol, String name, String selectedDate) throws Exception {
        LocalDate target = LocalDate.parse(selectedDate, DAY);
        long p1 = target.minusDays(12).atStartOfDay(ZoneId.of("UTC")).toEpochSecond();
        long p2 = target.plusDays(3).atStartOfDay(ZoneId.of("UTC")).toEpochSecond();
        String enc = URLEncoder.encode(symbol, StandardCharsets.UTF_8.toString());
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + enc + "?period1=" + p1 + "&period2=" + p2 + "&interval=1d&includePrePost=false";
        JSONObject root = new JSONObject(httpGet(url));
        JSONObject result = root.getJSONObject("chart").getJSONArray("result").getJSONObject(0);
        ZoneId zone = safeZone(result.getJSONObject("meta").optString("exchangeTimezoneName", "UTC"));
        JSONArray ts = result.getJSONArray("timestamp");
        JSONArray closes = result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close");

        List<LocalDate> dates = new ArrayList<>();
        List<Double> vals = new ArrayList<>();
        for (int i=0; i<Math.min(ts.length(), closes.length()); i++) {
            if (closes.isNull(i)) continue;
            double c = closes.optDouble(i, Double.NaN);
            if (Double.isNaN(c)) continue;
            LocalDate d = Instant.ofEpochSecond(ts.getLong(i)).atZone(zone).toLocalDate();
            if (!d.isAfter(target)) {
                dates.add(d); vals.add(c);
            }
        }
        if (vals.size() < 2) throw new IllegalStateException("해당 날짜 이전 거래 데이터가 부족합니다.");
        int i = vals.size()-1;
        double prev = vals.get(i-1);
        double now = vals.get(i);
        JSONObject o = new JSONObject();
        o.put("symbol", symbol);
        o.put("name", name);
        o.put("requestedDate", selectedDate);
        o.put("tradingDate", dates.get(i).format(DAY));
        o.put("value", now);
        o.put("change", now-prev);
        o.put("changePct", prev == 0 ? 0 : (now-prev)/prev*100.0);
        return o;
    }

    public static void save(Context c, MarketSnapshot s) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(s.symbol, s.toJson().toString()).apply();
    }

    public static MarketSnapshot load(Context c, String symbol, String name) {
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = p.getString(symbol, null);
        if (raw != null) {
            try { return fromJson(new JSONObject(raw)); } catch (Exception ignored) {}
        }
        return fallback(symbol, name);
    }

    private static MarketSnapshot fromJson(JSONObject o) throws Exception {
        MarketSnapshot s = new MarketSnapshot();
        s.symbol = o.optString("symbol"); s.name=o.optString("name"); s.date=o.optString("date");
        s.value=o.optDouble("value"); s.change=o.optDouble("change"); s.changePct=o.optDouble("changePct");
        s.score=o.optDouble("score",50); s.sentiment=o.optString("sentiment","중립"); s.alert=o.optString("alert","");
        s.prevDayScore=o.optDouble("prevDayScore",s.score); s.weekAgoScore=o.optDouble("weekAgoScore",s.score); s.monthAgoScore=o.optDouble("monthAgoScore",s.score); s.yearAgoScore=o.optDouble("yearAgoScore",s.score);
        s.fallback=o.optBoolean("fallback",false);
        JSONArray a=o.optJSONArray("weekly");
        if(a!=null) for(int i=0;i<a.length();i++){ JSONObject x=a.getJSONObject(i); s.weekly.add(new MarketSnapshot.WeekPoint(x.optString("date"),x.optDouble("value"))); }
        return s;
    }

    public static MarketSnapshot fallback(String symbol, String name) {
        MarketSnapshot s = new MarketSnapshot();
        s.symbol=symbol; s.name=name; s.fallback=true;
        if ("^KS11".equals(symbol)) { s.value=6258.77; s.change=-37.61; s.changePct=-0.60; s.score=68.0; s.date="2026-08-07"; }
        else if ("^KQ11".equals(symbol)) { s.value=798.81; s.change=-2.86; s.changePct=-0.36; s.score=54.0; s.date="2026-08-07"; }
        else { s.value=24839.37; s.change=396.42; s.changePct=1.62; s.score=63.7; s.date="2026-08-07"; }
        s.sentiment=sentimentLabel(s.score);
        s.prevDayScore=Math.max(0,s.score-4.0); s.weekAgoScore=Math.max(0,s.score-18.0); s.monthAgoScore=Math.max(0,s.score-28.0); s.yearAgoScore=Math.max(0,s.score-10.0);
        double[] sample={0.94,0.96,0.95,0.98,1.00,0.99,1.03,1.02,1.05};
        for(int i=0;i<sample.length;i++) s.weekly.add(new MarketSnapshot.WeekPoint("W"+(i+1), s.value*sample[i]));
        return s;
    }

    private static double calculateSentiment(List<Double> v) {
        int n=v.size();
        if(n<22) return 50;
        double rsi = rsi(v,14);
        int span=Math.min(20,n);
        double sum=0; for(int i=n-span;i<n;i++) sum+=v.get(i);
        double sma=sum/span;
        double trend=clamp(50 + ((v.get(n-1)/sma)-1.0)*500.0,0,100);
        double mom=(v.get(n-1)/v.get(n-1-span)-1.0)*100.0;
        double momentum=clamp(50 + mom*(50.0/15.0),0,100);
        return Math.round((0.45*rsi + 0.30*trend + 0.25*momentum)*10.0)/10.0;
    }

    private static double calculateSentimentAt(List<Double> v, int endInclusive) {
        if (v.isEmpty()) return 50;
        int end=Math.max(0,Math.min(endInclusive,v.size()-1));
        int start=Math.max(0,end-80);
        return calculateSentiment(new ArrayList<>(v.subList(start,end+1)));
    }

    private static double rsi(List<Double> v, int period) {
        int n=v.size(); int start=Math.max(1,n-period);
        double gain=0,loss=0;
        for(int i=start;i<n;i++){ double d=v.get(i)-v.get(i-1); if(d>=0)gain+=d; else loss-=d; }
        if(loss==0) return 100; double rs=gain/loss; return 100-(100/(1+rs));
    }

    private static void fillWeekly(MarketSnapshot s, List<LocalDate> dates, List<Double> vals) {
        WeekFields wf=WeekFields.of(Locale.KOREA);
        Map<String, MarketSnapshot.WeekPoint> m=new LinkedHashMap<>();
        for(int i=0;i<dates.size();i++){
            LocalDate d=dates.get(i);
            String k=d.getYear()+"-"+d.get(wf.weekOfWeekBasedYear());
            m.put(k,new MarketSnapshot.WeekPoint(d.format(DateTimeFormatter.ofPattern("M/d")), vals.get(i)));
        }
        List<MarketSnapshot.WeekPoint> all=new ArrayList<>(m.values());
        int start=Math.max(0, all.size()-10);
        for(int i=start;i<all.size();i++) s.weekly.add(all.get(i));
    }

    public static String sentimentLabel(double score) {
        if(score<20) return "극도의 공포";
        if(score<40) return "공포";
        if(score<60) return "중립";
        if(score<80) return "탐욕";
        return "극도의 탐욕";
    }

    private static ZoneId safeZone(String z){ try{return ZoneId.of(z);}catch(Exception e){return ZoneId.of("UTC");} }
    private static double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}

    public static String httpGet(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(9000); c.setReadTimeout(12000); c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent","Mozilla/5.0 MarketMoodWidget/1.0");
        c.setRequestProperty("Accept","application/json,text/html,*/*");
        int code=c.getResponseCode();
        if(code<200||code>=300) throw new IllegalStateException("HTTP "+code);
        BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line).append('\n');
        br.close(); c.disconnect(); return sb.toString();
    }
}
