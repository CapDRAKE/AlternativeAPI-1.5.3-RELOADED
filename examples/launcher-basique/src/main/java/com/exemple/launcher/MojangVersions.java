package com.exemple.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Trouve l'URL du JSON officiel d'une version de Minecraft.
 *
 * Mojang publie la liste de toutes les versions dans un "manifest". Chaque entrée
 * donne l'URL du JSON de la version, qui décrit tout ce qu'il faut télécharger
 * (jar du client, librairies, assets, version de Java...). C'est ce JSON
 * qu'AlternativeAPI attend dans {@code GameLinks}.
 */
public final class MojangVersions {

    private static final String MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private MojangVersions() {
    }

    /**
     * @param versionId l'identifiant de la version, par exemple "1.21.1"
     * @return l'URL du JSON de cette version
     * @throws IOException si le manifest est injoignable ou si la version n'existe pas
     */
    public static String resolveVersionJsonUrl(String versionId) throws IOException {
        URLConnection connection = new URL(MANIFEST_URL).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        try (InputStream in = connection.getInputStream();
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject manifest = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement element : manifest.getAsJsonArray("versions")) {
                JsonObject version = element.getAsJsonObject();
                if (versionId.equals(version.get("id").getAsString())) {
                    return version.get("url").getAsString();
                }
            }
        }
        throw new IOException("Version de Minecraft inconnue : " + versionId);
    }
}
