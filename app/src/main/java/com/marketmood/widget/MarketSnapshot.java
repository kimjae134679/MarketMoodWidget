package com.marketmood.widget;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MarketSnapshot {
    public String symbol;
    public String name;
    public String date = "";
    public double value;
    public double change;
    public double changePct;
    public double score;
    public String sentiment = "중립";
    public String alert = "";
    public double prevDayScore = 50;
    public double weekAgoScore = 50;
    public double monthAgoScore = 50;
    public double yearAgoScore = 50;
    public boolean fallback = false;
    public final List<WeekPoint> weekly = new ArrayList<>();

    public static class WeekPoint {
        public final String date;
        public final double value;
        public WeekPoint(String date, double value) {
            this.date = date;
            this.value = value;
        }
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("symbol", symbol);
            o.put("name", name);
            o.put("date", date);
            o.put("value", value);
            o.put("change", change);
            o.put("changePct", changePct);
            o.put("score", score);
            o.put("sentiment", sentiment);
            o.put("alert", alert == null ? "" : alert);
            o.put("prevDayScore", prevDayScore);
            o.put("weekAgoScore", weekAgoScore);
            o.put("monthAgoScore", monthAgoScore);
            o.put("yearAgoScore", yearAgoScore);
            o.put("fallback", fallback);
            JSONArray a = new JSONArray();
            for (WeekPoint p : weekly) {
                JSONObject x = new JSONObject();
                x.put("date", p.date);
                x.put("value", p.value);
                a.put(x);
            }
            o.put("weekly", a);
        } catch (Exception ignored) {}
        return o;
    }
}
