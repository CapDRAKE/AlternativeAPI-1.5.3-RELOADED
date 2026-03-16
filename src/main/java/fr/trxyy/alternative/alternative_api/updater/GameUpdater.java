package fr.trxyy.alternative.alternative_api.updater;

import fr.trxyy.alternative.alternative_api.*;
import fr.trxyy.alternative.alternative_api.assets.*;
import fr.trxyy.alternative.alternative_api.build.*;
import fr.trxyy.alternative.alternative_api.minecraft.java.*;
import fr.trxyy.alternative.alternative_api.minecraft.json.*;
import fr.trxyy.alternative.alternative_api.minecraft.utils.*;
import fr.trxyy.alternative.alternative_api.utils.*;
import fr.trxyy.alternative.alternative_api.utils.file.*;
import fr.trxyy.alternative.alternative_auth.account.*;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

import com.google.gson.*;

/**
 * @author Trxyy
 */
public class GameUpdater extends Thread {

    /**
     * The custom files to download in a HashMap
     */
    public HashMap<String, LauncherFile> files = new HashMap<String, LauncherFile>();
    /**
     * The Assets Url
     */
    private static final String ASSETS_URL = "https://resources.download.minecraft.net/";
    private static final String FORGE_PROMOTIONS_URL = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String FORGE_MAVEN_BASE = "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    private static final String MOJANG_VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String BOOTSTRAPLAUNCHER_VERSION = "1.1.2";
    private static final String[] FORGE_WRAPPER_URLS = new String[] {
            "https://github.com/ZekerZhayard/ForgeWrapper/releases/latest/download/ForgeWrapper-mmc2.jar",
            "https://github.com/ZekerZhayard/ForgeWrapper/releases/download/1.6.0/ForgeWrapper-1.6.0.jar"
    };
    
    /**
     * The Host to check for offline
     */
    private String HOST = "https://www.google.com";
    /**
     * The Minecraft Version from Json
     */
    public static MinecraftVersion minecraftVersion;
    /**
     * The Minecraft JVM manifest
     */
    public JVMManifest jvmManifest;
    /**
     * The Minecraft Java Manifest
     */
    public JavaManifest javaManifest;
    /**
     * The Java style
     */
    public String javaStyle;
    /**
     * The Minecraft Local Version from Json
     */
    public MinecraftVersion minecraftLocalVersion;
    /**
     * Custom files has a custom jar ?
     */
    public boolean hasCustomJar = false;
    /**
     * The AssetIndex
     */
    public AssetIndex assetsList;
    /**
     * The GameEngine instance
     */
    public GameEngine engine;
    /**
     * The Session of the user
     */
    private Session session;
    /**
     * The GameVerifier instance
     */
    private GameVerifier verifier;
    /**
     * The assets Executor
     */
    private ExecutorService assetsExecutor = Executors.newFixedThreadPool(5);
    /**
     * The custom files Executor
     */
    private ExecutorService customJarsExecutor = Executors.newFixedThreadPool(5);
    /**
     * The libraries Executor
     */
    private ExecutorService jarsExecutor = Executors.newFixedThreadPool(5);
    /**
     * The java Executor
     */
    private ExecutorService javaExecutor = Executors.newFixedThreadPool(5);
    /**
     * The log4j Executor
     */
    private ExecutorService log4jExecutor = Executors.newFixedThreadPool(5);
    /**
     * The current Info text of the progressbar
     */
    private String currentInfoText = "";
    /**
     * The current file of the progressbar
     */
    private String currentFile = "";
    /**
     * The downloaded custom files
     */
    public int downloadedFiles = 0;
    /**
     * The custom files to download
     */
    public int filesToDownload = 0;
    private boolean isOnline = true;

        public final Map<String, String> jars = Collections.synchronizedMap(new LinkedHashMap<String, String>());
    private final List<String> failedDownloads = Collections.synchronizedList(new ArrayList<String>());
    private volatile boolean hasDownloadError = false;
    private volatile File clientJarFile = null;
    private volatile File forgeInstallerJar = null;
    private volatile String forgeFullVersion = null;
    private volatile boolean javaPrepared = false;
    private volatile boolean forgeInstalledByInstaller = false;
    private volatile boolean forgeWrapperFallbackRequired = false;
    private volatile File forgeWrapperJar = null;
    private volatile File forgeLauncherJar = null;

    /**
     * Register some things...
     *
     * @param gameEngine The GameEngine instance
     */
    public void reg(GameEngine gameEngine) {
        this.engine = gameEngine;
    }

    /**
     * Register some things
     *
     * @param account The Session of the user
     */
    public void reg(Session account) {
        this.session = account;
    }


