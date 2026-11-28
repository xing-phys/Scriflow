package org.eink.inkprobe.notebook;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Tablet controls for exchanging selected files and web addresses. */
public final class NotebookShareActivity extends NotebookDisplayActivity {
    static final String LINK_DRAFT = "shareLinkDraft";
    private static final int CHOOSE_FILES = 4303;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable update = new Runnable() {
        @Override public void run() { refresh(); ui.postDelayed(this, 500); }
    };
    private TextView status, address, code, fileStatus;
    private NotebookButton start, choose;
    private EditText link;
    private LinearLayout session, offered, received;
    private NotebookShareServer pickerSession;
    private List<NotebookShareServer.Item> shownOffered, shownReceived;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setWindowAnimations(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout root = column();
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        root.setFocusableInTouchMode(true);
        LinearLayout header = row();
        header.addView(heading("Wi-Fi Sharing", 21), new LinearLayout.LayoutParams(0, dp(48), 1));
        header.addView(button("Done", this::finish), new LinearLayout.LayoutParams(dp(76), dp(44)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body = column();
        body.addView(label("Connect both devices to the same trusted Wi-Fi. The connection uses unencrypted HTTP.", 14));
        status = heading("", 16);
        body.addView(status);
        start = button("Start sharing", () -> { NotebookShareService.start(this); refresh(); });
        body.addView(start, wideAction());

        session = column();
        session.addView(label("Open this address in the other device’s browser.", 14));
        LinearLayout addressRow = row();
        address = label("", 18);
        address.setTextIsSelectable(true);
        addressRow.addView(address, new LinearLayout.LayoutParams(0, -2, 1));
        NotebookButton copy = button("Copy address", this::copyAddress);
        addressRow.addView(copy, new LinearLayout.LayoutParams(dp(112), dp(44)));
        session.addView(addressRow);
        code = heading("", 18);
        code.setTextIsSelectable(true);
        session.addView(code);
        session.addView(button("Stop sharing", () -> {
            NotebookShareService share = NotebookShareService.active();
            if (share != null) share.stop();
            refresh();
        }), wideAction());

        session.addView(heading("Send to other devices", 17));
        choose = button("Choose files…", this::chooseFiles);
        session.addView(choose, wideAction());
        fileStatus = label("", 13);
        session.addView(fileStatus);
        LinearLayout linkRow = row();
        link = new EditText(this);
        link.setSingleLine(true);
        link.setTextSize(15);
        link.setTextColor(Color.BLACK);
        link.setHintTextColor(Color.DKGRAY);
        link.setHint("Paste a web link");
        link.setContentDescription("Web link to send");
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        link.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8192)});
        link.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        link.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEND) return false;
            sendLink();
            return true;
        });
        link.setText(saved != null ? saved.getString(LINK_DRAFT, "") : getIntent().getStringExtra(LINK_DRAFT));
        linkRow.addView(link, new LinearLayout.LayoutParams(0, dp(50), 1));
        linkRow.addView(button("Send link", this::sendLink), new LinearLayout.LayoutParams(dp(94), dp(44)));
        session.addView(linkRow);
        offered = column();
        session.addView(offered);

        session.addView(heading("Received on this tablet", 17));
        received = column();
        session.addView(received);
        body.addView(session);
        body.addView(button("Open received files in Files", this::openFolder), wideAction());
        FrameLayout centered = new FrameLayout(this);
        centered.addView(body, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(centered);
        scroll.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            int width = Math.min(Math.max(1, r - l), dp(760));
            if (body.getLayoutParams().width != width) {
                body.getLayoutParams().width = width;
                body.requestLayout();
            }
        });
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        root.requestFocus();
        refresh();
    }

    @Override protected boolean allowScreenCast() { return false; }
    @Override protected void onResume() { super.onResume(); ui.post(update); }
    @Override protected void onPause() { ui.removeCallbacks(update); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(LINK_DRAFT, link.getText().toString());
        super.onSaveInstanceState(state);
    }

    private void refresh() {
        NotebookShareService share = NotebookShareService.active();
        text(status, NotebookShareService.status());
        start.setVisibility(share == null ? View.VISIBLE : View.GONE);
        start.setAvailable(!NotebookShareService.starting());
        session.setVisibility(share == null ? View.GONE : View.VISIBLE);
        if (share == null) {
            text(address, "");
            text(code, "");
            renderItems(null, Collections.emptyList(), Collections.emptyList());
            return;
        }
        NotebookShareServer server = share.server();
        text(address, server.address());
        text(code, "Pairing code  " + share.code());
        text(fileStatus, share.fileStatus());
        fileStatus.setVisibility(share.fileStatus().isEmpty() ? View.GONE : View.VISIBLE);
        choose.setAvailable(!share.staging() && pickerSession == null);
        renderItems(share, server.offered(), server.received());
    }

    private void renderItems(NotebookShareService share, List<NotebookShareServer.Item> outgoing,
                             List<NotebookShareServer.Item> incoming) {
        if (outgoing != shownOffered) {
            offered.removeAllViews();
            for (NotebookShareServer.Item item : outgoing) {
                offered.addView(itemRow(item, "Remove", () -> {
                    if (NotebookShareService.active() == share) { share.withdraw(item); refresh(); }
                }));
            }
            shownOffered = outgoing;
        }
        if (incoming != shownReceived) {
            received.removeAllViews();
            if (incoming.isEmpty()) received.addView(label("Files and links from the other browser appear here.", 14));
            for (NotebookShareServer.Item item : incoming)
                received.addView(itemRow(item, item.url == null ? "Open file" : "Open link", () -> open(item)));
            shownReceived = incoming;
        }
    }

    private LinearLayout itemRow(NotebookShareServer.Item item, String action, Runnable run) {
        LinearLayout row = row();
        TextView name = label(item.url != null ? item.url : item.name + " · " + Formatter.formatShortFileSize(this, item.size), 14);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(button(action, run), new LinearLayout.LayoutParams(dp(88), dp(44)));
        return row;
    }

    private void chooseFiles() {
        NotebookShareService share = NotebookShareService.active();
        if (share == null || share.staging() || pickerSession != null) return;
        pickerSession = share.server();
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try { startActivityForResult(picker, CHOOSE_FILES); }
        catch (ActivityNotFoundException unavailable) { pickerSession = null; toast("Android’s file picker is unavailable."); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CHOOSE_FILES) return;
        NotebookShareServer selectedFor = pickerSession;
        pickerSession = null;
        if (result != RESULT_OK || data == null) return;
        NotebookShareService share = NotebookShareService.active();
        if (share == null || share.server() != selectedFor) { toast("The sharing session ended. Choose the files again."); return; }
        LinkedHashSet<Uri> uris = new LinkedHashSet<>();
        if (data.getData() != null) uris.add(data.getData());
        ClipData clip = data.getClipData();
        if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) {
            Uri uri = clip.getItemAt(i).getUri();
            if (uri != null) uris.add(uri);
        }
        if (uris.size() > NotebookShareServer.MAX_OFFERED) { toast("Choose up to 32 files at a time."); return; }
        share.stageFiles(new ArrayList<>(uris));
        refresh();
    }

    private void sendLink() {
        NotebookShareService share = NotebookShareService.active();
        if (share == null) return;
        try {
            share.server().offerLink(link.getText().toString());
            link.setText("");
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(link.getWindowToken(), 0);
            link.clearFocus();
            refresh();
        } catch (IllegalArgumentException error) { link.setError(error.getMessage()); }
        catch (IOException error) { toast(error.getMessage()); }
    }

    private void copyAddress() {
        NotebookShareService share = NotebookShareService.active();
        if (share == null) return;
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Scriflow Wi-Fi Sharing", share.server().address()));
        toast("Sharing address copied.");
    }

    private void open(NotebookShareServer.Item item) {
        if (item.url != null) {
            startActivity(new Intent(this, NotebookBrowserActivity.class).putExtra(NotebookBrowserActivity.INITIAL_URL, item.url));
            overridePendingTransition(0, 0);
            return;
        }
        try {
            startActivity(NotebookFileStorage.downloadIntent(this, Uri.parse(item.receivedUri), item.name, null));
        } catch (ActivityNotFoundException | SecurityException error) {
            toast("Could not open this file. Check Downloads in Files.");
        }
    }

    private void openFolder() {
        startActivity(new Intent(this, NotebookFilesActivity.class).putExtra(NotebookFilesActivity.SHOW_SHARED_DOWNLOADS, true));
        overridePendingTransition(0, 0);
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    private void text(TextView view, String value) { if (!value.contentEquals(view.getText())) view.setText(value); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams wideAction() { return new LinearLayout.LayoutParams(-1, dp(44)); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setTextColor(Color.BLACK); view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, dp(6), dp(8), dp(6));
        return view;
    }
    private TextView heading(String text, int size) {
        TextView view = label(text, size);
        view.setTypeface(null, Typeface.BOLD);
        return view;
    }
    private NotebookButton button(String text, Runnable action) {
        NotebookButton button = new NotebookButton(this);
        button.setText(text);
        button.setTextSize(14);
        NotebookUi.action(button, null);
        button.setOnClickListener(view -> action.run());
        return button;
    }
}
