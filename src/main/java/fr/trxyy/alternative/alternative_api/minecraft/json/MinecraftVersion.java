package fr.trxyy.alternative.alternative_api.minecraft.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Maps;

import fr.trxyy.alternative.alternative_api.assets.AssetIndexInfo;
import fr.trxyy.alternative.alternative_api.minecraft.java.JavaVersion;
import fr.trxyy.alternative.alternative_api.minecraft.log4j.Log4jLogging;

/**
 * @author Trxyy
 */
public class MinecraftVersion {

	/**
	 * The minecraft version id
	 */
	private String id;
	/**
	 * The inheritsFrom
	 */
	private String inheritsFrom;
	/**
	 * The minecraft arguments (old)
	 */
	private String minecraftArguments;
	/**
	 * The minecraft libraries
	 */
	private List<MinecraftLibrary> libraries;
	/**
	 * The main class
	 */
	private String mainClass;
	/**
	 * The assets
	 */
	private String assets;
	/**
	 * The asset Index
	 */
	private AssetIndexInfo assetIndex;
	/**
	 * The required downloads
	 */
	private MinecraftClient downloads;
	/**
	 * The minecraft arguments (new)
	 */
	public Map<ArgumentType, List<Argument>> arguments;
	/**
	 * The minecraft java version to use
	 */
	public JavaVersion javaVersion;
	/**
	 * The Logging file
	 */
	public Log4jLogging logging;

	/**
	 * The Constructor
	 */
	public MinecraftVersion() {
	}

	/**
	 * The Constructor
	 * @param version The MinecraftVersion
	 */
	public MinecraftVersion(MinecraftVersion version) {
		this.id = version.id;
		if (version.inheritsFrom != null) {
			this.inheritsFrom = version.inheritsFrom;
		}
		if (version.assetIndex != null) {
			this.assetIndex = version.assetIndex;
		}
		if (version.arguments != null) {
			this.arguments = Maps.newEnumMap(ArgumentType.class);
			for (Map.Entry<ArgumentType, List<Argument>> entry : version.arguments.entrySet()) {
				this.arguments.put(entry.getKey(), new ArrayList<Argument>(entry.getValue()));
			}
		}
	    this.minecraftArguments = version.minecraftArguments;
		this.libraries = version.libraries;
		this.mainClass = version.mainClass;
		this.assets = version.assets;
		this.logging = version.logging;
	}
	
	/**
	 * @return The Minecraft arguments (old)
	 */
	public String getMinecraftArguments() {
		return this.minecraftArguments;
	}

	/**
	 * @return The Minecraft libraries as a List
	 */
	public List<MinecraftLibrary> getLibraries() {
		return libraries;
	}

	/**
	 * @return The Minecraft required downloads
	 */
	public MinecraftClient getDownloads() {
		return downloads;
	}
	
	/**
	 * @return The Minecraft required java version
	 */
	public JavaVersion getJavaVersion() {
		return javaVersion;
	}
	
	/**
	 * @return The Logging
	 */
	public Log4jLogging getLogging() {
		return logging;
	}

	/**
	 * @return The version Id
	 */
	public String getId() {
		return id;
	}

	/**
	 * Set the Version Id
	 * @param idd The version id
	 */
	public void setId(String idd) {
		this.id = idd;
	}

	/**
	 * @return The inherits From
	 */
	public String getInheritsFrom() {
		return inheritsFrom;
	}

	/**
	 * Set the InheritsFrom
	 * @param inheritsFrom The inherits from version
	 */
	public void setInheritsFrom(String inheritsFrom) {
		this.inheritsFrom = inheritsFrom;
	}

	/**
	 * @return The main class
	 */
	public String getMainClass() {
		return mainClass;
	}

	/**
	 * Set the main class
	 * @param mainClass The main class
	 */
	public void setMainClass(String mainClass) {
		this.mainClass = mainClass;
	}

	/**
	 * @return The assets
	 */
	public String getAssets() {
		return assets;
	}

	/**
	 * Set the Assets
	 * @param assets The assets
	 */
	public void setAssets(String assets) {
		this.assets = assets;
	}

