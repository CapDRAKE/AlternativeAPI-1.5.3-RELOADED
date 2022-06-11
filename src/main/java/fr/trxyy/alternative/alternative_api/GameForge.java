package fr.trxyy.alternative.alternative_api;


import java.util.*;

/**
 * @author Trxyy
 */
public class GameForge {

	private Arguments arguments;

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