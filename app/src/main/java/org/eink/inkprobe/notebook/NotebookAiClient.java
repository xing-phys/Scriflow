package org.eink.inkprobe.notebook;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.*;

/** Direct HTTPS requests; no redirects, background retries, logs, or notebook attachments. */
final class NotebookAiClient {
    static final String SYSTEM="You are a helpful assistant in Scriflow, a notebook app. Answer in well-structured Markdown. "
        +"Use headings, lists, tables and fenced code blocks where useful. Use $...$ for inline math and $$...$$ for display math. "
        +"Match the user's language. You can only see the messages in this chat; you cannot see their notebooks or PDFs.";
    interface Connections { HttpURLConnection open(URL url) throws IOException; }
    static final class Cancellation {
        private volatile boolean cancelled;
        private HttpURLConnection connection;
        synchronized void attach(HttpURLConnection value) throws IOException {
            if (cancelled) { value.disconnect(); throw new InterruptedIOException("AI request stopped"); }
            connection=value;
        }
        void cancel() {
            cancelled=true;
            final HttpURLConnection value;
            synchronized (this) { value=connection; connection=null; }
            if (value!=null) new Thread(value::disconnect,"ai-disconnect").start();
        }
        void check() throws InterruptedIOException { if (cancelled) throw new InterruptedIOException("AI request stopped"); }
        synchronized void release() { connection=null; }
    }
    static final class Reply {
        final String text; final boolean truncated;
        Reply(String text,boolean truncated) { this.text=text; this.truncated=truncated; }
    }
    private final Connections connections;
    NotebookAiClient() { this(url->(HttpURLConnection)url.openConnection()); }
    NotebookAiClient(Connections connections) { this.connections=connections; }

    Reply complete(NotebookAiConfig config,List<NotebookAiConversation.Message> history,Cancellation cancellation) throws IOException {
        config.validate(); cancellation.check();
        if (history.isEmpty() || !history.get(history.size()-1).role.equals("user")) throw new IOException("No message to send");
        HttpURLConnection connection=connections.open(new URL(config.endpoint()));
        try {
            cancellation.attach(connection);
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(20000); connection.setReadTimeout(180000);
            connection.setUseCaches(false); connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("Accept","application/json");
            if (config.provider==NotebookAiConfig.Provider.ANTHROPIC) {
                connection.setRequestProperty("Authorization","Bearer "+config.apiKey);
                connection.setRequestProperty("anthropic-version","2026-10-21");
            } else if (!config.apiKey.isEmpty()) connection.setRequestProperty("Authorization","Bearer "+config.apiKey);
            byte[] body=NotebookAiConversation.bytes(request(config,history).toString());
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out=connection.getOutputStream()) { cancellation.check(); out.write(body); }
            cancellation.check(); int status=connection.getResponseCode();
            if (status>=300 && status<400) throw new IOException("Provider redirected the request. Update the API base URL in Settings.");
            if (status<200 || status>=300) {
                // Provider bodies can echo credentials or prompts. Keep failures local and bounded.
                String message=status==401||status==403?"Check your API key and model access in Settings."
                    :status==429?"Provider rate or credit limit reached. Wait, then Retry."
                    :status>=500?"Provider is temporarily unavailable. Try again later."
                    :"Check the API base URL, model ID, and provider configuration in Settings.";
                throw new IOException("AI provider returned HTTP "+status+". "+message);
            }
            try (InputStream in=connection.getInputStream()) { return response(config,read(in,cancellation)); }
        } catch (JSONException error) { throw new IOException("Provider returned an invalid chat response",error); }
        finally { cancellation.release(); connection.disconnect(); }
    }
    static JSONObject request(NotebookAiConfig config,List<NotebookAiConversation.Message> history) throws JSONException {
        boolean anthropic=config.provider==NotebookAiConfig.Provider.ANTHROPIC;
        JSONObject request=new JSONObject().put("model",config.model).put("stream",false);
        JSONArray messages=new JSONArray();
        if (anthropic) request.put("system",SYSTEM).put("max_tokens",8192);
        else {
            messages.put(new JSONObject().put("role","system").put("content",SYSTEM));
            if (config.provider==NotebookAiConfig.Provider.OPENAI) request.put("store",false).put("max_completion_tokens",16384);
            else request.put("max_tokens",8192);
        }
        for (NotebookAiConversation.Message message:history)
            messages.put(new JSONObject().put("role",message.role).put("content",message.content));
        return request.put("messages",messages);
    }
    static Reply response(NotebookAiConfig config,String data) throws IOException,JSONException {
        JSONObject response=new JSONObject(data); String text,reason;
        if (config.provider==NotebookAiConfig.Provider.ANTHROPIC) {
            text=textParts(response.getJSONArray("content")); reason=response.optString("stop_reason");
        } else {
            JSONObject choice=response.getJSONArray("choices").getJSONObject(0), message=choice.getJSONObject("message");
            Object content=message.opt("content");
            text=content instanceof String?(String)content:content instanceof JSONArray?textParts((JSONArray)content):"";
            if (text.trim().isEmpty()) text=message.optString("refusal","");
            reason=choice.optString("finish_reason");
        }
        if (text.trim().isEmpty()) throw new IOException("Provider returned no answer. Retry or choose another model.");
        if (NotebookAiConversation.bytes(text).length>NotebookAiConversation.MAX_MESSAGE_BYTES)
            throw new IOException("AI reply exceeds 128 KiB. Start a new chat with a shorter request.");
        return new Reply(text,reason.equals("length")||reason.equals("max_tokens"));
    }
    private static String textParts(JSONArray blocks) throws JSONException {
        StringBuilder text=new StringBuilder();
        for (int i=0;i<blocks.length();i++) {
            JSONObject block=blocks.getJSONObject(i);
            if (block.optString("type").equals("text")) text.append(block.optString("text"));
        }
        return text.toString();
    }
    private static String read(InputStream in,Cancellation cancellation) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int count;
        while ((count=in.read(buffer))!=-1) {
            cancellation.check();
            if (out.size()+count>2*1024*1024) throw new IOException("AI response exceeds its size limit");
            out.write(buffer,0,count);
        }
        cancellation.check(); return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
}
