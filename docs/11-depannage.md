# 11. Dépannage

Pendant le développement, lancez toujours votre launcher depuis un terminal
(`java -jar MonLauncher.jar`) : le journal de la lib (`[AUpdater] ...`) y est affiché, et
la plupart des problèmes s'y lisent directement. La sortie du jeu est dans
`.monlauncher/logs/latest-game.log`.

## Construction

**`Could not find artifact com.oracle:javafx:jar:2.2 at specified path ...`**
Le chemin de `jfxrt.jar` dans le `pom.xml` de la lib ne correspond pas à votre JDK.
Voir [Installation](01-installation.md#adapter-le-chemin-de-javafx).

**`package javafx.scene does not exist`** (dans votre projet)
Vous compilez avec un JDK sans JavaFX, ou avec Java 11+. Utilisez un JDK 8 qui
embarque JavaFX, y compris pour Maven (`mvn -v`).

**Un correctif dans la lib semble sans effet**
Réinstallez la lib (`mvn clean install`) puis reconstruisez votre launcher avec
`mvn clean package` : sans `clean`, le jar *shadé* peut contenir l'ancienne version de la lib.

## Au lancement

**Le launcher se ferme sans message après le clic sur « Jouer »**
La lib appelle `System.exit` si le JSON de version ou la liste `files/` ne peuvent pas
être téléchargés alors que l'hôte répond (erreur 404, 403…). Vérifiez ces URL dans un
navigateur ; l'erreur exacte est dans la console.

**« Echec de telechargement de certaines librairies. »**
`updater.hasDownloadError()` vaut `true` ; le détail est dans
`updater.getFailedDownloads()` et la console. Souvent un problème réseau passager : un
nouveau lancement ne retélécharge que les fichiers manquants.

**Rien ne se passe au deuxième clic sur « Jouer »**
- Un `GameUpdater` ne peut être démarré qu'une fois (`IllegalThreadStateException`) :
  créez-en un nouveau à chaque lancement.
- Un seul updater peut tourner à la fois : tant que le jeu précédent est ouvert, un
  nouveau lancement est ignoré (`Another update/launch is already running`).

**Le jeu plante au démarrage**
Lisez `logs/latest-game.log`. Causes fréquentes :
- mémoire insuffisante : sans `engine.reg(GameMemory...)`, le jeu n'a que 1 Go ;
- `GameStyle` inadapté à la version ou au modloader : voir
  [le tableau](03-concepts.md#gamestyle) ;
- mod incompatible avec la version ou le loader.

Une `InvalidCredentialsException: Status: 401` dans le log avec un compte hors ligne est
normale et sans conséquence.

**Le launcher ne réapparaît pas après la partie**
C'est le comportement par défaut de JavaFX : voir
[Garder le launcher ouvert](04-mise-a-jour-et-lancement.md#garder-le-launcher-ouvert-pendant-le-jeu).

**Le launcher reste bloqué sur une boîte de dialogue vide au démarrage**
La maintenance est activée mais `status.json` est introuvable. Voir
[Maintenance](08-serveur-de-fichiers.md#maintenance).

## Fichiers et données des joueurs

**Des fichiers disparaissent du dossier de jeu**
C'est le [nettoyage de `bin/`](04-mise-a-jour-et-lancement.md#le-nettoyage-du-dossier-bin) :
déclarez les dossiers concernés dans `ignore.json`.

**Les progrès et statistiques d'un joueur hors ligne repartent de zéro**
La lib attribue un UUID aléatoire à chaque connexion hors ligne : utilisez un
[UUID stable](06-authentification.md#utilisez-un-uuid-stable).

**Un fichier mis à jour sur mon serveur n'arrive pas chez les joueurs**
Limite actuelle : un fichier personnalisé déjà présent n'est pas retéléchargé, même
modifié. Changez son nom. Voir [Fichiers personnalisés](08-serveur-de-fichiers.md#comportement-et-limites-actuelles).

**Un mod retiré de mon serveur reste chez les joueurs**
Les dossiers protégés (`mods/`, `config/`…) ne sont jamais nettoyés, même par
`delete.json`.

## Authentification Microsoft

**Le joueur ne voit pas de code à saisir**
`GameAuth.connectMicrosoft(...)` n'affiche pas le code. Utilisez
[l'implémentation recommandée](06-authentification.md#implémentation-recommandée).

**La connexion échoue après la saisie du code**
Le compte ne possède probablement pas Minecraft, ou c'est un compte mineur sans
autorisation Xbox. Le détail est dans l'exception et la console.

**Changer de compte reconnecte toujours le même**
Appelez `auth.logout(engine)` avant la nouvelle connexion : il supprime les jetons
enregistrés.

## Interface

**Une image ne s'affiche pas / `IllegalArgumentException: input == null`**
Le fichier n'est pas trouvé dans le classpath. Il doit être dans
`src/main/resources/resources/` et appelé par son nom exact
(`loadImage(engine, "logo.png")`). N'utilisez pas `loadImage(String)` ni
`playSound(String)` d'`IScreen`, qui ne trouvent pas les ressources.

**Le launcher se fige**
Un appel réseau ou une attente est exécuté dans le thread JavaFX. Déplacez-le dans un
autre thread et revenez à l'interface avec `Platform.runLater(...)`.

---

## Limites connues (AlternativeAPI 1.7.0)

Récapitulatif des comportements à connaître, détaillés dans les chapitres concernés :

| Domaine | Limite |
|---|---|
| Fichiers personnalisés | Un fichier modifié sur le serveur sous le même nom n'est pas retéléchargé |
| Nettoyage | `delete.json` ne peut rien supprimer dans les dossiers protégés (`mods/`, `config/`…) ; il ne s'applique qu'à `bin/` |
| Fichiers personnalisés | Ignorés avec `VANILLA`, `VANILLA_1_19_HIGHER`, les styles Forge et `OPTIFINE` |
| Erreurs | `System.exit` si le JSON de version ou la liste `files/` ne peuvent pas être téléchargés |
| Hors ligne | UUID aléatoire à chaque connexion |
| Microsoft | `CLIENT_ID` Azure fixé dans la lib ; `connectMicrosoft` n'affiche pas le code |
| Microsoft | Jetons stockés dans `%APPDATA%\authconfig\`, partagés entre launchers ; dossier relatif hors Windows |
| Mémoire | 1 à 10 Go uniquement ; un `-Xmx` passé en argument JVM est écrasé |
| Forge | Version de Forge non configurable (recommandée, sinon la plus récente) |
| Textes | Messages d'état, alertes et fenêtre de crash en français, non personnalisables ; la fenêtre de crash mentionne le support MajestyCraft |
| Interface | `IScreen.loadImage(String)` et `IScreen.playSound(String)` ne trouvent pas les ressources ; `SwitchButton` n'expose pas son état |
| Build | Chemin absolu vers `jfxrt.jar` dans le `pom.xml` |
| Divers | La console affiche « Version: 1.6 » (constante `ApiConstants`) alors que l'artefact est en 1.7.0 |
