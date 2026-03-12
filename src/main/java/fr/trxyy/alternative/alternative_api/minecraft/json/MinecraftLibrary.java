package fr.trxyy.alternative.alternative_api.minecraft.json;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import fr.trxyy.alternative.alternative_api.minecraft.utils.Arch;
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
        return this.name.split(":");
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

    private String rawVersionToken() {
        String[] p = parts();
        if (p.length < 3) throw new IllegalStateException("Bad maven coords: " + this.name);
        return p[2];
    }

    private String rawClassifierToken() {
        String[] p = parts();
        if (p.length >= 4) {
            return p[3];
        }
        return null;
    }

    private String stripExtension(String token) {
        if (token == null) return null;
        int at = token.indexOf('@');
        if (at >= 0) {
            return token.substring(0, at);
        }
        return token;
    }

    private String extractExtension(String token) {
        if (token == null) return null;
        int at = token.indexOf('@');
        if (at >= 0 && at + 1 < token.length()) {
            return token.substring(at + 1);
        }
        return null;
    }

    private String version() {
        return stripExtension(rawVersionToken());
    }

    public String getDeclaredClassifier() {
        return stripExtension(rawClassifierToken());
    }

    public boolean hasDeclaredClassifier() {
        String classifier = getDeclaredClassifier();
        return classifier != null && !classifier.trim().isEmpty();
    }

    public String getDeclaredExtension() {
        String ext = extractExtension(rawClassifierToken());
        if (ext == null || ext.trim().isEmpty()) {
            ext = extractExtension(rawVersionToken());
        }
        if (ext == null || ext.trim().isEmpty()) {
            ext = "jar";
        }
        return ext;
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
        if (downloads != null && downloads.getArtifact() != null && downloads.getArtifact().getPath() != null
                && !downloads.getArtifact().getPath().trim().isEmpty()) {
            return downloads.getArtifact().getPath();
        }
        return getArtifactPath(null);
    }

    public String getArtifactPath(String classifier) {
        if (classifier == null && downloads != null && downloads.getArtifact() != null && downloads.getArtifact().getPath() != null
                && !downloads.getArtifact().getPath().trim().isEmpty()) {
            return downloads.getArtifact().getPath();
        }
        return String.format("%s/%s", getArtifactBaseDir(), getArtifactFilename(classifier));
    }

    public String getArtifactFilename(String classifier) {
        String effectiveClassifier = classifier;
        if (effectiveClassifier == null || effectiveClassifier.trim().isEmpty()) {
            effectiveClassifier = getDeclaredClassifier();
        }

        String ext = getDeclaredExtension();
        String result;
        if (effectiveClassifier == null || effectiveClassifier.trim().isEmpty()) {
            result = String.format("%s-%s.%s", artifactId(), version(), ext);
        } else {
            result = String.format("%s-%s-%s.%s", artifactId(), version(), effectiveClassifier, ext);
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
        try {
            if (downloads != null && downloads.getClassifiers() != null
                    && downloads.getClassifiers().get(nativeClassifierKey) != null
                    && downloads.getClassifiers().get(nativeClassifierKey).getPath() != null) {

                String p = downloads.getClassifiers().get(nativeClassifierKey).getPath();
                return new File(p).getName();
            }
        } catch (Exception ignored) {}

        return artifactId() + "-" + version() + "-" + nativeClassifierKey + ".jar";
    }

    public boolean isDeclaredNativeLibrary() {
        String classifier = getDeclaredClassifier();
        return classifier != null && classifier.toLowerCase().contains("natives-");
    }

    public boolean declaredNativeMatchesCurrentEnvironment() {
        if (!isDeclaredNativeLibrary()) {
            return true;
        }

        String classifier = getDeclaredClassifier().toLowerCase();
        OperatingSystem currentOs = OperatingSystem.getCurrent();

        boolean osMatches = false;
        if (currentOs == OperatingSystem.WINDOWS) {
            osMatches = classifier.contains("windows");
        } else if (currentOs == OperatingSystem.LINUX) {
            osMatches = classifier.contains("linux");
        } else if (currentOs == OperatingSystem.OSX) {
            osMatches = classifier.contains("osx") || classifier.contains("mac");
        }

        if (!osMatches) {
            return false;
        }

        if (classifier.contains("arm64") || classifier.contains("aarch64")) {
            String osArch = System.getProperty("os.arch", "").toLowerCase();
            return osArch.contains("arm") || osArch.contains("aarch64");
        }

        if (classifier.contains("x86")) {
            return Arch.CURRENT == Arch.x86;
        }

        if (currentOs == OperatingSystem.WINDOWS && Arch.CURRENT == Arch.x86) {
            return false;
        }

        return true;
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
