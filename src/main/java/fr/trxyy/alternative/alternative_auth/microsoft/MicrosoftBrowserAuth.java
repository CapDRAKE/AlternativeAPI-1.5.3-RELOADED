package fr.trxyy.alternative.alternative_auth.microsoft;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import fr.trxyy.alternative.alternative_auth.base.Logger;
import fr.trxyy.alternative.alternative_auth.microsoft.model.MicrosoftModel;

/**
 * Connexion Microsoft dans le navigateur du joueur (code d'autorisation + PKCE).
 *
 * Le launcher ouvre la page de connexion Microsoft ; une fois le joueur connecté,
 * Microsoft renvoie le navigateur vers un petit serveur local
 * (http://localhost:&lt;port&gt;/) qui récupère le code, puis la classe l'échange
 * contre les jetons. Aucun code à recopier, contrairement au flux d'appareil.
 *
 * L'application Azure doit déclarer l'adresse de retour http://localhost sur la
 * plateforme « Applications de bureau et mobiles ». Microsoft accepte alors n'importe
 * quel port, mais aucun chemin : le serveur écoute donc à la racine.
 *
 * Utilisation (hors du thread JavaFX, les appels sont bloquants) :
 * <pre>
 * MicrosoftBrowserAuth browserAuth = new MicrosoftBrowserAuth();
 * String url = browserAuth.start();
 * Desktop.getDesktop().browse(URI.create(url));
 * MicrosoftModel tokens = browserAuth.awaitTokens(5, TimeUnit.MINUTES);
 * </pre>
 */
public class MicrosoftBrowserAuth {

    /** Le joueur a refusé ou annulé la connexion (sur la page Microsoft ou dans le launcher). */
    public static class LoginCancelledException extends Exception {
        private static final long serialVersionUID = 1L;

