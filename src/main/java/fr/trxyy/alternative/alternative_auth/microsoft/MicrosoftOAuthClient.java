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
	private static final String CLIENT_ID = "33250748-fc1d-4053-825e-1e7b345d5d95";

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
        body.put("client_id", CLIENT_ID);
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
            body.put("client_id", CLIENT_ID);
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

            Logger.log("POST " + endpoint + " → " + sb);
            return sb.toString();
        }
    }
    
    public MicrosoftModel refreshWithToken(String refreshToken) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("client_id", CLIENT_ID);
        body.put("grant_type", "refresh_token");
        body.put("refresh_token", refreshToken);
        body.put("scope", SCOPE);

        String json = postForm(TOKEN_URL, body);
        return AuthConstants.getGson().fromJson(json, MicrosoftModel.class);
    }

}
