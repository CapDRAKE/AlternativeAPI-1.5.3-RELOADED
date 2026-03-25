package fr.trxyy.alternative.alternative_auth.microsoft;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;

import org.json.simple.JSONObject;

import fr.trxyy.alternative.alternative_auth.account.Session;
import fr.trxyy.alternative.alternative_auth.base.AuthConstants;
import fr.trxyy.alternative.alternative_auth.base.Logger;
import fr.trxyy.alternative.alternative_auth.microsoft.model.MicrosoftModel;
import fr.trxyy.alternative.alternative_auth.microsoft.model.MinecraftMicrosoftModel;
import fr.trxyy.alternative.alternative_auth.microsoft.model.MinecraftProfileModel;
import fr.trxyy.alternative.alternative_auth.microsoft.model.XboxLiveModel;

/**
 * Auth Microsoft – flux Live ID v1.
 * URL /authorize.srf  → code
 * POST /token.srf     → access_token
 * puis chaîne Xbox → XSTS → Minecraft.
 */
public class MicrosoftXboxAuth {

    /* Live ID v1 constants */
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String AUTH_URL  = "https://login.live.com/oauth20_authorize.srf";
    private static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String REDIRECT  = "https://login.live.com/oauth20_desktop.srf";
    private static final String SCOPE = "XboxLive.signin offline_access";

    /*──────────────────── URL d’autorisation ────────────────────*/
    public String getAuthorizationUrl(String state) {
        try {
            StringBuilder sb = new StringBuilder(AUTH_URL)
                    .append("?client_id=").append(URLEncoder.encode(CLIENT_ID, "UTF-8"))
                    .append("&response_type=code")
                    .append("&redirect_uri=").append(URLEncoder.encode(REDIRECT, "UTF-8"))
                    .append("&scope=").append(URLEncoder.encode(SCOPE, "UTF-8"));
            if (state != null) sb.append("&state=").append(URLEncoder.encode(state, "UTF-8"));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de construire l’URL Live ID", e);
        }
    }

    /*────────────────── code ↔ access_token ──────────────────*/
    public MicrosoftModel getAuthorizationCode(ParamType type, String code) throws Exception {
        Map<Object,Object> body = new HashMap<>();
        body.put("client_id", CLIENT_ID);
        body.put("redirect_uri", REDIRECT);

        switch (type) {
            case AUTH:
                body.put("grant_type", "authorization_code");
                body.put("code", code);
                break;
            case REFRESH:
                body.put("grant_type", "refresh_token");
                body.put("refresh_token", code);
                break;
            default:
                throw new IllegalArgumentException("Seuls AUTH ou REFRESH sont valides.");
        }

        String json = postForm(TOKEN_URL, body);
        return AuthConstants.getGson().fromJson(json, MicrosoftModel.class);
    }

    /*────────────────── Xbox → XSTS → Minecraft ──────────────────*/
    public Session getLiveToken(String accessToken) throws Exception {
        String xblJson = postInformations(ParamType.XBL,
                AuthConstants.MICROSOFT_AUTHENTICATE_XBOX, accessToken, null);

        if (xblJson.isEmpty())
            throw new IllegalStateException("Réponse vide de Xbox Live authenticate.");

        XboxLiveModel xbl = AuthConstants.getGson().fromJson(xblJson, XboxLiveModel.class);

        String xstsJson = postInformations(ParamType.XSTS,
                AuthConstants.MICROSOFT_AUTHORIZE_XSTS, xbl.getToken(), null);

        if (xstsJson.isEmpty())
            throw new IllegalStateException("Réponse vide de XSTS.");

        XboxLiveModel xsts = AuthConstants.getGson().fromJson(xstsJson, XboxLiveModel.class);

        String mcJson = postInformations(ParamType.MC,
                AuthConstants.MICROSOFT_LOGIN_XBOX,
                xsts.getDisplayClaims().getUsers()[0].getUhs(),
                xsts.getToken());

        if (mcJson.isEmpty())
            throw new IllegalStateException("Réponse vide de login_with_xbox.");

        MinecraftMicrosoftModel mc = AuthConstants.getGson().fromJson(mcJson, MinecraftMicrosoftModel.class);
        return getMinecraftProfile(mc.getToken_type(), mc.getAccess_token());
    }

