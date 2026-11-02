package org.eink.inkprobe.notebook;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import org.json.*;

/** Direct HTTPS requests; only explicitly appended selections are included with chat text. */
final class NotebookAiClient {
    static final String SYSTEM="You are a helpful assistant in Scriflow, a notebook app. Answer in well-structured Markdown. "
        +"Use headings, lists, tables and fenced code blocks where useful. Use $...$ for inline math and $$...$$ for display math. "
        +"Match the user's language. You can see this chat and explicitly attached selections, but cannot see the rest of their notebooks or PDFs.";
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

    List<String> models(NotebookAiConfig config,Cancellation cancellation) throws IOException {
        List<String> ids=new ArrayList<>();for(NotebookAiModel model:catalog(config,cancellation,false))ids.add(model.id);return ids;
    }
    List<NotebookAiModel> catalog(NotebookAiConfig config,Cancellation cancellation)throws IOException{return catalog(config,cancellation,true);}
    private List<NotebookAiModel> catalog(NotebookAiConfig config,Cancellation cancellation,boolean nativeMetadata) throws IOException {
        config.validateConnection(); TreeMap<String,NotebookAiModel> result=new TreeMap<>(); String cursor="";
        boolean gemini=nativeMetadata&&config.provider==NotebookAiConfig.Provider.GEMINI&&config.modelsEndpoint().equals("https://generativelanguage.googleapis.com/v1beta/openai/models");
        for (int page=0;page<10;page++) {
            cancellation.check();
            String query=cursor.isEmpty()?"":"?"+(gemini?"pageToken":config.provider==NotebookAiConfig.Provider.ANTHROPIC?"after_id":"after")+"="+URLEncoder.encode(cursor,"UTF-8");
            HttpURLConnection connection=connections.open(new URL((gemini?"https://generativelanguage.googleapis.com/v1beta/models":config.modelsEndpoint())+query));
            try {
                cancellation.attach(connection); connection.setRequestMethod("GET"); connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(20000); connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept","application/json");
                if (!config.apiKey.isEmpty()) connection.setRequestProperty(gemini?"x-goog-api-key":"Authorization",gemini?config.apiKey:"Bearer "+config.apiKey);
                if (config.provider==NotebookAiConfig.Provider.ANTHROPIC) connection.setRequestProperty("anthropic-version","2026-10-21");
                int status=connection.getResponseCode();
                if (status>=300&&status<400) throw new IOException("Model list redirected. Enter the final API base URL.");
                if (status!=200) throw new IOException("Model fetch returned HTTP "+status+". Check your key and URL; you can also enter a model ID manually.");
                JSONObject response;
                try (InputStream in=connection.getInputStream()) { response=new JSONObject(read(in,cancellation)); }
                JSONArray data=response.getJSONArray(gemini?"models":"data");
                for (int i=0;i<data.length();i++) {
                    NotebookAiModel model=NotebookAiModel.provider(config.provider,data.getJSONObject(i));
                    result.put(model.id,model);
                    if (result.size()>1000) throw new IOException("Provider lists too many models. Enter a model ID manually.");
                }
                if (gemini?response.optString("nextPageToken","").isEmpty():!response.optBoolean("has_more",false)) {
                    if (result.isEmpty()) throw new IOException("No available models were returned for this key");
                    return new ArrayList<>(result.values());
                }
                String next=gemini?response.getString("nextPageToken"):response.optString("last_id",data.length()==0?"":data.getJSONObject(data.length()-1).getString("id"));
                if (next.isEmpty()||next.equals(cursor)) throw new IOException("Provider returned an invalid model-list cursor");
                cursor=next;
            } catch (JSONException|IllegalArgumentException error) { throw new IOException("Provider returned an invalid model list. Enter a model ID manually.",error); }
            finally { cancellation.release(); connection.disconnect(); }
        }
        throw new IOException("Model list exceeded its page limit. Enter a model ID manually.");
    }

