# 4. Mise à jour et lancement

Tout passe par **`GameUpdater`** (`alternative_api.updater.GameUpdater`), un `Thread`
qui télécharge ce qui manque, vérifie l'installation puis lance le jeu.

## Démarrer

```java
GameUpdater updater = new GameUpdater();
updater.reg(engine);              // le moteur
updater.reg(session);             // le joueur (voir Authentification)
engine.reg(updater);              // obligatoire : la lib y accède pendant le téléchargement
updater.start();
```

- **Un `GameUpdater` par lancement.** Un `Thread` ne peut être démarré qu'une fois :
  créez-en un nouveau à chaque clic sur « Jouer ».
- **Un seul lancement à la fois** : si un updater tourne déjà, le suivant s'arrête
  aussitôt (message `Another update/launch is already running` dans la console).
  Désactivez votre bouton « Jouer » pendant l'opération.
- Les réglages (`engine.reg(GameMemory...)`, etc.) doivent être faits **avant**
  `updater.start()`.

## Ce que fait l'updater

En ligne (l'hôte du JSON répond) :

1. télécharge le JSON de la version dans `cache/` ;
2. lit les listes `ignore.json` / `delete.json` si elles sont configurées ;
3. [Forge 1.13+] récupère les métadonnées Forge officielles ;
4. indexe la version et les assets ;
5. [styles avec fichiers perso] lit la liste `files/` de votre serveur ;
6. télécharge les assets, les librairies, le jar client, les natives ;
7. télécharge vos fichiers personnalisés, les librairies Forge, installe NeoForge ;
8. télécharge **le Java requis par la version** ;
9. récupère la configuration log4j ;
10. **nettoie `bin/`** (voir plus bas) ;
11. si un téléchargement a échoué : s'arrête là, **sans lancer le jeu** ;
12. sinon : masque la fenêtre du launcher, lance Minecraft et **attend sa fermeture**.

Les fichiers déjà présents et intacts ne sont pas retéléchargés : les lancements
suivants sont rapides.

**Hors ligne** (l'hôte ne répond pas) : aucun téléchargement ni nettoyage, la lib lance
le jeu avec les fichiers déjà installés. Le premier lancement nécessite donc Internet.

## Suivre la progression

La lib ne publie pas d'événements : lisez l'état de l'updater à intervalle régulier,
depuis le thread JavaFX (une `Timeline` convient très bien).

| Membre | Type | Contenu |
|---|---|---|
| `filesToDownload` | `int` (champ public) | Nombre de fichiers à télécharger, découvert au fil de l'eau |
| `downloadedFiles` | `int` (champ public) | Nombre de fichiers téléchargés |
| `getCurrentInfo()` | `String` | Étape en cours, en français : `Telechargement des ressources.`, `Verification de l'installation.`… |
| `getCurrentFile()` | `String` | Nom du fichier en cours, quand il est renseigné |
| `isAlive()` | `boolean` | `false` quand l'updater a fini (jeu fermé, ou échec) |
| `hasDownloadError()` | `boolean` | `true` si au moins un fichier n'a pas pu être téléchargé |
| `getFailedDownloads()` | `List<String>` | Le détail des échecs |

```java
Timeline timeline = new Timeline(new KeyFrame(Duration.millis(200), e -> {
    int total = updater.filesToDownload;
    int done = updater.downloadedFiles;
    progressBar.setProgress(total > 0 ? (double) done / total : ProgressBar.INDETERMINATE_PROGRESS);
    statusLabel.setText(updater.getCurrentInfo());
    if (!updater.isAlive() && updater.hasDownloadError()) {
        statusLabel.setText("Échec du téléchargement");
    }
}));
timeline.setCycleCount(Animation.INDEFINITE);
timeline.play();
```

`filesToDownload` augmente pendant l'indexation : la barre peut reculer au début. Pour
une barre parfaitement monotone, affichez une progression indéterminée tant que
`getCurrentInfo()` n'indique pas un téléchargement.

## Pendant et après la partie

Quand le jeu démarre, la lib **masque** la fenêtre du launcher (`stage.hide()`) puis
attend la fin du processus Minecraft dans le thread de l'updater.

### Comportement par défaut

JavaFX arrête l'application quand sa dernière fenêtre est masquée (« implicit exit »).
Résultat : **le launcher disparaît au lancement du jeu et le processus du launcher se
termine quand le joueur quitte Minecraft.** C'est le comportement du tutoriel.

### Garder le launcher ouvert pendant le jeu

Pour réafficher le launcher à la fermeture du jeu (ou laisser la lib afficher sa fenêtre
de crash, voir plus bas), désactivez l'arrêt implicite au démarrage, puis surveillez la
fin de l'updater :

```java
// dans start(), avant toute chose
Platform.setImplicitExit(false);

// dans votre Timeline de progression
if (!updater.isAlive()) {
    timeline.stop();
    Platform.runLater(() -> {
        // runLater : laisse passer l'éventuelle fenêtre de crash affichée par la lib
        if (!stage.isShowing()) {
            stage.show();                    // ou Platform.exit() pour quitter
        }
    });
}
```

N'oubliez pas qu'avec `setImplicitExit(false)`, fermer la fenêtre ne quitte plus
l'application d'elle-même : `LauncherBase` s'en charge (il appelle `Platform.exit()` et
`System.exit(0)` sur la croix), mais si vous masquez la fenêtre vous-même, quittez
explicitement.

### Crash du jeu

Si Minecraft se termine avec un code d'erreur, la lib tente de réafficher le launcher
et d'ouvrir une boîte de dialogue « Minecraft s'est fermé » proposant d'ouvrir le log.
Cela ne fonctionne que si l'interface JavaFX est encore active (voir
`setImplicitExit(false)` ci-dessus). Le texte de cette fenêtre est en français et
mentionne le support MajestyCraft : il n'est pas personnalisable sans modifier la lib.

