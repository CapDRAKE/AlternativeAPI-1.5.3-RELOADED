package fr.trxyy.alternative.alternative_api.build;

import fr.trxyy.alternative.alternative_api.*;
import fr.trxyy.alternative.alternative_api.minecraft.json.*;
import fr.trxyy.alternative.alternative_api.minecraft.utils.EnumJavaVersion;
import fr.trxyy.alternative.alternative_api.utils.*;
import fr.trxyy.alternative.alternative_api.utils.file.*;
import fr.trxyy.alternative.alternative_auth.account.*;
import javafx.application.*;
import org.apache.commons.lang3.text.*;

import java.awt.*;
import java.io.*;
import java.lang.ProcessBuilder.*;
import java.net.*;
import java.util.List;
import java.util.*;

/**
 * @author Trxyy
 */
public class GameRunner {

	private static final String[] SHARED_PROFILE_ENTRIES = new String[] {
			"servers.dat",
			"servers.dat_old",
			"resourcepacks",
			"shaderpacks",
			"options.txt",
			"optionsof.txt",
			"optionsshaders.txt",
			"optionsfullscreen.txt",
			"optionsoculus.txt"
	};

	/**
	 * The GameEngine instance
	 */
	private GameEngine engine;
	/**
	 * The Session of the user
	 */
	private Session session;

	/**
	 * The Constructor
	 * @param gameEngine The GameEngine instance
	 * @param account The session
	 */
	public GameRunner(GameEngine gameEngine, Session account) {
		this.engine = gameEngine;
		this.session = account;
		Logger.log("========================================");
		Logger.log("Unpacking natives             [Step 5/7]");
		Logger.log("========================================");
		this.unpackNatives();
		Logger.log("Deleting unrequired Natives   [Step 6/7]");
		Logger.log("========================================");
		this.deleteFakeNatives();
		if (engine.getStage() != null) {
			Platform.runLater(new Runnable() {
				public void run() {
					engine.getStage().hide();
				}
			});
		}
	}

	/**
	 * Launch the game
	 * @throws Exception
	 */
    public void launch() throws Exception {
    	syncSharedProfileData();
    	ArrayList<String> commands = this.getLaunchCommand();
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.redirectInput(Redirect.INHERIT);
        processBuilder.redirectOutput(Redirect.INHERIT);
        processBuilder.redirectError(Redirect.INHERIT);
		processBuilder.directory(engine.getGameFolder().getGameDir());
		processBuilder.redirectErrorStream(true);
		Logger.log("Launching: " + hideAccessToken(commands));
		try {
			Process process = processBuilder.start();
			process.waitFor();
			syncSharedProfileData();
			int exitVal = process.exitValue();
			if (exitVal != 0) {
				Logger.log("\n\n");
				Logger.log("========================================");
				Logger.log("|         Minecraft has crashed.       |");
				Logger.log("========================================");
			}
		} catch (IOException e) {
			throw new Exception("Cannot launch !", e);
		}
	}

