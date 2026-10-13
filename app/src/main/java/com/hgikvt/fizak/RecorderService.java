package com.hgikvt.fizak;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.audiofx.NoiseSuppressor;
import android.os.*;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class RecorderService extends Service {
    public static final String START="fizak.START", STOP="fizak.STOP", STATE="fizak.STATE";
    public static final String EXTRA_PATH="path", EXTRA_ACTIVE="active", EXTRA_ERROR="error", EXTRA_TEXT="text", EXTRA_PARTIAL="partial", EXTRA_AI="ai";
    private static final int NOTIFICATION_ID=41, SAMPLE_RATE=16000;
    private AudioRecord audio; private NoiseSuppressor suppressor; private Model model; private Recognizer recognizer;
    private FileOutputStream wave; private String currentPath=""; private volatile boolean running=false;
    private Thread captureThread; private final StringBuilder transcript=new StringBuilder();

    @Override public void onCreate(){super.onCreate();createChannel();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent==null)return START_NOT_STICKY;if(STOP.equals(intent.getAction()))stopRecording();else if(START.equals(intent.getAction()))startRecording(intent.getStringExtra(EXTRA_PATH));return START_NOT_STICKY;}

    private void startRecording(String path){
        if(running)return; currentPath=path==null?"":path; transcript.setLength(0);
        try {
            if(currentPath.isEmpty())throw new IOException("Не задан файл записи");
            startForeground(NOTIFICATION_ID,notification("Идёт запись · WAV 16 кГц"));
            File f=new File(currentPath);File parent=f.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Не удалось создать папку записи");
            wave=new FileOutputStream(f);wave.write(new byte[44]);
            File modelDir=OfflineModelManager.modelDir(this);
            if(OfflineModelManager.isInstalled(this)){model=new Model(modelDir.getAbsolutePath());recognizer=new Recognizer(model,(float)SAMPLE_RATE);}
            int min=AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IOException("Телефон не поддерживает запись с нужной частотой");
            int size=Math.max(min*2,8192);
            AudioFormat format=new AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build();
            audio=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION).setAudioFormat(format).setBufferSizeInBytes(size).build();
            if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("Не удалось открыть микрофон");
            if(NoiseSuppressor.isAvailable()){try{suppressor=NoiseSuppressor.create(audio.getAudioSessionId());if(suppressor!=null)suppressor.setEnabled(true);}catch(Exception ignored){suppressor=null;}}
            audio.startRecording(); running=true; captureThread=new Thread(()->captureLoop(size),"FizAk-Audio");captureThread.start();
            broadcast(true,currentPath,null,null,null,recognizer!=null);
        }catch(Exception e){running=false;releaseAudio();closeWave();if(!currentPath.isEmpty())new File(currentPath).delete();String message=e.getMessage()==null?"Не удалось начать запись":e.getMessage();broadcast(false,currentPath,message,null,null,false);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    }
    private void captureLoop(int bufferSize){
        long written=0;byte[] bytes=new byte[bufferSize];
        try {
            while(running){int n=audio.read(bytes,0,bytes.length,AudioRecord.READ_BLOCKING);if(n<0){if(!running)break;throw new IOException("Ошибка чтения микрофона");}if(n==0)continue;
                wave.write(bytes,0,n);written+=n;
                if(recognizer!=null){if(recognizer.acceptWaveForm(bytes,n)){sendRecognized(parseText(recognizer.getResult()));}else{String partial=parseField(recognizer.getPartialResult(),"partial");if(!partial.isEmpty())broadcast(true,currentPath,null,null,partial,true);}}
            }
            if(recognizer!=null)sendRecognized(parseText(recognizer.getFinalResult()));
        }catch(Exception e){if(running)broadcast(true,currentPath,"Распознавание остановилось: "+(e.getMessage()==null?"ошибка":e.getMessage()),null,null,recognizer!=null);}
        finally {try{if(wave!=null){wave.flush();wave.close();wave=null;}}catch(Exception ignored){} releaseAudio(); closeRecognizer(); writeWaveHeader(new File(currentPath),written); saveTranscript();}
    }
    private void sendRecognized(String part){if(part==null||part.trim().isEmpty())return;String clean=part.trim();if(transcript.length()>0)transcript.append(' ');transcript.append(clean);broadcast(true,currentPath,null,clean,null,true);}
    private String parseText(String raw){return parseField(raw,"text");}
    private String parseField(String raw,String key){try{return new JSONObject(raw).optString(key,"").trim();}catch(Exception ignored){return "";}}

    private void stopRecording(){
        if(!running){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return;}
        running=false;
        try{if(audio!=null&&audio.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)audio.stop();}catch(Exception ignored){}
        if(captureThread!=null&&captureThread!=Thread.currentThread()){try{captureThread.join(5000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        String path=currentPath;String text=transcript.toString();
        try{stopForeground(STOP_FOREGROUND_REMOVE);}catch(Exception ignored){stopForeground(true);}
        broadcast(false,path,null,text,null,recognizer!=null);stopSelf();
    }
    private void saveTranscript(){try{File f=new File(currentPath);String name=f.getName();if(name.endsWith(".wav"))name=name.substring(0,name.length()-4)+".txt";File t=new File(f.getParentFile(),name);try(FileOutputStream o=new FileOutputStream(t)){o.write(transcript.toString().getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}}
    private void writeWaveHeader(File file,long dataBytes){try(RandomAccessFile raf=new RandomAccessFile(file,"rw")){raf.seek(0);raf.writeBytes("RIFF");writeIntLE(raf,(int)(dataBytes+36));raf.writeBytes("WAVE");raf.writeBytes("fmt ");writeIntLE(raf,16);writeShortLE(raf,1);writeShortLE(raf,1);writeIntLE(raf,SAMPLE_RATE);writeIntLE(raf,SAMPLE_RATE*2);writeShortLE(raf,2);writeShortLE(raf,16);raf.writeBytes("data");writeIntLE(raf,(int)dataBytes);}catch(Exception ignored){}}
    private void writeIntLE(RandomAccessFile f,int v)throws IOException{f.write(v&255);f.write((v>>8)&255);f.write((v>>16)&255);f.write((v>>24)&255);}
    private void writeShortLE(RandomAccessFile f,int v)throws IOException{f.write(v&255);f.write((v>>8)&255);}
    private void releaseAudio(){if(suppressor!=null){try{suppressor.setEnabled(false);suppressor.release();}catch(Exception ignored){}suppressor=null;}if(audio!=null){try{audio.release();}catch(Exception ignored){}audio=null;}}
    private void closeRecognizer(){if(recognizer!=null){try{recognizer.close();}catch(Exception ignored){}recognizer=null;}if(model!=null){try{model.close();}catch(Exception ignored){}model=null;}}
    private void closeWave(){if(wave!=null){try{wave.close();}catch(Exception ignored){}wave=null;}}
    private void broadcast(boolean active,String path,String error,String text,String partial,boolean ai){Intent i=new Intent(STATE);i.setPackage(getPackageName());i.putExtra(EXTRA_ACTIVE,active);i.putExtra(EXTRA_AI,ai);if(path!=null)i.putExtra(EXTRA_PATH,path);if(error!=null)i.putExtra(EXTRA_ERROR,error);if(text!=null)i.putExtra(EXTRA_TEXT,text);if(partial!=null)i.putExtra(EXTRA_PARTIAL,partial);sendBroadcast(i);}
    private Notification notification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Intent stop=new Intent(this,RecorderService.class).setAction(STOP);PendingIntent stopPi=PendingIntent.getService(this,2,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);return new Notification.Builder(this,"recording").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("FizAk").setContentText(text).setContentIntent(pi).setOngoing(true).addAction(android.R.drawable.ic_media_pause,"Остановить",stopPi).build();}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("recording","Запись аудио",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    @Override public void onDestroy(){if(running)stopRecording();else{releaseAudio();closeRecognizer();closeWave();}super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
