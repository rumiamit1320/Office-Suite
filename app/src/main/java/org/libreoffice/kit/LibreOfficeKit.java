package org.libreoffice.kit;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.util.Log;
import java.nio.ByteBuffer;

public final class LibreOfficeKit {
    private static final String LOGTAG = "LibreOfficeKit";
    private static AssetManager mgr;
    private static boolean initializeDone = false;

    private LibreOfficeKit() {}

    private static native boolean initializeNative(String dataDir, String cacheDir, String apkFile, AssetManager mgr);
    public static native ByteBuffer getLibreOfficeKitHandle();
    public static native void putenv(String string);
    public static native void redirectStdio(boolean state);

    public static synchronized boolean init(Activity activity) {
        if (initializeDone) return true;
        mgr = activity.getResources().getAssets();
        ApplicationInfo applicationInfo = activity.getApplicationInfo();
        String dataDir = applicationInfo.dataDir;
        String cacheDir = activity.getApplication().getCacheDir().getAbsolutePath();
        String apkFile = activity.getApplication().getPackageResourcePath();
        Log.i(LOGTAG, "Initializing LibreOfficeKit, dataDir=" + dataDir);
        redirectStdio(true);
        try {
            if (!initializeNative(dataDir, cacheDir, apkFile, mgr)) {
                Log.e(LOGTAG, "Initialize native failed");
                redirectStdio(false);
                return false;
            }
            initializeDone = true;
            return true;
        } catch (Throwable t) {
            Log.e(LOGTAG, "Initialize native threw", t);
            redirectStdio(false);
            return false;
        }
    }

    static { NativeLibLoader.load(); }

    private static final class NativeLibLoader {
        private static boolean done = false;
        static synchronized void load() {
            if (done) return;
            System.loadLibrary("nspr4");
            System.loadLibrary("plds4");
            System.loadLibrary("plc4");
            System.loadLibrary("nssutil3");
            System.loadLibrary("freebl3");
            System.loadLibrary("sqlite3");
            System.loadLibrary("softokn3");
            System.loadLibrary("nss3");
            System.loadLibrary("nssckbi");
            System.loadLibrary("nssdbm3");
            System.loadLibrary("smime3");
            System.loadLibrary("ssl3");
            System.loadLibrary("c++_shared");
            System.loadLibrary("lo-native-code");
            done = true;
        }
    }
}
