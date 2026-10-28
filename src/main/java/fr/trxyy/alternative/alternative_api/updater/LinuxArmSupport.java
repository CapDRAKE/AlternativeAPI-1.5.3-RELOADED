package fr.trxyy.alternative.alternative_api.updater;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.trxyy.alternative.alternative_api.GameVerifier;
import fr.trxyy.alternative.alternative_api.minecraft.json.MinecraftLibrary;
import fr.trxyy.alternative.alternative_api.minecraft.json.MinecraftVersion;
import fr.trxyy.alternative.alternative_api.utils.Logger;
import fr.trxyy.alternative.alternative_api.utils.OperatingSystem;
import fr.trxyy.alternative.alternative_api.utils.file.JsonUtil;

/**
 * Linux sur processeur ARM 64 bits (Chromebooks ARM, Raspberry Pi, PC ARM).
 *
 * Mojang ne fournit pour Linux ni natives ARM ni Java ARM. Mais Minecraft 1.19+
 * utilise LWJGL 3.3, qui publie lui-même ses natives linux-arm64 sur Maven Central :
 * on remplace les natives x86_64 du JSON de version par celles-ci, et le Java du
 * jeu est pris chez Azul (Zulu, même source que le Java du launcher). Les versions
 * plus anciennes (LWJGL 2 / 3.1 / 3.2) n'existent pas en ARM : elles sont refusées
 * avec un message clair.
 */
public final class LinuxArmSupport {

    private static final String MAVEN = "https://repo1.maven.org/maven2/";
    private static final Pattern LWJGL_LINUX_NATIVE = Pattern.compile("^org\\.lwjgl:([^:]+):([^:]+):natives-linux$");
    private static final String MARKER = "majesty-runtime.txt";

    private LinuxArmSupport() {
    }

    /** Linux sur processeur ARM 64 bits ? */
    public static boolean isLinuxArm() {
        return OperatingSystem.getCurrentPlatform() == OperatingSystem.LINUX && OperatingSystem.isHostArm64();
    }

    // ------------------------------------------------------------------ natives

    /**
     * Remplace les natives LWJGL linux (x86_64) par les natives linux-arm64 de Maven
     * Central. Renvoie false si la version utilise un LWJGL sans natives ARM
     * (Minecraft 1.18 et avant).
     *
     * @param fetchSha1 vérifier les fichiers via les empreintes publiées par Maven (en ligne)
     */
    public static boolean substituteNatives(MinecraftVersion version, boolean fetchSha1) {
        if (version == null || version.getLibraries() == null) {
            return true;
        }
        List<MinecraftLibrary> libraries = version.getLibraries();
        int replaced = 0;
        for (int i = 0; i < libraries.size(); i++) {
            MinecraftLibrary library = libraries.get(i);
            if (library == null || library.getName() == null) {
                continue;
            }
            String name = library.getName();
            if (isOldStyleLwjglNative(library)) {
                Logger.err("Linux ARM : " + name + " n'existe pas pour ce processeur (Minecraft 1.19+ requis).");
                return false;
            }
            Matcher m = LWJGL_LINUX_NATIVE.matcher(name);
            if (!m.matches()) {
                continue;
            }
            String artifact = m.group(1);
            String lwjglVersion = m.group(2);
            String path = "org/lwjgl/" + artifact + "/" + lwjglVersion + "/" + artifact + "-" + lwjglVersion + "-natives-linux-arm64.jar";
            JsonObject download = new JsonObject();
            download.addProperty("path", path);
            download.addProperty("url", MAVEN + path);
            String sha1 = fetchSha1 ? readText(MAVEN + path + ".sha1") : null;
            if (sha1 != null) {
                sha1 = sha1.trim().split("\\s+")[0];
                if (sha1.matches("[0-9a-fA-F]{40}")) {
                    download.addProperty("sha1", sha1.toLowerCase());
                }
            }
            JsonObject downloads = new JsonObject();
            downloads.add("artifact", download);
            JsonObject json = new JsonObject();
            json.addProperty("name", "org.lwjgl:" + artifact + ":" + lwjglVersion + ":natives-linux-arm64");
            json.add("downloads", downloads);

            MinecraftLibrary arm = JsonUtil.getGson().fromJson(json, MinecraftLibrary.class);
            arm.rules = library.rules;
            libraries.set(i, arm);
            replaced++;
        }
        if (replaced > 0) {
            Logger.log("Linux ARM : " + replaced + " natives LWJGL remplacees par leurs versions arm64.");
        }
        return true;
    }

    /** Natives LWJGL déclarées à l'ancienne (classifiers) : LWJGL 2 / 3.1 / 3.2, sans version ARM. */
    private static boolean isOldStyleLwjglNative(MinecraftLibrary library) {
        String name = library.getName();
        boolean lwjgl = name.startsWith("org.lwjgl:") || name.startsWith("org.lwjgl.lwjgl:");
        return lwjgl && library.getDownloads() != null && library.getDownloads().getClassifiers() != null
                && library.getDownloads().getClassifiers().keySet().stream().anyMatch(k -> k != null && k.startsWith("natives-linux"));
    }

