package com.hgikvt.fizak;

import android.app.*;
import android.content.*;
import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
import android.os.*;
import java.io.File;

public class RecorderService extends Service {
    public static final String START="fizak.START", STOP="fizak.STOP", STATE="fizak.STATE";
    public static final String EXTRA_PATH="path", EXTRA_ACTIVE="active", EXTRA_ERROR="error";
    private MediaRecorder recorder;
    private NoiseSuppressor suppressor;
    private String currentPath;
    private static final int NOTIFICATION_ID=41;
    @Override public void onCreate(){ super.onCreate(); createChannel(); }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null) return START_NOT_STICKY;
        if(STOP.equals(intent.getAction())) stopRecording();
        else if(START.equals(intent.getAction())) startRecording(intent.getStringExtra(EXTRA_PATH));
        return START_NOT_STICKY;
    }
    private void startRecording(String path){
        if(recorder!=null) return;
        try {
            currentPath=path;
            File f=new File(path); File parent=f.getParentFile(); if(parent!=null) parent.mkdirs();
            if(Build.VERSION.SDK_INT>=31) recorder=new MediaRecorder(this); else recorder=new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(48000); recorder.setAudioEncodingBitRate(128000);
            recorder.setOutputFile(path); recorder.prepare(); recorder.start();
            int session=recorder.getAudioSessionId();
            if(NoiseSuppressor.isAvailable()) { suppressor=NoiseSuppressor.create(session); if(suppressor!=null) suppressor.setEnabled(true); }
            startForeground(NOTIFICATION_ID,notification("Идёт запись лекции"));
            broadcast(true,path,null);
        } catch(Exception e){ release(); broadcast(false,path,e.getMessage()==null?"Не удалось начать запись":e.getMessage()); stopSelf(); }
    }
    private void stopRecording(){
        String finished=currentPath;
        try { if(recorder!=null) recorder.stop(); } catch(Exception ignored) { if(finished!=null) new File(finished).delete(); finished=null; }
        release();
        try { stopForeground(STOP_FOREGROUND_REMOVE); } catch(Exception ignored) { stopForeground(true); }
        broadcast(false,finished,null); stopSelf();
    }
    private void broadcast(boolean active,String path,String error){
        Intent i=new Intent(STATE); i.setPackage(getPackageName()); i.putExtra(EXTRA_ACTIVE,active);
        if(path!=null) i.putExtra(EXTRA_PATH,path); if(error!=null) i.putExtra(EXTRA_ERROR,error); sendBroadcast(i);
    }
    private void release(){
        if(suppressor!=null){ try{suppressor.setEnabled(false); suppressor.release();}catch(Exception ignored){} suppressor=null; }
        if(recorder!=null){ try{recorder.reset();}catch(Exception ignored){} try{recorder.release();}catch(Exception ignored){} recorder=null; }
    }
    private Notification notification(String text){
        Intent open=new Intent(this,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"recording").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("FizAk").setContentText(text).setContentIntent(pi).setOngoing(true).build();
    }
    private void createChannel(){ if(Build.VERSION.SDK_INT>=26){ NotificationChannel c=new NotificationChannel("recording","Запись аудио",NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager.class).createNotificationChannel(c); } }
    @Override public void onDestroy(){ if(recorder!=null) stopRecording(); super.onDestroy(); }
    @Override public android.os.IBinder onBind(Intent intent){ return null; }
}