        public LoginCancelledException(String message) {
            super(message);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CompletableFuture<String> codeFuture = new CompletableFuture<>();
    private final String state = randomToken(32);
    private final String codeVerifier = randomToken(64);

    private HttpServer server;
    private HttpServer serverIpv6;
    private boolean stopped;
    private String redirectUri;
    private String launcherName = "le launcher";

    /**
     * Nom affiché sur la page de confirmation du navigateur
     * (« Tu peux revenir dans &lt;nom&gt; »).
     */
    public MicrosoftBrowserAuth setLauncherName(String name) {
        if (name != null && !name.trim().isEmpty()) {
            this.launcherName = name.trim();
        }
        return this;
    }

    /**
     * Démarre le serveur local sur un port libre.
     *
     * @return l'adresse de la page de connexion Microsoft, à ouvrir dans le navigateur
     */
    public synchronized String start() throws Exception {
        if (this.server != null) {
            throw new IllegalStateException("Connexion déjà démarrée");
        }
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
        int port = this.server.getAddress().getPort();

        // Selon le navigateur, "localhost" peut d'abord être tenté en IPv6 (::1) :
        // on écoute aussi là quand c'est possible, sinon il se rabattra sur 127.0.0.1.
        try {
            this.serverIpv6 = HttpServer.create(new InetSocketAddress(InetAddress.getByName("::1"), port), 0);
            this.serverIpv6.createContext("/", this::handle);
            this.serverIpv6.start();
        } catch (Exception ignored) {
            this.serverIpv6 = null;
        }

        this.redirectUri = "http://localhost:" + port + "/";
        Logger.log("Connexion Microsoft dans le navigateur, retour attendu sur " + this.redirectUri);
        return MicrosoftOAuthClient.buildAuthorizeUrl(this.redirectUri, this.state, codeChallenge(this.codeVerifier));
    }

    /**
     * Attend que le joueur se connecte dans le navigateur, puis échange le code
     * contre les jetons Microsoft. Arrête le serveur local dans tous les cas.
     *
     * @throws LoginCancelledException       refus sur la page Microsoft, ou cancel()
     * @throws java.util.concurrent.TimeoutException pas de connexion dans le délai
     */
    public MicrosoftModel awaitTokens(long timeout, TimeUnit unit) throws Exception {
        if (this.server == null) {
            throw new IllegalStateException("start() doit être appelé avant awaitTokens()");
        }
        try {
            String code;
            try {
                code = this.codeFuture.get(timeout, unit);
            } catch (CancellationException e) {
                throw new LoginCancelledException("Connexion annulée");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw e;
            }
            return new MicrosoftOAuthClient().exchangeAuthorizationCode(code, this.redirectUri, this.codeVerifier);
        } finally {
            stopServers();
        }
    }

    /** Abandonne la connexion : awaitTokens() lève aussitôt LoginCancelledException. */
    public void cancel() {
        this.codeFuture.cancel(false);
        stopServers();
    }

    private void handle(HttpExchange exchange) {
        int status;
        String title;
        String message;

        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        if (!"/".equals(exchange.getRequestURI().getPath())) {
            status = 404;
            title = "Introuvable";
            message = "";
        } else if (!this.state.equals(params.get("state"))) {
            // Une requête sans le bon state ne vient pas de notre demande de connexion
            // (autre page qui sonde le port, ancien onglet) : on l'ignore et on continue
            // d'attendre la vraie réponse.
            status = 400;
            title = "Lien de connexion invalide";
            message = "Ce lien ne correspond pas à la connexion en cours. Relance la connexion depuis "
                    + this.launcherName + ".";
        } else if ("access_denied".equals(params.get("error"))) {
            status = 200;
            title = "Connexion annulée";
            message = "Tu peux fermer cet onglet et revenir dans " + this.launcherName + ".";
            this.codeFuture.completeExceptionally(new LoginCancelledException("Connexion refusée sur la page Microsoft"));
        } else if (params.get("error") != null) {
            status = 200;
            title = "Connexion impossible";
            message = "Microsoft a renvoyé une erreur. Reviens dans " + this.launcherName + " pour réessayer.";
            this.codeFuture.completeExceptionally(new IOException("Microsoft authorize error : "
                    + params.get("error") + " – " + params.get("error_description")));
        } else if (params.get("code") == null || params.get("code").isEmpty()) {
            status = 400;
            title = "Réponse incomplète";
            message = "Aucun code reçu de Microsoft. Relance la connexion depuis " + this.launcherName + ".";
        } else {
            status = 200;
            title = "Connexion réussie";
            message = "Tu peux fermer cet onglet et revenir dans " + this.launcherName + ".";
            this.codeFuture.complete(params.get("code"));
        }

        // La connexion est déjà tranchée ci-dessus : un onglet fermé trop vite ne doit
        // pas la faire échouer, l'envoi de la page est donc au mieux.
        try {
            respond(exchange, status, title, message);
        } catch (IOException e) {
            Logger.log("Page de confirmation non envoyée au navigateur : " + e.getMessage());
        } finally {
            exchange.close();
        }
    }

    private synchronized void stopServers() {
        if (this.stopped) {
            return;
        }
        this.stopped = true;
        // HttpServer.stop(1) bloque jusqu'à une seconde (le temps de laisser partir la
        // page de confirmation) : fait en arrière-plan pour ne figer ni l'interface
        // (cancel) ni la fin de la connexion (awaitTokens).
        for (final HttpServer s : new HttpServer[] { this.server, this.serverIpv6 }) {
            if (s != null) {
                Thread stopper = new Thread(() -> s.stop(1), "MicrosoftBrowserAuth-stop");
                stopper.setDaemon(true);
                stopper.start();
            }
        }
    }

    private static void respond(HttpExchange exchange, int status, String title, String message) throws IOException {
        String html = "<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\"><title>" + escape(title)
                + "</title><style>body{margin:0;min-height:100vh;display:flex;align-items:center;"
                + "justify-content:center;background:#0b1118;color:#fff;font-family:Segoe UI,Arial,sans-serif}"
                + "main{text-align:center;padding:40px 48px;border-radius:24px;background:#141c26;"
                + "border:1px solid rgba(255,255,255,.12)}h1{margin:0 0 12px;font-size:24px}"
                + "p{margin:0;color:rgba(255,255,255,.7)}</style></head><body><main><h1>" + escape(title)
                + "</h1><p>" + escape(message) + "</p></main></body></html>";
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                params.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                        URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
            } catch (Exception ignored) {
                // paramètre mal encodé : ignoré
            }
        }
        return params;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static String codeChallenge(String verifier) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
