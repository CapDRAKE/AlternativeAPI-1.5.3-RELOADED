package fr.trxyy.alternative.alternative_api;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

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
	@SuppressWarnings("unchecked")
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

		for (File file : this.filesList) {

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

			String relativePath = file.getAbsolutePath().replace(gameDirAbs, "");

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
	 * FIXES:
	 * - si ignore.cfg absent (404) => ignore list vide (pas de crash)
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

		try (BufferedReader read = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
			String i;
			while ((i = read.readLine()) != null) {
				i = i.trim();
				if (i.isEmpty() || i.startsWith("#")) continue;

				String correctName = normalizeSeparators(i);

				boolean isFolder = correctName.endsWith("/") || correctName.endsWith("\\") || correctName.endsWith(String.valueOf(File.separatorChar));
				if (isFolder) {
					// on stocke la forme “dossier” avec séparateur final pour matcher plus facilement
					correctName = normalizeSeparators(correctName);
					if (!correctName.endsWith(File.separator)) {
						correctName = correctName + File.separator;
					}
					this.ignoreListFolder.add(correctName);
				} else {
					this.ignoreList.add("" + this.engine.getGameFolder().getGameDir() + File.separatorChar + correctName);
				}
			}
		} catch (FileNotFoundException e) {
			// 404 ignore.cfg => OK (ex: Mojang), on continue sans ignore list
		} catch (IOException e) {
			// réseau / autre IO => pas bloquant, on continue sans ignore list
			e.printStackTrace();
		}
	}

	/**
	 * Getting the delete list from URL
	 *
	 * FIXES:
	 * - si delete.cfg absent (404) => delete list vide (pas de crash)
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

		try (BufferedReader read = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
			String i;
			while ((i = read.readLine()) != null) {
				i = i.trim();
				if (i.isEmpty() || i.startsWith("#")) continue;

				String correctName = normalizeSeparators(i);
				this.deleteList.add("" + this.engine.getGameFolder().getGameDir() + File.separatorChar + correctName);
			}
		} catch (FileNotFoundException e) {
			// 404 delete.cfg => OK
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
