package org.eink.inkprobe.notebook;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Provider metadata, public documentation, persisted selections and actual request policy without live accounts. */
public final class NotebookAiModelsTest {
    private static int assertions;
    interface Checked {void run()throws Exception;}
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static void fails(Class<? extends Exception> type,Checked action)throws Exception{try{action.run();}catch(Exception error){check(type.isInstance(error),"Expected "+type+", got "+error);return;}throw new AssertionError("Expected "+type);}
    private static JSONObject json(String text)throws Exception{return new JSONObject(text);}
    private static NotebookAiConfig config(NotebookAiConfig.Provider provider,NotebookAiModel model){return new NotebookAiConfig(provider,provider==NotebookAiConfig.Provider.CUSTOM?"https://ai.example/v1":provider.baseUrl,model.id,"private-test-key",model);}
    private static final String DOCUMENT="# Example model\nModel ID: `gpt-example`\n`reasoning.effort` supports `low`, `medium` (default), `high`, and `max`.\n"
        +"## Model details\n- Input modalities: text, image\n- Output modalities: text\n- 100,000 context window\n- Maximum input tokens: 80,000\n- 20,000 max output tokens\n"
        +"## Endpoints\n| Endpoint | Route | Support |\n| Chat Completions | `v1/chat/completions` | Supported |\n## Snapshots\n- `gpt-example`\n- `gpt-example-2026-11-01`\n";
    public static void main(String[] args)throws Exception{
        metadata();documents();transport();requests();library();manualSettings();
        System.out.println("NotebookAiModels: "+assertions+" assertions passed (live-shaped specifications, native discovery, credential isolation, saved selections and context/thinking policy). No live API calls.");
    }
    private static void metadata()throws Exception{
        NotebookAiModel bare=NotebookAiModel.provider(NotebookAiConfig.Provider.CUSTOM,json("{\"id\":\"unknown\"}"));
        check(!bare.known()&&bare.context==0&&bare.input==0&&bare.output==0&&bare.vision==-1&&bare.chat==-1,"ID-only catalogs do not acquire invented capabilities");
        check(bare.effort.isEmpty()&&bare.efforts.isEmpty()&&bare.summary(NotebookAiConfig.Provider.CUSTOM).contains("provider default"),"Unknown models need no manual configuration");
        NotebookAiModel claude=claude();
        check(claude.input==200000&&claude.output==64000&&claude.context==0,"Anthropic input and output limits retain their distinct meanings");
        check(claude.vision==1&&claude.chat==1&&claude.thinking.equals("adaptive")&&claude.thinkingRequired,"Native capabilities choose a supported thinking type");
        check(claude.efforts.equals(Arrays.asList("low","high","max"))&&claude.effort.isEmpty(),"Only advertised effort levels are offered; default remains Automatic");
        fails(IllegalArgumentException.class,()->claude.withEffort("medium"));
        NotebookAiModel deep=deepseek();
        check(deep.context==65536&&deep.output==8192&&deep.vision==0&&deep.chat==1,"DeepSeek total context, output and modalities are read from live metadata");
        check(deep.efforts.equals(Arrays.asList("low","high","max")),"Provider effort ordering is retained");
        NotebookAiModel gemini=NotebookAiModel.provider(NotebookAiConfig.Provider.GEMINI,json("{\"name\":\"models/gemini-example\",\"displayName\":\"Example\",\"inputTokenLimit\":1048576,\"outputTokenLimit\":65536,\"thinking\":true,\"supportedGenerationMethods\":[\"generateContent\"]}"));
        check(gemini.id.equals("gemini-example")&&gemini.input==1048576&&gemini.output==65536&&gemini.chat==1,"Native Gemini model metadata is usable with its chat-compatible ID");
        check(gemini.efforts.isEmpty(),"A thinking boolean does not invent supported effort levels");
        NotebookAiModel embedding=NotebookAiModel.provider(NotebookAiConfig.Provider.GEMINI,json("{\"name\":\"models/embed\",\"supportedGenerationMethods\":[\"embedContent\"]}"));
        check(embedding.chat==0,"Native non-chat generation methods are distinguishable in model selection");
        NotebookAiModel compatible=NotebookAiModel.provider(NotebookAiConfig.Provider.CUSTOM,json("{\"id\":\"compatible\",\"context_length\":32000,\"max_output_tokens\":4096,\"input_modalities\":[\"text\",\"image\"],\"output_modalities\":[\"image\"],\"supported_parameters\":[\"max_completion_tokens\"],\"reasoning_efforts\":[\"high\",\"bogus\",\"high\"]}"));
        check(compatible.context==32000&&compatible.output==4096&&compatible.outputParameter.equals("max_completion_tokens"),"Compatible servers can advertise their actual token parameter");
        check(compatible.chat==0&&compatible.vision==1&&compatible.efforts.equals(Collections.singletonList("high")),"Unknown or duplicate effort values cannot configure a request");
        NotebookAiModel malformed=NotebookAiModel.provider(NotebookAiConfig.Provider.CUSTOM,json("{\"id\":\"bounded\",\"context_window\":-10,\"max_input_tokens\":999999999999,\"max_output_tokens\":\"unknown\"}"));
        check(malformed.context==0&&malformed.input==0&&malformed.output==0,"Invalid limits remain unknown rather than creating unsafe budgets");
        fails(IllegalArgumentException.class,()->new NotebookAiModel("bad\nmodel"));
    }
    private static NotebookAiModel claude()throws Exception{return NotebookAiModel.provider(NotebookAiConfig.Provider.ANTHROPIC,json("{\"id\":\"claude-example\",\"display_name\":\"Claude Example\",\"max_input_tokens\":200000,\"max_tokens\":64000,\"capabilities\":{\"image_input\":{\"supported\":true},\"effort\":{\"supported\":true,\"low\":{\"supported\":true},\"medium\":{\"supported\":false},\"high\":{\"supported\":true},\"max\":{\"supported\":true}},\"thinking\":{\"supported\":true,\"types\":{\"adaptive\":{\"supported\":true},\"between_tools\":{\"supported\":true},\"disabled\":{\"supported\":false},\"enabled\":{\"supported\":false}}}}}"));}
    private static NotebookAiModel deepseek()throws Exception{return NotebookAiModel.provider(NotebookAiConfig.Provider.DEEPSEEK,json("{\"id\":\"deep-example\",\"name\":\"Deep Example\",\"context_window\":65536,\"max_output_tokens\":8192,\"input_modalities\":[\"text\"],\"output_modalities\":[\"text\"],\"effort\":{\"supported_levels\":[\"low\",\"high\",\"max\"],\"default_level\":\"high\"}}"));}
    private static void documents()throws Exception{
        NotebookAiModel model=NotebookAiModel.openAiDocument("gpt-example",DOCUMENT);
        check(model.context==100000&&model.input==80000&&model.output==20000,"Official OpenAI Markdown exposes actual token limits");
        check(model.vision==1&&model.chat==1&&model.efforts.equals(Arrays.asList("low","medium","high","max")),"Official documentation supplies modality and supported efforts");
        check(model.effort.isEmpty()&&model.source.equals("OpenAI documentation")&&model.fetched>0,"Documentation provenance is retained separately from an optional preference");
        check(NotebookAiModel.openAiDocument("gpt-example-2026-11-01",DOCUMENT).context==100000,"Snapshot metadata is accepted only when the ID is actually documented");
        fails(IOException.class,()->NotebookAiModel.openAiDocument("gpt-unlisted",DOCUMENT));
        fails(IOException.class,()->NotebookAiModel.openAiDocument("gpt-example","# No limits\n`gpt-example`"));
        NotebookAiModel noChat=NotebookAiModel.openAiDocument("gpt-example",DOCUMENT.replace("| Supported |","| Not supported |"));
        check(noChat.chat==0,"Documented unsupported chat endpoints cannot be offered as working chat models");
        NotebookAiModel noLimits=NotebookAiModel.openAiDocument("gpt-example","# Embedding\n`gpt-example`\n| Chat Completions | `v1/chat/completions` | Not supported |\n");
        check(noLimits.chat==0&&noLimits.context==0,"A documented non-chat endpoint remains known even without token limits");
    }
    private static final class Connection extends HttpURLConnection {
        int status=200;String response;boolean disconnected;ByteArrayOutputStream output=new ByteArrayOutputStream();
        Connection(String response)throws Exception{super(new URL("https://fixture.example"));this.response=response;}
        public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}public void connect(){}
        public int getResponseCode(){return status;}public InputStream getInputStream(){return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));}
        public OutputStream getOutputStream(){return output;}
    }
    private static void transport()throws Exception{
        NotebookAiConfig gemini=config(NotebookAiConfig.Provider.GEMINI,new NotebookAiModel("gemini-example"));
        Connection first=new Connection("{\"models\":[{\"name\":\"models/gemini-a\",\"inputTokenLimit\":10000}],\"nextPageToken\":\"opaque /+\"}"),second=new Connection("{\"models\":[{\"name\":\"models/gemini-b\",\"outputTokenLimit\":4096}]}");
        ArrayDeque<Connection> pages=new ArrayDeque<>(Arrays.asList(first,second));List<String> urls=new ArrayList<>();
        List<NotebookAiModel> catalog=new NotebookAiClient(url->{urls.add(url.toString());return pages.remove();}).catalog(gemini,new NotebookAiClient.Cancellation());
        check(catalog.size()==2&&catalog.get(0).input==10000&&catalog.get(1).output==4096,"Native Gemini discovery retains specifications across pages");
        check(urls.get(0).equals("https://generativelanguage.googleapis.com/v1beta/models")&&urls.get(1).endsWith("?pageToken=opaque+%2F%2B"),"Native metadata and opaque pagination use fixed provider destinations");
        check(first.getRequestProperty("x-goog-api-key").equals(gemini.apiKey)&&first.getRequestProperty("Authorization")==null&&!urls.toString().contains(gemini.apiKey),"Gemini keys stay in headers rather than URLs");
        check(first.disconnected&&second.disconnected&&!first.getInstanceFollowRedirects(),"Metadata requests release connections without following redirects");
        Connection original=new Connection("{\"data\":[{\"id\":\"custom\",\"context_window\":1000}]}");
        NotebookAiConfig proxy=new NotebookAiConfig(NotebookAiConfig.Provider.GEMINI,"https://proxy.example/v1","custom","key");
        new NotebookAiClient(url->{check(url.toString().equals(proxy.modelsEndpoint()),"A configured proxy never sends its key to native Google metadata");return original;}).catalog(proxy,new NotebookAiClient.Cancellation());
        NotebookAiConfig openai=config(NotebookAiConfig.Provider.OPENAI,new NotebookAiModel("gpt-example"));Connection doc=new Connection(DOCUMENT);
        NotebookAiModel metadata=new NotebookAiClient(url->{check(url.toString().equals("https://developers.openai.com/api/docs/models/gpt-example.md"),"Public specs use only the fixed official documentation origin");return doc;}).specifications(openai,openai.specs,new NotebookAiClient.Cancellation());
        check(metadata.output==20000&&doc.output.size()==0&&doc.getRequestMethod().equals("GET"),"Specification fetching sends no prompt");
        check(doc.getRequestProperty("Authorization")==null&&doc.getRequestProperty("x-goog-api-key")==null&&doc.disconnected,"No API key is ever attached to public model documentation");
        Connection missing=new Connection("");missing.status=404;Connection snapshot=new Connection(DOCUMENT);ArrayDeque<Connection> docs=new ArrayDeque<>(Arrays.asList(missing,snapshot));List<String> paths=new ArrayList<>();
        NotebookAiModel dated=new NotebookAiModel("gpt-example-2026-11-01");
        check(new NotebookAiClient(url->{paths.add(url.toString());return docs.remove();}).specifications(openai,dated,new NotebookAiClient.Cancellation()).context==100000&&paths.get(1).endsWith("/gpt-example.md"),"Dated snapshots can resolve through a document that explicitly lists them");
        for(int status:new int[]{301,401,404,500}){Connection error=new Connection("");error.status=status;fails(IOException.class,()->new NotebookAiClient(url->error).specifications(openai,openai.specs,new NotebookAiClient.Cancellation()));check(error.disconnected&&!error.getInstanceFollowRedirects(),"Failed metadata reads release credentials-free sockets");}
        NotebookAiClient noNetwork=new NotebookAiClient(url->{throw new AssertionError("Unexpected documentation request to "+url);});
        check(noNetwork.specifications(openai,metadata,new NotebookAiClient.Cancellation())==metadata,"The fetch/add flow reuses recently fetched documentation instead of requesting it twice");
        NotebookAiConfig custom=new NotebookAiConfig(NotebookAiConfig.Provider.OPENAI,"https://proxy.example/v1","gpt-example","proxy-key");
        check(noNetwork.specifications(custom,custom.specs,new NotebookAiClient.Cancellation())==custom.specs,"OpenAI-shaped model IDs on custom servers do not inherit official specifications");
        NotebookAiModel path=new NotebookAiModel("model/other");check(noNetwork.specifications(openai,path,new NotebookAiClient.Cancellation())==path,"Provider-controlled IDs cannot inject documentation paths");
        NotebookAiClient.Cancellation stopped=new NotebookAiClient.Cancellation();stopped.cancel();fails(InterruptedIOException.class,()->noNetwork.specifications(openai,openai.specs,stopped));
    }
    private static void requests()throws Exception{
        List<NotebookAiConversation.Message> history=new NotebookAiConversation().question("Explain this").messages;
        NotebookAiModel claude=claude();NotebookAiConfig config=config(NotebookAiConfig.Provider.ANTHROPIC,claude);
        JSONObject auto=NotebookAiClient.request(config,history);
        check(!auto.has("output_config")&&auto.getJSONObject("thinking").getString("type").equals("adaptive"),"Automatic effort does not force a manually guessed level");
        JSONObject high=NotebookAiClient.request(config(NotebookAiConfig.Provider.ANTHROPIC,claude.withEffort("high")),history);
        check(high.getJSONObject("output_config").getString("effort").equals("high")&&!high.has("reasoning_effort")&&high.getInt("max_tokens")==32768,"Anthropic thinking and effort use its own request schema and bounded output");
        NotebookAiModel deep=deepseek();JSONObject deepRequest=NotebookAiClient.request(config(NotebookAiConfig.Provider.DEEPSEEK,deep.withEffort("max")),history);
        check(deepRequest.getString("reasoning_effort").equals("max")&&deepRequest.getJSONObject("thinking").getString("type").equals("enabled")&&deepRequest.getInt("max_tokens")==8192,"DeepSeek enables thinking for a selected advertised effort and respects its output maximum");
        JSONObject deepAuto=NotebookAiClient.request(config(NotebookAiConfig.Provider.DEEPSEEK,deep),history);
        check(!deepAuto.has("thinking")&&!deepAuto.has("reasoning_effort"),"Automatic DeepSeek uses the model's default thinking configuration");
        NotebookAiModel open=NotebookAiModel.openAiDocument("gpt-example",DOCUMENT);JSONObject openRequest=NotebookAiClient.request(config(NotebookAiConfig.Provider.OPENAI,open.withEffort("low")),history);
        check(openRequest.getString("reasoning_effort").equals("low")&&openRequest.getInt("max_completion_tokens")==20000&&!openRequest.getBoolean("store"),"OpenAI applies a supported effort and discovered maximum while retaining store=false");
        NotebookAiModel tiny=NotebookAiModel.provider(NotebookAiConfig.Provider.CUSTOM,json("{\"id\":\"tiny\",\"context_window\":4096,\"max_output_tokens\":8000,\"input_modalities\":[\"text\"]}"));
        check(tiny.replyBudget(NotebookAiConfig.Provider.CUSTOM)==1024,"Small contexts reserve room for input automatically");
        List<NotebookAiConversation.Message> tooLong=new NotebookAiConversation().question(String.join("",Collections.nCopies(4000,"界"))).messages;
        NotebookAiClient noSocket=new NotebookAiClient(url->{throw new AssertionError("Local preflight must happen before opening a billable request");});
        fails(IOException.class,()->noSocket.complete(config(NotebookAiConfig.Provider.CUSTOM,tiny),tooLong,new NotebookAiClient.Cancellation()));
        check(tooLong.size()==1&&tooLong.get(0).content.length()==4000,"Context checks preserve all saved messages rather than silently trimming");
        tiny.validateRequest(history,tiny.replyBudget(NotebookAiConfig.Provider.CUSTOM));check(true,"A fitting prompt remains sendable");
        byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAXpeqz8AAAAASUVORK5CYII=");
        List<NotebookAiConversation.Message> image=new NotebookAiConversation().attach(new NotebookAiAttachment("image",png)).question("Read").messages;
        fails(IOException.class,()->noSocket.complete(config(NotebookAiConfig.Provider.CUSTOM,tiny),image,new NotebookAiClient.Cancellation()));
        NotebookAiModel notChat=NotebookAiModel.provider(NotebookAiConfig.Provider.CUSTOM,json("{\"id\":\"image-only\",\"output_modalities\":[\"image\"]}"));
        fails(IOException.class,()->noSocket.complete(config(NotebookAiConfig.Provider.CUSTOM,notChat),history,new NotebookAiClient.Cancellation()));
        JSONObject unknown=NotebookAiClient.request(config(NotebookAiConfig.Provider.CUSTOM,new NotebookAiModel("manual")),history);
        check(!unknown.has("reasoning_effort")&&!unknown.has("thinking")&&unknown.getInt("max_tokens")==8192,"Manual and unknown models retain compatible conservative defaults");
        NotebookAiModel legacy=NotebookAiModel.provider(NotebookAiConfig.Provider.ANTHROPIC,json("{\"id\":\"legacy\",\"max_tokens\":16000,\"capabilities\":{\"thinking\":{\"types\":{\"enabled\":{\"supported\":true},\"disabled\":{\"supported\":false}}}}}"));
        JSONObject enabled=NotebookAiClient.request(config(NotebookAiConfig.Provider.ANTHROPIC,legacy),history).getJSONObject("thinking");
        check(enabled.getInt("budget_tokens")>=1024&&enabled.getInt("budget_tokens")<legacy.replyBudget(NotebookAiConfig.Provider.ANTHROPIC),"Extended-only thinking receives a valid budget within the output maximum");
        Path directory=Files.createTempDirectory("ai-model-preflight-");
        try{
            final int[] calls={0};NotebookAiSession session=new NotebookAiSession(directory.toFile(),config(NotebookAiConfig.Provider.CUSTOM,tiny),Runnable::run,()->{},Runnable::run,Runnable::run,
                (settings,chat,cancel)->{calls[0]++;return new NotebookAiClient.Reply("ok",false);});
            session.load();String draft=tooLong.get(0).content;session.draft(draft);session.send(draft);
            check(calls[0]==0&&!session.busy&&!session.chat.awaitingReply()&&session.chat.draft.equals(draft),"Local context rejection keeps the draft editable and never schedules a provider request");
            check(NotebookAiHistory.read(directory.toFile(),session.id).chat.draft.equals(draft),"Rejected prompts retain their saved draft for reopening");
            session.draft("Shorter question");session.send("Shorter question");check(calls[0]==1&&session.chat.lastAnswer().equals("ok"),"The user can shorten a rejected draft and send successfully in the same session");session.close();
        }finally{try(java.util.stream.Stream<Path> files=Files.walk(directory)){for(Path file:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(file);}}
    }
    private static void library()throws Exception{
        NotebookAiModel one=claude().withEffort("high"),two=deepseek();List<NotebookAiModel> selected=Arrays.asList(one,two);
        String encoded=NotebookAiModelLibrary.encode(selected);List<NotebookAiModel> decoded=NotebookAiModelLibrary.decode(encoded);
        check(decoded.size()==2&&decoded.get(0).effort.equals("high")&&decoded.get(0).input==200000&&decoded.get(1).vision==0,"Selected model specs and effort preferences survive an offline round trip");
        check(!encoded.contains("private-test-key")&&!encoded.contains("Explain this"),"Model profiles contain neither API keys nor conversation content");
        List<NotebookAiModel> edited=NotebookAiModelLibrary.merge(decoded,Collections.singletonList(one.withEffort("low")));
        check(edited.size()==2&&edited.get(0).effort.equals("low")&&edited.get(1).id.equals(two.id),"Changing a preference updates only that saved model");
        check(NotebookAiModelLibrary.decode("invalid").isEmpty(),"Corrupt model preferences do not configure a request");
        check(NotebookAiModelLibrary.decode("[{\"id\":\"safe\"},{\"id\":\"bad\\nID\"},{\"id\":\"safe\"}]").size()==1,"Corrupt and duplicate model records are isolated");
        JSONObject unsupported=one.json().put("effort","invalid");check(NotebookAiModel.stored(unsupported).effort.isEmpty(),"Stale or unsupported preferences return to Automatic");
        List<NotebookAiModel> tooMany=new ArrayList<>();for(int i=0;i<=NotebookAiModelLibrary.MAX_MODELS;i++)tooMany.add(new NotebookAiModel("m"+i));
        fails(IOException.class,()->NotebookAiModelLibrary.encode(tooMany));fails(IOException.class,()->NotebookAiModelLibrary.encode(Arrays.asList(one,one)));
        NotebookAiConfig a=config(NotebookAiConfig.Provider.ANTHROPIC,one),b=config(NotebookAiConfig.Provider.ANTHROPIC,one.withEffort("low"));
        check(a.profile().equals(b.profile()),"Changing model effort preserves the same saved-session identity");
        String endpointA=new NotebookAiConfig(a.provider,a.baseUrl,"","").profile(),endpointB=new NotebookAiConfig(a.provider,"https://other.example/v1","","").profile();
        check(!endpointA.equals(endpointB),"Saved model libraries have different identities for different endpoints");
        NotebookAiConfig wrong=new NotebookAiConfig(a.provider,a.baseUrl,"different-model","key",one);
        check(!wrong.specs.known()&&wrong.specs.id.equals("different-model"),"A model cannot accidentally inherit another ID's limits or thinking options");
    }
    private static void manualSettings()throws Exception{
        NotebookAiModel original=claude();String defaults=String.join(", ",original.efforts);
        NotebookAiModel unchanged=original.edit(original.context,original.input,original.output,0,original.vision,defaults,"");
        check(!unchanged.edited()&&unchanged.reply==0,"Applying untouched fetched fields leaves automatic configuration intact");
        NotebookAiModel edited=original.edit(100000,80000,16000,12000,0,"low, medium, high","medium");
        check(edited.edited()&&edited.context==100000&&edited.input==80000&&edited.output==16000,"Manual context, input and output edits apply to that model");
        check(edited.replyBudget(NotebookAiConfig.Provider.ANTHROPIC)==12000&&edited.vision==0&&edited.effort.equals("medium"),"Manual reply, image and effort preferences are effective");
        JSONObject request=NotebookAiClient.request(config(NotebookAiConfig.Provider.ANTHROPIC,edited),new NotebookAiConversation().question("Explain").messages);
        check(request.getInt("max_tokens")==12000&&request.getJSONObject("output_config").getString("effort").equals("medium"),"The actual provider request uses the edited output budget and effort");
        NotebookAiModel restored=NotebookAiModelLibrary.decode(NotebookAiModelLibrary.encode(Collections.singletonList(edited))).get(0);
        check(restored.edited()&&restored.context==100000&&restored.reply==12000&&restored.effort.equals("medium"),"Manual parameters survive an offline save/reopen round trip");
        NotebookAiModel reset=restored.automatic();
        check(!reset.edited()&&reset.input==original.input&&reset.output==original.output&&reset.vision==original.vision,"Reset restores fetched limits and image support");
        check(reset.reply==0&&reset.effort.isEmpty()&&reset.efforts.equals(original.efforts),"Reset restores automatic reply and thinking preferences");
        NotebookAiModel fresh=NotebookAiModel.stored(original.json().put("context",300000).put("input",250000).put("output",128000).put("vision",1));
        NotebookAiModel refreshed=fresh.refreshed(restored);
        check(refreshed.context==100000&&refreshed.input==80000&&refreshed.output==16000&&refreshed.reply==12000,"Fetching again preserves explicit manual limits");
        check(refreshed.vision==0&&refreshed.effort.equals("medium"),"Fetching again preserves manual capabilities and thinking choices");
        check(refreshed.automatic().context==300000&&refreshed.automatic().input==250000&&refreshed.automatic().output==128000,"Reset after refresh uses the latest fetched defaults");
        NotebookAiModel oneEdit=original.edit(50000,original.input,original.output,0,original.vision,defaults,"");
        NotebookAiModel oneRefreshed=fresh.refreshed(oneEdit);
        check(oneRefreshed.context==50000&&oneRefreshed.input==250000&&oneRefreshed.output==128000,"Only edited fields override a newer provider specification");
        NotebookAiModel unknown=new NotebookAiModel("private-model").edit(32000,28000,4096,2048,1,"low, high","high");
        check(unknown.known()&&unknown.edited()&&!unknown.automatic().known(),"An opaque model can be configured manually without inventing fetched defaults");
        check(NotebookAiClient.request(config(NotebookAiConfig.Provider.CUSTOM,unknown),new NotebookAiConversation().question("Hi").messages).getString("reasoning_effort").equals("high"),"Explicitly configured thinking options work for compatible custom servers");
        NotebookAiModel blank=original.edit(0,0,0,0,-1,"","");
        check(blank.context==0&&blank.input==0&&blank.output==0&&blank.vision==-1&&blank.efforts.isEmpty(),"Clearing a field records an explicit unknown/provider-default choice");
        check(blank.automatic().input==original.input,"Clearing fields does not destroy the fetched baseline");
        fails(IllegalArgumentException.class,()->original.edit(-1,0,0,0,-1,"",""));
        fails(IllegalArgumentException.class,()->original.edit(100000001,0,0,0,-1,"",""));
        fails(IllegalArgumentException.class,()->original.edit(0,0,1000,2000,-1,"",""));
        fails(IllegalArgumentException.class,()->original.edit(2000,0,0,2000,-1,"",""));
        fails(IllegalArgumentException.class,()->original.edit(0,0,0,0,2,"",""));
        fails(IllegalArgumentException.class,()->original.edit(0,0,0,0,-1,"invalid",""));
        fails(IllegalArgumentException.class,()->original.edit(0,0,0,0,-1,"low","high"));
        List<NotebookAiModel> catalog=new ArrayList<>();for(int i=0;i<80;i++)catalog.add(new NotebookAiModel("model-"+i));catalog.set(0,edited);
        String cached=NotebookAiModelLibrary.encodeCatalog(catalog);
        check(NotebookAiModelLibrary.catalog(cached).size()==80,"The inline catalog can retain more rows than the enabled-model limit");
        check(NotebookAiModelLibrary.catalog(cached).get(0).reply==12000,"Catalog caching retains model parameters even for an unchecked row");
        fails(IOException.class,()->NotebookAiModelLibrary.encode(catalog));
        List<NotebookAiModel> merged=NotebookAiModelLibrary.refreshed(Arrays.asList(fresh,new NotebookAiModel("newly-discovered")),Arrays.asList(edited,new NotebookAiModel("saved-offline")));
        check(merged.size()==3&&NotebookAiModelLibrary.find(merged,edited.id).context==100000,"Refresh combines discovered rows with previous configurations and local edits");
        check(NotebookAiModelLibrary.refreshed(Collections.singletonList(new NotebookAiModel(edited.id)),Collections.singletonList(edited)).get(0).context==100000,"An ID-only refresh does not erase known limits or manual edits");
        int size=edited.json().toString().length();for(int i=0;i<20;i++)edited=NotebookAiModel.stored(edited.json());
        check(edited.json().toString().length()==size,"Repeated saves do not nest or grow automatic/default metadata");
    }
}
