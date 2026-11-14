package org.eink.inkprobe.notebook;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.PromptDelegate;

/** Native responses for ordinary website dialogs and HTML select controls. */
final class NotebookBrowserPrompts implements PromptDelegate {
    private final Context context;
    private final Supplier<String> address;
    private AlertDialog shown;
    NotebookBrowserPrompts(Context context, Supplier<String> address) { this.context = context; this.address = address; }

    private AlertDialog.Builder builder() {
        String url = address.get();
        String host = url == null ? null : Uri.parse(url).getHost();
        return new AlertDialog.Builder(context).setTitle(host == null ? "Website" : host);
    }

    private GeckoResult<PromptResponse> show(BasePrompt prompt, AlertDialog.Builder builder,
            GeckoResult<PromptResponse> result) {
        close();
        AlertDialog dialog = builder.create(); shown = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (shown == dialog) shown = null;
            if (!prompt.isComplete()) result.complete(prompt.dismiss());
        });
        prompt.setDelegate(new PromptInstanceDelegate() {
            @Override public void onPromptDismiss(BasePrompt value) { dialog.dismiss(); }
            @Override public void onPromptUpdate(BasePrompt value) { dialog.dismiss(); }
        });
        NotebookUi.show(dialog); return result;
    }

    @Override public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession session, AlertPrompt prompt) {
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        return show(prompt, builder().setMessage(prompt.message).setPositiveButton("OK", (dialog, which) -> {
            if (!prompt.isComplete()) result.complete(prompt.dismiss());
        }), result);
    }

    @Override public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession session, ButtonPrompt prompt) {
        GeckoResult<PromptResponse> result = new GeckoResult<>();
        return show(prompt, builder().setMessage(prompt.message).setNegativeButton("Cancel", null)
            .setPositiveButton("OK", (dialog, which) -> { if (!prompt.isComplete()) result.complete(prompt.confirm(ButtonPrompt.Type.POSITIVE)); }), result);
    }

    @Override public GeckoResult<PromptResponse> onTextPrompt(GeckoSession session, TextPrompt prompt) {
        GeckoResult<PromptResponse> result = new GeckoResult<>(); EditText text = new EditText(context);
        text.setText(prompt.defaultValue); text.setSelectAllOnFocus(true);
        return show(prompt, builder().setMessage(prompt.message).setView(text).setNegativeButton("Cancel", null)
            .setPositiveButton("OK", (dialog, which) -> { if (!prompt.isComplete()) result.complete(prompt.confirm(text.getText().toString())); }), result);
    }

    @Override public GeckoResult<PromptResponse> onChoicePrompt(GeckoSession session, ChoicePrompt prompt) {
        GeckoResult<PromptResponse> result = new GeckoResult<>(); List<ChoicePrompt.Choice> choices = new ArrayList<>();
        flatten(prompt.choices, choices, false);
        if (choices.isEmpty()) return GeckoResult.fromValue(prompt.dismiss());
        CharSequence[] labels = new CharSequence[choices.size()]; boolean[] selected = new boolean[choices.size()]; int current = -1;
        for (int i = 0; i < choices.size(); i++) {
            labels[i] = choices.get(i).label; selected[i] = choices.get(i).selected; if (selected[i]) current = i;
        }
        AlertDialog.Builder builder = builder().setNegativeButton("Cancel", null);
        if (prompt.type == ChoicePrompt.Type.MULTIPLE) {
            builder.setMultiChoiceItems(labels, selected, (dialog, which, checked) -> selected[which] = checked)
                .setPositiveButton("OK", (dialog, which) -> {
                    List<String> ids = new ArrayList<>(); for (int i = 0; i < selected.length; i++) if (selected[i]) ids.add(choices.get(i).id);
                    if (!prompt.isComplete()) result.complete(prompt.confirm(ids.toArray(new String[0])));
                });
        } else builder.setSingleChoiceItems(labels, current, (dialog, which) -> {
            if (!prompt.isComplete()) result.complete(prompt.confirm(choices.get(which).id)); dialog.dismiss();
        });
        return show(prompt, builder, result);
    }

    private static void flatten(ChoicePrompt.Choice[] values, List<ChoicePrompt.Choice> output, boolean disabled) {
        for (ChoicePrompt.Choice value : values) {
            if (value.items != null) flatten(value.items, output, disabled || value.disabled);
            else if (!disabled && !value.disabled && !value.separator) output.add(value);
        }
    }

    void close() { if (shown != null) { AlertDialog dialog = shown; shown = null; dialog.dismiss(); } }
}
