package fr.trxyy.alternative.alternative_auth.base;

import fr.trxyy.alternative.alternative_api.GameEngine;
import fr.trxyy.alternative.alternative_auth.account.AccountType;
import fr.trxyy.alternative.alternative_auth.account.Session;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftBrowserAuth;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftXboxAuth;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftOAuthClient;
import fr.trxyy.alternative.alternative_auth.microsoft.model.MicrosoftModel;
import fr.trxyy.alternative.alternative_auth.mojang.model.MojangAuthResult;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
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
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
            setSession(user, TokenGenerator.generateToken(user), offlineUuid(user));
        }
    }

    /**
     * UUID hors ligne standard de Minecraft, celui qu'un serveur en online-mode=false
     * attribue au pseudo. Il doit rester le même d'une session à l'autre : le jeu range
     * les progrès et statistiques du joueur par UUID (un UUID aléatoire les faisait
     * repartir de zéro à chaque connexion).
     */
    public static String offlineUuid(String username) {
        byte[] seed = ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(seed).toString().replace("-", "");
    }

    public GameAuth(AccountType type) {
        AuthConstants.displayCopyrights();
    }

    /*──────────────────────  Auth Microsoft  ──────────────────────*/

    public void connectMicrosoft(GameEngine engine, Pane root) {
        connectMicrosoft(engine, root, null);
    }

    /**
     * Connexion Microsoft tout-en-un : reconnexion silencieuse si des jetons sont
     * enregistrés, sinon connexion dans le navigateur du joueur (MicrosoftBrowserAuth).
     * Une petite fenêtre d'attente reste affichée pendant ce temps ; la fermer annule.
     *
     * @param onFinished appelé sur le thread JavaFX à la fin : true si connecté
     *                   (getSession() est alors renseignée), false sinon. Peut être null.
     */
    public void connectMicrosoft(GameEngine engine, Pane root, Consumer<Boolean> onFinished) {
        this.authConfig = new AuthConfig(engine);
        final MicrosoftBrowserAuth browserAuth = new MicrosoftBrowserAuth();

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);
        Label message = new Label("Termine la connexion dans ton navigateur…");
        message.setWrapText(true);
        VBox content = new VBox(16, spinner, message);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20));

        Stage dlg = new Stage();
        dlg.setScene(new Scene(content, 340, 170));
        dlg.setTitle("Connexion Microsoft");
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setResizable(false);
        // Fermer la fenêtre abandonne la connexion en cours.
        dlg.setOnCloseRequest(e -> browserAuth.cancel());
        dlg.show();

        new Thread(() -> {
            try {
                /* ===================== SILENT LOGIN ===================== */
                if (trySilentRefresh(engine)) {
                    Platform.runLater(() -> finish(true, dlg, onFinished));
                    return;
                }

                /* ===================== CONNEXION DANS LE NAVIGATEUR ===================== */
                String url = browserAuth.start();
                Desktop.getDesktop().browse(URI.create(url));
                MicrosoftModel model = browserAuth.awaitTokens(5, TimeUnit.MINUTES);

                authConfig.createConfigFile(model);
                Session res = new MicrosoftXboxAuth().getLiveToken(model.getAccess_token());
                setSession(res.getUsername(), res.getToken(), res.getUuid());
                Platform.runLater(() -> finish(true, dlg, onFinished));

            } catch (MicrosoftBrowserAuth.LoginCancelledException e) {
                Logger.log("Connexion Microsoft annulée");
                this.isAuthenticated = false;
                Platform.runLater(() -> finish(false, dlg, onFinished));
            } catch (Exception e) {
                e.printStackTrace();
                this.isAuthenticated = false;
                Platform.runLater(() -> finish(false, dlg, onFinished));
            }
        }, "AlternativeAuth-Microsoft").start();
    }

    private static void finish(boolean connected, Stage dlg, Consumer<Boolean> onFinished) {
        dlg.close();
        if (onFinished != null) {
            onFinished.accept(connected);
        }
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

    /**
     * Deconnexion complete du compte Microsoft : vide la session courante ET
     * supprime les jetons enregistres sur le disque. Sans cette suppression, la
     * connexion suivante se ferait en silence sur le meme compte, rendant tout
     * changement de compte impossible.
     *
     * @param engine The GameEngine instance
     * @return true si les jetons ont bien ete supprimes
     */
    public boolean logout(GameEngine engine) {
        this.isAuthenticated = false;
        this.session = new Session();
        try {
            this.authConfig = new AuthConfig(engine);
            return this.authConfig.deleteConfigFile();
        } catch (Exception e) {
            Logger.err("Echec de la deconnexion Microsoft : " + e.getMessage());
            return false;
        }
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
