package fr.trxyy.alternative.alternative_api.minecraft.json;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import fr.trxyy.alternative.alternative_api.minecraft.utils.CompatibilityRule;
import fr.trxyy.alternative.alternative_api.minecraft.utils.Substitutor;
import fr.trxyy.alternative.alternative_api.utils.OperatingSystem;

/**
 * @author Trxyy
 */
public class MinecraftLibrary {

    private Substitutor SUBSTITUTOR = new Substitutor(new HashMap<String, String>());

    protected String name;
    public List<CompatibilityRule> rules;
    protected Map<OperatingSystem, String> natives;
    protected MinecraftRules extract;
    protected LibraryDownloadInfo downloads;
    private String url;
    private boolean skipped = false;

    public MinecraftLibrary() {}

    public MinecraftLibrary(String name) {
        if ((name == null) || (name.length() == 0)) {
            throw new IllegalArgumentException("Library name cannot be null or empty");
        }
        this.name = name;
    }

    public boolean appliesToCurrentEnvironment() {
        if (this.rules == null) return true;
        CompatibilityRule.Action lastAction = CompatibilityRule.Action.disallow;

        for (CompatibilityRule compatibilityRule : this.rules) {
            CompatibilityRule.Action action = compatibilityRule.getAppliedAction();
            if (action != null) lastAction = action;
        }
        return (lastAction == CompatibilityRule.Action.allow);
    }

    public MinecraftLibrary(MinecraftLibrary library) {
        this.name = library.name;
        this.url = library.url;
        if (library.extract != null) this.extract = new MinecraftRules(library.extract);

        if (library.rules != null) {
            this.rules = new ArrayList<>();
            for (CompatibilityRule compatibilityRule : library.rules) {
                this.rules.add(new CompatibilityRule(compatibilityRule));
            }
        }

        if (library.natives != null) {
            this.natives = new LinkedHashMap<>();
            for (Map.Entry<OperatingSystem, String> entry : library.getNatives().entrySet()) {
                this.natives.put(entry.getKey(), entry.getValue());
            }
        }

        if (library.downloads != null) {
            this.downloads = new LibraryDownloadInfo(library.downloads);
        }
    }

    public MinecraftLibrary addNative(OperatingSystem operatingSystem, String name) {
        if ((operatingSystem == null) || (!operatingSystem.isSupported())) {
            throw new IllegalArgumentException("Cannot add native for unsupported OS");
        }
        if ((name == null) || (name.length() == 0)) {
            throw new IllegalArgumentException("Cannot add native for null or empty name");
        }
        if (this.natives == null) {
            this.natives = new EnumMap<>(OperatingSystem.class);
        }
        this.natives.put(operatingSystem, name);
        return this;
    }

    public Map<OperatingSystem, String> getNatives() {
        return this.natives;
    }

    public boolean hasNatives() {
        return this.natives != null;
    }

    public MinecraftRules getExtractRules() {
        return this.extract;
    }

    public List<CompatibilityRule> getCompatibilityRules() {
        return this.rules;
    }

    public String getName() {
        return this.name;
    }

    public MinecraftLibrary setExtractRules(MinecraftRules rules) {
        this.extract = rules;
        return this;
    }

    // ===================== FIX PARSING =====================

    private String[] parts() {
        if (this.name == null) throw new IllegalStateException("Library name is null");
        return this.name.split(":"); // NO LIMIT
    }

    private String groupId() {
        String[] p = parts();
        if (p.length < 3) throw new IllegalStateException("Bad maven coords: " + this.name);
        return p[0];
    }

    private String artifactId() {
        String[] p = parts();
        if (p.length < 3) throw new IllegalStateException("Bad maven coords: " + this.name);
        return p[1];
    }

    private String version() {
        String[] p = parts();
        if (p.length < 3) throw new IllegalStateException("Bad maven coords: " + this.name);
        return p[2]; // ALWAYS the 3rd segment
    }

    /**
     * @return The Artifact base directory (group/artifact/version)
     */
    public String getArtifactBaseDir() {
        return String.format("%s/%s/%s",
                groupId().replace(".", "/"),
                artifactId(),
                version()
        );
    }

    public String getArtifactPath() {
        return getArtifactPath(null);
    }

    public String getArtifactPath(String classifier) {
        return String.format("%s/%s", getArtifactBaseDir(), getArtifactFilename(classifier));
    }

    public String getArtifactFilename(String classifier) {
        String result;
        if (classifier == null) {
            result = String.format("%s-%s.jar", artifactId(), version());
        } else {
            result = String.format("%s-%s-%s.jar", artifactId(), version(), classifier);
        }
        return SUBSTITUTOR.replace(result);
    }

    @Deprecated
    public String getArtifactCustom(String name) {
        String[] split = name.split(":");
        String libName = split[1];
        String libVersion = split[2];
        return libName + "-" + libVersion + ".jar";
    }

    /**
     * @param nativeClassifierKey ex: natives-windows, natives-windows-x86, natives-linux...
     * @return native jar filename
     */
    public String getArtifactNatives(String nativeClassifierKey) {
        // Si on a le path officiel dans downloads.classifiers, utilise le filename exact
        try {
            if (downloads != null && downloads.getClassifiers() != null
                    && downloads.getClassifiers().get(nativeClassifierKey) != null
                    && downloads.getClassifiers().get(nativeClassifierKey).getPath() != null) {

                String p = downloads.getClassifiers().get(nativeClassifierKey).getPath();
                return new File(p).getName();
            }
        } catch (Exception ignored) {}

        // Fallback safe : artifact-version-<classifier>.jar
        return artifactId() + "-" + version() + "-" + nativeClassifierKey + ".jar";
    }

    public String getPlainName() {
        return groupId() + "." + artifactId();
    }

    public boolean isSkipped() {
        return this.skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public LibraryDownloadInfo getDownloads() {
        return downloads;
    }

    public void setDownloads(LibraryDownloadInfo downloads) {
        this.downloads = downloads;
    }

    public String toString() {
        return "Library{name='" + this.name + '\'' + ", rules=" + this.rules + ", natives=" + this.natives
                + ", extract=" + this.extract + '}';
    }
}
