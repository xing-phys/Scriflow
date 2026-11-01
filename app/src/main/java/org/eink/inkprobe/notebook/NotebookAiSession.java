package org.eink.inkprobe.notebook;

import java.io.*;
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
    boolean loaded,busy,loadFailed,saveFailed,closed;
    String status="Loading chat…";
    private int generation;
    private NotebookAiClient.Cancellation cancellation;

    NotebookAiSession(File directory,NotebookAiConfig config,Consumer<Runnable> dispatch,Runnable changed) {
        this(directory,config,dispatch,changed,STORAGE,r->new Thread(r,"ai-chat-request").start(),
            (settings,chat,cancel)->new NotebookAiClient().complete(settings,chat.messages,cancel));
    }
    NotebookAiSession(File directory,NotebookAiConfig config,Consumer<Runnable> dispatch,Runnable changed,Executor storage,Executor network,Transport transport) {
        this.directory=directory; this.config=config; this.dispatch=dispatch; this.changed=changed;
        this.storage=storage; this.network=network; this.transport=transport;
    }
    void load() {
        storage.execute(()->{
            NotebookAiConversation value=null; String problem=null;
            try { value=NotebookAiConversation.read(directory,config.profile()); } catch (IOException error) { problem=error.getMessage()==null?"Saved AI chat could not be read":error.getMessage(); }
            final NotebookAiConversation result=value; final String failure=problem;
            dispatch.accept(()->{
                if (closed) return; loaded=true; loadFailed=failure!=null;
                if (result!=null) chat=result;
                status=failure!=null?failure+". Start a new chat to reset it.":chat.awaitingReply()?"Previous reply was interrupted. Tap Retry.":"";
                changed.run();
            });
        });
    }
    void draft(String text) {
        if (closed||!loaded||loadFailed) return;
        try { chat=chat.draft(text); save(chat,null); }
        catch (IllegalArgumentException error) { status=error.getMessage(); changed.run(); }
    }
    void attach(NotebookAiAttachment image) {
        if (closed||!loaded||loadFailed) return;
        try { chat=chat.attach(image); status="Selection attached. Add a message and tap Send."; save(chat,null); changed.run(); }
        catch (IllegalArgumentException error) { status=error.getMessage(); changed.run(); }
    }
    void removeImage(int index) {
        if (closed||!loaded||loadFailed) return;
        chat=chat.removeImage(index); save(chat,null); changed.run();
    }
    void send(String text) {
        if (closed||!loaded||loadFailed||busy) return;
        try { config.validate(); if (!chat.awaitingReply()) chat=chat.question(text); }
        catch (IllegalArgumentException error) { status=error.getMessage(); changed.run(); return; }
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
    void clear() {
        if (closed||!loaded) return;
        stop(); chat=new NotebookAiConversation(); loadFailed=false; status=""; save(chat,null); changed.run();
    }
    void retrySave() {
        if (closed||busy||!saveFailed) return;
        status="Saving chat…"; changed.run();
        save(chat,()->{ saveFailed=false; status=""; changed.run(); });
    }
    private void save(NotebookAiConversation snapshot,Runnable after) {
        final int request=generation;
        storage.execute(()->{
            String problem=null;
            try { NotebookAiConversation.write(directory,config.profile(),snapshot); } catch (IOException error) { problem="Could not save AI chat. Check device storage and Retry."; }
            final String failure=problem;
            dispatch.accept(()->{
                if (closed||request!=generation) return;
                if (snapshot!=chat && after==null) return;
                if (failure!=null) { busy=false; saveFailed=true; if (cancellation!=null) cancellation.cancel(); cancellation=null; status=failure; changed.run(); }
                else { saveFailed=false; if (after!=null) after.run(); }
            });
        });
    }
    void close() {
        if (closed) return; closed=true; ++generation;
        if (cancellation!=null) cancellation.cancel(); cancellation=null;
    }
}
