package fr.trxyy.alternative.alternative_api.minecraft.json;

public class Forge1_17_HigherLibrary {

    private static final String FORGE_MAVEN_BASE = "https://maven.minecraftforge.net/";

    private String name;
    private Downloads downloads;

    public Downloads getDownloads() {
        return downloads;
    }

    public String getName() {
        return name;
    }

    public String getArtifactPath() {
        if (downloads != null && downloads.getArtifact() != null && downloads.getArtifact().getPath() != null && !downloads.getArtifact().getPath().isEmpty()) {
            return downloads.getArtifact().getPath();
        }
        return deriveArtifactPathFromName(name);
    }

    public String getArtifactUrl() {
        if (downloads != null && downloads.getArtifact() != null && downloads.getArtifact().getUrl() != null && !downloads.getArtifact().getUrl().isEmpty()) {
            return downloads.getArtifact().getUrl();
        }
        String path = getArtifactPath();
        return path == null ? null : FORGE_MAVEN_BASE + path;
    }

    public String getArtifactSha1() {
        if (downloads != null && downloads.getArtifact() != null) {
            return downloads.getArtifact().getSha1();
        }
        return null;
    }

    private static String deriveArtifactPathFromName(String coordinate) {
        if (coordinate == null || coordinate.trim().isEmpty()) return null;

        String coords = coordinate.trim();
        String extension = "jar";
        int extIdx = coords.indexOf('@');
        if (extIdx >= 0 && extIdx < coords.length() - 1) {
            extension = coords.substring(extIdx + 1);
            coords = coords.substring(0, extIdx);
        }

        String[] parts = coords.split(":");
        if (parts.length < 3) return null;

        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length >= 4 ? parts[3] : null;

        StringBuilder sb = new StringBuilder();
        sb.append(group.replace('.', '/')).append('/');
        sb.append(artifact).append('/');
        sb.append(version).append('/');
        sb.append(artifact).append('-').append(version);
        if (classifier != null && !classifier.isEmpty()) {
            sb.append('-').append(classifier);
        }
        sb.append('.').append(extension);
        return sb.toString();
    }

    public class Artifact{
        private String path;
        private String url;
        private String sha1;
        private int size;

        public String getPath() {
            return path;
        }

        public String getUrl() {
            return url;
        }

        public String getSha1() {
            return sha1;
        }

        public int getSize() {
            return size;
        }
    }

    public class Downloads {
        private Artifact artifact;

        public Artifact getArtifact() {
            return artifact;
        }
    }
}
