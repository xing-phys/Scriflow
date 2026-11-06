package org.eink.inkprobe.notebook;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Window-independent state machine. All state and callbacks belong to the UI dispatcher. */
final class NotebookAiSession {
    // Serialize loads and writes across reopened windows; a late save cannot overwrite a newer chat.
    private static final ExecutorService STORAGE=Executors.newSingleThreadExecutor(r->new Thread(r,"ai-chat-storage"));
    interface Transport { NotebookAiClient.Reply complete(NotebookAiConfig config,NotebookAiConversation chat,NotebookAiClient.Cancellation cancel) throws IOException; }
    private final File directory;
    final NotebookAiConfig config;
    private final Consumer<Runnable> dispatch;
    private final Executor storage,network;
    private final Runnable changed;
    private final Transport transport;
    NotebookAiConversation chat=new NotebookAiConversation();
    boolean loaded,busy,loadFailed,saveFailed,closed,switching;
    String id;
    private long created;
    private final boolean restoring;
    boolean rebindAllowed;
    String sendProblem="";
    interface HistoryCallback { void complete(List<NotebookAiHistory.Entry> entries,String error); }
    interface DeleteCallback { void complete(String error); }
    String status="Loading chat…";
    private int generation;
    private NotebookAiClient.Cancellation cancellation;

