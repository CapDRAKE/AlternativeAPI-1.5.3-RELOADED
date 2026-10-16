# 8. Serveur de fichiers

Toutes les fonctions de ce chapitre sont **facultatives**. Un launcher peut
fonctionner uniquement avec le JSON de Mojang (voir le [tutoriel](02-tutoriel-launcher-basique.md)).
Héberger quelques fichiers sur un serveur web permet en plus :

- de mettre le launcher **en maintenance** ;
- de **préserver** ou **supprimer** des fichiers chez les joueurs ;
- d'**installer vos propres fichiers** (mods, configurations, resource packs…).

Un simple hébergement web statique suffit, plus un script pour la liste des fichiers
personnalisés (PHP ci-dessous).

## Organisation

Avec `new GameLinks("https://monsite.fr/launcher/", "1.21.1.json")` :

```
https://monsite.fr/launcher/
├── 1.21.1.json      JSON de la version
├── status.json      maintenance
├── ignore.json      fichiers à préserver
├── delete.json      fichiers à supprimer
└── files/           fichiers personnalisés
    ├── index.php    liste des fichiers (XML)
    └── bin/game/monserveur/mods/monmod.jar
```

Pour ne pas héberger le JSON de version, gardez celui de Mojang et pointez le reste
vers votre serveur :

```java
GameLinks links = new GameLinks(
        mojangJsonUrl,
        "https://monsite.fr/launcher/ignore.json",
        "https://monsite.fr/launcher/delete.json",
        "https://monsite.fr/launcher/status.json",
        "https://monsite.fr/launcher/files/");
```

Chaque URL peut être `null` pour désactiver la fonction correspondante.

> **Les chemins sont toujours relatifs à la racine du dossier du jeu**
> (`.monlauncher/`) et utilisent `/`. Comme les dossiers de jeu s'appellent
> `<style>-<version>` par défaut, fixez un nom stable avec
> `engine.setProfileDirectoryName("monserveur")` : vos chemins deviennent
> `bin/game/monserveur/...` quelle que soit la version.

---

## Maintenance

`status.json` :

```json
{ "status": "Ok" }
```

Toute autre valeur est un message de maintenance :

```json
{ "status": "Le serveur est en maintenance jusqu'à 18 h. Merci de votre patience !" }
```

