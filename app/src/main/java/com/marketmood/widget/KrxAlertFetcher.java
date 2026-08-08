package com.marketmood.widget;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KrxAlertFetcher {
    // Best-effort parser. Normal state returns empty text and is never shown in the UI/widget.
    public static String fetchKospiAlert() {
        try {
            String html = MarketRepository.httpGet("https://data.krx.co.kr/contents/MDC/MAIN/main/index.cmd");
            String text = html.replaceAll("(?is)<script.*?</script>", " ")
                    .replaceAll("(?is)<style.*?</style>", " ")
                    .replaceAll("(?is)<[^>]+>", " ")
                    .replace("&nbsp;", " ")
                    .replaceAll("\\s+", " ");
            LocalDate now = LocalDate.now(ZoneId.of("Asia/Seoul"));
            String d1 = now.format(DateTimeFormatter.ofPattern("yy/MM/dd"));
            String d2 = now.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"));
            String d3 = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (!(text.contains(d1) || text.contains(d2) || text.contains(d3))) return "";

            String side = findAroundToday(text, d1, d2, d3, "유가증권시장", "사이드카", "발동");
            if (!side.isEmpty()) {
                String direction = detectSidecarDirection(side);
                String base = direction.isEmpty() ? "사이드카 발동" : direction + " 사이드카 발동";
                return enrichTime(base, side);
            }
            String circuit = findAroundToday(text, d1, d2, d3, "유가증권시장", "서킷브레이커", "발동");
            if (!circuit.isEmpty()) {
                String direction = circuit.contains("상승") ? "상승" : "하락";
                return enrichTime(direction + " 서킷브레이커 발동", circuit);
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String detectSidecarDirection(String text) {
        if (text.contains("상승") || text.contains("매수호가") || text.contains("매수 호가")) return "상승";
        if (text.contains("하락") || text.contains("매도호가") || text.contains("매도 호가")) return "하락";
        return "";
    }

    private static String findAroundToday(String text, String d1, String d2, String d3, String... needles) {
        int from=0;
        while(from < text.length()) {
            int pos=text.indexOf(needles[0],from);
            if(pos<0) return "";
            int a=Math.max(0,pos-260), b=Math.min(text.length(),pos+520);
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
