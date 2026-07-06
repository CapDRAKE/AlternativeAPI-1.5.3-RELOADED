package fr.trxyy.alternative.alternative_api;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.trxyy.alternative.alternative_api.utils.file.FileUtil;

/**
 * @author Trxyy
 */
public class GameVerifier {

	/**
	 * The GameEngine instance
	 */
	public GameEngine engine;
	/**
	 * The allowed files list
	 */
	public static List<String> allowedFiles = new ArrayList<String>();
	/**
	 * The files list (existing)
	 */
	public List<File> filesList;
	/**
	 * The ignore list (files not to be deleted)
	 */
	public List<String> ignoreList = new ArrayList<String>();
	/**
	 * The ignore list (entire folder not to be deleted)
	 */
	public List<String> ignoreListFolder = new ArrayList<String>();
	/**
	 * The delete list (files forced to be deleted)
	 */
	public List<String> deleteList = new ArrayList<String>();

	/**
	 * The Constructor
	 * @param gameEngine The instance of GameEngine
	 */
	public GameVerifier(GameEngine gameEngine) {
		this.engine = gameEngine;
	}

	/**
	 * Verify files to ignore/delete
	 */
	public void verify() {
		if (this.engine == null || this.engine.getGameFolder() == null || this.engine.getGameFolder().getBinDir() == null) {
			return;
		}

		this.filesList = (List<File>) FileUtils.listFiles(
				this.engine.getGameFolder().getBinDir(),
				TrueFileFilter.INSTANCE,
				TrueFileFilter.INSTANCE
		);

		final String gameDirAbs = this.engine.getGameFolder().getGameDir().getAbsolutePath();
		final String jsonName = (this.engine.getGameLinks() != null) ? this.engine.getGameLinks().getJsonName() : null;
		final String currentJavaHome = new File(System.getProperty("java.home")).getAbsolutePath();

		for (File file : this.filesList) {

			if (file.getAbsolutePath().startsWith(currentJavaHome + File.separator)) {
				continue;
			}

			String relativePath = file.getAbsolutePath().replace(gameDirAbs, "");

			// Protège les données utilisateur du dossier de jeu (options, serveurs, saves, etc.)
			if (isProtectedUserData(relativePath)) {
				continue;
			}

			// ne touche pas au json
			if (jsonName != null && file.getAbsolutePath().endsWith(jsonName)) {
				continue;
			}

			// ne touche pas au fichier logging (si présent)
			if (engine.getMinecraftVersion() != null && engine.getMinecraftVersion().getLogging() != null) {
				try {
					String loggingId = engine.getMinecraftVersion().getLogging().getClient().getFile().getId();
					if (loggingId != null && file.getAbsolutePath().endsWith(loggingId)) {
						continue;
					}
				} catch (Exception ignored) {
					// si le logging est mal formé, on n'explose pas
				}
			}

			// ne touche pas à downloads.xml
			if (file.getAbsolutePath().endsWith("downloads.xml")) {
				continue;
			}

			// delete forcé
			if (existInDeleteList(relativePath)) {
				FileUtil.deleteSomething(file.getAbsolutePath());
				continue;
			}

			// pas dans allowed => potentiellement supprimé, sauf si ignoré
			if (!existInAllowedFiles(relativePath)) {
				String parentRel = file.getParent().replace(gameDirAbs, "");

				if (existInIgnoreListFolder(parentRel)) {
					continue;
				}
				if (existInIgnoreList(relativePath)) {
					continue;
				}

				FileUtil.deleteSomething(file.getAbsolutePath());
			}
		}
	}

	private boolean isProtectedUserData(String relativePath) {
		if (isBlank(relativePath)) return false;

		String path = normalizeSeparators(relativePath);
		String playPrefix = File.separator + "bin" + File.separator + "game" + File.separator;

		if (!path.startsWith(playPrefix)) {
			return false;
		}

		String insideGame = path.substring(playPrefix.length());
		String lower = insideGame.toLowerCase();
		String nestedLower = stripFirstGameProfileSegment(insideGame).toLowerCase();

		return isProtectedGameContent(lower) || isProtectedGameContent(nestedLower);
	}