(L'ancien format, un fichier texte contenant juste `Ok` ou le message, est aussi accepté.)

La maintenance est **désactivée par défaut**. Pour l'activer, enregistrez-la dans le
moteur **avant de créer `LauncherBase`** :

```java
engine.reg(new GameMaintenance(Maintenance.USE, engine));
new LauncherBase(primaryStage, scene, StageStyle.DECORATED, engine);
```

Au démarrage, `LauncherBase` lit `status.json`. Si la valeur n'est pas `Ok`, le message
s'affiche dans une boîte de dialogue et **la fenêtre du launcher ne s'ouvre pas**
(`engine.getGameMaintenance().isAccessBlocked()` vaut alors `true`).

> Si la maintenance est activée et que `status.json` est introuvable ou que le serveur
> ne répond pas, le launcher reste bloqué (boîte de dialogue vide). Assurez-vous que le
> fichier existe toujours.

---

## `ignore.json` : fichiers à préserver

Protège des fichiers du [nettoyage de `bin/`](04-mise-a-jour-et-lancement.md#le-nettoyage-du-dossier-bin).

```json
{
  "files": [
    "bin/game/monserveur/journeymap.properties"
  ],
  "folders": [
    "journeymap/",
    "xaero/",
    "bin/game/monserveur/schematics/"
  ]
}
```

- **`files`** : chemins complets depuis la racine du dossier du jeu.
- **`folders`** : un fichier est préservé si le chemin de son dossier **contient**
  l'entrée. `journeymap/` protège donc tout dossier `journeymap` (et ses
  sous-dossiers), dans tous les profils. Pour viser un seul profil, donnez le chemin
  complet.
- Inutile d'y lister `saves/`, `mods/`, `config/`, `options.txt`, etc. : ils sont déjà
  protégés.

Ancien format accepté (`ignore.cfg`, texte) : une entrée par ligne, les dossiers
finissent par `/`, les lignes commençant par `#` sont des commentaires.

---

## `delete.json` : fichiers à supprimer

```json
{
  "files": [
    "bin/game/monserveur/mods/monmod-1.1.jar",
    "bin/game/monserveur/journeymap/ancienne-config.txt",
    "libraries/com/google/guava/guava/15.0/guava-15.0.jar"
  ]
}
```

Chaque entrée est un chemin de fichier complet depuis la racine du dossier du jeu (pas
de dossiers entiers). L'effet dépend de l'emplacement :

- **dans `bin/`** : le fichier est supprimé à chaque lancement, **y compris dans les
  dossiers protégés** (`mods/`, `config/`…) et dans les dossiers ignorés. C'est le moyen
  de retirer un mod de chez vos joueurs après l'avoir retiré du serveur ;
- **une librairie (`libraries/...`)** : la lib **ne la télécharge pas**. Sert à écarter
  une version de librairie en conflit (par exemple une ancienne Guava avec certaines
  versions de Forge). Une copie déjà présente n'est pas supprimée ;
- **un fichier personnalisé** listé ici n'est pas téléchargé.

La correspondance est exacte : `mods/monmod.jar.old` ne désigne pas `mods/monmod.jar`.

> Les suppressions dans les dossiers protégés touchent les fichiers des joueurs :
> ne listez que des fichiers que vous avez vous-même distribués.

Ancien format accepté (`delete.cfg`) : une entrée par ligne.

---

## Fichiers personnalisés

La lib télécharge les fichiers listés par l'URL `files/` et les place dans le dossier
du jeu.

> **Uniquement avec les `GameStyle` `VANILLA_PLUS`, `VANILLA_PLUS_1_16_HIGHER`,
> `FABRIC`, `QUILT` et `NEOFORGE`.** Avec `VANILLA`, `VANILLA_1_19_HIGHER`, les styles
> Forge et `OPTIFINE`, l'URL `files/` est ignorée.

### Format de la liste

Une requête sur l'URL `files/` doit renvoyer un XML au format d'un listing Amazon S3 :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ListBucketResult>
  <Contents>
    <Key>bin/game/monserveur/mods/monmod.jar</Key>
    <ETag>"5d41402abc4b2a76b9719d911017c592"</ETag>
    <Size>48213</Size>
  </Contents>
  <Contents>
    <Key>bin/game/monserveur/config/monmod.toml</Key>
    <ETag>"7d793037a0760186574b0282f2f435e7"</ETag>
    <Size>1024</Size>
  </Contents>
</ListBucketResult>
```

| Élément | Contenu |
|---|---|
| `Key` | Chemin de destination, relatif à la racine du dossier du jeu |
| `ETag` | Empreinte **MD5** du fichier (hexadécimal, guillemets facultatifs) |
| `Size` | Taille en octets |

Chaque fichier est téléchargé depuis `<URL files/>` + `Key` : sur le serveur, il doit
donc se trouver au même chemin sous `files/`. Évitez les espaces et caractères spéciaux
dans les noms (ils ne sont pas encodés dans l'URL).

Si la clé est `bin/minecraft.jar`, ce jar remplace le jar client de Mojang.

### Script de listing (PHP)

À placer dans `files/index.php`, à côté de vos fichiers :

```php
<?php
// Liste les fichiers de ce dossier au format attendu par AlternativeAPI.
header('Content-Type: application/xml; charset=utf-8');

$base = __DIR__;
$files = new RecursiveIteratorIterator(
    new RecursiveDirectoryIterator($base, FilesystemIterator::SKIP_DOTS)
);

echo "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<ListBucketResult>\n";
foreach ($files as $file) {
    if (!$file->isFile()) {
        continue;
    }
    $key = str_replace('\\', '/', substr($file->getPathname(), strlen($base) + 1));
    if ($key === 'index.php') {
        continue;
    }
    echo "  <Contents>\n";
    echo '    <Key>' . htmlspecialchars($key, ENT_XML1) . "</Key>\n";
    echo '    <ETag>"' . md5_file($file->getPathname()) . "\"</ETag>\n";
    echo '    <Size>' . $file->getSize() . "</Size>\n";
    echo "  </Contents>\n";
}
echo "</ListBucketResult>\n";
```

Le serveur doit servir `index.php` quand on demande le dossier `files/` (c'est le
réglage par défaut d'Apache et de la plupart des hébergements). Vérifiez en ouvrant
`https://monsite.fr/launcher/files/` dans un navigateur : vous devez voir le XML.

Le MD5 est recalculé à chaque requête : pour un gros modpack, mettez la liste en cache
ou générez un fichier XML statique lors de vos mises en ligne.

### Mise à jour des fichiers

- Un fichier **absent** chez le joueur, ou dont la taille ou le MD5 diffère de la liste,
  est téléchargé. Modifier un fichier sur le serveur suffit donc à le mettre à jour chez
  tous les joueurs ; un fichier inchangé n'est pas retéléchargé.
- L'`ETag` doit être le **MD5** du fichier : sinon, le fichier est considéré comme
  modifié et retéléchargé à chaque lancement.
- Les fichiers personnalisés sont conservés par le nettoyage de `bin/`. Un fichier
  retiré du serveur est supprimé par le nettoyage, **sauf dans les dossiers protégés**
  (`mods/`, `config/`…) : pour ceux-là, ajoutez-le à
  [`delete.json`](#deletejson--fichiers-à-supprimer). Exemple pour passer de
  `monmod-1.1.jar` à `monmod-1.2.jar` : déposez le nouveau fichier dans `files/`,
  retirez l'ancien et listez `bin/game/monserveur/mods/monmod-1.1.jar` dans
  `delete.json`.
- Si l'URL `files/` ne répond pas correctement (erreur HTTP, réseau), le jeu n'est pas
  lancé : `hasDownloadError()` vaut `true` et l'URL apparaît dans
  `getFailedDownloads()`.

---

## Héberger le JSON de version

Avec le constructeur `GameLinks(base, "1.21.1.json")`, vous hébergez le JSON vous-même.
Partez du JSON officiel (récupéré via le manifest Mojang, voir le
[tutoriel](02-tutoriel-launcher-basique.md#étape-2--trouver-le-json-de-la-version)) et
déposez-le tel quel ; le nom du fichier sert aussi d'identifiant de version quand le
JSON n'en fournit pas.

Héberger le JSON vous permet de figer la version servie ou d'en publier une
modifiée (par exemple fusionnée avec un modloader, voir [Modloaders](09-modloaders.md)).
