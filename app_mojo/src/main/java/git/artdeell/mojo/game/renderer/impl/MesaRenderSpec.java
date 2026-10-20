package git.artdeell.mojo.game.renderer.impl;

import static android.os.Build.VERSION.SDK_INT;

import android.content.Context;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.game.renderer.RenderSpec;
import git.artdeell.mojo.game.renderer.def.Renderers;
import git.artdeell.mojo.utils.plugins.LibraryPlugin;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.utils.GpuUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import git.artdeell.mojo.R;
import git.artdeell.mojoexec.MojoExec;

/**
 * Mesa3D RenderSpec. Provides desktop Mesa, zink & freedreno
 */
public class MesaRenderSpec implements RenderSpec {
    public String library() {
        return "libEGL_mesa.so";
    }
    public void setupEnvironment(Context context, Map<String, String> envMap) {
        envMap.put("MESA_GLSL_CACHE_DIR", Tools.DIR_CACHE.getAbsolutePath());
    }
    protected boolean hasMesa() {
        return SDK_INT >= 29 && new File(Tools.NATIVE_LIB_DIR, this.library()).exists();
    }
    public boolean compatibleDevice(Context context) {
        return hasMesa() && GpuUtils.checkChromebook(context.getPackageManager());
    }
    public String name() {
        return "Mesa";
    }
    public int displayName() {
        return R.string.mcl_setting_renderer_mesa_desktop;
    }
    public String tag() {
        return Renderers.MESA_RENDERER;
    }
    public boolean setupRenderer() {
        return MojoExec.prepareEgl(library(), true, false, 3);
    }

    public static class ZinkRenderSpec extends MesaRenderSpec {
        public String name() {
            return "ZINK";
        }
        public String tag() {
            return Renderers.ZINK_RENDERER;
        }
        public int displayName() {
            return R.string.mcl_setting_renderer_vulkan_zink;
        }
        public void setupEnvironment(Context context, Map<String, String> envMap) {
            envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
            envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
            envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
            super.setupEnvironment(context, envMap);
        }
        public boolean setupRenderer() {
            MojoExec.preloadVulkan();
            return super.setupRenderer();
        }
        public boolean compatibleDevice(Context context) {
            return hasMesa() && GpuUtils.checkVulkanSupport(context.getPackageManager());
        }
    }

