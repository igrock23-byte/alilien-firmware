package work.alilien.ethwwidget;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;
import java.io.*; import java.net.*; import java.nio.charset.StandardCharsets; import java.text.*; import java.util.*;
import org.json.*;

public class EthwWidgetProvider extends AppWidgetProvider {
    private static final String REFRESH="work.alilien.ethwwidget.REFRESH";
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids){ for(int id:ids) update(c,id,false); }
    @Override public void onReceive(Context c,Intent i){ super.onReceive(c,i); if(REFRESH.equals(i.getAction())) update(c,i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1),true); }
    public static void update(Context c,int id,boolean force){
        if(id<0)return;
        RemoteViews rv=new RemoteViews(c.getPackageName(),R.layout.widget_ethw);
        Intent ri=new Intent(c,EthwWidgetProvider.class).setAction(REFRESH).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        rv.setOnClickPendingIntent(R.id.refresh,PendingIntent.getBroadcast(c,id,ri,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        AppWidgetManager.getInstance(c).updateAppWidget(id,rv);
        new Thread(()->load(c,id)).start();
    }
    private static void load(Context c,int id){
        RemoteViews rv=new RemoteViews(c.getPackageName(),R.layout.widget_ethw);
        String account=c.getSharedPreferences("widget",Context.MODE_PRIVATE).getString("id_"+id,"").trim();
        try {
            if(account.isEmpty()) throw new IllegalArgumentException("K1Pool ID not configured");
            JSONObject o=new JSONObject(get("https://k1pool.com/api/miner/ethw/"+URLEncoder.encode(account,"UTF-8")));
            double cur=num(o,"hashrateShort");
            double h3=num(o,"hashrateLong");
            double h24=first(o,"hashrate24h","hashrate_24h","average24h");
            double ethwDay=num(o,"ethw_per_day");
            double usdtDay=num(o,"usdt_per_day");
            double price=num(o,"ethwPrice");
            if(Double.isNaN(usdtDay) && !Double.isNaN(ethwDay) && !Double.isNaN(price)) usdtDay=ethwDay*price;
            rv.setTextViewText(R.id.hashrate,"K1POOL  "+hash(cur));
            rv.setTextViewText(R.id.averages,"3h "+hash(h3)+"  •  24h "+hash(h24));
            rv.setTextViewText(R.id.price,"ETHW  "+money(price));
            String income=(Double.isNaN(ethwDay)?"--":String.format(Locale.US,"%.4f",ethwDay))+" ETHW/day  •  "+money(usdtDay)+"/day  •  "+money(Double.isNaN(usdtDay)?Double.NaN:usdtDay*30)+"/month";
            rv.setTextViewText(R.id.income,income);
            rv.setTextViewText(R.id.updated,"UPDATED "+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
        } catch(Exception e){
            rv.setTextViewText(R.id.updated,"DATA ERROR • TAP ↻");
        }
        AppWidgetManager.getInstance(c).updateAppWidget(id,rv);
    }
    private static String get(String u)throws Exception{
        HttpURLConnection h=(HttpURLConnection)new URL(u).openConnection(); h.setConnectTimeout(10000); h.setReadTimeout(10000); h.setRequestProperty("User-Agent","AliLien-ETHW-Widget/0.2");
        int code=h.getResponseCode(); if(code<200||code>=300) throw new IOException("HTTP "+code);
        try(InputStream in=h.getInputStream()){ ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] x=new byte[4096]; int n; while((n=in.read(x))>0)b.write(x,0,n); return b.toString(StandardCharsets.UTF_8.name()); }
    }
    private static double num(JSONObject o,String key){ if(!o.has(key)||o.isNull(key))return Double.NaN; Object v=o.opt(key); if(v instanceof Number)return ((Number)v).doubleValue(); try{return Double.parseDouble(String.valueOf(v));}catch(Exception e){return Double.NaN;} }
    private static double first(JSONObject o,String... keys){ for(String k:keys){double v=num(o,k); if(!Double.isNaN(v))return v;} return Double.NaN; }
    private static String hash(double v){ if(Double.isNaN(v)||v<0)return "--"; if(v>=1e9)return String.format(Locale.US,"%.2f GH/s",v/1e9); if(v>=1e6)return String.format(Locale.US,"%.2f MH/s",v/1e6); if(v>=1e3)return String.format(Locale.US,"%.2f kH/s",v/1e3); return String.format(Locale.US,"%.0f H/s",v); }
    private static String money(double v){ return Double.isNaN(v)?"$--":String.format(Locale.US,"$%.2f",v); }
}
