package fr.trxyy.alternative.alternative_api.assets;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.annotations.SerializedName;

/**
 * @author Trxyy
 */
public class AssetIndex {
	/**
	 * The default asset name
	 */
	public static final String DEFAULT_ASSET_NAME = "legacy";
	/**
	 * The objects stocked inside a Map<String, AssetObject>
	 */
	private final Map<String, AssetObject> objects;
	/**
	 * Is Virtual ?
	 */
	private boolean virtual;
	/**
	 * Index "pre-1.6" : ressources lues dans le dossier "resources" du jeu
	 */
	@SerializedName("map_to_resources")
	private boolean mapToResources;

	/**
	 * The Constructor
	 */
	public AssetIndex() {
		this.objects = new LinkedHashMap<String, AssetObject>();
	}

	/**
	 * @return The objects list as a Map
	 */
	public Map<String, AssetObject> getObjects() {
		return this.objects;
	}

	/**
	 * @return Get the unique Objects as a Set
	 */
	public Set<AssetObject> getUniqueObjects() {
		return new HashSet<AssetObject>(this.objects.values());
	}

	/**
	 * @return If it's virtual
	 */
	public boolean isVirtual() {
		return this.virtual;
	}

	/**
	 * @return If the assets must be copied to the game's "resources" folder (before 1.6)
	 */
	public boolean isMapToResources() {
		return this.mapToResources;
	}

}
