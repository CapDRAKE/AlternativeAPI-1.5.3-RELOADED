package fr.trxyy.alternative.alternative_api.utils.file;

import fr.trxyy.alternative.alternative_api.*;
import fr.trxyy.alternative.alternative_api.utils.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Trxyy
 */
public class GameUtils {

    /**
     * @param workDir The working directory
     * @return The working directory fro each OS
     */
    public static File getWorkingDirectory(String workDir) {
        String userHome = System.getProperty("user.home", ".");
        File workingDirectory;
        switch (getPlatform()) {
            case 1:
                workingDirectory = new File(userHome + "/." + workDir);
                break;
            case 2:
                workingDirectory = new File(userHome + "/." + workDir);
                break;
            case 3:
                workingDirectory = new File(userHome + "\\AppData\\Roaming\\." + workDir);
                break;
            case 4:
                workingDirectory = new File(userHome + "/Library/Application Support/" + workDir);
                break;
            default:
                workingDirectory = new File(userHome + "/." + workDir);
        }
        return workingDirectory;
    }

    /**
     * @return The current Platform
     */
    private static int getPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("linux"))
            return 1;
        if (osName.contains("unix"))
            return 1;
        if (osName.contains("solaris"))
            return 2;
        if (osName.contains("sunos"))
            return 2;
        if (osName.contains("win"))
            return 3;
        if (osName.contains("mac"))
            return 4;
        return 5;
    }

    /**
     * @param engine The GameEngine instance
     * @return A String that contains the classpath
     */
    public static String constructClasspath(GameEngine engine) {
        LinkedHashSet<String> classpathEntries = new LinkedHashSet<String>();
        boolean modernForge = isModernForge(engine);

        for (String lib : engine.getGameUpdater().getJars()) {
            if (lib == null || !(lib.endsWith(".jar") || lib.endsWith(".zip"))) {
                continue;
            }

            File file = new File(lib);
            String fileName = file.getName().toLowerCase(Locale.ROOT);

            if (isOptiFine(engine) && isManagedOptiFineLibrary(file)) {
                Logger.log("Skipping generic OptiFine library from base classpath: " + normalizeFile(file));
                continue;
            }

            if (modernForge && shouldSkipFromModernForgeClasspath(fileName)) {
                Logger.log("Skipping duplicated Forge entry from classpath: " + lib);
                continue;
            }

            if (modernForge) {
                file = remapAsmFileIfNeeded(file);
            }

            String normalized = normalizeFile(file);
            if (!classpathEntries.contains(normalized)) {
                Logger.log("Adding " + normalized);
                classpathEntries.add(normalized);
            }
        }

        if (isOptiFine(engine)) {
            addOptiFineClasspathEntries(classpathEntries, engine);
        }

        File clientJar = engine.getGameUpdater().getClientJarFile();
        if (clientJar != null) {
            classpathEntries.add(normalizeFile(clientJar));
        }

        return String.join(File.pathSeparator, classpathEntries);
    }

    private static boolean isModernForge(GameEngine engine) {
        if (engine == null || engine.getGameStyle() == null) {
            return false;
        }

        GameStyle style = engine.getGameStyle();
        return style.equals(GameStyle.FORGE_1_13_HIGHER)
                || style.equals(GameStyle.FORGE_1_17_HIGHER)
                || style.equals(GameStyle.FORGE_1_19_HIGHER);
    }

    /**
     * Pour Forge moderne, on ne filtre plus le jar forge-...-universal.jar.
     * Le log montre que sans lui FML échoue avec "Failed to find system mod: forge".
     * On se contente donc d'éviter les doublons exacts via le LinkedHashSet.
     */
    private static boolean shouldSkipFromModernForgeClasspath(String fileName) {
        return false;
    }

    private static File remapAsmFileIfNeeded(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.startsWith("asm")) {
            return file;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return file;
        }

        String remappedName = "ow2-" + file.getName();
        File remapped = new File(parent, remappedName);

        if (!remapped.exists() || remapped.length() != file.length()) {
            copyFile(file, remapped);
        }

        return remapped.exists() ? remapped : file;
    }

    private static void copyFile(File source, File target) {
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
        } catch (IOException e) {
            Logger.log("Failed to create remapped ASM jar for BootstrapLauncher: " + source.getAbsolutePath());
        } finally {
            try { if (input != null) input.close(); } catch (IOException ignored) {}
            try { if (output != null) output.close(); } catch (IOException ignored) {}
        }
    }

    private static String normalizeFile(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return file.getAbsolutePath();
        }
    }

    private static boolean isOptiFine(GameEngine engine) {
        return engine != null && engine.getGameStyle() != null && engine.getGameStyle().equals(GameStyle.OPTIFINE);
    }

    private static boolean isManagedOptiFineLibrary(File file) {
        if (file == null) {
            return false;
        }
        String normalized = normalizeFile(file).replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/libraries/optifine/launchwrapper-of/")
                || normalized.contains("/libraries/optifine/optifine/");
    }

    private static void removeManagedOptiFineLibraries(LinkedHashSet<String> classpathEntries) {
        if (classpathEntries == null || classpathEntries.isEmpty()) {
            return;
        }
        classpathEntries.removeIf(path -> {
            if (path == null || path.trim().isEmpty()) {
                return false;
            }
            String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);
            return normalized.contains("/libraries/optifine/launchwrapper-of/")
                    || normalized.contains("/libraries/optifine/optifine/");
        });
    }

    private static String extractLaunchwrapperOfVersion(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("launchwrapper-of-") || !lower.endsWith(".jar")) {
            return null;
        }
        return fileName.substring("launchwrapper-of-".length(), fileName.length() - ".jar".length());
    }

    private static int compareLooseVersions(String left, String right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }

        String[] leftParts = left.split("[._-]");
        String[] rightParts = right.split("[._-]");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int l = i < leftParts.length ? safeParseVersionPart(leftParts[i]) : 0;
            int r = i < rightParts.length ? safeParseVersionPart(rightParts[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return left.compareToIgnoreCase(right);
    }

    private static int safeParseVersionPart(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static void addOptiFineClasspathEntries(LinkedHashSet<String> classpathEntries, GameEngine engine) {
        if (engine == null || engine.getGameFolder() == null) {
            return;
        }

        removeManagedOptiFineLibraries(classpathEntries);

        File libsDir = engine.getGameFolder().getLibsDir();
        String requestedVersion = null;
        if (engine.getMinecraftVersion() != null && engine.getMinecraftVersion().getId() != null) {
            requestedVersion = engine.getMinecraftVersion().getId();
        } else if (engine.getGameUpdater() != null && engine.getGameUpdater().getLocalVersion() != null
                && engine.getGameUpdater().getLocalVersion().getId() != null) {
            requestedVersion = engine.getGameUpdater().getLocalVersion().getId();
        }

        File clientJar = engine.getGameUpdater() != null ? engine.getGameUpdater().getClientJarFile() : null;
        File optifineRoot = new File(libsDir, "optifine" + File.separator + "OptiFine");
        File optifineJar = findBestOptiFineJar(optifineRoot, requestedVersion);

        File launchwrapperRoot = new File(libsDir, "optifine" + File.separator + "launchwrapper-of");
        optifineJar = prepareOptiFineJarForLaunch(optifineJar, clientJar, launchwrapperRoot);
        File launchwrapper = findBestLaunchwrapperOfJar(launchwrapperRoot, libsDir, requestedVersion, optifineJar);
        addIfExists(classpathEntries, launchwrapper);
        addIfExists(classpathEntries, optifineJar);
    }

    private static File findBestLaunchwrapperOfJar(File launchwrapperRoot, File libsDir, String requestedVersion, File optifineJar) {
        if (launchwrapperRoot == null) {
            return null;
        }

        String preferredWrapperVersion = resolvePreferredLaunchwrapperOfVersion(requestedVersion);
        File preferred = new File(launchwrapperRoot, preferredWrapperVersion + File.separator + "launchwrapper-of-" + preferredWrapperVersion + ".jar");
        if (preferred.isFile()) {
            Logger.log("Using exact OptiFine launchwrapper " + preferred.getName() + " for Minecraft " + requestedVersion);
            return preferred;
        }

        File reconstructed = recoverExactLaunchwrapper(launchwrapperRoot, libsDir, optifineJar, preferredWrapperVersion);
        if (reconstructed != null && reconstructed.isFile()) {
            Logger.log("Recovered exact OptiFine launchwrapper " + reconstructed.getName() + " for Minecraft " + requestedVersion);
            return reconstructed;
        }

        if (shouldRefuseNewerLaunchwrapperFallback(requestedVersion)) {
            // Avant de refuser, verifier si la version embarquee dans l'OptiFine jar est deja extraite.
            // Ex: OptiFine G6 pour 1.15.2 embarque launchwrapper-of-2.2.jar au lieu de 2.1.
            File installerJar = resolveOptiFineInstallerJar(optifineJar);
            String embeddedVersion = readEmbeddedLaunchwrapperVersion(installerJar != null ? installerJar : optifineJar);
            if (embeddedVersion != null && !embeddedVersion.trim().isEmpty()) {
                File embeddedJar = new File(launchwrapperRoot, embeddedVersion + File.separator + "launchwrapper-of-" + embeddedVersion + ".jar");
                if (embeddedJar.isFile()) {
                    Logger.log("Using OptiFine-embedded launchwrapper-of-" + embeddedVersion + ".jar for Minecraft "
                            + requestedVersion + " (preferred was " + preferredWrapperVersion + ")");
                    return embeddedJar;
                }
                // Tenter d'extraire la version embarquee depuis le jar OptiFine
                File recovered = recoverFromOptiFineJar(installerJar != null ? installerJar : optifineJar, launchwrapperRoot, embeddedVersion);
                if (recovered != null && recovered.isFile()) {
                    Logger.log("Recovered OptiFine-embedded launchwrapper-of-" + embeddedVersion + ".jar for Minecraft "
                            + requestedVersion + " (preferred was " + preferredWrapperVersion + ")");
                    return recovered;
                }
            }
            Logger.log("Exact OptiFine launchwrapper-of-" + preferredWrapperVersion + ".jar is required for Minecraft "
                    + requestedVersion + ". Refusing generic/newer fallback because it causes binary mismatches.");
            return null;
        }

        List<File> allJars = list(launchwrapperRoot);
        File bestCompatibleFallback = null;
        String bestCompatibleVersion = null;
        File newestFallback = null;
        long newestFallbackTime = Long.MIN_VALUE;

        for (File file : allJars) {
            if (file == null || !file.isFile()) {
                continue;
            }
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (!name.startsWith("launchwrapper-of-") || !name.endsWith(".jar")) {
                continue;
            }

            String version = extractLaunchwrapperOfVersion(file.getName());
            if (version != null) {
                int cmp = compareLooseVersions(version, preferredWrapperVersion);
                if (cmp <= 0) {
                    if (bestCompatibleFallback == null || compareLooseVersions(version, bestCompatibleVersion) > 0) {
                        bestCompatibleFallback = file;
                        bestCompatibleVersion = version;
                    }
                }
            }

            long lastModified = file.lastModified();
            if (newestFallback == null || lastModified > newestFallbackTime) {
                newestFallback = file;
                newestFallbackTime = lastModified;
            }
        }

        if (bestCompatibleFallback != null) {
            Logger.log("Using compatible OptiFine launchwrapper fallback " + bestCompatibleFallback.getName()
                    + " for Minecraft " + requestedVersion + " (preferred " + preferredWrapperVersion + ")");
            return bestCompatibleFallback;
        }

        File syntheticExact = synthesizeExactLaunchwrapperFromGeneric(libsDir, launchwrapperRoot, preferredWrapperVersion);
        if (syntheticExact != null) {
            Logger.log("Using synthesized OptiFine launchwrapper fallback " + syntheticExact.getName()
                    + " for Minecraft " + requestedVersion + " (generic LaunchWrapper 1.12 source)");
            return syntheticExact;
        }

        File genericLaunchWrapper = findGenericLaunchWrapperJar(libsDir);
        if (genericLaunchWrapper != null) {
            Logger.log("Using generic LaunchWrapper fallback " + genericLaunchWrapper.getName()
                    + " because exact OptiFine launchwrapper-of-" + preferredWrapperVersion + ".jar is unavailable.");
            return genericLaunchWrapper;
        }


        if (newestFallback != null) {
            Logger.log("Using latest OptiFine launchwrapper fallback " + newestFallback.getName()
                    + " for Minecraft " + requestedVersion + " (preferred " + preferredWrapperVersion + ")");
            return newestFallback;
        }

        Logger.log("No OptiFine launchwrapper-of found for Minecraft " + requestedVersion
                + ", preferred wrapper=" + preferredWrapperVersion);
        return null;
    }

    private static File recoverExactLaunchwrapper(File launchwrapperRoot, File libsDir, File optifineJar, String preferredWrapperVersion) {
        File installerJar = resolveOptiFineInstallerJar(optifineJar);
        File recovered = recoverFromOptiFineJar(installerJar, launchwrapperRoot, preferredWrapperVersion);
        if (recovered != null && recovered.isFile()) {
            return recovered;
        }

        recovered = recoverFromExplodedOptiFineDirectory(installerJar != null ? installerJar : optifineJar, launchwrapperRoot, preferredWrapperVersion);
        if (recovered != null && recovered.isFile()) {
            return recovered;
        }

        recovered = recoverFromLibrariesScan(libsDir, launchwrapperRoot, preferredWrapperVersion);
        if (recovered != null && recovered.isFile()) {
            return recovered;
        }

        return null;
    }


    private static File prepareOptiFineJarForLaunch(File optifineJar, File clientJar, File launchwrapperRoot) {
        if (optifineJar == null) {
            return null;
        }

        File installerJar = resolveOptiFineInstallerJar(optifineJar);
        if (installerJar == null || !installerJar.isFile()) {
            return optifineJar;
        }

        String wrapperVersion = readEmbeddedLaunchwrapperVersion(installerJar);
        if (wrapperVersion != null && launchwrapperRoot != null) {
            File wrapper = recoverFromOptiFineJar(installerJar, launchwrapperRoot, wrapperVersion);
            if (wrapper != null && wrapper.isFile()) {
                Logger.log("Prepared exact OptiFine launchwrapper from installer: " + normalizeFile(wrapper));
            }
        }

        if (clientJar == null || !clientJar.isFile()) {
            Logger.log("Cannot patch OptiFine jar because base Minecraft jar is missing: " + clientJar);
            return optifineJar;
        }

        if (!isOptiFineInstallerJar(installerJar)) {
            return optifineJar;
        }

        File patchedTarget = getPatchedOptiFineTarget(installerJar);
        if (patchedTarget == null) {
            return optifineJar;
        }

        if (patchedTarget.isFile() && !isOptiFineInstallerJar(patchedTarget)) {
            return patchedTarget;
        }

        File tempPatched = new File(patchedTarget.getParentFile(), patchedTarget.getName() + ".patched.tmp");
        if (tempPatched.exists()) {
            tempPatched.delete();
        }

        if (!runOptiFinePatcher(installerJar, clientJar, tempPatched)) {
            Logger.log("Failed to patch OptiFine installer " + normalizeFile(installerJar) + " against " + normalizeFile(clientJar));
            if (tempPatched.exists()) {
                tempPatched.delete();
            }
            return optifineJar;
        }

        if (!replaceFile(tempPatched, patchedTarget)) {
            Logger.log("Failed to replace OptiFine target jar with patched output: " + normalizeFile(patchedTarget));
            if (tempPatched.exists()) {
                tempPatched.delete();
            }
            return optifineJar;
        }

        Logger.log("Patched OptiFine jar for launch: " + normalizeFile(patchedTarget));
        return patchedTarget;
    }

    private static File resolveOptiFineInstallerJar(File optifineJar) {
        if (optifineJar == null) {
            return null;
        }

        if (isOptiFineInstallerJar(optifineJar)) {
            File installerCopy = getInstallerCopyTarget(optifineJar);
            if (installerCopy == null) {
                return optifineJar;
            }

            if (!installerCopy.isFile()) {
                File parent = installerCopy.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    Logger.log("Failed to create OptiFine installer directory: " + parent.getAbsolutePath());
                    return optifineJar;
                }
                copyFile(optifineJar, installerCopy);
                if (installerCopy.isFile()) {
                    Logger.log("Preserved OptiFine installer jar: " + normalizeFile(installerCopy));
                }
            }

            return installerCopy.isFile() ? installerCopy : optifineJar;
        }

        File installerCopy = getInstallerCopyTarget(optifineJar);
        if (installerCopy != null && installerCopy.isFile()) {
            return installerCopy;
        }

        return null;
    }

    private static File getInstallerCopyTarget(File optifineJar) {
        if (optifineJar == null) {
            return null;
        }
        String name = optifineJar.getName();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return null;
        }
        if (name.toLowerCase(Locale.ROOT).endsWith("-installer.jar")) {
            return optifineJar;
        }
        String installerName = name.substring(0, name.length() - 4) + "-installer.jar";
        File parent = optifineJar.getParentFile();
        return parent == null ? null : new File(parent, installerName);
    }

    private static File getPatchedOptiFineTarget(File installerJar) {
        if (installerJar == null) {
            return null;
        }
        String name = installerJar.getName();
        String lower = name.toLowerCase(Locale.ROOT);
        File parent = installerJar.getParentFile();
        if (parent == null) {
            return installerJar;
        }
        if (lower.endsWith("-installer.jar")) {
            String patchedName = name.substring(0, name.length() - "-installer.jar".length()) + ".jar";
            return new File(parent, patchedName);
        }
        return installerJar;
    }

    private static boolean isOptiFineInstallerJar(File jarFile) {
        if (jarFile == null || !jarFile.isFile()) {
            return false;
        }

        ZipFile zip = null;
        try {
            zip = new ZipFile(jarFile);
            if (zip.getEntry("launchwrapper-of.txt") != null || zip.getEntry("optifine/Patcher.class") != null) {
                return true;
            }

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry == null || entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name == null) {
                    continue;
                }
                String simpleName = new File(name).getName().toLowerCase(Locale.ROOT);
                if (simpleName.startsWith("launchwrapper-of-") && simpleName.endsWith(".jar")) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            return false;
        } finally {
            if (zip != null) {
                try { zip.close(); } catch (IOException ignored) {}
            }
        }
        return false;
    }

    private static String readEmbeddedLaunchwrapperVersion(File installerJar) {
        if (installerJar == null || !installerJar.isFile()) {
            return null;
        }

        ZipFile zip = null;
        try {
            zip = new ZipFile(installerJar);
            ZipEntry entry = zip.getEntry("launchwrapper-of.txt");
            if (entry == null) {
                return null;
            }

            InputStream input = null;
            ByteArrayOutputStream output = null;
            try {
                input = new BufferedInputStream(zip.getInputStream(entry));
                output = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
                String version = new String(output.toByteArray(), "UTF-8").trim();
                return version.isEmpty() ? null : version;
            } finally {
                try { if (input != null) input.close(); } catch (IOException ignored) {}
                try { if (output != null) output.close(); } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {
            return null;
        } finally {
            if (zip != null) {
                try { zip.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static boolean runOptiFinePatcher(File installerJar, File clientJar, File outputJar) {
        if (installerJar == null || clientJar == null || outputJar == null) {
            return false;
        }

        File javaBin = resolveJavaBinary();
        List<String> command = new ArrayList<String>();
        command.add(javaBin.getAbsolutePath());
        command.add("-cp");
        command.add(installerJar.getAbsolutePath());
        command.add("optifine.Patcher");
        command.add(clientJar.getAbsolutePath());
        command.add(installerJar.getAbsolutePath());
        command.add(outputJar.getAbsolutePath());

        Process process = null;
        BufferedReader reader = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();

            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Logger.log("[OptiFinePatcher] " + line);
            }

            int exitCode = process.waitFor();
            return exitCode == 0 && outputJar.isFile() && outputJar.length() > 0L;
        } catch (Exception e) {
            Logger.log("Failed to execute OptiFine patcher: " + e.getMessage());
            return false;
        } finally {
            try { if (reader != null) reader.close(); } catch (IOException ignored) {}
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static File resolveJavaBinary() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.trim().isEmpty()) {
            File bin = new File(javaHome, "bin");
            File javaExe = new File(bin, isWindows() ? "java.exe" : "java");
            if (javaExe.isFile()) {
                return javaExe;
            }
        }
        return new File(isWindows() ? "java.exe" : "java");
    }

    private static boolean replaceFile(File source, File target) {
        if (source == null || target == null || !source.isFile()) {
            return false;
        }

        if (target.exists() && !target.delete()) {
            Logger.log("Failed to delete previous OptiFine target file: " + target.getAbsolutePath());
            return false;
        }

        if (source.renameTo(target)) {
            return true;
        }

        copyFile(source, target);
        boolean ok = target.isFile() && target.length() > 0L;
        if (ok) {
            source.delete();
        }
        return ok;
    }

    private static boolean isWindows() {
        return getPlatform() == 3;
    }

    private static boolean shouldRefuseNewerLaunchwrapperFallback(String requestedVersion) {
        if (requestedVersion == null) {
            return false;
        }
        return requestedVersion.matches("1\\.(14|15)(\\.\\d+)?");
    }

    private static File recoverFromOptiFineJar(File optifineJar, File launchwrapperRoot, String preferredWrapperVersion) {
        if (optifineJar == null || !optifineJar.isFile()) {
            return null;
        }

        File extracted = extractEmbeddedLaunchwrapperFile(optifineJar, launchwrapperRoot, preferredWrapperVersion);
        if (extracted != null && extracted.isFile()) {
            return extracted;
        }

        return repackEmbeddedLaunchwrapperDirectory(optifineJar, launchwrapperRoot, preferredWrapperVersion);
    }

    private static File recoverFromExplodedOptiFineDirectory(File optifineJar, File launchwrapperRoot, String preferredWrapperVersion) {
        if (optifineJar == null) {
            return null;
        }

        File parent = optifineJar.getParentFile();
        if (parent == null || !parent.isDirectory()) {
            return null;
        }

        File exactJar = new File(parent, "launchwrapper-of-" + preferredWrapperVersion + ".jar");
        if (exactJar.isFile()) {
            return copyRecoveredLaunchwrapper(exactJar, launchwrapperRoot, preferredWrapperVersion);
        }

        File explodedDir = new File(parent, "launchwrapper-of-" + preferredWrapperVersion + ".jar");
        if (explodedDir.isDirectory()) {
            return packDirectoryAsJar(explodedDir, launchwrapperRoot, preferredWrapperVersion, "Recovered exploded launchwrapper directory next to OptiFine jar");
        }

        return null;
    }

    private static File recoverFromLibrariesScan(File libsDir, File launchwrapperRoot, String preferredWrapperVersion) {
        if (libsDir == null || !libsDir.isDirectory()) {
            return null;
        }

        String expectedName = "launchwrapper-of-" + preferredWrapperVersion + ".jar";
        List<File> files = list(libsDir);
        for (File file : files) {
            if (file == null) {
                continue;
            }

            if (file.isFile() && file.getName().equalsIgnoreCase(expectedName)) {
                return copyRecoveredLaunchwrapper(file, launchwrapperRoot, preferredWrapperVersion);
            }

            if (file.isDirectory() && file.getName().equalsIgnoreCase(expectedName)) {
                return packDirectoryAsJar(file, launchwrapperRoot, preferredWrapperVersion, "Recovered exploded launchwrapper directory from libraries scan");
            }
        }

        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }
            String lower = file.getName().toLowerCase(Locale.ROOT);
            if (!(lower.endsWith(".jar") || lower.endsWith(".zip"))) {
                continue;
            }

            File extracted = extractEmbeddedLaunchwrapperFile(file, launchwrapperRoot, preferredWrapperVersion);
            if (extracted != null && extracted.isFile()) {
                Logger.log("Recovered exact OptiFine launchwrapper from archive scan: " + normalizeFile(file));
                return extracted;
            }

            File repacked = repackEmbeddedLaunchwrapperDirectory(file, launchwrapperRoot, preferredWrapperVersion);
            if (repacked != null && repacked.isFile()) {
                Logger.log("Recovered exact OptiFine launchwrapper from exploded archive entry scan: " + normalizeFile(file));
                return repacked;
            }
        }

        return null;
    }

    private static File extractEmbeddedLaunchwrapperFile(File archive, File launchwrapperRoot, String preferredWrapperVersion) {
        if (archive == null || !archive.isFile() || launchwrapperRoot == null || preferredWrapperVersion == null) {
            return null;
        }

        File targetDir = new File(launchwrapperRoot, preferredWrapperVersion);
        File targetFile = new File(targetDir, "launchwrapper-of-" + preferredWrapperVersion + ".jar");
        if (targetFile.isFile()) {
            return targetFile;
        }

        String expectedName = "launchwrapper-of-" + preferredWrapperVersion + ".jar";
        ZipFile zip = null;
        try {
            zip = new ZipFile(archive);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry == null || entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                if (entryName == null) {
                    continue;
                }
                String simpleName = new File(entryName).getName();
                if (!expectedName.equalsIgnoreCase(simpleName)) {
                    continue;
                }

                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    Logger.log("Failed to create OptiFine launchwrapper target directory: " + targetDir.getAbsolutePath());
                    return null;
                }

                InputStream input = null;
                OutputStream output = null;
                try {
                    input = new BufferedInputStream(zip.getInputStream(entry));
                    output = new BufferedOutputStream(new FileOutputStream(targetFile));
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = input.read(buffer)) != -1) {
                        output.write(buffer, 0, len);
                    }
                } finally {
                    try { if (input != null) input.close(); } catch (IOException ignored) {}
                    try { if (output != null) output.close(); } catch (IOException ignored) {}
                }

                if (targetFile.isFile() && targetFile.length() > 0L) {
                    Logger.log("Extracted embedded OptiFine launchwrapper from " + archive.getName()
                            + " to " + targetFile.getAbsolutePath());
                    return targetFile;
                }
                break;
            }
        } catch (IOException e) {
            Logger.log("Failed to extract embedded OptiFine launchwrapper from " + archive.getAbsolutePath()
                    + ": " + e.getMessage());
        } finally {
            if (zip != null) {
                try { zip.close(); } catch (IOException ignored) {}
            }
        }
        return null;
    }

    private static File repackEmbeddedLaunchwrapperDirectory(File archive, File launchwrapperRoot, String preferredWrapperVersion) {
        if (archive == null || !archive.isFile() || launchwrapperRoot == null || preferredWrapperVersion == null) {
            return null;
        }

        String prefix = "launchwrapper-of-" + preferredWrapperVersion + ".jar/";
        File targetDir = new File(launchwrapperRoot, preferredWrapperVersion);
        File targetFile = new File(targetDir, "launchwrapper-of-" + preferredWrapperVersion + ".jar");
        if (targetFile.isFile()) {
            return targetFile;
        }

        ZipFile zip = null;
        JarOutputStream jarOut = null;
        boolean foundAnyEntry = false;
        try {
            zip = new ZipFile(archive);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry == null) {
                    continue;
                }
                String entryName = entry.getName();
                if (entryName == null || !entryName.startsWith(prefix)) {
                    continue;
                }

                if (!foundAnyEntry) {
                    if (!targetDir.exists() && !targetDir.mkdirs()) {
                        Logger.log("Failed to create OptiFine launchwrapper target directory: " + targetDir.getAbsolutePath());
                        return null;
                    }
                    jarOut = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(targetFile)));
                    foundAnyEntry = true;
                }

                String relativeName = entryName.substring(prefix.length());
                if (relativeName.length() == 0) {
                    continue;
                }

                JarEntry jarEntry = new JarEntry(relativeName);
                jarEntry.setTime(entry.getTime());
                jarOut.putNextEntry(jarEntry);
                if (!entry.isDirectory()) {
                    InputStream input = null;
                    try {
                        input = new BufferedInputStream(zip.getInputStream(entry));
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = input.read(buffer)) != -1) {
                            jarOut.write(buffer, 0, len);
                        }
                    } finally {
                        try { if (input != null) input.close(); } catch (IOException ignored) {}
                    }
                }
                jarOut.closeEntry();
            }
        } catch (IOException e) {
            Logger.log("Failed to repack embedded OptiFine launchwrapper directory from " + archive.getAbsolutePath()
                    + ": " + e.getMessage());
        } finally {
            if (jarOut != null) {
                try { jarOut.close(); } catch (IOException ignored) {}
            }
            if (zip != null) {
                try { zip.close(); } catch (IOException ignored) {}
            }
        }

        if (foundAnyEntry && targetFile.isFile() && targetFile.length() > 0L) {
            Logger.log("Repacked embedded OptiFine launchwrapper directory from " + archive.getName()
                    + " to " + targetFile.getAbsolutePath());
            return targetFile;
        }

        if (targetFile.exists() && targetFile.length() == 0L) {
            targetFile.delete();
        }
        return null;
    }

    private static File copyRecoveredLaunchwrapper(File source, File launchwrapperRoot, String preferredWrapperVersion) {
        if (source == null || !source.isFile()) {
            return null;
        }
        File targetDir = new File(launchwrapperRoot, preferredWrapperVersion);
        File targetFile = new File(targetDir, "launchwrapper-of-" + preferredWrapperVersion + ".jar");
        if (targetFile.isFile()) {
            return targetFile;
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Logger.log("Failed to create OptiFine launchwrapper target directory: " + targetDir.getAbsolutePath());
            return null;
        }
        copyFile(source, targetFile);
        if (targetFile.isFile() && targetFile.length() > 0L) {
            Logger.log("Copied recovered OptiFine launchwrapper from " + normalizeFile(source)
                    + " to " + targetFile.getAbsolutePath());
            return targetFile;
        }
        return null;
    }

    private static File packDirectoryAsJar(File sourceDir, File launchwrapperRoot, String preferredWrapperVersion, String logPrefix) {
        if (sourceDir == null || !sourceDir.isDirectory()) {
            return null;
        }

        File targetDir = new File(launchwrapperRoot, preferredWrapperVersion);
        File targetFile = new File(targetDir, "launchwrapper-of-" + preferredWrapperVersion + ".jar");
        if (targetFile.isFile()) {
            return targetFile;
        }
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Logger.log("Failed to create OptiFine launchwrapper target directory: " + targetDir.getAbsolutePath());
            return null;
        }

        JarOutputStream jarOut = null;
        try {
            jarOut = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(targetFile)));
            addDirectoryToJar(sourceDir, sourceDir, jarOut);
        } catch (IOException e) {
            Logger.log(logPrefix + ": " + e.getMessage());
        } finally {
            if (jarOut != null) {
                try { jarOut.close(); } catch (IOException ignored) {}
            }
        }

        if (targetFile.isFile() && targetFile.length() > 0L) {
            Logger.log(logPrefix + ": " + targetFile.getAbsolutePath());
            return targetFile;
        }
        return null;
    }

    private static void addDirectoryToJar(File root, File current, JarOutputStream jarOut) throws IOException {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            String relative = root.toURI().relativize(child.toURI()).getPath();
            if (child.isDirectory()) {
                if (!relative.isEmpty()) {
                    if (!relative.endsWith("/")) {
                        relative = relative + "/";
                    }
                    JarEntry entry = new JarEntry(relative);
                    entry.setTime(child.lastModified());
                    jarOut.putNextEntry(entry);
                    jarOut.closeEntry();
                }
                addDirectoryToJar(root, child, jarOut);
            } else {
                JarEntry entry = new JarEntry(relative);
                entry.setTime(child.lastModified());
                jarOut.putNextEntry(entry);
                FileInputStream input = null;
                try {
                    input = new FileInputStream(child);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = input.read(buffer)) != -1) {
                        jarOut.write(buffer, 0, len);
                    }
                } finally {
                    try { if (input != null) input.close(); } catch (IOException ignored) {}
                }
                jarOut.closeEntry();
            }
        }
    }

    private static File synthesizeExactLaunchwrapperFromGeneric(File libsDir, File launchwrapperRoot, String preferredWrapperVersion) {
        if (libsDir == null || launchwrapperRoot == null || preferredWrapperVersion == null || preferredWrapperVersion.trim().isEmpty()) {
            return null;
        }

        File targetDir = new File(launchwrapperRoot, preferredWrapperVersion);
        File targetFile = new File(targetDir, "launchwrapper-of-" + preferredWrapperVersion + ".jar");
        if (targetFile.isFile() && jarContainsEntry(targetFile, "net/minecraft/launchwrapper/Launch.class")) {
            return targetFile;
        }

        File generic = ensureGenericLaunchWrapperJar(libsDir);
        if (generic == null || !generic.isFile()) {
            return null;
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Logger.log("Failed to create OptiFine launchwrapper target directory: " + targetDir.getAbsolutePath());
            return null;
        }

        copyFile(generic, targetFile);
        if (targetFile.isFile() && jarContainsEntry(targetFile, "net/minecraft/launchwrapper/Launch.class")) {
            Logger.log("Synthesized launchwrapper-of-" + preferredWrapperVersion + ".jar from generic LaunchWrapper: " + targetFile.getAbsolutePath());
            return targetFile;
        }

        return null;
    }

    private static File ensureGenericLaunchWrapperJar(File libsDir) {
        File existing = findGenericLaunchWrapperJar(libsDir);
        if (existing != null) {
            return existing;
        }

        if (libsDir == null) {
            return null;
        }

        File targetDir = new File(libsDir, "net" + File.separator + "minecraft" + File.separator + "launchwrapper" + File.separator + "1.12");
        File targetFile = new File(targetDir, "launchwrapper-1.12.jar");
        if (targetFile.isFile() && jarContainsEntry(targetFile, "net/minecraft/launchwrapper/Launch.class")) {
            return targetFile;
        }

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Logger.log("Failed to create generic LaunchWrapper directory: " + targetDir.getAbsolutePath());
            return null;
        }

        if (!downloadToFile("https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar", targetFile)) {
            if (targetFile.exists() && targetFile.length() == 0L) {
                targetFile.delete();
            }
            return null;
        }

        if (jarContainsEntry(targetFile, "net/minecraft/launchwrapper/Launch.class")) {
            Logger.log("Downloaded generic LaunchWrapper fallback: " + targetFile.getAbsolutePath());
            return targetFile;
        }

        Logger.log("Downloaded generic LaunchWrapper fallback is invalid: " + targetFile.getAbsolutePath());
        if (targetFile.exists()) {
            targetFile.delete();
        }
        return null;
    }

    private static boolean downloadToFile(String url, File targetFile) {
        if (url == null || url.trim().isEmpty() || targetFile == null) {
            return false;
        }

        HttpURLConnection connection = null;
        InputStream input = null;
        OutputStream output = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "MajestyLauncher/OptiFineFix");
            connection.connect();

            int response = connection.getResponseCode();
            if (response < 200 || response >= 300) {
                Logger.log("Failed to download generic LaunchWrapper fallback from " + url + " (HTTP " + response + ")");
                return false;
            }

            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Logger.log("Failed to create download target directory: " + parent.getAbsolutePath());
                return false;
            }

            input = new BufferedInputStream(connection.getInputStream());
            output = new BufferedOutputStream(new FileOutputStream(targetFile));
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            output.flush();
            return targetFile.isFile() && targetFile.length() > 0L;
        } catch (IOException e) {
            Logger.log("Failed to download generic LaunchWrapper fallback from " + url + ": " + e.getMessage());
            return false;
        } finally {
            try { if (input != null) input.close(); } catch (IOException ignored) {}
            try { if (output != null) output.close(); } catch (IOException ignored) {}
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static File findGenericLaunchWrapperJar(File libsDir) {
        if (libsDir == null || !libsDir.isDirectory()) {
            return null;
        }

        List<File> files = list(libsDir);
        File best = null;
        for (File file : files) {
            if (file == null || !file.isFile()) {
                continue;
            }

            String normalized = normalizeFile(file).replace('\\', '/').toLowerCase(Locale.ROOT);
            if (normalized.contains("/optifine/launchwrapper-of/")) {
                continue;
            }
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                continue;
            }
            if (!jarContainsEntry(file, "net/minecraft/launchwrapper/Launch.class")) {
                continue;
            }

            if (best == null) {
                best = file;
                continue;
            }

            String bestName = best.getName().toLowerCase(Locale.ROOT);
            String currentName = file.getName().toLowerCase(Locale.ROOT);
            boolean currentLooksStandard = currentName.startsWith("launchwrapper-") || normalized.contains("/net/minecraft/launchwrapper/");
            boolean bestLooksStandard = bestName.startsWith("launchwrapper-") || normalizeFile(best).replace('\\', '/').toLowerCase(Locale.ROOT).contains("/net/minecraft/launchwrapper/");
            if (currentLooksStandard && !bestLooksStandard) {
                best = file;
            }
        }
        return best;
    }

    private static boolean jarContainsEntry(File file, String entryName) {
        ZipFile zip = null;
        try {
            zip = new ZipFile(file);
            return zip.getEntry(entryName) != null;
        } catch (IOException ignored) {
            return false;
        } finally {
            if (zip != null) {
                try { zip.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static String resolvePreferredLaunchwrapperOfVersion(String requestedVersion) {
        if (requestedVersion != null) {
            if (requestedVersion.matches("1\\.(14|15)(\\.\\d+)?")) {
                return "2.1";
            }
            if (requestedVersion.matches("1\\.16(\\.\\d+)?")) {
                return "2.2";
            }
        }
        return "2.3";
    }

    private static void addIfExists(LinkedHashSet<String> classpathEntries, File file) {
        if (file == null || !file.isFile()) {
            return;
        }
        String normalized = normalizeFile(file);
        if (!classpathEntries.contains(normalized)) {
            Logger.log("Adding " + normalized);
            classpathEntries.add(normalized);
        }
    }

    private static File findBestOptiFineJar(File root, String requestedVersion) {
        if (root == null || !root.isDirectory()) {
            return null;
        }

        List<File> allJars = list(root);
        File bestMatch = null;
        long bestMatchTime = Long.MIN_VALUE;
        File fallback = null;
        long fallbackTime = Long.MIN_VALUE;

        String versionPrefix = requestedVersion == null || requestedVersion.trim().isEmpty()
                ? null
                : ("optifine-" + requestedVersion.toLowerCase(Locale.ROOT) + "_");

        for (File file : allJars) {
            if (file == null || !file.isFile()) {
                continue;
            }
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (!name.startsWith("optifine-") || !name.endsWith(".jar")) {
                continue;
            }
            if (name.endsWith("-installer.jar")) {
                continue;
            }

            long lastModified = file.lastModified();
            if (versionPrefix != null && name.startsWith(versionPrefix)) {
                if (bestMatch == null || lastModified > bestMatchTime) {
                    bestMatch = file;
                    bestMatchTime = lastModified;
                }
            }

            if (fallback == null || lastModified > fallbackTime) {
                fallback = file;
                fallbackTime = lastModified;
            }
        }

        return bestMatch != null ? bestMatch : fallback;
    }

    /**
     * @param folder The folder to list
     * @return A ArrayList that contains all files listed
     */
    public static ArrayList<File> list(File folder) {
        ArrayList<File> files = new ArrayList<File>();
        if (folder == null || !folder.isDirectory())
            return files;

        File[] folderFiles = folder.listFiles();
        if (folderFiles != null)
            for (File f : folderFiles)
                if (f.isDirectory())
                    files.addAll(list(f));
                else
                    files.add(f);

        return files;
    }

}