    // ------------------------------------------------------------------ Java

    /** Version de Java pour un composant Mojang (alpha = Java 16 : Java 17 convient). */
    static int javaMajorFor(String component) {
        if (component == null) return 21;
        switch (component) {
            case "jre-legacy":
                return 8;
            case "java-runtime-alpha":
            case "java-runtime-beta":
            case "java-runtime-gamma":
            case "java-runtime-gamma-snapshot":
                return 17;
            case "java-runtime-delta":
                return 21;
            case "java-runtime-epsilon":
                return 25;
            default:
                return 21;
        }
    }

    /**
     * Installe (si besoin) un Java Zulu linux-aarch64 dans le dossier du composant.
     * L'archive est vérifiée (SHA-256 publié par Azul) puis extraite avec tar.
     */
    public static boolean installRuntime(File runtimeDir, String component) {
        int major = javaMajorFor(component);
        File marker = new File(runtimeDir, MARKER);
        try {
            if (OperatingSystem.findRuntimeJavaBinary(runtimeDir) != null && marker.isFile()
                    && new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim().equals("zulu-" + major)) {
                registerFiles(runtimeDir);
                return true;
            }

            String api = "https://api.azul.com/metadata/v1/zulu/packages/?java_version=" + major
                    + "&os=linux&arch=aarch64&archive_type=tar.gz&java_package_type=jre&javafx_bundled=false"
                    + "&release_status=ga&availability_types=CA&latest=true&page=1&page_size=1";
            JsonArray packages = new JsonParser().parse(readText(api)).getAsJsonArray();
            if (packages.size() == 0) {
                Logger.err("Linux ARM : aucun Java " + major + " disponible chez Azul.");
                return false;
            }
            JsonObject pkg = packages.get(0).getAsJsonObject();
            String url = pkg.get("download_url").getAsString();
            String sha256 = null;
            try {
                JsonObject details = new JsonParser().parse(readText("https://api.azul.com/metadata/v1/zulu/packages/"
                        + pkg.get("package_uuid").getAsString())).getAsJsonObject();
                if (details.has("sha256_hash")) sha256 = details.get("sha256_hash").getAsString();
            } catch (Exception e) {
                Logger.log("Linux ARM : empreinte Azul indisponible (" + e.getMessage() + ")");
            }

            Logger.log("Linux ARM : telechargement de Java " + major + " (" + url + ")");
            deleteRecursively(runtimeDir);
            runtimeDir.mkdirs();
            File archive = new File(runtimeDir.getParentFile(), runtimeDir.getName() + ".tar.gz");
            download(url, archive);
            if (sha256 != null && !sha256.equalsIgnoreCase(sha256Of(archive))) {
                archive.delete();
                Logger.err("Linux ARM : archive Java corrompue (empreinte SHA-256 differente).");
                return false;
            }

            Process tar = new ProcessBuilder("tar", "-xzf", archive.getAbsolutePath(), "-C", runtimeDir.getAbsolutePath())
                    .redirectErrorStream(true).start();
            drain(tar.getInputStream());
            int code = tar.waitFor();
            archive.delete();
            if (OperatingSystem.findRuntimeJavaBinary(runtimeDir) == null) {
                Logger.err("Linux ARM : extraction de Java impossible (tar, code " + code + ").");
                return false;
            }
            if (code != 0) {
                // Ex. : liens des fichiers de licence non créés ; le Java lui-même est en place.
                Logger.log("Linux ARM : tar a signale des erreurs (code " + code + "), Java present malgre tout.");
            }
            Files.write(marker.toPath(), ("zulu-" + major).getBytes(StandardCharsets.UTF_8));
            registerFiles(runtimeDir);
            Logger.log("Linux ARM : Java " + major + " pret.");
            return true;
        } catch (Exception e) {
            Logger.err("Linux ARM : installation de Java " + major + " impossible : " + e.getMessage());
            return false;
        }
    }

    /** Déclare les fichiers du runtime au vérificateur, qui supprime tout fichier inconnu de bin/. */
    private static void registerFiles(File runtimeDir) throws IOException {
        try (Stream<Path> paths = Files.walk(runtimeDir.toPath())) {
            paths.forEach(p -> GameVerifier.addToFileList(p.toAbsolutePath().toString()));
        }
    }

    // ------------------------------------------------------------------ outils

    private static String readText(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "MajestyLauncher");
            if (c.getResponseCode() != 200) return null;
            try (Scanner s = new Scanner(c.getInputStream(), "UTF-8").useDelimiter("\\A")) {
                return s.hasNext() ? s.next() : "";
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static void download(String url, File target) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "MajestyLauncher");
        try (InputStream in = new BufferedInputStream(c.getInputStream()); OutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static String sha256Of(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void drain(InputStream in) throws IOException {
        byte[] buf = new byte[4096];
        while (in.read(buf) > 0) {
            // sortie de tar ignorée (évite le blocage du processus)
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null && !Files.isSymbolicLink(f.toPath())) {
            for (File c : children) deleteRecursively(c);
        }
        f.delete();
    }
}