	private boolean isProtectedGameContent(String lower) {
		if (lower.equals("servers.dat") || lower.equals("servers.dat_old")
				|| lower.equals("options.txt") || lower.equals("optionsof.txt")
				|| lower.equals("optionsshaders.txt") || lower.equals("optionsfullscreen.txt")
				|| lower.equals("optionsoculus.txt") || lower.equals("usercache.json")
				|| lower.equals("usernamecache.json") || lower.equals("lastlogin")) {
			return true;
		}

		if (lower.startsWith("options") && lower.endsWith(".txt")) {
			return true;
		}

		return lower.startsWith("saves" + File.separator)
				|| lower.startsWith("screenshots" + File.separator)
				|| lower.startsWith("logs" + File.separator)
				|| lower.startsWith("crash-reports" + File.separator)
				|| lower.startsWith("resourcepacks" + File.separator)
				|| lower.startsWith("shaderpacks" + File.separator)
				|| lower.startsWith("server-resource-packs" + File.separator);
	}

	private String stripFirstGameProfileSegment(String insideGame) {
		if (isBlank(insideGame)) {
			return "";
		}

		String normalized = normalizeSeparators(insideGame);
		int separatorIndex = normalized.indexOf(File.separatorChar);
		if (separatorIndex < 0 || separatorIndex + 1 >= normalized.length()) {
			return normalized;
		}

		return normalized.substring(separatorIndex + 1);
	}

/**
	 * Add files to allowed files list
	 * @param allowed The file to add
	 */
	public static void addToFileList(String allowed) {
		allowedFiles.add(allowed);
	}

	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private String normalizeSeparators(String s) {
		if (s == null) return null;
		// pour matcher correctement sous Windows/Linux
		return s.replace('/', File.separatorChar).replace('\\', File.separatorChar);
	}

