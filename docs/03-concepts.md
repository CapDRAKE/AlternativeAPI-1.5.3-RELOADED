# 3. Concepts

Un launcher AlternativeAPI repose sur quatre objets, assemblés une fois au démarrage :

```
GameFolder ──┐
GameLinks ───┼──► GameEngine ──► GameUpdater (télécharge, vérifie, lance)
LauncherPreferences ─┤
GameStyle ───┘
```

Le **`GameEngine`** est passé partout : à l'interface, à l'updater, à l'authentification
Microsoft. Il porte aussi les réglages optionnels (mémoire, taille de fenêtre…) qu'on
lui ajoute avec `engine.reg(...)`.

---

## GameFolder

Le dossier d'installation du jeu.

```java
GameFolder folder = new GameFolder("monlauncher");
```

| Système | Dossier créé |
|---|---|
| Windows | `%APPDATA%\.monlauncher` (`C:\Users\<nom>\AppData\Roaming\.monlauncher`) |
| Linux | `~/.monlauncher` |
| macOS | `~/Library/Application Support/monlauncher` |

Le dossier est calculé à partir de la propriété système `user.home`. Le constructeur
crée immédiatement toute l'arborescence :

| Getter | Chemin | Contenu |
|---|---|---|
| `getGameDir()` | `.monlauncher/` | Racine |
| `getBinDir()` | `bin/` | Tout ce que la lib vérifie et nettoie (voir [Mise à jour](04-mise-a-jour-et-lancement.md#le-nettoyage-du-dossier-bin)) |
| `getPlayDir()` | `bin/game/` | Dossiers de jeu (un par profil) |
| `getGameJar()` | `bin/minecraft.jar` | Jar client personnalisé, s'il est fourni par vos fichiers serveur |
| `getNativesDir()` | `bin/natives/` | Librairies natives extraites |
| `getNativesCacheDir()` | `bin/cache_natives/` | Cache des natives |
| `getLibsDir()` | `libraries/` | Librairies Java du jeu |
| `getAssetsDir()` | `assets/` | Index et objets (sons, langues…) |
| `getLogConfigsDir()` | `assets/log_configs/` | Configuration log4j de Mojang |
| `getCacheDir()` | `cache/` | Copie du JSON de version, fichiers temporaires |

S'y ajoutent, au fil des lancements : `bin/versions/<version>/<version>.jar` (le jar du
client), `bin/<runtime-java>/` (le Java du jeu) et `logs/latest-game.log`.

> Utilisez un nom propre à votre launcher. Deux launchers partageant le même dossier
> s'effaceraient mutuellement des fichiers lors du nettoyage.

## LauncherPreferences

Les propriétés de la **fenêtre du launcher** (pas du jeu) :

```java
LauncherPreferences prefs = new LauncherPreferences("Mon launcher", 880, 520, Mover.DONT_MOVE);
```

| Paramètre | Rôle |
|---|---|
| `name` | Titre de la fenêtre |
| `width`, `height` | Taille en pixels, utilisée par `LauncherBase`, `LauncherPane` et les fonds d'écran |
| `Mover.MOVE` / `Mover.DONT_MOVE` | `MOVE` rend la fenêtre déplaçable en la faisant glisser n'importe où. Indispensable pour une fenêtre sans barre de titre (`StageStyle.TRANSPARENT` ou `UNDECORATED`) |

## GameLinks

**Où trouver le jeu.** Trois constructeurs, selon que vous hébergez des fichiers ou non.

### 1. Seulement le JSON de Mojang

```java
GameLinks links = new GameLinks("https://piston-meta.mojang.com/v1/packages/<sha1>/1.21.1.json");
```

Aucune fonction serveur : pas de maintenance, pas de listes ignore/delete, pas de
fichiers personnalisés. C'est ce qu'utilise le [tutoriel](02-tutoriel-launcher-basique.md).

### 2. Un dossier sur votre serveur

```java
GameLinks links = new GameLinks("https://monsite.fr/launcher/", "1.21.1.json");
```

La lib en déduit toutes les URL :

| URL | Rôle |
|---|---|
| `https://monsite.fr/launcher/1.21.1.json` | JSON de la version (que vous hébergez) |
| `https://monsite.fr/launcher/status.json` | [Maintenance](08-serveur-de-fichiers.md#maintenance) |
| `https://monsite.fr/launcher/ignore.json` | [Liste des fichiers à préserver](08-serveur-de-fichiers.md#ignorejson--fichiers-à-préserver) |
| `https://monsite.fr/launcher/delete.json` | [Liste des fichiers à supprimer](08-serveur-de-fichiers.md#deletejson--fichiers-à-supprimer) |
| `https://monsite.fr/launcher/files/` | [Fichiers personnalisés](08-serveur-de-fichiers.md#fichiers-personnalisés) (mods, configs…) |

Tous ne sont pas obligatoires :

- `ignore.json` et `delete.json` absents (erreur 404) : simplement ignorés ;
- `status.json` : lu uniquement si vous activez la maintenance, il doit alors exister ;
- `files/` : interrogé uniquement par les `GameStyle` qui gèrent les fichiers
  personnalisés (voir [GameStyle](#gamestyle)). Il doit alors répondre, sinon la lib
  **ferme l'application** (`System.exit`) ;
- le JSON de version est toujours obligatoire.

### 3. URL par URL

```java
GameLinks links = new GameLinks(
        mojangJsonUrl,                               // JSON de la version (ici celui de Mojang)
        "https://monsite.fr/launcher/ignore.json",   // ou null pour désactiver
        "https://monsite.fr/launcher/delete.json",   // ou null
        "https://monsite.fr/launcher/status.json",   // ou null
        "https://monsite.fr/launcher/files/");       // ou null
```

Le cas le plus courant : le JSON officiel de Mojang (pas besoin de l'héberger) et vos
propres fichiers à côté. Une chaîne vide équivaut à `null`. Les URL `file:` sont
acceptées (fichiers locaux).

### À savoir

- **Détection du mode hors ligne** : au lancement, la lib ouvre une connexion vers le
  *dossier* du JSON (`getBaseUrl()`). Si elle échoue, elle passe en **mode hors ligne** :
  aucun téléchargement, lancement avec les fichiers déjà présents (s'ils existent).
- **Si le JSON de version (ou la liste `files/`) ne peut pas être téléchargé alors que
  l'hôte répond**, la lib appelle `System.exit(0)` : le launcher se ferme sans message.
  Vérifiez vos URL ; la cause est affichée dans la console.
- On peut changer de `GameLinks` à tout moment avec `engine.reg(nouveauxLinks)`, par
  exemple quand le joueur choisit une autre version. Attention : `reg(GameLinks)`
  remet aussi à `null` les métadonnées Forge et `reg(GameConnect)` également.

## GameStyle

**Comment installer et lancer le jeu** : classe principale, gestion des librairies, des
modloaders, des fichiers personnalisés…

| `GameStyle` | À utiliser pour | Fichiers perso (`files/`) |
|---|---|---|
| `VANILLA` | Vanilla jusqu'à 1.18.2 | Non |
| `VANILLA_1_19_HIGHER` | Vanilla 1.19 et plus | Non |
| `VANILLA_PLUS` | Vanilla jusqu'à 1.15.2 + vos fichiers | **Oui** |
| `VANILLA_PLUS_1_16_HIGHER` | Vanilla 1.16 et plus + vos fichiers (testé en 1.21.1) | **Oui** |
| `FABRIC` | Fabric | **Oui** |
| `QUILT` | Quilt | **Oui** |
| `NEOFORGE` | NeoForge | **Oui** |
| `FORGE_1_7_10_OLD` | Forge 1.7.10 | Non |
| `FORGE_1_8_TO_1_12_2` | Forge 1.8 à 1.12.2 | Non |
| `FORGE_1_13_HIGHER` | Forge 1.13.2 à 1.16.5 | Non |
| `FORGE_1_17_HIGHER` | Forge 1.17 à 1.18.2 | Non |
| `FORGE_1_19_HIGHER` | Forge 1.19 et plus | Non |
| `OPTIFINE` | OptiFine (voir [Modloaders](09-modloaders.md#optifine)) | Non |

> Pour distribuer des mods ou des configurations depuis votre serveur sur une version
> vanilla, prenez donc `VANILLA_PLUS` / `VANILLA_PLUS_1_16_HIGHER` et non
> `VANILLA` / `VANILLA_1_19_HIGHER`, qui ignorent l'URL `files/`.

Les modloaders demandent un JSON particulier : voir [Modloaders](09-modloaders.md).

## GameEngine

```java
GameEngine engine = new GameEngine(folder, links, prefs, GameStyle.VANILLA_1_19_HIGHER);
engine.setRequestedVersionId("1.21.1");
```

Autres constructeurs :

```java
new GameEngine(folder, links, prefs, style, GameSize.SIZE_1280x1024);   // taille de fenêtre du jeu
new GameEngine(folder, links, prefs, style, gameForge);                  // métadonnées Forge déjà chargées (rare)
```

### Profils de jeu

Chaque combinaison style + version a **son propre dossier de jeu** dans `bin/game/`,
nommé `<style en minuscules>-<version>` : `vanilla_1_19_higher-1.21.1`,
`fabric-1.20.1`… Les mondes, captures d'écran et mods d'une version ne se mélangent
donc pas avec ceux d'une autre.

- `setRequestedVersionId("1.21.1")` fournit la partie « version » du nom. Sans elle, le
  dossier s'appelle `<style>-default`.
- `setProfileDirectoryName("survie")` impose un nom de dossier libre (`bin/game/survie`).

Certains réglages sont **partagés entre tous les profils** via `bin/game/_shared-userdata/` :
`options.txt`, `optionsof.txt`, `optionsshaders.txt`, `optionsfullscreen.txt`,
`optionsoculus.txt`, `servers.dat`, `servers.dat_old`, `resourcepacks/` et
`shaderpacks/`. La lib les synchronise avant et après chaque partie : le joueur
retrouve ses touches, sa liste de serveurs et ses packs quelle que soit la version.

### Enregistrer des réglages : `reg(...)`

`GameEngine` reçoit ses réglages optionnels par des méthodes `reg` surchargées :

| Appel | Effet | Détails |
|---|---|---|
| `reg(GameMemory)` | Mémoire max du jeu (`-Xmx`) | [Options de jeu](05-options-de-jeu.md#mémoire) |
| `reg(GameSize)` | Taille de la fenêtre du jeu | [Options de jeu](05-options-de-jeu.md#taille-de-la-fenêtre) |
| `reg(GameConnect)` | Connexion directe à un serveur | [Options de jeu](05-options-de-jeu.md#connexion-directe-à-un-serveur) |
| `reg(JVMArguments)` | Arguments JVM supplémentaires | [Options de jeu](05-options-de-jeu.md#arguments-jvm) |
| `reg(GameArguments)` | Arguments de jeu supplémentaires | [Options de jeu](05-options-de-jeu.md#arguments-de-jeu) |
| `reg(GameMaintenance)` | Active la maintenance | [Serveur de fichiers](08-serveur-de-fichiers.md#maintenance) |
| `reg(GameLinks)` | Change de version/de serveur | ci-dessus |
| `reg(GameStyle)` | Change de style | ci-dessus |
| `reg(GameUpdater)` | Obligatoire avant `updater.start()` | [Mise à jour](04-mise-a-jour-et-lancement.md) |
| `reg(Stage)` | Fait par `LauncherBase` | La fenêtre à masquer au lancement du jeu |

Raccourcis combinés : `reg(links, connect)`, `reg(links, connect, memory)`,
`reg(links, forge, connect, memory)`.
