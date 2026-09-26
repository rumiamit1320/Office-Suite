package org.libreoffice.kit;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
            // LibreOffice Android deliberately keeps files such as udkapi.rdb
            // under assets/unpack and expands them into the application's data
            // directory before the native bootstrap is entered. Do this
            // explicitly so the embedded runtime does not depend on an
            // app-specific unpack hook being invoked first.
            ensureRuntimeUnpacked(dataDir);

            if (!initializeNative(dataDir, cacheDir, apkFile, mgr)) {
                Log.e(LOGTAG, "Initialize native failed");
                redirectStdio(false);
                return false;
            }

            initializeDone = true;
            return true;
        } catch (Throwable t) {
            Log.e(LOGTAG, "Initialize LibreOfficeKit threw", t);
            redirectStdio(false);
            return false;
        }
    }

    private static void ensureRuntimeUnpacked(String dataDir) throws IOException {
        File root = new File(dataDir);
        File marker = new File(root, ".docuflow_lokit_runtime_ready");
        File required = new File(root, "program/udkapi.rdb");

        if (marker.isFile() && required.isFile() && required.length() > 0) {
            return;
        }

        Log.i(LOGTAG, "Unpacking LibreOffice runtime assets from assets/unpack");

        File tempRoot = new File(root, ".docuflow_lokit_unpacking");
        deleteTree(tempRoot);
        if (!tempRoot.mkdirs() && !tempRoot.isDirectory()) {
            throw new IOException("Unable to create runtime staging directory: " + tempRoot);
        }

        copyAssetTree("unpack", tempRoot);

        File stagedRequired = new File(tempRoot, "program/udkapi.rdb");
        if (!stagedRequired.isFile() || stagedRequired.length() == 0) {
            deleteTree(tempRoot);
            throw new IOException("LibreOffice runtime is incomplete: assets/unpack/program/udkapi.rdb is missing");
        }

        // Replace only the runtime directories we own. Existing user data and
        // application cache remain untouched.
        replaceDirectory(new File(root, "program"), new File(tempRoot, "program"));
        if (new File(tempRoot, "user").isDirectory()) {
            replaceDirectory(new File(root, "user"), new File(tempRoot, "user"));
        }
        if (new File(tempRoot, "etc").isDirectory()) {
            replaceDirectory(new File(root, "etc"), new File(tempRoot, "etc"));
        }

        deleteTree(tempRoot);

        File markerTmp = new File(root, ".docuflow_lokit_runtime_ready.tmp");
        try (FileOutputStream output = new FileOutputStream(markerTmp)) {
            output.write("docuflow-lokit-runtime-v2\n".getBytes("UTF-8"));
            output.flush();
            output.getFD().sync();
        }
        if (!markerTmp.renameTo(marker)) {
            marker.delete();
            if (!markerTmp.renameTo(marker)) {
                throw new IOException("Unable to commit LibreOffice runtime marker");
            }
        }

        Log.i(LOGTAG, "LibreOffice runtime unpacked; udkapi.rdb=" +
                required.getAbsolutePath() + " (" + required.length() + " bytes)");
    }

    private static void copyAssetTree(String assetPath, File destination) throws IOException {
        String[] children = mgr.list(assetPath);
        if (children == null) {
            throw new IOException("Unable to list LibreOffice asset directory: " + assetPath);
        }

        if (children.length == 0) {
            copyAssetFile(assetPath, destination);
            return;
        }

        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Unable to create directory: " + destination);
        }

        for (String child : children) {
            String childAssetPath = assetPath + "/" + child;
            File childDestination = new File(destination, child);
            String[] nested = mgr.list(childAssetPath);
            if (nested != null && nested.length > 0) {
                copyAssetTree(childAssetPath, childDestination);
            } else {
                copyAssetFile(childAssetPath, childDestination);
            }
        }
    }

    private static void copyAssetFile(String assetPath, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Unable to create parent directory: " + parent);
        }

        try (InputStream input = mgr.open(assetPath, AssetManager.ACCESS_STREAMING);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
            output.getFD().sync();
        }
    }

    private static void replaceDirectory(File target, File staged) throws IOException {
        if (!staged.isDirectory()) return;

        File backup = new File(target.getParentFile(), target.getName() + ".docuflow-backup");
        deleteTree(backup);
        if (target.exists() && !target.renameTo(backup)) {
            deleteTree(target);
            if (target.exists()) {
                throw new IOException("Unable to replace runtime directory: " + target);
            }
        }

        if (!staged.renameTo(target)) {
            if (backup.isDirectory() && !target.exists()) {
                backup.renameTo(target);
            }
            throw new IOException("Unable to install runtime directory: " + target);
        }

        deleteTree(backup);
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        file.delete();
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