    public static class FreedrenoRenderSpec extends MesaRenderSpec {
        public String name() {
            return "Freedreno";
        }
        public int displayName() {
            return R.string.mcl_setting_renderer_freedreno_kgsl;
        }
        public String tag() {
            return Renderers.FREEDRENO_RENDERER;
        }
        public boolean compatibleDevice(Context context) {
            return hasMesa() && GpuUtils.getGlInfo().isAdreno();
        }
        public void setupEnvironment(Context context, Map<String, String> envMap) {
            if (LauncherPreferences.PREF_FREEDRENO_SYSMEM) envMap.put("FD_MESA_DEBUG", "sysmem");
            if(LauncherPreferences.PREF_UBWC_WORKAROUND) envMap.put("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1");
            envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "kgsl");
            if (GpuUtils.getGlInfo().isAdreno500Lower()) {
                envMap.put("MESA_GL_VERSION_OVERRIDE", "3.3");
                envMap.put("MESA_GLSL_VERSION_OVERRIDE", "330");
            }
            super.setupEnvironment(context, envMap);
        }
    }
    public static class ExtMesaRenderSpec extends MesaRenderSpec {
        private LibraryPlugin provider;
        protected String plugin() {
            return LibraryPlugin.ID_MESA_PLUGIN;
        }
        public String name() {
            return "Mesa (external)";
        }
        public String librarySearchPath() {
            return provider.getLibraryPath();
        }
        public String tag() {
            return Renderers.MESA_RENDERER_EXT;
        }
        public int displayName() {
            return R.string.mcl_setting_renderer_mesa_desktop_ext;
        }
        private boolean discover(Context context) {
            if(provider == null) provider = LibraryPlugin.discoverPlugin(context, plugin());
            return provider != null;
        }
        public boolean compatibleDevice(Context context) {
            return discover(context) && provider.checkLibraries(library());
        }
        public void setupEnvironment(Context context, Map<String, String> envMap) {
            discover(context);
            super.setupEnvironment(context, envMap);
        }
        public boolean setupRenderer() {
            if(provider == null) return false;
            return MojoExec.prepareEgl(provider.resolveAbsolutePath(library()), true, false, 0);
        }
    }
    public static class LegacyZinkRenderSpec extends ExtMesaRenderSpec {
        protected String plugin() {
            return LibraryPlugin.ID_ZINK_PLUGIN;
        }
        public String name() {
            return "ZINK (Legacy)";
        }
        public String tag() {
            return Renderers.LEGACYZINK_RENDERER;
        }
        public int displayName() {
            return R.string.mcl_setting_renderer_vulkan_lzink;
        }
        public void setupEnvironment(Context context, Map<String, String> envMap) {
            super.setupEnvironment(context, envMap);
            envMap.put("MESA_GL_VERSION_OVERRIDE", "4.3");
            envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
            envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
        }
        public boolean setupRenderer() {
            MojoExec.preloadVulkan();
            return super.setupRenderer();
        }
        public boolean compatibleDevice(Context context) {
            return GpuUtils.checkVulkanSupport(context.getPackageManager()) && super.compatibleDevice(context);
        }
        public String library() {
            return "libEGL_legacy.so";
        }
    }

    /**
     * PanVK ICD (libvulkan_panfrost.so via mali_kbase).
     * .so lives in jniLibs/arm64-v8a and/or is extracted to cache/app_runtime_mod.
     * Loader finds it via VK_ICD_FILENAMES + absolute library_path in the ICD JSON.
     */
    public static class PanvkRenderSpec extends MesaRenderSpec {
        public String name() {
            return "PanVK";
        }
        public String tag() {
            return Renderers.PANVK_RENDERER;
        }
        public int displayName() {
            return R.string.mcl_setting_renderer_vulkan_panvk;
        }
        public boolean compatibleDevice(Context context) {
            return hasMesa()
                    && GpuUtils.checkVulkanSupport(context.getPackageManager())
                    && GpuUtils.getGlInfo().isArm();
        }
        public void setupEnvironment(Context context, Map<String, String> envMap) {
            File runtimeDir = new File(context.getCacheDir(), "app_runtime_mod");
            //noinspection ResultOfMethodCallIgnored
            runtimeDir.mkdirs();
            File so = new File(runtimeDir, "libvulkan_panfrost.so");

            // Prefer copy from packaged native lib dir (jniLibs/arm64-v8a)
            File nativeSo = new File(Tools.NATIVE_LIB_DIR, "libvulkan_panfrost.so");
            try {
                if (nativeSo.exists() && nativeSo.length() > 1024) {
                    if (!so.exists() || so.length() != nativeSo.length()) {
                        try (java.io.InputStream in = new java.io.FileInputStream(nativeSo);
                             FileOutputStream out = new FileOutputStream(so)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        }
                        android.util.Log.i("PanVK", "Copied from nativeLibraryDir -> " + so.getAbsolutePath());
                    }
                } else if (!so.exists() || so.length() < 1024) {
                    try {
                        Tools.copyAssetFile(context, "components/panvk/libvulkan_panfrost.so",
                                runtimeDir.getAbsolutePath(), true);
                    } catch (Exception e1) {
                        try {
                            Tools.copyAssetFile(context, "runtime_mod/libvulkan_panfrost.so",
                                    runtimeDir.getAbsolutePath(), true);
                        } catch (Exception e2) {
                            android.util.Log.e("PanVK", "Failed to extract libvulkan_panfrost.so", e2);
                        }
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e("PanVK", "extract/copy error", t);
            }

            // Write ICD JSON with absolute path (required by Vulkan loader)
            File icd = new File(runtimeDir, "panfrost_icd.json");
            String soPath = so.exists() ? so.getAbsolutePath() : nativeSo.getAbsolutePath();
            String icdJson = "{\n"
                    + "  \"file_format_version\": \"1.0.0\",\n"
                    + "  \"ICD\": {\n"
                    + "    \"library_path\": \"" + soPath.replace("\\", "\\\\") + "\",\n"
                    + "    \"api_version\": \"1.1.0\"\n"
                    + "  }\n"
                    + "}\n";
            try (FileOutputStream fos = new FileOutputStream(icd)) {
                fos.write(icdJson.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                android.util.Log.e("PanVK", "Failed to write ICD json", e);
            }

            envMap.put("MOD_ANDROID_RUNTIME", runtimeDir.getAbsolutePath());
            envMap.put("VK_ICD_FILENAMES", icd.getAbsolutePath());
            envMap.put("VK_DRIVER_FILES", icd.getAbsolutePath());
            // Prefer our dir for dlopen of the ICD library
            String ld = runtimeDir.getAbsolutePath();
            if (Tools.NATIVE_LIB_DIR != null) ld = ld + ":" + Tools.NATIVE_LIB_DIR;
            String oldLd = System.getenv("LD_LIBRARY_PATH");
            if (oldLd != null && !oldLd.isEmpty()) ld = ld + ":" + oldLd;
            envMap.put("LD_LIBRARY_PATH", ld);

            // Mesa GL path over Vulkan ICD
            envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
            envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
            envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
            envMap.put("MESA_VK_VERSION_OVERRIDE", "1.1");
            // G52 jm-v7 is experimental; some builds still gate on this
            envMap.put("PAN_I_WANT_A_BROKEN_VULKAN_DRIVER", "1");
            envMap.put("PANVK_DEBUG", "info");
            super.setupEnvironment(context, envMap);

            android.util.Log.i("PanVK", "so=" + soPath + " exists=" + new File(soPath).exists()
                    + " size=" + new File(soPath).length()
                    + " ICD=" + icd.getAbsolutePath());
        }
        public boolean setupRenderer() {
            try {
                MojoExec.setUsePanvk(true);
                MojoExec.setUseTurnip(false);
            } catch (Throwable t) {
                android.util.Log.w("PanVK", "setUsePanvk missing", t);
            }
            try {
                MojoExec.preloadVulkan();
            } catch (Throwable t) {
                android.util.Log.e("PanVK", "preloadVulkan failed", t);
                return false;
            }
            try {
                return super.setupRenderer();
            } catch (Throwable t) {
                android.util.Log.e("PanVK", "prepareEgl failed", t);
                return false;
            }
        }
    }
}
