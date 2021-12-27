package fr.trxyy.alternative.alternative_api.utils.config;

public enum EnumConfig {
	
	TOKEN("token",""),
	USERNAME("username", "Player"),
	VERSION("version", ""),
	RAM("allocatedram", 1.0),
	GAME_SIZE("gamesize", "0"),
	AUTOLOGIN("autologin", false),
	VM_ARGUMENTS("vmarguments", "-XX:+CMSIncrementalMode"),
	USE_VM_ARGUMENTS("usevmarguments", false),
	USE_MUSIC("usemusic",true),
	USE_DISCORD("usediscord", true),
	USE_FORGE("useforge", true),
	USE_PREMIUM("usePremium", false),
	USE_MICROSOFT("useMicrosoft", false),
	PASSWORD("password", ""),
	REMEMBER_ME("rememberme", false);
	
	public String option;
	public Object def;
	
	EnumConfig(String opt, Object d) {
		this.option = opt;
		this.def = d;
	}
	
	public String getOption() {
		return this.option;
	}
	
	public Object getDefault() {
		return this.def;
	}
	
}
