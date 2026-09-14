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
        RemoteViews rv=new RemoteViews(c.getPackageName(),R.layout.widget_ethw);
        String account=c.getSharedPreferences("widget",Context.MODE_PRIVATE).getString("id_"+id,"").trim();
        try {
            if(account.isEmpty()){ rv.setTextViewText(R.id.status,"NO K1POOL ID • TAP ⚙"); rv.setViewVisibility(R.id.status,View.VISIBLE); finish(c,id,rv); return; }
            JSONObject root=new JSONObject(get("https://k1pool.com/api/miner/ethw/"+URLEncoder.encode(account,"UTF-8")));
            JSONObject miner=root.optJSONObject("miner"); JSONObject pool=root.optJSONObject("pool");
            if(miner==null) throw new JSONException("miner missing");
            double cur=num(miner,"curHashrate"); double h3=num(miner,"avgHashrate"); double h24=num(miner,"dayHashrate");
            double ethwDay=num(miner,"coinsPerDay");
            double price=pool==null?Double.NaN:num(pool,"coinPriceUsd");
            double change=pool==null?Double.NaN:first(pool,"coinPriceChange24h","priceChange24h","coinPriceChange");
            double epoch=pool==null?Double.NaN:first(pool,"epoch","currentEpoch");
            double dag=pool==null?Double.NaN:first(pool,"dagSize","dag","dagSizeMb");
            double height=pool==null?Double.NaN:first(pool,"blockHeight","height","blockchainHeight");
            double revenue1gh=pool==null?Double.NaN:first(pool,"coinsPerDayPerGh","coinPerDayPerGh","revenuePerGh","oneGhRevenue");
            if(Double.isNaN(ethwDay)&&!Double.isNaN(revenue1gh)&&!Double.isNaN(h3)) ethwDay=revenue1gh*(h3/1e9);
            double usdtDay=(!Double.isNaN(ethwDay)&&!Double.isNaN(price))?ethwDay*price:Double.NaN;
            rv.setTextViewText(R.id.hashrate,"K1POOL  "+hash(cur));
            rv.setTextViewText(R.id.averages,"30m CURRENT  •  3h "+hash(h3)+"  •  24h "+hash(h24));
            rv.setTextViewText(R.id.price,"ETHW  "+money(price)+(Double.isNaN(change)?"":String.format(Locale.US,"  %+.2f%%",change)));
            rv.setTextViewText(R.id.income,(Double.isNaN(ethwDay)?"--":String.format(Locale.US,"%.4f",ethwDay))+" ETHW/day  •  "+money(usdtDay)+"/day  •  "+money(Double.isNaN(usdtDay)?Double.NaN:usdtDay*30)+"/month");
            if(Double.isNaN(epoch)&&!Double.isNaN(height)) epoch=Math.floor(height/30000.0);
            String next=nextEpoch(height);
            rv.setTextViewText(R.id.epoch,"EPOCH "+whole(epoch)+"  •  DAG "+dagText(dag)+"  •  NEXT "+next);
            boolean coreOk=!Double.isNaN(cur)&&!Double.isNaN(h3)&&!Double.isNaN(h24);
            rv.setTextViewText(R.id.status,(coreOk?"SIGNAL LOCKED":"PARTIAL DATA")+" • "+shortId(account));
            rv.setViewVisibility(R.id.status,View.VISIBLE);
            rv.setTextViewText(R.id.updated,"UPDATED "+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()));
        }catch(Exception e){ rv.setTextViewText(R.id.status,"K1POOL ERROR • TAP ⚙ / ↻"); rv.setViewVisibility(R.id.status,View.VISIBLE); rv.setTextViewText(R.id.updated,"ERROR "+new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date())); }
        finish(c,id,rv);
    }
    private static void finish(Context c,int id,RemoteViews rv){ AppWidgetManager.getInstance(c).updateAppWidget(id,rv); }
    private static String get(String u)throws Exception{ HttpURLConnection h=(HttpURLConnection)new URL(u).openConnection(); h.setConnectTimeout(12000);h.setReadTimeout(12000);h.setRequestProperty("Accept","application/json");h.setRequestProperty("User-Agent","Mozilla/5.0 AliLienWidget/0.4"); int code=h.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code); try(InputStream in=h.getInputStream()){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[4096];int n;while((n=in.read(x))>0)b.write(x,0,n);return b.toString(StandardCharsets.UTF_8.name());}}
    private static double num(JSONObject o,String k){if(o==null||!o.has(k)||o.isNull(k))return Double.NaN;Object v=o.opt(k);if(v instanceof Number)return((Number)v).doubleValue();try{return Double.parseDouble(String.valueOf(v).replace(",","."));}catch(Exception e){return Double.NaN;}}
    private static double first(JSONObject o,String...ks){for(String k:ks){double v=num(o,k);if(!Double.isNaN(v))return v;}return Double.NaN;}
    private static String hash(double v){if(Double.isNaN(v)||v<0)return"--";if(v>=1e9)return String.format(Locale.US,"%.2f GH/s",v/1e9);if(v>=1e6)return String.format(Locale.US,"%.2f MH/s",v/1e6);if(v>=1e3)return String.format(Locale.US,"%.2f kH/s",v/1e3);return String.format(Locale.US,"%.0f H/s",v);}
    private static String money(double v){return Double.isNaN(v)?"$--":String.format(Locale.US,v<1?"$%.3f":"$%.2f",v);}
    private static String whole(double v){return Double.isNaN(v)?"--":String.format(Locale.US,"%.0f",v);}
    private static String dagText(double v){if(Double.isNaN(v))return"--";if(v>1000000000)return String.format(Locale.US,"%.2f GB",v/1073741824.0);if(v>100)return String.format(Locale.US,"%.2f GB",v/1024.0);return String.format(Locale.US,"%.2f GB",v);}
    private static String nextEpoch(double height){if(Double.isNaN(height))return"--";long h=(long)height;long rem=30000-(h%30000);return String.format(Locale.US,"%,d blocks",rem);}
    private static String shortId(String s){return s.length()>10?s.substring(0,5)+"…"+s.substring(s.length()-4):s;}
}
