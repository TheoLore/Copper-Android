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
     * PanVK: load libvulkan_panfrost.so (mali_kbase) and use Mesa EGL for GL.
     * Any Mali with /dev/mali0 — not limited to one GPU model.
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
            try {
                if (!so.exists() || so.length() < 1024) {
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
                    File alt = new File(runtimeDir, "libvulkan_panfrost-android-aarch64.so");
                    if (!so.exists() && alt.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        alt.renameTo(so);
                    }
                }
            } catch (Throwable t) {
                android.util.Log.e("PanVK", "extract error", t);
            }
            envMap.put("MOD_ANDROID_RUNTIME", runtimeDir.getAbsolutePath());
            // Mesa needs a GL driver name; panvk is Vulkan-only so Mesa uses its GL-over-Vulkan path
            envMap.put("MESA_LOADER_DRIVER_OVERRIDE", "zink");
            envMap.put("MESA_GLSL_VERSION_OVERRIDE", "460");
            envMap.put("MESA_GL_VERSION_OVERRIDE", "4.6");
            super.setupEnvironment(context, envMap);
        }
        public boolean setupRenderer() {
            try {
                MojoExec.setUsePanvk(true);
                MojoExec.setUseTurnip(false);
            } catch (Throwable t) {
                android.util.Log.w("PanVK", "setUsePanvk missing", t);
            }
            MojoExec.preloadVulkan();
            return super.setupRenderer();
        }
    }
}
