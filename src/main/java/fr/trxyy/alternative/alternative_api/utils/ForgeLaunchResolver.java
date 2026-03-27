package fr.trxyy.alternative.alternative_api.utils;

import fr.trxyy.alternative.alternative_api.GameEngine;
import fr.trxyy.alternative.alternative_api.GameForge;
import fr.trxyy.alternative.alternative_api.GameStyle;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Resolve the correct Forge launch mode at runtime.
 *
 * Forge 1.19 / 1.20.x still launch through BootstrapLauncher with target forgeclient.
 * Newer Forge builds (like 1.21.11) use ForgeBootstrap with target forge_client.
 */
public final class ForgeLaunchResolver {

    public enum Mode {
        NON_FORGE,
        LEGACY_LAUNCHWRAPPER,
        DIRECT_MODLAUNCHER,
        FORGE_WRAPPER,
        LEGACY_BOOTSTRAP_LAUNCHER,
        MODERN_FORGE_BOOTSTRAP
    }

    private static final String MAIN_BOOTSTRAP_LAUNCHER = "cpw.mods.bootstraplauncher.BootstrapLauncher";
    private static final String MAIN_FORGE_BOOTSTRAP = "net.minecraftforge.bootstrap.ForgeBootstrap";
    private static final String MAIN_FORGE_WRAPPER = "io.github.zekerzhayard.forgewrapper.installer.Main";

    private ForgeLaunchResolver() {
    }

    public static Mode resolveMode(GameEngine engine) {
        if (engine == null || engine.getGameStyle() == null) {
            return Mode.NON_FORGE;
        }

        GameStyle style = engine.getGameStyle();
        if (!isForgeStyle(style)) {
            return Mode.NON_FORGE;
        }

        if (style.equals(GameStyle.FORGE_1_7_10_OLD) || style.equals(GameStyle.FORGE_1_8_TO_1_12_2)) {
            return Mode.LEGACY_LAUNCHWRAPPER;
        }

        if (style.equals(GameStyle.FORGE_1_13_HIGHER)) {
            if (shouldUseForgeWrapper(engine)) {
                return Mode.FORGE_WRAPPER;
            }
            return Mode.DIRECT_MODLAUNCHER;
        }

        String launchTarget = findLaunchTarget(engine.getGameForge());
        if ("forge_client".equalsIgnoreCase(launchTarget)) {
            return Mode.MODERN_FORGE_BOOTSTRAP;
        }
        if ("forgeclient".equalsIgnoreCase(launchTarget) || "fmlclient".equalsIgnoreCase(launchTarget)) {
            return Mode.LEGACY_BOOTSTRAP_LAUNCHER;
        }

        if (containsForgeBootstrapLibrary(engine)) {
            return Mode.MODERN_FORGE_BOOTSTRAP;
        }

        return Mode.LEGACY_BOOTSTRAP_LAUNCHER;
    }

    public static String resolveMainClass(GameEngine engine) {
        Mode mode = resolveMode(engine);
        if (mode == Mode.MODERN_FORGE_BOOTSTRAP) {
            return MAIN_FORGE_BOOTSTRAP;
        }
        if (mode == Mode.FORGE_WRAPPER) {
            return MAIN_FORGE_WRAPPER;
        }
        if (mode == Mode.LEGACY_BOOTSTRAP_LAUNCHER) {
            return MAIN_BOOTSTRAP_LAUNCHER;
        }
        if (mode == Mode.LEGACY_LAUNCHWRAPPER) {
            return engine != null && engine.getGameStyle() != null
                    ? engine.getGameStyle().getMainClass()
                    : "net.minecraft.launchwrapper.Launch";
        }
        if (mode == Mode.DIRECT_MODLAUNCHER) {
            return engine != null && engine.getGameStyle() != null
                    ? engine.getGameStyle().getMainClass()
                    : "cpw.mods.modlauncher.Launcher";
        }
        return engine != null && engine.getGameStyle() != null
                ? engine.getGameStyle().getMainClass()
                : "net.minecraft.client.main.Main";
    }

    private static boolean isForgeStyle(GameStyle style) {
        return style.equals(GameStyle.FORGE_1_7_10_OLD)
                || style.equals(GameStyle.FORGE_1_8_TO_1_12_2)
                || style.equals(GameStyle.FORGE_1_13_HIGHER)
                || style.equals(GameStyle.FORGE_1_17_HIGHER)
                || style.equals(GameStyle.FORGE_1_19_HIGHER)
                || style.equals(GameStyle.NEOFORGE);
    }

    private static String findLaunchTarget(GameForge gameForge) {
        if (gameForge == null || gameForge.getArguments() == null || gameForge.getArguments().getGame() == null) {
            return "";
        }

        List<String> args = gameForge.getArguments().getGame();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("--launchTarget".equals(arg) && i + 1 < args.size()) {
                return safeLower(args.get(i + 1));
            }
        }

        for (String arg : args) {
            String lower = safeLower(arg);
            if (lower.contains("forge_client")) {
                return "forge_client";
            }
            if (lower.contains("forgeclient")) {
                return "forgeclient";
            }
            if (lower.contains("fmlclient")) {
                return "fmlclient";
            }
        }

        return "";
    }

    private static boolean containsForgeBootstrapLibrary(GameEngine engine) {
        if (engine == null || engine.getGameUpdater() == null) {
            return false;
        }

        Collection<String> jars = engine.getGameUpdater().getJars();
        if (jars == null) {
            return false;
        }

        for (String jar : jars) {
            String lower = safeLower(jar).replace('\\', '/');
            if (lower.contains("/net/minecraftforge/bootstrap/") || lower.contains("forgebootstrap")) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldUseForgeWrapper(GameEngine engine) {
        if (engine == null || engine.getGameUpdater() == null) {
            return false;
        }
        if (engine.getGameUpdater().isForgeWrapperFallbackRequired()) {
            return true;
        }
        String fullVersion = resolveForgeFullVersion(engine);
        if (fullVersion == null || fullVersion.trim().isEmpty() || engine.getGameFolder() == null) {
            return false;
        }

        java.io.File libDir = new java.io.File(engine.getGameFolder().getLibsDir(), "net/minecraftforge/forge/" + fullVersion);
        java.io.File clientJar = new java.io.File(libDir, "forge-" + fullVersion + "-client.jar");
        java.io.File launcherJar = new java.io.File(libDir, "forge-" + fullVersion + "-launcher.jar");
        return !clientJar.exists() && launcherJar.exists();
    }

    private static String resolveForgeFullVersion(GameEngine engine) {
        GameForge gameForge = engine == null ? null : engine.getGameForge();
        if (gameForge != null && gameForge.getArguments() != null && gameForge.getArguments().getGame() != null) {
            String mcVersion = null;
            String forgeVersion = null;
            List<String> args = gameForge.getArguments().getGame();
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if ("--fml.mcVersion".equals(arg) && i + 1 < args.size()) {
                    mcVersion = args.get(i + 1);
                } else if ("--fml.forgeVersion".equals(arg) && i + 1 < args.size()) {
                    forgeVersion = args.get(i + 1);
                }
            }
            if (mcVersion != null && forgeVersion != null) {
                return mcVersion + "-" + forgeVersion;
            }
        }
        return engine != null && engine.getGameUpdater() != null ? engine.getGameUpdater().getForgeFullVersion() : null;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
