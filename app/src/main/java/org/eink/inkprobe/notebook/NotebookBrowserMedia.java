package org.eink.inkprobe.notebook;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/** Foreground HTTPS microphone requests require Android permission and consent for the exact origin. */
final class NotebookBrowserMedia implements GeckoSession.PermissionDelegate {
    private final Activity activity;
    private final NotebookMicrophone microphone;
    private final BooleanSupplier allowed;
    private final Supplier<String> address;
    private AlertDialog dialog;
    private String approved = "";
    private long document;

    NotebookBrowserMedia(Activity activity, NotebookMicrophone microphone, BooleanSupplier allowed, Supplier<String> address) {
        this.activity = activity; this.microphone = microphone; this.allowed = allowed; this.address = address;
    }

    @Override public void onAndroidPermissionsRequest(GeckoSession session, String[] permissions, Callback callback) {
        // Gecko can ask to enumerate devices through a different (hidden) window.
        // Request Android access only at the later origin-specific media request.
        if (allowed.getAsBoolean() && permissions != null && permissions.length == 1
                && Manifest.permission.RECORD_AUDIO.equals(permissions[0]) && microphone.granted()) callback.grant();
        else callback.reject();
    }

    @Override public void onMediaPermissionRequest(GeckoSession session, String uri, MediaSource[] video, MediaSource[] audio, MediaCallback callback) {
        String origin = NotebookBrowserAddress.origin(uri);
        if (!allowed.getAsBoolean() || !origin.startsWith("https://") || !origin.equals(NotebookBrowserAddress.origin(address.get()))
                || video != null && video.length != 0 || audio == null || audio.length == 0 || dialog != null) { callback.reject(); return; }
        MediaSource selected = null;
        for (MediaSource source : audio) if (source.type == MediaSource.TYPE_AUDIO && source.source == MediaSource.SOURCE_MICROPHONE) { selected = source; break; }
        if (selected == null) { callback.reject(); return; }
        int decision = NotebookWebsitePermissions.get(activity, origin);
        if (decision == NotebookWebsitePermissions.BLOCK) { callback.reject(); return; }
        if (!microphone.granted()) {
            long expected = document;
            microphone.request(granted -> {
                if (granted && expected == document && allowed.getAsBoolean()
                        && origin.equals(NotebookBrowserAddress.origin(address.get()))) onMediaPermissionRequest(session, uri, video, audio, callback);
                else callback.reject();
            });
            return;
        }
        if (decision == NotebookWebsitePermissions.ALLOW) { approved = origin; callback.grant(null, selected); return; }
        MediaSource source = selected; long expected = document; int[] choice = {NotebookWebsitePermissions.ASK};
        AlertDialog prompt = new AlertDialog.Builder(activity).setTitle("Allow microphone?")
            .setMessage(origin + " wants to use the microphone for voice input. You can change this website’s permission in Settings.")
            .setNegativeButton("Don't allow", (value, which) -> choice[0] = NotebookWebsitePermissions.BLOCK)
            .setPositiveButton("Allow", (value, which) -> choice[0] = NotebookWebsitePermissions.ALLOW).create();
        dialog = prompt;
        prompt.setOnDismissListener(value -> {
            if (dialog == prompt) dialog = null;
            boolean current = expected == document && allowed.getAsBoolean() && microphone.granted()
                && origin.equals(NotebookBrowserAddress.origin(address.get()));
            if (current && choice[0] != NotebookWebsitePermissions.ASK) NotebookWebsitePermissions.set(activity, origin, choice[0]);
            if (current && choice[0] == NotebookWebsitePermissions.ALLOW) { approved = origin; callback.grant(null, source); }
            else callback.reject();
        });
        NotebookUi.show(prompt);
    }

    @Override public GeckoResult<Integer> onContentPermissionRequest(GeckoSession session, ContentPermission permission) {
        boolean voicePlayback = permission.permission == PERMISSION_AUTOPLAY_AUDIBLE && allowed.getAsBoolean()
            && microphone.granted() && !approved.isEmpty() && approved.equals(NotebookBrowserAddress.origin(permission.uri))
            && approved.equals(NotebookBrowserAddress.origin(address.get()))
            && NotebookWebsitePermissions.get(activity, approved) == NotebookWebsitePermissions.ALLOW;
        return GeckoResult.fromValue(voicePlayback ? ContentPermission.VALUE_ALLOW : ContentPermission.VALUE_DENY);
    }

    void cancelPrompt() { if (dialog != null) dialog.dismiss(); }
    void reset() { ++document; approved = ""; cancelPrompt(); }
}
