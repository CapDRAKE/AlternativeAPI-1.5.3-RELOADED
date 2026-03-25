package fr.trxyy.alternative.alternative_auth.base;

import java.nio.charset.Charset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Ensemble des constantes utilisées par le module d'authentification.
 *
 * ➤ Mise à jour mai 2025 :
 *   • Passage aux endpoints Microsoft Identity Platform v2 (PKCE).
 *   • Ajout d'un champ SCOPE explicite.
 *   • L'ancien point d'entrée Live Connect (MBI_SSL) est retiré.
 */
public class AuthConstants {

    // ─────────────────────────────────────────────────────────────────────────────
    //  Mojang
    // ─────────────────────────────────────────────────────────────────────────────

    public static final String MOJANG_BASE_URL = "https://authserver.mojang.com/authenticate";

    // ─────────────────────────────────────────────────────────────────────────────
    //  Microsoft Identity Platform v2 (PKCE)
    // ─────────────────────────────────────────────────────────────────────────────

    /** URL racine pour la requête d'autorisation. Les paramètres (client_id, scope,
     *  code_challenge, etc.) sont ajoutés dynamiquement dans MicrosoftAuth. */
    public static final String MICROSOFT_BASE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";

    /** Redirection recommandée pour les applications desktop/native. */
    public static final String MICROSOFT_RESPONSE_URL = "https://login.microsoftonline.com/common/oauth2/nativeclient?code=";

    /** Endpoint pour l'échange du code (token). */
    public static final String MICROSOFT_AUTH_TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

    /** Scopes Xbox Live autorisés pour Minecraft. */
    public static final String MICROSOFT_SCOPE = "XboxLive.signin offline_access";
    
    public static final String REDIRECT_URI = "http://localhost:51735/callback";

    // ─────────────────────────────────────────────────────────────────────────────
    //  API Xbox / Minecraft (inchangées)
    // ─────────────────────────────────────────────────────────────────────────────

    public static final String MICROSOFT_AUTHENTICATE_XBOX = "https://user.auth.xboxlive.com/user/authenticate";
    public static final String MICROSOFT_AUTHORIZE_XSTS    = "https://xsts.auth.xboxlive.com/xsts/authorize";
    public static final String MICROSOFT_LOGIN_XBOX        = "https://api.minecraftservices.com/authentication/login_with_xbox";
    public static final String MICROSOFT_MINECRAFT_STORE   = "https://api.minecraftservices.com/entitlements/mcstore";
    public static final String MICROSOFT_MINECRAFT_PROFILE = "https://api.minecraftservices.com/minecraft/profile";

    // ─────────────────────────────────────────────────────────────────────────────
    //  Utils
    // ─────────────────────────────────────────────────────────────────────────────

    public static final String APP_JSON     = "application/json";
    public static final String URL_ENCODED  = "application/x-www-form-urlencoded";
    public static final Charset UTF_8       = Charset.forName("UTF-8");

    // ─────────────────────────────────────────────────────────────────────────────
    //  Versioning (librairie)
    // ─────────────────────────────────────────────────────────────────────────────

    private static final String VERSION_ID = "1.0.4"; // bump de version

    public static String getVersion() {
        return VERSION_ID;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Gson helper
    // ─────────────────────────────────────────────────────────────────────────────

    /** @return Un objet Gson configuré. */
    public static Gson getGson() {
        return new GsonBuilder()
                .enableComplexMapKeySerialization()
                .setPrettyPrinting()
                .create();
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Affichage CLI
    // ─────────────────────────────────────────────────────────────────────────────

    public static void displayCopyrights() {
        Logger.log("========================================");
        Logger.log("|            AlternativeAuth           |");
        Logger.log("|            Version: " + getVersion() + "            |");
        Logger.log("========================================");
    }
}
