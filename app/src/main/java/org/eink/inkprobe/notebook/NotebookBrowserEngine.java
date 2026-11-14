package org.eink.inkprobe.notebook;

import android.content.Context;
import android.os.Bundle;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A page surface independent of the browser's floating controls and archive catalog. */
abstract class NotebookBrowserEngine extends FrameLayout {
    interface Listener {
        boolean overrideNavigation(NotebookBrowserEngine view, String url, boolean mainFrame, boolean gesture);
        void onPageStarted(NotebookBrowserEngine view, String url);
        void onPageFinished(NotebookBrowserEngine view, String url);
        void onHistoryChanged(NotebookBrowserEngine view, String url);
        void onTitle(NotebookBrowserEngine view, String title);
        void onError(NotebookBrowserEngine view, String message);
        void onRendererGone(NotebookBrowserEngine view);
        void onDownload(NotebookBrowserEngine view);
    }
    final Listener listener;
    NotebookBrowserEngine(Context context, Listener listener) { super(context); this.listener = listener; }
    abstract String getUrl();
    abstract String getTitle();
    abstract void loadUrl(String url);
    abstract void reload();
    abstract boolean canGoBack();
    abstract boolean canGoForward();
    abstract void goBack();
    abstract void goForward();
    abstract void clearHistory();
    abstract History copyBackForwardList();
    abstract History restoreState(Bundle saved);
    abstract void saveState(Bundle saved);
    abstract void saveWebArchive(String path, boolean automatic, Consumer<String> callback);
    abstract void cancelCapture();
    abstract void stopLoading();
    abstract void onResume();
    abstract void onPause();
    abstract void destroy();
    boolean htmlCapture() { return true; }
    int captureWarnings() { return 0; }
    int pageScrollX() { return 0; }
    int pageScrollY() { return 0; }
    void scrollPageTo(int x, int y) { }

    static final class History {
        private final List<String> urls;
        private final int index;
        History(List<String> urls, int index) { this.urls = new ArrayList<>(urls); this.index = index; }
        int getSize() { return urls.size(); }
        int getCurrentIndex() { return index; }
        Item getItemAtIndex(int position) { return new Item(urls.get(position)); }
        void save(Bundle saved) { saved.putStringArrayList("urls", new ArrayList<>(urls)); saved.putInt("index", index); }
        static History restore(Bundle saved) {
            ArrayList<String> urls = saved.getStringArrayList("urls");
            return urls == null ? null : new History(urls, saved.getInt("index", -1));
        }
    }
    static final class Item {
        private final String url;
        Item(String url) { this.url = url; }
        String getUrl() { return url; }
    }
}
