package work.alilien.ethwwidget;

import android.app.Activity; import android.appwidget.AppWidgetManager; import android.content.Intent; import android.os.Bundle; import android.text.InputType; import android.view.ViewGroup; import android.widget.*;

public class SettingsActivity extends Activity {
 private int widgetId=AppWidgetManager.INVALID_APPWIDGET_ID;
 @Override public void onCreate(Bundle b){super.onCreate(b);widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);setResult(RESULT_CANCELED);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(40,50,40,30);root.setBackgroundColor(0xff050806);
  TextView title=new TextView(this);title.setText("ALI_LIEN // LV-426 TERMINAL");title.setTextColor(0xffbce9b3);title.setTextSize(22);root.addView(title);
  TextView help=new TextView(this);help.setText("K1Pool ETHW public account ID (Kr...). Пароль не нужен. ID можно изменить в любой момент кнопкой ⚙ на виджете.");help.setTextColor(0xff829d83);help.setPadding(0,24,0,12);root.addView(help);
  EditText id=new EditText(this);id.setSingleLine(true);id.setHint("Kr...");id.setTextColor(0xffd6f0d2);id.setHintTextColor(0xff607463);id.setInputType(InputType.TYPE_CLASS_TEXT);String old=getSharedPreferences("widget",MODE_PRIVATE).getString("id_"+widgetId,"");id.setText(old);root.addView(id,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
  Button save=new Button(this);save.setText("CONNECT TO K1POOL");root.addView(save);save.setOnClickListener(v->{String value=id.getText().toString().trim();if(!value.startsWith("Kr")||value.length()<20){Toast.makeText(this,"Проверьте K1Pool ID",Toast.LENGTH_SHORT).show();return;}getSharedPreferences("widget",MODE_PRIVATE).edit().putString("id_"+widgetId,value).apply();EthwWidgetProvider.update(this,widgetId,true);Intent r=new Intent();r.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);setResult(RESULT_OK,r);finish();});setContentView(root);
 }
}
