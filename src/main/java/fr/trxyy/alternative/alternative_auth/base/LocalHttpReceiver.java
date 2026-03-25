package fr.trxyy.alternative.alternative_auth.base;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class LocalHttpReceiver {

    private final HttpServer server;
    private final CompletableFuture<String> codeFuture = new CompletableFuture<>();

    public LocalHttpReceiver(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/callback", exchange -> {
            URI requestURI = exchange.getRequestURI();
            String query   = requestURI.getRawQuery();          // code=...&state=...
            String response = "<html><body>Connexion terminée ! "
                            + "Vous pouvez retourner dans le launcher.</body></html>";

            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
            // récupère le paramètre code
            for (String kv : query.split("&")) {
                if (kv.startsWith("code=")) {
                    codeFuture.complete(kv.substring("code=".length()));
                    break;
                }
            }
        });
        server.start();
    }

    public CompletableFuture<String> waitForCode() {
        return codeFuture;
    }

    public void stop() {
        server.stop(0);
    }
}
