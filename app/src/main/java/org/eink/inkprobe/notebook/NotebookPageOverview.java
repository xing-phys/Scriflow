package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
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
import java.util.ArrayList;
import java.util.List;

/** A separate window covers the top-Z native ink surface without destroying it. */
final class NotebookPageOverview extends Dialog {
    private static final int CACHE_SIZE = 32;
    private final NotebookSession session;
    private final int notebookId;
    private int currentPage, pageCount;
    private final String notebookName;
    private final Map<Integer, Bitmap> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<Integer> unavailable = new HashSet<>();
    private final Runnable loadVisible = this::loadNext;
    private final PreviewAdapter adapter = new PreviewAdapter();
    private NotebookSession.PreviewRequest pending;
    private GridView grid;
    private EditText pageInput;
    private TextView validation;
    private boolean closed, managing, busy, pagesChanged;
    private NotebookButton closeButton, manageButton, undoPagesButton, redoPagesButton;
    private LinearLayout pageHistory;
    private AlertDialog actionDialog;
    private int chosenPage;
    private NotebookDeviceStatus deviceStatus;

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
    boolean pagesChanged() { return pagesChanged; }
    boolean hasPendingPageChange() { return busy; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setDimAmount(0f);
        window.setWindowAnimations(0);
        window.setElevation(0f);
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
        manageButton = button("Manage");
        manageButton.setOnClickListener(v -> {
            if (busy || session.error() != null) return;
            managing = !managing; updateManagementControls();
        });
        header.addView(manageButton, new LinearLayout.LayoutParams(dp(100), dp(52)));
        closeButton = button("Close");
        closeButton.setOnClickListener(v -> { if (!busy) dismiss(); });
        header.addView(closeButton, new LinearLayout.LayoutParams(dp(84), dp(52)));
        deviceStatus = new NotebookDeviceStatus(getContext(), () -> true);
        deviceStatus.setOnClickListener(v -> {
            if (busy) return;
            Activity owner = getOwnerActivity(); dismiss();
            if (owner != null) { owner.startActivity(new android.content.Intent(owner, NotebookSettingsActivity.class)); owner.overridePendingTransition(0, 0); }
        });
        header.addView(deviceStatus, new LinearLayout.LayoutParams(-2, dp(32)));
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
        int columns = NotebookSettings.pageColumns(getContext(), NotebookSettings.landscape(getContext()));
        grid.setNumColumns(columns);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(4));
        grid.setVerticalSpacing(dp(4));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setDefaultFocusHighlightEnabled(false);
        grid.setDrawSelectorOnTop(false);
        grid.setCacheColorHint(Color.WHITE);
        grid.setBackgroundColor(Color.WHITE);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setVerticalScrollBarEnabled(false);
        grid.setScrollingCacheEnabled(false);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            if (busy || session.error() != null) return;
            if (managing) showPageActions(position + 1); else choose(position + 1);
        });
        grid.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!busy && session.error() == null) showPageActions(position + 1);
            return true;
        });
        grid.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int state) { scheduleLoad(); }
            @Override public void onScroll(AbsListView view, int first, int visible, int total) { scheduleLoad(); }
        });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        undoPagesButton = button("Undo page change");
        undoPagesButton.setOnClickListener(v -> {
            if (!busy && session.canUndoPageChange() && session.error() == null) runPageChange(null, 0, 0);
        });
        redoPagesButton = button("Redo page change");
        redoPagesButton.setOnClickListener(v -> {
            if (!busy && session.canRedoPageChange() && session.error() == null) runPageChange(null, 0, 1);
        });
        pageHistory = new LinearLayout(getContext());
        pageHistory.addView(undoPagesButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        pageHistory.addView(redoPagesButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(pageHistory, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        updateManagementControls();
        root.requestFocus();
        grid.setSelection((currentPage - 1) / columns * columns);
        scheduleLoad();
    }

    @Override public void show() {
        super.show();
        deviceStatus.start();
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override public void dismiss() {
        closed = true;
        if (deviceStatus != null) deviceStatus.stop();
        if (actionDialog != null) { actionDialog.dismiss(); actionDialog = null; }
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

    @Override public void onBackPressed() { if (!busy) super.onBackPressed(); }

    private void goToNumber() {
        if (busy) return;
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

    private void updateManagementControls() {
        boolean ready = !busy && session.error() == null;
        manageButton.setText(managing ? "Browse" : "Manage");
        manageButton.setAvailable(ready); closeButton.setAvailable(!busy);
        undoPagesButton.setAvailable(ready && session.canUndoPageChange());
        redoPagesButton.setAvailable(ready && session.canRedoPageChange());
        pageHistory.setVisibility(session.canUndoPageChange() || session.canRedoPageChange() ? View.VISIBLE : View.GONE);
        pageInput.setHint("1–" + pageCount);
        pageInput.setContentDescription("Page number, from 1 to " + pageCount);
        if (ready) validation.setText((managing ? "Tap a page to manage" : "Tap a page to jump · hold for actions")
            + " · " + pageCount + (pageCount == 1 ? " page" : " pages"));
    }

    private void showPageActions(int number) {
        if (closed || busy || actionDialog != null) return;
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        labels.add("Open page"); actions.add(() -> choose(number));
        if (number > 1) {
            labels.add("Move earlier"); actions.add(() -> runPageChange(NotebookBook.PageAction.MOVE, number, number - 1));
        }
        if (number < pageCount) {
            labels.add("Move later"); actions.add(() -> runPageChange(NotebookBook.PageAction.MOVE, number, number + 1));
        }
        if (pageCount > 1) {
            labels.add("Move to page…"); actions.add(() -> moveToPosition(number));
        }
        if (pageCount < NotebookIndex.MAX_PAGES) {
            labels.add("Duplicate"); actions.add(() -> runPageChange(NotebookBook.PageAction.DUPLICATE, number, 0));
            labels.add("Insert blank before"); actions.add(() -> runPageChange(NotebookBook.PageAction.INSERT_BEFORE, number, 0));
            labels.add("Insert blank after"); actions.add(() -> runPageChange(NotebookBook.PageAction.INSERT_AFTER, number, 0));
        }
        if (pageCount > 1) {
            labels.add("Delete…"); actions.add(() -> confirmDelete(number));
        }
        final Runnable[] chosen = {null};
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("Page " + number)
            .setItems(labels.toArray(new String[0]), (choice, which) -> chosen[0] = actions.get(which))
            .setNegativeButton("Cancel", null).create();
        dialog.setOnDismissListener(choice -> {
            actionDialog = null;
            if (!closed && chosen[0] != null) chosen[0].run();
        });
        showActionDialog(dialog);
    }

    private void confirmDelete(int number) {
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("Delete page " + number + "?")
            .setMessage("This removes the page and its writing from the notebook. You can restore it with Undo page change until the app restarts.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete page", (choice, which) -> runPageChange(NotebookBook.PageAction.DELETE, number, 0)).create();
        dialog.setOnDismissListener(choice -> actionDialog = null);
        showActionDialog(dialog);
    }

    private void moveToPosition(int number) {
        LinearLayout input = new LinearLayout(getContext()); input.setOrientation(LinearLayout.VERTICAL);
        input.setPadding(dp(24), dp(8), dp(24), 0);
        TextView hint = text("Final page number · 1–" + pageCount, 15); input.addView(hint);
        EditText position = new EditText(getContext()); position.setSingleLine(true);
        position.setInputType(InputType.TYPE_CLASS_NUMBER); position.setText(Integer.toString(number));
        position.setCursorVisible(false); position.setStateListAnimator(null); position.setForeground(null);
        position.setDefaultFocusHighlightEnabled(false); position.setTextColor(Color.BLACK);
        position.setBackgroundColor(0xffeeeeee); position.setPadding(dp(12), dp(8), dp(12), dp(8));
        position.setSelectAllOnFocus(true); input.addView(position);
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("Move page " + number)
            .setView(input).setNegativeButton("Cancel", null).setPositiveButton("Move", null).create();
        dialog.setOnDismissListener(choice -> actionDialog = null);
        showActionDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int target;
            try { target = Integer.parseInt(position.getText().toString().trim()); }
            catch (NumberFormatException error) { target = 0; }
            if (target < 1 || target > pageCount) { hint.setText("Enter a page number from 1 to " + pageCount + "."); return; }
            dialog.dismiss(); runPageChange(NotebookBook.PageAction.MOVE, number, target);
        });
    }

    private void showActionDialog(AlertDialog dialog) {
        actionDialog = dialog; NotebookUi.show(dialog);
    }

    private void runPageChange(NotebookBook.PageAction action, int source, int destination) {
        if (closed || busy || session.error() != null) return;
        clearPreviews(); busy = true; updateManagementControls();
        validation.setText("Saving page changes…");
        NotebookSession.Callback completion = (page, error) -> {
            if (closed) return;
            busy = false;
            if (error != null) {
                updateManagementControls(); validation.setText("Couldn’t save page changes. Close to retry."); return;
            }
            pagesChanged = true; currentPage = session.pageNumber(); pageCount = session.pageCount();
            updateManagementControls(); adapter.notifyDataSetChanged(); grid.setSelection(currentPage - 1); scheduleLoad();
        };
        if (action != null) session.managePages(action, source, destination, completion);
        else if (destination == 1) session.redoPageChange(completion); else session.undoPageChange(completion);
    }

    private void clearPreviews() {
        if (pending != null) { pending.cancel(); pending = null; }
        grid.removeCallbacks(loadVisible);
        for (int i = 0; i < grid.getChildCount(); i++) ((NotebookPageThumbnail) grid.getChildAt(i)).clear();
        cache.clear(); unavailable.clear();
    }

    private void scheduleLoad() {
        if (closed || busy || session.error() != null || grid == null) return;
        grid.removeCallbacks(loadVisible);
        grid.post(loadVisible);
    }

    /** Only one read/render is queued, always for a page that is currently visible. */
    private void loadNext() {
        if (closed || busy || session.error() != null || pending != null || grid.getChildCount() == 0) return;
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
        @Override public boolean hasStableIds() { return false; }
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
