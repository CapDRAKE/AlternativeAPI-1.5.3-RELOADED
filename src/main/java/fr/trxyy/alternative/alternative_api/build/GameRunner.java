package fr.trxyy.alternative.alternative_api.build;

import fr.trxyy.alternative.alternative_api.*;
import fr.trxyy.alternative.alternative_api.minecraft.json.*;
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
    public void launch() throws Exception
    {
    	ArrayList<String> commands = this.getLaunchCommand();
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.redirectInput(Redirect.INHERIT);
        processBuilder.redirectOutput(Redirect.INHERIT);
        processBuilder.redirectError(Redirect.INHERIT);
		processBuilder.directory(engine.getGameFolder().getGameDir());
		processBuilder.redirectErrorStream(true);
		String cmds = "";
		for (String command : commands) {
			cmds += command + " ";
		}
		String[] ary = cmds.split(" ");
		Logger.log("Launching: " + hideAccessToken(ary));
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
        
		
		if (this.engine.isOnline()) {
			if (this.engine.getMinecraftVersion().getJavaVersion() != null) {
				String component = this.engine.getMinecraftVersion().getJavaVersion().getComponent();
				if (component != null) {
					commands.add(OperatingSystem.getJavaPath(this.engine));
				}
				else {
					commands.add(OperatingSystem.getJavaPath());
				}
			} else {
				commands.add(OperatingSystem.getJavaPath());
			}
		}
		else {
			if (this.engine.getGameUpdater().getLocalVersion().getJavaVersion() != null) {
				String component = this.engine.getGameUpdater().getLocalVersion().getJavaVersion().getComponent();
				if (component != null) {
					commands.add(OperatingSystem.getJavaPath(this.engine));
				} else {
					commands.add(OperatingSystem.getJavaPath());
				}
			} else {
				commands.add(OperatingSystem.getJavaPath());
			}
		}
		
        commands.add("-XX:-UseAdaptiveSizePolicy");
		
		if (engine.getJVMArguments() != null) {
			commands.addAll(engine.getJVMArguments().getJVMArguments());
		}

		if (engine.getGameStyle().equals(GameStyle.FORGE_1_17_HIGHER) || engine.getGameStyle().equals(GameStyle.FORGE_1_19_HIGHER)) {
			commands.addAll(this.getForgeJVMArguments());
			Logger.log(String.valueOf(this.getForgeJVMArguments()));
		}
		commands.add("-Dbsl.debug=True"); // important to debug

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
		}
		else {
			if (this.engine.getGameUpdater().getLocalVersion().getJavaVersion() != null) {
				commands.add("-XX:+UnlockExperimentalVMOptions");
				commands.add("-XX:+UseG1GC");
				commands.add("-XX:G1NewSizePercent=20");
				commands.add("-XX:G1ReservePercent=20");
				commands.add("-XX:MaxGCPauseMillis=50");
				commands.add("-XX:G1HeapRegionSize=32M");
			}
		}
		
		if (this.engine.getMinecraftVersion().getLogging() != null) {
			File log4jFile = new File(this.engine.getGameFolder().getLogConfigsDir(), this.engine.getMinecraftVersion().getLogging().getClient().getFile().getId());
			commands.add(this.engine.getMinecraftVersion().getLogging().getClient().getArgument().replace("${path}", log4jFile.getAbsolutePath()));
		}
		if (!this.engine.getGameStyle().equals(GameStyle.VANILLA_1_19_HIGHER) || !this.engine.getGameStyle().equals(GameStyle.FORGE_1_19_HIGHER))
			commands.add("-Djava.library.path=" + engine.getGameFolder().getNativesDir().getAbsolutePath());
		commands.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
		commands.add("-Dfml.ignorePatchDiscrepancies=true");
		

		boolean is32Bit = "32".equals(System.getProperty("sun.arch.data.model"));
		String defaultArgument = is32Bit ? "-Xmx512M -Xmn128M" : "-Xmx1G -Xmn128M";
		if (engine.getGameMemory() != null) {
			defaultArgument = is32Bit ? "-Xmx512M -Xmn128M" : "-Xmx" + engine.getGameMemory().getCount() + " -Xmn128M";
		}
		String str[] = defaultArgument.split(" ");
		List<String> args = Arrays.asList(str);
		commands.addAll(args);

		commands.add("-cp");
		commands.add("\"" + GameUtils.constructClasspath(engine) + "\"");
		commands.add(engine.getGameStyle().getMainClass());

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
				sb.append(newerArgumentsString[i] + " ");
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
		if (!engine.getGameStyle().getSpecificsArguments().equals("")) {
			commands.addAll(getForgeArguments());
		}

		/** ----- Direct connect to a server if required. ----- */
		if (engine.getGameConnect() != null) {
			System.out.println(engine.getGameConnect().getIp() + " " + engine.getGameConnect().getPort() );
			commands.add("--server=" + engine.getGameConnect().getIp());
			commands.add("--port=" + engine.getGameConnect().getPort());
		}

		/** ----- Tweak Class if required ----- */
		if (engine.getGameStyle().equals(GameStyle.FORGE_1_7_10_OLD) || engine.getGameStyle().equals(GameStyle.FORGE_1_8_TO_1_12_2) || engine.getGameStyle().equals(GameStyle.OPTIFINE)) {
			commands.add("--tweakClass");
			commands.add(engine.getGameStyle().getTweakArgument());
		}
		return commands;
	}

	private List<String> getForgeJVMArguments() {
		List<String> forgeArgs = this.engine.getGameForge().getArguments().getJvm();
		List<String> jvmArgs = new ArrayList<>();
		for (String arg : forgeArgs) {
			jvmArgs.add(arg.replace("${version_name}","minecraft")
					.replace("${library_directory}",this.engine.getGameFolder().getLibsDir().getAbsolutePath())
					.replace("${classpath_separator}",System.getProperty("path.separator")));
		}
		return jvmArgs;
	}

	/**
	 * Get forge arguments (If gameStyle != Vanilla or Vanilla_Plus)
	 * @return A List<String> of specifics arguments
	 */
	private List<String> getForgeArguments() {
		return engine.getGameForge().getArguments().getGame();
	}

	/**
	 * Get minecraft launch arguments for old versions of Minecraft
	 * @return a String[] with multiples arguments
	 */
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
        for (int i = 0; i < arguments.length; i++) {
            if (i > 0 && Objects.equals(arguments[i-1], "--accessToken")) {
                output.add("????????");
            } else {
                output.add(arguments[i]);
            }
        }
        return output;
    }
}