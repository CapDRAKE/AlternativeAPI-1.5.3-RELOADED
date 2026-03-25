package fr.trxyy.alternative.alternative_auth.base;

import fr.trxyy.alternative.alternative_api.GameEngine;
import fr.trxyy.alternative.alternative_auth.account.AccountType;
import fr.trxyy.alternative.alternative_auth.account.Session;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftXboxAuth;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftOAuthClient;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftOAuthClient.DeviceCode;
import fr.trxyy.alternative.alternative_auth.microsoft.model.MicrosoftModel;
import fr.trxyy.alternative.alternative_auth.mojang.model.MojangAuthResult;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.UUID;

/**
 * GameAuth
 */
public class GameAuth {

    private boolean isAuthenticated = false;
    private Session session = new Session();

    private AuthConfig authConfig;

    /*──────────────────────────  Constructeurs  ──────────────────────────*/

    public GameAuth(String user, String pwd, AccountType type) {
        AuthConstants.displayCopyrights();
        if (type == AccountType.MOJANG) {
            connectMinecraft(user, pwd);
        } else if (type == AccountType.OFFLINE) {
            setSession(user, TokenGenerator.generateToken(user), UUID.randomUUID().toString().replace("-", ""));
        }
    }

    public GameAuth(AccountType type) {
        AuthConstants.displayCopyrights();
    }

    /*──────────────────────  Auth Microsoft  ──────────────────────*/

    public void connectMicrosoft(GameEngine engine, Pane root) {
        this.authConfig = new AuthConfig(engine);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(80, 80);
        StackPane content = new StackPane(spinner);
        content.setPadding(new Insets(20));

        Stage dlg = new Stage();
        dlg.setScene(new Scene(content, 300, 160));
        dlg.setTitle("Connexion Microsoft");
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setResizable(false);
        dlg.show();

        new Thread(() -> {
            try {
                /* ===================== SILENT LOGIN ===================== */
                if (trySilentRefresh(engine)) {
                    Platform.runLater(() -> dlg.close());
                    return;
                }

                /* ===================== DEVICE FLOW ===================== */
                MicrosoftOAuthClient deviceAuth = new MicrosoftOAuthClient();

                // 1️⃣ Récupération du device code
                DeviceCode deviceCode = deviceAuth.requestDeviceCode();

                // 2️⃣ Ouvre la page Microsoft officielle
                Desktop.getDesktop().browse(
                        URI.create(deviceCode.getVerificationUri())
                );

                // 3️⃣ Affiche le code à l’utilisateur (UI à toi)
                Platform.runLater(() -> {
                    Logger.log("Code Microsoft : " + deviceCode.getUserCode());
                    // 👉 idéalement : popup / label visible
                });

                // 4️⃣ Polling jusqu’à validation
                MicrosoftModel model = deviceAuth.pollForToken(deviceCode);

                // 5️⃣ Sauvegarde tokens
                authConfig.createConfigFile(model);

                // 6️⃣ Chaîne Xbox → XSTS → Minecraft
                Session res = new MicrosoftXboxAuth().getLiveToken(model.getAccess_token());

                Platform.runLater(() -> success(res, dlg));

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> error(dlg));
            }
        }).start();
    }

    private void success(Session session, Stage dlg) {
        setSession(session.getUsername(), session.getToken(), session.getUuid());
        dlg.close();
    }

    private void error(Stage dlg) {
        this.isAuthenticated = false;
        dlg.close();
    }

    /*──────────────────  Auth Mojang (inchangée)  ──────────────────*/

    public void connectMinecraft(String username, String password) {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            HttpPost httpPost = new HttpPost(AuthConstants.MOJANG_BASE_URL);
            StringEntity parameters = new StringEntity(
                    "{\"agent\":{\"name\":\"Minecraft\",\"version\":1},\"username\":\"" + username + "\",\"password\":\"" + password + "\"}",
                    ContentType.create(AuthConstants.APP_JSON));
            httpPost.addHeader("content-type", AuthConstants.APP_JSON);
            httpPost.setEntity(parameters);
            try (CloseableHttpResponse resp = httpClient.execute(httpPost)) {
                BufferedReader br = new BufferedReader(new InputStreamReader(resp.getEntity().getContent()));
                String json = br.readLine();
                if (!json.contains("\"name\"")) {
                    this.isAuthenticated = false;
                    return;
                }
                MojangAuthResult result = AuthConstants.getGson().fromJson(json, MojangAuthResult.class);
                setSession(result.getSelectedProfile().getName(), result.getAccessToken(), result.getSelectedProfile().getId());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /*──────────────────  Setters / Getters  ──────────────────*/

    private void setSession(String user, String token, String id) {
        this.session.setUsername(user);
        this.session.setToken(token);
        this.session.setUuid(id);
        this.isAuthenticated = true;
        Logger.log("Connected successfully as " + user);
    }

    public void setSession(Session s) {
        setSession(s.getUsername(), s.getToken(), s.getUuid());
    }

    public boolean isLogged() {
        return isAuthenticated;
    }

    public Session getSession() {
        return session;
    }

    /* ------------------------------------------------------------------
     *  Tentative silencieuse : si un refresh_token est présent et valide
     *  → on met à jour la Session et on renvoie true.
     *  Sinon renvoie false et ne modifie rien.
     * ------------------------------------------------------------------ */
    public boolean trySilentRefresh(GameEngine engine) {
        try {
            this.authConfig = new AuthConfig(engine);
            if (!authConfig.canRefresh()) return false;

            authConfig.loadConfiguration();

            MicrosoftOAuthClient deviceAuth = new MicrosoftOAuthClient();

            // 🔁 Device Flow refresh
            MicrosoftModel m = deviceAuth.refreshWithToken(
                    authConfig.microsoftModel.getRefresh_token()
            );

            authConfig.updateValues(m);

            Session s = new MicrosoftXboxAuth().getLiveToken(m.getAccess_token());
            setSession(s);
            return true;

        } catch (Exception ex) {
            Logger.log("Silent refresh failed : " + ex.getMessage());
            return false;
        }
    }


}