    NotebookAiSession(File directory,NotebookAiConfig config,Consumer<Runnable> dispatch,Runnable changed) {
        this(directory,config,null,dispatch,changed);
    }
    NotebookAiSession(File directory,NotebookAiConfig config,String id,Consumer<Runnable> dispatch,Runnable changed) {
        this(directory,config,id,dispatch,changed,STORAGE,r->new Thread(r,"ai-chat-request").start(),
            (settings,chat,cancel)->new NotebookAiClient().complete(settings,chat.messages,cancel));
    }
    NotebookAiSession(File directory,NotebookAiConfig config,Consumer<Runnable> dispatch,Runnable changed,Executor storage,Executor network,Transport transport) {
        this(directory,config,null,dispatch,changed,storage,network,transport);
    }
    NotebookAiSession(File directory,NotebookAiConfig config,String id,Consumer<Runnable> dispatch,Runnable changed,Executor storage,Executor network,Transport transport) {
        this.directory=directory; this.config=config; this.dispatch=dispatch; this.changed=changed;
        this.storage=storage; this.network=network; this.transport=transport;
        restoring=id!=null; this.id=restoring?id:NotebookAiHistory.newId(); NotebookAiHistory.validateId(this.id);
        created=Math.max(1,System.currentTimeMillis());
    }
    void load() {
        final String requested=id;
        storage.execute(()->{
            NotebookAiHistory.Saved value=null; String problem=null;
            try {
                NotebookAiHistory.migrate(directory,Collections.singletonList(config));
                if (restoring) value=NotebookAiHistory.read(directory,requested);
                if (value!=null&&!value.entry.profile.equals(config.profile())&&(value.entry.config!=null||!rebindAllowed))
                    throw new IOException("This chat belongs to a different AI configuration");
                if (value!=null&&value.entry.config!=null&&!value.entry.config.providerId.equals(config.providerId))
                    throw new IOException("This chat belongs to a different AI provider");
            } catch (IOException error) { problem=error.getMessage()==null?"Saved AI chat could not be read":error.getMessage(); }
            final NotebookAiHistory.Saved result=value; final String failure=problem;
            dispatch.accept(()->{
                if (closed) return; loaded=true; loadFailed=failure!=null;
                if (result!=null&&failure==null) { chat=result.chat; created=result.entry.created; }
                status=failure!=null?failure+". You can start a new chat; the saved file is retained.":chat.awaitingReply()?"Previous reply was interrupted. Tap Retry.":"";
                changed.run();
            });
        });
    }
    void history(List<NotebookAiConfig> known,HistoryCallback callback) {
        storage.execute(()->{
            List<NotebookAiHistory.Entry> entries=null;String problem=null;
            try {entries=NotebookAiHistory.list(directory,known);}catch(IOException error){problem="Cannot open chat history. Check device storage and retry.";}
            final List<NotebookAiHistory.Entry> result=entries;final String failure=problem;
            dispatch.accept(()->{if(!closed)callback.complete(result,failure);});
        });
    }
    void delete(String target,DeleteCallback callback) {
        if (closed||switching) return;
        final boolean current=id.equals(target);
        if (current) {stop();++generation;switching=true;changed.run();}
        storage.execute(()->{
            String problem=null;boolean removed=false;
            try {NotebookAiHistory.delete(directory,target);removed=true;}
            catch(IOException error){problem="Could not finish deleting this chat. Retry Delete in History.";
                try {removed=NotebookAiHistory.deleted(directory,target);}catch(IOException ignored){}}
            final boolean deleted=removed;final String failure=problem;
            dispatch.accept(()->{
                if(closed)return;
                if(current) {
                    switching=false;
                    if(deleted) fresh();
                    if(failure!=null)status=failure;
                    changed.run();
                }
                callback.complete(failure);
            });
        });
    }
    void draft(String text) {
        if (closed||!loaded||loadFailed||switching) return;
        if (chat.draft.equals(text)) return;
        try { chat=chat.draft(text); save(chat,null); }
        catch (IllegalArgumentException error) { status=error.getMessage(); changed.run(); }
    }
    void attach(NotebookAiAttachment image) {
        if (closed||!loaded||loadFailed||switching) return;
        try { chat=chat.attach(image); status=""; save(chat,null); changed.run(); }
        catch (IllegalArgumentException error) { status=error.getMessage(); changed.run(); }
    }
    void removeImage(int index) {
        if (closed||!loaded||loadFailed||switching) return;
        if(index<0||index>=chat.draftImages.size())return;
        chat=chat.removeImage(index); save(chat,null); changed.run();
    }
    void send(String text) {
        if (closed||!loaded||loadFailed||busy||switching) return;
        if(!sendProblem.isEmpty()){status=sendProblem;changed.run();return;}
        try { config.validate();NotebookAiConversation candidate=chat.awaitingReply()?chat:chat.question(text);
            config.specs.validateRequest(candidate.messages,config.specs.replyBudget(config.provider));NotebookAiClient.validateAttachments(config,candidate.messages);chat=candidate; }
        catch (IllegalArgumentException|IOException error) { status=error.getMessage(); changed.run(); return; }
        busy=true; status="Generating…"; final int request=++generation;
        cancellation=new NotebookAiClient.Cancellation(); final NotebookAiClient.Cancellation cancel=cancellation;
        final NotebookAiConversation history=chat;
        changed.run();
        // A question is recoverable before any potentially billable provider request.
        save(history,()->{
            if (closed||request!=generation) return;
            network.execute(()->{
                NotebookAiClient.Reply reply=null; String problem=null;
                try { cancel.check(); reply=transport.complete(config,history,cancel); }
                catch (IOException|RuntimeException error) { problem=error instanceof java.net.SocketTimeoutException?"AI request timed out. Tap Retry.":error instanceof IOException?error.getMessage():"Could not complete the AI request. Check Settings and Retry."; }
                final NotebookAiClient.Reply answer=reply; final String failure=problem;
                dispatch.accept(()->{
                    if (closed||request!=generation) return;
                    busy=false; cancellation=null;
                    if (answer!=null) {
                        try {
                            chat=chat.answer(answer.text); status=answer.truncated?"Reply reached the provider's output limit. Ask it to continue.":""; save(chat,null);
                        } catch (IllegalArgumentException error) { status=error.getMessage(); }
                    } else status=failure==null?"Provider returned no reply. Tap Retry.":failure;
                    changed.run();
                });
            });
        });
    }
    void stop() {
        if (!busy) return;
        ++generation; busy=false;
        if (cancellation!=null) cancellation.cancel(); cancellation=null;
        status="Stopped. Tap Retry to send the last message again."; changed.run();
    }
    /** New chat archives the current conversation; it never overwrites a saved session. */
    void clear() {
        checkpoint(()->{fresh();changed.run();});
    }
    void checkpoint(Runnable after) {
        if(closed||!loaded||switching)return;
        stop();++generation;
        if(loadFailed){after.run();return;}
        switching=true;status="Saving chat…";changed.run();
        save(chat,()->{switching=false;after.run();});
    }
    private void fresh() {
        id=NotebookAiHistory.newId();created=Math.max(1,System.currentTimeMillis());
        chat=new NotebookAiConversation();loaded=true;busy=false;loadFailed=false;saveFailed=false;switching=false;status="";
    }
    void retrySave() {
        if (closed||busy||switching||!saveFailed) return;
        status="Saving chat…"; changed.run();
        save(chat,()->{ saveFailed=false; status=""; changed.run(); });
    }
    private void save(NotebookAiConversation snapshot,Runnable after) {
        final int request=generation; final String savedId=id; final long savedCreated=created;
        storage.execute(()->{
            String problem=null;boolean deleted=false;
            try { NotebookAiHistory.save(directory,savedId,config,savedCreated,snapshot); } catch (IOException error) { problem="Could not save AI chat. Check device storage and Retry."; }
            if(problem!=null)try{deleted=NotebookAiHistory.deleted(directory,savedId);}catch(IOException ignored){}
            final String failure=problem;
            final boolean removed=deleted;
            dispatch.accept(()->{
                if (closed||request!=generation) return;
                if (snapshot!=chat && after==null) return;
                if (failure!=null) { busy=false; switching=false; saveFailed=true;loadFailed=removed;
                    if (cancellation!=null) cancellation.cancel(); cancellation=null;
                    status=removed?"This saved chat was deleted. Start a new chat.":failure;changed.run(); }
                else { saveFailed=false; if (after!=null) after.run(); }
            });
        });
    }
    void close() {
        if (closed) return; closed=true; ++generation;
        if (cancellation!=null) cancellation.cancel(); cancellation=null;
    }
}
