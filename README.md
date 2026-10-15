<h3 align="center">AlternativeAPI</h3>
<p align="center">Bibliothèque Java 8 / JavaFX pour créer son propre launcher Minecraft.</p>

AlternativeAPI prend en charge toute la partie technique d'un launcher : téléchargement
et vérification du jeu, téléchargement automatique du Java requis par chaque version,
lancement, comptes Microsoft et hors ligne, Forge / Fabric / NeoForge, synchronisation
de fichiers depuis votre serveur, mode maintenance. Vous écrivez l'interface, la lib
fait le reste.

L'authentification (anciennement AlternativeAuth) est incluse : un seul jar suffit.

## Démarrage rapide

```java
GameFolder folder = new GameFolder("monlauncher");                 // %APPDATA%\.monlauncher
GameLinks links = new GameLinks(urlDuJsonMojang);                  // la version à installer
LauncherPreferences prefs = new LauncherPreferences("Mon launcher", 880, 520, Mover.DONT_MOVE);
GameEngine engine = new GameEngine(folder, links, prefs, GameStyle.VANILLA_1_19_HIGHER);
engine.reg(GameMemory.RAM_4G);

Session session = new GameAuth("Steve", "", AccountType.OFFLINE).getSession();

GameUpdater updater = new GameUpdater();
updater.reg(engine);
updater.reg(session);
engine.reg(updater);
updater.start();                                                   // télécharge puis lance le jeu
```

## Documentation

La documentation complète est dans [`docs/`](docs/README.md) :

1. [Installation](docs/01-installation.md)
2. [Tutoriel : un launcher basique](docs/02-tutoriel-launcher-basique.md) — Minecraft 1.21.1 en trois classes
3. [Concepts](docs/03-concepts.md)
4. [Mise à jour et lancement](docs/04-mise-a-jour-et-lancement.md)
5. [Options de jeu](docs/05-options-de-jeu.md)
6. [Authentification](docs/06-authentification.md)
7. [Interface](docs/07-interface.md)
8. [Serveur de fichiers](docs/08-serveur-de-fichiers.md)
9. [Modloaders](docs/09-modloaders.md)
10. [Référence de l'API](docs/10-reference-api.md)
11. [Dépannage et limites connues](docs/11-depannage.md)

Le projet du tutoriel, prêt à compiler, est dans [`examples/launcher-basique`](examples/launcher-basique).

## Installation en bref

Prérequis : un **JDK 8 avec JavaFX** et Maven.

```bash
git clone https://github.com/CapDRAKE/AlternativeAPI-1.5.3-RELOADED.git
cd AlternativeAPI-1.5.3-RELOADED
# adaptez le chemin de jfxrt.jar dans pom.xml si votre JDK est ailleurs
mvn clean install
```

Puis, dans votre launcher :

```xml
<dependency>
    <groupId>fr.trxyy.alternative</groupId>
    <artifactId>alternative-api</artifactId>
    <version>1.7.0</version>
</dependency>
```

Détails : [Installation](docs/01-installation.md).

## Licence

[Apache 2.0](LICENSE).
