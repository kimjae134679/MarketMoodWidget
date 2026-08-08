package com.marketmood.widget;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KrxAlertFetcher {
    // Best-effort parser of the official KRX Data Marketplace market-operation notices.
    // If a same-day KOSPI sidecar/circuit-breaker notice cannot be confirmed, returns an empty string.
    public static String fetchKospiAlert() {
        try {
            String html = MarketRepository.httpGet("https://data.krx.co.kr/contents/MDC/MAIN/main/index.cmd");
            String text = html.replaceAll("(?is)<script.*?</script>", " ")
                    .replaceAll("(?is)<style.*?</style>", " ")
                    .replaceAll("(?is)<[^>]+>", " ")
                    .replace("&nbsp;", " ")
                    .replaceAll("\\s+", " ");
            LocalDate now = LocalDate.now(ZoneId.of("Asia/Seoul"));
            String date1 = now.format(DateTimeFormatter.ofPattern("yy/MM/dd"));
            String date2 = now.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            String date3 = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (!(text.contains(date1) || text.contains(date2) || text.contains(date3))) return "";

            String side = findAroundToday(text, date1, date2, date3, "유가증권시장", "사이드카", "발동");
            if (!side.isEmpty()) return enrichTime("사이드카 발동", side);
            String circuit = findAroundToday(text, date1, date2, date3, "유가증권시장", "서킷브레이커", "발동");
            if (!circuit.isEmpty()) return enrichTime("서킷브레이커 발동", circuit);
        } catch (Exception ignored) {}
        return "";
    }

    private static String findAroundToday(String text, String d1, String d2, String d3, String... needles) {
        int from=0;
        while(from < text.length()) {
            int pos=text.indexOf(needles[0],from);
            if(pos<0) return "";
            int a=Math.max(0,pos-220), b=Math.min(text.length(),pos+420);
            String piece=text.substring(a,b);
            boolean all=true; for(String n:needles) if(!piece.contains(n)){all=false;break;}
            boolean today=piece.contains(d1)||piece.contains(d2)||piece.contains(d3);
            if(all && today) return piece;
            from=pos+needles[0].length();
        }
        return "";
    }

    private static String enrichTime(String base, String text) {
        Matcher m=Pattern.compile("([01]?[0-9]|2[0-3])[:시]([0-5][0-9])").matcher(text);
        if(m.find()) return base+" "+String.format("%02d:%02d",Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)));
        return base;
    }
}
