package fr.trxyy.alternative.alternative_api.utils.file;

import fr.trxyy.alternative.alternative_api.*;
import fr.trxyy.alternative.alternative_api.utils.*;

import java.io.*;
import java.util.*;

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

    /**
     * @param folder The folder to list
     * @return A ArrayList that contains all files listed
     */
    public static ArrayList<File> list(File folder) {
        ArrayList<File> files = new ArrayList<File>();
        if (!folder.isDirectory())
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
