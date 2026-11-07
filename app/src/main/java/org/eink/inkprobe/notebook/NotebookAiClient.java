package org.eink.inkprobe.notebook;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;

/** Direct HTTPS requests; only explicitly appended selections are included with chat text. */
final class NotebookAiClient {
    static final String SYSTEM="You are a helpful assistant in Scriflow, a notebook app. Answer in well-structured Markdown. "
        +"Use headings, lists, tables and fenced code blocks where useful. Use $...$ for inline math and $$...$$ for display math. "
        +"Match the user's language. You can see this chat and explicitly attached selections or notebooks, but cannot see other notebooks or PDFs.";
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
        return complete(config,history,cancellation,null);
    }
    Reply stream(NotebookAiConfig config,List<NotebookAiConversation.Message> history,Cancellation cancellation,Consumer<String> progress) throws IOException {
        if(progress==null)throw new IllegalArgumentException("Missing stream listener");
        return complete(config,history,cancellation,progress);
    }
    private Reply complete(NotebookAiConfig config,List<NotebookAiConversation.Message> history,Cancellation cancellation,Consumer<String> progress) throws IOException {
        config.validate(); cancellation.check();
        if (history.isEmpty() || !history.get(history.size()-1).role.equals("user")) throw new IOException("No message to send");
        config.specs.validateRequest(history,config.specs.replyBudget(config.provider));validateAttachments(config,history);
        boolean gemini=usesNativeGemini(config,history);
        boolean streaming=progress!=null;
        String endpoint=gemini?"https://generativelanguage.googleapis.com/v1beta/models/"+config.model+(streaming?":streamGenerateContent?alt=sse":":generateContent"):config.endpoint();
        HttpURLConnection connection=connections.open(new URL(endpoint));
        try {
            cancellation.attach(connection);
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(20000); connection.setReadTimeout(180000);
            connection.setUseCaches(false); connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
            connection.setRequestProperty("Accept",streaming?"text/event-stream, application/json":"application/json");
            if (config.provider==NotebookAiConfig.Provider.ANTHROPIC) {
                connection.setRequestProperty("Authorization","Bearer "+config.apiKey);
                connection.setRequestProperty("anthropic-version","2026-10-21");
            } else if(gemini)connection.setRequestProperty("x-goog-api-key",config.apiKey);
            else if (!config.apiKey.isEmpty()) connection.setRequestProperty("Authorization","Bearer "+config.apiKey);
            JSONObject payload=gemini?geminiRequest(config,history):request(config,history);
            if(streaming&&!gemini)payload.put("stream",true);
            byte[] body=NotebookAiConversation.bytes(payload.toString());
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
            try (BufferedInputStream in=new BufferedInputStream(connection.getInputStream())) {
                String contentType=connection.getContentType();
                boolean json=contentType!=null&&contentType.toLowerCase(Locale.ROOT).startsWith("application/json");
                if(streaming&&!json){
                    // Some compatible servers omit the response type when they ignore stream=true.
                    in.mark(512);int first,seen=0;
                    do{cancellation.check();first=in.read();seen++;}while(first>=0&&first<=32&&seen<256);
                    in.reset();json=first=='{'||first=='[';
                }
                if(streaming&&!json)return streamResponse(config,gemini,in,cancellation,progress);
                String data=read(in,cancellation);Reply answer=gemini?geminiResponse(data):response(config,data);
                if(streaming)progress.accept(answer.text);return answer;
            }
        } catch (JSONException error) { throw new IOException("Provider returned an invalid chat response",error); }
        finally { cancellation.release(); connection.disconnect(); }
    }
    /** Read bounded SSE lines without buffering the entire response or provider error bodies. */
    private static final class EventReader {
        final Reader in;int total;
        EventReader(InputStream input){in=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));}
        String line(Cancellation cancel)throws IOException{
            StringBuilder line=new StringBuilder();int c;
            while((c=in.read())!=-1){
                cancel.check();if(++total>2*1024*1024||line.length()>256*1024)throw new IOException("AI stream exceeds its size limit");
                if(c=='\n')break;if(c!='\r')line.append((char)c);
            }
            return c==-1&&line.length()==0?null:line.toString();
        }
    }
    static Reply streamResponse(NotebookAiConfig config,boolean gemini,InputStream input,Cancellation cancel,Consumer<String> progress)throws IOException {
        EventReader reader=new EventReader(input);StringBuilder data=new StringBuilder(),answer=new StringBuilder();
        String event="",reason="";boolean finished=false,done=false;long lastUpdate=0;int size=0;
        try {
            while(true){
                String line=reader.line(cancel);
                if(line!=null&&!line.isEmpty()){
                    if(line.startsWith("data:")){if(data.length()>0)data.append('\n');data.append(line.substring(5).replaceFirst("^ ",""));}
                    else if(line.startsWith("event:"))event=line.substring(6).trim();
                    continue;
                }
                if(data.length()>0){
                    String payload=data.toString();data.setLength(0);
                    if(payload.equals("[DONE]")){done=true;break;}
                    JSONObject item=new JSONObject(payload);String fragment="";
                    if(event.equals("error")||item.has("error"))throw new IOException("AI provider interrupted the response. Try again later.");
                    if(gemini){
                        JSONArray candidates=item.optJSONArray("candidates");
                        if(candidates!=null&&candidates.length()>0){JSONObject candidate=candidates.getJSONObject(0);
                            JSONObject content=candidate.optJSONObject("content");JSONArray parts=content==null?null:content.optJSONArray("parts");
                            StringBuilder text=new StringBuilder();if(parts!=null)for(int i=0;i<parts.length();i++){
                                JSONObject part=parts.getJSONObject(i);if(!part.optBoolean("thought",false))text.append(part.optString("text",""));}
                            fragment=text.toString();reason=candidate.optString("finishReason",reason);finished|=!reason.isEmpty();}
                    }else if(config.provider==NotebookAiConfig.Provider.ANTHROPIC){
                        String type=item.optString("type",event);JSONObject delta=item.optJSONObject("delta");
                        if(type.equals("content_block_delta")&&delta!=null&&delta.optString("type").equals("text_delta"))fragment=delta.optString("text","");
                        if(type.equals("message_delta")&&delta!=null)reason=delta.optString("stop_reason",reason);
                        if(type.equals("message_stop"))finished=true;
                    }else{
                        JSONArray choices=item.optJSONArray("choices");
                        if(choices!=null&&choices.length()>0){JSONObject choice=choices.getJSONObject(0),delta=choice.optJSONObject("delta");
                            if(delta!=null){Object content=delta.opt("content");fragment=content instanceof String?(String)content:content instanceof JSONArray?textParts((JSONArray)content):"";
                                if(fragment.isEmpty())fragment=delta.optString("refusal","");}
                            if(!choice.isNull("finish_reason")){reason=choice.optString("finish_reason","");finished|=!reason.isEmpty();}}
                    }
                    if(!fragment.isEmpty()){
                        size+=NotebookAiConversation.bytes(fragment).length;if(size>NotebookAiConversation.MAX_MESSAGE_BYTES)throw new IOException("AI reply exceeds 128 KiB. Start a new chat with a shorter request.");
                        answer.append(fragment);long now=System.nanoTime();
                        if(lastUpdate==0||now-lastUpdate>=250_000_000L){progress.accept(answer.toString());lastUpdate=now;}
                    }
                }
                event="";if(finished||line==null)break;
            }
            cancel.check();
            if(!done&&!finished)throw new IOException("AI response was interrupted. You can retry or continue the partial reply.");
            if(answer.toString().trim().isEmpty())throw new IOException("Provider returned no answer. Retry or choose another model.");
            return new Reply(answer.toString(),reason.equals("length")||reason.equals("max_tokens")||reason.equals("MAX_TOKENS"));
        }catch(JSONException error){throw new IOException("Provider returned an invalid streamed response",error);}
        finally{if(answer.length()>0)progress.accept(answer.toString());}
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
                    if(image.isPdf()&&anthropic)parts.put(new JSONObject().put("type","document").put("title",image.name).put("source",new JSONObject()
                        .put("type","base64").put("media_type","application/pdf").put("data",image.base64())));
                    else if(image.isPdf())parts.put(new JSONObject().put("type","file").put("file",new JSONObject()
                        .put("filename",image.name+".pdf").put("file_data",image.dataUrl())));
                    else if (anthropic) parts.put(new JSONObject().put("type","image").put("source",new JSONObject()
                        .put("type","base64").put("media_type","image/png").put("data",image.base64())));
                    else parts.put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",image.dataUrl())));
                }
                content=parts;
            }
            messages.put(new JSONObject().put("role",message.role).put("content",content));
        }
        return request.put("messages",messages);
    }
    static void validateAttachments(NotebookAiConfig config,List<NotebookAiConversation.Message> history)throws IOException {
        if(!hasPdf(history))return;
        if(config.provider==NotebookAiConfig.Provider.DEEPSEEK)
            throw new IOException("DeepSeek's chat endpoint does not support whole-notebook PDFs. Choose an image-capable provider that supports PDF input.");
        if(config.provider==NotebookAiConfig.Provider.GEMINI) {
            if(!config.endpoint().equals(NotebookAiConfig.Provider.GEMINI.baseUrl+"/chat/completions")||!config.model.matches("[A-Za-z0-9._-]{1,200}"))
                throw new IOException("Notebook PDFs require the standard Gemini endpoint and a valid Gemini model ID.");
            if(!config.specs.effort.isEmpty())
                throw new IOException("Set Gemini thinking effort to Automatic before sending a notebook PDF.");
        }
    }
    private static boolean hasPdf(List<NotebookAiConversation.Message> history){
        for(NotebookAiConversation.Message message:history)for(NotebookAiAttachment attachment:message.images)if(attachment.isPdf())return true;
        return false;
    }
    private static boolean usesNativeGemini(NotebookAiConfig config,List<NotebookAiConversation.Message> history){
        return config.provider==NotebookAiConfig.Provider.GEMINI&&hasPdf(history);
    }
    /** Gemini's OpenAI-compatible endpoint does not accept PDFs; use its native API only for those chats. */
    static JSONObject geminiRequest(NotebookAiConfig config,List<NotebookAiConversation.Message> history)throws JSONException {
        JSONArray contents=new JSONArray();
        for(NotebookAiConversation.Message message:history) {
            JSONArray parts=new JSONArray().put(new JSONObject().put("text",message.content));
            for(NotebookAiAttachment attachment:message.images)parts.put(new JSONObject().put("inlineData",new JSONObject()
                .put("mimeType",attachment.isPdf()?"application/pdf":"image/png").put("data",attachment.base64())));
            contents.put(new JSONObject().put("role",message.role.equals("assistant")?"model":"user").put("parts",parts));
        }
        return new JSONObject().put("systemInstruction",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",SYSTEM))))
            .put("contents",contents).put("generationConfig",new JSONObject().put("maxOutputTokens",config.specs.replyBudget(config.provider)));
    }
    static Reply geminiResponse(String data)throws IOException,JSONException {
        JSONArray candidates=new JSONObject(data).optJSONArray("candidates");
        if(candidates==null||candidates.length()==0)throw new IOException("Gemini returned no answer. Retry or choose another model.");
        JSONObject candidate=candidates.getJSONObject(0),content=candidate.optJSONObject("content");
        JSONArray parts=content==null?null:content.optJSONArray("parts");StringBuilder answer=new StringBuilder();
        if(parts!=null)for(int i=0;i<parts.length();i++) {
            JSONObject part=parts.getJSONObject(i);if(!part.optBoolean("thought",false))answer.append(part.optString("text",""));
        }
        String text=answer.toString();
        if(text.trim().isEmpty())throw new IOException("Gemini returned no answer. Retry or choose another model.");
        if(NotebookAiConversation.bytes(text).length>NotebookAiConversation.MAX_MESSAGE_BYTES)throw new IOException("AI reply exceeds 128 KiB. Start a new chat with a shorter request.");
        return new Reply(text,candidate.optString("finishReason").equals("MAX_TOKENS"));
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