    /** OpenAI's authenticated model list has no limits. Read its public documentation without the API key. */
    NotebookAiModel specifications(NotebookAiConfig config,NotebookAiModel model,Cancellation cancel)throws IOException {
        cancel.check();
        if(config.provider!=NotebookAiConfig.Provider.OPENAI||!config.modelsEndpoint().equals("https://api.openai.com/v1/models")||!model.id.matches("[a-zA-Z0-9._-]{1,120}"))return model;
        if(model.source.equals("OpenAI documentation")&&model.fetched>System.currentTimeMillis()-600000)return model;
        String id=model.id;
        for(int attempt=0;attempt<2;attempt++){
            cancel.check();HttpURLConnection connection=connections.open(new URL("https://developers.openai.com/api/docs/models/"+id+".md"));
            try {
                cancel.attach(connection);connection.setRequestMethod("GET");connection.setUseCaches(false);connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(10000);connection.setReadTimeout(10000);connection.setRequestProperty("Accept","text/markdown");
                int status=connection.getResponseCode();
                if(status==200)try(InputStream in=connection.getInputStream()){return NotebookAiModel.openAiDocument(model.id,read(in,cancel));}
                String alias=id.replaceFirst("-\\d{4}-\\d{2}-\\d{2}$","");
                if(status!=404||alias.equals(id))throw new IOException("Official specifications are unavailable for "+model.id);
                id=alias;
            } finally {cancel.release();connection.disconnect();}
        }
        return model;
    }

    Reply complete(NotebookAiConfig config,List<NotebookAiConversation.Message> history,Cancellation cancellation) throws IOException {
        config.validate(); cancellation.check();
        if (history.isEmpty() || !history.get(history.size()-1).role.equals("user")) throw new IOException("No message to send");
        config.specs.validateRequest(history,config.specs.replyBudget(config.provider));
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
                boolean images=false;
                for (NotebookAiConversation.Message message:history) images|=!message.images.isEmpty();
                // Provider bodies can echo credentials or prompts. Keep failures local and bounded.
                String message=status==401||status==403?"Check your API key and model access in Settings."
                    :status==429?"Provider rate or credit limit reached. Wait, then Retry."
                    :status>=500?"Provider is temporarily unavailable. Try again later."
                    :status==400&&images?"Check that the selected model supports image input, as well as the API URL and model ID."
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
        NotebookAiModel specs=config.specs;int budget=specs.replyBudget(config.provider);
        if (anthropic) request.put("system",SYSTEM).put("max_tokens",budget);
        else {
            messages.put(new JSONObject().put("role","system").put("content",SYSTEM));
            if (config.provider==NotebookAiConfig.Provider.OPENAI) request.put("store",false);
            request.put(config.provider==NotebookAiConfig.Provider.OPENAI?"max_completion_tokens":specs.outputParameter,budget);
        }
        if(!specs.effort.isEmpty()){
            if(anthropic)request.put("output_config",new JSONObject().put("effort",specs.effort));
            else request.put("reasoning_effort",specs.effort);
            if(config.provider==NotebookAiConfig.Provider.DEEPSEEK)request.put("thinking",new JSONObject().put("type","enabled"));
        }
        if(anthropic&&!specs.thinking.isEmpty()&&(specs.thinkingRequired||!specs.effort.isEmpty())){
            JSONObject thinking=new JSONObject().put("type",specs.thinking);
            if(specs.thinking.equals("enabled"))thinking.put("budget_tokens",Math.min(8192,Math.max(1024,budget/2)));
            request.put("thinking",thinking);
        }
        for (NotebookAiConversation.Message message:history) {
            Object content=message.content;
            if (!message.images.isEmpty()) {
                JSONArray parts=new JSONArray().put(new JSONObject().put("type","text").put("text",message.content));
                for (NotebookAiAttachment image:message.images) {
                    if (anthropic) parts.put(new JSONObject().put("type","image").put("source",new JSONObject()
                        .put("type","base64").put("media_type","image/png").put("data",image.base64())));
                    else parts.put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",image.dataUrl())));
                }
                content=parts;
            }
            messages.put(new JSONObject().put("role",message.role).put("content",content));
        }
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
