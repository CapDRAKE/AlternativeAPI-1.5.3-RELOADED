package fr.trxyy.alternative.alternative_api.utils.config;

public enum EnumConfig {
	
	TOKEN("token",""),
	UUID("uuid",""),
	USERNAME("username", "Player"),
	VERSION("version", "1.20.6"),
	RAM("allocatedram", 2.0),
	GAME_SIZE("gamesize", "0"),
	AUTOLOGIN("autologin", false),
	VM_ARGUMENTS("vmarguments", "-XX:+CMSIncrementalMode"),
	USE_VM_ARGUMENTS("usevmarguments", false),
	USE_MUSIC("usemusic",true),
	USE_DISCORD("usediscord", true),
	USE_OPTIFINE("useOptifine", true),
	USE_FORGE("useforge", false),
	USE_PREMIUM("usePremium", false),
	USE_MICROSOFT("useMicrosoft", false),
	USE_CONNECT("useConnect", false),
	PASSWORD("password", ""),
	REMEMBER_ME("rememberme", false),
	DATE("date", ""),
	LANGUAGE("language","Fran�ais");
	
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
