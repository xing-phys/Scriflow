package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** A separate window covers the top-Z native ink surface without destroying it. */
final class NotebookPageOverview extends Dialog {
    private static final int CACHE_SIZE = 12;
    private final NotebookSession session;
    private final int notebookId, currentPage, pageCount;
    private final String notebookName;
    private final Map<Integer, Bitmap> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<Integer> unavailable = new HashSet<>();
    private final Runnable loadVisible = this::loadNext;
    private final PreviewAdapter adapter = new PreviewAdapter();
    private NotebookSession.PreviewRequest pending;
    private GridView grid;
    private EditText pageInput;
    private TextView validation;
    private boolean closed;
    private int chosenPage;

    NotebookPageOverview(Activity owner, NotebookSession session, int currentPage) {
        super(owner);
        setOwnerActivity(owner);
        this.session = session;
        this.notebookId = session.notebookId();
        this.notebookName = session.notebookName();
        this.currentPage = currentPage;
        this.pageCount = session.pageCount();
    }

    /** Zero means Close/Back: the editor must keep its exact previous scroll position. */
    int chosenPage() { return chosenPage; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0f);
        window.setWindowAnimations(0);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setCanceledOnTouchOutside(false);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        // Keep initial keyboard focus away from the number field.
        root.setFocusableInTouchMode(true);
        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Pages · " + notebookName, 19);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        NotebookButton close = button("Close");
        close.setOnClickListener(v -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(84), dp(52)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout jump = new LinearLayout(getContext());
        jump.setGravity(Gravity.CENTER_VERTICAL);
        jump.addView(text("Go to page", 15), new LinearLayout.LayoutParams(-2, dp(48)));
        pageInput = new EditText(getContext(), null, 0);
        pageInput.setSingleLine(true);
        pageInput.setTextSize(17);
        pageInput.setTextColor(Color.BLACK);
        pageInput.setHintTextColor(0xff666666);
        pageInput.setHint("1–" + pageCount);
        pageInput.setContentDescription("Page number, from 1 to " + pageCount);
        pageInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        pageInput.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        pageInput.setBackgroundColor(0xffeeeeee);
        pageInput.setForeground(null);
        pageInput.setStateListAnimator(null);
        pageInput.setDefaultFocusHighlightEnabled(false);
        pageInput.setPadding(dp(12), 0, dp(12), 0);
        pageInput.setCursorVisible(false);
        LinearLayout.LayoutParams inputLayout = new LinearLayout.LayoutParams(dp(120), dp(44));
        inputLayout.setMargins(dp(12), 0, dp(8), 0);
        jump.addView(pageInput, inputLayout);
        NotebookButton go = button("Go");
        go.setOnClickListener(v -> goToNumber());
        jump.addView(go, new LinearLayout.LayoutParams(dp(64), dp(48)));
        root.addView(jump, new LinearLayout.LayoutParams(-1, -2));
        validation = text("Tap a page to jump · " + pageCount + (pageCount == 1 ? " page" : " pages"), 13);
        root.addView(validation, new LinearLayout.LayoutParams(-1, dp(34)));
        pageInput.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || (event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) {
                goToNumber(); return true;
            }
            return false;
        });

        grid = new GridView(getContext());
        int columns = getContext().getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        grid.setNumColumns(columns);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(8));
        grid.setVerticalSpacing(dp(12));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setDefaultFocusHighlightEnabled(false);
        grid.setDrawSelectorOnTop(false);
        grid.setCacheColorHint(Color.WHITE);
        grid.setBackgroundColor(Color.WHITE);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setVerticalScrollBarEnabled(false);
        grid.setScrollingCacheEnabled(false);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> choose(position + 1));
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int state) { scheduleLoad(); }
            @Override public void onScroll(AbsListView view, int first, int visible, int total) { scheduleLoad(); }
        });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        root.requestFocus();
        grid.setSelection((currentPage - 1) / columns * columns);
        scheduleLoad();
    }

    @Override public void show() {
        super.show();
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override public void dismiss() {
        closed = true;
        if (pending != null) { pending.cancel(); pending = null; }
        if (grid != null) {
            grid.removeCallbacks(loadVisible);
            for (int i = 0; i < grid.getChildCount(); i++)
                ((NotebookPageThumbnail) grid.getChildAt(i)).clear();
            grid.setAdapter(null);
        }
        cache.clear(); unavailable.clear();
        super.dismiss();
    }

    private void goToNumber() {
        int target;
        try { target = Integer.parseInt(pageInput.getText().toString().trim()); }
        catch (NumberFormatException error) { target = 0; }
        if (target < 1 || target > pageCount) {
            validation.setText("Enter a page number from 1 to " + pageCount + ".");
            return;
        }
        choose(target);
    }

    private void choose(int target) { chosenPage = target; dismiss(); }

    private void scheduleLoad() {
        if (closed || grid == null) return;
        grid.removeCallbacks(loadVisible);
        grid.post(loadVisible);
    }

    /** Only one read/render is queued, always for a page that is currently visible. */
    private void loadNext() {
        if (closed || pending != null || grid.getChildCount() == 0) return;
        int first = Math.max(0, grid.getFirstVisiblePosition());
        int last = Math.min(pageCount - 1, grid.getLastVisiblePosition());
        for (int position = first; position <= last; position++) {
            final int number = position + 1;
            if (cache.containsKey(number) || unavailable.contains(number)) continue;
            NotebookPageThumbnail visible = (NotebookPageThumbnail) grid.getChildAt(position - first);
            if (visible != null && visible.hasPreview(number)) continue;
            pending = session.previewPage(notebookId, number, (bitmap, error) -> {
                pending = null;
                if (closed) return;
                if (error != null || bitmap == null) unavailable.add(number);
                else {
                    cache.put(number, bitmap);
                    while (cache.size() > CACHE_SIZE) cache.remove(cache.keySet().iterator().next());
                }
                int index = number - 1 - grid.getFirstVisiblePosition();
                if (index >= 0 && index < grid.getChildCount()) {
                    NotebookPageThumbnail cell = (NotebookPageThumbnail) grid.getChildAt(index);
                    if (Integer.valueOf(number).equals(cell.getTag())) bind(cell, number);
                }
                scheduleLoad();
            });
            return;
        }
    }

    private void bind(NotebookPageThumbnail cell, int number) {
        cell.setTag(number);
        Bitmap bitmap = cache.get(number);
        if (bitmap != null) cell.bind(number, bitmap, number == currentPage);
        // A visible cell may outlive its cache entry on a particularly tall window.
        // Keep its pixels until it is reused, without reloading it repeatedly.
        else if (cell.hasPreview(number)) return;
        else if (unavailable.contains(number)) cell.showError(number, number == currentPage);
        else cell.showLoading(number, number == currentPage);
    }

    private final class PreviewAdapter extends BaseAdapter {
        @Override public int getCount() { return pageCount; }
        @Override public Object getItem(int position) { return position + 1; }
        @Override public long getItemId(int position) { return position + 1; }
        @Override public boolean hasStableIds() { return true; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            NotebookPageThumbnail cell = recycled instanceof NotebookPageThumbnail
                ? (NotebookPageThumbnail) recycled : new NotebookPageThumbnail(getContext());
            bind(cell, position + 1);
            scheduleLoad();
            return cell;
        }
    }

    private TextView text(String value, int size) {
        TextView label = new TextView(getContext());
        label.setText(value); label.setTextSize(size); label.setTextColor(Color.BLACK);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private NotebookButton button(String label) {
        NotebookButton button = new NotebookButton(getContext());
        button.setText(label); button.setTextSize(16);
        return button;
    }

    private int dp(int value) { return Math.round(value * getContext().getResources().getDisplayMetrics().density); }
}