	/**
	 * Check if the file exist in the ignore list
	 * @param search The file path to search
	 * @return If the file exist
	 */
	public boolean existInIgnoreList(String search) {
		if (isBlank(search)) return false;
		for (String str : this.ignoreList) {
			if (str != null && str.trim().contains(search)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if the file exist in the ignore folder
	 * @param search The file path to search
	 * @return If the file exist
	 */
	public boolean existInIgnoreListFolder(String search) {
		if (isBlank(search)) return false;

		String newSearch = normalizeSeparators(search);
		if (!newSearch.endsWith(File.separator)) {
			newSearch = newSearch + File.separator;
		}

		for (String str : this.ignoreListFolder) {
			if (isBlank(str)) continue;

			String folder = normalizeSeparators(str);
			if (!folder.endsWith(File.separator)) {
				folder = folder + File.separator;
			}

			if (newSearch.contains(folder)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if the file exist in allowed files
	 * @param search The file path to search
	 * @return If the file exist
	 */
	public boolean existInAllowedFiles(String search) {
		if (isBlank(search)) return false;
		for (String str : allowedFiles) {
			if (str != null && str.trim().contains(search)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if the file exist in the delete list
	 * @param search The file path to search
	 * @return If the file exist
	 */
	public boolean existInDeleteList(String search) {
		if (isBlank(search)) return false;
		for (String str : this.deleteList) {
			if (str != null && str.trim().contains(search)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Getting the ignore list from URL
	 *
	 * Accepte le nouveau format JSON ({"files": [...], "folders": [...]})
	 * et, en fallback, l'ancien format texte brut (ignore.cfg, une entrée par ligne).
	 *
	 * FIXES:
	 * - si ignore.json absent (404) => ignore list vide (pas de crash)
	 * - si URL null/malformée => return
	 * - try-with-resources => pas de NPE sur read.close()
	 */
	public void getIgnoreList() {
		if (this.engine == null || this.engine.getGameLinks() == null) return;

		String ignoreUrl = this.engine.getGameLinks().getIgnoreListUrl();
		if (isBlank(ignoreUrl)) {
			// ignore list désactivée
			return;
		}

		final URL url;
		try {
			url = new URL(ignoreUrl);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return;
		}

		String raw;
		try {
			raw = readUrlBody(url);
		} catch (FileNotFoundException e) {
			// 404 ignore.json => OK (ex: Mojang), on continue sans ignore list
			return;
		} catch (IOException e) {
			// réseau / autre IO => pas bloquant, on continue sans ignore list
			e.printStackTrace();
			return;
		}

		if (!parseIgnoreListJson(raw)) {
			parseIgnoreListLegacy(raw);
		}
	}

	private boolean parseIgnoreListJson(String raw) {
		try {
			JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
			JsonArray files = obj.has("files") ? obj.getAsJsonArray("files") : null;
			JsonArray folders = obj.has("folders") ? obj.getAsJsonArray("folders") : null;
			if (files == null && folders == null) return false;

			if (files != null) {
				for (JsonElement el : files) {
					addIgnoreEntry(el.getAsString(), false);
				}
			}
			if (folders != null) {
				for (JsonElement el : folders) {
					addIgnoreEntry(el.getAsString(), true);
				}
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void parseIgnoreListLegacy(String raw) {
		for (String line : raw.split("\\r?\\n")) {
			String i = line.trim();
			if (i.isEmpty() || i.startsWith("#")) continue;

			boolean isFolder = i.endsWith("/") || i.endsWith("\\");
			addIgnoreEntry(i, isFolder);
		}
	}

	private void addIgnoreEntry(String entry, boolean isFolder) {
		String correctName = normalizeSeparators(entry);
		if (isFolder) {
			// on stocke la forme “dossier” avec séparateur final pour matcher plus facilement
			if (!correctName.endsWith(File.separator)) {
				correctName = correctName + File.separator;
			}
			this.ignoreListFolder.add(correctName);
		} else {
			this.ignoreList.add("" + this.engine.getGameFolder().getGameDir() + File.separatorChar + correctName);
		}
	}

	/**
	 * Getting the delete list from URL
	 *
	 * Accepte le nouveau format JSON ({"files": [...]})
	 * et, en fallback, l'ancien format texte brut (delete.cfg, une entrée par ligne).
	 *
	 * FIXES:
	 * - si delete.json absent (404) => delete list vide (pas de crash)
	 * - si URL null/malformée => return
	 */
	public void getDeleteList() {
		if (this.engine == null || this.engine.getGameLinks() == null) return;

		String deleteUrl = this.engine.getGameLinks().getDeleteListUrl();
		if (isBlank(deleteUrl)) {
			// delete list désactivée
			return;
		}

		final URL url;
		try {
			url = new URL(deleteUrl);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return;
		}

		String raw;
		try {
			raw = readUrlBody(url);
		} catch (FileNotFoundException e) {
			// 404 delete.json => OK
			return;
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		if (!parseDeleteListJson(raw)) {
			parseDeleteListLegacy(raw);
		}
	}

	private boolean parseDeleteListJson(String raw) {
		try {
			JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
			if (!obj.has("files")) return false;

			for (JsonElement el : obj.getAsJsonArray("files")) {
				addDeleteEntry(el.getAsString());
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void parseDeleteListLegacy(String raw) {
		for (String line : raw.split("\\r?\\n")) {
			String i = line.trim();
			if (i.isEmpty() || i.startsWith("#")) continue;
			addDeleteEntry(i);
		}
	}

	private void addDeleteEntry(String entry) {
		String correctName = normalizeSeparators(entry);
		this.deleteList.add("" + this.engine.getGameFolder().getGameDir() + File.separatorChar + correctName);
	}

	/**
	 * Lit le corps d'une URL avec un User-Agent explicite (certains hébergeurs
	 * renvoient 403 sur les requêtes sans User-Agent).
	 */
	private String readUrlBody(URL url) throws IOException {
		URLConnection connection = url.openConnection();
		connection.setRequestProperty("User-Agent", "MajestyLauncher");
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(10000);

		StringBuilder sb = new StringBuilder();
		try (BufferedReader read = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = read.readLine()) != null) {
				sb.append(line).append('\n');
			}
		}
		return sb.toString();
	}
}
