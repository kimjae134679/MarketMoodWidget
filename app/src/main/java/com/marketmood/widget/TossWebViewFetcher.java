package com.marketmood.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads the public Toss Securities WTS in an off-screen WebView and reads the
 * ticker after Toss' own JavaScript has rendered it. This is deliberately based
 * on exact index rows/buttons rather than scanning unrelated page numbers.
 */
public final class TossWebViewFetcher {
    public interface Callback { void onResult(JSONObject quotes); }

    private static final String URL =
            "https://www.tossinvest.com/?contentParams=%7B%22id%22%3A803%7D&contentType=tics";
    private static final long POLL_MS = 700L;
    private static final int MAX_POLLS = 18;

    private TossWebViewFetcher() {}

    @SuppressLint("SetJavaScriptEnabled")
    public static void fetch(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        main.post(() -> {
            final WebView web;
            try {
                web = new WebView(app);
            } catch (Throwable t) {
                callback.onResult(null);
                return;
            }

            AtomicBoolean finished = new AtomicBoolean(false);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setBlockNetworkImage(true);
            s.setLoadsImagesAutomatically(false);
            s.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36");

            Runnable fail = () -> finish(web, finished, callback, null);
            web.setWebViewClient(new WebViewClient() {
                private boolean startedPolling = false;

                @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                }

                @Override public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (!startedPolling) {
                        startedPolling = true;
                        poll(view, 0, finished, callback, main);
                    }
                }
            });

            // Hard timeout in case the page never reaches onPageFinished.
            main.postDelayed(fail, POLL_MS * (MAX_POLLS + 5L));
            try { web.loadUrl(URL); }
            catch (Throwable t) { fail.run(); }
        });
    }

    private static void poll(WebView web, int attempt, AtomicBoolean finished,
                             Callback callback, Handler main) {
        if (finished.get()) return;
        if (attempt >= MAX_POLLS) {
            finish(web, finished, callback, null);
            return;
        }

        web.evaluateJavascript(extractScript(), encoded -> {
            if (finished.get()) return;
            JSONObject result = decodeResult(encoded);
            // KOSPI + KOSDAQ must both be present. NASDAQ may be retained from
            // the last Toss-only cache if its row is temporarily absent.
            if (result != null && result.has("kospi") && result.has("kosdaq")) {
                finish(web, finished, callback, result);
            } else {
                main.postDelayed(() -> poll(web, attempt + 1, finished, callback, main), POLL_MS);
            }
        });
    }

    private static void finish(WebView web, AtomicBoolean finished, Callback callback, JSONObject result) {
        if (!finished.compareAndSet(false, true)) return;
        try { web.stopLoading(); } catch (Throwable ignored) {}
        try { web.loadUrl("about:blank"); } catch (Throwable ignored) {}
        try { web.destroy(); } catch (Throwable ignored) {}
        try { callback.onResult(result); } catch (Throwable ignored) {}
    }

    private static JSONObject decodeResult(String encoded) {
        if (encoded == null || encoded.equals("null") || encoded.equals("\"\"")) return null;
        try {
            String raw = new JSONArray("[" + encoded + "]").getString(0);
            if (raw == null || raw.isEmpty()) return null;
            JSONObject out = new JSONObject(raw);
            return out.length() == 0 ? null : out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractScript() {
        // Toss has two useful representations of the same ticker: a list row
        // with an /indices/ link and a compact button strip. We prefer the exact
        // index-link row and use the compact button only as a fallback.
        return "(function(){" +
                "const out={};" +
                "const defs=[['코스피','kospi'],['코스닥','kosdaq'],['나스닥','nasdaq']];" +
                "for(const d of defs){" +
                "let a=document.querySelector('a[aria-label=\\\"'+d[0]+'\\\"][href^=\\\"/indices/\\\"]');" +
                "let box=a?(a.closest('[data-listrow-root]')||a.parentElement):null;" +
                "if(!box) box=document.querySelector('button[data-content-value=\\\"'+d[0]+'\\\"]');" +
                "if(!box) continue;" +
                "let t=(box.innerText||box.textContent||'').replace(/\\s+/g,' ').trim();" +
                "let m=t.match(/(?:^|\\s)([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+([+-][0-9][0-9,]*(?:\\.[0-9]+)?)\\s+\\(?([0-9]+(?:\\.[0-9]+)?)%\\)?/);" +
                "if(!m) continue;" +
                "let value=parseFloat(m[1].replace(/,/g,''));" +
                "let move=parseFloat(m[2].replace(/,/g,''));" +
                "let pct=parseFloat(m[3]); if(move<0)pct=-pct;" +
                "if(!Number.isFinite(value)||!Number.isFinite(move)||!Number.isFinite(pct))continue;" +
                "out[d[1]]={value:value,change:move,changePct:pct,source:'토스증권',fetchedAt:Date.now()};" +
                "}" +
                "return JSON.stringify(out);" +
                "})()";
    }
}
