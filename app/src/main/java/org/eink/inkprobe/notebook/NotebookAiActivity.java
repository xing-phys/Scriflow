package org.eink.inkprobe.notebook;

/** An AI-first workspace: notebooks are opened only after an explicit choice. */
public final class NotebookAiActivity extends NotebookActivity {
    @Override protected boolean isIndependentAiWorkspace() { return true; }

    @Override public android.content.SharedPreferences getPreferences(int mode) {
        String parent=NotebookActivity.class.getName(),prefix=getPackageName()+".";
        return getSharedPreferences(parent.startsWith(prefix)?parent.substring(prefix.length()):parent,mode);
    }
}
