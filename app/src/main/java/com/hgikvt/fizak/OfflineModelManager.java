package com.hgikvt.fizak;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.util.zip.*;

public final class OfflineModelManager {
    private static final String URL="https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip";
    private static final long MAX_DOWNLOAD=60L*1024*1024, MAX_UNPACKED=220L*1024*1024;
    private OfflineModelManager() {}
    public interface Listener { void progress(int percent); void complete(File modelDirectory); void error(String message); }
    public static File modelDir(Context c){ return new File(new File(c.getFilesDir(),"vosk"),"model-ru"); }
    public static boolean isInstalled(Context c){ File d=modelDir(c); return new File(d,"am/final.mdl").isFile() && new File(d,"conf/mfcc.conf").isFile(); }

    public static void download(Context context,Listener listener){
        File base=new File(context.getFilesDir(),"vosk"); File archive=new File(base,"model.zip"); File staging=new File(base,"staging");
        try {
            if(!base.exists()&&!base.mkdirs())throw new IOException("Не удалось подготовить папку модели");
            if(staging.exists())deleteTree(staging); if(!staging.mkdirs())throw new IOException("Не удалось создать временную папку модели");
            HttpURLConnection conn=(HttpURLConnection)new URL(URL).openConnection(); conn.setConnectTimeout(20000); conn.setReadTimeout(30000); conn.setRequestProperty("User-Agent","FizAk-Android"); conn.connect();
            int code=conn.getResponseCode(); if(code<200||code>=300)throw new IOException("Сервер модели ответил кодом "+code);
            long expected=conn.getContentLengthLong(); if(expected>MAX_DOWNLOAD)throw new IOException("Размер модели превышает безопасный лимит");
            long total=0; byte[] buf=new byte[65536];
            try(InputStream in=conn.getInputStream(); FileOutputStream out=new FileOutputStream(archive)){
                int n; while((n=in.read(buf))!=-1){ total+=n; if(total>MAX_DOWNLOAD)throw new IOException("Загружаемый файл больше 60 МБ"); out.write(buf,0,n); if(expected>0)listener.progress((int)Math.min(100,total*100/expected)); }
            } finally { conn.disconnect(); }
            if(total<1024*1024)throw new IOException("Архив модели загружен не полностью");
            long unpacked=0; int entries=0; String basePath=staging.getCanonicalPath()+File.separator;
            try(ZipInputStream zin=new ZipInputStream(new BufferedInputStream(new FileInputStream(archive)))){
                ZipEntry entry; while((entry=zin.getNextEntry())!=null){
                    if(++entries>20000)throw new IOException("В архиве слишком много файлов");
                    String name=entry.getName().replace('\\','/'); if(name.startsWith("/")||name.contains("../")||name.equals(".."))throw new IOException("Архив содержит небезопасный путь");
                    File outFile=new File(staging,name); String canonical=outFile.getCanonicalPath(); if(!canonical.startsWith(basePath))throw new IOException("Архив выходит за пределы папки приложения");
                    if(entry.isDirectory()){if(!outFile.isDirectory()&&!outFile.mkdirs())throw new IOException("Не удалось распаковать модель");}
                    else {File parent=outFile.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не удалось распаковать модель");try(FileOutputStream out=new FileOutputStream(outFile)){int n;while((n=zin.read(buf))!=-1){unpacked+=n;if(unpacked>MAX_UNPACKED)throw new IOException("Распакованная модель превышает безопасный лимит");out.write(buf,0,n);}}}
                    zin.closeEntry();
                }
            }
            File candidate=new File(staging,"vosk-model-small-ru-0.22");
            if(!new File(candidate,"am/final.mdl").isFile()||!new File(candidate,"conf/mfcc.conf").isFile())throw new IOException("В архиве не найдена целая русская модель Vosk");
            File destination=modelDir(context); if(destination.exists())deleteTree(destination);
            if(!candidate.renameTo(destination))throw new IOException("Не удалось установить модель");
            deleteTree(staging); archive.delete(); listener.complete(destination);
        } catch(Exception e){ deleteTree(staging); archive.delete(); listener.error(e.getMessage()==null?"Не удалось скачать модель":e.getMessage()); }
    }
    private static void deleteTree(File f){ if(f==null||!f.exists())return; File[] children=f.listFiles(); if(children!=null)for(File child:children)deleteTree(child); try{f.delete();}catch(Exception ignored){} }
}
