package fr.trxyy.alternative.alternative_auth.microsoft;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import fr.trxyy.alternative.alternative_auth.base.AuthConstants;
import fr.trxyy.alternative.alternative_auth.base.Logger;
import fr.trxyy.alternative.alternative_auth.microsoft.model.MicrosoftModel;

public class MicrosoftOAuthClient {

	/* ===== Microsoft endpoints ===== */

	/** Application Azure utilisée si le launcher n'en déclare pas une autre. */
	public static final String DEFAULT_CLIENT_ID = "33250748-fc1d-4053-825e-1e7b345d5d95";

	private static volatile String clientId = DEFAULT_CLIENT_ID;

	/**
	 * Déclare l'application Azure du launcher (à appeler une fois au démarrage, avant
	 * toute connexion). Les jetons enregistrés sont liés à l'application qui les a
	 * émis : après un changement, le joueur doit se reconnecter.
	 *
	 * @param id l'identifiant d'application (client) Azure ; null ou vide rétablit
	 *           l'application par défaut
	 */
	public static void setClientId(String id) {
		clientId = (id == null || id.trim().isEmpty()) ? DEFAULT_CLIENT_ID : id.trim();
	}

	/** @return l'identifiant d'application Azure utilisé pour l'authentification */
	public static String getClientId() {
		return clientId;
	}

	private static final String DEVICE_CODE_URL =
	        "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";

	private static final String TOKEN_URL =
	        "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

	private static final String SCOPE = "XboxLive.signin offline_access";

    /* ===================== MODEL ===================== */

    public static class DeviceCode {
        private final String deviceCode;
        private final String userCode;
        private final String verificationUri;
        private final int interval;
        private final int expiresIn;

        public DeviceCode(String deviceCode, String userCode,
                          String verificationUri, int interval, int expiresIn) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.interval = interval;
            this.expiresIn = expiresIn;
        }

        public String getDeviceCode()      { return deviceCode; }
        public String getUserCode()        { return userCode; }
        public String getVerificationUri() { return verificationUri; }
        public int getInterval()           { return interval; }
        public int getExpiresIn()          { return expiresIn; }
    }

    /* ===================== STEP 1 : DEVICE CODE ===================== */

    public DeviceCode requestDeviceCode() throws Exception {

        Map<String, String> body = new HashMap<>();
        body.put("client_id", clientId);
        body.put("scope", SCOPE);

        String json = postForm(DEVICE_CODE_URL, body);

        if (json == null || json.isEmpty())
            throw new IllegalStateException("Réponse vide de Microsoft /devicecode");

        JSONObject o = (JSONObject) new JSONParser().parse(json);

        if (o.containsKey("error")) {
            throw new IllegalStateException(
                    "Microsoft DeviceCode error : "
                    + o.get("error") + " – " + o.get("error_description"));
        }

        return new DeviceCode(
                (String) o.get("device_code"),
                (String) o.get("user_code"),
                (String) o.get("verification_uri"),
                ((Number) o.get("interval")).intValue(),
                ((Number) o.get("expires_in")).intValue()
        );
    }

    /* ===================== STEP 2 : POLLING TOKEN ===================== */

    public MicrosoftModel pollForToken(DeviceCode code) throws Exception {

        while (true) {

            Thread.sleep(code.getInterval() * 1000L);

            Map<String, String> body = new HashMap<>();
            body.put("client_id", clientId);
            body.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            body.put("device_code", code.getDeviceCode());

            String json = postForm(TOKEN_URL, body);
            if (json == null || json.isEmpty())
                continue;

            JSONObject o = (JSONObject) new JSONParser().parse(json);

            if (o.containsKey("access_token")) {
                return AuthConstants.getGson().fromJson(json, MicrosoftModel.class);
            }

            String err = (String) o.get("error");

            if ("authorization_pending".equals(err))
                continue;

            if ("slow_down".equals(err))
                continue;

            throw new IllegalStateException("Device Flow error : " + json);
        }
    }

    /* ===================== CONNEXION PAR NAVIGATEUR ===================== */

    /**
     * Échange le code reçu après une connexion dans le navigateur (voir
     * MicrosoftBrowserAuth) contre les jetons Microsoft.
     *
     * @param code         le code d'autorisation renvoyé par Microsoft
     * @param redirectUri  exactement l'adresse de retour utilisée pour la demande
     * @param codeVerifier le secret PKCE dont le défi a accompagné la demande
     */
    public MicrosoftModel exchangeAuthorizationCode(String code, String redirectUri, String codeVerifier) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("client_id", clientId);
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", redirectUri);
        body.put("code_verifier", codeVerifier);
        body.put("scope", SCOPE);

        String json = postForm(TOKEN_URL, body);
        if (json == null || json.isEmpty())
            throw new IllegalStateException("Réponse vide de Microsoft /token");

        JSONObject o = (JSONObject) new JSONParser().parse(json);
        if (!o.containsKey("access_token")) {
            throw new IllegalStateException("Microsoft token error : "
                    + o.get("error") + " – " + o.get("error_description"));
        }
        return AuthConstants.getGson().fromJson(json, MicrosoftModel.class);
    }

    /** @return l'adresse de la page de connexion Microsoft pour ces paramètres */
    static String buildAuthorizeUrl(String redirectUri, String state, String codeChallenge) throws Exception {
        return AuthConstants.MICROSOFT_BASE_URL
                + "?client_id=" + URLEncoder.encode(clientId, "UTF-8")
                + "&response_type=code"
                + "&response_mode=query"
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
                + "&scope=" + URLEncoder.encode(SCOPE, "UTF-8")
                + "&state=" + URLEncoder.encode(state, "UTF-8")
                + "&code_challenge=" + URLEncoder.encode(codeChallenge, "UTF-8")
                + "&code_challenge_method=S256"
                // Laisse toujours choisir le compte : indispensable pour changer de compte
                // quand le navigateur est déjà connecté à un autre compte Microsoft.
                + "&prompt=select_account";
    }

    /* ===================== HTTP FORM ===================== */

    private static String postForm(String endpoint, Map<String, String> params) throws Exception {

        StringBuilder data = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) data.append("&");
            first = false;
            data.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                .append("=")
                .append(URLEncoder.encode(e.getValue(), "UTF-8"));
        }

        HttpsURLConnection c = (HttpsURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        c.setRequestProperty("Accept", "application/json");

        try (OutputStream os = c.getOutputStream()) {
            os.write(data.toString().getBytes(StandardCharsets.UTF_8));
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        c.getResponseCode() == 200
                                ? c.getInputStream()
                                : c.getErrorStream(),
                        StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null)
                sb.append(line);

            // Jetons masques : la reponse brute contient access_token / refresh_token.
            Logger.log("POST " + endpoint + " → " + AuthConstants.redactSecrets(sb.toString()));
            return sb.toString();
        }
    }
    
    public MicrosoftModel refreshWithToken(String refreshToken) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("client_id", clientId);
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", refreshToken);
        body.put("scope", SCOPE);

        String json = postForm(TOKEN_URL, body);
        return AuthConstants.getGson().fromJson(json, MicrosoftModel.class);
    }

}
