package fr.trxyy.alternative.alternative_api.utils.config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import fr.trxyy.alternative.alternative_api.GameEngine;
import fr.trxyy.alternative.alternative_api.utils.Logger;
import fr.trxyy.alternative.alternative_api.utils.file.JsonUtil;

@SuppressWarnings("unchecked")
public class LauncherConfig {

	public ConfigVersion configVersion;
	public boolean read = false;
	public File launcherConfig;
	public GameEngine gameEngine;

	/**
	 * The Constructor
	 * @param engine The GameEngine instance
	 */
	public LauncherConfig(GameEngine engine) {
		this.gameEngine = engine;
		String osName = System.getProperty("os.name").toLowerCase();
		String configDir = System.getProperty("user.home");

		if (osName.contains("win")) {
		    configDir += File.separator + "AppData" + File.separator + "Roaming";
		} else if (osName.contains("mac")) {
		    configDir += File.separator + "Library" + File.separator + "Application Support";
		}

		this.launcherConfig = new File(configDir, "launcher_config.json");

		if (!this.launcherConfig.exists()) {
			writeDefaultConfigFile();
		}
	}

	/**
	 * (Re)cree le fichier de configuration avec les valeurs par defaut.
	 * Extrait du constructeur pour pouvoir aussi servir a la reparation
	 * automatique d'une configuration illisible.
	 */
	private void writeDefaultConfigFile() {
		{
			try {
				this.launcherConfig.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}

			/**
			 * Config details
			 */
			JSONObject configDetails = new JSONObject();
			configDetails.put(EnumConfig.USERNAME.getOption(), EnumConfig.USERNAME.getDefault());
			configDetails.put(EnumConfig.TOKEN.getOption(), EnumConfig.TOKEN.getDefault());
			configDetails.put(EnumConfig.UUID.getOption(), EnumConfig.UUID.getDefault());
			configDetails.put(EnumConfig.RAM.getOption(), EnumConfig.RAM.getDefault());
			configDetails.put(EnumConfig.GAME_SIZE.getOption(), EnumConfig.GAME_SIZE.getDefault());
			configDetails.put(EnumConfig.AUTOLOGIN.getOption(), false);
			configDetails.put(EnumConfig.VM_ARGUMENTS.getOption(), EnumConfig.VM_ARGUMENTS.getDefault());
			configDetails.put(EnumConfig.USE_VM_ARGUMENTS.getOption(), false);
			configDetails.put(EnumConfig.USE_MICROSOFT.getOption(), false);
			configDetails.put(EnumConfig.USE_CONNECT.getOption(), false);
			configDetails.put(EnumConfig.USE_DISCORD.getOption(), true);
			configDetails.put(EnumConfig.USE_MUSIC.getOption(), true);
			configDetails.put(EnumConfig.REMEMBER_ME.getOption(), false);
			configDetails.put(EnumConfig.VERSION.getOption(), EnumConfig.VERSION.getDefault());
			configDetails.put(EnumConfig.USE_FORGE.getOption(), false);
			configDetails.put(EnumConfig.USE_FABRIC.getOption(), false);
			configDetails.put(EnumConfig.USE_QUILT.getOption(), false);
			configDetails.put(EnumConfig.USE_NEOFORGE.getOption(), false);
			configDetails.put(EnumConfig.USE_OPTIFINE.getOption(), true);
			configDetails.put(EnumConfig.USE_PREMIUM.getOption(), false);
			configDetails.put(EnumConfig.PASSWORD.getOption(), EnumConfig.PASSWORD.getDefault());
			configDetails.put(EnumConfig.DATE.getOption(), EnumConfig.DATE.getDefault());
			configDetails.put(EnumConfig.LANGUAGE.getOption(), EnumConfig.LANGUAGE.getDefault());
			configDetails.put(EnumConfig.CFG_INCLUDE_SNAPSHOTS.getOption(), EnumConfig.CFG_INCLUDE_SNAPSHOTS.getDefault());
			
			try {
				FileWriter fw = new FileWriter(this.launcherConfig);
				JsonUtil.getGson().toJson(configDetails, fw);
				fw.flush();
				fw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Update a value in the config json
	 */
	public void updateValue(String toUpdate, Object value) {
		this.loadConfiguration();
		String configJson = JsonUtil.getGson().toJson(this.configVersion);
		JSONObject jsonObject = (JSONObject) JSONValue.parse(configJson);
		jsonObject.put(toUpdate, value);
		try {
			FileWriter fileWriter = new FileWriter(this.launcherConfig);
			JsonUtil.getGson().toJson(jsonObject, fileWriter);
			fileWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Update multiple values in the config json
	 */
	public void updateValues(HashMap<String, String> values) {
		this.loadConfiguration();
		String configJson = JsonUtil.getGson().toJson(this.configVersion);
		JSONObject jsonObject = (JSONObject) JSONValue.parse(configJson);
		for (Entry<String, String> entry : values.entrySet()) {
			jsonObject.put(entry.getKey(), entry.getValue());
		}
		
		try {
			FileWriter fileWriter = new FileWriter(this.launcherConfig);
			JsonUtil.getGson().toJson(jsonObject, fileWriter);
			fileWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.loadConfiguration();
	}
	
	/**
	 * Get a specified value
	 */
	public Object getValue(EnumConfig option) {
		String configJson = JsonUtil.getGson().toJson(this.configVersion);
		JSONObject jsonObject = (JSONObject) JSONValue.parse(configJson);
		return jsonObject.get(option.getOption());
	}

	/**
	 * Charge la configuration, en resistant a un fichier illisible.
	 *
	 * Auparavant l'analyse JSON n'etait pas protegee : un fichier corrompu (BOM,
	 * troncature, edition manuelle ratee) faisait remonter une exception jusqu'au
	 * main() et le launcher se fermait sans le moindre message, l'utilisateur
	 * constatant seulement qu'il "ne demarre plus".
	 *
	 * Desormais on tente d'abord une reparation transparente (nettoyage du BOM et
	 * des espaces de tete), puis en dernier recours on met le fichier fautif de
	 * cote et on repart sur une configuration par defaut.
	 */
	public void loadConfiguration() {
		String json = null;
		try {
			json = JsonUtil.loadJSON(this.launcherConfig.toURI().toURL().toString());
			this.read = true;
		} catch (IOException e) {
			Logger.err("Lecture de launcher_config.json impossible : " + e.getMessage());
		}

		if (!this.read || json == null) {
			repairConfiguration(null, null);
			return;
		}

		try {
			this.configVersion = JsonUtil.getGson().fromJson(json, ConfigVersion.class);
			if (this.configVersion == null) {
				throw new IllegalStateException("configuration vide");
			}
			return;
		} catch (Exception first) {
			// 1re tentative de reparation : BOM UTF-8 ou espaces avant le '{'.
			String cleaned = stripLeadingNoise(json);
			if (cleaned != null && !cleaned.equals(json)) {
				try {
					this.configVersion = JsonUtil.getGson().fromJson(cleaned, ConfigVersion.class);
					if (this.configVersion != null) {
						Logger.log("launcher_config.json nettoye (caracteres parasites en tete).");
						rewriteCleanConfig(cleaned);
						return;
					}
				} catch (Exception ignored) {
					// on bascule sur la reparation complete ci-dessous
				}
			}
			repairConfiguration(first, json);
		}
	}

	/**
	 * Retire tout ce qui precede le debut reel de l'objet JSON (BOM, espaces,
	 * caracteres parasites).
	 *
	 * On ne cherche pas un caractere BOM precis a dessein : le fichier est lu avec
	 * le charset par defaut de la plateforme, donc un BOM UTF-8 apparait tantot
	 * comme un unique U+FEFF, tantot comme trois caracteres mal decodes selon
	 * l'encodage courant. Se caler sur la premiere accolade couvre tous les cas.
	 */
	private String stripLeadingNoise(String json) {
		if (json == null) {
			return null;
		}
		int brace = json.indexOf('{');
		// 0 = deja propre, -1 = aucun objet JSON (reparation complete necessaire)
		return brace <= 0 ? json : json.substring(brace);
	}

	/** Reecrit le fichier avec le contenu assaini, pour ne pas rejouer le nettoyage. */
	private void rewriteCleanConfig(String cleanJson) {
		try (FileWriter fw = new FileWriter(this.launcherConfig, false)) {
			fw.write(cleanJson);
			fw.flush();
		} catch (IOException e) {
			Logger.err("Reecriture de la configuration nettoyee impossible : " + e.getMessage());
		}
	}

	/**
	 * Met le fichier illisible de cote et repart sur une configuration par defaut,
	 * afin que le launcher demarre malgre tout.
	 */
	private void repairConfiguration(Exception cause, String rawContent) {
		Logger.err("Configuration illisible"
				+ (cause != null ? " (" + cause.getMessage() + ")" : "")
				+ " : reinitialisation.");

		// On ecrit le contenu fautif dans un fichier a part plutot que de renommer
		// l'original : sous Windows renameTo echoue tant que le fichier est encore
		// ouvert par la lecture, et l'ancienne configuration etait alors perdue.
		if (rawContent != null && !rawContent.isEmpty()) {
			File backup = new File(this.launcherConfig.getParentFile(),
					"launcher_config.corrompu-" + System.currentTimeMillis() + ".json");
			try (FileWriter fw = new FileWriter(backup, false)) {
				fw.write(rawContent);
				fw.flush();
				Logger.log("Ancienne configuration conservee sous " + backup.getName());
			} catch (IOException e) {
				Logger.err("Sauvegarde de l'ancienne configuration impossible : " + e.getMessage());
			}
		}

		writeDefaultConfigFile();

		try {
			String json = JsonUtil.loadJSON(this.launcherConfig.toURI().toURL().toString());
			this.configVersion = JsonUtil.getGson().fromJson(json, ConfigVersion.class);
			this.read = true;
		} catch (Exception e) {
			Logger.err("Configuration par defaut illisible : " + e.getMessage());
		}
	}
}
