# 9. Modloaders

| Modloader | `GameStyle` | JSON à fournir | Ce que fait la lib | Testé avec cette doc |
|---|---|---|---|---|
| Forge | `FORGE_*` (selon la version) | Le JSON **Mojang** de la version | Choisit la version de Forge, télécharge et exécute l'installeur officiel | 1.20.1 |
| Fabric | `FABRIC` | JSON Mojang **fusionné** avec le profil Fabric | Télécharge les librairies du loader | 1.21.1 |
| Quilt | `QUILT` | JSON Mojang fusionné avec le profil Quilt | Idem Fabric | non |
| NeoForge | `NEOFORGE` | JSON Mojang fusionné avec le `version.json` de l'installeur | Télécharge et exécute l'installeur officiel | non |
| OptiFine | `OPTIFINE` | JSON Mojang | Lance via LaunchWrapper, **sans** télécharger OptiFine | non |

Les mods eux-mêmes vont dans `bin/game/<profil>/mods/`, installés par les
[fichiers personnalisés](08-serveur-de-fichiers.md#fichiers-personnalisés) (styles
`FABRIC`, `QUILT`, `NEOFORGE`) ou par le joueur. Ce dossier est protégé du nettoyage.

---

## Forge

Le plus simple : fournissez le JSON **vanilla** de Mojang et le bon `GameStyle`, la lib
s'occupe du reste.

```java
String mc = "1.20.1";
GameLinks links = new GameLinks(MojangVersions.resolveVersionJsonUrl(mc));
GameEngine engine = new GameEngine(folder, links, prefs, GameStyle.FORGE_1_19_HIGHER);
engine.setRequestedVersionId(mc);
```

| Minecraft | `GameStyle` |
|---|---|
| 1.7.10 | `FORGE_1_7_10_OLD` |
| 1.8 à 1.12.2 | `FORGE_1_8_TO_1_12_2` |
| 1.13.2 à 1.16.5 | `FORGE_1_13_HIGHER` |
| 1.17 à 1.18.2 | `FORGE_1_17_HIGHER` |
| 1.19 et plus | `FORGE_1_19_HIGHER` |

Au premier lancement, la lib :

1. lit la liste officielle des versions de Forge (`promotions_slim.json`) et prend la
   version **recommandée** pour cette version de Minecraft, à défaut la plus récente ;
2. télécharge l'installeur officiel depuis `maven.minecraftforge.net` et l'exécute avec
   le Java de la version (comptez une à deux minutes) ;
3. lance le jeu avec Forge.

La version de Forge n'est pas configurable : c'est toujours la recommandée (ou la
dernière). Pour imposer une version précise, il faut modifier la lib.

> En session hors ligne, le log du jeu affiche une `InvalidCredentialsException:
> Status: 401` au démarrage : c'est normal (le jeu tente de récupérer des données de
> profil auprès de Mojang) et sans conséquence.

---

## Fabric

La lib ne télécharge pas le profil Fabric : elle attend un JSON **complet**, c'est-à-dire
le JSON de Mojang dans lequel on a fusionné le profil du loader (sa classe principale,
ses librairies, ses arguments). La classe ci-dessous prépare ce JSON et renvoie son URL
`file:` :

```java
String mc = "1.21.1";
String jsonUrl = FabricVersion.prepare(folder, mc, MojangVersions.resolveVersionJsonUrl(mc));

GameLinks links = new GameLinks(jsonUrl);
GameEngine engine = new GameEngine(folder, links, prefs, GameStyle.FABRIC);
engine.setRequestedVersionId(mc);
```