### Temps de jeu

`GameRunner.gameSessionCallback` (statique, `java.util.function.LongConsumer`) est
appelé à la fin de chaque partie avec sa durée en millisecondes :

```java
GameRunner.gameSessionCallback = dureeMs -> System.out.println("Partie de " + dureeMs / 60000 + " min");
```

(`GameRunner` est dans `alternative_api.build`. Le callback est appelé depuis le thread
de l'updater : utilisez `Platform.runLater` pour toucher à l'interface.)

## Les logs

| Où | Contenu |
|---|---|
| Console (sortie standard) | Journal de la lib, préfixé `[AUpdater]` : étapes, fichiers, commande de lancement (jeton masqué) |
| `.monlauncher/logs/latest-game.log` | Sortie complète de Minecraft, réécrite à chaque lancement |
| `bin/game/<profil>/logs/` | Logs propres à Minecraft (`latest.log`…) |

Lancez votre launcher depuis un terminal (`java -jar MonLauncher.jar`) pendant le
développement pour lire le journal de la lib.

## Le Java du jeu

Chaque JSON de version indique le Java dont il a besoin (`javaVersion.component`).
L'updater télécharge ce runtime officiel de Mojang dans `bin/<component>/`, par exemple
`bin/java-runtime-delta/` (Java 21) pour Minecraft 1.21, et lance le jeu avec.

- Le joueur n'a rien à installer pour le jeu.
- Si le runtime téléchargé est absent ou inutilisable, la lib essaie le Java qui
  exécute le launcher (Java 8, donc seulement valable pour les anciennes versions). Le
  Java retenu est affiché dans la console : `Using bundled Java runtime: ...`.
- Certains modloaders imposent un autre runtime ; la lib le choisit elle-même.

## Le nettoyage du dossier `bin`

> ⚠️ **À lire absolument.**

À chaque lancement en ligne, après les téléchargements, la lib parcourt **tout le
dossier `bin/`** et **supprime chaque fichier qu'elle n'a pas elle-même installé**.
L'objectif : garantir une installation identique pour tous les joueurs (un mod retiré
du serveur disparaît chez eux).

Sont **conservés** :

- les fichiers installés par la lib (jeu, librairies, natives, Java, vos fichiers
  `files/`) ;
- dans chaque dossier de jeu `bin/game/<profil>/` (et `bin/game/` lui-même) :
  - `saves/`, `screenshots/`, `logs/`, `crash-reports/`
  - `resourcepacks/`, `shaderpacks/`, `server-resource-packs/`
  - `mods/`, `config/`
  - `options*.txt`, `servers.dat`, `servers.dat_old`, `usercache.json`,
    `usernamecache.json`, `lastlogin` ;
- ce que vous listez dans [`ignore.json`](08-serveur-de-fichiers.md#ignorejson--fichiers-à-préserver).

Tout le reste de `bin/` est supprimé. Exemples réels :

| Fichier | Résultat |
|---|---|
| `bin/game/vanilla_1_19_higher-1.21.1/saves/Monde/level.dat` | conservé |
| `bin/game/vanilla_1_19_higher-1.21.1/mods/monmod.jar` | conservé |
| `bin/game/vanilla_1_19_higher-1.21.1/options.txt` | conservé |
| `bin/game/vanilla_1_19_higher-1.21.1/journeymap/data.txt` | **supprimé** |
| `bin/game/vanilla_1_19_higher-1.21.1/notes.txt` | **supprimé** |
| `bin/perso/fichier.txt` | **supprimé** |

Ce qui est hors de `bin/` (`assets/`, `libraries/`, `cache/`, `logs/`) n'est jamais
nettoyé.

**Conséquences pratiques**

- Un mod qui écrit ailleurs que dans `mods/` ou `config/` (cartes JourneyMap ou
  Xaero, sauvegardes de schémas, etc.) perd ses données à chaque lancement si vous ne
  déclarez pas son dossier dans `ignore.json`.
- Ne stockez jamais les données de **votre launcher** (paramètres, cache) dans `bin/`.

### Protéger des fichiers sans serveur

Si vous n'hébergez pas d'`ignore.json`, générez-en un localement (hors de `bin/`) et
passez-le en URL `file:` :

```java
File ignore = new File(gameFolder.getCacheDir(), "ignore.json");
String json = "{\"files\": [], \"folders\": [\"journeymap/\", \"xaero/\"]}";
Files.write(ignore.toPath(), json.getBytes(StandardCharsets.UTF_8));

GameLinks links = new GameLinks(mojangJsonUrl, ignore.toURI().toString(), null, null, null);
```

Le format est détaillé dans [Serveur de fichiers](08-serveur-de-fichiers.md#ignorejson--fichiers-à-préserver).
