package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executor;

/** Real archive files and deterministic worker queues; no provider calls or Android stubs. */
public final class NotebookAiHistoryTest {
    private static int assertions;
    private interface Checked { void run() throws Exception; }
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static void fails(Class<? extends Exception> expected,Checked work)throws Exception{
        try{work.run();}catch(Exception error){check(expected.isInstance(error),"Expected "+expected+", got "+error);return;}
        throw new AssertionError("Expected "+expected);
    }
    private static NotebookAiConfig config(NotebookAiConfig.Provider provider){
        return new NotebookAiConfig(provider,provider.baseUrl,provider.model,"not-a-real-secret-key");
    }
    private static NotebookAiAttachment image(){
        return new NotebookAiAttachment("Handwriting selection",Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAXpeqz8AAAAASUVORK5CYII="));
    }
    private static File record(File directory,String id){return new File(new File(directory,"ai-chat-sessions"),id+".chat");}
    public static void main(String[] args)throws Exception{
        files();legacy();sessions();
        System.out.println("NotebookAiHistory: "+assertions+" assertions passed (independent sessions, migration, metadata, atomic saves, reopen/delete and request isolation).");
    }
    private static void files()throws Exception{
        File directory=Files.createTempDirectory("ai-history-files-").toFile();
        NotebookAiConfig openai=config(NotebookAiConfig.Provider.OPENAI),claude=config(NotebookAiConfig.Provider.ANTHROPIC);
        List<NotebookAiConfig> known=Arrays.asList(openai,claude);
        try{
            String first=NotebookAiHistory.newId(),second=NotebookAiHistory.newId(),blank=NotebookAiHistory.newId();
            check(NotebookAiHistory.restoreId(first,openai.profile(),openai.profile()).equals(first),"Rotation restores the active session when Settings is unchanged");
            check(NotebookAiHistory.restoreId(first,openai.profile(),claude.profile())==null,"Changing provider/model in Settings starts a new default session");
            check(NotebookAiHistory.restoreId(first,openai.profile(),new NotebookAiConfig(openai.provider,openai.baseUrl,openai.model,"rotated-key").profile()).equals(first),"Key rotation preserves the active session");
            check(NotebookAiHistory.restoreId("../bad",openai.profile(),openai.profile())==null&&NotebookAiHistory.restoreId(null,openai.profile(),openai.profile())==null,"Invalid or absent lifecycle IDs safely start a fresh chat");
            check(openai.sameEndpoint(new NotebookAiConfig(openai.provider,openai.endpoint()+"/","another-model","rotated-key")),"Credential matching permits key/model changes on the same exact endpoint");
            check(!openai.sameEndpoint(new NotebookAiConfig(openai.provider,"https://another.example/v1",openai.model,"new-key")),"A saved endpoint cannot inherit a key configured for a different server");
            NotebookAiHistory.save(directory,blank,openai,10,new NotebookAiConversation());
            check(NotebookAiHistory.list(directory,known).isEmpty(),"Opening an untouched new session does not clutter history");
            NotebookAiConversation a=new NotebookAiConversation().question("First question\nwith another line").answer("# First answer").draft("Keep my draft").attach(image());
            NotebookAiConversation b=new NotebookAiConversation().question("Second provider's question");
            NotebookAiHistory.save(directory,first,openai,10,a);
            long later=System.currentTimeMillis()+100;
            NotebookAiHistory.save(directory,second,claude,later,b);
            List<NotebookAiHistory.Entry> entries=NotebookAiHistory.list(directory,known);
            check(entries.size()==2&&entries.get(0).id.equals(second),"All providers are listed together with most recent first");
            check(entries.get(1).title.equals("First question with another line")&&entries.get(1).messages==2&&entries.get(1).draft,"Title and counts describe the archived conversation and unsent draft");
            check(entries.get(0).config.provider==NotebookAiConfig.Provider.ANTHROPIC&&entries.get(1).config.model.equals(openai.model),"Original provider, endpoint and model are preserved per session");
            check(entries.get(0).config.apiKey.isEmpty()&&!new String(Files.readAllBytes(record(directory,first).toPath()),StandardCharsets.ISO_8859_1).contains(openai.apiKey),"History metadata never stores the credential");
            NotebookAiHistory.Saved restored=NotebookAiHistory.read(directory,first);
            check(restored.chat.lastAnswer().equals(a.lastAnswer())&&restored.chat.draft.equals(a.draft)&&Arrays.equals(restored.chat.draftImages.get(0).bytes(),a.draftImages.get(0).bytes()),"Text, answer and image draft reopen exactly");
            check(restored.entry.created==10&&restored.entry.config.apiKey.isEmpty(),"Creation time and public configuration survive reopening");
            byte[] previous=Files.readAllBytes(record(directory,first).toPath());
            File temporary=new File(record(directory,first).getPath()+".tmp");temporary.mkdir();Files.write(new File(temporary,"blocked").toPath(),new byte[]{1});
            fails(IOException.class,()->NotebookAiHistory.save(directory,first,openai,10,a.draft("replacement")));
            check(Arrays.equals(previous,Files.readAllBytes(record(directory,first).toPath())),"Failed atomic replacement preserves the exact previous history file");
            delete(temporary.toPath());
            check(NotebookAiHistory.list(directory,known).size()==2,"An interrupted staging file never becomes a history row");
            NotebookAiHistory.save(directory,blank,openai,10,new NotebookAiConversation().draft("temporary draft"));
            NotebookAiHistory.save(directory,blank,openai,10,new NotebookAiConversation());
            check(NotebookAiHistory.read(directory,blank)==null,"Erasing an unsent draft removes the old draft instead of restoring stale text later");
            String title=NotebookAiHistory.title(new NotebookAiConversation().question(String.join("",Collections.nCopies(100,"量😀"))));
            check(title.codePointCount(0,title.length())==73&&!Character.isHighSurrogate(title.charAt(title.length()-2)),"Automatic titles truncate without splitting Unicode characters");
            for(String id:Arrays.asList("../escape","1-1-1-1-1","not-a-session"))fails(IllegalArgumentException.class,()->NotebookAiHistory.read(directory,id));
            byte[] corrupt=previous.clone();corrupt[corrupt.length-1]^=1;Files.write(record(directory,first).toPath(),corrupt);
            check(NotebookAiHistory.list(directory,known).stream().anyMatch(e->e.id.equals(first)&&e.error.isEmpty()),"History reads only the bounded metadata header, not every image payload");
            fails(IOException.class,()->NotebookAiHistory.read(directory,first));
            corrupt=previous.clone();corrupt[18]^=1;Files.write(record(directory,first).toPath(),corrupt);
            check(NotebookAiHistory.list(directory,known).stream().anyMatch(e->e.id.equals(first)&&!e.error.isEmpty()),"Damaged metadata remains visible as a deletable history entry");
            NotebookAiHistory.delete(directory,first);
            check(!record(directory,first).exists()&&NotebookAiHistory.list(directory,known).size()==1,"Delete removes only the selected conversation and all embedded image/draft data");
            fails(IOException.class,()->NotebookAiHistory.save(directory,first,openai,10,a));
            fails(IOException.class,()->NotebookAiHistory.read(directory,first));
            check(NotebookAiHistory.read(directory,second).chat.messages.get(0).content.equals(b.messages.get(0).content),"Deleting one session preserves another provider's chat");
            File blocked=new File(record(directory,second).getPath()+".tmp");blocked.mkdir();Files.write(new File(blocked,"held").toPath(),new byte[]{1});
            fails(IOException.class,()->NotebookAiHistory.delete(directory,second));
            check(NotebookAiHistory.list(directory,known).stream().anyMatch(e->e.id.equals(second)&&e.error.contains("Deletion pending")),"Partial deletion remains visible for an explicit retry");
            Files.delete(new File(blocked,"held").toPath());NotebookAiHistory.delete(directory,second);
            check(NotebookAiHistory.list(directory,known).isEmpty(),"Retry completes deletion without resurrecting either session");
        }finally{delete(directory.toPath());}
    }
    private static void legacy()throws Exception{
        File directory=Files.createTempDirectory("ai-history-legacy-").toFile();
        NotebookAiConfig known=config(NotebookAiConfig.Provider.OPENAI),unknown=new NotebookAiConfig(NotebookAiConfig.Provider.CUSTOM,"https://previous.example/v1","previous-model","");
        try{
            NotebookAiConversation old=new NotebookAiConversation().attach(image()).question("Old image question").draft("Unsent old draft");
            NotebookAiConversation.write(directory,known.profile(),old);
            NotebookAiConversation.write(directory,unknown.profile(),new NotebookAiConversation().question("Earlier custom provider"));
            List<NotebookAiHistory.Entry> entries=NotebookAiHistory.list(directory,Collections.singletonList(known));
            check(entries.size()==2,"All prior per-configuration histories migrate into separate saved sessions");
            NotebookAiHistory.Entry matched=entries.stream().filter(e->e.profile.equals(known.profile())).findFirst().get();
            NotebookAiHistory.Entry unresolved=entries.stream().filter(e->e.profile.equals(unknown.profile())).findFirst().get();
            check(matched.config!=null&&matched.config.apiKey.isEmpty()&&unresolved.config==null,"Only an exact matching public configuration is inferred for a legacy chat");
            check(NotebookAiHistory.read(directory,matched.id).chat.draft.equals("Unsent old draft")&&NotebookAiHistory.read(directory,matched.id).chat.messages.get(0).images.size()==1,"Legacy images and unsent drafts are preserved");
            check(!new File(directory,"ai-chat-"+known.profile()+".bin").exists(),"Legacy source is retired after the atomic archive is durable");
            check(NotebookAiHistory.list(directory,Arrays.asList(known,unknown)).stream().filter(e->e.id.equals(unresolved.id)).findFirst().get().config!=null,"An older configuration can be resolved later without modifying its messages");
            NotebookAiConversation.write(directory,known.profile(),old);
            check(NotebookAiHistory.list(directory,Collections.singletonList(known)).size()==2,"Retrying migration imports the same source only once");
            NotebookAiHistory.delete(directory,matched.id);NotebookAiConversation.write(directory,known.profile(),old);
            check(NotebookAiHistory.list(directory,Collections.singletonList(known)).size()==1,"A retained legacy copy cannot resurrect an explicitly deleted session");
            NotebookAiConversation.write(directory,known.profile(),new NotebookAiConversation().question("A different later legacy chat"));
            check(NotebookAiHistory.list(directory,Collections.singletonList(known)).size()==2,"Distinct later legacy content is preserved as a separate session");
            File broken=new File(directory,"ai-chat-"+config(NotebookAiConfig.Provider.ANTHROPIC).profile()+".bin");Files.write(broken.toPath(),new byte[]{0});
            NotebookAiHistory.Entry damaged=NotebookAiHistory.list(directory,Collections.singletonList(known)).stream().filter(e->!e.error.isEmpty()).findFirst().get();
            check(broken.length()==1,"A damaged legacy source is retained rather than silently replaced");
            NotebookAiHistory.delete(directory,damaged.id);check(!broken.exists(),"Unusable legacy sessions can be deleted from history");
            // Real v1 text-only file, without using the current writer.
            byte[] text="Version one question".getBytes(StandardCharsets.UTF_8);ByteArrayOutputStream body=new ByteArrayOutputStream();
            try(DataOutputStream out=new DataOutputStream(body)){out.writeInt(1);out.writeBoolean(true);out.writeInt(text.length);out.write(text);out.writeInt(0);}
            java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(body.toByteArray());
            try(DataOutputStream out=new DataOutputStream(new FileOutputStream(new File(directory,"ai-chat-"+known.profile()+".bin")))){out.writeInt(0x53414931);out.writeLong(crc.getValue());out.write(body.toByteArray());}
            check(NotebookAiHistory.list(directory,Collections.singletonList(known)).stream().anyMatch(e->e.title.equals("Version one question")),"Version-1 text sessions migrate alongside version-2 image sessions");
        }finally{delete(directory.toPath());}
    }
    private static final class Queue implements Executor{
        final ArrayDeque<Runnable> work=new ArrayDeque<>();public void execute(Runnable r){work.add(r);}void drain(){while(!work.isEmpty())work.remove().run();}
    }
    private static void sessions()throws Exception{
        File directory=Files.createTempDirectory("ai-history-controller-").toFile();NotebookAiConfig cfg=config(NotebookAiConfig.Provider.OPENAI);
        Queue io=new Queue(),network=new Queue(),ui=new Queue();List<String> sent=new ArrayList<>();
        NotebookAiSession.Transport transport=(config,chat,cancel)->{sent.add(chat.messages.get(0).content+":"+chat.messages.size());return new NotebookAiClient.Reply("Answer",false);};
        try{
            NotebookAiSession first=new NotebookAiSession(directory,cfg,ui::execute,()->{},io,network,transport);
            first.load();io.drain();ui.drain();first.send("one");io.drain();ui.drain();network.drain();ui.drain();io.drain();ui.drain();
            String original=first.id;first.draft("retained draft");first.attach(image());io.drain();ui.drain();
            first.clear();check(first.switching&&first.id.equals(original),"New waits until the previous conversation is saved");io.drain();ui.drain();
            check(!first.id.equals(original)&&first.chat.isEmpty()&&NotebookAiHistory.read(directory,original).chat.draft.equals("retained draft"),"New creates a separate session without overwriting old draft/history");
            first.send("two");io.drain();ui.drain();network.drain();ui.drain();io.drain();ui.drain();
            check(sent.equals(Arrays.asList("one:1","two:1")),"Provider context contains only the chosen session's messages");
            NotebookAiSession reopened=new NotebookAiSession(directory,cfg,original,ui::execute,()->{},io,network,transport);reopened.load();io.drain();ui.drain();
            check(reopened.chat.messages.size()==2&&reopened.chat.draftImages.size()==1&&reopened.chat.draft.equals("retained draft"),"Explicit history reopen restores messages and unsent selected content");
            NotebookAiSession fresh=new NotebookAiSession(directory,cfg,ui::execute,()->{},io,network,transport);fresh.load();io.drain();ui.drain();
            check(fresh.chat.isEmpty()&&!fresh.id.equals(first.id)&&!fresh.id.equals(original),"Each ordinary chat opening starts a fresh session by default");
            fresh.sendProblem="Configure this saved endpoint first";fresh.send("must stay local");
            check(sent.size()==2&&fresh.chat.isEmpty(),"Read-only history cannot send content or credentials to an unmatched endpoint");fresh.sendProblem="";
            NotebookAiConfig noKey=new NotebookAiConfig(cfg.provider,cfg.baseUrl,cfg.model,"");
            NotebookAiSession offline=new NotebookAiSession(directory,noKey,original,ui::execute,()->{},io,network,transport);offline.load();io.drain();ui.drain();offline.send("no key");
            check(!offline.loadFailed&&offline.chat.messages.size()==2&&sent.size()==2,"Saved history is readable without a key and reopening never calls the provider");
            NotebookAiSession wrong=new NotebookAiSession(directory,config(NotebookAiConfig.Provider.ANTHROPIC),original,ui::execute,()->{},io,network,transport);wrong.load();io.drain();ui.drain();
            check(wrong.loadFailed&&sent.size()==2,"A saved chat cannot silently switch provider destinations");
            reopened.send("pending");io.drain();ui.drain();network.drain();
            reopened.delete(original,error->{check(error==null,"Deleting current session succeeds");});io.drain();ui.drain();
            check(reopened.chat.isEmpty()&&!reopened.id.equals(original)&&NotebookAiHistory.deleted(directory,original),"Deleting current chat cancels pending generation and opens a blank replacement");
            fails(IOException.class,()->NotebookAiHistory.read(directory,original));
            first.draft("late update to another session");io.drain();ui.drain();
            check(NotebookAiHistory.read(directory,first.id)!=null,"Another session continues independently after deletion");
            offline.draft("stale editor write");io.drain();ui.drain();
            check(offline.saveFailed&&!record(directory,original).exists(),"A stale reopened window cannot recreate a deleted session");
            offline.clear();check(offline.chat.isEmpty()&&!offline.id.equals(original),"A deleted stale window can start a fresh chat without recreating the deleted one");
            final List<List<NotebookAiHistory.Entry>> lists=new ArrayList<>();fresh.history(Collections.singletonList(cfg),(entries,error)->{check(error==null,"History lists asynchronously");lists.add(entries);});
            io.drain();ui.drain();check(lists.size()==1&&lists.get(0).size()==1,"History contains saved nonempty conversations only");
            NotebookAiHistory.Entry remaining=lists.get(0).get(0);
            fresh.delete(remaining.id,error->{check(error==null,"Deleting another session succeeds");});io.drain();ui.drain();
            check(fresh.chat.isEmpty()&&NotebookAiHistory.list(directory,Collections.singletonList(cfg)).isEmpty(),"Deleting an older session preserves the current blank chat");
        }finally{delete(directory.toPath());}
    }
    private static void delete(Path root)throws IOException{
        try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.deleteIfExists(path);}
    }
}
