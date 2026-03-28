package fr.trxyy.alternative.alternative_api.utils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import fr.trxyy.alternative.alternative_api.GameEngine;
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
		File javaPath = new File(engine.getGameFolder().getBinDir(), component);
		File javaBinary = resolveJavaBinary(javaPath, false);
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

	public static boolean isArm64Architecture() {
		String osArch = System.getProperty("os.arch", "");
		if (osArch == null) {
			return false;
		}
		String normalized = osArch.toLowerCase();
		return normalized.contains("aarch64") || normalized.contains("arm64");
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
