package fr.trxyy.alternative.alternative_api.minecraft.json;

public class Forge1_17_HeigherLibrary {
    private Downloads downloads;

    public Downloads getDownloads() {
        return downloads;
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
