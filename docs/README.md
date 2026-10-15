# Documentation d'AlternativeAPI

AlternativeAPI est une bibliothèque Java 8 / JavaFX pour écrire **son propre launcher
Minecraft**. Elle s'occupe de toute la partie technique :

- télécharger et vérifier le jeu (jar client, librairies, natives, assets) ;
- télécharger automatiquement le **Java requis par la version** (Java 21 pour 1.21, etc.) ;
- construire la ligne de commande et **lancer le jeu** ;
- authentifier le joueur (compte **Microsoft** ou **hors ligne**) ;
- fournir des composants JavaFX prêts à l'emploi pour l'interface du launcher ;
- en option : synchroniser des fichiers depuis votre serveur (mods, configs),
  afficher un message de maintenance, gérer Forge, Fabric, Quilt, NeoForge…

Vous écrivez l'interface et le scénario (« le joueur saisit son pseudo, clique sur
Jouer »), la bibliothèque fait le reste.

## Par où commencer

1. **[Installation](01-installation.md)** : prérequis (JDK 8 avec JavaFX) et ajout
   de la lib à votre projet Maven.
2. **[Tutoriel : un launcher basique](02-tutoriel-launcher-basique.md)** : un launcher
   complet pour Minecraft 1.21.1 en trois classes. Le code du tutoriel est aussi
   disponible, prêt à compiler, dans [`examples/launcher-basique`](../examples/launcher-basique).

## Guides

| Chapitre | Contenu |
|---|---|
| [Concepts](03-concepts.md) | `GameFolder`, `GameLinks`, `GameEngine`, `GameStyle` : les briques de base et l'arborescence créée sur le disque |
| [Mise à jour et lancement](04-mise-a-jour-et-lancement.md) | `GameUpdater`, suivi de la progression, nettoyage du dossier, Java embarqué, logs, crash |
| [Options de jeu](05-options-de-jeu.md) | Mémoire, taille de fenêtre, connexion directe à un serveur, arguments JVM et jeu |
| [Authentification](06-authentification.md) | Comptes hors ligne et Microsoft, session, reconnexion silencieuse, déconnexion |
| [Interface](07-interface.md) | `LauncherBase`, `LauncherPane`, `IScreen` et les composants graphiques |
| [Serveur de fichiers](08-serveur-de-fichiers.md) | Héberger vos fichiers : maintenance, listes ignore/delete, fichiers personnalisés |
| [Modloaders](09-modloaders.md) | Forge, NeoForge, Fabric, Quilt, OptiFine : ce que fait la lib et ce que vous fournissez |
| [Référence de l'API](10-reference-api.md) | Toutes les classes publiques utiles, leurs constructeurs et méthodes |
| [Dépannage](11-depannage.md) | Erreurs fréquentes et limites connues |

## Vue d'ensemble en 30 secondes

```java
GameFolder folder = new GameFolder("monlauncher");                 // %APPDATA%\.monlauncher
GameLinks links = new GameLinks(urlDuJsonMojang);                  // quelle version installer
LauncherPreferences prefs = new LauncherPreferences("Mon launcher", 880, 520, Mover.DONT_MOVE);
GameEngine engine = new GameEngine(folder, links, prefs, GameStyle.VANILLA_1_19_HIGHER);

Session session = new GameAuth("Steve", "", AccountType.OFFLINE).getSession();

GameUpdater updater = new GameUpdater();                           // un Thread
updater.reg(engine);
updater.reg(session);
engine.reg(updater);
updater.start();                                                   // télécharge puis lance le jeu
```

## Conventions de cette documentation

- Les chemins sont donnés pour **Windows** (`%APPDATA%`), la plateforme principale de
  la lib. Les équivalents Linux et macOS sont indiqués dans [Concepts](03-concepts.md).
- Les noms de paquets sont abrégés dans le texte : `alternative_api` désigne
  `fr.trxyy.alternative.alternative_api`, `alternative_auth` désigne
  `fr.trxyy.alternative.alternative_auth`, etc. La [référence](10-reference-api.md)
  donne les imports complets.
- Cette documentation décrit **AlternativeAPI 1.7.0**.