    /**
     * Open a link
     * @param urlString The url to open
     */
	@SuppressWarnings("deprecation")
	public void openLink(String urlString) {
		try {
			Desktop.getDesktop().browse(new URL(urlString).toURI());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Get launch commands as a ArrayList<String>
	 * @return Launch commands as a ArrayList<String>
	 */
	private ArrayList<String> getLaunchCommand() {
		ArrayList<String> commands = new ArrayList<String>();
		OperatingSystem os = OperatingSystem.getCurrentPlatform();
		boolean modernForgeStyle = isModernForgeStyle();
		boolean forgeWrapperStyle = isForgeWrapperStyle();
		boolean managedG1GcProfile = shouldApplyManagedG1GcProfile();
		List<String> forgeJvmArguments = Collections.emptyList();
		String javaBinary = resolveJavaBinaryForLaunch();

		commands.add(javaBinary);

        commands.add("-XX:-UseAdaptiveSizePolicy");

		if (engine.getJVMArguments() != null) {
			commands.addAll(engine.getJVMArguments().getJVMArguments());
		}
		commands.addAll(getVersionJvmArguments());

		if (modernForgeStyle) {
			forgeJvmArguments = this.getForgeJVMArguments();
			commands.addAll(forgeJvmArguments);
			Logger.log(String.valueOf(forgeJvmArguments));
		}
		commands.add("-Dbsl.debug=True");

		if (os.equals(OperatingSystem.OSX)) {
			commands.add("-Xdock:name=Minecraft");
			commands.add("-Xdock:icon=" + engine.getGameFolder().getAssetsDir() + "icons/minecraft.icns");
		} else if (os.equals(OperatingSystem.WINDOWS)) {
			if (!shouldSkipLegacyCmsGc() && !managedG1GcProfile) {
				commands.add("-XX:+UseConcMarkSweepGC");
			}
			commands.add("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump");
		}

		if (managedG1GcProfile) {
			addManagedG1GcArguments(commands);
		}

		if (shouldInjectMinecraftLoggingConfiguration()) {
			File log4jFile = new File(this.engine.getGameFolder().getLogConfigsDir(), this.engine.getMinecraftVersion().getLogging().getClient().getFile().getId());
			commands.add(this.engine.getMinecraftVersion().getLogging().getClient().getArgument().replace("${path}", log4jFile.getAbsolutePath()));
		}
		String nativesPath = resolveRuntimeNativesDirectory().getAbsolutePath();
		commands.add("-Djava.library.path=" + nativesPath);
		commands.add("-Dorg.lwjgl.librarypath=" + nativesPath);
		commands.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
		commands.add("-Dfml.ignorePatchDiscrepancies=true");
		if (forgeWrapperStyle) {
			addForgeWrapperProperties(commands);
		}

		boolean is32Bit = "32".equals(System.getProperty("sun.arch.data.model"));
		String defaultArgument = is32Bit ? "-Xmx512M -Xmn128M" : "-Xmx1G -Xmn128M";
		if (engine.getGameMemory() != null) {
			defaultArgument = is32Bit ? "-Xmx512M -Xmn128M" : "-Xmx" + engine.getGameMemory().getCount() + " -Xmn128M";
		}
		String str[] = defaultArgument.split(" ");
		List<String> args = Arrays.asList(str);
		commands.addAll(args);

		commands.add("-cp");
		String classpath = GameUtils.constructClasspath(engine);
		if (modernForgeStyle) {
			classpath = filterModulePathEntriesFromClasspath(classpath, forgeJvmArguments);
			classpath = filterIncompatibleLibrariesFromModernForgeClasspath(classpath);
		}
		if (isLegacyBootstrapForgeStyle()) {
			classpath = filterLegacyForgeVersionJarFromClasspath(classpath);
		}
		if (forgeWrapperStyle) {
			classpath = filterForgeWrapperClasspath(classpath);
		}
		commands.add(classpath);
		commands.add(resolveMainClass());

		/** ----- Minecraft Arguments ----- */
		if (engine.getMinecraftVersion().getMinecraftArguments() != null) {
	        final String[] argsD = getArgumentsOlder();
	        List<String> arguments = Arrays.asList(argsD);
	        commands.addAll(arguments);
		}
		/** ----- Minecraft Arguments 1.13+ ----- */
		if (engine.getMinecraftVersion().getArguments() != null) {
			List<Argument> argsNewer = engine.getMinecraftVersion().getArguments().get(ArgumentType.GAME);
			final boolean legacyOptiFineCompatibility = shouldUseLegacyOptiFineArgumentCompatibility();
			final String[] newerArgumentsString = legacyOptiFineCompatibility
					? getArgumentsNewerLegacyCompatible(argsNewer)
					: getArgumentsNewer(argsNewer);

			commands.addAll(legacyOptiFineCompatibility
					? filterLegacyJsonArguments(newerArgumentsString)
					: filterModernMinecraftArguments(newerArgumentsString));
		}

		/** ----- Addons arguments ----- */
		if (engine.getGameArguments() != null) {
			commands.addAll(engine.getGameArguments().getGameArguments());
		}

		/** ----- Size of window ----- */
		if (engine.getGameSize() != null) {
			commands.add("--width=" + engine.getGameSize().getWidth());
			commands.add("--height=" + engine.getGameSize().getHeight());
		}

		/** ----- Change properties of Forge (1.13+) ----- */
		if (!engine.getGameStyle().getSpecificsArguments().equals("") && !forgeWrapperStyle) {
			commands.addAll(getForgeArguments());
		}

		/** ----- Direct connect to a server if required. ----- */
		if (engine.getGameConnect() != null) {
			System.out.println(getConnectHost() + " " + getConnectPort());
			if (!supportsQuickPlayMultiplayer()) {
				commands.add("--server");
				commands.add(getConnectHost());
				commands.add("--port");
				commands.add(getConnectPort());
			}
		}

		/** ----- Tweak Class if required ----- */
		if (engine.getGameStyle().equals(GameStyle.FORGE_1_7_10_OLD) || engine.getGameStyle().equals(GameStyle.FORGE_1_8_TO_1_12_2) || engine.getGameStyle().equals(GameStyle.OPTIFINE)) {
			commands.add("--tweakClass");
			commands.add(engine.getGameStyle().getTweakArgument());
		}

	    /** ----- Filtrage des paramètres quickPlay* ----- */
	    pruneMissingValueOption(commands, "--quickPlayPath");
	    pruneMissingValueOption(commands, "--quickPlaySingleplayer");
	    pruneMissingValueOption(commands, "--quickPlayMultiplayer");
	    pruneMissingValueOption(commands, "--quickPlayRealms");
	    /** ----- Suppression des arguments vides ----- */
	    commands.removeIf(arg -> arg.trim().isEmpty());
	    pruneMissingValueOption(commands, "--quickPlayPath");
	    pruneMissingValueOption(commands, "--quickPlaySingleplayer");
	    pruneMissingValueOption(commands, "--quickPlayMultiplayer");
	    pruneMissingValueOption(commands, "--quickPlayRealms");
	    pruneMissingValueOption(commands, "--clientId");
	    pruneMissingValueOption(commands, "--xuid");
	    pruneConflictingCollectorOptions(commands);

	    Logger.log("Commande de lancement complète : " + String.join(" ", commands));
	    return commands;
	}

	private boolean isModernForgeStyle() {
		return engine.getGameStyle().equals(GameStyle.FORGE_1_13_HIGHER)
				|| engine.getGameStyle().equals(GameStyle.FORGE_1_17_HIGHER)
				|| engine.getGameStyle().equals(GameStyle.FORGE_1_19_HIGHER);
	}

	private String resolveMainClass() {
		if (engine == null || engine.getGameStyle() == null) {
			return GameStyle.VANILLA.getMainClass();
		}
		if (shouldPreferVersionMainClass()) {
			String mainClass = resolveMainClassFromVersionMetadata();
			if (mainClass != null && !mainClass.trim().isEmpty()) {
				return mainClass;
			}
		}
		return ForgeLaunchResolver.resolveMainClass(engine);
	}

	private boolean shouldPreferVersionMainClass() {
		return engine.getGameStyle().equals(GameStyle.FABRIC)
				|| engine.getGameStyle().equals(GameStyle.QUILT)
				|| engine.getGameStyle().equals(GameStyle.NEOFORGE);
	}

	private boolean shouldUseVersionJvmArguments() {
		return engine.getGameStyle().equals(GameStyle.NEOFORGE);
	}

	private String resolveMainClassFromVersionMetadata() {
		if (engine != null && engine.getMinecraftVersion() != null && engine.getMinecraftVersion().getMainClass() != null) {
			return engine.getMinecraftVersion().getMainClass();
		}
		if (engine != null && engine.getGameUpdater() != null && engine.getGameUpdater().getLocalVersion() != null
				&& engine.getGameUpdater().getLocalVersion().getMainClass() != null) {
			return engine.getGameUpdater().getLocalVersion().getMainClass();
		}
		return null;
	}

	private String resolveJavaBinaryForLaunch() {
		String preferredComponent = resolvePreferredJavaComponentForLaunch();
		String preferredBinary = null;
		if (preferredComponent != null && !preferredComponent.trim().isEmpty()) {
			preferredBinary = findJavaBinaryInComponent(preferredComponent);
			if (preferredBinary != null && isAcceptableLaunchJava(preferredBinary)) {
				Logger.log("Using bundled Java runtime: " + describeJavaBinary(preferredBinary));
				return preferredBinary;
			}
			if (preferredBinary != null) {
				Logger.log("Bundled Java runtime rejected for this launch: " + describeJavaBinary(preferredBinary));
			}
		}

		if (isModernForgeStyle()) {
			String alphaBinary = findJavaBinaryInComponent(EnumJavaVersion.JAVA_RUNTIME_ALPHA.getCode());
			if (alphaBinary != null && isAcceptableLaunchJava(alphaBinary)) {
				Logger.log("Using alpha Java runtime fallback: " + describeJavaBinary(alphaBinary));
				return alphaBinary;
			}
		}

		String systemJava = stripWrappingQuotes(OperatingSystem.getJavaPath());
		if (systemJava != null && !systemJava.trim().isEmpty() && isAcceptableLaunchJava(systemJava)) {
			Logger.log("Using system Java runtime: " + describeJavaBinary(systemJava));
			return systemJava;
		}

		if (preferredBinary != null) {
			Logger.log("Falling back to bundled Java runtime despite validation failure: " + describeJavaBinary(preferredBinary));
			return preferredBinary;
		}

		Logger.log("Falling back to current launcher Java runtime: " + stripWrappingQuotes(OperatingSystem.getJavaPath()));
		return stripWrappingQuotes(OperatingSystem.getJavaPath());
	}

	private String resolvePreferredJavaComponentForLaunch() {
		if (shouldUseDeltaRuntimeForNeoForge()) {
			return EnumJavaVersion.JAVA_RUNTIME_DELTA.getCode();
		}
		if (shouldUseAlphaRuntimeForModernModloader()) {
			return EnumJavaVersion.JAVA_RUNTIME_ALPHA.getCode();
		}

		if (shouldUseLegacyRuntimeForForge113To116()) {
			return EnumJavaVersion.JRE_LEGACY.getCode();
		}

		if (this.engine.isOnline()) {
			if (this.engine.getMinecraftVersion() != null && this.engine.getMinecraftVersion().getJavaVersion() != null) {
				return this.engine.getMinecraftVersion().getJavaVersion().getComponent();
			}
		} else if (this.engine.getGameUpdater() != null && this.engine.getGameUpdater().getLocalVersion() != null
				&& this.engine.getGameUpdater().getLocalVersion().getJavaVersion() != null) {
			return this.engine.getGameUpdater().getLocalVersion().getJavaVersion().getComponent();
		}

		return null;
	}

	private boolean shouldUseAlphaRuntimeForLegacyBootstrapForge() {
		return engine != null
				&& engine.getGameStyle() != null
				&& engine.getGameStyle().equals(GameStyle.FORGE_1_13_HIGHER)
				&& isLegacyBootstrapForgeStyle();
	}

	private boolean shouldUseAlphaRuntimeForModernModloader() {
		return shouldUseAlphaRuntimeForLegacyBootstrapForge();
	}

	private boolean shouldUseDeltaRuntimeForNeoForge() {
		return engine != null
				&& engine.getGameStyle() != null
				&& engine.getGameStyle().equals(GameStyle.NEOFORGE)
				&& resolveConfiguredJavaMajorVersion() >= 21;
	}

	private boolean shouldUseLegacyRuntimeForForge113To116() {
		return engine != null
				&& engine.getGameStyle() != null
				&& engine.getGameStyle().equals(GameStyle.FORGE_1_13_HIGHER)
				&& !isLegacyBootstrapForgeStyle();
	}

	private boolean shouldSkipLegacyCmsGc() {
		if (isModernForgeStyle()) {
			return true;
		}
		if (requiresJava21ForLaunch()) {
			return true;
		}

		String preferredComponent = resolvePreferredJavaComponentForLaunch();
		return EnumJavaVersion.JAVA_RUNTIME_ALPHA.getCode().equals(preferredComponent)
				|| EnumJavaVersion.JAVA_RUNTIME_DELTA.getCode().equals(preferredComponent);
	}

	private boolean shouldApplyManagedG1GcProfile() {
		if (this.engine == null) {
			return false;
		}
		if (this.engine.isOnline()) {
			return this.engine.getMinecraftVersion() != null
					&& this.engine.getMinecraftVersion().getJavaVersion() != null;
		}
		return this.engine.getGameUpdater() != null
				&& this.engine.getGameUpdater().getLocalVersion() != null
				&& this.engine.getGameUpdater().getLocalVersion().getJavaVersion() != null;
	}

	private void addManagedG1GcArguments(List<String> commands) {
		if (commands == null) {
			return;
		}
		commands.add("-XX:+UnlockExperimentalVMOptions");
		commands.add("-XX:+UseG1GC");
		commands.add("-XX:G1NewSizePercent=20");
		commands.add("-XX:G1ReservePercent=20");
		commands.add("-XX:MaxGCPauseMillis=50");
		commands.add("-XX:G1HeapRegionSize=32M");
	}

	private void pruneConflictingCollectorOptions(List<String> commands) {
		if (commands == null || commands.isEmpty()) {
			return;
		}

		List<String> collectorFlags = Arrays.asList(
				"-XX:+UseConcMarkSweepGC",
				"-XX:+UseG1GC",
				"-XX:+UseParallelGC",
				"-XX:+UseParallelOldGC",
				"-XX:+UseSerialGC",
				"-XX:+UseZGC",
				"-XX:+UseShenandoahGC",
				"-XX:+UseEpsilonGC");

		String preferredCollector = null;
		for (String command : commands) {
			if (collectorFlags.contains(command)) {
				preferredCollector = command;
			}
		}

		if (preferredCollector == null) {
			return;
		}

		boolean keptPreferredCollector = false;
		for (int i = 0; i < commands.size(); i++) {
			String command = commands.get(i);
			if (!collectorFlags.contains(command)) {
				continue;
			}
			if (!preferredCollector.equals(command) || keptPreferredCollector) {
				Logger.log("Removing conflicting GC option: " + command);
				commands.remove(i);
				i--;
				continue;
			}
			keptPreferredCollector = true;
		}
	}

	private String findJavaBinaryInComponent(String component) {
		if (component == null || component.trim().isEmpty() || engine == null || engine.getGameFolder() == null) {
			return null;
		}

		File runtimeDir = new File(engine.getGameFolder().getBinDir(), component);
		File[] candidates = new File[] {
				new File(runtimeDir, "bin" + File.separator + "java.exe"),
				new File(runtimeDir, "bin" + File.separator + "javaw.exe"),
				new File(runtimeDir, "bin" + File.separator + "java")
		};

		for (File candidate : candidates) {
			if (candidate.exists()) {
				if (!OperatingSystem.isWindows() && !candidate.canExecute()) {
					try {
						candidate.setExecutable(true, false);
					} catch (SecurityException ignored) {
					}
				}
				return candidate.getAbsolutePath();
			}
		}

		return null;
	}

	private boolean shouldInjectMinecraftLoggingConfiguration() {
		return this.engine.getMinecraftVersion() != null
				&& this.engine.getMinecraftVersion().getLogging() != null
				&& !isModernForgeStyle()
				&& !isForgeWrapperStyle();
	}

	private String filterIncompatibleLibrariesFromModernForgeClasspath(String classpath) {
		if (classpath == null || classpath.trim().isEmpty()) {
			return classpath;
		}

		String[] classpathEntries = classpath.split(java.util.regex.Pattern.quote(File.pathSeparator));
		List<String> filtered = new ArrayList<String>();
		Map<String, Integer> lastIndexByKey = new HashMap<String, Integer>();

		for (int i = 0; i < classpathEntries.length; i++) {
			String entry = classpathEntries[i] == null ? "" : classpathEntries[i].trim();
			if (entry.isEmpty()) {
				continue;
			}
			String libraryKey = resolveLibraryCoordinateKey(entry);
			if (libraryKey != null && !libraryKey.isEmpty()) {
				lastIndexByKey.put(libraryKey, i);
			}
		}

		for (int i = 0; i < classpathEntries.length; i++) {
			String entry = classpathEntries[i] == null ? "" : classpathEntries[i].trim();
			if (entry.isEmpty()) {
				continue;
			}

			String fileName = new File(entry).getName();
			if ("log4j-api-2.8.1.jar".equalsIgnoreCase(fileName) || "log4j-core-2.8.1.jar".equalsIgnoreCase(fileName)) {
				Logger.log("Skipping vanilla Log4j entry for modern Forge: " + entry);
				continue;
			}

			String libraryKey = resolveLibraryCoordinateKey(entry);
			Integer lastIndex = libraryKey == null ? null : lastIndexByKey.get(libraryKey);
			if (lastIndex != null && lastIndex.intValue() != i && isPotentiallyConflictingModernForgeLibrary(libraryKey)) {
				Logger.log("Skipping duplicated modern Forge library entry: " + entry);
				continue;
			}

			filtered.add(entry);
		}

		return String.join(File.pathSeparator, filtered);
	}

	private boolean isPotentiallyConflictingModernForgeLibrary(String libraryKey) {
		return libraryKey.startsWith("org.apache.logging.log4j:")
				|| libraryKey.equals("net.sf.jopt-simple:jopt-simple")
				|| libraryKey.equals("com.google.guava:guava")
				|| libraryKey.equals("commons-io:commons-io")
				|| libraryKey.equals("org.ow2.asm:asm")
				|| libraryKey.equals("org.ow2.asm:asm-analysis")
				|| libraryKey.equals("org.ow2.asm:asm-commons")
				|| libraryKey.equals("org.ow2.asm:asm-tree")
				|| libraryKey.equals("org.ow2.asm:asm-util");
	}

	private String resolveLibraryCoordinateKey(String path) {
		if (path == null || path.trim().isEmpty()) {
			return null;
		}

		String normalized = path.replace('\\', '/');
		int libsIndex = normalized.indexOf("/libraries/");
		if (libsIndex < 0) {
			return null;
		}

		String relative = normalized.substring(libsIndex + "/libraries/".length());
		String[] parts = relative.split("/");
		if (parts.length < 4) {
			return null;
		}

		String artifact = parts[parts.length - 3];
		String version = parts[parts.length - 2];
		if (artifact == null || artifact.trim().isEmpty() || version == null || version.trim().isEmpty()) {
			return null;
		}

		StringBuilder group = new StringBuilder();
		for (int i = 0; i < parts.length - 3; i++) {
			if (group.length() > 0) {
				group.append('.');
			}
			group.append(parts[i]);
		}

		if (group.length() == 0) {
			return null;
		}
		return group + ":" + artifact;
	}

	private List<String> getVersionJvmArguments() {
		if (!shouldUseVersionJvmArguments()
				|| this.engine == null
				|| this.engine.getMinecraftVersion() == null
				|| this.engine.getMinecraftVersion().getArguments() == null
				|| this.engine.getMinecraftVersion().getArguments().get(ArgumentType.JVM) == null) {
			return Collections.emptyList();
		}

		List<String> jvmArgs = new ArrayList<String>();
		for (Argument argument : this.engine.getMinecraftVersion().getArguments().get(ArgumentType.JVM)) {
			if (argument == null || !argument.appliesToCurrentEnvironment() || argument.getValues() == null) {
				continue;
			}
			for (String rawValue : argument.getValues()) {
				String resolved = resolveVersionJvmArgument(rawValue);
				if (shouldSkipManagedVersionJvmArgument(rawValue, resolved)) {
					continue;
				}
				jvmArgs.add(resolved);
			}
		}
		return jvmArgs;
	}

	private String resolveVersionJvmArgument(String value) {
		if (value == null) {
			return "";
		}
		String resolved = value
				.replace("${library_directory}", this.engine.getGameFolder().getLibsDir().getAbsolutePath())
				.replace("${classpath_separator}", System.getProperty("path.separator"))
				.replace("${natives_directory}", this.resolveRuntimeNativesDirectory().getAbsolutePath())
				.replace("${launcher_name}", this.engine.getLauncherPreferences() != null ? safe(this.engine.getLauncherPreferences().getName()) : "")
				.replace("${launcher_version}", "1.0");
		resolved = replaceLaunchPlaceholders(resolved);
		if (resolved.startsWith("-DignoreList=")) {
			resolved = "-DignoreList=" + expandIgnoreList(resolved.substring("-DignoreList=".length()));
		}
		return resolved;
	}

	private boolean shouldSkipManagedVersionJvmArgument(String rawValue, String resolvedValue) {
		if (resolvedValue == null || resolvedValue.trim().isEmpty()) {
			return true;
		}
		if ("-cp".equals(rawValue) || "${classpath}".equals(rawValue)) {
			return true;
		}
		return resolvedValue.startsWith("-Djava.library.path=")
				|| resolvedValue.startsWith("-Djna.tmpdir=")
				|| resolvedValue.startsWith("-Dorg.lwjgl.system.SharedLibraryExtractPath=")
				|| resolvedValue.startsWith("-Dio.netty.native.workdir=")
				|| resolvedValue.startsWith("-XX:HeapDumpPath=");
	}

	private boolean isAcceptableModernForgeJava(String javaBinary) {
		JavaBinaryVersion version = readJavaBinaryVersion(javaBinary);
		if (version == null) {
			return false;
		}
		if (requiresJava21ForLaunch()) {
			return version.major >= 21;
		}
		if (version.major >= 9) {
			return true;
		}
		if (version.major == 8) {
			return version.update >= 101;
		}
		return false;
	}

	private boolean isAcceptableLaunchJava(String javaBinary) {
		if (javaBinary == null || javaBinary.trim().isEmpty()) {
			return false;
		}
		JavaBinaryVersion version = readJavaBinaryVersion(javaBinary);
		if (version == null) {
			return false;
		}
		int requiredMajor = resolveConfiguredJavaMajorVersion();
		if (requiredMajor > 0) {
			return version.major >= requiredMajor;
		}
		if (requiresUpdatedJava8ForLaunch()) {
			return isAcceptableModernForgeJava(javaBinary);
		}
		return true;
	}

	private boolean requiresUpdatedJava8ForLaunch() {
		if (requiresJava21ForLaunch()) {
			return true;
		}
		String versionId = resolveBaseMinecraftVersionId();
		if (versionId == null || versionId.trim().isEmpty()) {
			return false;
		}
		return versionId.matches("1\\.(13|14|15|16)(\\.\\d+)?");
	}

	private boolean requiresJava21ForLaunch() {
		return resolveConfiguredJavaMajorVersion() >= 21;
	}

	private int resolveConfiguredJavaMajorVersion() {
		if (this.engine != null && this.engine.getMinecraftVersion() != null
				&& this.engine.getMinecraftVersion().getJavaVersion() != null
				&& this.engine.getMinecraftVersion().getJavaVersion().getMajorVersion() > 0) {
			return this.engine.getMinecraftVersion().getJavaVersion().getMajorVersion();
		}
		if (this.engine != null && this.engine.getGameUpdater() != null
				&& this.engine.getGameUpdater().getLocalVersion() != null
				&& this.engine.getGameUpdater().getLocalVersion().getJavaVersion() != null
				&& this.engine.getGameUpdater().getLocalVersion().getJavaVersion().getMajorVersion() > 0) {
			return this.engine.getGameUpdater().getLocalVersion().getJavaVersion().getMajorVersion();
		}

		String baseVersion = resolveBaseMinecraftVersionId();
		if (isMinecraftVersionAtLeast(baseVersion, 1, 21)) {
			return 21;
		}
		return 0;
	}

	private boolean isMinecraftVersionAtLeast(String versionId, int major, int minor) {
		if (versionId == null || versionId.trim().isEmpty()) {
			return false;
		}

		String[] parts = versionId.trim().split("\\.");
		if (parts.length < 2) {
			return false;
		}

		try {
			int currentMajor = Integer.parseInt(parts[0]);
			int currentMinor = Integer.parseInt(parts[1]);
			if (currentMajor != major) {
				return currentMajor > major;
			}
			return currentMinor >= minor;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	private String resolveCurrentVersionId() {
		if (this.engine != null && this.engine.getMinecraftVersion() != null && this.engine.getMinecraftVersion().getId() != null) {
			return this.engine.getMinecraftVersion().getId();
		}
		if (this.engine != null && this.engine.getGameUpdater() != null && this.engine.getGameUpdater().getLocalVersion() != null
				&& this.engine.getGameUpdater().getLocalVersion().getId() != null) {
			return this.engine.getGameUpdater().getLocalVersion().getId();
		}
		return null;
	}

	private String resolveBaseMinecraftVersionId() {
		String inheritedVersion = resolveInheritedMinecraftVersionId();
		if (inheritedVersion != null && !inheritedVersion.trim().isEmpty()) {
			return inheritedVersion;
		}

		String currentVersion = resolveCurrentVersionId();
		String extractedVersion = extractMinecraftVersionToken(currentVersion);
		if (extractedVersion != null && !extractedVersion.trim().isEmpty()) {
			return extractedVersion;
		}

		return currentVersion;
	}

	private File resolveRuntimeGameDirectory() {
		return GameProfilePaths.resolveRuntimeGameDirectory(this.engine);
	}

	private File resolveSharedProfileDirectory() {
		File basePlayDir = this.engine != null && this.engine.getGameFolder() != null
				? this.engine.getGameFolder().getPlayDir()
				: null;
		if (basePlayDir == null) {
			return new File(".");
		}

		File sharedDir = new File(basePlayDir, "_shared-userdata");
		sharedDir.mkdirs();
		return sharedDir;
	}

	private File resolveRuntimeNativesDirectory() {
		File baseNativesDir = this.engine != null && this.engine.getGameFolder() != null
				? this.engine.getGameFolder().getNativesDir()
				: null;
		if (baseNativesDir == null) {
			return new File(".");
		}

		File runtimeDir = new File(baseNativesDir, GameProfilePaths.resolveProfileDirectoryName(this.engine));
		runtimeDir.mkdirs();
		return runtimeDir;
	}

	private void syncSharedProfileData() {
		try {
			File runtimeDir = resolveRuntimeGameDirectory();
			File sharedDir = resolveSharedProfileDirectory();
			if (runtimeDir == null || sharedDir == null) {
				return;
			}

			for (String entry : SHARED_PROFILE_ENTRIES) {
				mergeSharedPath(new File(sharedDir, entry), new File(runtimeDir, entry));
			}
		} catch (IOException e) {
			Logger.log("Couldn't sync shared profile data!");
			e.printStackTrace();
		}
	}

	private void mergeSharedPath(File sharedPath, File runtimePath) throws IOException {
		boolean sharedExists = sharedPath.exists();
		boolean runtimeExists = runtimePath.exists();

		if (!sharedExists && !runtimeExists) {
			return;
		}
		if (!sharedExists) {
			copyPath(runtimePath, sharedPath);
			return;
		}
		if (!runtimeExists) {
			copyPath(sharedPath, runtimePath);
			return;
		}

		if (sharedPath.isDirectory() || runtimePath.isDirectory()) {
			if (!sharedPath.isDirectory() || !runtimePath.isDirectory()) {
				File preferred = sharedPath.lastModified() >= runtimePath.lastModified() ? sharedPath : runtimePath;
				File target = preferred == sharedPath ? runtimePath : sharedPath;
				replacePath(preferred, target);
				return;
			}
			mergeDirectories(sharedPath, runtimePath);
			return;
		}

		if (sharedPath.lastModified() > runtimePath.lastModified()) {
			copyFile(sharedPath, runtimePath);
		} else if (runtimePath.lastModified() > sharedPath.lastModified()) {
			copyFile(runtimePath, sharedPath);
		}
	}

	private void mergeDirectories(File sharedDir, File runtimeDir) throws IOException {
		sharedDir.mkdirs();
		runtimeDir.mkdirs();

		Set<String> children = new LinkedHashSet<String>();
		File[] sharedChildren = sharedDir.listFiles();
		File[] runtimeChildren = runtimeDir.listFiles();
		if (sharedChildren != null) {
			for (File child : sharedChildren) {
				children.add(child.getName());
			}
		}
		if (runtimeChildren != null) {
			for (File child : runtimeChildren) {
				children.add(child.getName());
			}
		}

		for (String childName : children) {
			mergeSharedPath(new File(sharedDir, childName), new File(runtimeDir, childName));
		}
	}

	private void replacePath(File source, File target) throws IOException {
		deleteRecursively(target);
		copyPath(source, target);
	}

	private void copyPath(File source, File target) throws IOException {
		if (source == null || !source.exists()) {
			return;
		}
		if (source.isDirectory()) {
			target.mkdirs();
			File[] children = source.listFiles();
			if (children != null) {
				for (File child : children) {
					copyPath(child, new File(target, child.getName()));
				}
			}
			return;
		}
		copyFile(source, target);
	}

	private void copyFile(File source, File target) throws IOException {
		File parent = target.getParentFile();
		if (parent != null) {
			parent.mkdirs();
		}
		java.nio.file.Files.copy(source.toPath(), target.toPath(),
				java.nio.file.StandardCopyOption.REPLACE_EXISTING,
				java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
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
		if (!file.delete() && file.exists()) {
			throw new IOException("Unable to delete " + file.getAbsolutePath());
		}
	}

	private String resolveInheritedMinecraftVersionId() {
		if (this.engine != null && this.engine.getMinecraftVersion() != null
				&& this.engine.getMinecraftVersion().getInheritsFrom() != null
				&& !this.engine.getMinecraftVersion().getInheritsFrom().trim().isEmpty()) {
			return this.engine.getMinecraftVersion().getInheritsFrom().trim();
		}
		if (this.engine != null && this.engine.getGameUpdater() != null
				&& this.engine.getGameUpdater().getLocalVersion() != null
				&& this.engine.getGameUpdater().getLocalVersion().getInheritsFrom() != null
				&& !this.engine.getGameUpdater().getLocalVersion().getInheritsFrom().trim().isEmpty()) {
			return this.engine.getGameUpdater().getLocalVersion().getInheritsFrom().trim();
		}
		return null;
	}

	private String extractMinecraftVersionToken(String rawValue) {
		if (rawValue == null || rawValue.trim().isEmpty()) {
			return null;
		}

		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(1\\.\\d+(?:\\.\\d+)?)").matcher(rawValue);
		String lastMatch = null;
		while (matcher.find()) {
			lastMatch = matcher.group(1);
		}
		return lastMatch;
	}

	private String describeJavaBinary(String javaBinary) {
		JavaBinaryVersion version = readJavaBinaryVersion(javaBinary);
		if (version == null) {
			return javaBinary + " [version=unknown]";
		}
		return javaBinary + " [version=" + version.raw + "]";
	}

	private String stripWrappingQuotes(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
			return trimmed.substring(1, trimmed.length() - 1);
		}
		return trimmed;
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
		} catch (Exception ignored) {
			return null;
		}
	}

	private static final class JavaBinaryVersion {
		private final int major;
		private final int update;
		private final String raw;

		private JavaBinaryVersion(int major, int update, String raw) {
			this.major = major;
			this.update = update;
			this.raw = raw;
		}

		private static JavaBinaryVersion parse(String rawLine) {
			if (rawLine == null) {
				return null;
			}
			String raw = rawLine;
			int firstQuote = rawLine.indexOf('"');
			int secondQuote = rawLine.indexOf('"', firstQuote + 1);
			String version = (firstQuote >= 0 && secondQuote > firstQuote)
					? rawLine.substring(firstQuote + 1, secondQuote)
					: rawLine;

			if (version.startsWith("1.")) {
				String[] parts = version.split("[_\\.]");
				int major = parts.length > 1 ? safeParse(parts[1]) : 8;
				int update = parts.length > 3 ? safeParse(parts[3]) : 0;
				return new JavaBinaryVersion(major, update, raw);
			}

			String[] parts = version.split("[._-]");
			int major = parts.length > 0 ? safeParse(parts[0]) : 0;
			return new JavaBinaryVersion(major, 0, raw);
		}

		private static int safeParse(String value) {
			try {
				return Integer.parseInt(value);
			} catch (Exception ignored) {
				return 0;
			}
		}
	}

	private List<String> getForgeJVMArguments() {
		if (this.engine == null || this.engine.getGameForge() == null || this.engine.getGameForge().getArguments() == null
				|| this.engine.getGameForge().getArguments().getJvm() == null) {
			return Collections.emptyList();
		}
		List<String> forgeArgs = this.engine.getGameForge().getArguments().getJvm();
		List<String> jvmArgs = new ArrayList<>();
		for (String arg : forgeArgs) {
			String resolved = arg.replace("${version_name}", "minecraft")
					.replace("${library_directory}", this.engine.getGameFolder().getLibsDir().getAbsolutePath())
					.replace("${classpath_separator}", System.getProperty("path.separator"));
			resolved = replaceLaunchPlaceholders(resolved);
			if (resolved.startsWith("-DignoreList=")) {
				resolved = "-DignoreList=" + expandIgnoreList(resolved.substring("-DignoreList=".length()));
			}
			jvmArgs.add(resolved);
		}
		return jvmArgs;
	}

	private String expandIgnoreList(String rawValue) {
		LinkedHashSet<String> values = new LinkedHashSet<String>();
		if (rawValue != null && !rawValue.trim().isEmpty()) {
			for (String item : rawValue.split(",")) {
				String trimmed = item == null ? "" : item.trim();
				if (!trimmed.isEmpty()) {
					values.add(trimmed);
					if (trimmed.equals("asm")) {
						values.add("ow2-asm");
					} else if (trimmed.startsWith("asm-")) {
						values.add("ow2-" + trimmed);
					} else if (trimmed.equals("securejarhandler")) {
						values.add("securemodules");
					}
				}
			}
		}
		return String.join(",", values);
	}

	private String filterModulePathEntriesFromClasspath(String classpath, List<String> forgeJvmArguments) {
		if (classpath == null || classpath.trim().isEmpty()) {
			return classpath;
		}

		Set<String> modulePathEntries = extractModulePathEntries(forgeJvmArguments);
		if (modulePathEntries.isEmpty()) {
			return classpath;
		}

		String separatorRegex = java.util.regex.Pattern.quote(File.pathSeparator);
		String[] classpathEntries = classpath.split(separatorRegex);
		List<String> filtered = new ArrayList<String>();

		for (String entry : classpathEntries) {
			String trimmed = entry == null ? "" : entry.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			String normalized = normalizePath(trimmed);
			if (modulePathEntries.contains(normalized)) {
				Logger.log("Skipping duplicated module path entry from classpath: " + trimmed);
				continue;
			}
			filtered.add(trimmed);
		}

		return String.join(File.pathSeparator, filtered);
	}


	private boolean isLegacyBootstrapForgeStyle() {
		return ForgeLaunchResolver.resolveMode(engine) == ForgeLaunchResolver.Mode.LEGACY_BOOTSTRAP_LAUNCHER;
	}

	private boolean isForgeWrapperStyle() {
		return ForgeLaunchResolver.resolveMode(engine) == ForgeLaunchResolver.Mode.FORGE_WRAPPER;
	}

	private void addForgeWrapperProperties(List<String> commands) {
		if (engine == null || engine.getGameUpdater() == null) {
			return;
		}

		File installer = engine.getGameUpdater().getForgeInstallerJar();
		File minecraftJar = engine.getGameUpdater().getClientJarFile();
		if (installer != null && installer.exists()) {
			commands.add("-Dforgewrapper.installer=" + installer.getAbsolutePath());
		}
		commands.add("-Dforgewrapper.librariesDir=" + engine.getGameFolder().getLibsDir().getAbsolutePath());
		if (minecraftJar != null && minecraftJar.exists()) {
			commands.add("-Dforgewrapper.minecraft=" + minecraftJar.getAbsolutePath());
		}
	}

	private String filterLegacyForgeVersionJarFromClasspath(String classpath) {
		if (classpath == null || classpath.trim().isEmpty() || engine == null || engine.getMinecraftVersion() == null) {
			return classpath;
		}

		Set<String> expectedFileNames = new java.util.HashSet<String>();
		String versionId = engine.getMinecraftVersion().getId();
		if (versionId != null && !versionId.trim().isEmpty()) {
			expectedFileNames.add(versionId + ".jar");
		}
		if (engine.getGameUpdater() != null) {
			File clientJar = engine.getGameUpdater().getClientJarFile();
			if (clientJar != null) {
				String clientJarName = clientJar.getName();
				if (clientJarName != null && !clientJarName.trim().isEmpty()) {
					expectedFileNames.add(clientJarName);
				}
			}
		}
		if (expectedFileNames.isEmpty()) {
			return classpath;
		}

		String separatorRegex = java.util.regex.Pattern.quote(File.pathSeparator);
		String[] classpathEntries = classpath.split(separatorRegex);
		List<String> filtered = new ArrayList<String>();

		for (String entry : classpathEntries) {
			String trimmed = entry == null ? "" : entry.trim();
			if (trimmed.isEmpty()) {
				continue;
			}

			String fileName = new File(trimmed).getName();
			if (matchesLegacyMinecraftJar(fileName, expectedFileNames)) {
				Logger.log("Skipping legacy Forge duplicated Minecraft version jar from classpath: " + trimmed);
				continue;
			}

			filtered.add(trimmed);
		}

		return String.join(File.pathSeparator, filtered);
	}

	private boolean matchesLegacyMinecraftJar(String fileName, Set<String> expectedFileNames) {
		if (fileName == null || expectedFileNames == null || expectedFileNames.isEmpty()) {
			return false;
		}

		for (String expected : expectedFileNames) {
			if (expected != null && expected.equalsIgnoreCase(fileName)) {
				return true;
			}
		}

		return false;
	}

	private String filterForgeWrapperClasspath(String classpath) {
		if (classpath == null || classpath.trim().isEmpty() || engine == null || engine.getGameUpdater() == null) {
			return classpath;
		}

		String forgeFullVersion = engine.getGameUpdater().getForgeFullVersion();
		if (forgeFullVersion == null || forgeFullVersion.trim().isEmpty()) {
			return classpath;
		}

		String separatorRegex = java.util.regex.Pattern.quote(File.pathSeparator);
		String[] classpathEntries = classpath.split(separatorRegex);
		List<String> filtered = new ArrayList<String>();
		String directForgeJarName = "forge-" + forgeFullVersion + ".jar";
		String clientForgeJarName = "forge-" + forgeFullVersion + "-client.jar";

		for (String entry : classpathEntries) {
			String trimmed = entry == null ? "" : entry.trim();
			if (trimmed.isEmpty()) {
				continue;
			}

			String fileName = new File(trimmed).getName();
			if (directForgeJarName.equalsIgnoreCase(fileName) || clientForgeJarName.equalsIgnoreCase(fileName)) {
				Logger.log("Skipping direct Forge artifact from ForgeWrapper classpath: " + trimmed);
				continue;
			}
			filtered.add(trimmed);
		}

		return String.join(File.pathSeparator, filtered);
	}

	private Set<String> extractModulePathEntries(List<String> forgeJvmArguments) {
		Set<String> entries = new LinkedHashSet<String>();
		if (forgeJvmArguments == null || forgeJvmArguments.isEmpty()) {
			return entries;
		}

		for (int i = 0; i < forgeJvmArguments.size(); i++) {
			String arg = forgeJvmArguments.get(i);
			if ("-p".equals(arg) || "--module-path".equals(arg)) {
				if (i + 1 < forgeJvmArguments.size()) {
					addPathListEntries(entries, forgeJvmArguments.get(i + 1));
				}
			} else if (arg != null && arg.startsWith("-p")) {
				String value = arg.substring(2).trim();
				if (!value.isEmpty()) {
					addPathListEntries(entries, value);
				}
			} else if (arg != null && arg.startsWith("--module-path=")) {
				addPathListEntries(entries, arg.substring("--module-path=".length()));
			}
		}
		return entries;
	}

	private void addPathListEntries(Set<String> entries, String rawPathList) {
		if (rawPathList == null || rawPathList.trim().isEmpty()) {
			return;
		}
		String separatorRegex = java.util.regex.Pattern.quote(File.pathSeparator);
		String[] paths = rawPathList.split(separatorRegex);
		for (String path : paths) {
			String trimmed = path == null ? "" : path.trim();
			if (!trimmed.isEmpty()) {
				entries.add(normalizePath(trimmed));
			}
		}
	}

	private String normalizePath(String path) {
		try {
			return new File(path).getCanonicalPath();
		} catch (IOException ignored) {
			return new File(path).getAbsolutePath();
		}
	}

	/**
	 * Get forge arguments (If gameStyle != Vanilla or Vanilla_Plus)
	 * @return A List<String> of specifics arguments
	 */
	private List<String> getForgeArguments() {
		List<String> rawArgs = engine.getGameForge().getArguments().getGame();
		List<String> resolved = new ArrayList<String>();
		for (String arg : rawArgs) {
			String value = replaceLaunchPlaceholders(arg);
			if (value.contains("${")) {
				continue;
			}
			resolved.add(value);
		}
		return resolved;
	}

	private String replaceLaunchPlaceholders(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}

		Map<String, String> values = new HashMap<String, String>();
		values.put("auth_player_name", this.session != null ? safe(this.session.getUsername()) : "");
		values.put("auth_uuid", this.session != null ? safe(this.session.getUuid()) : "");
		values.put("auth_access_token", this.session != null ? safe(this.session.getToken()) : "");
		values.put("version_name", this.engine != null && this.engine.getMinecraftVersion() != null ? safe(this.engine.getMinecraftVersion().getId()) : "");
		values.put("version_type", "release");
		values.put("game_directory", this.engine != null ? safe(resolveRuntimeGameDirectory().getAbsolutePath()) : "");
		values.put("assets_root", this.engine != null ? safe(this.engine.getGameFolder().getAssetsDir().getAbsolutePath()) : "");
		values.put("assets_index_name", this.engine != null && this.engine.getMinecraftVersion() != null ? safe(this.engine.getMinecraftVersion().getAssets()) : "");
		values.put("user_type", "legacy");
		values.put("user_properties", "{}");

		String reflectedClientId = invokeSessionStringGetter("getClientId");
		String reflectedXuid = invokeSessionStringGetter("getXuid");
		values.put("clientid", reflectedClientId);
		values.put("client_id", reflectedClientId);
		values.put("auth_xuid", reflectedXuid);
		values.put("xuid", reflectedXuid);
		values.put("quickPlayMultiplayer", buildQuickPlayMultiplayerValue());
		values.put("quickPlaySingleplayer", "");
		values.put("quickPlayRealms", "");
		values.put("quickPlayPath", "");
		values.put("resolution_width", getConfiguredWidth());
		values.put("resolution_height", getConfiguredHeight());

		String resolved = value;
		for (Map.Entry<String, String> entry : values.entrySet()) {
			resolved = resolved.replace("${" + entry.getKey() + "}", safe(entry.getValue()));
		}
		return resolved;
	}

	private void pruneMissingValueOption(List<String> commands, String optionName) {
		if (commands == null || optionName == null || optionName.trim().isEmpty()) {
			return;
		}
		for (int i = 0; i < commands.size(); i++) {
			String current = commands.get(i);
			if (!optionName.equals(current)) {
				continue;
			}
			if (i + 1 >= commands.size() || commands.get(i + 1).startsWith("--")) {
				commands.remove(i);
				i--;
			}
		}
	}

	private boolean supportsQuickPlayMultiplayer() {
		if (this.engine == null || this.engine.getMinecraftVersion() == null
				|| this.engine.getMinecraftVersion().getArguments() == null
				|| this.engine.getMinecraftVersion().getArguments().get(ArgumentType.GAME) == null) {
			return false;
		}

		for (Argument argument : this.engine.getMinecraftVersion().getArguments().get(ArgumentType.GAME)) {
			if (argument == null || !argument.appliesToCurrentEnvironment() || argument.getValues() == null) {
				continue;
			}
			for (String value : argument.getValues()) {
				if ("--quickPlayMultiplayer".equals(value) || (value != null && value.contains("${quickPlayMultiplayer}"))) {
					return true;
				}
			}
		}
		return false;
	}

	private List<String> filterModernMinecraftArguments(String[] arguments) {
		List<String> filteredArguments = new ArrayList<String>();
		if (arguments == null || arguments.length == 0) {
			return filteredArguments;
		}

		for (int i = 0; i < arguments.length; i++) {
			String argument = arguments[i];
			if (argument == null || argument.trim().isEmpty()) {
				continue;
			}
			if ("--demo".equals(argument)) {
				continue;
			}
			if ("--width".equals(argument) || "--height".equals(argument)) {
				i++;
				continue;
			}
			filteredArguments.add(argument);
		}

		return filteredArguments;
	}

	private String buildQuickPlayMultiplayerValue() {
		String host = getConnectHost();
		String port = getConnectPort();
		if (host.isEmpty()) {
			return "";
		}
		if (port.isEmpty() || "25565".equals(port)) {
			return host;
		}
		return host + ":" + port;
	}

	private boolean shouldUseLegacyOptiFineArgumentCompatibility() {
		return this.engine != null
				&& this.engine.getGameStyle() != null
				&& this.engine.getGameStyle().equals(GameStyle.OPTIFINE)
				&& !isMinecraftVersionAtLeast(resolveBaseMinecraftVersionId(), 1, 16);
	}

	private String getConnectHost() {
		if (this.engine == null || this.engine.getGameConnect() == null) {
			return "";
		}

		String rawHost = safe(this.engine.getGameConnect().getIp()).trim();
		String explicitPort = safe(String.valueOf(this.engine.getGameConnect().getPort())).trim();
		if (!rawHost.isEmpty() && explicitPort.isEmpty()) {
			int separatorIndex = rawHost.lastIndexOf(':');
			if (separatorIndex > 0 && separatorIndex < rawHost.length() - 1 && rawHost.indexOf(':') == separatorIndex) {
				return rawHost.substring(0, separatorIndex).trim();
			}
		}
		return rawHost;
	}

	private String getConnectPort() {
		if (this.engine == null || this.engine.getGameConnect() == null) {
			return "";
		}

		String explicitPort = safe(String.valueOf(this.engine.getGameConnect().getPort())).trim();
		if (!explicitPort.isEmpty()) {
			return explicitPort;
		}

		String rawHost = safe(this.engine.getGameConnect().getIp()).trim();
		int separatorIndex = rawHost.lastIndexOf(':');
		if (separatorIndex > 0 && separatorIndex < rawHost.length() - 1 && rawHost.indexOf(':') == separatorIndex) {
			return rawHost.substring(separatorIndex + 1).trim();
		}
		return "";
	}

	private String getConfiguredWidth() {
		if (this.engine == null || this.engine.getGameSize() == null) {
			return "";
		}
		return String.valueOf(this.engine.getGameSize().getWidth());
	}

	private String getConfiguredHeight() {
		if (this.engine == null || this.engine.getGameSize() == null) {
			return "";
		}
		return String.valueOf(this.engine.getGameSize().getHeight());
	}

	private String invokeSessionStringGetter(String methodName) {
		if (this.session == null || methodName == null || methodName.trim().isEmpty()) {
			return "";
		}
		try {
			java.lang.reflect.Method method = this.session.getClass().getMethod(methodName);
			Object value = method.invoke(this.session);
			return value == null ? "" : String.valueOf(value);
		} catch (Exception ignored) {
			return "";
		}
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	/**
	 * Get minecraft launch arguments for old versions of Minecraft
	 * @return a String[] with multiples arguments
	 */
	@SuppressWarnings("deprecation")
	private String[] getArgumentsOlder() {
		final Map<String, String> map = new HashMap<String, String>();
		final StrSubstitutor substitutor = new StrSubstitutor(map);
		final String[] split = engine.getMinecraftVersion().getMinecraftArguments().split(" ");
		map.put("auth_player_name", this.session.getUsername());
		map.put("auth_uuid", this.session.getUuid());
		map.put("auth_access_token", this.session.getToken());
		map.put("user_type", "legacy");
		map.put("version_name", this.engine.getMinecraftVersion().getId());
		map.put("version_type", "release");
		map.put("game_directory", this.resolveRuntimeGameDirectory().getAbsolutePath());
		map.put("assets_root", this.engine.getGameFolder().getAssetsDir().getAbsolutePath());
		map.put("assets_index_name", this.engine.getMinecraftVersion().getAssets());
		map.put("user_properties", "{}");

		for (int i = 0; i < split.length; i++)
			split[i] = replaceLaunchPlaceholders(substitutor.replace(split[i]));

		return split;
	}

	/**
	 * Get minecraft launch arguments for new versions of Minecraft
	 * @param args The arguments from json as a List
	 * @return a String[] with multiples arguments
	 */
	@SuppressWarnings("deprecation")
	private String[] getArgumentsNewer(List<Argument> args) {
		if (args == null || args.isEmpty()) {
			return new String[0];
		}
		final Map<String, String> map = new HashMap<String, String>();
		final StrSubstitutor substitutor = new StrSubstitutor(map);
		final List<String> collectedArguments = new ArrayList<String>();
		for (Argument argument : args) {
			if (argument == null || !argument.appliesToCurrentEnvironment() || argument.getValues() == null) {
				continue;
			}
			for (String value : argument.getValues()) {
				if (value != null && !value.trim().isEmpty()) {
					collectedArguments.add(value);
				}
			}
		}
		final String[] split = collectedArguments.toArray(new String[0]);
		map.put("auth_player_name", this.session.getUsername());
		map.put("auth_uuid", this.session.getUuid());
		map.put("auth_access_token", this.session.getToken());
		map.put("user_type", "legacy");
		map.put("version_name", this.engine.getMinecraftVersion().getId());
		map.put("version_type", "release");
		map.put("game_directory", this.resolveRuntimeGameDirectory().getAbsolutePath());
		map.put("assets_root", this.engine.getGameFolder().getAssetsDir().getAbsolutePath());
		map.put("assets_index_name", this.engine.getMinecraftVersion().getAssets());
		map.put("user_properties", "{}");
		map.put("quickPlayMultiplayer", buildQuickPlayMultiplayerValue());
		map.put("quickPlaySingleplayer", "");
		map.put("quickPlayRealms", "");
		map.put("quickPlayPath", "");
		map.put("resolution_width", getConfiguredWidth());
		map.put("resolution_height", getConfiguredHeight());

		for (int i = 0; i < split.length; i++)
			split[i] = replaceLaunchPlaceholders(substitutor.replace(split[i]));

		return split;
	}

	private List<String> filterLegacyJsonArguments(String[] arguments) {
		List<String> filteredArguments = new ArrayList<String>();
		if (arguments == null || arguments.length == 0) {
			return filteredArguments;
		}

		StringBuilder sb = new StringBuilder();
		for (String argument : arguments) {
			if (argument != null && !argument.trim().isEmpty()) {
				sb.append(argument).append(" ");
			}
		}

		String[] splitArguments = sb.toString()
				.replace("--demo", "")
				.replace("--width", "")
				.replace("--height", "")
				.split(" ");

		for (String splitArgument : splitArguments) {
			if (splitArgument != null && !splitArgument.trim().isEmpty()) {
				filteredArguments.add(splitArgument);
			}
		}

		return filteredArguments;
	}

	@SuppressWarnings("deprecation")
	private String[] getArgumentsNewerLegacyCompatible(List<Argument> args) {
		if (args == null || args.isEmpty()) {
			return new String[0];
		}

		final Map<String, String> map = new HashMap<String, String>();
		final StrSubstitutor substitutor = new StrSubstitutor(map);
		final List<String> collectedArguments = new ArrayList<String>();
		for (Argument argument : args) {
			if (argument == null || !argument.appliesToCurrentEnvironment()) {
				continue;
			}
			String value = argument.getArguments();
			if (value != null && !value.trim().isEmpty() && !"null".equals(value)) {
				collectedArguments.add(value);
			}
		}

		final String[] split = collectedArguments.toArray(new String[0]);
		map.put("auth_player_name", this.session.getUsername());
		map.put("auth_uuid", this.session.getUuid());
		map.put("auth_access_token", this.session.getToken());
		map.put("user_type", "legacy");
		map.put("version_name", this.engine.getMinecraftVersion().getId());
		map.put("version_type", "release");
		map.put("game_directory", this.resolveRuntimeGameDirectory().getAbsolutePath());
		map.put("assets_root", this.engine.getGameFolder().getAssetsDir().getAbsolutePath());
		map.put("assets_index_name", this.engine.getMinecraftVersion().getAssets());
		map.put("user_properties", "{}");

		for (int i = 0; i < split.length; i++) {
			split[i] = replaceLaunchPlaceholders(substitutor.replace(split[i]));
		}

		return split;
	}

	/**
	 * Unpack natives before launching the game
	 */
	private void unpackNatives() {
		try {
			FileUtil.unpackNatives(resolveRuntimeNativesDirectory(), engine);
		} catch (IOException e) {
			Logger.log("Couldn't unpack natives!");
			e.printStackTrace();
			return;
		}
	}

	/**
	 * Delete old natives
	 */
	private void deleteFakeNatives() {
		try {
			FileUtil.deleteFakeNatives(resolveRuntimeNativesDirectory(), engine);
		} catch (IOException e) {
			Logger.log("Couldn't delete natives!");
			e.printStackTrace();
			return;
		}
	}

	/**
	 * Hide the access token inside the console
	 * @param arguments The Token
	 * @return A List<String> of the token hidden
	 */
	public static List<String> hideAccessToken(String[] arguments) {
        final ArrayList<String> output = new ArrayList<String>();
        if (arguments == null) {
            return output;
        }
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0 && Objects.equals(arguments[i-1], "--accessToken")) {
                output.add("????????");
            } else {
                output.add(arguments[i]);
            }
        }
        return output;
    }

	public static List<String> hideAccessToken(List<String> arguments) {
        final ArrayList<String> output = new ArrayList<String>();
        if (arguments == null) {
            return output;
        }
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0 && Objects.equals(arguments.get(i - 1), "--accessToken")) {
                output.add("????????");
            } else {
                output.add(arguments.get(i));
            }
        }
        return output;
    }

}
