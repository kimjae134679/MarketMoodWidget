package com.marketmood.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

public class WidgetConfigActivity extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        Intent intent=getIntent();
        if(intent!=null&&intent.getExtras()!=null) appWidgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);
        if(appWidgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}

        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(48,44,48,36); root.setBackgroundColor(Color.rgb(247,247,250));
        TextView title=text("위젯 설정",30,true); root.addView(title);
        root.addView(text("시장",16,true),lpTop(26));
        RadioGroup market=new RadioGroup(this); market.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton kospi=radio("코스피"); RadioButton nasdaq=radio("나스닥"); kospi.setChecked(true); market.addView(kospi);market.addView(nasdaq);root.addView(market);

        root.addView(text("테마",16,true),lpTop(22));
        RadioGroup theme=new RadioGroup(this);theme.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton dark=radio("다크");RadioButton light=radio("라이트");dark.setChecked(true);theme.addView(dark);theme.addView(light);root.addView(theme);

        root.addView(text("표시 방식",16,true),lpTop(22));
        RadioGroup mode=new RadioGroup(this);mode.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton gauge=radio("게이지");RadioButton graph=radio("주봉 그래프");gauge.setChecked(true);mode.addView(gauge);mode.addView(graph);root.addView(mode);

        TextView note=text("• 코스피/나스닥의 당일 등락률도 함께 표시됩니다.\n• 사이드카·서킷브레이커는 발동이 확인된 경우에만 나타납니다.",14,false); note.setTextColor(Color.DKGRAY); root.addView(note,lpTop(26));

        Button save=new Button(this);save.setText("위젯 추가");save.setTextSize(17); root.addView(save,lpTop(30));
        save.setOnClickListener(v->{
            SharedPreferences.Editor e=getSharedPreferences(MarketWidgetProvider.CONFIG,MODE_PRIVATE).edit();
            e.putString("market_"+appWidgetId,nasdaq.isChecked()?"NASDAQ":"KOSPI");
            e.putBoolean("dark_"+appWidgetId,dark.isChecked());
            e.putBoolean("graph_"+appWidgetId,graph.isChecked());e.apply();
            AppWidgetManager manager=AppWidgetManager.getInstance(this);MarketWidgetProvider.updateOne(this,manager,appWidgetId);
            Intent result=new Intent();result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,appWidgetId);setResult(RESULT_OK,result);
            MarketUpdater.refreshAsync(this,null);finish();
        });
        setContentView(root);
    }

    private TextView text(String s,float sp,boolean bold){ TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(Color.rgb(20,20,24));if(bold)v.setTypeface(v.getTypeface(),1);return v; }
    private RadioButton radio(String s){RadioButton r=new RadioButton(this);r.setText(s);r.setTextSize(16);r.setPadding(0,0,28,0);return r;}
    private LinearLayout.LayoutParams lpTop(int dp){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp;return p;}
}