    private Session getMinecraftProfile(String tokenType, String mcAccessToken) {
        String profJson = connectToMinecraft(AuthConstants.MICROSOFT_MINECRAFT_PROFILE,
                                             tokenType + " " + mcAccessToken);
        if (profJson.isEmpty())
            throw new IllegalStateException("Réponse vide de minecraft/profile.");

        MinecraftProfileModel prof = AuthConstants.getGson().fromJson(profJson, MinecraftProfileModel.class);
        String uuid = prof.getId().replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5");

        return new Session(prof.getName(), mcAccessToken, uuid);
    }

    /*────────────────── Helpers HTTP ──────────────────*/
    private static String urlEncode(Object o) {
        try { return URLEncoder.encode(String.valueOf(o), "UTF-8"); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String postForm(String endpoint, Map<Object,Object> params) throws IOException {
        String data = params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));

        HttpsURLConnection c = (HttpsURLConnection) new URL(endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        c.setRequestProperty("Accept", "application/json");

        try (OutputStream os = c.getOutputStream()) {
            os.write(data.getBytes(StandardCharsets.UTF_8));
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                c.getResponseCode()==200 ? c.getInputStream() : c.getErrorStream(),
                StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining());
        } finally { c.disconnect(); }
    }

    private String postInformations(ParamType type, String url,
            String p1, String p2) {

    	try {
			byte[] body = new JSONObject(getAuthParameters(type, p1, p2))
			      .toJSONString()
			      .getBytes(AuthConstants.UTF_8);
			
			HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
			c.setRequestMethod("POST");
			c.setRequestProperty("Content-Type", AuthConstants.APP_JSON);
			c.setRequestProperty("Accept",       AuthConstants.APP_JSON);
			c.setRequestProperty("x-xbl-contract-version", "1");    // requis
			c.setDoOutput(true);
			
			try (OutputStream os = c.getOutputStream()) { os.write(body); }
			
			int http = c.getResponseCode();
			InputStreamReader isr = null;
			
			if (http == 200 && c.getInputStream() != null) {
				isr = new InputStreamReader(c.getInputStream(), AuthConstants.UTF_8);
			} else if (c.getErrorStream() != null) {
				isr = new InputStreamReader(c.getErrorStream(), AuthConstants.UTF_8);
			}
			
			String resp = "";
			if (isr != null) {
				try (BufferedReader br = new BufferedReader(isr)) {
					resp = br.lines().collect(Collectors.joining());
				}
			}
				
				Logger.log("POST " + url + " → HTTP " + http + " / " + resp);
				return resp;                       // peut être vide, mais plus de NPE
				
		} catch (Exception ex) {
			Logger.log("postInformations exception : " + ex);
			return "";
		}
	}



    private String connectToMinecraft(String url, String auth) {
        try {
            HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
            c.setRequestProperty("Accept", AuthConstants.APP_JSON);
            c.setRequestProperty("Authorization", auth);
            c.setRequestProperty("x-xbl-contract-version", "1");

            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    c.getResponseCode()==200 ? c.getInputStream() : c.getErrorStream(),
                    AuthConstants.UTF_8))) {
                return br.lines().collect(Collectors.joining());
            }
        } catch (Exception e) {
            Logger.log("connectToMinecraft error : " + e.getMessage());
            return "";
        }
    }

    /*────────────────── JSON bodies Xbox / XSTS / MC ──────────────────*/
    protected Map<Object,Object> getAuthParameters(ParamType param, String p1, String p2) {
        Map<Object,Object> m = new HashMap<>();

        if (param == ParamType.REFRESH) {
            m.put("client_id", CLIENT_ID);
            m.put("refresh_token", p1);
            m.put("grant_type", "refresh_token");
            m.put("redirect_uri", REDIRECT);
        }
        if (param == ParamType.XBL) {
            Map<Object,Object> props = new HashMap<>();
            props.put("AuthMethod", "RPS");
            props.put("SiteName",  "user.auth.xboxlive.com");
            props.put("RpsTicket", "d=" + p1);      // préfixe "d="
            m.put("Properties", props);
            m.put("RelyingParty", "http://auth.xboxlive.com");
            m.put("TokenType",   "JWT");
        }
        if (param == ParamType.XSTS) {
            Map<Object,Object> props = new HashMap<>();
            props.put("SandboxId", "RETAIL");
            props.put("UserTokens", Collections.singletonList(p1));
            m.put("Properties", props);
            m.put("RelyingParty", "rp://api.minecraftservices.com/");
            m.put("TokenType",   "JWT");
        }
        if (param == ParamType.MC) {
            m.put("identityToken", "XBL3.0 x=" + p1 + ";" + p2);
        }
        return m;
    }
}
