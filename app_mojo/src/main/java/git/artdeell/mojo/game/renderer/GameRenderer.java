package git.artdeell.mojo.game.renderer;

import static git.artdeell.mojo.game.renderer.def.Renderers.FREEDRENO_RENDERER;
import static git.artdeell.mojo.game.renderer.def.Renderers.GL4ES_RENDERER;
import static git.artdeell.mojo.game.renderer.def.Renderers.LEGACYZINK_RENDERER;
import static git.artdeell.mojo.game.renderer.def.Renderers.LTW_RENDERER;
import static git.artdeell.mojo.game.renderer.def.Renderers.MESA_RENDERER;
import static git.artdeell.mojo.game.renderer.def.Renderers.MESA_RENDERER_EXT;
import static git.artdeell.mojo.game.renderer.def.Renderers.MOBILEGLUES_RENDERER;
import static git.artdeell.mojo.game.renderer.def.Renderers.SFPEW_RENDERER;
import static git.artdeell.mojo.game.renderer.def.Renderers.ZINK_RENDERER;
import static git.artdeell.mojo.game.renderer.def.Renderers.PANVK_RENDERER;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import git.artdeell.mojo.Logger;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.game.renderer.impl.GLESRenderSpec;
import git.artdeell.mojo.game.renderer.impl.MesaRenderSpec;
import git.artdeell.mojo.prefs.LauncherPreferences;

import java.util.HashMap;
import java.util.Map;

import git.artdeell.mojoexec.MojoExec;

/*
 HOW TO ADD A NEW RENDERER:
 1. Create a new RenderSpec implementation
 2. Add a new constant to the Renderers class
 3. Add a new case to the GameRenderer#getKnownRenderer method
 4. Add a new string resource for the renderer name
 5. Add the renderer tag onto the list of renderers to check for the compatibility (see RendererCache)
 6. ???
 7. PROFIT

*/

/**
 * Class for managing game renderers (OpenGL ES & Vulkan)
 */
public class GameRenderer {
    private final static String TAG = "Renderer";
    private final static String FALLBACK_RENDERER = GL4ES_RENDERER;
    private RenderSpec currentRenderer;
    private Map<String, String> environment = new HashMap<>();

    public GameRenderer(String currentRenderer) {
        this.currentRenderer = getKnownRenderer(currentRenderer);
        if(this.currentRenderer == null) this.currentRenderer = getKnownRenderer(GL4ES_RENDERER);
        if(this.currentRenderer == null) throw new IllegalStateException("Failed to create the current renderer!");
    }

    /**
     * Map renderer string to a known RenderSpec
     */
    public static RenderSpec getKnownRenderer(String renderer) {
        switch (renderer) {
            case "opengles2_4":
            case "opengles2_5":
            case GL4ES_RENDERER: return new GLESRenderSpec.GL4ESRenderSpec();
            case LTW_RENDERER: return new GLESRenderSpec.LTWRenderSpec();
            case ZINK_RENDERER: return new MesaRenderSpec.ZinkRenderSpec();
            case PANVK_RENDERER: return new MesaRenderSpec.PanvkRenderSpec();
            case FREEDRENO_RENDERER: return new MesaRenderSpec.FreedrenoRenderSpec();
            case MESA_RENDERER: return new MesaRenderSpec();
            case MESA_RENDERER_EXT: return new MesaRenderSpec.ExtMesaRenderSpec();
            case LEGACYZINK_RENDERER: return new MesaRenderSpec.LegacyZinkRenderSpec();
            case MOBILEGLUES_RENDERER: return new GLESRenderSpec.MgRenderSpec();
            case SFPEW_RENDERER: return new GLESRenderSpec.SFPEWRenderSpec();
            default:
                Log.e(TAG, "Unknown renderer " + renderer);
                return null;
        }
    }

    public static void setRendererLibraryPath(String mainPath, String additionalPath) {
        if (additionalPath != null) mainPath = additionalPath + ":" + mainPath;
        MojoExec.setNativeLibraryDir(mainPath);
    }

    public void setupEnvironment(Context context) throws ErrnoException {
        if(environment == null) {
            Log.w(TAG, "Tried to call setupEnvironment in already initialized environment");
            return;
        }
        currentRenderer.setupEnvironment(context, environment);
        for(Map.Entry<String, String> e : environment.entrySet()) {
            Logger.appendToLog("Added renderer env: " + e.getKey() + '=' + e.getValue());
            Os.setenv(e.getKey(), e.getValue(), true);
        }
        environment.clear();
        environment = null;
    }

    public RenderSpec getCurrentRenderer() {
        return currentRenderer;
    }

    public void setCurrentRenderer(RenderSpec spec) {
        Log.i(TAG, "Replacing default renderer with the new: " + spec.name());
        currentRenderer = spec;
    }

    public void setCurrentRenderer(String renderer) throws IllegalArgumentException {
        RenderSpec spec = getKnownRenderer(renderer);
        if(spec == null) throw new IllegalArgumentException("Invalid renderer string" + renderer + "!");
        this.setCurrentRenderer(spec);
    }

    public boolean maybeSetupRenderer() {
        if(!currentRenderer.setupRenderer()) {
            Log.e(TAG, "Failed to setup renderer " + currentRenderer.name() + ", falling back to " + FALLBACK_RENDERER);
            currentRenderer = getKnownRenderer(FALLBACK_RENDERER);
            if(currentRenderer == null || !currentRenderer.setupRenderer()) {
                Log.e(TAG, "Failed to setup fallback renderer");
                return false;
            }
        }
        return true;
    }

    public void overrideVulkanDriver() {
        if(LauncherPreferences.PREF_FREEDRENO_SYSMEM) environment.put("TU_DEBUG", "sysmem");
        if(LauncherPreferences.PREF_UBWC_WORKAROUND) environment.put("FD_DEV_FEATURES", "enable_tp_ubwc_flag_hint=1");
        if (currentRenderer != null && PANVK_RENDERER.equals(currentRenderer.tag())) {
            try {
                MojoExec.setUsePanvk(true);
                MojoExec.setUseTurnip(false);
            } catch (Throwable ignored) {}
        } else {
            MojoExec.setUseTurnip(true);
        }
    }
}
