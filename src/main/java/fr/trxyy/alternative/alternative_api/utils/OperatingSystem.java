package fr.trxyy.alternative.alternative_api.utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import fr.trxyy.alternative.alternative_api.GameEngine;
import fr.trxyy.alternative.alternative_api.minecraft.json.MinecraftLibrary;
import fr.trxyy.alternative.alternative_api.minecraft.json.MinecraftVersion;
import fr.trxyy.alternative.alternative_api.minecraft.utils.Arch;

/**
 * @author Trxyy
 */
public enum OperatingSystem {

	LINUX(new String[] { "linux", "unix" }),
	WINDOWS(new String[] { "win" }), 
	OSX(new String[] { "mac" }),
	SOLARIS(new String[] { "solaris", "sunos" }),
	UNKNOWN(new String[] { "unknown" });

	/**
	 * The OS Name in System Properties
	 */
	public static final String NAME = System.getProperty("os.name");
	/**
	 * The name
	 */
	private final String name;
	/**
	 * The Os Aliases
	 */
	private final String[] aliases;

	/**
	 * The Constructor
	 * @param aliases The os aliases
	 */
	private OperatingSystem(String... aliases) {
		if (aliases == null) {
			throw new NullPointerException();
		}
		this.name = toString().toLowerCase();
		this.aliases = aliases;
	}

	/**
	 * @return The name
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * @return The aliases as a String[]
	 */
	public String[] getAliases() {
		return this.aliases;
	}

	/**
	 * @return If is supported OS
	 */
	public boolean isSupported() {
		return this != OperatingSystem.UNKNOWN;
	}

	/**
	 * @return If is unsupported OS
	 */
	public boolean isUnsupported() {
		return this == UNKNOWN;
	}

	/**
	 * @return The Java Path
	 */
	public static String getJavaPath() {
		File javaBinary = resolveJavaBinary(new File(System.getProperty("java.home")), false);
		if (javaBinary == null) {
			return "java";
		}
		if (isWindows()) {
			return "\"" + javaBinary.getAbsolutePath() + "\"";
		}
		return javaBinary.getAbsolutePath();
	}
	
	/**
	 * @return The Java Path Installed
	 */
	public static String getJavaPath(GameEngine engine) {
		if (engine == null || engine.getMinecraftVersion() == null || engine.getMinecraftVersion().getJavaVersion() == null
				|| engine.getGameFolder() == null) {
			return getJavaPath();
		}
		String component = engine.getMinecraftVersion().getJavaVersion().getComponent();
		File javaPath = runtimeDirectory(engine.getGameFolder().getBinDir(), component);
		File javaBinary = findRuntimeJavaBinary(javaPath);
		if (javaBinary == null) {
			return new File(new File(javaPath, "bin"), isWindows() ? "java.exe" : "java").getAbsolutePath();
		}
		return javaBinary.getAbsolutePath();
	}

	/**
	 * @return The Java directory
	 */
	public String getJavaDir() {
		File javaBinary = resolveJavaBinary(new File(System.getProperty("java.home")), true);
		if (javaBinary != null) {
			return javaBinary.getAbsolutePath();
		}
		final String separator = System.getProperty("file.separator");
		return System.getProperty("java.home") + separator + "bin" + separator + "java";
	}

	/**
	 * @return The current Platform
	 */
	public static OperatingSystem getCurrentPlatform() {
		final String osName = System.getProperty("os.name").toLowerCase();
		for (final OperatingSystem os : values()) {
			for (final String alias : os.getAliases()) {
				if (osName.contains(alias)) {
					return os;
				}
			}
		}
		return OperatingSystem.UNKNOWN;
	}

