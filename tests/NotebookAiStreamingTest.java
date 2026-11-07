package org.eink.inkprobe.notebook;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Real provider SSE decoding and durable session behavior without network calls. */
public final class NotebookAiStreamingTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static NotebookAiConfig config(NotebookAiConfig.Provider provider){
        return new NotebookAiConfig(provider,provider==NotebookAiConfig.Provider.CUSTOM?"https://example.org/v1":provider.baseUrl,"model","test-key");
    }
    private static InputStream input(String value){return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));}
    private static String event(String data){return "data: "+data+"\n\n";}
    public static void main(String[] args)throws Exception{
        protocols();liveChunks();transport();lifecycle();
        System.out.println("NotebookAiStreaming: "+checks+" assertions passed (incremental SSE, provider protocols, failure, cancellation and durable partial replies). No live API calls.");
    }
    private static void liveChunks()throws Exception{
        PipedInputStream input=new PipedInputStream();PipedOutputStream output=new PipedOutputStream(input);
        CountDownLatch visible=new CountDownLatch(1);List<String> updates=Collections.synchronizedList(new ArrayList<>());
        FutureTask<NotebookAiClient.Reply> task=new FutureTask<>(()->NotebookAiClient.streamResponse(config(NotebookAiConfig.Provider.OPENAI),false,input,
            new NotebookAiClient.Cancellation(),text->{updates.add(text);visible.countDown();}));
        Thread worker=new Thread(task,"stream-fixture");worker.start();
        try{
            output.write(event("{\"choices\":[{\"delta\":{\"content\":\"first\"}}]}").getBytes(StandardCharsets.UTF_8));output.flush();
            check(visible.await(3,TimeUnit.SECONDS)&&updates.get(0).equals("first")&&!task.isDone(),"a partial answer is delivered while the connection remains open");
            output.write(event("{\"choices\":[{\"delta\":{\"content\":\" next\"},\"finish_reason\":\"stop\"}]}").getBytes(StandardCharsets.UTF_8));output.flush();
            check(task.get(3,TimeUnit.SECONDS).text.equals("first next"),"the final event completes without waiting for the socket to close");
        }finally{output.close();input.close();worker.join(3000);}
    }
    private static void protocols()throws Exception{
        List<String> progress=new ArrayList<>();
        String open=event("{\"choices\":[{\"delta\":{\"content\":\"Hi \",\"reasoning_content\":\"secret\"}}]}")
            +event("{\"choices\":[{\"delta\":{\"content\":\"ψ\"},\"finish_reason\":\"length\"}]}")+event("[DONE]");
        NotebookAiClient.Reply reply=NotebookAiClient.streamResponse(config(NotebookAiConfig.Provider.OPENAI),false,input(open),new NotebookAiClient.Cancellation(),progress::add);
        check(reply.text.equals("Hi ψ")&&reply.truncated,"OpenAI deltas assemble UTF-8 and report output limits");
        check(progress.get(0).equals("Hi ")&&progress.get(progress.size()-1).equals("Hi ψ"),"first response text is delivered before the completed reply");
        check(!progress.toString().contains("secret"),"provider reasoning is excluded from visible output");
        String anthropic=event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"secret\"}}")
            +event("{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}")
            +event("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"max_tokens\"}}")+event("{\"type\":\"message_stop\"}");
        reply=NotebookAiClient.streamResponse(config(NotebookAiConfig.Provider.ANTHROPIC),false,input(anthropic),new NotebookAiClient.Cancellation(),v->{});
        check(reply.text.equals("Hello")&&reply.truncated,"Anthropic text deltas and terminal event exclude thinking");
        String gemini=event("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"secret\",\"thought\":true},{\"text\":\"One\"}]}}]}")
            +event("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\" two\"}]},\"finishReason\":\"STOP\"}]}");
        reply=NotebookAiClient.streamResponse(config(NotebookAiConfig.Provider.GEMINI),true,input(gemini),new NotebookAiClient.Cancellation(),v->{});
        check(reply.text.equals("One two")&&!reply.truncated,"native Gemini SSE consumes visible parts and final reason");
        for(String broken:new String[]{event("{\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}"),event("not-json"),event("{\"error\":{\"message\":\"private\"}}")}){
            try{NotebookAiClient.streamResponse(config(NotebookAiConfig.Provider.CUSTOM),false,input(broken),new NotebookAiClient.Cancellation(),v->{});throw new AssertionError("accepted broken stream");}
            catch(IOException expected){check(!expected.getMessage().contains("private"),"broken or interrupted streams fail without leaking provider details");}
        }
        NotebookAiClient.Cancellation cancelled=new NotebookAiClient.Cancellation();cancelled.cancel();
        try{NotebookAiClient.streamResponse(config(NotebookAiConfig.Provider.OPENAI),false,input(open),cancelled,v->{});throw new AssertionError("accepted cancelled stream");}
        catch(InterruptedIOException expected){check(true,"cancelled stream stops reading");}
    }
    private static final class Connection extends HttpURLConnection{
        final ByteArrayOutputStream output=new ByteArrayOutputStream();String type="text/event-stream",response;boolean disconnected;
        Connection(String response)throws Exception{super(new URL("https://example.org/v1/chat/completions"));this.response=response;}
        public void disconnect(){disconnected=true;}public boolean usingProxy(){return false;}public void connect(){}
        public OutputStream getOutputStream(){return output;}public int getResponseCode(){return 200;}public String getContentType(){return type;}
        public InputStream getInputStream(){return input(response);}
    }
    private static void transport()throws Exception{
        String sse=event("{\"choices\":[{\"delta\":{\"content\":\"streamed\"},\"finish_reason\":\"stop\"}]}")+event("[DONE]");
        Connection c=new Connection(sse);List<String> progress=new ArrayList<>();
        NotebookAiClient.Reply reply=new NotebookAiClient(url->c).stream(config(NotebookAiConfig.Provider.CUSTOM),new NotebookAiConversation().question("q").messages,new NotebookAiClient.Cancellation(),progress::add);
        check(reply.text.equals("streamed")&&new JSONObject(c.output.toString("UTF-8")).getBoolean("stream"),"transport explicitly requests streaming");
        check(c.disconnected&&c.getRequestProperty("Accept").contains("text/event-stream"),"stream transport closes the socket and negotiates SSE");
        Connection fallback=new Connection("{\"choices\":[{\"message\":{\"content\":\"complete\"},\"finish_reason\":\"stop\"}]}");fallback.type="application/json; charset=utf-8";
        reply=new NotebookAiClient(url->fallback).stream(config(NotebookAiConfig.Provider.CUSTOM),new NotebookAiConversation().question("q").messages,new NotebookAiClient.Cancellation(),progress::add);
        check(reply.text.equals("complete")&&progress.get(progress.size()-1).equals("complete"),"custom providers returning JSON still work");
        final Connection noType=new Connection("  {\"choices\":[{\"message\":{\"content\":\"no type\"},\"finish_reason\":\"stop\"}]}");noType.type=null;
        reply=new NotebookAiClient(url->noType).stream(config(NotebookAiConfig.Provider.CUSTOM),new NotebookAiConversation().question("q").messages,new NotebookAiClient.Cancellation(),progress::add);
        check(reply.text.equals("no type"),"a JSON fallback works even if a custom server omits its content type");
    }
    private static final class Queue implements Executor{
        final ArrayDeque<Runnable> work=new ArrayDeque<>();public void execute(Runnable value){work.add(value);}void drain(){while(!work.isEmpty())work.remove().run();}
    }
    private static void lifecycle()throws Exception{
        File directory=Files.createTempDirectory("ai-stream-").toFile();Queue storage=new Queue(),network=new Queue(),ui=new Queue();
        try{
            NotebookAiSession.Transport transport=new NotebookAiSession.Transport(){
                public NotebookAiClient.Reply complete(NotebookAiConfig c,NotebookAiConversation h,NotebookAiClient.Cancellation cancel){throw new AssertionError("not streaming");}
                public NotebookAiClient.Reply stream(NotebookAiConfig c,NotebookAiConversation h,NotebookAiClient.Cancellation cancel,Consumer<String> update){
                    update.accept("Partial");update.accept("Partial answer");return new NotebookAiClient.Reply("Partial answer",false);
                }
            };
            NotebookAiSession session=new NotebookAiSession(directory,config(NotebookAiConfig.Provider.OPENAI),ui::execute,()->{},storage,network,transport);
            session.load();storage.drain();ui.drain();session.send("question");storage.drain();ui.drain();network.drain();
            ui.work.remove().run();
            check(session.busy&&session.chat.awaitingReply()&&session.displayMessages().get(1).content.equals("Partial"),"partial reply is visible before the final network callback");
            session.stop();ui.drain();storage.drain();ui.drain();
            check(!session.busy&&session.chat.lastAnswer().equals("Partial")&&!session.chat.awaitingReply(),"Stop saves the visible partial and rejects later queued updates");
            check(NotebookAiHistory.read(directory,session.id).chat.lastAnswer().equals("Partial"),"stopped partial reply survives reopening");
            session.send("next");storage.drain();ui.drain();network.drain();ui.drain();storage.drain();ui.drain();
            check(session.chat.lastAnswer().equals("Partial answer")&&session.streamText.isEmpty(),"a later streamed turn completes and clears transient text");
            session.close();
            NotebookAiSession.Transport failure=new NotebookAiSession.Transport(){
                public NotebookAiClient.Reply complete(NotebookAiConfig c,NotebookAiConversation h,NotebookAiClient.Cancellation cancel){return null;}
                public NotebookAiClient.Reply stream(NotebookAiConfig c,NotebookAiConversation h,NotebookAiClient.Cancellation cancel,Consumer<String> update)throws IOException{
                    update.accept("Kept before interruption");throw new IOException("Connection interrupted");
                }
            };
            NotebookAiSession interrupted=new NotebookAiSession(directory,config(NotebookAiConfig.Provider.OPENAI),ui::execute,()->{},storage,network,failure);
            interrupted.load();storage.drain();ui.drain();interrupted.send("question");storage.drain();ui.drain();network.drain();ui.drain();storage.drain();ui.drain();
            check(interrupted.status.contains("Partial reply saved")&&NotebookAiHistory.read(directory,interrupted.id).chat.lastAnswer().equals("Kept before interruption"),"an interrupted stream saves its partial reply and reports the interruption");interrupted.close();
            String block=String.join("",Collections.nCopies(120000,"x")),largeReply=String.join("",Collections.nCopies(100000,"y"));
            NotebookAiSession.Transport large=new NotebookAiSession.Transport(){
                public NotebookAiClient.Reply complete(NotebookAiConfig c,NotebookAiConversation h,NotebookAiClient.Cancellation cancel){return null;}
                public NotebookAiClient.Reply stream(NotebookAiConfig c,NotebookAiConversation h,NotebookAiClient.Cancellation cancel,Consumer<String> update){update.accept(largeReply);return new NotebookAiClient.Reply(largeReply,false);}
            };
            NotebookAiSession full=new NotebookAiSession(directory,config(NotebookAiConfig.Provider.OPENAI),ui::execute,()->{},storage,network,large);
            full.load();storage.drain();ui.drain();for(int i=0;i<4;i++)full.chat=full.chat.question(block).answer(block);
            full.send("last");storage.drain();ui.drain();network.drain();ui.drain();storage.drain();ui.drain();
            check(full.chat.awaitingReply()&&full.displayAnswer().equals(largeReply)&&full.status.contains("Copy"),"a reply exceeding the archive budget remains visible and copyable");full.close();
        }finally{NotebookBackupDelete.delete(directory);}
    }
    private static final class NotebookBackupDelete{
        static void delete(File file)throws IOException{if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);}Files.deleteIfExists(file.toPath());}
    }
}
