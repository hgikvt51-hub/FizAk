package com.hgikvt.fizak;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class GeminiAI {
    private GeminiAI() {}
    public static String summarize(String transcript,String apiKey) throws Exception {
        if(apiKey==null||apiKey.trim().isEmpty())throw new IOException("Добавь Gemini API-ключ в настройках");
        if(transcript==null||transcript.trim().length()<80)throw new IOException("Для сводки нужно больше распознанного текста");
        String input=transcript.length()>60000?transcript.substring(0,60000):transcript;
        JSONObject part=new JSONObject(); part.put("text","Составь короткую, точную сводку лекции на русском языке.\n"+
                "Формат: 3–7 пунктов с главными идеями; отдельно добавь определения, формулы, выводы и задания, только если они есть в исходном тексте.\n"+
                "Не выдумывай факты и не повторяй ошибки распознавания как уверенные сведения. Если фрагмент неясен, пометь это.\n\nРАСШИФРОВКА:\n"+input);
        JSONArray parts=new JSONArray().put(part); JSONObject content=new JSONObject().put("parts",parts); JSONArray contents=new JSONArray().put(content);
        JSONObject generation=new JSONObject().put("temperature",0.2).put("maxOutputTokens",700);
        JSONObject request=new JSONObject().put("contents",contents).put("generationConfig",generation);
        HttpURLConnection conn=(HttpURLConnection)new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent").openConnection();
        conn.setRequestMethod("POST"); conn.setConnectTimeout(12000); conn.setReadTimeout(60000); conn.setDoOutput(true); conn.setRequestProperty("Content-Type","application/json; charset=utf-8"); conn.setRequestProperty("x-goog-api-key",apiKey.trim());
        try(OutputStream out=conn.getOutputStream()){out.write(request.toString().getBytes(StandardCharsets.UTF_8));}
        int status=conn.getResponseCode(); InputStream stream=status>=200&&status<300?conn.getInputStream():conn.getErrorStream(); String response;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder();String line;while((line=reader.readLine())!=null)b.append(line);response=b.toString();}
        finally {conn.disconnect();}
        if(status<200||status>=300)throw new IOException(status==401||status==403?"Ключ Gemini не принят. Проверь ключ и доступность API.":"Gemini не смог обработать запрос (HTTP "+status+")");
        try {JSONObject root=new JSONObject(response);JSONArray candidates=root.optJSONArray("candidates");if(candidates==null||candidates.length()==0)throw new IOException("Gemini не вернул сводку");JSONObject cont=candidates.getJSONObject(0).optJSONObject("content");JSONArray outParts=cont==null?null:cont.optJSONArray("parts");if(outParts==null)throw new IOException("Gemini вернул пустой ответ");StringBuilder text=new StringBuilder();for(int i=0;i<outParts.length();i++){String s=outParts.optJSONObject(i)==null?null:outParts.getJSONObject(i).optString("text",null);if(s!=null){if(text.length()>0)text.append("\n");text.append(s);}}if(text.length()==0)throw new IOException("Gemini вернул пустую сводку");return text.toString().trim();}
        catch(JSONException e){throw new IOException("Не удалось прочитать ответ Gemini");}
    }
}
