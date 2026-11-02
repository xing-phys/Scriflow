package org.eink.inkprobe.notebook;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executor;
import org.json.*;

/** Real production requests and files with deterministic HTTP connections and worker queues. */
public final class NotebookAiTest {
    private static int assertions;
    private interface Checked { void run() throws Exception; }
    private static void check(boolean yes,String reason) { assertions++; if (!yes) throw new AssertionError(reason); }
    private static Exception fails(Class<? extends Exception> type,Checked action) throws Exception {
        try { action.run(); } catch (Exception error) { check(type.isInstance(error),"Expected "+type+", got "+error); return error; }
        throw new AssertionError("Expected "+type);
    }
    private static NotebookAiConfig config(NotebookAiConfig.Provider provider) {
        return new NotebookAiConfig(provider,provider==NotebookAiConfig.Provider.CUSTOM?"https://ai.example/api/v1":provider.baseUrl,
            provider==NotebookAiConfig.Provider.CUSTOM?"model/test":provider.model,"test-key-never-live");
    }
    public static void main(String[] args) throws Exception {
        configuration(); persistence(); protocols(); transport(); lifecycle(); models(); attachments();
        System.out.println("NotebookAi: "+assertions+" assertions passed (provider protocols, HTTP failures/cancellation, private storage, worker ordering and lifecycle). No live API calls.");
    }
    private static void configuration() throws Exception {
        for (NotebookAiConfig.Provider provider:NotebookAiConfig.Provider.values()) {
            NotebookAiConfig config=config(provider); config.validate();
            check(config.endpoint().endsWith(provider==NotebookAiConfig.Provider.ANTHROPIC?"/messages":"/chat/completions"),"Provider-specific endpoint");
            NotebookAiConfig full=new NotebookAiConfig(provider,config.endpoint()+"/",config.model,"rotated-key");
            check(full.endpoint().equals(config.endpoint())&&full.profile().equals(config.profile()),"Trailing slash/full endpoint/key rotation keep history identity");
            check(!new NotebookAiConfig(provider,config.baseUrl,config.model+"-other",config.apiKey).profile().equals(config.profile()),"Models have independent history");
        }
        for (String url:Arrays.asList("http://ai.example/v1","https://user:password@ai.example/v1","https://ai.example/v1?key=secret","https://ai.example/v1#x","https://ai.example:0","https://ai.example:99999","", "https://ai.example/\npath"))
            fails(IllegalArgumentException.class,()->new NotebookAiConfig(NotebookAiConfig.Provider.CUSTOM,url,"model","").validate());
        fails(IllegalArgumentException.class,()->new NotebookAiConfig(NotebookAiConfig.Provider.OPENAI,"https://api.openai.com/v1","model","").validate());
        fails(IllegalArgumentException.class,()->new NotebookAiConfig(NotebookAiConfig.Provider.CUSTOM,"https://ai.example/v1","model","key\r\nHeader: injected").validate());
        new NotebookAiConfig(NotebookAiConfig.Provider.CUSTOM,"https://ai.example/v1","model","").validate(); check(true,"Self-hosted HTTPS endpoint may have no key");
    }
    private static void persistence() throws Exception {
        File directory=Files.createTempDirectory("ai-chat-files-").toFile(); String profile=config(NotebookAiConfig.Provider.OPENAI).profile();
        try {
            NotebookAiConversation chat=NotebookAiConversation.read(directory,profile).draft("Unsent 草稿 ψ\n");
            NotebookAiConversation.write(directory,profile,chat);
            check(NotebookAiConversation.read(directory,profile).draft.equals(chat.draft),"Unicode draft survives process restart");
            chat=chat.question("Derive $i\\hbar\\dot\\psi=H\\psi$.");
            NotebookAiConversation.write(directory,profile,chat); chat=NotebookAiConversation.read(directory,profile);
            check(chat.awaitingReply()&&chat.draft.isEmpty()&&chat.messages.size()==1,"Unanswered question remains available to Retry");
            chat=chat.answer("# Answer\n\n中文 · $\\ket{\\psi}$\n\n```julia\nU = exp(-im*H*t)\n```").draft("Next question");
            NotebookAiConversation.write(directory,profile,chat);
            check(NotebookAiConversation.read(directory,profile).lastAnswer().equals(chat.lastAnswer()),"Markdown source roundtrips exactly");
            check(!chat.awaitingReply()&&chat.messages.get(0).role.equals("user")&&chat.messages.get(1).role.equals("assistant"),"Follow-up role order");
            File target=new File(directory,"ai-chat-"+profile+".bin"),temporary=new File(directory,target.getName()+".tmp");
            byte[] original=Files.readAllBytes(target.toPath()); Files.createDirectory(temporary.toPath());
            NotebookAiConversation next=chat.draft("replacement");
            fails(IOException.class,()->NotebookAiConversation.write(directory,profile,next));
            check(Arrays.equals(original,Files.readAllBytes(target.toPath())),"Failed publication keeps last durable transcript");
            Files.write(temporary.toPath(),new byte[]{1,2,3});
            check(NotebookAiConversation.read(directory,profile).draft.equals("Next question"),"Interrupted staging file is ignored");
            byte[] damaged=original.clone(); damaged[damaged.length-1]^=1; Files.write(target.toPath(),damaged);
            fails(IOException.class,()->NotebookAiConversation.read(directory,profile));
            for (int length:new int[]{0,4,11,12,16}) {
                Files.write(target.toPath(),Arrays.copyOf(original,length)); fails(IOException.class,()->NotebookAiConversation.read(directory,profile));
            }
            NotebookAiConversation full=new NotebookAiConversation();
            for (int i=0;i<NotebookAiConversation.MAX_MESSAGES/2;i++) full=full.question("q"+i).answer("a"+i);
            NotebookAiConversation limit=full; fails(IllegalArgumentException.class,()->limit.question("beyond"));
            String tooLong=String.join("",Collections.nCopies(NotebookAiConversation.MAX_MESSAGE_BYTES/3+1,"中"));
            fails(IllegalArgumentException.class,()->new NotebookAiConversation().question(tooLong));
            fails(IllegalArgumentException.class,()->new NotebookAiConversation().question("q").question("duplicate"));
            fails(IllegalArgumentException.class,()->new NotebookAiConversation().answer("orphan"));
            check(NotebookAiConversation.read(directory,config(NotebookAiConfig.Provider.DEEPSEEK).profile()).messages.isEmpty(),"Another provider never receives previous provider's transcript");
        } finally { delete(directory.toPath()); }
    }
    private static void protocols() throws Exception {
        NotebookAiConversation chat=new NotebookAiConversation().question("one").answer("**two**").question("three");
        for (NotebookAiConfig.Provider provider:NotebookAiConfig.Provider.values()) {
            NotebookAiConfig config=config(provider); JSONObject request=NotebookAiClient.request(config,chat.messages);
            boolean anthropic=provider==NotebookAiConfig.Provider.ANTHROPIC;
            check(request.getString("model").equals(config.model)&&!request.getBoolean("stream"),"Editable model with one complete e-ink reply");
            JSONArray messages=request.getJSONArray("messages");
            check(messages.length()==(anthropic?3:4)&&messages.getJSONObject(messages.length()-1).getString("content").equals("three"),"Full ordered follow-up context");
            if (anthropic) check(request.getString("system").contains("Markdown")&&request.getInt("max_tokens")==8192&&!messages.getJSONObject(0).getString("role").equals("system"),"Anthropic separate system field");
            else check(messages.getJSONObject(0).getString("content").equals(NotebookAiClient.SYSTEM),"Markdown system prompt");
            if (provider==NotebookAiConfig.Provider.OPENAI) check(!request.getBoolean("store")&&request.has("max_completion_tokens"),"OpenAI disables stored completion and uses current output token field");
            check(!request.toString().contains(config.apiKey),"Credentials are only HTTP headers");
            String data=anthropic?"{\"content\":[{\"type\":\"thinking\",\"thinking\":\"private reasoning\"},{\"type\":\"text\",\"text\":\"# Answer\\n$E$\"}],\"stop_reason\":\"max_tokens\"}"
                :"{\"choices\":[{\"message\":{\"content\":\"# Answer\\n$E$\"},\"finish_reason\":\"length\"}]}";
            NotebookAiClient.Reply reply=NotebookAiClient.response(config,data);
            check(reply.text.equals("# Answer\n$E$")&&reply.truncated,"Extract answer, keep Markdown, report output truncation");
        }
        NotebookAiConfig config=config(NotebookAiConfig.Provider.OPENAI);
        check(NotebookAiClient.response(config,"{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"parts\"}]}}]}").text.equals("parts"),"OpenAI-compatible text blocks");
        check(NotebookAiClient.response(config,"{\"choices\":[{\"message\":{\"content\":null,\"refusal\":\"Cannot comply\"}}]}").text.equals("Cannot comply"),"Refusal remains a visible assistant reply");
        fails(IOException.class,()->NotebookAiClient.response(config,"{\"choices\":[{\"message\":{\"content\":null}}]}"));
        fails(JSONException.class,()->NotebookAiClient.response(config,"{\"choices\":[]}"));
    }
    private static final class Connection extends HttpURLConnection {
        int status=200,opens; boolean disconnected; String response="{\"choices\":[{\"message\":{\"content\":\"**Success**\"},\"finish_reason\":\"stop\"}]}";
        ByteArrayOutputStream output=new ByteArrayOutputStream(); InputStream input;
        Connection() throws Exception { super(new URL("https://ai.example/v1/chat/completions")); }
        @Override public void disconnect() { disconnected=true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
        @Override public OutputStream getOutputStream() { opens++; return output; }
        @Override public int getResponseCode() { return status; }
        @Override public InputStream getInputStream() { return input==null?new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)):input; }
    }
    private static void transport() throws Exception {
        NotebookAiConversation history=new NotebookAiConversation().question("question");
        for (NotebookAiConfig.Provider provider:NotebookAiConfig.Provider.values()) {
            Connection connection=new Connection();
            if (provider==NotebookAiConfig.Provider.ANTHROPIC) connection.response="{\"content\":[{\"type\":\"text\",\"text\":\"**Success**\"}],\"stop_reason\":\"end_turn\"}";
            NotebookAiClient client=new NotebookAiClient(url->{check(url.toString().equals(config(provider).endpoint()),"Request reaches configured endpoint only"); return connection;});
            check(client.complete(config(provider),history.messages,new NotebookAiClient.Cancellation()).text.equals("**Success**"),"Successful transport");
            check(connection.disconnected&&!connection.getInstanceFollowRedirects()&&connection.getConnectTimeout()>0&&connection.getReadTimeout()>0,"Always release sockets; finite timeout and no credential redirects");
            check(connection.getRequestMethod().equals("POST")&&connection.getRequestProperty("Content-Type").startsWith("application/json"),"JSON POST");
            check(connection.getRequestProperty("Authorization").contains(config(provider).apiKey),"Correct provider credential header");
            if (provider==NotebookAiConfig.Provider.ANTHROPIC) check("2026-10-21".equals(connection.getRequestProperty("anthropic-version")),"Anthropic version header");
            check(new JSONObject(new String(connection.output.toByteArray(),StandardCharsets.UTF_8)).getJSONArray("messages").length()>0,"Real serialized request body");
        }
        for (int status:new int[]{301,307,400,401,403,429,500}) {
            Connection connection=new Connection(); connection.status=status;
            Exception error=fails(IOException.class,()->new NotebookAiClient(url->connection).complete(config(NotebookAiConfig.Provider.OPENAI),history.messages,new NotebookAiClient.Cancellation()));
            check(connection.disconnected&&!error.getMessage().contains("test-key"),"HTTP errors release socket without leaking key");
        }
        NotebookAiClient.Cancellation cancelled=new NotebookAiClient.Cancellation(); cancelled.cancel(); final int[] opened={0};
        fails(InterruptedIOException.class,()->new NotebookAiClient(url->{opened[0]++; return null;}).complete(config(NotebookAiConfig.Provider.OPENAI),history.messages,cancelled));
        check(opened[0]==0,"Stop before dispatch cannot open a network connection");
        Connection invalid=new Connection(); invalid.response="not JSON";
        fails(IOException.class,()->new NotebookAiClient(url->invalid).complete(config(NotebookAiConfig.Provider.OPENAI),history.messages,new NotebookAiClient.Cancellation()));
        Connection oversized=new Connection(); oversized.input=new ByteArrayInputStream(new byte[2*1024*1024+1]);
        fails(IOException.class,()->new NotebookAiClient(url->oversized).complete(config(NotebookAiConfig.Provider.OPENAI),history.messages,new NotebookAiClient.Cancellation()));
        Connection stopped=new Connection(); NotebookAiClient.Cancellation duringRead=new NotebookAiClient.Cancellation();
        stopped.input=new ByteArrayInputStream(new byte[10]) { @Override public synchronized int read(byte[] b,int offset,int length) { duringRead.cancel(); return super.read(b,offset,length); } };
        fails(InterruptedIOException.class,()->new NotebookAiClient(url->stopped).complete(config(NotebookAiConfig.Provider.OPENAI),history.messages,duringRead));
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> work=new ArrayDeque<>();
        public void execute(Runnable action) { work.add(action); }
        void drain() { while (!work.isEmpty()) work.remove().run(); }
    }
    private static void lifecycle() throws Exception {
        File directory=Files.createTempDirectory("ai-chat-session-").toFile(); NotebookAiConfig config=config(NotebookAiConfig.Provider.OPENAI);
        try {
            Queue storage=new Queue(),network=new Queue(),main=new Queue(); final int[] calls={0};
            NotebookAiSession.Transport transport=(settings,chat,cancel)->{ calls[0]++; return new NotebookAiClient.Reply("# Answer",false); };
            NotebookAiSession session=new NotebookAiSession(directory,config,main::execute,()->{},storage,network,transport);
            session.load(); check(!session.loaded,"Load stays off UI thread"); storage.drain(); main.drain(); check(session.loaded,"Load callback enables chat");
            session.draft("draft"); storage.drain(); main.drain();
            session.send("first"); check(session.busy&&calls[0]==0&&network.work.isEmpty(),"Do not bill a request before its question is durable");
            storage.drain(); main.drain();
            check(NotebookAiHistory.read(directory,session.id).chat.awaitingReply()&&network.work.size()==1,"Durable question precedes network worker");
            session.send("duplicate"); check(network.work.size()==1,"Repeated Send cannot duplicate a paid request");
            session.stop(); network.drain(); main.drain(); check(calls[0]==0&&session.chat.awaitingReply(),"Stop before network begins skips provider and retains Retry");
            session.send(""); storage.drain(); main.drain(); network.drain();
            session.stop(); main.drain(); check(session.chat.awaitingReply(),"Stop rejects an already queued successful reply");
            session.send(""); storage.drain(); main.drain(); network.drain(); main.drain(); storage.drain(); main.drain();
            check(!session.busy&&session.chat.messages.size()==2&&session.chat.lastAnswer().equals("# Answer"),"Retry completes the original turn once");
            check(NotebookAiHistory.read(directory,session.id).chat.messages.size()==2,"Assistant reply is durable");
            session.send("second"); storage.drain(); main.drain(); network.drain(); session.close(); main.drain();
            check(session.chat.messages.size()==3,"Closing a window rejects stale network success");
            NotebookAiSession reopened=new NotebookAiSession(directory,config,session.id,main::execute,()->{},storage,network,transport);
            reopened.load(); storage.drain(); main.drain(); check(reopened.chat.awaitingReply(),"Reopen restores interrupted turn");
            String archived=reopened.id;
            reopened.send(""); storage.drain(); main.drain(); network.drain(); reopened.clear(); main.drain(); storage.drain(); main.drain();
            check(reopened.chat.messages.isEmpty()&&!reopened.id.equals(archived)&&NotebookAiHistory.read(directory,archived).chat.awaitingReply(),"New chat rejects prior reply and preserves the old session for reopening");
            reopened.draft("survives close"); reopened.close(); storage.drain(); main.drain();
            check(NotebookAiHistory.read(directory,reopened.id).chat.draft.equals("survives close"),"Queued draft flush survives closing and activity destruction");
            File blocker=new File(directory,"not-a-directory"); Files.write(blocker.toPath(),new byte[]{1});
            NotebookAiSession failed=new NotebookAiSession(blocker,config,main::execute,()->{},storage,network,transport);
            failed.load(); storage.drain(); main.drain(); int before=calls[0]; failed.send("cannot save"); storage.drain(); main.drain(); network.drain();
            check(!failed.busy&&failed.saveFailed&&failed.chat.awaitingReply()&&before==calls[0],"Storage failure preserves question and prevents paid request");
            File saved=new File(new File(directory,"ai-chat-sessions"),archived+".chat"); Files.write(saved.toPath(),new byte[]{0});
            NotebookAiSession corrupt=new NotebookAiSession(directory,config,archived,main::execute,()->{},storage,network,transport);
            corrupt.load(); storage.drain(); main.drain(); corrupt.send("must not replace");
            check(corrupt.loadFailed&&Files.size(saved.toPath())==1,"Corrupt history is not silently overwritten");
            corrupt.clear(); storage.drain(); main.drain(); check(!corrupt.loadFailed&&corrupt.chat.isEmpty()&&Files.size(saved.toPath())==1,"New chat leaves the damaged saved session untouched for explicit deletion or recovery");
        } finally { delete(directory.toPath()); }
    }
    private static void models() throws Exception {
        for (NotebookAiConfig.Provider provider:NotebookAiConfig.Provider.values()) {
            NotebookAiConfig config=config(provider); Connection connection=new Connection();
            connection.response="{\"data\":[{\"id\":\"z-model\"},{\"id\":\"a-model\"},{\"id\":\"z-model\"}]}";
            NotebookAiClient client=new NotebookAiClient(url->{check(url.toString().equals(config.modelsEndpoint()),"Model fetch uses base /models, including full chat URLs");return connection;});
            check(client.models(config,new NotebookAiClient.Cancellation()).equals(Arrays.asList("a-model","z-model")),"Provider model IDs are sorted and deduplicated");
            check(connection.getRequestMethod().equals("GET")&&connection.opens==0&&connection.disconnected&&!connection.getInstanceFollowRedirects(),"Model discovery sends no prompt and releases authenticated GET");
            check(connection.getRequestProperty("Authorization").contains(config.apiKey),"Model catalog uses the entered key");
            check(new NotebookAiConfig(provider,config.endpoint(),"",config.apiKey).modelsEndpoint().equals(config.modelsEndpoint()),"Model discovery works before a model is selected");
        }
        ArrayDeque<Connection> pages=new ArrayDeque<>(); Connection first=new Connection(),second=new Connection();
        first.response="{\"data\":[{\"id\":\"first\"}],\"has_more\":true,\"last_id\":\"cursor /+\"}";
        second.response="{\"data\":[{\"id\":\"second\"}],\"has_more\":false}"; pages.add(first);pages.add(second);
        List<String> urls=new ArrayList<>();
        List<String> listed=new NotebookAiClient(url->{urls.add(url.toString());return pages.remove();}).models(config(NotebookAiConfig.Provider.ANTHROPIC),new NotebookAiClient.Cancellation());
        check(listed.equals(Arrays.asList("first","second"))&&urls.get(1).contains("?after_id=cursor+%2F%2B"),"Anthropic catalog pagination encodes opaque cursor");
        for (int status:new int[]{302,401,403,404,429,500}) {
            Connection error=new Connection(); error.status=status;
            fails(IOException.class,()->new NotebookAiClient(url->error).models(config(NotebookAiConfig.Provider.OPENAI),new NotebookAiClient.Cancellation()));
            check(error.disconnected,"Model-list failures always release the connection");
        }
        for (String data:Arrays.asList("not JSON","{\"data\":[]}","{\"data\":[{\"id\":\"bad\\n\\nmodel\"}]}","{\"data\":[],\"has_more\":true}")) {
            Connection error=new Connection(); error.response=data;
            fails(IOException.class,()->new NotebookAiClient(url->error).models(config(NotebookAiConfig.Provider.OPENAI),new NotebookAiClient.Cancellation()));
        }
        NotebookAiClient.Cancellation cancel=new NotebookAiClient.Cancellation(); cancel.cancel();
        fails(InterruptedIOException.class,()->new NotebookAiClient(url->{throw new AssertionError("Cancelled model fetch opened a socket");}).models(config(NotebookAiConfig.Provider.GEMINI),cancel));
    }
    private static void attachments() throws Exception {
        byte[] pixels=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAXpeqz8AAAAASUVORK5CYII=");
        NotebookAiAttachment image=new NotebookAiAttachment("Handwriting selection",pixels); pixels[pixels.length-1]^=1;
        check(!Arrays.equals(pixels,image.bytes()),"Selection pixels are copied, independent of later notebook changes");
        NotebookAiConversation chat=new NotebookAiConversation().draft("Question").attach(image);
        check(chat.messages.isEmpty()&&chat.draftImages.size()==1,"Append prepares a draft without sending or changing history");
        File directory=Files.createTempDirectory("ai-images-").toFile(); String profile=config(NotebookAiConfig.Provider.OPENAI).profile();
        try {
            NotebookAiConversation.write(directory,profile,chat); NotebookAiConversation restored=NotebookAiConversation.read(directory,profile);
            check(restored.draft.equals("Question")&&Arrays.equals(restored.draftImages.get(0).bytes(),image.bytes()),"Unsent images and prompt survive reopening");
            check(restored.removeImage(0).draftImages.isEmpty(),"User can remove an attachment before Send");
            restored=restored.question("What is written here?").answer("Answer").question("Explain the image again");
            NotebookAiConversation.write(directory,profile,restored); restored=NotebookAiConversation.read(directory,profile);
            check(restored.draftImages.isEmpty()&&restored.messages.get(0).images.size()==1,"Send moves draft attachments into persistent user history");
            for (NotebookAiConfig.Provider provider:NotebookAiConfig.Provider.values()) {
                JSONArray history=NotebookAiClient.request(config(provider),restored.messages).getJSONArray("messages");
                JSONArray parts=history.getJSONObject(provider==NotebookAiConfig.Provider.ANTHROPIC?0:1).getJSONArray("content");
                check(parts.getJSONObject(0).getString("text").equals("What is written here?"),"Images keep the accompanying user prompt");
                if (provider==NotebookAiConfig.Provider.ANTHROPIC) check(parts.getJSONObject(1).getJSONObject("source").getString("data").equals(image.base64()),"Native Anthropic base64 image content");
                else check(parts.getJSONObject(1).getJSONObject("image_url").getString("url").equals(image.dataUrl()),"OpenAI-compatible vision image content");
                check(history.getJSONObject(history.length()-1).getString("content").equals("Explain the image again"),"Follow-ups retain earlier images without inventing a new attachment");
            }
            NotebookAiConversation pending=new NotebookAiConversation().attach(image).question("");
            check(!pending.messages.get(0).content.isEmpty(),"Image-only Send provides a useful prompt");
            NotebookAiConversation four=new NotebookAiConversation(); for(int i=0;i<4;i++)four=four.attach(image);
            NotebookAiConversation full=four; fails(IllegalArgumentException.class,()->full.attach(image));
            fails(IllegalArgumentException.class,()->new NotebookAiAttachment("bad",new byte[1024*1024+1]));
            fails(IllegalArgumentException.class,()->new NotebookAiConversation.Message("assistant","answer",Arrays.asList(image)));
            // An actual v1 transcript, rather than a freshly written v2 fixture.
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream body=new DataOutputStream(bytes);
            body.writeInt(1);body.writeBoolean(true);byte[] question="Legacy question".getBytes(StandardCharsets.UTF_8);body.writeInt(question.length);body.write(question);body.writeInt(0);body.flush();
            java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(bytes.toByteArray());
            try(DataOutputStream out=new DataOutputStream(new FileOutputStream(new File(directory,"ai-chat-"+profile+".bin")))) {out.writeInt(0x53414931);out.writeLong(crc.getValue());out.write(bytes.toByteArray());}
            NotebookAiConversation legacy=NotebookAiConversation.read(directory,profile);
            check(legacy.awaitingReply()&&legacy.messages.get(0).content.equals("Legacy question")&&legacy.messages.get(0).images.isEmpty(),"Existing alpha.1 chat files remain readable without conversion on open");
            Queue storage=new Queue(),network=new Queue(),main=new Queue();final int[] requests={0};
            NotebookAiConfig settings=config(NotebookAiConfig.Provider.GEMINI);
            NotebookAiSession session=new NotebookAiSession(directory,settings,main::execute,()->{},storage,network,
                (connection,history,cancel)->{requests[0]++;return new NotebookAiClient.Reply("First answer",false);});
            session.load();storage.drain();main.drain();session.attach(image);storage.drain();main.drain();
            check(requests[0]==0&&session.chat.messages.isEmpty(),"Appending an image saves a draft without any provider call");
            session.send("First question");storage.drain();main.drain();session.attach(image);session.draft("Next question");storage.drain();main.drain();
            network.drain();main.drain();storage.drain();main.drain();
            check(requests[0]==1&&session.chat.messages.get(0).images.size()==1&&session.chat.draftImages.size()==1&&session.chat.draft.equals("Next question"),"Appending during generation prepares the next message without altering the in-flight question");
            session.close();storage.drain();main.drain();
            check(NotebookAiHistory.read(directory,session.id).chat.draftImages.size()==1,"Unsent appended image remains after closing the chat");
        } finally {delete(directory.toPath());}
    }
    private static void delete(Path path) throws IOException {
        try (java.util.stream.Stream<Path> paths=Files.walk(path)) {
            for (Path item:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(item);
        }
    }
}
