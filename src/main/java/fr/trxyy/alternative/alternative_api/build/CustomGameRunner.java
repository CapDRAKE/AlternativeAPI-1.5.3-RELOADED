package fr.trxyy.alternative.alternative_api.build;
import java.util.Arrays;

import fr.flowarg.openlauncherlib.NewForgeVersionDiscriminator;
import fr.theshark34.openlauncherlib.LaunchException;
import fr.theshark34.openlauncherlib.external.ExternalLaunchProfile;
import fr.theshark34.openlauncherlib.external.ExternalLauncher;
import fr.theshark34.openlauncherlib.minecraft.AuthInfos;
import fr.theshark34.openlauncherlib.minecraft.GameFolder;
import fr.theshark34.openlauncherlib.minecraft.GameInfos;
import fr.theshark34.openlauncherlib.minecraft.GameTweak;
import fr.theshark34.openlauncherlib.minecraft.GameType;
import fr.theshark34.openlauncherlib.minecraft.GameVersion;
import fr.theshark34.openlauncherlib.minecraft.MinecraftLauncher;
import fr.trxyy.alternative.alternative_api.GameEngine;
import fr.trxyy.alternative.alternative_auth.account.AccountType;
import fr.trxyy.alternative.alternative_auth.account.Session;
import fr.trxyy.alternative.alternative_api.utils.config.EnumConfig;
import fr.trxyy.alternative.alternative_api.utils.config.LauncherConfig;

/**
 * @author Trxyy
 */
public class CustomGameRunner {

	public CustomGameRunner(Session auth, String vanillaVersion, String forgeVersion, String mcpVersion, fr.trxyy.alternative.alternative_api.GameFolder gameFolder, GameType gameType, GameTweak gameTweak[], GameEngine gameEngine) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {		
		
		NewForgeVersionDiscriminator discriminator = new NewForgeVersionDiscriminator(forgeVersion, vanillaVersion, mcpVersion);
		GameInfos infos = new GameInfos(gameFolder.getLocation(), new GameVersion(vanillaVersion, gameType.setNewForgeVersionDiscriminator(discriminator)), gameTweak);
		AuthInfos authInfos = new AuthInfos(auth.getUsername(), auth.getToken(), auth.getUuid());
		LauncherConfig config = new LauncherConfig(gameEngine);

		try {
			ExternalLaunchProfile profile = MinecraftLauncher.createExternalProfile(infos, GameFolder.FLOW_UPDATER, authInfos);
			ExternalLauncher launcher = new ExternalLauncher(profile);
			config.loadConfiguration();
			String ramToSet = (String) config.getValue(EnumConfig.RAM);
			profile.getVmArgs().addAll(Arrays.asList("-Xmx" + ramToSet.substring(0, 1) + "G"));
		//	profile.getVmArgs().addAll(Arrays.asList("--server play.hypixel.net", "--port 25565"));
 
			launcher.launch();			
			if(gameEngine.getStage() != null) gameEngine.getStage().hide();
		} catch (LaunchException e) {
			e.printStackTrace();
		}
	}
	
	public CustomGameRunner(Session auth, String vanillaVersion, fr.trxyy.alternative.alternative_api.GameFolder gameFolder, GameType gameType, GameEngine gameEngine) 
	{
		GameInfos infos = new GameInfos(gameFolder.getLocation(), new GameVersion(vanillaVersion, gameType), new GameTweak[] {});
		AuthInfos authInfos = new AuthInfos(auth.getUsername(), auth.getToken(), auth.getUuid());
		LauncherConfig config = new LauncherConfig(gameEngine);


		try {
			ExternalLaunchProfile profile = MinecraftLauncher.createExternalProfile(infos, GameFolder.FLOW_UPDATER, authInfos);
			ExternalLauncher launcher = new ExternalLauncher(profile);
			config.loadConfiguration();
			String ramToSet = (String) config.getValue(EnumConfig.RAM);
			profile.getVmArgs().addAll(Arrays.asList("-Xmx" + ramToSet.substring(0, 1) + "G"));
		//	profile.getVmArgs().addAll(Arrays.asList("--server play.hypixel.net", "--port 25565"));


			launcher.launch();			
			if(gameEngine.getStage() != null) gameEngine.getStage().hide();

		} catch (LaunchException e) {
			e.printStackTrace();
		}
	}

}