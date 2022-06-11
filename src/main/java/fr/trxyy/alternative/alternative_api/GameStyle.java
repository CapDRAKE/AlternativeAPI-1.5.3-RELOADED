package fr.trxyy.alternative.alternative_api;

public enum GameStyle {
  VANILLA("net.minecraft.client.main.Main", "", ""),
  VANILLA_1_16_HIGHER("net.minecraft.client.main.Main", "", ""),
  VANILLA_PLUS("net.minecraft.client.main.Main", "", ""),
  VANILLA_PLUS_1_16_HIGHER("net.minecraft.client.main.Main", "", ""),
  OPTIFINE("net.minecraft.launchwrapper.Launch", "optifine.OptiFineTweaker", ""),
  FORGE_1_7_10_OLD("net.minecraft.launchwrapper.Launch", "cpw.mods.fml.common.launcher.FMLTweaker", ""),
  FORGE_1_8_TO_1_12_2("net.minecraft.launchwrapper.Launch", "net.minecraftforge.fml.common.launcher.FMLTweaker", ""),
  FORGE_1_13_HIGHER("cpw.mods.modlauncher.Launcher", "", "--launchTarget ${launch_target_fml} --fml.forgeVersion ${forge_version_fml} --fml.mcVersion ${mc_version_fml} --fml.forgeGroup ${forge_group_fml} --fml.mcpVersion ${mcp_version_fml}"),
  FORGE_1_17_HIGHER("cpw.mods.bootstraplauncher.BootstrapLauncher", "", "--launchTarget ${launch_target_fml} --fml.forgeVersion ${forge_version_fml} --fml.mcVersion ${mc_version_fml} --fml.forgeGroup ${forge_group_fml} --fml.mcpVersion ${mcp_version_fml}");
  
  private String mainClass;
  
  private String tweakArgument;
  
  private String specificsArguments;
  
  GameStyle(String main, String tweak, String args) {
    this.mainClass = main;
    this.tweakArgument = tweak;
    this.specificsArguments = args;
  }
  
  public String getMainClass() {
    return this.mainClass;
  }
  
  public String getTweakArgument() {
    return this.tweakArgument;
  }
  
  public String getSpecificsArguments() {
    return this.specificsArguments;
  }
}