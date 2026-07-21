package fr.trxyy.alternative.alternative_api;

import java.io.File;
import java.util.Locale;

/**
 * Résout de façon déterministe le dossier de jeu (et son nom de profil) utilisé pour une
 * combinaison version+modloader donnée.
 * <p>
 * C'est la source unique de vérité partagée entre le lancement réel du jeu (GameRunner) et
 * l'UI du launcher (panels mods/packs/shaders) : les deux DOIVENT calculer exactement le même
 * chemin, sinon les mods/packs/shaders ajoutés via le launcher ne sont jamais vus par le jeu
 * réellement lancé.
 *
 * @author Trxyy
 */
public final class GameProfilePaths {

	private GameProfilePaths() {
	}

	/**
	 * @param engine The GameEngine instance
	 * @return Le nom du dossier de profil (ex: "fabric-1.21.11")
	 */
	public static String resolveProfileDirectoryName(GameEngine engine) {
		String styleName = engine != null && engine.getGameStyle() != null
				? engine.getGameStyle().name().toLowerCase(Locale.ROOT)
				: "unknown";

		String versionId = engine != null ? engine.getRequestedVersionId() : null;
		if (versionId == null || versionId.trim().isEmpty()) {
			versionId = "default";
		}

		return sanitizePathSegment(styleName + "-" + versionId);
	}

	/**
	 * @param engine The GameEngine instance
	 * @return Le dossier de jeu réel (bin/game/&lt;profil&gt;), ou le dossier partagé historique
	 *         pour la compatibilité OptiFine &lt; 1.16
	 */
	public static File resolveRuntimeGameDirectory(GameEngine engine) {
		File basePlayDir = engine != null && engine.getGameFolder() != null
				? engine.getGameFolder().getPlayDir()
				: null;
		if (basePlayDir == null) {
			return new File(".");
		}

		if (isLegacyOptiFineSharedGameDirectory(engine)) {
			basePlayDir.mkdirs();
			return basePlayDir;
		}

		File runtimeDir = new File(basePlayDir, resolveProfileDirectoryName(engine));
		runtimeDir.mkdirs();
		return runtimeDir;
	}

	private static boolean isLegacyOptiFineSharedGameDirectory(GameEngine engine) {
		if (engine == null || engine.getGameStyle() != GameStyle.OPTIFINE) {
			return false;
		}
		return !isVersionAtLeast(engine.getRequestedVersionId(), 1, 16);
	}

	private static boolean isVersionAtLeast(String versionId, int major, int minor) {
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
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static String sanitizePathSegment(String rawValue) {
		if (rawValue == null || rawValue.trim().isEmpty()) {
			return "default";
		}

		String sanitized = rawValue.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
		while (sanitized.contains("..")) {
			sanitized = sanitized.replace("..", ".");
		}
		return sanitized.isEmpty() ? "default" : sanitized;
	}
}
