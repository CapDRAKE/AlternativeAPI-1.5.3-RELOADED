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
		List<String> forgeJvmArguments = Collections.emptyList();

		commands.add(resolveJavaBinaryForLaunch());

        commands.add("-XX:-UseAdaptiveSizePolicy");

		if (engine.getJVMArguments() != null) {
			commands.addAll(engine.getJVMArguments().getJVMArguments());
		}

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
			if (!(this.engine.getMinecraftVersion().getJavaVersion() != null)) {
				commands.add("-XX:+UseConcMarkSweepGC");
			}
			commands.add("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump");
		}

		if (this.engine.isOnline()) {
			if (this.engine.getMinecraftVersion().getJavaVersion() != null) {
				commands.add("-XX:+UnlockExperimentalVMOptions");
				commands.add("-XX:+UseG1GC");
				commands.add("-XX:G1NewSizePercent=20");
				commands.add("-XX:G1ReservePercent=20");
				commands.add("-XX:MaxGCPauseMillis=50");
				commands.add("-XX:G1HeapRegionSize=32M");
			}
		} else {
			if (this.engine.getGameUpdater().getLocalVersion().getJavaVersion() != null) {
				commands.add("-XX:+UnlockExperimentalVMOptions");
				commands.add("-XX:+UseG1GC");
				commands.add("-XX:G1NewSizePercent=20");
				commands.add("-XX:G1ReservePercent=20");
				commands.add("-XX:MaxGCPauseMillis=50");
				commands.add("-XX:G1HeapRegionSize=32M");
			}
		}

		if (shouldInjectMinecraftLoggingConfiguration()) {
			File log4jFile = new File(this.engine.getGameFolder().getLogConfigsDir(), this.engine.getMinecraftVersion().getLogging().getClient().getFile().getId());
			commands.add(this.engine.getMinecraftVersion().getLogging().getClient().getArgument().replace("${path}", log4jFile.getAbsolutePath()));
		}
		String nativesPath = engine.getGameFolder().getNativesDir().getAbsolutePath();
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
			final String[] newerArgumentsString = getArgumentsNewer(argsNewer);

			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < newerArgumentsString.length; i++) {
				sb.append(newerArgumentsString[i]).append(" ");
			}
			String sub = sb.toString().replace("--demo", "").replace("--width", "").replace("--height", "");
			String strcs[] = sub.split(" ");
			List<String> newerList = Arrays.asList(strcs);
			commands.addAll(newerList);
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
			System.out.println(engine.getGameConnect().getIp() + " " + engine.getGameConnect().getPort());
			commands.add("--server");
			commands.add(engine.getGameConnect().getIp());
			commands.add("--port");
			commands.add(String.valueOf(engine.getGameConnect().getPort()));
		}

		/** ----- Tweak Class if required ----- */
		if (engine.getGameStyle().equals(GameStyle.FORGE_1_7_10_OLD) || engine.getGameStyle().equals(GameStyle.FORGE_1_8_TO_1_12_2) || engine.getGameStyle().equals(GameStyle.OPTIFINE)) {
			commands.add("--tweakClass");
			commands.add(engine.getGameStyle().getTweakArgument());
		}

	    /** ----- Filtrage des paramètres quickPlay* ----- */
	    commands.removeIf(arg -> arg.startsWith("--quickPlay"));
	    /** ----- Suppression des arguments vides ----- */
	    commands.removeIf(arg -> arg.trim().isEmpty());

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
		return ForgeLaunchResolver.resolveMainClass(engine);
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
		if (shouldUseAlphaRuntimeForLegacyBootstrapForge()) {
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

	private boolean shouldUseLegacyRuntimeForForge113To116() {
		return engine != null
				&& engine.getGameStyle() != null
				&& engine.getGameStyle().equals(GameStyle.FORGE_1_13_HIGHER)
				&& !isLegacyBootstrapForgeStyle();
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

	private boolean isAcceptableModernForgeJava(String javaBinary) {
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

	private boolean isAcceptableLaunchJava(String javaBinary) {
		if (javaBinary == null || javaBinary.trim().isEmpty()) {
			return false;
		}
		if (requiresUpdatedJava8ForLaunch()) {
			return isAcceptableModernForgeJava(javaBinary);
		}
		return true;
	}

	private boolean requiresUpdatedJava8ForLaunch() {
		String versionId = resolveCurrentVersionId();
		if (versionId == null || versionId.trim().isEmpty()) {
			return false;
		}
		return versionId.matches("1\\.(13|14|15|16)(\\.\\d+)?");
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

		String versionId = engine.getMinecraftVersion().getId();
		if (versionId == null || versionId.trim().isEmpty()) {
			return classpath;
		}

		String expectedFileName = versionId + ".jar";
		String separatorRegex = java.util.regex.Pattern.quote(File.pathSeparator);
		String[] classpathEntries = classpath.split(separatorRegex);
		List<String> filtered = new ArrayList<String>();

		for (String entry : classpathEntries) {
			String trimmed = entry == null ? "" : entry.trim();
			if (trimmed.isEmpty()) {
				continue;
			}

			String fileName = new File(trimmed).getName();
			if (expectedFileName.equalsIgnoreCase(fileName)) {
				Logger.log("Skipping legacy Forge duplicated Minecraft version jar from classpath: " + trimmed);
				continue;
			}

			filtered.add(trimmed);
		}

		return String.join(File.pathSeparator, filtered);
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
		values.put("game_directory", this.engine != null ? safe(this.engine.getGameFolder().getPlayDir().getAbsolutePath()) : "");
		values.put("assets_root", this.engine != null ? safe(this.engine.getGameFolder().getAssetsDir().getAbsolutePath()) : "");
		values.put("assets_index_name", this.engine != null && this.engine.getMinecraftVersion() != null ? safe(this.engine.getMinecraftVersion().getAssets()) : "");
		values.put("user_type", "legacy");
		values.put("user_properties", "{}");

		String reflectedClientId = invokeSessionStringGetter("getClientId");
		String reflectedXuid = invokeSessionStringGetter("getXuid");
		if (!reflectedClientId.isEmpty()) {
			values.put("clientid", reflectedClientId);
			values.put("client_id", reflectedClientId);
		}
		if (!reflectedXuid.isEmpty()) {
			values.put("auth_xuid", reflectedXuid);
			values.put("xuid", reflectedXuid);
		}

		String resolved = value;
		for (Map.Entry<String, String> entry : values.entrySet()) {
			resolved = resolved.replace("${" + entry.getKey() + "}", safe(entry.getValue()));
		}
		return resolved;
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
		map.put("game_directory", this.engine.getGameFolder().getPlayDir().getAbsolutePath());
		map.put("assets_root", this.engine.getGameFolder().getAssetsDir().getAbsolutePath());
		map.put("assets_index_name", this.engine.getMinecraftVersion().getAssets());
		map.put("user_properties", "{}");

		for (int i = 0; i < split.length; i++)
			split[i] = substitutor.replace(split[i]);

		return split;
	}

	/**
	 * Get minecraft launch arguments for new versions of Minecraft
	 * @param args The arguments from json as a List
	 * @return a String[] with multiples arguments
	 */
	@SuppressWarnings("deprecation")
	private String[] getArgumentsNewer(List<Argument> args) {
		final Map<String, String> map = new HashMap<String, String>();
		final StrSubstitutor substitutor = new StrSubstitutor(map);
		final String[] split = new String[args.size()];
		for (int i = 0; i < args.size(); i++) {
				split[i] = args.get(i).getArguments();
		}
		map.put("auth_player_name", this.session.getUsername());
		map.put("auth_uuid", this.session.getUuid());
		map.put("auth_access_token", this.session.getToken());
		map.put("user_type", "legacy");
		map.put("version_name", this.engine.getMinecraftVersion().getId());
		map.put("version_type", "release");
		map.put("game_directory", this.engine.getGameFolder().getPlayDir().getAbsolutePath());
		map.put("assets_root", this.engine.getGameFolder().getAssetsDir().getAbsolutePath());
		map.put("assets_index_name", this.engine.getMinecraftVersion().getAssets());
		map.put("user_properties", "{}");

		for (int i = 0; i < split.length; i++)
			split[i] = substitutor.replace(split[i]);

		return split;
	}

	/**
	 * Unpack natives before launching the game
	 */
	private void unpackNatives() {
		try {
			FileUtil.unpackNatives(engine.getGameFolder().getNativesDir(), engine);
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
			FileUtil.deleteFakeNatives(engine.getGameFolder().getNativesDir(), engine);
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