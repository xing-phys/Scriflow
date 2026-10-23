package org.eink.inkprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Strict, firmware-specific adapter: successful invocation is not evidence of visible live ink. */
final class HuaweiInk implements SurfaceHolder.Callback {
    interface Reporter { void report(String message); }
    final SurfaceView view;
    private final Reporter reporter;
    private final Trace trace;
    private final boolean editor;
    private Object surface, listener;
    private boolean wanted, attached, alive = true, surfaceReady, enabled, initialized;
    private int retries;

    HuaweiInk(Context context, Trace trace, Reporter reporter, boolean onTop, boolean editor) throws Exception {
        this.trace = trace; this.reporter = reporter; this.editor = editor;
        Class<?> type = Class.forName(editor ? "android.eink.view.edit.HWEditView" : "android.eink.view.HWSurfaceView");
        view = (SurfaceView) type.getConstructor(Context.class).newInstance(context);
        if (editor) {
            // Preserve native editor's own pixel format, Z ordering, and point listener.
            Object picture = get(view, "getSurfacePicture");
            call(picture, "bindColor", new Class<?>[]{int.class}, Color.WHITE);
        } else view.setZOrderOnTop(onTop);
        view.getHolder().addCallback(this);
        report(type.getSimpleName() + " constructed; " + (editor ? "native defaults" : "zOnTop=" + onTop));
    }
    private void report(String s) { reporter.report(s); }
    private static Object call(Object obj, String name, Class<?>[] types, Object... args) throws Exception {
        try {
            Method method = obj.getClass().getMethod(name, types);
            return method.invoke(obj, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new Exception(name + ": " + cause, cause);
        }
    }
    private static Object get(Object o, String name) throws Exception {
        return call(o, name, new Class<?>[0]);
    }
    private static int ballpoint() throws Exception {
        Class<?> type = Class.forName("android.eink.view.HandPenConfig");
        return type.getField("PEN_TYPE_YUANZHUBI").getInt(null);
    }
    void start() { wanted = true; retries = 0; view.post(this::tryEnable); }
    private void tryEnable() {
        if (!alive || !wanted) return;
        if (enabled) return;
        if (!surfaceReady || !view.getHolder().getSurface().isValid()) {
            if (++retries <= 15) view.postDelayed(this::tryEnable, 100);
            else report("ERROR: writing surface never became valid");
            return;
        }
        try {
            surface = get(view, "getFBSurface");
            if (surface == null) throw new IllegalStateException("getFBSurface returned null");
            if (get(view, "getActiveBuffer") == null)
                throw new IllegalStateException("Surface changed but active buffer is null");
            report("Framebuffer surface: " + surface.getClass().getName());
            if (!attached && !editor) {
                Class<?> iface = Class.forName("android.eink.view.IAhwTouchListener");
                listener = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (proxy, m, args) -> {
                    switch (m.getName()) {
                        case "toString": return "InkProbePointListener";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "onAhwAddPoint":
                            trace.point("huawei", (Integer) args[0], (Float) args[1], (Float) args[2],
                                (Long) args[3], (Float) args[4]);
                            // Deliberately NO app repaint, including at pen-up.
                            break;
                    }
                    return null;
                });
                call(surface, "setAhwTouchListener", new Class<?>[]{iface}, listener);
                attached = true;
                report("Point listener attached");
            }
            call(surface, "setAhwToolTypes", new Class<?>[]{int[].class}, new int[]{MotionEvent.TOOL_TYPE_STYLUS});
            call(surface, "setNeedAddDownEvent", new Class<?>[]{boolean.class}, true);
            call(surface, "setAhwPen", new Class<?>[]{int.class, float.class, int.class}, ballpoint(), 4f, Color.BLACK);
            if (editor) {
                int handwriting = view.getClass().getField("STATE_HADNWRITE").getInt(null);
                call(view, "setState", new Class<?>[]{int.class}, handwriting);
            } else if (!initialized) clearSurface();
            initialized = true;
            call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, true);
            enabled = Boolean.TRUE.equals(get(surface, "isAutoHandWrite"));
            report("Auto-HandWrite readback: " + enabled);
            if (!enabled) throw new IllegalStateException("Firmware rejected Auto-HandWrite activation");
            report("Stylus accepted: " + call(surface, "isAhwAcceptToolType", new Class<?>[]{int.class}, MotionEvent.TOOL_TYPE_STYLUS));
            report(editor ? "READY: native editing widget; Inspect reports its own track count."
                : "READY: firmware-only ink; write, then Inspect. Replay shows captured points separately.");
        } catch (Exception e) {
            report("ERROR enabling live ink: " + e);
            stop();
        }
    }
    void inspect() {
        try {
            if (surface == null) { report("No framebuffer surface"); return; }
            report("Auto-HandWrite=" + get(surface, "isAutoHandWrite")
                + "; last point time=" + get(surface, "getAhwLastPointEventTime"));
            Object[] blockers = (Object[]) get(surface, "dumpAhwDisableList");
            report("Disable tokens=" + (blockers == null ? 0 : blockers.length));
            if (editor) report("Native editor tracks=" + ((java.util.List<?>) get(view, "getTrackList")).size());
        } catch (Exception e) { report("ERROR inspecting: " + e); }
    }
    org.json.JSONObject nativeSnapshot() throws Exception {
        org.json.JSONObject result = new org.json.JSONObject();
        result.put("autoHandWrite", surface != null && Boolean.TRUE.equals(get(surface, "isAutoHandWrite")));
        if (!editor) return result;
        result.put("state", get(view, "getState"));
        org.json.JSONArray tracks = new org.json.JSONArray();
        for (Object track : (java.util.List<?>) get(view, "getTrackList")) {
            org.json.JSONArray points = new org.json.JSONArray();
            for (Object p : (java.util.List<?>) get(track, "getPoints")) {
                org.json.JSONArray a = new org.json.JSONArray();
                a.put(p.getClass().getField("x").getFloat(p));
                a.put(p.getClass().getField("y").getFloat(p));
                a.put(p.getClass().getField("eventtime").getLong(p));
                a.put(p.getClass().getField("pressure").getFloat(p));
                points.put(a);
            }
            tracks.put(points);
        }
        result.put("tracks_x_y_time_pressure", tracks);
        return result;
    }
    void clearSurface() throws Exception {
        if (surface == null) return;
        if (editor) { get(view, "clear"); return; }
        Canvas c = (Canvas) get(view, "lockActiveCanvas");
        if (c == null) throw new IllegalStateException("lockActiveCanvas returned null");
        try { c.drawColor(Color.WHITE); }
        finally { get(view, "unlockActiveCanvas"); }
        get(surface, "invalidate");
    }
    void stop() {
        wanted = false;
        enabled = false;
        if (surface != null) {
            try { call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, false); }
            catch (Exception e) { report("ERROR disabling: " + e); }
        }
        trace.cancel();
    }
    void release() {
        stop(); alive = false;
        detachListener();
        surface = null;
        view.getHolder().removeCallback(this);
    }
    private void detachListener() {
        if (surface != null && attached) {
            try {
                Class<?> iface = Class.forName("android.eink.view.IAhwTouchListener");
                call(surface, "setAhwTouchListener", new Class<?>[]{iface}, new Object[]{null});
            } catch (Exception e) { report("Listener cleanup: " + e); }
        }
        attached = false; listener = null;
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { surfaceReady = false; report("surfaceCreated"); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        boolean resume = wanted;
        stop(); wanted = resume;
        initialized = false;
        surfaceReady = width > 0 && height > 0;
        report("surfaceChanged " + width + "x" + height + " format=" + format);
        if (wanted) view.post(this::tryEnable);
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        boolean resume = wanted; stop(); wanted = resume;
        surfaceReady = false;
        initialized = false;
        detachListener();
        surface = null; attached = false; listener = null;
        report("surfaceDestroyed; ink disabled");
    }
}
