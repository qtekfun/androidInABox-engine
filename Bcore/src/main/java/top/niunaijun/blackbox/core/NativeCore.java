package top.niunaijun.blackbox.core;


import android.os.Process;
import android.util.Log;

import androidx.annotation.Keep;

import java.io.File;
import java.util.List;

import dalvik.system.DexFile;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.utils.compat.DexFileCompat;

import top.niunaijun.blackbox.core.system.JarManager;
import top.niunaijun.blackbox.utils.Slog;


public class NativeCore {
    public static final String TAG = "NativeCore";

    static {

        System.loadLibrary("blackbox");
    }

    public static native boolean init(int apiLevel);

    public static native boolean isJniHookHealthy();

    public static native void enableIO();

    public static native void addIORule(String targetPath, String relocatePath);

    public static native void hideXposed();

    public static native boolean disableResourceLoading();

    /** Activa o desactiva el perfil falso de propiedades en este proceso (apagado por defecto). */
    public static native boolean setPropertySpoof(boolean on);


    @Keep
    private static volatile int sBoxServerPid = -1;

    /** Hook nativo de IPCThreadState::getCallingUid (Binder.getCallingUid es @CriticalNative en Android 12+). */
    public static native boolean setCallingUidMapping(int hostUid, int serverPid, int selfUid);

