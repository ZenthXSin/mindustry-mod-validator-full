package modvalidator;

import arc.util.SharedLibraryLoader;

/**
 * Patches Arc's NativeUtils by loading a supplementary native library
 * containing setEnv/unsetEnv/getEnv implementations.
 *
 * This is needed because the precompiled libarc64.so from the Arc source tree
 * does not include NativeUtils methods (they were added in a later commit).
 *
 * The supplementary libnativeutils64.so is bundled in arc-natives jar.
 */
public class NativeUtilsPatch {
    private static boolean patched = false;

    /**
     * Load the supplementary native library so NativeUtils.setEnv etc. resolve correctly.
     * Must be called before ArcNativesLoader.load() (i.e., before SdlApplication starts).
     */
    public static synchronized void ensureLoaded() {
        if (patched) return;
        patched = true;

        try {
            new SharedLibraryLoader().load("nativeutils");
        } catch (Throwable ignored) {
            // If loading fails, NativeUtils calls will throw UnsatisfiedLinkError later
        }
    }
}
