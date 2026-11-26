package org.eink.inkprobe.notebook;

import java.io.File;

/** Creates a disposable, readable saved chat with production archive serialization. */
public final class NotebookAiUiFixture {
    public static void main(String[] args) throws Exception {
        NotebookAiConfig config = new NotebookAiConfig(NotebookAiConfig.Provider.CUSTOM,
            "https://example.org/v1", "fixture-model", "");
        NotebookAiConversation chat = new NotebookAiConversation().draft("Dialog fixture");
        NotebookAiHistory.save(new File(args[0]), args[1], config, System.currentTimeMillis(), chat);
    }
}