	/**
	 * @return The AssetIndexInfo
	 */
	public AssetIndexInfo getAssetIndex() {
		return assetIndex;
	}

	/**
	 * Set the asset Index
	 * @param s The asset Index version
	 */
	public void setAssetIndex(String s) {
		this.assets = s;
	}

	/**
	 * @return The arguments as a List (new)
	 */
	public Map<ArgumentType, List<Argument>> getArguments() {
		return this.arguments;
	}

	public static MinecraftVersion mergeInherited(MinecraftVersion parent, MinecraftVersion child) {
		if (parent == null) {
			return child;
		}
		if (child == null) {
			return parent;
		}

		MinecraftVersion merged = new MinecraftVersion();
		merged.id = child.id != null ? child.id : parent.id;
		merged.inheritsFrom = child.inheritsFrom;
		merged.minecraftArguments = child.minecraftArguments != null ? child.minecraftArguments : parent.minecraftArguments;
		merged.libraries = mergeLibraries(parent.libraries, child.libraries);
		merged.mainClass = child.mainClass != null ? child.mainClass : parent.mainClass;
		merged.assets = child.assets != null ? child.assets : parent.assets;
		merged.assetIndex = child.assetIndex != null ? child.assetIndex : parent.assetIndex;
		merged.downloads = child.downloads != null ? child.downloads : parent.downloads;
		merged.arguments = mergeArguments(parent.arguments, child.arguments);
		merged.javaVersion = child.javaVersion != null ? child.javaVersion : parent.javaVersion;
		merged.logging = child.logging != null ? child.logging : parent.logging;
		return merged;
	}

	private static List<MinecraftLibrary> mergeLibraries(List<MinecraftLibrary> parentLibraries, List<MinecraftLibrary> childLibraries) {
		LinkedHashMap<String, MinecraftLibrary> mergedLibraries = new LinkedHashMap<String, MinecraftLibrary>();

		if (parentLibraries != null) {
			for (MinecraftLibrary library : parentLibraries) {
				if (library == null) {
					continue;
				}
				mergedLibraries.put(resolveLibraryKey(library), new MinecraftLibrary(library));
			}
		}

		if (childLibraries != null) {
			for (MinecraftLibrary library : childLibraries) {
				if (library == null) {
					continue;
				}
				mergedLibraries.put(resolveLibraryKey(library), new MinecraftLibrary(library));
			}
		}

		return new ArrayList<MinecraftLibrary>(mergedLibraries.values());
	}

	private static Map<ArgumentType, List<Argument>> mergeArguments(Map<ArgumentType, List<Argument>> parentArguments,
			Map<ArgumentType, List<Argument>> childArguments) {
		if (parentArguments == null && childArguments == null) {
			return null;
		}

		Map<ArgumentType, List<Argument>> mergedArguments = Maps.newEnumMap(ArgumentType.class);
		appendArguments(mergedArguments, parentArguments);
		appendArguments(mergedArguments, childArguments);
		return mergedArguments;
	}

	private static void appendArguments(Map<ArgumentType, List<Argument>> target, Map<ArgumentType, List<Argument>> source) {
		if (target == null || source == null) {
			return;
		}

		for (Map.Entry<ArgumentType, List<Argument>> entry : source.entrySet()) {
			if (entry == null || entry.getKey() == null || entry.getValue() == null) {
				continue;
			}
			List<Argument> current = target.get(entry.getKey());
			if (current == null) {
				current = new ArrayList<Argument>();
				target.put(entry.getKey(), current);
			}
			current.addAll(new ArrayList<Argument>(entry.getValue()));
		}
	}

	private static String resolveLibraryKey(MinecraftLibrary library) {
		if (library == null) {
			return "null-library";
		}
		if (library.getName() != null && !library.getName().trim().isEmpty()) {
			return library.getName();
		}
		String artifactPath = library.getArtifactPath();
		if (artifactPath != null && !artifactPath.trim().isEmpty()) {
			return artifactPath;
		}
		return String.valueOf(System.identityHashCode(library));
	}
}
