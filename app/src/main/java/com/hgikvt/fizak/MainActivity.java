package com.hgikvt.fizak;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.*;
import android.speech.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private final int BG=Color.rgb(11,16,20), PANEL=Color.rgb(20,28,34), MINT=Color.rgb(100,230,177), WHITE=Color.rgb(238,244,245), MUTED=Color.rgb(147,164,171), RED=Color.rgb(255,105,112);
    private LinearLayout root, body, transcriptBox;
    private TextView timer, status, liveText;
    private Button recordButton;
    private boolean recording=false, repoMissing=false;
    private long startedAt=0;
    private String activePath="", transcript="";
    private final Handler handler=new Handler();
    private SpeechRecognizer speech;
    private Intent speechIntent;
    private boolean recognitionStarting=false;
    private String pendingExport="";
    private final ArrayList<JSONObject> items=new ArrayList<>();
    private static final int AUDIO_PERMISSION=5;
    private final Runnable tick= new Runnable(){ public void run(){ if(recording){ long s=(System.currentTimeMillis()-startedAt)/1000; timer.setText(String.format(Locale.getDefault(),"%02d:%02d:%02d",s/3600,(s/60)%60,s%60)); handler.postDelayed(this,500); } }};
    private final BroadcastReceiver receiver=new BroadcastReceiver(){ @Override public void onReceive(Context c,Intent i){
        if(!RecorderService.STATE.equals(i.getAction()))return;
        String err=i.getStringExtra(RecorderService.EXTRA_ERROR);
        if(err!=null){ recording=false; updateRecordUI(); toast("Запись не началась: "+err); return; }
        boolean active=i.getBooleanExtra(RecorderService.EXTRA_ACTIVE,false);
        String p=i.getStringExtra(RecorderService.EXTRA_PATH);
        if(active){ recording=true; activePath=p; startedAt=System.currentTimeMillis(); updateRecordUI(); handler.post(tick); listenAgain(); }
        else { recording=false; if(p!=null && new File(p).exists()) addItem(p,transcript); stopSpeech(); updateRecordUI(); showLibrary(); }
    }};

    @Override public void onCreate(Bundle state){ super.onCreate(state); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG); loadItems(); buildShell(); showHome();
        IntentFilter f=new IntentFilter(RecorderService.STATE); if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(receiver,f);
        checkRepositoryAsync();
    }
    @Override protected void onDestroy(){ try{unregisterReceiver(receiver);}catch(Exception ignored){} stopSpeech(); super.onDestroy(); }
    private void buildShell(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG); root.setPadding(dp(20),dp(16),dp(20),dp(10)); setContentView(root);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); TextView brand=text("FizAk",26,WHITE,true); top.addView(brand,new LinearLayout.LayoutParams(0,-2,1)); Button settings=button("⚙",PANEL,WHITE); settings.setMinWidth(dp(44)); settings.setOnClickListener(v->showSettings()); top.addView(settings); root.addView(top);
        body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,0,1); bp.topMargin=dp(20); root.addView(body,bp);
        LinearLayout nav=new LinearLayout(this); nav.setGravity(Gravity.CENTER); nav.setPadding(0,dp(8),0,dp(4));
        Button home=button("●  Запись",PANEL,MINT), library=button("♫  Аудио",PANEL,WHITE), prefs=button("⚙  Настройки",PANEL,WHITE);
        home.setOnClickListener(v->showHome()); library.setOnClickListener(v->showLibrary()); prefs.setOnClickListener(v->showSettings());
        for(Button b:new Button[]{home,library,prefs}){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1); p.setMargins(dp(3),0,dp(3),0); nav.addView(b,p); } root.addView(nav);
    }
    private void showHome(){ body.removeAllViews();
        TextView title=text("Запиши важное",26,WHITE,true); body.addView(title); body.addView(text("Лекции, встречи и идеи — всё останется у тебя",14,MUTED,false));
        LinearLayout card=card(); card.setGravity(Gravity.CENTER); card.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.topMargin=dp(24); body.addView(card,cp);
        TextView mic=text("◉",48,recording?RED:MINT,true); card.addView(mic); timer=text(recording?"00:00:00":"00:00:00",38,WHITE,true); LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2); tp.topMargin=dp(8); card.addView(timer,tp);
        status=text(recording?"Запись идёт":"Готов к записи",14,recording?RED:MUTED,false); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2); sp.topMargin=dp(4); card.addView(status,sp);
        recordButton=button(recording?"■  Остановить запись":"●  Начать запись",recording?RED:MINT,BG); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(58)); rp.topMargin=dp(22); card.addView(recordButton,rp); recordButton.setOnClickListener(v->{ if(recording) stopRecording(); else requestAudio(); });
        TextView info=text("Аудио сохраняется на устройстве. Используется голосовой профиль микрофона Android; обработка шума зависит от телефона.",13,MUTED,false); info.setGravity(Gravity.CENTER); LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2); ip.topMargin=dp(15); card.addView(info,ip);
        LinearLayout live=card(); live.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1); lp.topMargin=dp(17); body.addView(live,lp);
        TextView head=text("ЖИВАЯ РАСШИФРОВКА",12,MINT,true); live.addView(head); ScrollView sc=new ScrollView(this); transcriptBox=new LinearLayout(this); transcriptBox.setOrientation(LinearLayout.VERTICAL); liveText=text(transcript.isEmpty()?"Здесь появятся распознанные слова во время записи.":transcript,15,WHITE,false); liveText.setGravity(Gravity.TOP); transcriptBox.addView(liveText); sc.addView(transcriptBox); LinearLayout.LayoutParams sp2=new LinearLayout.LayoutParams(-1,0,1); sp2.topMargin=dp(10); live.addView(sc,sp2);
        body.addView(text("Распознавание работает офлайн, если на устройстве установлена офлайн-модель русского языка; иначе Android может использовать сетевую службу.",12,MUTED,false));
    }
    private void requestAudio(){
        ArrayList<String> need=new ArrayList<>(); if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)need.add(Manifest.permission.RECORD_AUDIO);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)need.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!need.isEmpty()){ requestPermissions(need.toArray(new String[0]),AUDIO_PERMISSION); return; } startRecording();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){ super.onRequestPermissionsResult(r,p,g); if(r==AUDIO_PERMISSION && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) startRecording(); else toast("Разреши доступ к микрофону для записи."); }
    private void startRecording(){
        transcript=""; activePath=new File(getFilesDir(),"recordings/FizAk_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".m4a").getAbsolutePath();
        Intent i=new Intent(this,RecorderService.class).setAction(RecorderService.START).putExtra(RecorderService.EXTRA_PATH,activePath);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        startSpeech();
    }
    private void stopRecording(){ stopSpeech(); startService(new Intent(this,RecorderService.class).setAction(RecorderService.STOP)); }
    private void startSpeech(){
        try{
            if(!SpeechRecognizer.isRecognitionAvailable(this)){ status.setText("Запись идёт · распознавание недоступно"); return; }
            if(Build.VERSION.SDK_INT>=31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) speech=SpeechRecognizer.createOnDeviceSpeechRecognizer(this); else speech=SpeechRecognizer.createSpeechRecognizer(this);
            speechIntent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU"); speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true); speechIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true); speechIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
            speech.setRecognitionListener(new RecognitionListener(){
                public void onReadyForSpeech(Bundle b){} public void onBeginningOfSpeech(){} public void onRmsChanged(float r){} public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){}
                public void onError(int e){ recognitionStarting=false; if(recording) handler.postDelayed(()->listenAgain(),900); }
                public void onResults(Bundle b){ appendSpeech(b); if(recording) handler.postDelayed(()->listenAgain(),200); }
                public void onPartialResults(Bundle b){ showPartial(b); } public void onEvent(int t,Bundle b){}
            }); listenAgain();
        }catch(Exception e){ status.setText("Запись идёт · нет службы распознавания"); }
    }
    private void listenAgain(){ if(!recording || speech==null || recognitionStarting)return; try{ recognitionStarting=true; speech.startListening(speechIntent); handler.postDelayed(()->recognitionStarting=false,4000); }catch(Exception e){recognitionStarting=false;} }
    private void appendSpeech(Bundle b){ if(b==null)return; ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(r==null||r.isEmpty())return; String s=r.get(0).trim(); if(s.isEmpty())return;
        if(!transcript.isEmpty() && !transcript.endsWith(" ")) transcript+=" "; transcript+=s; if(liveText!=null) liveText.setText(transcript); recognitionStarting=false;
    }
    private void showPartial(Bundle b){ if(b==null||liveText==null)return; ArrayList<String> r=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(r!=null&&!r.isEmpty()) liveText.setText(transcript+(transcript.isEmpty()?"":" ")+r.get(0)); }
    private void stopSpeech(){ if(speech!=null){ try{speech.stopListening(); speech.destroy();}catch(Exception ignored){} speech=null; } recognitionStarting=false; }

    private void showLibrary(){ body.removeAllViews(); TextView title=text("Твои записи",26,WHITE,true); body.addView(title); TextView sub=text(items.size()+" аудиофайлов · хранятся только на телефоне",14,MUTED,false); body.addView(sub);
        ScrollView scroll=new ScrollView(this); LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list); LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,0,1); sl.topMargin=dp(16); body.addView(scroll,sl);
        if(items.isEmpty()){ LinearLayout empty=card(); empty.setOrientation(LinearLayout.VERTICAL); empty.addView(text("Пока записей нет",19,WHITE,true)); empty.addView(text("Нажми «Начать запись», чтобы сохранить лекцию или заметку.",14,MUTED,false)); list.addView(empty); }
        for(int x=items.size()-1;x>=0;x--){ final JSONObject item=items.get(x); String path=item.optString("path"); String name=item.optString("name",new File(path).getName()); LinearLayout c=card(); c.setOrientation(LinearLayout.VERTICAL); LinearLayout.LayoutParams cpar=new LinearLayout.LayoutParams(-1,-2); cpar.bottomMargin=dp(12); list.addView(c,cpar);
            c.addView(text(name,17,WHITE,true)); c.addView(text(item.optString("date","")+"   ·   "+duration(item.optLong("seconds",0)),13,MUTED,false)); LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setPadding(0,dp(10),0,0);
            Button play=button("▶ Слушать",MINT,BG), summary=button("✦ Сводка",PANEL,WHITE), share=button("Поделиться",PANEL,WHITE), more=button("⋯",PANEL,WHITE);
            for(Button b:new Button[]{play,summary,share,more}){ LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(42),1); a.setMargins(dp(2),0,dp(2),0); actions.addView(b,a); } c.addView(actions);
            play.setOnClickListener(v->play(path)); summary.setOnClickListener(v->showSummary(item)); share.setOnClickListener(v->share(path)); more.setOnClickListener(v->itemMenu(item));
        }
    }
    private void showSummary(JSONObject item){ String source=item.optString("transcript",""); if(source.trim().isEmpty()){ new AlertDialog.Builder(this).setTitle("Пока нет расшифровки").setMessage("Во время записи Android не распознал речь. Можно открыть текстовый файл после расшифровки на устройстве.").setPositiveButton("Понятно",null).show(); return; }
        String summary=summarize(source); new AlertDialog.Builder(this).setTitle("Краткая сводка · на устройстве").setMessage(summary).setPositiveButton("Скопировать",(d,w)->{ android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE); cm.setPrimaryClip(ClipData.newPlainText("Сводка",summary)); toast("Сводка скопирована"); }).setNegativeButton("Закрыть",null).show();
    }
    private String summarize(String text){ String[] s=text.split("(?<=[.!?])\\s+"); LinkedHashSet<String> chosen=new LinkedHashSet<>(); for(String sentence:s){ String t=sentence.trim(); if(t.length()>24 && (t.matches("(?is).*(важн|итог|поэтому|значит|во-первых|нужно|необходимо|вывод|основн|причин|результат|цель|задач|формул|определя).*" )||chosen.isEmpty())) chosen.add(t); if(chosen.size()>=4)break; } if(chosen.isEmpty()) return text.length()>600?text.substring(0,600)+"…":text; StringBuilder out=new StringBuilder(); for(String s2:chosen){if(out.length()>0)out.append("\n\n");out.append("• ").append(s2);} return out.toString(); }
    private void itemMenu(JSONObject item){ String[] opts={"Поделиться аудио","Экспортировать расшифровку","Удалить запись"}; new AlertDialog.Builder(this).setTitle(item.optString("name","Запись")).setItems(opts,(d,w)->{if(w==0)share(item.optString("path")); if(w==1)exportText(item); if(w==2)confirmDelete(item);}).show(); }
    private void confirmDelete(JSONObject item){ new AlertDialog.Builder(this).setTitle("Удалить запись?").setMessage("Аудио и сохранённая расшифровка будут удалены с устройства.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{new File(item.optString("path")).delete(); items.remove(item); saveItems(); showLibrary();}).show(); }
    private void addItem(String path,String text){ JSONObject o=new JSONObject(); try{o.put("path",path);o.put("name",new SimpleDateFormat("d MMM · HH:mm",new Locale("ru")).format(new Date()));o.put("date",new SimpleDateFormat("d MMM yyyy · HH:mm",new Locale("ru")).format(new Date()));o.put("seconds",Math.max(0,(System.currentTimeMillis()-startedAt)/1000));o.put("transcript",text==null?"":text);}catch(Exception ignored){} items.add(o);saveItems(); }
    private void play(String path){ try{ if(!new File(path).exists()){toast("Файл уже не найден");return;} MediaPlayer p=new MediaPlayer(); p.setDataSource(path); p.setOnCompletionListener(m->{m.release();toast("Воспроизведение завершено");}); p.setOnPreparedListener(MediaPlayer::start); p.prepareAsync(); }catch(Exception e){toast("Не удалось открыть аудио");} }
    private void share(String path){ try{File f=new File(path); if(!f.exists()){toast("Файл не найден");return;} Intent i=new Intent(Intent.ACTION_SEND); i.setType("audio/mp4"); i.putExtra(Intent.EXTRA_STREAM,androidxUri(f)); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i,"Поделиться записью")); }catch(Exception e){toast("Не удалось поделиться файлом");} }
    private android.net.Uri androidxUri(File f){ return androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".files",f); }
    private void exportText(JSONObject item){ String t=item.optString("transcript",""); if(t.trim().isEmpty()){toast("Расшифровка пуста");return;} pendingExport=t; Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TITLE,"FizAk_расшифровка.txt"); try{startActivityForResult(i,77);}catch(Exception e){shareText(t);} }
    @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data); if(r==77&&c==RESULT_OK&&data!=null){try(OutputStream o=getContentResolver().openOutputStream(data.getData())){o.write(pendingExport.getBytes("UTF-8"));toast("Текст сохранён");}catch(Exception e){toast("Не удалось сохранить текст");}}}
    private void shareText(String t){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,t);startActivity(Intent.createChooser(i,"Экспортировать текст"));}
    private void showSettings(){ body.removeAllViews(); body.addView(text("Настройки",26,WHITE,true)); body.addView(text("Приватность и работа FizAk",14,MUTED,false)); LinearLayout c=card();c.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(18);body.addView(c,cp);
        c.addView(text("Локальное хранение",17,MINT,true)); c.addView(text("Аудиозаписи не отправляются на сервер. При удалении приложения записи из его памяти тоже удалятся.",14,WHITE,false));
        c.addView(text("Расшифровка речи",17,MINT,true)); c.addView(text("FizAk использует распознавание речи Android. Офлайн-работа зависит от установленной языковой модели и поддержки телефона; без неё распознавание может потребовать интернет.",14,WHITE,false));
        c.addView(text("Обработка звука",17,MINT,true)); c.addView(text("Для записи выбран профиль микрофона Android, настроенный на распознавание голоса. Фильтрация шума и итоговое качество зависят от микрофона и производителя телефона.",14,WHITE,false));
        c.addView(text("Краткая сводка",17,MINT,true)); c.addView(text("Сводка формируется локально из распознанного текста — без облака. Проверь её перед использованием: автоматическое сокращение может пропускать детали.",14,WHITE,false));
        c.addView(text("FizAk · бесплатно · без аккаунта",13,MUTED,false));
    }
    private void updateRecordUI(){ if(recordButton!=null && root!=null){ showHome(); if(recording){status.setText("Запись идёт · не закрывай принудительно");} } }
    private void checkRepositoryAsync(){ new Thread(()->{ int code=-1; try{ HttpURLConnection c=(HttpURLConnection)new URL("https://api.github.com/repos/hgikvt51-hub/FizAk").openConnection(); c.setRequestMethod("GET");c.setConnectTimeout(3500);c.setReadTimeout(3500);c.setRequestProperty("Accept","application/vnd.github+json");code=c.getResponseCode();c.disconnect();}catch(Exception ignored){} if(code==404)runOnUiThread(this::repositoryRemoved); }).start(); }
    private void repositoryRemoved(){ if(isFinishing()||repoMissing)return;repoMissing=true;new AlertDialog.Builder(this).setTitle("Error").setMessage("Репозиторий FizAk удалён или больше недоступен. Приложение остановлено.").setCancelable(false).setPositiveButton("Удалить приложение",(d,w)->{Intent i=new Intent(Intent.ACTION_DELETE,android.net.Uri.parse("package:"+getPackageName()));startActivity(i);}).show(); }
    private void loadItems(){ try{JSONArray a=new JSONArray(getSharedPreferences("fizak",0).getString("items","[]"));for(int i=0;i<a.length();i++)items.add(a.getJSONObject(i));}catch(Exception ignored){} }
    private void saveItems(){JSONArray a=new JSONArray();for(JSONObject o:items)a.put(o);getSharedPreferences("fizak",0).edit().putString("items",a.toString()).apply();}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(18),dp(17),dp(18),dp(17));GradientDrawable g=new GradientDrawable();g.setColor(PANEL);g.setCornerRadius(dp(22));l.setBackground(g);return l;}
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setPadding(0,dp(5),0,dp(5));return t;}
    private Button button(String s,int fill,int color){Button b=new Button(this);b.setText(s);b.setTextColor(color);b.setTextSize(13);b.setAllCaps(false);GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(14));b.setBackground(d);b.setMinHeight(dp(42));b.setPadding(dp(8),0,dp(8),0);return b;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);} private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private String duration(long s){return String.format(Locale.getDefault(),"%02d:%02d",s/60,s%60);}
}