    /** PID del proceso servidor del box (<host>:black), buscando en /proc; -1 si no está. */
    public static int findBoxServerPid() {
        java.io.File[] procs = new java.io.File("/proc").listFiles();
        if (procs == null) {
            return -1;
        }
        for (java.io.File f : procs) {
            String n = f.getName();
            if (n.isEmpty() || !Character.isDigit(n.charAt(0))) {
                continue;
            }
            try {
                int pid = Integer.parseInt(n);
                if (isBoxServerPid(pid)) {
                    return pid;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    /** El PID es el del proceso servidor del box (<host>:black), leído de /proc/<pid>/cmdline. */
    private static boolean isBoxServerPid(int pid) {
        if (pid <= 0) {
            return false;
        }
        if (pid == sBoxServerPid) {
            return true;
        }
        try (java.io.FileInputStream in = new java.io.FileInputStream("/proc/" + pid + "/cmdline")) {
            byte[] buf = new byte[256];
            int n = in.read(buf);
            if (n <= 0) {
                return false;
            }
            String name = new String(buf, 0, n).trim();
            int end = name.indexOf('\0');
            if (end >= 0) {
                name = name.substring(0, end);
            }
            if (name.equals(BlackBoxCore.getHostPkg() + ":black")) {
                sBoxServerPid = pid;
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static final java.util.concurrent.atomic.AtomicInteger sCrossGuestLogs = new java.util.concurrent.atomic.AtomicInteger();

    public static int getCallingUid(int origCallingUid) {
        try {
            
            if (origCallingUid > 0 && origCallingUid < Process.FIRST_APPLICATION_UID)
                return origCallingUid;
            
            if (origCallingUid > Process.LAST_APPLICATION_UID)
                return origCallingUid;

            if (origCallingUid == BlackBoxCore.getHostUid()) {
                // Dentro del box el servidor del motor (:black) hace de system_server. Un UID de sistema no
                // existe en el box (getPackagesForUid(1000) no da nada), así que se presenta como el propio
                // proceso: es lo que las apps consideran de confianza (p. ej. GMS: callingUid == myUid()).
                if (isBoxServerPid(android.os.Binder.getCallingPid())) {
                    int self = top.niunaijun.blackbox.app.BActivityThread.getBUid();
                    return self > 0 ? self : Process.SYSTEM_UID;
                }
                
                String appPackageName = BlackBoxCore.getAppPackageName();
                if (appPackageName != null && appPackageName.equals("com.google.android.gms")){
                    
                }
                
                
                
                if (appPackageName != null && appPackageName.equals("com.google.android.webview")){
                    return Process.myUid();
                }
                
                
                // Llamada entre procesos invitados: el UID real del llamante es el del host, y es el que
                // lleva su AttributionSource. callingBUid es el del proceso que lanzó este, no el del
                // llamante de esta transacción, y hacía fallar enforceCallingUid en los providers.
                if (sCrossGuestLogs.getAndIncrement() < 5) {
                    Log.d(TAG, "getCallingUid entre invitados: uid real " + origCallingUid
                            + ", sin callingBUid obsoleto");
                }
                
                try {
                    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
                    for (StackTraceElement element : stackTrace) {
                        String className = element.getClassName();
                        String methodName = element.getMethodName();
                        
                        
                        if ((className.contains("Settings") || className.contains("FeatureFlag")) &&
                            (methodName.contains("getString") || methodName.contains("getInt") || 
                             methodName.contains("getLong") || methodName.contains("getFloat"))) {
                            
                            Log.d(TAG, "System settings access detected, using system UID to prevent mismatch");
                            
                            return Process.SYSTEM_UID; 
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error analyzing stack trace for UID resolution: " + e.getMessage());
                }
                
                
                return BlackBoxCore.getHostUid();
            }
            return origCallingUid;
        } catch (Exception e) {
            Log.e(TAG, "Error in getCallingUid: " + e.getMessage());
            
            return Process.myUid();
        }
    }

    @Keep
    public static String redirectPath(String path) {
        return IOCore.get().redirectPath(path);
    }

    @Keep
    public static File redirectPath(File path) {
        return IOCore.get().redirectPath(path);
    }

    @Keep
    public static long[] loadEmptyDex() {
        try {
            File emptyJar = JarManager.getInstance().getEmptyJar();
            if (emptyJar == null) {
                Log.w(TAG, "Empty JAR not available, attempting sync initialization");
                JarManager.getInstance().initializeSync();
                emptyJar = JarManager.getInstance().getEmptyJar();
            }
            
            if (emptyJar == null || !emptyJar.exists()) {
                Log.e(TAG, "Empty JAR file not found or invalid");
                return new long[]{};
            }
            
            DexFile dexFile = new DexFile(emptyJar);
            List<Long> cookies = DexFileCompat.getCookies(dexFile);
            long[] longs = new long[cookies.size()];
            for (int i = 0; i < cookies.size(); i++) {
                longs[i] = cookies.get(i);
            }
            Log.d(TAG, "Successfully loaded empty DEX with " + cookies.size() + " cookies");
            return longs;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load empty DEX", e);
        }
        return new long[]{};
    }
    
    
    private static long[] createFallbackEmptyDex() {
        try {
            Slog.d(TAG, "Creating fallback empty DEX");
            
            
            
            byte[] emptyDexBytes = createMinimalDexBytes();
            
            
            File tempDexFile = File.createTempFile("fallback_empty", ".dex");
            tempDexFile.deleteOnExit();
            
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempDexFile);
            fos.write(emptyDexBytes);
            fos.close();
            
            
            DexFile dexFile = new DexFile(tempDexFile);
            List<Long> cookies = DexFileCompat.getCookies(dexFile);
            
            if (cookies != null && !cookies.isEmpty()) {
                long[] longs = new long[cookies.size()];
                for (int i = 0; i < cookies.size(); i++) {
                    longs[i] = cookies.get(i);
                }
                
                Slog.d(TAG, "Successfully created fallback empty DEX with " + cookies.size() + " cookies");
                return longs;
            }
            
        } catch (Exception e) {
            Slog.e(TAG, "Error creating fallback empty DEX: " + e.getMessage());
        }
        
        
        Slog.w(TAG, "Returning empty DEX array as last resort");
        return new long[]{};
    }
    
    
    private static byte[] createMinimalDexBytes() {
        
        
        
        
        
        byte[] dexHeader = {
            'd', 'e', 'x', '\n',  
            0x30, 0x33, 0x35, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x70, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00,  
            0x00, 0x00, 0x00, 0x00   
        };
        
        return dexHeader;
    }
}
