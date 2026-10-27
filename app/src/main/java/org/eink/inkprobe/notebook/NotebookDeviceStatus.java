package org.eink.inkprobe.notebook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Minute clock and event-driven device indicators. No TextClock or animated widgets. */
final class NotebookDeviceStatus extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BooleanSupplier idle;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final NotebookDeviceStatusState state;
    private NotebookDeviceStatusState.Snapshot shown;
    private final NotebookIconDrawable battery = new NotebookIconDrawable(NotebookIconDrawable.Icon.BATTERY);
    private final NotebookIconDrawable wifi = new NotebookIconDrawable(NotebookIconDrawable.Icon.WIFI_OFF);
    private final Set<Network> wifiNetworks = new HashSet<>();
    private final ConnectivityManager connectivity;
    private final int clockWidth, percentWidth;
    private boolean started, networkRegistered;
    private int networkEpoch;
    private ConnectivityManager.NetworkCallback network;

    NotebookDeviceStatus(Context context, BooleanSupplier idle) {
        super(context, null, 0);
        this.idle = idle;
        setBackgroundColor(Color.WHITE);
        setStateListAnimator(null);
        setDefaultFocusHighlightEnabled(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
        paint.setFontFeatureSettings("tnum");
        clockWidth = clockWidth(context);
        percentWidth = (int) Math.ceil(paint.measureText("100%"));
        state = new NotebookDeviceStatusState(clock());
        shown = state.publish(true); // Initial drawing precedes native pen enablement.
        connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        updateDescription();
        setOnClickListener(view -> {
            if (context instanceof NotebookDisplayActivity) ((NotebookDisplayActivity) context).openAppSettings();
        });
        setFocusable(true);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!started) return;
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                boolean present = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
                state.battery(present ? intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1,
                    intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1));
            }
            state.clock(clock());
            refresh();
        }
    };

    private ConnectivityManager.NetworkCallback networkCallback(final int epoch) {
        return new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network value) {
                if (!started || epoch != networkEpoch) return;
                wifiNetworks.add(value); publishWifi();
            }
            @Override public void onLost(Network value) {
                if (!started || epoch != networkEpoch) return;
                wifiNetworks.remove(value); publishWifi();
            }
            @Override public void onCapabilitiesChanged(Network value, NetworkCapabilities capabilities) {
                if (!started || epoch != networkEpoch) return;
                if (physicalWifi(capabilities)) wifiNetworks.add(value);
                else wifiNetworks.remove(value);
                publishWifi();
            }
        };
    }

    private static boolean physicalWifi(NetworkCapabilities capabilities) {
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    void start() {
        if (started) return;
        started = true;
        network = networkCallback(++networkEpoch);
        state.clock(clock());
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getContext().registerReceiver(receiver, filter);
        wifiNetworks.clear();
        try {
            if (connectivity == null) throw new IllegalStateException("No connectivity service");
            NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
            // Passive registration observes Wi-Fi even while a VPN is the default network.
            connectivity.registerNetworkCallback(request, network, main);
            networkRegistered = true;
            for (Network value : connectivity.getAllNetworks()) {
                NetworkCapabilities capabilities = connectivity.getNetworkCapabilities(value);
                if (physicalWifi(capabilities))
                    wifiNetworks.add(value);
            }
            publishWifi();
        } catch (RuntimeException unavailable) {
            state.wifi(-1); refresh();
        }
        refresh();
    }

    void stop() {
        if (!started) return;
        started = false;
        ++networkEpoch;
        getContext().unregisterReceiver(receiver);
        if (networkRegistered) {
            try { connectivity.unregisterNetworkCallback(network); }
            catch (RuntimeException ignored) { /* Already removed by the system. */ }
            networkRegistered = false;
        }
        wifiNetworks.clear();
    }

    /** Also called after pen-idle; pending events do not schedule a redraw while writing. */
    void refresh() {
        if (!started) return;
        NotebookDeviceStatusState.Snapshot next = state.publish(idle.getAsBoolean());
        if (next == null) return;
        shown = next;
        wifi.setIcon(next.wifi == 1 ? NotebookIconDrawable.Icon.WIFI : NotebookIconDrawable.Icon.WIFI_OFF);
        updateDescription();
        invalidate();
    }

    private void publishWifi() {
        state.wifi(wifiNetworks.isEmpty() ? 0 : 1);
        refresh();
    }

    private void updateDescription() {
        setContentDescription(shown.clock + ", battery "
            + (shown.battery < 0 ? "unavailable" : shown.battery + " percent")
            + (shown.wifi < 0 ? ", Wi-Fi status unavailable" : shown.wifi == 1 ? ", Wi-Fi connected" : ", Wi-Fi disconnected")
            + "; tap to open settings");
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = clockWidth + percentWidth + dp(16 + 16 + 4 + 8 + 8);
        int height = Math.max(dp(24), (int) Math.ceil(paint.descent() - paint.ascent()) + dp(4));
        setMeasuredDimension(resolveSize(width, widthSpec), resolveSize(height, heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        float baseline = getHeight() / 2f - (paint.ascent() + paint.descent()) / 2;
        canvas.drawText(shown.clock, clockWidth - paint.measureText(shown.clock), baseline, paint);
        int size = dp(16), top = (getHeight() - size) / 2;
        int left = clockWidth + dp(8);
        if (shown.battery > 0) {
            int save = canvas.save();
            canvas.translate(left, top); canvas.scale(size / 24f, size / 24f);
            canvas.drawRect(3.5f, 8.5f, 3.5f + 15 * shown.battery / 100f, 15.5f, paint);
            canvas.restoreToCount(save);
        }
        battery.setBounds(left, top, left + size, top + size); battery.draw(canvas);
        left += size + dp(4);
        String percent = shown.battery < 0 ? "--%" : shown.battery + "%";
        canvas.drawText(percent, left + percentWidth - paint.measureText(percent), baseline, paint);
        left += percentWidth + dp(8);
        wifi.setBounds(left, top, left + size, top + size); wifi.draw(canvas);
    }

    private String clock() { return DateFormat.getTimeFormat(getContext()).format(new Date()); }

    private int clockWidth(Context context) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        java.text.DateFormat twelve = new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "hm"), locale);
        java.text.DateFormat twentyFour = new SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "Hm"), locale);
        java.text.DateFormat preferred = DateFormat.getTimeFormat(context);
        Calendar sample = Calendar.getInstance(); sample.set(Calendar.MINUTE, 58);
        float maximum = 0;
        for (int hour = 0; hour < 24; hour++) {
            sample.set(Calendar.HOUR_OF_DAY, hour);
            for (java.text.DateFormat format : new java.text.DateFormat[]{twelve, twentyFour, preferred})
                maximum = Math.max(maximum, paint.measureText(format.format(sample.getTime())));
        }
        return (int) Math.ceil(maximum);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
