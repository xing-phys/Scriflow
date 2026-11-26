package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;
import static org.eink.inkprobe.notebook.NotebookBrowserUi.*;

/** Native start page. Previews are local and never fetch a website or favicon. */
final class NotebookBrowserHome extends ScrollView {
    interface Actions {
        void open(String url); void add(); void manage(NotebookBrowserLibrary.Link link);
        void bookmarks(); void history(); void downloads(); void settings(); void tabs(); void newTab();
        void savedWebsites(); void bookshelf(); void openTab(String id); void closeTab(String id);
    }
    private final Actions actions;
    private final LinearLayout body, shortcuts, pinsGrid, tabsGrid;
    private final TextView tabsCount;
    private final NotebookBrowserIconButton pinsPresentation, tabsPresentation;
    private NotebookBrowserLibrary.Snapshot snapshot;
    private List<NotebookBrowserTabs.Tab> openTabs = new ArrayList<>();
    private String selectedTab;
    private boolean pinThumbnails, tabThumbnails;

    NotebookBrowserHome(Context context, Actions actions) {
        super(context); this.actions = actions; setBackgroundColor(Color.WHITE); setOverScrollMode(OVER_SCROLL_NEVER);
        FrameLayout center = new FrameLayout(context); addView(center, new LayoutParams(-1, -2));
        body = column(context); body.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 28));
        center.addView(body, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        TextView title = label(context, "Browser", 24); title.setTypeface(null, Typeface.BOLD); body.addView(title);
        space(body, dp(context, 18));
        LinearLayout search = row(context); EditText query = input(context, "Search or enter a website", ""); field(query);
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        Runnable go = () -> { if (!query.getText().toString().trim().isEmpty()) actions.open(query.getText().toString()); };
        query.setOnEditorActionListener((v, action, event) -> { if (action == android.view.inputmethod.EditorInfo.IME_ACTION_GO) { go.run(); return true; } return false; });
        search.addView(query, new LinearLayout.LayoutParams(0, dp(context, 48), 1));
        search.addView(icon(context, NotebookIconDrawable.Icon.SEARCH, "Search or open website", go), beside(context, 48, 48)); body.addView(search);
        shortcuts = column(context); LinearLayout.LayoutParams shortcutSize = new LinearLayout.LayoutParams(-1, -2);
        shortcutSize.setMargins(0, dp(context, 16), 0, dp(context, 20)); body.addView(shortcuts, shortcutSize);

        pinThumbnails = context.getSharedPreferences("browser-ui", Context.MODE_PRIVATE).getBoolean("pinsThumbnails", true);
        tabThumbnails = context.getSharedPreferences("browser-ui", Context.MODE_PRIVATE).getBoolean("homeTabsThumbnails", true);
        LinearLayout pinHeading = row(context); TextView pins = label(context, "Pinned websites", 18); pins.setTypeface(null, Typeface.BOLD);
        pinHeading.addView(pins, new LinearLayout.LayoutParams(0, dp(context, 44), 1));
        pinsPresentation = icon(context, NotebookIconDrawable.Icon.OVERVIEW, "Show thumbnails", () -> {
            pinThumbnails = !pinThumbnails; context.getSharedPreferences("browser-ui", Context.MODE_PRIVATE).edit().putBoolean("pinsThumbnails", pinThumbnails).apply(); render();
        }); pinHeading.addView(pinsPresentation, beside(context, 44, 44)); body.addView(pinHeading);
        TextView help = label(context, "Hold a shortcut to edit, unpin or reorder it.", 12); help.setTextColor(0xff555555); body.addView(help);
        pinsGrid = column(context); body.addView(pinsGrid);

        space(body, dp(context, 22));
        LinearLayout tabsHeading = row(context); TextView titleTabs = label(context, "Open tabs", 18); titleTabs.setTypeface(null, Typeface.BOLD);
        tabsHeading.addView(titleTabs, new LinearLayout.LayoutParams(0, dp(context, 44), 1));
        tabsCount = label(context, "", 12); tabsCount.setTextColor(0xff555555); tabsHeading.addView(tabsCount);
        tabsPresentation = icon(context, NotebookIconDrawable.Icon.OVERVIEW, "Show tab thumbnails", () -> {
            tabThumbnails = !tabThumbnails; context.getSharedPreferences("browser-ui", Context.MODE_PRIVATE).edit().putBoolean("homeTabsThumbnails", tabThumbnails).apply(); render();
        }); tabsHeading.addView(tabsPresentation, beside(context, 44, 44)); body.addView(tabsHeading);
        tabsGrid = column(context); body.addView(tabsGrid);
        space(body, dp(context, 24)); body.addView(label(context, "Save keeps a page for offline reading. Bookmarks open the live website.", 13));
        renderShortcuts();
    }

    void show(NotebookBrowserLibrary.Snapshot value) { snapshot = value; render(); }
    void showTabs(List<NotebookBrowserTabs.Tab> value, String selected) { openTabs = new ArrayList<>(value); selectedTab = selected; render(); }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) {
            FrameLayout.LayoutParams size = (FrameLayout.LayoutParams) body.getLayoutParams();
            size.width = Math.min(w, dp(getContext(), 960)); body.setLayoutParams(size);
            post(() -> { renderShortcuts(); render(); });
        }
    }

    private int contentWidth() { return Math.max(dp(getContext(), 120), Math.min(getWidth(), dp(getContext(), 960)) - body.getPaddingLeft() - body.getPaddingRight()); }

    private void renderShortcuts() {
        Context c = getContext(); shortcuts.removeAllViews();
        NotebookIconDrawable.Icon[] symbols = {NotebookIconDrawable.Icon.BOOKMARK, NotebookIconDrawable.Icon.HISTORY, NotebookIconDrawable.Icon.DOWNLOAD, NotebookIconDrawable.Icon.TABS,
            NotebookIconDrawable.Icon.PLUS, NotebookIconDrawable.Icon.FOLDER, NotebookIconDrawable.Icon.NEW_FOLDER, NotebookIconDrawable.Icon.SETTINGS, NotebookIconDrawable.Icon.LIBRARY};
        String[] names = {"Bookmarks", "History", "Downloads", "Tabs", "New tab", "Saved websites", "Add shortcut", "Browser data", "Bookshelf"};
        Runnable[] clicks = {actions::bookmarks, actions::history, actions::downloads, actions::tabs, actions::newTab, actions::savedWebsites, actions::add, actions::settings, actions::bookshelf};
        int columns = Math.max(1, Math.min(symbols.length, contentWidth() / dp(c, 54))); LinearLayout line = null;
        for (int i = 0; i < symbols.length; i++) {
            if (i % columns == 0) { line = row(c); shortcuts.addView(line, new LinearLayout.LayoutParams(-1, dp(c, 48))); }
            FrameLayout slot = new FrameLayout(c); slot.addView(icon(c, symbols[i], names[i], clicks[i]), new FrameLayout.LayoutParams(dp(c, 48), dp(c, 48), Gravity.CENTER));
            line.addView(slot, new LinearLayout.LayoutParams(0, dp(c, 48), 1));
        }
        if (line != null && symbols.length % columns != 0) for (int i = symbols.length % columns; i < columns; i++) line.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
    }

    private void render() {
        Context c = getContext(); pinsGrid.removeAllViews(); tabsGrid.removeAllViews();
        pinsPresentation.setSymbol(pinThumbnails ? NotebookIconDrawable.Icon.OUTLINE : NotebookIconDrawable.Icon.OVERVIEW, pinThumbnails ? "Show pinned websites as list" : "Show pinned website thumbnails");
        tabsPresentation.setSymbol(tabThumbnails ? NotebookIconDrawable.Icon.OUTLINE : NotebookIconDrawable.Icon.OVERVIEW, tabThumbnails ? "Show open tabs as list" : "Show open tab thumbnails");
        tabsCount.setText(openTabs.size() + " open");
        if (snapshot != null) {
            List<NotebookBrowserLibrary.Link> pins = new ArrayList<>(); for (NotebookBrowserLibrary.Link link : snapshot.links) if (link.pinned) pins.add(link);
            int columns = columns(pinThumbnails), count = 0; LinearLayout line = null;
            for (int i = 0; i <= pins.size(); i++) {
                if (count % columns == 0) { line = row(c); line.setGravity(Gravity.TOP); pinsGrid.addView(line); }
                NotebookBrowserLibrary.Link link = i < pins.size() ? pins.get(i) : null; View card;
                if (pinThumbnails) card = new NotebookBrowserCard(c, link == null ? "Add shortcut" : link.title, link == null ? null : link.url,
                    link == null ? "Add shortcut" : "Open pinned website " + link.title, false, link == null,
                    link == null ? actions::add : () -> actions.open(link.url), NotebookIconDrawable.Icon.MORE,
                    link == null ? null : "Manage pinned website " + link.title, link == null ? null : () -> actions.manage(link), true);
                else { NotebookButton item = button(c, link == null ? "+ Add shortcut" : link.title + "\n" + NotebookBrowserLibrary.host(link.url), link == null ? actions::add : () -> actions.open(link.url));
                    item.setMaxLines(2); item.setEllipsize(TextUtils.TruncateAt.END); item.setContentDescription(link == null ? "Add shortcut" : "Open pinned website " + link.title); card = item; }
                if (link != null) card.setOnLongClickListener(v -> { actions.manage(link); return true; });
                addCell(line, card, count++, columns, pinThumbnails);
            }
            fill(line, count, columns);
        }
        int columns = columns(tabThumbnails), count = 0; LinearLayout line = null;
        for (NotebookBrowserTabs.Tab tab : openTabs) {
            if (count % columns == 0) { line = row(c); line.setGravity(Gravity.TOP); tabsGrid.addView(line); }
            boolean page = NotebookBrowserAddress.isWebUrl(tab.url); String name = page ? tab.title : "Start page";
            boolean selected = tab.id.equals(selectedTab); View card;
            if (tabThumbnails) card = new NotebookBrowserCard(c, name, page ? tab.url : null, "Open tab " + name, selected, false,
                () -> actions.openTab(tab.id), NotebookIconDrawable.Icon.CLOSE, "Close tab " + name, () -> actions.closeTab(tab.id), true);
            else {
                LinearLayout item = row(c); item.setBackground(NotebookUi.rounded(c.getResources().getDisplayMetrics().density, 7)); item.setPadding(dp(c, 10), 0, dp(c, 6), 0);
                TextView text = label(c, (selected ? "✓ " : "") + name + (page ? "\n" + NotebookBrowserLibrary.host(tab.url) : ""), 13);
                text.setMaxLines(2); text.setEllipsize(TextUtils.TruncateAt.END); text.setContentDescription("Open tab " + name); text.setOnClickListener(v -> actions.openTab(tab.id));
                item.addView(text, new LinearLayout.LayoutParams(0, dp(c, 56), 1));
                item.addView(icon(c, NotebookIconDrawable.Icon.CLOSE, "Close tab " + name, () -> actions.closeTab(tab.id)), beside(c, 44, 44)); card = item;
            }
            addCell(line, card, count++, columns, tabThumbnails);
        }
        fill(line, count, columns);
    }

    private int columns(boolean thumbs) { return thumbs ? Math.max(1, Math.min(6, (contentWidth() + dp(getContext(), 8)) / dp(getContext(), 150))) : 1; }
    private void addCell(LinearLayout line, View card, int index, int columns, boolean thumbs) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, thumbs ? -2 : dp(getContext(), 64), 1);
        size.topMargin = dp(getContext(), 8); if (index % columns != 0) size.setMarginStart(dp(getContext(), 8)); line.addView(card, size);
    }
    private void fill(LinearLayout line, int count, int columns) {
        if (line != null && count % columns != 0) for (int i = count % columns; i < columns; i++) {
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, 1, 1); size.setMarginStart(dp(getContext(), 8)); line.addView(new View(getContext()), size);
        }
    }
}
