package fr.trxyy.alternative.alternative_api.maintenance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.trxyy.alternative.alternative_api.GameEngine;

/**
 * @author Trxyy
 */
public class GameMaintenance {

	/**
	 * The Maintenance enum
	 */
	public Maintenance maintenance;
	/**
	 * The GameEngine instance
	 */
	public GameEngine engine;
	/**
	 * Block access to launcher
	 */
	public boolean block_access = false;

	/**
	 * The Constructor
	 * @param enumMaintenance The enum of Maintenance
	 * @param eng The gameEngine instance
	 */
	public GameMaintenance(Maintenance enumMaintenance, GameEngine eng) {
		this.maintenance = enumMaintenance;
		this.engine = eng;
	}

	/**
	 * Read the status.json file in url
	 * If "Ok", launcher will continue
	 * If Other text but not "Ok", the launcher will display a Alert with the content of the status.json file.
	 * Also accepts the legacy plain-text status.cfg format for backward compatibility.
	 * @return The status content
	 * @throws IOException
	 */
	public String readMaintenance() throws IOException {
		URL url = new URL(this.engine.getGameLinks().getMaintenanceUrl());
		URLConnection connection = url.openConnection();
		connection.setRequestProperty("User-Agent", "MajestyLauncher");
		if (connection instanceof HttpURLConnection) {
			((HttpURLConnection) connection).setInstanceFollowRedirects(true);
		}
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(10000);

		StringBuilder raw = new StringBuilder();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
			String inputLine;
			while ((inputLine = in.readLine()) != null) {
				raw.append(inputLine).append('\n');
			}
		}

		return parseStatus(raw.toString().trim());
	}

	/**
	 * @param raw The raw body of the status file (JSON or legacy plain text)
	 * @return The status value ("Ok" or a maintenance message)
	 */
	private String parseStatus(String raw) {
		if (raw.isEmpty()) {
			return raw;
		}
		try {
			JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
			if (obj.has("status") && !obj.get("status").isJsonNull()) {
				return obj.get("status").getAsString();
			}
		} catch (Exception ignored) {
			// Pas du JSON valide : on suppose l'ancien format texte brut (status.cfg)
		}
		return raw;
	}

	/**
	 * @return if is in Maintenance or not
	 */
	public Maintenance getMaintenance() {
		return this.maintenance;
	}

	/**
	 * Set the maintenance
	 * @param maintenance_ The maintenance enum
	 */
	public void setMaintenance(Maintenance maintenance_) {
		this.maintenance = maintenance_;
	}

	/**
	 * @return Is access blocked ?
	 */
	public boolean isAccessBlocked() {
		return block_access;
	}

	/**
	 * Set if the access to the launcher is blocked or not
	 * @param blckd The boolean to set if blocked or not
	 */
	public void setAccessBlocked(boolean blckd) {
		this.block_access = blckd;
	}

}
