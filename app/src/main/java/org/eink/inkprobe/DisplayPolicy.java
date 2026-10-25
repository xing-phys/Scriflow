package org.eink.inkprobe;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import org.json.JSONObject;

/** App-local declaration checks. Effective vendor policy is verified separately from ADB. */
public final class DisplayPolicy {
    public static final int HANDWRITING_MODE = 13;

    private DisplayPolicy() {}
    private static int declared(Context context) throws Exception {
        Bundle data = context.getPackageManager().getApplicationInfo(context.getPackageName(),
            PackageManager.GET_META_DATA).metaData;
        return data == null ? 0 : data.getInt("hw_eink.app.refresh.mode", 0);
    }
    static JSONObject json(Context context) throws Exception {
        JSONObject result = new JSONObject();
        result.put("declaredMode", declared(context));
        result.put("source", "application manifest; effective policy requires host query");
        return result;
    }
    public static void requireHandwritingPolicy(Context context) throws Exception {
        int actual = declared(context);
        if (actual != HANDWRITING_MODE)
            throw new IllegalStateException("Handwriting disabled: expected declared app policy "
                + HANDWRITING_MODE + ", got " + actual);
    }
}