    public void registerDownloadFailure(File file, String url, Exception e) {
        this.hasDownloadError = true;
        this.failedDownloads.add(file.getAbsolutePath() + " <- " + url + " (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
    }

    public boolean hasDownloadError() {
        return this.hasDownloadError;
    }

    public List<String> getFailedDownloads() {
        return new ArrayList<String>(this.failedDownloads);
    }

    public File getClientJarFile() {
        if (this.clientJarFile != null) {
            return this.clientJarFile;
        }
        return this.engine.getGameFolder().getGameJar();
    }

    private String resolveVersionId() {
        if (minecraftVersion != null && minecraftVersion.getId() != null && !minecraftVersion.getId().trim().isEmpty()) {
            return minecraftVersion.getId();
        }
        if (this.engine != null && this.engine.getGameLinks() != null && this.engine.getGameLinks().getJsonName() != null) {
            String jsonName = this.engine.getGameLinks().getJsonName();
            if (jsonName.endsWith(".json")) {
                return jsonName.substring(0, jsonName.length() - 5);
            }
            return jsonName;
        }
        return "minecraft";
    }

    private File resolveClientJarTarget() {
        if (this.hasCustomJar) {
            return new File(this.engine.getGameFolder().getBinDir(), "minecraft.jar");
        }

        String versionId = resolveVersionId();
        File versionsDir = new File(this.engine.getGameFolder().getBinDir(), "versions");
        File versionDir = new File(versionsDir, versionId);
        versionDir.mkdirs();
        return new File(versionDir, versionId + ".jar");
    }

    private void resetState() {
        this.files.clear();
        this.downloadedFiles = 0;
        this.filesToDownload = 0;
        this.currentFile = "";
        this.currentInfoText = "";
        this.hasCustomJar = false;
        this.hasDownloadError = false;
        this.failedDownloads.clear();
        this.jars.clear();
        this.javaPrepared = false;
        this.forgeInstalledByInstaller = false;
        this.forgeWrapperFallbackRequired = false;
        this.forgeWrapperJar = null;
        this.forgeLauncherJar = null;
        GameVerifier.allowedFiles.clear();
        initExecutors();
    }

    private void initExecutors() {
        this.assetsExecutor = Executors.newFixedThreadPool(5);
        this.customJarsExecutor = Executors.newFixedThreadPool(5);
        this.jarsExecutor = Executors.newFixedThreadPool(5);
        this.javaExecutor = Executors.newFixedThreadPool(5);
        this.log4jExecutor = Executors.newFixedThreadPool(5);
    }


    /**
     * Run the update (Thread)
     */
    @Override
    public void run() {
        /** -------------------------------------- */
        this.resetState();
        this.HOST = engine.getGameLinks().getBaseUrl();
        this.isOnline = this.isOnline();
        this.engine.setOnline(this.isOnline);
        if (this.isOnline) {
            Logger.log("=============UPDATING GAME==============");

            this.setCurrentInfoText("Preparation de la mise a jour.");

            Logger.log("Updating Local Minecraft Version.");
            Logger.log("========================================");
            this.downloadVersion();

            this.verifier = new GameVerifier(this.engine);
            Logger.log("Getting ignore/delete list   [Step 1/6]");
            Logger.log("========================================");

            this.setCurrentInfoText("Recuperation de la ignoreList/deleteList.");

            this.verifier.getIgnoreList();
            this.verifier.getDeleteList();
            Logger.log("Indexing version              [Step 2/6]");
            Logger.log("========================================");

            this.setCurrentInfoText("Indexion de la version Minecraft.");

            if (this.requiresForgeMetadata()) {

                Logger.log("Indexing forge version        [2-bonus/6]");
                Logger.log("========================================");

                this.setCurrentInfoText("Indexion de la version Forge.");

                this.indexForge();

            }

            this.indexVersion();
            Logger.log("Indexing assets               [Step 3/6]");
            Logger.log("========================================");


            this.setCurrentInfoText("Acquisition des fichiers de ressources.");

            this.indexAssets();
            if (this.usesRemoteCustomFiles()) {
                Logger.log("Indexing custom jars         [3-bonus/6]");
                Logger.log("========================================");

                this.setCurrentInfoText("Acquisition des fichiers perso requis.");

                GameParser.getFilesToDownload(engine);
            }
            Logger.log("Updating assets               [Step 4/6]");
            Logger.log("========================================");

            this.setCurrentInfoText("Telechargement des ressources.");

            this.updateAssets();
            Logger.log("Updating jars/libraries       [Step 5/6]");
            Logger.log("========================================");

            this.setCurrentInfoText("Telechargement des librairies.");

            if (this.engine.getGameStyle().equals(GameStyle.VANILLA_1_19_HIGHER) || this.engine.getGameStyle().equals(GameStyle.FORGE_1_19_HIGHER)) {
                this.update1_19_HighersLibraries();
            } else {
                this.updateJars();
            }


            if (this.usesRemoteCustomFiles()) {
                Logger.log("Updating custom jars         [5-bonus/6]");
                Logger.log("========================================");
                this.setCurrentInfoText("Telechargement des ressources perso.");
                this.updateCustomJars();
            }

            if (this.isForge()) {
                Logger.log("Updating forge libraries     [5-bonus-bis/6]");
                Logger.log("========================================");
                this.setCurrentInfoText("Telechargement des librairies Forge.");
                if (this.isLegacyForgeStyle()) {
                    this.updateLegacyForgeArtifacts();
                } else {
                    this.updateForgeLibraries();
                }

            }
            this.customJarsExecutor.shutdown();
            try {
                this.customJarsExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Logger.log("Downloading required java version");
//			this.downloadJavaVersion(OperatingSystem.getJavaBit(), OperatingSystem.getCurrentPlatform());
            this.ensureJavaPrepared();

            Logger.log("Downloading log4j configuration file");
            this.updateLog4j();

            Logger.log("Cleaning installation         [Step 6/6]");
            Logger.log("========================================");

            this.setCurrentInfoText("Verification de l'installation.");

            this.verifier.verify();
            Logger.log("========================================");
            Logger.log("|      Update Finished. Launching.     |");
            Logger.log("|            Version " + minecraftVersion.getId() + "            |");
            Logger.log("|          Runtime: " + System.getProperty("java.version") + "          |");
            Logger.log("========================================");
            Logger.log("\n\n");
            Logger.log("==============GAME OUTPUT===============");

            this.setCurrentInfoText("Telechargement accompli.");

            if (this.hasDownloadError()) {
                Logger.err("Launch cancelled: some downloads failed.");
                for (String failedDownload : this.getFailedDownloads()) {
                    Logger.err(failedDownload);
                }
                this.setCurrentInfoText("Echec de telechargement de certaines librairies.");
                return;
            }

            GameRunner gameRunner = new GameRunner(this.engine, this.session);
            try {
                gameRunner.launch();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else { // OFFLINE
            Logger.log("\n\n");
            Logger.log("=========UPDATING GAME OFFLINE==========");

            this.setCurrentInfoText("Preparation de la mise a jour.");

            Logger.log("Indexing local version         [Step 1/1]");
            Logger.log("========================================");
            this.indexLocalVersion();

            if (this.requiresForgeMetadata()) {
                Logger.log("Indexing forge version        [2-bonus/6]");
                Logger.log("========================================");

                this.setCurrentInfoText("Indexion de la version Forge.");

                this.indexLocalForge();

            }

            if (this.usesOfflineCustomFiles()) {
                Logger.log("Indexing custom local jars   [Extra Step]");
                Logger.log("========================================");
                this.setCurrentInfoText("Acquisition des fichiers perso en local.");
                GameParser.getFilesToDownloadOffline(engine);
            }

            if (this.isForge()) {
                Logger.log("Updating forge libraries     [5-bonus-bis/6]");
                Logger.log("========================================");
                this.setCurrentInfoText("Verification des librairies Forge.");
                if (this.isLegacyForgeStyle()) {
                    this.updateLegacyForgeArtifacts();
                } else {
                    this.updateForgeLibraries();
                }
            }
            this.customJarsExecutor.shutdown();
            try {
                this.customJarsExecutor.awaitTermination(200000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Logger.log("========================================");
            Logger.log("|       Can't Update. Launching.       |");
            Logger.log("|            Version " + minecraftLocalVersion.getId() + "            |");
            Logger.log("|          Runtime: " + System.getProperty("java.version") + "          |");
            Logger.log("========================================");
            Logger.log("\n\n");
            Logger.log("==============GAME OUTPUT===============");

            this.setCurrentInfoText("Telechargement accompli.");

            if (this.hasDownloadError()) {
                Logger.err("Offline launch cancelled: some downloads failed.");
                for (String failedDownload : this.getFailedDownloads()) {
                    Logger.err(failedDownload);
                }
                this.setCurrentInfoText("Echec de telechargement de certaines librairies.");
                return;
            }

            GameRunner gameRunner = new GameRunner(this.engine, this.session);
            try {
                gameRunner.launch();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private void downloadJavaManifest() {
        this.downloadJavaManifestForComponent(resolvePreferredJavaComponent());
    }

    private boolean downloadJavaManifestForComponent(String targetComponent) {
        if (targetComponent == null || targetComponent.trim().isEmpty()) {
            return false;
        }

        String json = null;
        String manifestUrl = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
        try {
            json = JsonUtil.loadJSON(manifestUrl);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            this.javaManifest = (JavaManifest) JsonUtil.getGson().fromJson(json, JavaManifest.class);
            if (this.javaManifest == null) {
                return false;
            }
            Logger.log("CurrentRuntime: " + this.javaManifest.getCurrentOS());
            Map<String, List<JavaRuntime>> runtimes = this.javaManifest.getCurrentJava();
            if (runtimes == null) {
                return false;
            }
            for (String run : runtimes.keySet()) {
                if (run.equals(targetComponent)) {
                    ArrayList<JavaRuntime> selected = (ArrayList<JavaRuntime>) runtimes.get(run);
                    if (selected != null && !selected.isEmpty() && selected.get(0).getManifest() != null) {
                        this.indexJava(selected.get(0).getManifest().getUrl().toString());
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    }

    private void updateLog4j() {
        if (this.getEngine().getMinecraftVersion().getLogging() != null) {
            File log4jfolder = this.getEngine().getGameFolder().getLogConfigsDir();
            log4jfolder.mkdirs();
            File log4jfile = new File(log4jfolder, this.getEngine().getMinecraftVersion().getLogging().getClient().getFile().getId());

            Downloader downloadTask = new Downloader(log4jfile, this.getEngine().getMinecraftVersion().getLogging().getClient().getFile().getUrl().toString(), this.getEngine().getMinecraftVersion().getLogging().getClient().getFile().getSha1(), engine);
            GameVerifier.addToFileList(log4jfile.getAbsolutePath().replace(this.getEngine().getGameFolder().getLogConfigsDir().getAbsolutePath(), "").replace('/', File.separatorChar));
            if (downloadTask.requireUpdate()) {
                this.log4jExecutor.submit(downloadTask);
                this.filesToDownload++;
            }
        }

        this.log4jExecutor.shutdown();
        try {
            this.log4jExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Logger.log("Log4j Update finished.");
    }

    /**
     * Download Minecraft Json version at Every Launch to be up to date.
     */
    @SuppressWarnings("deprecation")
	public void downloadVersion() {
        File theFile = new File(engine.getGameFolder().getCacheDir(), engine.getGameLinks().getJsonName());
        GameVerifier.addToFileList(theFile.getAbsolutePath().replace(engine.getGameFolder().getCacheDir().getAbsolutePath(), "").replace('/', File.separatorChar));
        try {
            URL url = new URL(this.engine.getGameLinks().getJsonUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            float totalDataRead = 0;
            BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
            FileOutputStream fos = new FileOutputStream(theFile);
            BufferedOutputStream bout = new BufferedOutputStream(fos, 1024);
            byte[] data = new byte[1024];
            int i = 0;
            while ((i = in.read(data, 0, 1024)) >= 0) {
                totalDataRead = totalDataRead + i;
                bout.write(data, 0, i);
            }
            bout.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    /**
     * @return A generated Lot Number
     */
    public static String generateLot() {
        String lot = "";
        SimpleDateFormat year = new SimpleDateFormat("YY");
        SimpleDateFormat hour = new SimpleDateFormat("HHmmss");
        Date date = new Date();
        int julianDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);
        lot = "L" + year.format(date) + julianDay + "/" + hour.format(date);
        return lot;
    }

    /**
     * Construct the classpath
     *
     * @param engine The GameEngine instance
     * @return The result of the classpath
     */
    public static String constructClasspath(GameEngine engine) {
        Logger.log("Constructing classpath (new, only in version)");
        String result = "";
        String separator = System.getProperty("path.separator");
        for (MinecraftLibrary lib : minecraftVersion.getLibraries()) {
            File libPath = new File(engine.getGameFolder().getLibsDir(), lib.getArtifactPath());
            result += libPath + separator;
        }
        result += engine.getGameFolder().getGameJar().getAbsolutePath();
        return result;
    }

    /**
     * Update minecraft libraries
     */
    @SuppressWarnings({ "unlikely-arg-type", "unused" })
    public void updateJars() {
        for (MinecraftLibrary lib : minecraftVersion.getLibraries()) {
            File libPath = resolveLibraryTargetFile(lib);

            GameVerifier.addToFileList(
                    libPath.getAbsolutePath().replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "")
                            .replace('/', File.separatorChar));

            applyCompatibilityRules(lib);

            if (lib.isSkipped() || !matchesDeclaredNativeEnvironment(lib)) {
                continue;
            }

            if (!isNativeCoordinateLibrary(lib)) {
                jars.put(lib.getName(), libPath.getAbsolutePath());
            }

            if (lib.getDownloads() != null && lib.getDownloads().getArtifact() != null
                    && lib.getDownloads().getArtifact().getUrl() != null) {
                final Downloader downloadTask = new Downloader(libPath,
                        lib.getDownloads().getArtifact().getUrl().toString(),
                        lib.getDownloads().getArtifact().getSha1(), engine);
                if (downloadTask.requireUpdate()) {
                    if (!verifier.existInDeleteList(libPath.getAbsolutePath()
                            .replace(engine.getGameFolder().getGameDir().getAbsolutePath(), ""))) {
                        this.filesToDownload++;
                        this.jarsExecutor.submit(downloadTask);
                    }
                }
            }

            if (!isNativeCoordinateLibrary(lib)) {
                queueNativeDownload(lib);
            }
        }

        File clientJarTarget = resolveClientJarTarget();
        this.clientJarFile = clientJarTarget;

        final Downloader downloadTask3 = new Downloader(clientJarTarget,
                minecraftVersion.getDownloads().getClient().getUrl().toString(),
                minecraftVersion.getDownloads().getClient().getSha1(), engine);
        GameVerifier.addToFileList(clientJarTarget.getAbsolutePath()
                .replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "").replace('/', File.separatorChar));

        if (downloadTask3.requireUpdate()) {
            if (!this.hasCustomJar) {
                this.jarsExecutor.submit(downloadTask3);
                this.filesToDownload++;
            }
        }

        this.jarsExecutor.shutdown();

        try {
            this.jarsExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void applyCompatibilityRules(MinecraftLibrary lib) {
        if (lib.getCompatibilityRules() != null) {
            for (final CompatibilityRule rule : lib.getCompatibilityRules()) {
                if (rule.getOs() != null && rule.getAction() != null) {
                    for (final String os : rule.getOs().getName().getAliases()) {
                        if (lib.appliesToCurrentEnvironment()) {
                            if (rule.getAction().equals("disallow")) {
                                lib.setSkipped(true);
                            } else {
                                lib.setSkipped(false);
                            }
                        } else {
                            if (rule.getAction().equals("allow")) {
                                lib.setSkipped(false);
                            } else {
                                lib.setSkipped(true);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isNativeCoordinateLibrary(MinecraftLibrary lib) {
        return lib != null && lib.isDeclaredNativeLibrary();
    }

    private boolean matchesDeclaredNativeEnvironment(MinecraftLibrary lib) {
        return lib == null || lib.declaredNativeMatchesCurrentEnvironment();
    }

    private File resolveLibraryTargetFile(MinecraftLibrary lib) {
        if (isNativeCoordinateLibrary(lib)) {
            String nativeFileName = null;
            if (lib.getDownloads() != null && lib.getDownloads().getArtifact() != null
                    && lib.getDownloads().getArtifact().getPath() != null
                    && !lib.getDownloads().getArtifact().getPath().trim().isEmpty()) {
                nativeFileName = new File(lib.getDownloads().getArtifact().getPath()).getName();
            }
            if (nativeFileName == null || nativeFileName.trim().isEmpty()) {
                nativeFileName = lib.getArtifactFilename(null);
            }
            return new File(engine.getGameFolder().getNativesCacheDir(), nativeFileName);
        }

        if (lib.getDownloads() != null && lib.getDownloads().getArtifact() != null
                && lib.getDownloads().getArtifact().getPath() != null
                && !lib.getDownloads().getArtifact().getPath().trim().isEmpty()) {
            return new File(engine.getGameFolder().getLibsDir(), lib.getDownloads().getArtifact().getPath());
        }

        return new File(engine.getGameFolder().getLibsDir(), lib.getArtifactPath());
    }

    private void queueNativeDownload(MinecraftLibrary lib) {
        String classifierKey = resolveNativeClassifier(lib);
        if (classifierKey == null || lib.getDownloads() == null || lib.getDownloads().getClassifiers() == null) {
            return;
        }

        DownloadInfo nativeInfo = lib.getDownloads().getClassifiers().get(classifierKey);
        if (nativeInfo == null || nativeInfo.getUrl() == null) {
            return;
        }

        String nativeFileName = null;
        if (nativeInfo.getPath() != null && !nativeInfo.getPath().trim().isEmpty()) {
            nativeFileName = new File(nativeInfo.getPath()).getName();
        }
        if (nativeFileName == null || nativeFileName.trim().isEmpty()) {
            nativeFileName = lib.getArtifactNatives(classifierKey);
        }

        final File nativePath = new File(engine.getGameFolder().getNativesCacheDir(), nativeFileName);
        GameVerifier.addToFileList(nativePath.getAbsolutePath()
                .replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "")
                .replace('/', File.separatorChar));

        final Downloader downloadTask = new Downloader(nativePath,
                nativeInfo.getUrl().toString(),
                nativeInfo.getSha1(),
                engine);

        if (downloadTask.requireUpdate()) {
            if (!verifier.existInDeleteList(nativePath.getAbsolutePath()
                    .replace(engine.getGameFolder().getGameDir().getAbsolutePath(), ""))) {
                this.filesToDownload++;
                this.jarsExecutor.submit(downloadTask);
            }
        }
    }

    private String resolveNativeClassifier(MinecraftLibrary lib) {
        if (lib == null || !lib.hasNatives() || lib.getDownloads() == null || lib.getDownloads().getClassifiers() == null) {
            return null;
        }

        Map<String, DownloadInfo> classifiers = lib.getDownloads().getClassifiers();
        if (classifiers.isEmpty()) {
            return null;
        }

        String classifier = null;
        if (lib.getNatives() != null) {
            classifier = lib.getNatives().get(OperatingSystem.getCurrent());
        }

        if (classifier != null) {
            classifier = classifier.replace("${arch}", Arch.CURRENT.getBit());
            if (classifiers.containsKey(classifier)) {
                return classifier;
            }
        }

        List<String> candidates = new ArrayList<>();
        OperatingSystem currentOs = OperatingSystem.getCurrent();
        if (currentOs == OperatingSystem.WINDOWS) {
            if (Arch.CURRENT == Arch.x86) {
                candidates.add("natives-windows-x86");
                candidates.add("natives-windows");
            } else {
                candidates.add("natives-windows");
                candidates.add("natives-windows-x86");
            }
            candidates.add("natives-windows-arm64");
        } else if (currentOs == OperatingSystem.LINUX) {
            candidates.add("natives-linux");
        } else if (currentOs == OperatingSystem.OSX) {
            candidates.add("natives-macos");
            candidates.add("natives-osx");
            candidates.add("natives-osx-arm64");
        }

        for (String candidate : candidates) {
            if (classifiers.containsKey(candidate)) {
                return candidate;
            }
        }

        for (String key : classifiers.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (currentOs == OperatingSystem.WINDOWS && lower.contains("windows") && !lower.contains("arm64")) {
                return key;
            }
            if (currentOs == OperatingSystem.LINUX && lower.contains("linux")) {
                return key;
            }
            if (currentOs == OperatingSystem.OSX && (lower.contains("mac") || lower.contains("osx"))) {
                return key;
            }
        }

        return null;
    }


    @SuppressWarnings("unused")
    private void update1_19_HighersLibraries() {
        for (MinecraftLibrary lib : minecraftVersion.getLibraries()) {
            File libPath = resolveLibraryTargetFile(lib);
            GameVerifier.addToFileList(
                    libPath.getAbsolutePath().replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "")
                            .replace('/', File.separatorChar));

            applyCompatibilityRules(lib);

            if (lib.isSkipped() || !matchesDeclaredNativeEnvironment(lib)) {
                continue;
            }

            if (!isNativeCoordinateLibrary(lib)) {
                jars.put(lib.getName(), libPath.getAbsolutePath());
            }

            if (lib.getDownloads() != null && lib.getDownloads().getArtifact() != null
                    && lib.getDownloads().getArtifact().getUrl() != null) {
                final Downloader downloadTask = new Downloader(libPath,
                        lib.getDownloads().getArtifact().getUrl().toString(),
                        lib.getDownloads().getArtifact().getSha1(), engine);
                if (downloadTask.requireUpdate()) {
                    if (!verifier.existInDeleteList(libPath.getAbsolutePath()
                            .replace(engine.getGameFolder().getGameDir().getAbsolutePath(), ""))) {
                        this.filesToDownload++;
                        this.jarsExecutor.submit(downloadTask);
                    }
                }
            }

            if (!isNativeCoordinateLibrary(lib)) {
                queueNativeDownload(lib);
            }
        }
        File clientJarTarget = resolveClientJarTarget();
        this.clientJarFile = clientJarTarget;

        final Downloader downloadTask3 = new Downloader(clientJarTarget,
                minecraftVersion.getDownloads().getClient().getUrl().toString(),
                minecraftVersion.getDownloads().getClient().getSha1(), engine);
        GameVerifier.addToFileList(clientJarTarget.getAbsolutePath()
                .replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "").replace('/', File.separatorChar));

        if (downloadTask3.requireUpdate()) {
            if (!this.hasCustomJar) {
                this.jarsExecutor.submit(downloadTask3);
                this.filesToDownload++;
            }
        }
        this.jarsExecutor.shutdown();

        try {
            this.jarsExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void ensureJavaPrepared() {
        if (this.javaPrepared) {
            return;
        }
        this.downloadJavaManifest();
        this.updateJava();
    }

    private void ensureModernForgeInstalledOfficially() throws Exception {
        if (this.forgeInstalledByInstaller && this.hasInstalledForgeClientArtifact()) {
            this.reloadForgeProfileFromInstalledVersion();
            return;
        }

        this.ensureJavaPrepared();

        if (this.forgeInstallerJar == null || !this.forgeInstallerJar.exists()) {
            String mcVersion = resolveVersionId();
            String forgeVersion = resolveOfficialForgeVersion(mcVersion);
            if (forgeVersion == null || forgeVersion.trim().isEmpty()) {
                throw new FileNotFoundException("No Forge version found in promotions for Minecraft " + mcVersion);
            }
            this.forgeFullVersion = mcVersion + "-" + forgeVersion;

            String installerUrl = buildOfficialForgeInstallerUrl(mcVersion, forgeVersion);
            File installerJar = new File(engine.getGameFolder().getCacheDir(), "forge-" + this.forgeFullVersion + "-installer.jar");
            installerJar.getParentFile().mkdirs();
            downloadFile(installerUrl, installerJar);
            this.forgeInstallerJar = installerJar;
        }

        if (this.hasInstalledForgeClientArtifact()) {
            this.reloadForgeProfileFromInstalledVersion();
            this.forgeInstalledByInstaller = true;
            return;
        }

        String javaPath = resolveInstallerJavaBinary();
        File installRoot = this.engine.getGameFolder().getGameDir();
        prepareVanillaFilesForForgeInstaller(installRoot);
        cleanupForgeInstallerClientCache();
        ensureForgeInstallerLauncherProfiles(installRoot);

        List<List<String>> attempts = new ArrayList<List<String>>();
        attempts.add(Arrays.asList(javaPath, "-jar", this.forgeInstallerJar.getAbsolutePath(), "--installClient", installRoot.getAbsolutePath()));
        attempts.add(Arrays.asList(javaPath, "-jar", this.forgeInstallerJar.getAbsolutePath(), "--installClient"));

        StringBuilder combinedOutput = new StringBuilder();
        boolean success = false;
        Exception lastError = null;

        for (List<String> attempt : attempts) {
            try {
                int exit = runForgeInstallerCommand(attempt, installRoot, combinedOutput);
                if (exit == 0 && this.hasInstalledForgeClientArtifact()) {
                    success = true;
                    break;
                }
            } catch (Exception e) {
                lastError = e;
                combinedOutput.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n");
            }
        }

        File versionJson = findInstalledForgeVersionJson();
        if (!success && versionJson != null && versionJson.exists()) {
            if (isForgeWrapperFallbackCandidate()) {
                Logger.log("Forge client artifact missing after installer run, enabling ForgeWrapper fallback for " + this.forgeFullVersion);
                this.forgeWrapperFallbackRequired = true;
                success = true;
            }
        }

        if (!success) {
            String message = "Forge installer did not generate the expected client artifacts.";
            if (combinedOutput.length() > 0) {
                message += " Output: " + combinedOutput.toString().trim();
            }
            if (lastError != null) {
                throw new IOException(message, lastError);
            }
            throw new IOException(message);
        }

        this.reloadForgeProfileFromInstalledVersion();
        this.forgeInstalledByInstaller = !this.forgeWrapperFallbackRequired && this.hasInstalledForgeClientArtifact();
        if (this.forgeWrapperFallbackRequired) {
            this.ensureForgeWrapperArtifacts();
        }
    }

    private String resolveInstallerJavaBinary() {
        try {
            String preferredComponent = resolvePreferredJavaComponent();
            String preferredBinary = ensureInstallerJavaComponent(preferredComponent, true);
            if (preferredBinary != null && isAcceptableForgeInstallerJava(preferredBinary)) {
                Logger.log("Forge installer Java: " + preferredBinary);
                return preferredBinary;
            }

            if (preferredComponent != null && !preferredComponent.trim().isEmpty()) {
                Logger.log("Forge installer runtime " + preferredComponent + " is missing or outdated, trying a safer fallback runtime.");
            }

            String alphaBinary = ensureInstallerJavaComponent(EnumJavaVersion.JAVA_RUNTIME_ALPHA.getCode(), false);
            if (alphaBinary != null && isAcceptableForgeInstallerJava(alphaBinary)) {
                Logger.log("Forge installer Java fallback(alpha): " + alphaBinary);
                return alphaBinary;
            }
        } catch (Exception ignored) {
        }

        File sysJavaHome = new File(System.getProperty("java.home"));
        File[] candidates = new File[] {
                new File(sysJavaHome, "bin" + File.separator + "java.exe"),
                new File(sysJavaHome, "bin" + File.separator + "javaw.exe"),
                new File(sysJavaHome, "bin" + File.separator + "java")
        };
        for (File candidate : candidates) {
            if (candidate.exists() && isAcceptableForgeInstallerJava(candidate.getAbsolutePath())) {
                Logger.log("Forge installer Java fallback(system): " + candidate.getAbsolutePath());
                return candidate.getAbsolutePath();
            }
        }

        String osJava = OperatingSystem.getJavaPath().replace("", "");
        if (osJava != null && !osJava.trim().isEmpty()) {
            Logger.log("Forge installer Java final fallback: " + osJava);
            return osJava;
        }
        return osJava;
    }

    private void prepareVanillaFilesForForgeInstaller(File installRoot) throws IOException {
        if (installRoot == null) {
            throw new IOException("Forge install root is null");
        }

        String versionId = resolveVersionId();
        File targetVersionDir = new File(installRoot, "versions" + File.separator + versionId);
        if (!targetVersionDir.exists() && !targetVersionDir.mkdirs()) {
            throw new IOException("Unable to create Forge version directory: " + targetVersionDir.getAbsolutePath());
        }

        File sourceJson = resolveOfficialVanillaVersionJson(versionId);
        File sourceJar = resolveOfficialVanillaClientJar(versionId);
        if (!sourceJson.exists()) {
            throw new FileNotFoundException("Missing official vanilla json for Forge installer: " + sourceJson.getAbsolutePath());
        }
        if (!sourceJar.exists()) {
            throw new FileNotFoundException("Missing official vanilla client jar for Forge installer: " + sourceJar.getAbsolutePath());
        }

        copyFileIfNeeded(sourceJson, new File(targetVersionDir, versionId + ".json"));
        copyFileIfNeeded(sourceJar, new File(targetVersionDir, versionId + ".jar"));
    }

    private File resolveOfficialVanillaVersionJson(String versionId) throws IOException {
        File cacheDir = new File(engine.getGameFolder().getCacheDir(), "official-vanilla" + File.separator + versionId);
        cacheDir.mkdirs();
        File jsonFile = new File(cacheDir, versionId + ".json");
        if (jsonFile.exists() && jsonFile.length() > 0) {
            return jsonFile;
        }

        String manifest = JsonUtil.loadJSON(MOJANG_VERSION_MANIFEST_URL);
        JsonObject root = JsonParser.parseString(manifest).getAsJsonObject();
        String versionUrl = null;
        for (JsonElement el : root.getAsJsonArray("versions")) {
            JsonObject obj = el.getAsJsonObject();
            if (versionId.equals(obj.get("id").getAsString())) {
                versionUrl = obj.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            throw new FileNotFoundException("Official Mojang manifest entry not found for " + versionId);
        }

        String versionJson = JsonUtil.loadJSON(versionUrl);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(jsonFile), "UTF-8")) {
            writer.write(versionJson);
        }
        return jsonFile;
    }

    private File resolveOfficialVanillaClientJar(String versionId) throws IOException {
        File cacheDir = new File(engine.getGameFolder().getCacheDir(), "official-vanilla" + File.separator + versionId);
        cacheDir.mkdirs();
        File jarFile = new File(cacheDir, versionId + ".jar");
        File jsonFile = resolveOfficialVanillaVersionJson(versionId);
        String versionJson = readFile(jsonFile);
        JsonObject root = JsonParser.parseString(versionJson).getAsJsonObject();
        JsonObject client = root.getAsJsonObject("downloads").getAsJsonObject("client");
        String url = client.get("url").getAsString();
        String sha1 = client.get("sha1").getAsString();

        if (jarFile.exists() && FileUtil.matchSHA1(jarFile, sha1)) {
            return jarFile;
        }

        downloadFile(url, jarFile);
        if (!FileUtil.matchSHA1(jarFile, sha1)) {
            throw new IOException("Official vanilla client jar checksum mismatch for " + versionId);
        }
        return jarFile;
    }

    private void cleanupForgeInstallerClientCache() throws IOException {
        String versionId = resolveVersionId();
        if (versionId == null || versionId.trim().isEmpty()) {
            return;
        }

        File clientLibDir = new File(engine.getGameFolder().getLibsDir(), "net/minecraft/client/" + versionId);
        if (!clientLibDir.exists() && !clientLibDir.mkdirs()) {
            throw new IOException("Unable to create Forge client library directory: " + clientLibDir.getAbsolutePath());
        }

        deleteIfExists(new File(clientLibDir, "client-" + versionId + "-slim.jar"));
        deleteIfExists(new File(clientLibDir, "client-" + versionId + "-extra.jar"));

        File officialClientJar = resolveOfficialVanillaClientJar(versionId);
        File localClientJar = new File(clientLibDir, "client-" + versionId + ".jar");
        copyFileIfNeeded(officialClientJar, localClientJar);

        File localClientPom = new File(clientLibDir, "client-" + versionId + ".pom");
        if (localClientPom.exists() && localClientPom.length() == 0L) {
            deleteIfExists(localClientPom);
        }
    }

    private void copyFileIfNeeded(File source, File target) throws IOException {
        if (source == null || !source.exists()) {
            throw new FileNotFoundException("Missing source file: " + String.valueOf(source));
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create directory for " + target.getAbsolutePath());
        }
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void deleteIfExists(File file) throws IOException {
        if (file != null && file.exists() && !file.delete()) {
            throw new IOException("Unable to delete stale Forge processor file: " + file.getAbsolutePath());
        }
    }

    private void ensureForgeInstallerLauncherProfiles(File installRoot) throws IOException {
        if (installRoot == null) {
            return;
        }

        if (!installRoot.exists() && !installRoot.mkdirs()) {
            throw new IOException("Unable to create Forge install root: " + installRoot.getAbsolutePath());
        }

        JsonObject root = new JsonObject();

        JsonObject profiles = new JsonObject();
        JsonObject profile = new JsonObject();
        profile.addProperty("name", "MajestyLauncher");
        profile.addProperty("type", "custom");
        profile.addProperty("lastVersionId", "latest-release");
        profiles.add("MajestyLauncher", profile);

        root.add("profiles", profiles);
        root.addProperty("selectedProfile", "MajestyLauncher");
        root.addProperty("clientToken", "MajestyLauncher");
        root.add("authenticationDatabase", new JsonObject());
        root.add("settings", new JsonObject());
        root.addProperty("version", 3);

        String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);

        writeIfMissing(new File(installRoot, "launcher_profiles.json"), json);
        writeIfMissing(new File(installRoot, "launcher_profiles_microsoft_store.json"), json);
    }

    private void writeIfMissing(File target, String content) throws IOException {
        if (target.exists() && target.isFile() && target.length() > 0) {
            return;
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create directory for " + target.getAbsolutePath());
        }

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(target), "UTF-8")) {
            writer.write(content);
        }
    }

    private int runForgeInstallerCommand(List<String> command, File workingDir, StringBuilder outputCollector) throws Exception {
        Logger.log("Running official Forge installer: " + String.join(" ", command));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDir);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (outputCollector != null) {
                    outputCollector.append(line).append("\n");
                }
                Logger.log("[ForgeInstaller] " + line);
            }
        }

        return process.waitFor();
    }

    private boolean hasInstalledForgeClientArtifact() {
        if (this.forgeFullVersion == null || this.forgeFullVersion.trim().isEmpty()) {
            return false;
        }

        File libDir = new File(this.engine.getGameFolder().getLibsDir(), "net/minecraftforge/forge/" + this.forgeFullVersion);
        File clientJar = new File(libDir, "forge-" + this.forgeFullVersion + "-client.jar");
        File universalJar = new File(libDir, "forge-" + this.forgeFullVersion + "-universal.jar");
        File versionJson = findInstalledForgeVersionJson();

        if (this.engine != null && this.engine.getGameStyle() != null && this.engine.getGameStyle().equals(GameStyle.FORGE_1_13_HIGHER)) {
            return clientJar.exists() && versionJson != null && versionJson.exists();
        }
        return (clientJar.exists() || universalJar.exists()) && versionJson != null && versionJson.exists();
    }

    private File findInstalledForgeVersionJson() {
        File versionsRoot = new File(this.engine.getGameFolder().getGameDir(), "versions");
        if (!versionsRoot.exists()) {
            return null;
        }

        if (this.forgeFullVersion != null && !this.forgeFullVersion.trim().isEmpty()) {
            File direct1 = new File(versionsRoot, this.forgeFullVersion + File.separator + this.forgeFullVersion + ".json");
            if (direct1.exists()) {
                return direct1;
            }
            File direct2 = new File(versionsRoot, "forge-" + this.forgeFullVersion + File.separator + "forge-" + this.forgeFullVersion + ".json");
            if (direct2.exists()) {
                return direct2;
            }
            File direct3 = new File(versionsRoot, resolveVersionId() + "-forge-" + this.forgeFullVersion.substring(resolveVersionId().length() + 1) + File.separator
                    + resolveVersionId() + "-forge-" + this.forgeFullVersion.substring(resolveVersionId().length() + 1) + ".json");
            if (direct3.exists()) {
                return direct3;
            }
        }

        List<File> candidates = new ArrayList<File>();
        File[] versionDirs = versionsRoot.listFiles();
        if (versionDirs == null) {
            return null;
        }

        for (File dir : versionDirs) {
            if (!dir.isDirectory()) {
                continue;
            }
            File[] children = dir.listFiles();
            if (children == null) {
                continue;
            }
            for (File child : children) {
                if (!child.isFile() || !child.getName().endsWith(".json")) {
                    continue;
                }
                try {
                    String content = readFile(child);
                    if (content.contains("net.minecraftforge") && (this.forgeFullVersion == null || content.contains(this.forgeFullVersion))) {
                        candidates.add(child);
                    }
                } catch (IOException ignored) {
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort(new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return Long.compare(o2.lastModified(), o1.lastModified());
            }
        });
        return candidates.get(0);
    }

    private void reloadForgeProfileFromInstalledVersion() throws IOException {
        File versionJson = findInstalledForgeVersionJson();
        if (versionJson == null || !versionJson.exists()) {
            throw new FileNotFoundException("Installed Forge version json not found");
        }

        String json = readFile(versionJson);
        if (json == null || json.trim().isEmpty()) {
            throw new IOException("Installed Forge version json is empty: " + versionJson.getAbsolutePath());
        }

        File localForge = new File(engine.getGameFolder().getCacheDir(), "forge.json");
        localForge.getParentFile().mkdirs();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(localForge), "UTF-8")) {
            writer.write(json);
        }

        engine.reg(JsonUtil.getGson().fromJson(json, GameForge.class));
    }

    private String readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        }
    }

    private boolean isForgeWrapperFallbackCandidate() {
        return this.engine != null
                && this.engine.getGameStyle() != null
                && this.engine.getGameStyle().equals(GameStyle.FORGE_1_13_HIGHER);
    }

    private String buildOfficialForgeLauncherUrl(String fullVersion) {
        return FORGE_MAVEN_BASE + fullVersion + "/forge-" + fullVersion + "-launcher.jar";
    }

    private void ensureForgeWrapperArtifacts() throws IOException {
        if (!isForgeWrapperFallbackCandidate()) {
            return;
        }
        if (this.forgeFullVersion == null || this.forgeFullVersion.trim().isEmpty()) {
            throw new FileNotFoundException("Forge wrapper fallback requires a resolved Forge version");
        }

        File legacyWrapper = new File(this.engine.getGameFolder().getLibsDir(), "io/github/zekerzhayard/ForgeWrapper/mmc2/ForgeWrapper-mmc2.jar");
        File versionedWrapper = new File(this.engine.getGameFolder().getLibsDir(), "io/github/zekerzhayard/ForgeWrapper/1.6.0/ForgeWrapper-1.6.0.jar");
        File wrapper = legacyWrapper.exists() && legacyWrapper.length() > 0L ? legacyWrapper : versionedWrapper;
        if (!wrapper.exists() || wrapper.length() == 0L) {
            downloadForgeWrapperJar(legacyWrapper, versionedWrapper);
            wrapper = versionedWrapper.exists() && versionedWrapper.length() > 0L ? versionedWrapper : legacyWrapper;
        }
        this.forgeWrapperJar = wrapper;
        this.jars.put("io.github.zekerzhayard:ForgeWrapper:mmc2", wrapper.getAbsolutePath());
        GameVerifier.addToFileList(wrapper.getAbsolutePath()
                .replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "")
                .replace('/', File.separatorChar));

        File launcher = new File(this.engine.getGameFolder().getLibsDir(),
                "net/minecraftforge/forge/" + this.forgeFullVersion + "/forge-" + this.forgeFullVersion + "-launcher.jar");
        if (!launcher.exists() || launcher.length() == 0L) {
            downloadFile(buildOfficialForgeLauncherUrl(this.forgeFullVersion), launcher);
        }
        this.forgeLauncherJar = launcher;
        this.jars.put("net.minecraftforge:forge:" + this.forgeFullVersion + ":launcher", launcher.getAbsolutePath());
        GameVerifier.addToFileList(launcher.getAbsolutePath()
                .replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "")
                .replace('/', File.separatorChar));
    }

    private void downloadForgeWrapperJar(File legacyWrapper, File versionedWrapper) throws IOException {
        IOException lastError = null;
        for (String url : FORGE_WRAPPER_URLS) {
            File target = url.contains("ForgeWrapper-mmc2.jar") ? legacyWrapper : versionedWrapper;
            try {
                downloadFile(url, target);
                return;
            } catch (IOException e) {
                lastError = e;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Unable to download ForgeWrapper jar");
    }

    private void updateForgeLibraries() {
        if (!this.isLegacyForgeStyle()) {
            try {
                this.ensureModernForgeInstalledOfficially();
            } catch (Exception e) {
                File marker = new File(engine.getGameFolder().getCacheDir(), "forge-installer.failed");
                this.registerDownloadFailure(marker, "official forge installer", (e instanceof Exception) ? (Exception) e : new Exception(e));
                Logger.err("Official Forge installer failed: " + e.getMessage());
                return;
            }
        }

        if (this.engine.getGameForge() == null) {
            Logger.err("No forge version found");
            System.exit(3);
        }

        for (Forge1_17_HigherLibrary lib : this.engine.getGameForge().getLibraries()) {
            String artifactPath = lib.getArtifactPath();
            if (artifactPath == null || artifactPath.trim().isEmpty()) {
                continue;
            }

            File libPath = new File(engine.getGameFolder().getLibsDir(), artifactPath);
            GameVerifier.addToFileList(
                    libPath.getAbsolutePath().replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "")
                            .replace('/', File.separatorChar));
            jars.put(lib.getName(), libPath.getAbsolutePath());

            boolean alreadyValid = libPath.exists() && (lib.getArtifactSha1() == null || lib.getArtifactSha1().trim().isEmpty() || FileUtil.matchSHA1(libPath, lib.getArtifactSha1()));
            if (alreadyValid) {
                continue;
            }

            if (extractForgeArtifactFromInstaller(lib, libPath)) {
                continue;
            }

            String artifactUrl = lib.getArtifactUrl();
            if (artifactUrl == null || artifactUrl.trim().isEmpty()) {
                this.registerDownloadFailure(libPath, String.valueOf(artifactUrl), new IOException("Artifact missing from installer and no official URL available"));
                continue;
            }

            final Downloader downloadTask = new Downloader(libPath, artifactUrl, lib.getArtifactSha1(), engine);
            if (downloadTask.requireUpdate()) {
                if (!verifier.existInDeleteList(libPath.getAbsolutePath()
                        .replace(engine.getGameFolder().getGameDir().getAbsolutePath(), ""))) {
                    this.filesToDownload++;
                    this.customJarsExecutor.submit(downloadTask);
                }
            }
        }
    }

    private void updateLegacyForgeArtifacts() {
        try {
            String mcVersion = resolveVersionId();
            String forgeVersion = resolveOfficialForgeVersion(mcVersion);
            if (forgeVersion == null || forgeVersion.trim().isEmpty()) {
                throw new FileNotFoundException("No Forge promotion found for Minecraft " + mcVersion);
            }

            String fullVersion = mcVersion + "-" + forgeVersion;
            this.forgeFullVersion = fullVersion;

            try {
                String installerUrl = buildOfficialForgeInstallerUrl(mcVersion, forgeVersion);
                File installerJar = new File(engine.getGameFolder().getCacheDir(), "forge-" + fullVersion + "-installer.jar");
                installerJar.getParentFile().mkdirs();
                downloadFile(installerUrl, installerJar);
                this.forgeInstallerJar = installerJar;
            } catch (Exception ignored) {
            }

            String artifactPath = "net/minecraftforge/forge/" + fullVersion + "/forge-" + fullVersion + "-universal.jar";
            File libPath = new File(engine.getGameFolder().getLibsDir(), artifactPath);
            GameVerifier.addToFileList(libPath.getAbsolutePath()
                    .replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "")
                    .replace('/', File.separatorChar));
            jars.put("net.minecraftforge:forge:" + fullVersion + ":universal", libPath.getAbsolutePath());

            if (libPath.exists()) {
                return;
            }

            if (extractInstallerEntryToFile(artifactPath, libPath, null)) {
                return;
            }

            String url = buildOfficialForgeUniversalUrl(fullVersion);
            final Downloader downloadTask = new Downloader(libPath, url, null, engine);
            if (downloadTask.requireUpdate()) {
                this.filesToDownload++;
                this.customJarsExecutor.submit(downloadTask);
            }
        } catch (Exception e) {
            this.registerDownloadFailure(new File(engine.getGameFolder().getLibsDir(), "net/minecraftforge/forge/legacy-forge-placeholder.jar"), "official forge maven", (e instanceof Exception) ? (Exception) e : new Exception(e));
        }
    }


    public void updateJava() {
        String targetComponent = resolvePreferredJavaComponent();
        if (targetComponent == null || targetComponent.trim().isEmpty()) {
            this.javaPrepared = true;
            return;
        }
        this.updateJavaComponent(targetComponent);
        this.javaPrepared = true;
    }

    private void updateJavaComponent(String targetComponent) {
        if (targetComponent == null || targetComponent.trim().isEmpty()) {
            return;
        }
        if (this.jvmManifest == null || this.jvmManifest.getFiles() == null) {
            return;
        }

        if (this.javaExecutor == null || this.javaExecutor.isShutdown() || this.javaExecutor.isTerminated()) {
            this.javaExecutor = Executors.newFixedThreadPool(5);
        }

        Map<String, JVMFile> objects = this.jvmManifest.getFiles();
        for (String javaFile : objects.keySet()) {
            JVMFile jvmFile = (JVMFile) objects.get(javaFile);
            File localFolder = new File(engine.getGameFolder().getBinDir(), targetComponent);
            localFolder.mkdirs();
            File local = new File(localFolder, javaFile);

            if (!jvmFile.getType().equals(EnumJavaFileType.DIRECTORY.getName())) {
                Downloader downloadTask = new Downloader(local, jvmFile.getDownloads().getRaw().getUrl().toString(), jvmFile.getDownloads().getRaw().getSha1(), engine);
                GameVerifier.addToFileList(local.getAbsolutePath().replace(engine.getGameFolder().getCacheDir().getAbsolutePath(), "").replace('/', File.separatorChar));
                if (downloadTask.requireUpdate()) {
                    this.javaExecutor.submit(downloadTask);
                    this.filesToDownload++;
                }
            } else {
                local.mkdirs();
            }
        }

        this.javaExecutor.shutdown();
        try {
            this.javaExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Logger.log("Jre Update finished for component " + targetComponent + ".");
    }

    private String resolvePreferredJavaComponent() {
        if (shouldUseAlphaRuntimeForLegacyBootstrapForge()) {
            return EnumJavaVersion.JAVA_RUNTIME_ALPHA.getCode();
        }
        if (shouldUseLegacyRuntimeForForge113To116()) {
            return EnumJavaVersion.JRE_LEGACY.getCode();
        }
        if (this.getEngine() != null && this.getEngine().getMinecraftVersion() != null
                && this.getEngine().getMinecraftVersion().getJavaVersion() != null) {
            return this.getEngine().getMinecraftVersion().getJavaVersion().getComponent();
        }
        return null;
    }

    private boolean shouldUseAlphaRuntimeForLegacyBootstrapForge() {
        return this.engine != null
                && this.engine.getGameStyle() != null
                && this.engine.getGameStyle().equals(GameStyle.FORGE_1_13_HIGHER)
                && ForgeLaunchResolver.resolveMode(this.engine) == ForgeLaunchResolver.Mode.LEGACY_BOOTSTRAP_LAUNCHER;
    }

    private boolean shouldUseLegacyRuntimeForForge113To116() {
        return this.engine != null
                && this.engine.getGameStyle() != null
                && this.engine.getGameStyle().equals(GameStyle.FORGE_1_13_HIGHER)
                && ForgeLaunchResolver.resolveMode(this.engine) != ForgeLaunchResolver.Mode.LEGACY_BOOTSTRAP_LAUNCHER;
    }

    private String findJavaBinaryInComponent(String component) {
        if (component == null || component.trim().isEmpty() || this.engine == null || this.engine.getGameFolder() == null) {
            return null;
        }

        File runtimeDir = new File(this.engine.getGameFolder().getBinDir(), component);
        File[] candidates = new File[] {
                new File(runtimeDir, "bin" + File.separator + "java.exe"),
                new File(runtimeDir, "bin" + File.separator + "javaw.exe"),
                new File(runtimeDir, "bin" + File.separator + "java")
        };
        for (File candidate : candidates) {
            if (candidate.exists()) {
                return candidate.getAbsolutePath();
            }
        }
        return null;
    }


    private String ensureInstallerJavaComponent(String component, boolean allowRefresh) {
        if (component == null || component.trim().isEmpty() || this.engine == null || this.engine.getGameFolder() == null) {
            return null;
        }

        String binary = findJavaBinaryInComponent(component);
        if (binary != null && isAcceptableForgeInstallerJava(binary)) {
            return binary;
        }

        if (!allowRefresh) {
            return binary;
        }

        try {
            Logger.log("Refreshing Java component for Forge installer: " + component);
            File runtimeDir = new File(this.engine.getGameFolder().getBinDir(), component);
            deleteRecursively(runtimeDir);
            if (downloadJavaManifestForComponent(component)) {
                updateJavaComponent(component);
            }
            binary = findJavaBinaryInComponent(component);
            if (binary != null) {
                Logger.log("Refreshed Java component " + component + " -> " + describeJavaBinary(binary));
            }
            return binary;
        } catch (Exception e) {
            Logger.err("Unable to refresh Java component " + component + " for Forge installer: " + e.getMessage());
            return binary;
        }
    }

    private boolean isAcceptableForgeInstallerJava(String javaBinary) {
        JavaBinaryVersion version = readJavaBinaryVersion(javaBinary);
        if (version == null) {
            return false;
        }
        if (version.major >= 9) {
            return true;
        }
        if (version.major == 8) {
            return version.update >= 101;
        }
        return false;
    }

    private String describeJavaBinary(String javaBinary) {
        JavaBinaryVersion version = readJavaBinaryVersion(javaBinary);
        if (version == null) {
            return javaBinary + " (unknown version)";
        }
        return javaBinary + " (" + version.raw + ")";
    }

    private JavaBinaryVersion readJavaBinaryVersion(String javaBinary) {
        if (javaBinary == null || javaBinary.trim().isEmpty()) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(javaBinary, "-version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String firstLine = null;
            while ((line = reader.readLine()) != null) {
                if (firstLine == null && !line.trim().isEmpty()) {
                    firstLine = line.trim();
                }
            }
            process.waitFor();
            if (firstLine == null) {
                return null;
            }
            return JavaBinaryVersion.parse(firstLine);
        } catch (Exception e) {
            return null;
        }
    }

    private void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IOException("Unable to delete " + file.getAbsolutePath());
        }
    }

    private static final class JavaBinaryVersion {
        private final String raw;
        private final int major;
        private final int update;

        private JavaBinaryVersion(String raw, int major, int update) {
            this.raw = raw;
            this.major = major;
            this.update = update;
        }

        private static JavaBinaryVersion parse(String versionLine) {
            if (versionLine == null) {
                return null;
            }
            int firstQuote = versionLine.indexOf('"');
            int secondQuote = versionLine.indexOf('"', firstQuote + 1);
            if (firstQuote < 0 || secondQuote <= firstQuote) {
                return null;
            }
            String raw = versionLine.substring(firstQuote + 1, secondQuote);
            if (raw.startsWith("1.8.0_")) {
                try {
                    return new JavaBinaryVersion(raw, 8, Integer.parseInt(raw.substring("1.8.0_".length())));
                } catch (NumberFormatException ignored) {
                    return new JavaBinaryVersion(raw, 8, 0);
                }
            }

            String[] parts = raw.split("[._-]");
            if (parts.length == 0) {
                return null;
            }
            try {
                return new JavaBinaryVersion(raw, Integer.parseInt(parts[0]), 0);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /**
     * Update minecraft assets
     */
    public void updateAssets() {
        String json = null;
        String assetUrl = minecraftVersion.getAssetIndex().getUrl().toString();
        AssetIndex assetsList;
        try {
            json = JsonUtil.loadJSON(assetUrl);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            assetsList = (AssetIndex) JsonUtil.getGson().fromJson(json, AssetIndex.class);
        }
        Map<String, AssetObject> objects = assetsList.getObjects();
        for (String assetKey : objects.keySet()) {
            AssetObject asset = (AssetObject) objects.get(assetKey);
            File mc = getAssetInMcFolder(asset.getHash());
            File local = getAsset(asset.getHash());

            GameVerifier.addToFileList(
                    local.getAbsolutePath().replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "")
                            .replace('/', File.separatorChar));

            local.getParentFile().mkdirs();
            if ((!local.exists()) || (!FileUtil.matchSHA1(local, asset.getHash()))) {
                if ((!local.exists()) && (mc.exists()) && (FileUtil.matchSHA1(mc, asset.getHash()))) {
                    this.assetsExecutor.submit(new Duplicator(mc, local));
                    Logger.log("Copying asset " + local.getName());
                    this.setCurrentInfoText("Copie d'une ressource.");
                } else {
                    Downloader downloadTask = new Downloader(local, toURL(asset.getHash()), asset.getHash(), engine);
                    if (downloadTask.requireUpdate()) {
                        this.assetsExecutor.submit(downloadTask);
                        this.filesToDownload++;
                        Logger.log("Downloading asset " + local.getName());
                    }
                }
            }
        }
        this.assetsExecutor.shutdown();
        File indexes = new File(engine.getGameFolder().getAssetsDir(), "indexes");
        indexes.mkdirs();
        File index = new File(indexes, minecraftVersion.getAssets() + ".json");

        GameVerifier.addToFileList(index.getAbsolutePath()
                .replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "").replace('/', File.separatorChar));

        if (!index.exists()) {
            try {
                index.createNewFile();
                BufferedWriter writer = new BufferedWriter(new FileWriter(index));
                writer.write(JsonUtil.getGson().toJson(assetsList));
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            this.assetsExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    /**
     * Index Minecraft version json
     */
    public void indexVersion() {
        String json = null;
        try {
            json = JsonUtil.loadJSON(engine.getGameLinks().getJsonUrl());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            minecraftVersion = (MinecraftVersion) JsonUtil.getGson().fromJson(json, MinecraftVersion.class);
            engine.reg(minecraftVersion);
        }
    }

    private void indexForge() {
        String mcVersion = resolveVersionId();
        String json;

        try {
            json = tryLoadOfficialForgeJson(mcVersion);
        } catch (Exception e) {
            throw new RuntimeException("Unable to load official Forge metadata for Minecraft " + mcVersion, e);
        }

        try {
            File localForge = new File(engine.getGameFolder().getCacheDir(), "forge.json");
            localForge.getParentFile().mkdirs();
            try (Writer w = new OutputStreamWriter(new FileOutputStream(localForge), "UTF-8")) {
                w.write(json);
            }
        } catch (IOException ignored) {
        }

        engine.reg(JsonUtil.getGson().fromJson(json, GameForge.class));
    }

    private String tryLoadOfficialForgeJson(String mcVersion) throws IOException {
        String forgeVersion = resolveOfficialForgeVersion(mcVersion);
        if (forgeVersion == null || forgeVersion.trim().isEmpty()) {
            throw new FileNotFoundException("No Forge version found in promotions for Minecraft " + mcVersion);
        }

        this.forgeFullVersion = mcVersion + "-" + forgeVersion;

        String installerUrl = buildOfficialForgeInstallerUrl(mcVersion, forgeVersion);
        File installerJar = new File(engine.getGameFolder().getCacheDir(), "forge-" + this.forgeFullVersion + "-installer.jar");
        installerJar.getParentFile().mkdirs();
        downloadFile(installerUrl, installerJar);
        this.forgeInstallerJar = installerJar;

        String versionJson = readZipEntry(installerJar, "version.json");
        if (versionJson != null && !versionJson.trim().isEmpty()) {
            return versionJson;
        }

        String installProfile = readZipEntry(installerJar, "install_profile.json");
        if (installProfile == null || installProfile.trim().isEmpty()) {
            throw new FileNotFoundException("Neither version.json nor install_profile.json found in Forge installer");
        }

        JsonObject root = JsonParser.parseString(installProfile).getAsJsonObject();
        if (root.has("versionInfo") && root.get("versionInfo").isJsonObject()) {
            return root.getAsJsonObject("versionInfo").toString();
        }
        return installProfile;
    }

    private String resolveOfficialForgeVersion(String mcVersion) throws IOException {
        String json = JsonUtil.loadJSON(FORGE_PROMOTIONS_URL);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject promos = root.getAsJsonObject("promos");
        if (promos == null) return null;

        String recommendedKey = mcVersion + "-recommended";
        String latestKey = mcVersion + "-latest";

        if (promos.has(recommendedKey) && !promos.get(recommendedKey).isJsonNull()) {
            return promos.get(recommendedKey).getAsString();
        }
        if (promos.has(latestKey) && !promos.get(latestKey).isJsonNull()) {
            return promos.get(latestKey).getAsString();
        }
        return null;
    }

    private String buildOfficialForgeInstallerUrl(String mcVersion, String forgeVersion) {
        String fullVersion = mcVersion + "-" + forgeVersion;
        return FORGE_MAVEN_BASE + fullVersion + "/forge-" + fullVersion + "-installer.jar";
    }

    private String buildOfficialForgeUniversalUrl(String fullVersion) {
        return FORGE_MAVEN_BASE + fullVersion + "/forge-" + fullVersion + "-universal.jar";
    }

    private void downloadFile(String urlString, File destination) throws IOException {
        destination.getParentFile().mkdirs();
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "MajestyLauncher");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " for " + urlString);
        }

        File tmp = new File(destination.getAbsolutePath() + ".part");
        try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        if (destination.exists() && !destination.delete()) {
            throw new IOException("Unable to delete existing installer " + destination.getAbsolutePath());
        }
        if (!tmp.renameTo(destination)) {
            throw new IOException("Unable to move Forge installer to cache");
        }
    }

    private boolean extractForgeArtifactFromInstaller(Forge1_17_HigherLibrary lib, File destination) {
        String artifactPath = lib.getArtifactPath();
        String sha1 = lib.getArtifactSha1();
        try {
            return extractInstallerEntryToFile(artifactPath, destination, sha1);
        } catch (IOException e) {
            this.registerDownloadFailure(destination, artifactPath, e);
            return false;
        }
    }

    private boolean extractInstallerEntryToFile(String artifactPath, File destination, String expectedSha1) throws IOException {
        if (this.forgeInstallerJar == null || !this.forgeInstallerJar.exists() || artifactPath == null || artifactPath.trim().isEmpty()) {
            return false;
        }

        String normalized = artifactPath.replace('\\', '/');

        try (ZipFile zip = new ZipFile(this.forgeInstallerJar)) {
            ZipEntry entry = findInstallerArtifactEntry(zip, normalized);
            if (entry == null) {
                return false;
            }

            destination.getParentFile().mkdirs();
            File tmp = new File(destination.getAbsolutePath() + ".part");
            try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            if (expectedSha1 != null && !expectedSha1.trim().isEmpty() && !FileUtil.matchSHA1(tmp, expectedSha1)) {
                tmp.delete();
                throw new IOException("SHA1 mismatch for installer-embedded artifact " + normalized);
            }

            if (destination.exists() && !destination.delete()) {
                throw new IOException("Unable to delete existing file " + destination.getAbsolutePath());
            }
            if (!tmp.renameTo(destination)) {
                throw new IOException("Unable to move extracted Forge artifact to final destination");
            }

            this.downloadedFiles++;
            return true;
        }
    }

    private ZipEntry findInstallerArtifactEntry(ZipFile zip, String artifactPath) {
        String[] candidates = new String[] {
                artifactPath,
                "maven/" + artifactPath,
                "META-INF/" + artifactPath,
                "data/" + artifactPath
        };

        for (String candidate : candidates) {
            ZipEntry direct = zip.getEntry(candidate);
            if (direct != null) {
                return direct;
            }
        }

        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.equals(artifactPath) || name.endsWith("/" + artifactPath)) {
                return entry;
            }
        }
        return null;
    }

    private String readZipEntry(File zipFile, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) return null;
            try (InputStream in = zip.getInputStream(entry);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toString("UTF-8");
            }
        }
    }

    /**
     * Index Minecraft local version json
     */
    @SuppressWarnings("deprecation")
	public void indexLocalVersion() {
        File f = new File(engine.getGameFolder().getCacheDir(), engine.getGameLinks().getJsonName());
        String json = null;
        try {
            json = JsonUtil.loadJSON(f.toURL().toString());
        } catch (IOException e) {
            System.err.println(f.getAbsolutePath() + "not found.");
            e.printStackTrace();
        } finally {
            minecraftLocalVersion = (MinecraftVersion) JsonUtil.getGson().fromJson(json, MinecraftVersion.class);
            engine.reg(minecraftLocalVersion);
        }
    }

    @SuppressWarnings("deprecation")
	private void indexLocalForge() {
        File f = new File(engine.getGameFolder().getCacheDir(), "forge.json");
        String json = null;
        try {
            json = JsonUtil.loadJSON(f.toURL().toString());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            engine.reg(JsonUtil.getGson().fromJson(json, GameForge.class));
        }
    }


    /**
     * Index Minecraft java version
     */
//	public void indexJava(EnumJavaOS java) {
//		String javaManifestJson = null;
//		try {
//			javaManifestJson = JsonUtil.loadJSON(java.getUrl());
//		} catch (IOException e) {
//			e.printStackTrace();
//		} finally {
//			Logger.log("jsonM: " + javaManifestJson);
//			jvmManifest = (JVMManifest) JsonUtil.getGson().fromJson(javaManifestJson, JVMManifest.class);
//		}
//	}
    public void indexJava(String url) {
        String javaManifestJson = null;
        try {
            javaManifestJson = JsonUtil.loadJSON(url);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            jvmManifest = (JVMManifest) JsonUtil.getGson().fromJson(javaManifestJson, JVMManifest.class);
        }
    }

    /**
     * Index minecraft assets json
     */
    public void indexAssets() {
        String json = null;
        String assetUrl = minecraftVersion.getAssetIndex().getUrl().toString();
        try {
            json = JsonUtil.loadJSON(assetUrl);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            assetsList = (AssetIndex) JsonUtil.getGson().fromJson(json, AssetIndex.class);
        }
    }

    /**
     * @return The assetsList
     */
    public AssetIndex getAssetsList() {
        return assetsList;
    }

    /**
     * @param hash The hash
     * @return The hash url of the assets
     */
    private String toURL(String hash) {
        return ASSETS_URL + hash.substring(0, 2) + "/" + hash;
    }

    /**
     * Update custom jars
     */
    private void updateCustomJars() {
        for (Map.Entry<String, LauncherFile> entry : this.files.entrySet()) {
            LauncherFile launcherFile = entry.getValue();
            File libPath = new File(launcherFile.getPath());
            String url = launcherFile.getUrl();

            final Downloader customDownloadTask = new Downloader(libPath, url, null, engine);

            if (!verifier.existInDeleteList(
                    libPath.getAbsolutePath().replace(engine.getGameFolder().getGameDir().getAbsolutePath(), ""))) {
                if (customDownloadTask.requireUpdate()) {
                    this.customJarsExecutor.submit(customDownloadTask);
                }
            }
        }
    }


    private boolean usesRemoteCustomFiles() {
        return !this.isForge()
                && !this.engine.getGameStyle().equals(GameStyle.VANILLA)
                && !this.engine.getGameStyle().equals(GameStyle.VANILLA_1_19_HIGHER);
    }

    private boolean usesOfflineCustomFiles() {
        return !this.isForge()
                && !this.engine.getGameStyle().equals(GameStyle.VANILLA)
                && !this.engine.getGameStyle().equals(GameStyle.VANILLA_1_19_HIGHER);
    }

    private boolean isForge() {
        return this.engine.getGameStyle().equals(GameStyle.FORGE_1_13_HIGHER)
                || this.engine.getGameStyle().equals(GameStyle.FORGE_1_17_HIGHER)
                || this.engine.getGameStyle().equals(GameStyle.FORGE_1_7_10_OLD)
                || this.engine.getGameStyle().equals(GameStyle.FORGE_1_8_TO_1_12_2)
                || this.engine.getGameStyle().equals(GameStyle.FORGE_1_19_HIGHER);
    }

    private boolean isLegacyForgeStyle() {
        return this.engine.getGameStyle().equals(GameStyle.FORGE_1_7_10_OLD)
                || this.engine.getGameStyle().equals(GameStyle.FORGE_1_8_TO_1_12_2);
    }

    private boolean requiresForgeMetadata() {
        return this.engine.getGameStyle().equals(GameStyle.FORGE_1_13_HIGHER)
                || this.engine.getGameStyle().equals(GameStyle.FORGE_1_17_HIGHER)
                || this.engine.getGameStyle().equals(GameStyle.FORGE_1_19_HIGHER);
    }

    /**
     * @param hash The hash
     * @return The asset File
     */
    private File getAsset(String hash) {
        File assetsDir = this.engine.getGameFolder().getAssetsDir();
        File mcObjectsDir = new File(assetsDir, "objects");
        File hex = new File(mcObjectsDir, hash.substring(0, 2));
        return new File(hex, hash);
    }

    public Collection<String> getJars() {
        return  jars.values();
    }

    /**
     * @param hash The hash
     * @return The asset file in minecraft folder
     */
    private File getAssetInMcFolder(String hash) {
        File minecraftAssetsDir = new File(GameUtils.getWorkingDirectory("minecraft"), "assets");
        File minecraftObjectsDir = new File(minecraftAssetsDir, "objects");
        File hex = new File(minecraftObjectsDir, hash.substring(0, 2));
        return new File(hex, hash);
    }

    /**
     * @return The GameEngine instance
     */
    public GameEngine getEngine() {
        return engine;
    }

    /**
     * @return Get current Info text
     */
    public String getCurrentInfo() {
        return this.currentInfoText;
    }

    /**
     * Set current info text
     *
     * @param name The text of the info
     */
    public void setCurrentInfoText(String name) {
        this.currentInfoText = name;
    }

    /**
     * @return The current File name
     */
    public String getCurrentFile() {
        return this.currentFile;
    }

    /**
     * Set current File name
     *
     * @param name The name
     */
    public void setCurrentFile(String name) {
        this.currentFile = name;
    }

    public boolean isForgeWrapperFallbackRequired() {
        if (this.forgeWrapperFallbackRequired) {
            return true;
        }
        if (this.engine == null || this.engine.getGameStyle() == null || !this.engine.getGameStyle().equals(GameStyle.FORGE_1_13_HIGHER)) {
            return false;
        }
        String fullVersion = this.forgeFullVersion;
        if ((fullVersion == null || fullVersion.trim().isEmpty()) && this.engine.getGameForge() != null && this.engine.getGameForge().getArguments() != null && this.engine.getGameForge().getArguments().getGame() != null) {
            String mcVersion = null;
            String forgeVersion = null;
            List<String> args = this.engine.getGameForge().getArguments().getGame();
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if ("--fml.mcVersion".equals(arg) && i + 1 < args.size()) {
                    mcVersion = args.get(i + 1);
                } else if ("--fml.forgeVersion".equals(arg) && i + 1 < args.size()) {
                    forgeVersion = args.get(i + 1);
                }
            }
            if (mcVersion != null && forgeVersion != null) {
                fullVersion = mcVersion + "-" + forgeVersion;
            }
        }
        if (fullVersion == null || fullVersion.trim().isEmpty()) {
            return false;
        }
        File libDir = new File(this.engine.getGameFolder().getLibsDir(), "net/minecraftforge/forge/" + fullVersion);
        File clientJar = new File(libDir, "forge-" + fullVersion + "-client.jar");
        File launcherJar = new File(libDir, "forge-" + fullVersion + "-launcher.jar");
        return !clientJar.exists() && launcherJar.exists();
    }

    public File getForgeInstallerJar() {
        if (this.forgeInstallerJar != null && this.forgeInstallerJar.exists()) {
            return this.forgeInstallerJar;
        }
        if (this.forgeFullVersion != null && !this.forgeFullVersion.trim().isEmpty()) {
            File cached = new File(this.engine.getGameFolder().getCacheDir(), "forge-" + this.forgeFullVersion + "-installer.jar");
            if (cached.exists()) {
                return cached;
            }
        }
        return null;
    }

    public File getForgeWrapperJar() {
        if (this.forgeWrapperJar != null && this.forgeWrapperJar.exists()) {
            return this.forgeWrapperJar;
        }
        File versioned = new File(this.engine.getGameFolder().getLibsDir(), "io/github/zekerzhayard/ForgeWrapper/1.6.0/ForgeWrapper-1.6.0.jar");
        if (versioned.exists()) {
            return versioned;
        }
        File legacy = new File(this.engine.getGameFolder().getLibsDir(), "io/github/zekerzhayard/ForgeWrapper/mmc2/ForgeWrapper-mmc2.jar");
        return legacy.exists() ? legacy : null;
    }

    public File getForgeLauncherJar() {
        if (this.forgeLauncherJar != null && this.forgeLauncherJar.exists()) {
            return this.forgeLauncherJar;
        }
        if (this.forgeFullVersion != null && !this.forgeFullVersion.trim().isEmpty()) {
            File cached = new File(this.engine.getGameFolder().getLibsDir(),
                    "net/minecraftforge/forge/" + this.forgeFullVersion + "/forge-" + this.forgeFullVersion + "-launcher.jar");
            if (cached.exists()) {
                return cached;
            }
        }
        return null;
    }

    public String getForgeFullVersion() {
        return this.forgeFullVersion;
    }

    public boolean isRunningOnline() {
        return this.isOnline;
    }

    public MinecraftVersion getLocalVersion() {
        return this.minecraftLocalVersion;
    }

    /**
     * @return If the host is reachable
     */
    @SuppressWarnings("deprecation")
	public boolean isOnline() {
        try {
            URL url = new URL(HOST);
            URLConnection connection = url.openConnection();
            connection.connect();
            return true;
        } catch (MalformedURLException e) {
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
