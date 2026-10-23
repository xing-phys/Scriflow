import java.lang.reflect.Method;

/** ADB-shell-only, read-only inspection. This vendor API is hidden from ordinary APKs. */
public final class InkPolicy {
    public static void main(String[] args) throws Exception {
        Class<?> manager = Class.forName("com.huawei.android.hardware.display.HwDisplayManager");
        Method info = manager.getMethod("getHwContentDetectorInfo", String.class);
        boolean verified = false;
        for (String name : new String[]{"org.eink.inkprobe", "com.huawei.hinote"}) {
            Object bundle = info.invoke(null, name);
            if (bundle == null) throw new IllegalStateException("No policy returned for " + name);
            bundle.getClass().getMethod("size").invoke(bundle);
            Object mode = bundle.getClass().getMethod("get", String.class).invoke(bundle, "mode");
            System.out.println(name + ": " + bundle);
            if ("org.eink.inkprobe".equals(name)) verified = "3".equals(String.valueOf(mode));
        }
        if (!verified) throw new IllegalStateException("Ink Probe effective policy is not 3");
        System.out.println("Verified: Ink Probe effective application policy is 3.");
    }
}