`MojangVersions` est la classe du [tutoriel](02-tutoriel-launcher-basique.md#étape-2--trouver-le-json-de-la-version).
`FabricVersion` :

```java
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import fr.trxyy.alternative.alternative_api.GameFolder;

/**
 * Prépare le JSON d'une version Fabric pour AlternativeAPI : le JSON de Mojang
 * fusionné avec le profil du loader publié par Fabric.
 */
public final class FabricVersion {

    private static final String FABRIC_META = "https://meta.fabricmc.net/v2/versions/loader/";

    private FabricVersion() {
    }

    /**
     * @param mojangJsonUrl l'URL du JSON Mojang de la version (voir MojangVersions)
     * @return l'URL (file:) du JSON fusionné, à passer à new GameLinks(...)
     */
    public static String prepare(GameFolder folder, String mcVersion, String mojangJsonUrl) throws IOException {
        // Dernier loader stable pour cette version de Minecraft
        String loader = null;
        for (JsonElement element : readJson(FABRIC_META + mcVersion).getAsJsonArray()) {
            JsonObject entry = element.getAsJsonObject().getAsJsonObject("loader");
            if (entry.get("stable").getAsBoolean()) {
                loader = entry.get("version").getAsString();
                break;
            }
        }
        if (loader == null) {
            throw new IOException("Aucun loader Fabric stable pour " + mcVersion);
        }

        JsonObject vanilla = readJson(mojangJsonUrl).getAsJsonObject();
        JsonObject fabric = readJson(FABRIC_META + mcVersion + "/" + loader + "/profile/json").getAsJsonObject();
        JsonObject merged = merge(vanilla, fabric);
        merged.remove("inheritsFrom");

        // Hors de bin/ (nettoyé à chaque lancement) et pas directement dans cache/
        // (la lib y recopie le JSON sous le même nom).
        File dir = new File(folder.getCacheDir(), "loaders");
        dir.mkdirs();
        File file = new File(dir, "fabric-" + mcVersion + ".json");
        Files.write(file.toPath(), merged.toString().getBytes(StandardCharsets.UTF_8));
        return file.toURI().toString();
    }

    /** Fusionne overlay dans base : objets fusionnés, librairies et arguments cumulés, le reste remplacé. */
    private static JsonObject merge(JsonObject base, JsonObject overlay) {
        JsonObject result = base.deepCopy();
        for (Map.Entry<String, JsonElement> entry : overlay.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            JsonElement current = result.get(key);
            if (current != null && current.isJsonArray() && value.isJsonArray() && "libraries".equals(key)) {
                Map<String, JsonElement> libraries = new LinkedHashMap<>();
                for (JsonElement library : current.getAsJsonArray()) {
                    libraries.put(library.getAsJsonObject().get("name").getAsString(), library);
                }
                for (JsonElement library : value.getAsJsonArray()) {
                    libraries.put(library.getAsJsonObject().get("name").getAsString(), library);
                }
                JsonArray array = new JsonArray();
                libraries.values().forEach(array::add);
                result.add(key, array);
            } else if (current != null && current.isJsonArray() && value.isJsonArray()
                    && ("game".equals(key) || "jvm".equals(key))) {
                JsonArray array = current.getAsJsonArray().deepCopy();
                array.addAll(value.getAsJsonArray());
                result.add(key, array);
            } else if (current != null && current.isJsonObject() && value.isJsonObject()) {
                result.add(key, merge(current.getAsJsonObject(), value.getAsJsonObject()));
            } else {
                result.add(key, value.deepCopy());
            }
        }
        return result;
    }

    private static JsonElement readJson(String url) throws IOException {
        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        try (InputStream in = connection.getInputStream();
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }
}
```

Règles de fusion : les objets sont fusionnés récursivement, les librairies cumulées
(celles du loader remplacent celles de même nom), les arguments `game` et `jvm`
ajoutés à la suite, toute autre valeur (`id`, `mainClass`…) remplacée par celle du
loader. Le champ `inheritsFrom` est retiré : le JSON se suffit à lui-même.

Appelez `prepare` hors du thread JavaFX (accès réseau), par exemple au clic sur
« Jouer » juste avant de créer le `GameUpdater`, puis enregistrez les nouveaux liens
avec `engine.reg(links)` si le moteur existe déjà.

> Le JSON fusionné étant local, la lib ne détecte pas l'absence de réseau : sans
> connexion, elle tente quand même ses téléchargements, ils échouent et le jeu ne se
> lance pas. Le jeu Fabric nécessite donc une connexion à chaque lancement.

---

## Quilt

Même principe que Fabric, avec l'API de Quilt :

- versions du loader : `https://meta.quiltmc.org/v3/versions/loader/<version MC>` ;
- profil : `https://meta.quiltmc.org/v3/versions/loader/<version MC>/<loader>/profile/json`.

Adaptez la sélection du loader de `FabricVersion` (prenez la première entrée de la
liste, la plus récente) et utilisez `GameStyle.QUILT`.

---

## NeoForge

Procédure avancée :

1. téléchargez l'installeur `neoforge-<version>-installer.jar` depuis
   `https://maven.neoforged.net/releases/net/neoforged/neoforge/<version>/` ;
2. extrayez le fichier `version.json` qu'il contient ;
3. fusionnez-le avec le JSON Mojang de la version de Minecraft correspondante (mêmes
   règles que pour Fabric), en conservant son `id` (`neoforge-<version>`) ;
4. utilisez ce JSON avec `GameStyle.NEOFORGE`.

La lib déduit la version de NeoForge de l'`id` du JSON, télécharge l'installeur
officiel, l'exécute (`--installClient`) puis lance le jeu.

---

## OptiFine

`GameStyle.OPTIFINE` lance le jeu via LaunchWrapper avec le *tweaker* d'OptiFine, mais
**la lib ne télécharge ni OptiFine ni la version de LaunchWrapper adaptée** : OptiFine ne
propose pas d'API de téléchargement. Votre launcher doit les placer lui-même dans
`libraries/`. Pour la plupart des usages, Fabric avec les mods Sodium et Iris est une
alternative plus simple.
