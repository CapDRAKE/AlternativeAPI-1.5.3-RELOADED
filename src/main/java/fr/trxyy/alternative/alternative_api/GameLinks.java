package fr.trxyy.alternative.alternative_api;

/**
 * @author Trxyy
 */
public class GameLinks {

    /**
     * The base url, ex: http://mywebsite.com/
     */
    public String BASE_URL;
    /**
     * The json url, ex: http://mywebsite.com/mc_version.json
     */
    public String JSON_URL;
    /**
     * The json name, 1.7.10.json for 1.7.10 version
     */
    public String JSON_NAME;
    /**
     * The maintenance url, ex: http://mywebsite.com/status.cfg
     */
    public String MAINTENANCE;
    /**
     * The ignore list, ex: http://mywebsite.com/ignore.cfg
     */
    public String IGNORE_LIST;
    /**
     * The delete list, ex: http://mywebsite.com/delete.cfg
     */
    public String DELETE_LIST;
    /**
     * The custom files url, ex: http://mywebsite.com/files/
     */
    public String CUSTOM_FILES_URL;

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) return "";
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    /**
     * Constructor historique : garde le comportement "default lists"
     */
    public GameLinks(String baseUrl, String jsonName) {
        this.BASE_URL = normalizeBaseUrl(baseUrl);

        this.JSON_NAME = jsonName;
        this.JSON_URL = this.BASE_URL + jsonName;          // FIX: utiliser BASE_URL

        this.IGNORE_LIST = this.BASE_URL + "ignore.cfg";   // FIX: utiliser BASE_URL
        this.DELETE_LIST = this.BASE_URL + "delete.cfg";
        this.CUSTOM_FILES_URL = this.BASE_URL + "files/";
        this.MAINTENANCE = this.BASE_URL + "status.cfg";
    }

    /**
     * Nouveau: JSON_URL complet (ex: Mojang), et tous les autres liens optionnels.
     * Si ignore/delete/maintenance/customFilesUrl == null/empty => feature désactivée.
     */
    public GameLinks(String jsonUrl, String ignoreListUrl, String deleteListUrl, String maintenanceUrl, String customFilesUrl) {
        this.JSON_URL = jsonUrl;
        this.JSON_NAME = extractFileName(jsonUrl);
        this.BASE_URL = extractBaseUrl(jsonUrl);

        this.IGNORE_LIST = emptyToNull(ignoreListUrl);
        this.DELETE_LIST = emptyToNull(deleteListUrl);
        this.MAINTENANCE = emptyToNull(maintenanceUrl);
        this.CUSTOM_FILES_URL = emptyToNull(customFilesUrl);
    }

    /**
     * Nouveau: juste un JSON_URL (ex: Mojang) => ignore/delete/maintenance/custom désactivés.
     */
    public GameLinks(String jsonUrl) {
        this(jsonUrl, null, null, null, null);
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String extractBaseUrl(String url) {
        if (url == null) return "";
        int idx = url.lastIndexOf('/');
        if (idx <= 0) return "";
        return url.substring(0, idx + 1);
    }

    private static String extractFileName(String url) {
        if (url == null) return null;
        int idx = url.lastIndexOf('/');
        if (idx < 0 || idx >= url.length() - 1) return url;
        return url.substring(idx + 1);
    }

    public String getBaseUrl() {
        return this.BASE_URL;
    }

    public String getJsonName() {
        return this.JSON_NAME;
    }

    public String getMaintenanceUrl() {
        return this.MAINTENANCE;
    }

    public String getJsonUrl() {
        return this.JSON_URL;
    }

    public String getIgnoreListUrl() {
        return this.IGNORE_LIST;
    }

    public String getDeleteListUrl() {
        return this.DELETE_LIST;
    }

    public String getCustomFilesUrl() {
        return this.CUSTOM_FILES_URL;
    }
}
