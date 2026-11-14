package org.eink.inkprobe.notebook;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;

/** One maintained browser runtime per app process, independent of the firmware WebView. */
final class NotebookBrowserRuntime {
    static final String EXTENSION_ID = "browser@scriflow.local";
    private static GeckoRuntime runtime;
    private static GeckoResult<WebExtension> extension;
    private static final Map<GeckoSession, Consumer<WebExtension.Port>> owners = new IdentityHashMap<>();
    private NotebookBrowserRuntime() { }

    static GeckoRuntime get(Context context) {
        if (runtime != null) return runtime;
        Context app = context.getApplicationContext();
        boolean debug = (app.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        GeckoRuntimeSettings.Builder settings = new GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true).remoteDebuggingEnabled(debug).consoleOutput(debug);
        // Only our private configuration is read. No account, telemetry or predictive
        // navigation is needed for visiting and saving pages in this app.
        try {
            File config = new File(app.getFilesDir(), "browser-runtime.yaml");
            String prefs = "prefs:\n  toolkit.telemetry.enabled: false\n  toolkit.telemetry.unified: false\n"
                + "  datareporting.healthreport.uploadEnabled: false\n  datareporting.policy.dataSubmissionEnabled: false\n"
                + "  app.normandy.enabled: false\n  app.shield.optoutstudies.enabled: false\n"
                + "  browser.ping-centre.telemetry: false\n  network.prefetch-next: false\n"
                + "  network.dns.disablePrefetch: true\n  network.predictor.enabled: false\n"
                + "  browser.sessionstore.interval: 1000\n";
            try (FileOutputStream output = new FileOutputStream(config)) { output.write(prefs.getBytes(StandardCharsets.UTF_8)); }
            settings.configFilePath(config.getAbsolutePath());
        } catch (java.io.IOException error) { throw new IllegalStateException("Could not prepare the browser profile.", error); }
        runtime = GeckoRuntime.create(app, settings.build());
        runtime.getSettings().getContentBlocking().setCookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY);
        return runtime;
    }

    static GeckoResult<WebExtension> extension(Context context) {
        if (extension == null) extension = get(context).getWebExtensionController()
            .ensureBuiltIn("resource://android/assets/browser-extension/", EXTENSION_ID);
        return extension;
    }

    static void bind(WebExtension extension, GeckoSession session, Consumer<WebExtension.Port> owner) {
        owners.put(session, owner);
        extension.setMessageDelegate(new WebExtension.MessageDelegate() {
            @Override public void onConnect(WebExtension.Port port) {
                Consumer<WebExtension.Port> listener = owners.get(port.sender.session);
                if (listener == null) port.disconnect(); else listener.accept(port);
            }
        }, "scriflow");
    }
    static void unbind(GeckoSession session) { owners.remove(session); }
}
