package fr.trxyy.alternative.alternative_api;


import fr.trxyy.alternative.alternative_api.minecraft.json.*;

import java.util.*;

/**
 * @author Trxyy
 */
public class GameForge {

	private Arguments arguments;

	private List<Forge1_17_HigherLibrary> libraries;

	public List<Forge1_17_HigherLibrary> getLibraries() {
		return libraries;
	}

	public Arguments getArguments() {
		return arguments;
	}

	public class Arguments {
		private List<String> game;
		private List<String> jvm;


		public List<String> getGame() {
			return game;
		}

		public List<String> getJvm() {
			return jvm;
		}
	}

}
