package fr.trxyy.alternative.alternative_api.utils.config;

import java.util.Date;

public class ConfigVersion {

	
	private static ConfigVersion instance;
	
	/**
	 * The UUID
	 */
	public String uuid;
	/**
	 * The token
	 */
	public String token;
	/**
	 * The username
	 */
	public String username;
	/**
	 * The password
	 */
	public String password;
	/**
	 * The password
	 */
	public boolean rememberme;
	/**
	 * The RAM
	 */
	public String allocatedram;
	/**
	 * The VERSION
	 */
	  public String version;
	/**
	 * 
	 /* Using optifine ?
	  */
	  public boolean useOptifine;
		/**
		 * 
		 /* Using connect auto ?
		 */
	  public boolean useConnect;
		  
	  
	  /* Using Forge ?
		  */
		  public boolean useforge;
		  /*
		   * 
		   */
		 /* Using discord ?
		  */
    public boolean usediscord;
	 /* Using music ?
	  */
public boolean usemusic;

/* Using premium ?
 */
public boolean usePremium;
/* Using microsoft ?
 */
public boolean useMicrosoft;

	 /* The game size
	 */
	public String gamesize;
	/**
	 * Is auto Login
	 */
	public boolean autologin;
	/**
	 * The VM arguments
	 */
	public String vmarguments;
	/**
	 * Use custom vm arguments 
	 */
	public boolean usevmarguments;
	
	public String date;
	
	public String language;

	/**
	 * The Constructor
	 */
	public ConfigVersion(ConfigVersion o) {
		instance = o;
		this.token = o.token;
		this.useConnect = o.useConnect;
		this.uuid = o.uuid;
		this.username = o.username;
		this.allocatedram = o.allocatedram;
		this.version = o.version;
		this.useOptifine = o.useOptifine;
		this.useforge = o.useforge;
		this.usediscord = o.usediscord;
		this.usemusic = o.usemusic;
		this.gamesize = o.gamesize;
		this.autologin = o.autologin;
		this.vmarguments = o.vmarguments;
		this.usevmarguments = o.usevmarguments;
		this.useMicrosoft = o.useMicrosoft;
		this.password = o.password;
		this.rememberme = o.rememberme;
		this.usePremium = o.usePremium;
		this.date = o.date;
		this.language = o.language;
	}
	
	public String getLanguage() {
		return this.language;
	}
	
	public String getDate() {
		return this.date;
	}
	
	/**
	 * take token from config json
	 */
	public String getToken() {
		return this.token;
	}
	
	/**
	 * take UUID from config json
	 */
	public String getUUID() {
		return this.uuid;
	}

	/**
	 * Update multiple values in the config json
	 */
	public String getAllocatedRam() {
		return this.allocatedram;
	}

	public String getVersion() {
		return version;
	}
	/**
	 * Get the game size
	 */
	public String getGameSize() {
		return this.gamesize;
	}

	/**
	 * Is using autoLogin
	 */
	public boolean isAutoLogin() {
		return this.autologin;
	}

	/**
	 * Get the username
	 */
	public String getUsername() {
		return this.username;
	}
	
	/**
	 * Get the VM arguments
	 */
	public String getVMArguments() {
		return this.vmarguments;
	}
	
	/**
	 * Is using custom vm arguments
	 */
	public boolean useVMArguments() {
		return this.usevmarguments;
	}
	
	public boolean useOptifine() 
	{
		return this.useOptifine;
	}
	
	public boolean useForge() 
	{
		return this.useforge;
	}
	
	public boolean useDiscord() {
		return this.usediscord;
	}
	
	public boolean useMusic() {
		return this.usemusic;
	}
	
	public boolean usePremium() {
		return this.usePremium;
	}
	
	public boolean useMicrosoft() {
		return this.useMicrosoft;
	}
	
	public boolean useConnect() {
		return this.useConnect;
	}
	
	public boolean useConnect2() {
		return useConnect;
	}
	
	public boolean isRememberme() {
		return rememberme;
	}

	public static ConfigVersion getInstance() {
		return instance;
	}
}
