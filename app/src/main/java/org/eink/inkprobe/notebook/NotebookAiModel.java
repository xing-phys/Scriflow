package org.eink.inkprobe.notebook;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Public model metadata and an optional effort preference. Never contains a credential. */
final class NotebookAiModel {
    static final List<String> EFFORTS=Collections.unmodifiableList(Arrays.asList("none","minimal","low","medium","high","xhigh","max","ultra"));
    final String id,name,source,thinking,outputParameter,effort;
    final int context,input,output,vision,chat;
    final long fetched;
    final boolean thinkingRequired;
    final List<String> efforts;

    NotebookAiModel(String id) { this(new JSONObject(),id); }
    private NotebookAiModel(JSONObject data,String id) {
        if(id.isEmpty()||id.length()>200||id.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("Invalid model ID");
        this.id=id;name=text(data,"name",id,200);source=text(data,"source","",200);
        context=limit(data,"context");input=limit(data,"input");output=limit(data,"output");
        vision=flag(data,"vision");chat=flag(data,"chat");fetched=Math.max(0,data.optLong("fetched",0));
        List<String> levels=new ArrayList<>();JSONArray values=data.optJSONArray("efforts");
        if(values!=null)for(int i=0;i<values.length();i++){String value=values.optString(i);if(EFFORTS.contains(value)&&!levels.contains(value))levels.add(value);}
        efforts=Collections.unmodifiableList(levels);String choice=data.optString("effort","");effort=efforts.contains(choice)?choice:"";
        String mode=data.optString("thinking","");thinking=Arrays.asList("adaptive","between_tools","enabled").contains(mode)?mode:"";
        thinkingRequired=data.optBoolean("thinkingRequired",false)&&!thinking.isEmpty();
        outputParameter=data.optString("outputParameter","").equals("max_completion_tokens")?"max_completion_tokens":"max_tokens";
    }
    private static String text(JSONObject value,String key,String fallback,int bound){String s=value.optString(key,fallback);return s.length()<=bound&&!s.matches("(?s).*[\\p{Cntrl}].*")?s:fallback;}
    private static int limit(JSONObject value,String key){long n=value.optLong(key,0);return n>0&&n<=100000000?(int)n:0;}
    private static int flag(JSONObject value,String key){int n=value.optInt(key,-1);return n==0||n==1?n:-1;}
    private static int support(JSONObject value,String key){if(value==null||!value.has(key)||value.isNull(key))return -1;Object v=value.opt(key);if(v instanceof Boolean)return (Boolean)v?1:0;return v instanceof JSONObject?support((JSONObject)v,"supported"):-1;}
    private static boolean contains(JSONArray values,String value){if(values!=null)for(int i=0;i<values.length();i++)if(value.equals(values.optString(i)))return true;return false;}
    JSONObject json() throws JSONException {
        return new JSONObject().put("id",id).put("name",name).put("source",source).put("context",context).put("input",input).put("output",output)
            .put("vision",vision).put("chat",chat).put("fetched",fetched).put("efforts",new JSONArray(efforts)).put("effort",effort)
            .put("thinking",thinking).put("thinkingRequired",thinkingRequired).put("outputParameter",outputParameter);
    }
    static NotebookAiModel stored(JSONObject data)throws JSONException{return new NotebookAiModel(data,data.getString("id"));}
    NotebookAiModel withEffort(String value) {
        if(!value.isEmpty()&&!efforts.contains(value))throw new IllegalArgumentException("This model does not advertise that thinking effort");
        try{return new NotebookAiModel(json().put("effort",value),id);}catch(JSONException error){throw new IllegalArgumentException("Invalid model settings",error);}
    }
    static NotebookAiModel provider(NotebookAiConfig.Provider provider,JSONObject row) throws JSONException {
        String id=row.has("id")?row.getString("id"):row.getString("name").replaceFirst("^models/","");
        JSONObject data=new JSONObject().put("name",row.optString("display_name",row.optString("displayName",row.optString("name",id))))
            .put("context",row.optLong("context_window",row.optLong("context_length",0)))
            .put("input",row.optLong("max_input_tokens",row.optLong("inputTokenLimit",0)))
            .put("output",row.optLong("max_output_tokens",row.optLong("outputTokenLimit",row.optLong("max_tokens",0))))
            .put("source","Provider metadata").put("fetched",System.currentTimeMillis());
        JSONObject caps=row.optJSONObject("capabilities"),effort=row.optJSONObject("effort");
        JSONArray levels=effort==null?row.optJSONArray("reasoning_efforts"):effort.optJSONArray("supported_levels");
        if(levels==null)levels=new JSONArray();
        if(caps!=null){
            data.put("vision",support(caps,"image_input"));
            JSONObject e=caps.optJSONObject("effort");if(e!=null)for(String level:EFFORTS)if(support(e,level)==1&&!contains(levels,level))levels.put(level);
            JSONObject thinking=caps.optJSONObject("thinking"),types=thinking==null?null:thinking.optJSONObject("types");
            if(types!=null){
                for(String type:Arrays.asList("adaptive","between_tools","enabled"))if(support(types,type)==1){data.put("thinking",type);break;}
                data.put("thinkingRequired",support(types,"disabled")==0);
            }
        }
        JSONArray inputs=row.optJSONArray("input_modalities"),outputs=row.optJSONArray("output_modalities");
        JSONObject architecture=row.optJSONObject("architecture");
        if(architecture!=null){if(inputs==null)inputs=architecture.optJSONArray("input_modalities");if(outputs==null)outputs=architecture.optJSONArray("output_modalities");}
        if(inputs!=null)data.put("vision",contains(inputs,"image")?1:0);
        if(outputs!=null)data.put("chat",contains(outputs,"text")?1:0);
        JSONArray methods=row.optJSONArray("supportedGenerationMethods");
        if(methods!=null)data.put("chat",contains(methods,"generateContent")?1:0);
        if(provider==NotebookAiConfig.Provider.ANTHROPIC)data.put("chat",1);
        JSONArray params=row.optJSONArray("supported_parameters");
        data.put("outputParameter",provider==NotebookAiConfig.Provider.OPENAI||contains(params,"max_completion_tokens")?"max_completion_tokens":"max_tokens");
        data.put("efforts",levels);
        // A server returning only IDs has not published any limits or capabilities.
        if(!data.has("vision")&&!data.has("chat")&&limit(data,"context")==0&&limit(data,"input")==0&&limit(data,"output")==0&&levels.length()==0)data.put("source","");
        return new NotebookAiModel(data,id);
    }
    static NotebookAiModel openAiDocument(String id,String markdown) throws IOException {
        if(!markdown.contains("`"+id+"`"))throw new IOException("No official specification for this model ID");
        try {
            JSONObject data=new JSONObject().put("source","OpenAI documentation").put("fetched",System.currentTimeMillis()).put("outputParameter","max_completion_tokens");
            Matcher name=Pattern.compile("(?m)^# (.+)$").matcher(markdown);if(name.find())data.put("name",name.group(1));
            data.put("context",number(markdown,"([0-9,]+) context window"));data.put("input",number(markdown,"Maximum input tokens: ([0-9,]+)"));
            data.put("output",number(markdown,"([0-9,]+) max output tokens"));
            Matcher modes=Pattern.compile("(?mi)^- Input modalities: ([^\\n]+)").matcher(markdown);if(modes.find())data.put("vision",Arrays.asList(modes.group(1).split(",\\s*")).contains("image")?1:0);
            Matcher endpoint=Pattern.compile("(?m)^\\| Chat Completions \\|[^\\n]+\\| (Supported|Not supported) \\|").matcher(markdown);if(endpoint.find())data.put("chat",endpoint.group(1).equals("Supported")?1:0);
            Matcher reasoning=Pattern.compile("(?i)`reasoning\\.effort` supports[^\\n.]+\\.?").matcher(markdown);JSONArray levels=new JSONArray();
            if(reasoning.find()){Matcher values=Pattern.compile("`([a-z]+)`").matcher(reasoning.group());while(values.find())if(EFFORTS.contains(values.group(1)))levels.put(values.group(1));}
            data.put("efforts",levels);
            if(limit(data,"context")==0&&limit(data,"input")==0&&limit(data,"output")==0&&!data.has("chat")&&!data.has("vision")&&levels.length()==0)throw new IOException("Official model specifications are unavailable");
            return new NotebookAiModel(data,id);
        }catch(JSONException error){throw new IOException("Invalid official model specifications",error);}
    }
    private static int number(String text,String expression){Matcher match=Pattern.compile(expression,Pattern.CASE_INSENSITIVE).matcher(text);if(!match.find())return 0;try{long n=Long.parseLong(match.group(1).replace(",",""));return n>0&&n<=100000000?(int)n:0;}catch(NumberFormatException error){return 0;}}
    boolean known(){return context>0||input>0||output>0||vision>=0||chat>=0||!efforts.isEmpty();}
    int replyBudget(NotebookAiConfig.Provider provider){int target=!efforts.isEmpty()||!thinking.isEmpty()?32768:provider==NotebookAiConfig.Provider.OPENAI?16384:8192;if(context>0)target=Math.min(target,Math.max(1,context/4));return output>0?Math.min(target,output):target;}
    String summary(NotebookAiConfig.Provider provider){
        String limits=context>0?"Context: "+String.format(Locale.ROOT,"%,d",context)+" tokens":input>0?"Input: "+String.format(Locale.ROOT,"%,d",input)+" tokens":"Context: provider default";
        if(output>0)limits+=" · Max output: "+String.format(Locale.ROOT,"%,d",output);
        limits+="\nReply budget: "+String.format(Locale.ROOT,"%,d",replyBudget(provider))+" · Images: "+(vision==1?"supported":vision==0?"unsupported":"not reported");
        limits+="\nThinking: "+(efforts.isEmpty()?"provider default":String.join(", ",efforts))+" · "+(source.isEmpty()?"Specs not published":source);
        return limits;
    }
    void validateRequest(List<NotebookAiConversation.Message> messages,int budget) throws IOException {
        if(chat==0)throw new IOException("This model does not advertise text chat. Choose another saved model.");
        if(thinking.equals("enabled")&&(thinkingRequired||!effort.isEmpty())&&budget<=1024)throw new IOException("This model's output limit is too small for extended thinking. Choose another model.");
        long text=NotebookAiConversation.bytes(NotebookAiClient.SYSTEM).length+32;
        boolean images=false;
        for(NotebookAiConversation.Message message:messages){text+=NotebookAiConversation.bytes(message.content).length+32;images|=!message.images.isEmpty();}
        if(images&&vision==0)throw new IOException("This model does not support image input. Choose an image-capable model.");
        // UTF-8 bytes are a deliberately conservative text estimate, not an exact tokenizer.
        long allowed=input>0?input:Long.MAX_VALUE;if(context>0)allowed=Math.min(allowed,Math.max(0,(long)context-budget));
        if(text>allowed)throw new IOException("Chat text exceeds this model's conservative context budget. Shorten the message or start a new chat; saved history is kept.");
    }
}
