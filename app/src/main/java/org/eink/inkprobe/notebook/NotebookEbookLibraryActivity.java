package org.eink.inkprobe.notebook;

import android.content.Intent;
import android.os.Bundle;

/** Compatibility route for an older saved task; ebooks now live on the homepage. */
public final class NotebookEbookLibraryActivity extends NotebookDisplayActivity {
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        startActivity(new Intent(this,NotebookLibraryActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();
    }
}