	/**
	 * Is this OS match with the part 
	 * @param part The Part to match
	 * @return If it match
	 */
	public static boolean match(String part) {
		if (part.contains(getCurrentPlatform().getName())) {
			return true;
		}
		List<String> aliases = Arrays.asList(getCurrentPlatform().getAliases());
		for (String alias : aliases) {
			if (part.contains(alias)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return The current Platform
	 */
	public static OperatingSystem getCurrent() {
		String osName = NAME.toLowerCase();
		OperatingSystem[] var4;
		int var3 = (var4 = values()).length;
		for (int var2 = 0; var2 < var3; var2++) {
			OperatingSystem os = var4[var2];
			String[] var8 = os.aliases;
			int var7 = os.aliases.length;
			for (int var6 = 0; var6 < var7; var6++) {
				String alias = var8[var6];
				if (osName.contains(alias)) {
					return os;
				}
			}
		}
		return UNKNOWN;
	}

	/**
	 * Open a link
	 * @param link The Url to open
	 */
	public static void openLink(final URI link) {
		if (link == null) {
			return;
		}
		if (getCurrentPlatform() != OperatingSystem.WINDOWS) {
			// Linux / macOS : commande système (voir openFile pour la raison).
			if (!openTarget(link.toString(), link)) {
				Logger.log("Failed to open link " + link.toString());
			}
			return;
		}
		try {
			final Class<?> desktopClass = Class.forName("java.awt.Desktop");
			final Object o = desktopClass.getMethod("getDesktop", (Class[]) new Class[0]).invoke(null, new Object[0]);
			desktopClass.getMethod("browse", URI.class).invoke(o, link);
		} catch (Throwable e2) {
			if (getCurrentPlatform() == OperatingSystem.OSX) {
				try {
					Runtime.getRuntime().exec(new String[] { "/usr/bin/open", link.toString() });
				} catch (IOException e1) {
					Logger.log("Failed to open link " + link.toString());
				}
			} else if (getCurrentPlatform() == OperatingSystem.LINUX || getCurrentPlatform() == OperatingSystem.SOLARIS) {
				try {
					Runtime.getRuntime().exec(new String[] { "xdg-open", link.toString() });
				} catch (IOException e1) {
					Logger.log("Failed to open link " + link.toString());
				}
			} else {
				Logger.log("Failed to open link " + link.toString());
			}
		}
	}
	
	/**
	 * Get the Java Bit
	 */
	public static Arch getJavaBit() {
		String res = System.getProperty("sun.arch.data.model");
		if (res != null && res.equalsIgnoreCase("64"))
			return Arch.x64;
		return Arch.x86;
	}

	/**
	 * Open a folder
	 * @param path The Folder Path
	 */
	@SuppressWarnings("deprecation")
	public static void openFolder(final File path) {
		final String absolutePath = path.getAbsolutePath();
		final OperatingSystem os = getCurrentPlatform();
		if (os == OperatingSystem.OSX) {
			try {
				Runtime.getRuntime().exec(new String[] { "/usr/bin/open", absolutePath });
				return;
			} catch (IOException e) {
				Logger.log("Couldn't open " + path + " through /usr/bin/open");
			}
		}
		if (os == OperatingSystem.WINDOWS) {
			final String cmd = String.format("cmd.exe /C start \"Open file\" \"%s\"", absolutePath);
			try {
				Runtime.getRuntime().exec(cmd);
				return;
			} catch (IOException e2) {
				Logger.log("Couldn't open " + path + " through cmd.exe");
			}
		}
		if (os == OperatingSystem.LINUX || os == OperatingSystem.SOLARIS) {
			try {
				Runtime.getRuntime().exec(new String[] { "xdg-open", absolutePath });
				return;
			} catch (IOException e) {
				Logger.log("Couldn't open " + path + " through xdg-open");
			}
		}
		try {
			final Class<?> desktopClass = Class.forName("java.awt.Desktop");
			final Object desktop = desktopClass.getMethod("getDesktop", (Class[]) new Class[0]).invoke(null,
					new Object[0]);
			desktopClass.getMethod("open", File.class).invoke(desktop, path);
		} catch (Throwable e3) {
			Logger.log("Couldn't open " + path + " through Desktop.browse()");
		}
	}

	public static boolean isWindows() {
		return getCurrentPlatform() == OperatingSystem.WINDOWS;
	}

	public static boolean isLinux() {
		return getCurrentPlatform() == OperatingSystem.LINUX;
	}

	public static boolean isMac() {
		return getCurrentPlatform() == OperatingSystem.OSX;
	}

	/**
	 * Architecture utilisée pour le jeu : ARM 64 bits, sauf quand une version sans
	 * natives ARM est lancée en x86_64 via Rosetta (voir configureForMinecraft).
	 */
	public static boolean isArm64Architecture() {
		return !emulateX86_64 && isHostArm64();
	}

	/** Processeur réel de la machine (ARM 64 bits ?), indépendamment de Rosetta. */
	public static boolean isHostArm64() {
		String osArch = System.getProperty("os.arch", "");
		if (osArch == null) {
			return false;
		}
		String normalized = osArch.toLowerCase();
		return normalized.contains("aarch64") || normalized.contains("arm64");
	}

	/** Architecture effective, au format de la propriété os.arch (règles des JSON de version). */
	public static String getEffectiveArch() {
		return emulateX86_64 ? "x86_64" : System.getProperty("os.arch", "");
	}

	// ------------------------------------------------------------------ Apple Silicon

	private static volatile boolean emulateX86_64;

	/**
	 * Mac Apple Silicon : Minecraft ne fournit des natives ARM qu'à partir de la 1.19.
	 * Les versions plus anciennes sont lancées, comme avec le launcher officiel, avec
	 * le Java x86_64 de Mojang et les natives x86_64, exécutés par Rosetta 2. À appeler
	 * avant de préparer les natives et le Java du jeu.
	 */
	public static void configureForMinecraft(MinecraftVersion version, String versionIdHint) {
		boolean emulate = getCurrentPlatform() == OSX && isHostArm64() && !providesMacArm64Natives(version, versionIdHint);
		if (emulate != emulateX86_64) {
			Logger.log(emulate
					? "Apple Silicon : version sans natives ARM, lancement en x86_64 via Rosetta 2."
					: "Architecture native utilisée pour cette version.");
		}
		emulateX86_64 = emulate;
	}

	public static boolean isEmulatingX86_64() {
		return emulateX86_64;
	}

	/**
	 * Chromebook : le launcher tourne dans l'environnement Linux de ChromeOS
	 * (conteneur « penguin », Crostini).
	 */
	public static boolean isChromeOS() {
		return getCurrentPlatform() == LINUX
				&& (new File("/dev/.cros_milestone").exists() || new File("/opt/google/cros-containers").exists());
	}

	/** Rosetta 2 installé (nécessaire au mode x86_64 sur Apple Silicon) ? */
	public static boolean isRosettaInstalled() {
		return new File("/Library/Apple/usr/libexec/oah/libRosettaRuntime").exists()
				|| new File("/usr/libexec/rosetta/oahd").exists();
	}

	private static boolean providesMacArm64Natives(MinecraftVersion version, String versionIdHint) {
		boolean sawLwjgl = false;
		if (version != null && version.getLibraries() != null) {
			for (MinecraftLibrary library : version.getLibraries()) {
				if (library == null) {
					continue;
				}
				String name = library.getName() == null ? "" : library.getName().toLowerCase();
				if (name.contains("lwjgl")) {
					sawLwjgl = true;
				}
				if (isMacArm64Native(name)) {
					return true;
				}
				if (library.getDownloads() != null && library.getDownloads().getClassifiers() != null) {
					for (String classifier : library.getDownloads().getClassifiers().keySet()) {
						if (classifier != null && isMacArm64Native(classifier.toLowerCase())) {
							return true;
						}
					}
				}
			}
		}
		if (sawLwjgl) {
			// Bibliothèques connues et aucune native ARM : il faut le mode x86_64.
			return false;
		}
		// Bibliothèques inconnues : on se fie au numéro de version (natives ARM dès 1.19).
		Integer minor = minecraftMinorVersion(version != null ? version.getId() : null);
		if (minor == null && version != null) {
			minor = minecraftMinorVersion(version.getInheritsFrom());
		}
		if (minor == null) {
			minor = minecraftMinorVersion(versionIdHint);
		}
		return minor == null || minor >= 19;
	}

	private static boolean isMacArm64Native(String lower) {
		return lower.contains("macos-arm64") || lower.contains("osx-arm64")
				|| lower.contains("macos-aarch64") || lower.contains("osx-aarch64");
	}

	private static Integer minecraftMinorVersion(String id) {
		if (id == null) {
			return null;
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:^|[^0-9.])1\\.(\\d{1,2})(?:[^0-9]|$)").matcher(id);
		if (!m.find()) {
			return null;
		}
		try {
			return Integer.valueOf(m.group(1));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------ runtimes Java

	/** Dossier d'un runtime Java de Mojang (séparé en mode x86_64 pour ne pas mélanger les architectures). */
	public static File runtimeDirectory(File binDir, String component) {
		return new File(binDir, emulateX86_64 ? component + "-x86_64" : component);
	}

	/**
	 * Binaire java d'un runtime Mojang : bin/java sous Windows et Linux,
	 * jre.bundle/Contents/Home/bin/java sous macOS. Null s'il est introuvable.
	 */
	public static File findRuntimeJavaBinary(File runtimeDir) {
		if (runtimeDir == null || !runtimeDir.isDirectory()) {
			return null;
		}
		File[] homes = {
				runtimeDir,
				new File(runtimeDir, "jre.bundle" + File.separator + "Contents" + File.separator + "Home")
		};
		for (File home : homes) {
			File binary = resolveJavaBinary(home, false);
			if (binary != null) {
				return binary;
			}
		}
		// Dernier recours : un sous-dossier qui contient bin/java (ou Contents/Home/bin/java).
		File[] children = runtimeDir.listFiles();
		if (children != null) {
			for (File child : children) {
				if (!child.isDirectory()) {
					continue;
				}
				File binary = resolveJavaBinary(child, false);
				if (binary == null) {
					binary = resolveJavaBinary(new File(child, "Contents" + File.separator + "Home"), false);
				}
				if (binary != null) {
					return binary;
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ ouverture

	/**
	 * Ouvre un fichier ou un dossier avec l'application du système. Sous Linux,
	 * xdg-open plutôt que java.awt.Desktop : en Java 8, Desktop charge GTK 2 alors
	 * que JavaFX peut avoir chargé GTK 3, et les deux dans un même processus font
	 * planter la JVM.
	 */
	public static boolean openFile(File file) {
		if (file == null || !file.exists()) {
			return false;
		}
		return openTarget(file.getAbsolutePath(), file.toURI());
	}

	private static boolean openTarget(String target, URI uri) {
		OperatingSystem os = getCurrentPlatform();
		if (os == LINUX || os == SOLARIS) {
			return startCommand("xdg-open", target) || startCommand("gio", "open", target);
		}
		if (os == OSX) {
			return startCommand("/usr/bin/open", target);
		}
		try {
			java.awt.Desktop.getDesktop().browse(uri);
			return true;
		} catch (Throwable e) {
			return startCommand("rundll32", "url.dll,FileProtocolHandler", target);
		}
	}

	private static boolean startCommand(String... command) {
		try {
			new ProcessBuilder(command).start();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static boolean is32BitJvm() {
		return Arch.x86.equals(getJavaBit());
	}

	private static File resolveJavaBinary(File javaHome, boolean preferWindowless) {
		if (javaHome == null) {
			return null;
		}
		File[] candidates = preferWindowless
				? new File[] {
						new File(javaHome, "bin" + File.separator + "javaw.exe"),
						new File(javaHome, "bin" + File.separator + "java.exe"),
						new File(javaHome, "bin" + File.separator + "java")
				}
				: new File[] {
						new File(javaHome, "bin" + File.separator + "java.exe"),
						new File(javaHome, "bin" + File.separator + "javaw.exe"),
						new File(javaHome, "bin" + File.separator + "java")
				};
		for (File candidate : candidates) {
			if (candidate.isFile()) {
				return candidate;
			}
		}
		return null;
	}
}
