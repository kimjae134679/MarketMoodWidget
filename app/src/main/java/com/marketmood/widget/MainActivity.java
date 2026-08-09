package com.marketmood.widget;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView web;
    private final ExecutorService exec= Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        web=new WebView(this); setContentView(web);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());web.addJavascriptInterface(new Bridge(),"MarketNative");
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onDestroy(){super.onDestroy();exec.shutdownNow();if(web!=null)web.destroy();}

    public class Bridge {
        @JavascriptInterface public void refresh(){
            MarketUpdater.refreshAsync(MainActivity.this,()->web.post(()->sendCached()));
            fetchFundsAsync();
        }
        @JavascriptInterface public void requestDashboard(){
            sendCached();
            MarketUpdater.refreshAsync(MainActivity.this,()->web.post(()->sendCached()));
            fetchFundsAsync();
        }
        @JavascriptInterface public void requestDate(String date){
            exec.execute(()->{
                JSONObject out=new JSONObject();
                try{
                    out.put("kospi",MarketRepository.fetchAtDate("^KS11","코스피",date));
                    out.put("nasdaq",MarketRepository.fetchAtDate("^IXIC","나스닥",date));
                    out.put("ok",true);
                }catch(Exception e){try{out.put("ok",false);out.put("error",e.getMessage()==null?"조회 실패":e.getMessage());}catch(Exception ignored){}}
                String json=out.toString();web.post(()->web.evaluateJavascript("window.onDateData("+JSONObject.quote(json)+")",null));
            });
        }
    }

    private void fetchFundsAsync(){
        exec.execute(()->{
            try{
                JSONObject funds=MarketFundsFetcher.fetch();
                String json=funds.toString();
                web.post(()->web.evaluateJavascript("window.onFundsData("+JSONObject.quote(json)+")",null));
            }catch(Exception ignored){}
        });
    }

    private void sendCached(){
        try{
            MarketSnapshot k=MarketRepository.load(this,"^KS11","코스피");
            MarketSnapshot q=MarketRepository.load(this,"^KQ11","코스닥");
            MarketSnapshot n=MarketRepository.load(this,"^IXIC","나스닥");
            JSONObject o=new JSONObject();o.put("kospi",k.toJson());o.put("kosdaq",q.toJson());o.put("nasdaq",n.toJson());
            String json=o.toString(); web.post(()->web.evaluateJavascript("window.onMarketData("+JSONObject.quote(json)+")",null));
        }catch(Exception ignored){}
    }
}
