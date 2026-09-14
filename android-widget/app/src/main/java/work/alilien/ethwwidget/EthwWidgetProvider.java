package work.alilien.ethwwidget;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.view.View;
import android.widget.RemoteViews;
import java.io.*; import java.net.*; import java.nio.charset.StandardCharsets; import java.text.*; import java.util.*;
import org.json.*;

public class EthwWidgetProvider extends AppWidgetProvider {
    private static final String REFRESH="work.alilien.ethwwidget.REFRESH";
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids){ for(int id:ids) update(c,id,false); }
    @Override public void onReceive(Context c,Intent i){ super.onReceive(c,i); if(REFRESH.equals(i.getAction())) update(c,i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1),true); }
    public static void update(Context c,int id,boolean force){
        if(id<0)return; RemoteViews rv=new RemoteViews(c.getPackageName(),R.layout.widget_ethw);
        Intent ri=new Intent(c,EthwWidgetProvider.class).setAction(REFRESH).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        rv.setOnClickPendingIntent(R.id.refresh,PendingIntent.getBroadcast(c,id,ri,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        Intent si=new Intent(c,SettingsActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        rv.setOnClickPendingIntent(R.id.settings,PendingIntent.getActivity(c,100000+id,si,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        AppWidgetManager.getInstance(c).updateAppWidget(id,rv); new Thread(()->load(c,id)).start();
    }
    private static void load(Context c,int id){
        RemoteViews rv=new RemoteViews(c.getPackageName(),R.layout.widget_ethw); String account=c.getSharedPreferences("widget",Context.MODE_PRIVATE).getString("id_"+id,"").trim();
        try {
            if(account.isEmpty()){ rv.setTextViewText(R.id.status,"NO K1POOL ID • TAP ⚙"); rv.setViewVisibility(R.id.status,View.VISIBLE); finish(c,id,rv); return; }
            JSONObject root=new JSONObject(get("https://k1pool.com/api/miner/ethw/"+URLEncoder.encode(account,"UTF-8")));
            double cur=find(root,"hashrateShort","hashrate_short","currentHashrate","current_hashrate");
            double h3=find(root,"hashrateLong","hashrate_long","hashrate3h","averageHashrate");
            double h24=find(root,"hashrate24h","hashrate_24h","average24h");
            double ethwDay=find(root,"ethw_per_day","ethwPerDay","coins_per_day");
            double usdtDay=find(root,"usdt_per_day","usdtPerDay");
            double price=find(root,"ethwPrice","ethw_price","price");
            double change=find(root,"priceChange24h","price_change_24h","change24h");
            double epoch=find(root,"epoch"); double dag=find(root,"dagSize","dag_size","dag");
            if(Double.isNaN(usdtDay)&&!Double.isNaN(ethwDay)&&!Double.isNaN(price))usdtDay=ethwDay*price;
            rv.setTextViewText(R.id.hashrate,"K1POOL  "+hash(cur)); rv.setTextViewText(R.id.averages,"3h "+hash(h3)+"  •  24h "+hash(h24));
            rv.setTextViewText(R.id.price,"ETHW  "+money(price)+(Double.isNaN(change)?"":String.format(Locale.US,"  %+.2f%%",change)));
            rv.setTextViewText(R.id.income,(Double.isNaN(ethwDay)?"--":String.format(Locale.US,"%.4f",ethwDay))+" ETHW/day  •  "+money(usdtDay)+"/day  •  "+money(Double.isNaN(usdtDay)?Double.NaN:usdtDay*30)+"/month");
            if(!Double.isNaN(epoch)||!Double.isNaN(dag)) rv.setTextViewText(R.id.epoch,"EPOCH "+whole(epoch)+"  •  DAG "+dagText(dag)+"  •  NEXT --");
            rv.setTextViewText(R.id.status,"LINK OK • "+shortId(account)); rv.setViewVisibility(R.id.status,View.VISIBLE); rv.setTextViewText(R.id.updated,"UPDATED "+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
        }catch(Exception e){ rv.setTextViewText(R.id.status,"K1POOL ERROR • TAP ⚙ / ↻"); rv.setViewVisibility(R.id.status,View.VISIBLE); rv.setTextViewText(R.id.updated,"ERROR "+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date())); }
        finish(c,id,rv);
    }
    private static void finish(Context c,int id,RemoteViews rv){ AppWidgetManager.getInstance(c).updateAppWidget(id,rv); }
    private static String get(String u)throws Exception{ HttpURLConnection h=(HttpURLConnection)new URL(u).openConnection(); h.setConnectTimeout(12000);h.setReadTimeout(12000);h.setRequestProperty("Accept","application/json");h.setRequestProperty("User-Agent","Mozilla/5.0 AliLienWidget/0.3"); int code=h.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code); try(InputStream in=h.getInputStream()){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[4096];int n;while((n=in.read(x))>0)b.write(x,0,n);return b.toString(StandardCharsets.UTF_8.name());}}
    private static double find(Object node,String...keys){try{if(node instanceof JSONObject){JSONObject o=(JSONObject)node;for(String k:keys)if(o.has(k)&&!o.isNull(k)){Object v=o.opt(k);if(v instanceof Number)return((Number)v).doubleValue();try{return Double.parseDouble(String.valueOf(v).replace(",","."));}catch(Exception ignored){}}Iterator<String>it=o.keys();while(it.hasNext()){double v=find(o.opt(it.next()),keys);if(!Double.isNaN(v))return v;}}else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++){double v=find(a.opt(i),keys);if(!Double.isNaN(v))return v;}}}catch(Exception ignored){}return Double.NaN;}
    private static String hash(double v){if(Double.isNaN(v)||v<0)return"--";if(v>=1e9)return String.format(Locale.US,"%.2f GH/s",v/1e9);if(v>=1e6)return String.format(Locale.US,"%.2f MH/s",v/1e6);if(v>=1e3)return String.format(Locale.US,"%.2f kH/s",v/1e3);return String.format(Locale.US,"%.2f GH/s",v);}
    private static String money(double v){return Double.isNaN(v)?"$--":String.format(Locale.US,"$%.3f",v);} private static String whole(double v){return Double.isNaN(v)?"--":String.format(Locale.US,"%.0f",v);} private static String dagText(double v){if(Double.isNaN(v))return"-- GB";if(v>100)return String.format(Locale.US,"%.2f GB",v/1024.0);return String.format(Locale.US,"%.2f GB",v);} private static String shortId(String s){return s.length()>10?s.substring(0,5)+"…"+s.substring(s.length()-4):s;}
}
