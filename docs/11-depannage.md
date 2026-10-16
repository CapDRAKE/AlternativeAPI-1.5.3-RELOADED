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

**« Impossible de recuperer la version du jeu. »**
Le JSON de version n'a pas pu être téléchargé alors que son hôte répond (erreur 404,
403…). Vérifiez l'URL dans un navigateur ; elle figure dans
`updater.getFailedDownloads()` et dans la console.

**« Echec de telechargement de certaines librairies. »**
`updater.hasDownloadError()` vaut `true` ; le détail est dans
`updater.getFailedDownloads()` et la console. Causes : un problème réseau passager (un
nouveau lancement ne retélécharge que les fichiers manquants), ou votre liste `files/`
qui ne répond pas.

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

**Un fichier personnalisé est retéléchargé à chaque lancement**
L'`ETag` de votre liste `files/` n'est pas le MD5 du fichier. Voir
[Format de la liste](08-serveur-de-fichiers.md#format-de-la-liste).

**Un mod retiré de mon serveur reste chez les joueurs**
Les dossiers protégés (`mods/`, `config/`…) ne sont pas nettoyés automatiquement :
listez le fichier dans [`delete.json`](08-serveur-de-fichiers.md#deletejson--fichiers-à-supprimer).

## Authentification Microsoft

**La page Microsoft affiche « The provided value for the input parameter 'redirect_uri' is not valid »**
Votre application Azure ne déclare pas l'adresse de retour `http://localhost`. Voir
[Réglage Azure nécessaire](06-authentification.md#réglage-azure-nécessaire).

**Après la connexion, le navigateur affiche « Ce site est inaccessible » sur `localhost`**
Le launcher n'attendait plus le retour : connexion annulée ou délai dépassé, ou
launcher fermé entre-temps. Relancez la connexion. Si le problème persiste sur une
machine précise (logiciel de sécurité qui bloque les connexions locales), proposez le
[code d'appareil](06-authentification.md#code-dappareil-secours).

**La connexion échoue une fois le compte validé**
Le compte ne possède probablement pas Minecraft, ou c'est un compte mineur sans
autorisation Xbox. Le détail est dans l'exception et la console.

**Changer de compte reconnecte toujours le même**
Appelez `auth.logout(engine)` avant la nouvelle connexion : il supprime les jetons
enregistrés.

## Interface

**Une image ne s'affiche pas / `IllegalArgumentException: input == null`**
Le fichier n'est pas trouvé dans le classpath. Il doit être dans
`src/main/resources/resources/` et appelé par son nom exact
(`loadImage(engine, "logo.png")`), sans le préfixe `resources/`.

**Le launcher se fige**
Un appel réseau ou une attente est exécuté dans le thread JavaFX. Déplacez-le dans un
autre thread et revenez à l'interface avec `Platform.runLater(...)`.

---

## Limites connues (AlternativeAPI 1.7.0)

Récapitulatif des comportements à connaître, détaillés dans les chapitres concernés :

| Domaine | Limite |
|---|---|
| Fichiers personnalisés | Ignorés avec `VANILLA`, `VANILLA_1_19_HIGHER`, les styles Forge et `OPTIFINE` |
| Nettoyage | Les fichiers retirés du serveur restent dans les dossiers protégés tant qu'ils ne sont pas listés dans `delete.json` ; `delete.json` ne supprime pas de dossiers entiers |
| Microsoft | `connectMicrosoft` ne propose pas le code d'appareil en secours |
| Microsoft | Jetons stockés dans `%APPDATA%\authconfig\`, partagés entre launchers ; dossier relatif hors Windows |
| Mémoire | 1 à 10 Go uniquement ; un `-Xmx` passé en argument JVM est écrasé |
| Forge | Version de Forge non configurable (recommandée, sinon la plus récente) |
| Textes | Messages d'état, alertes et fenêtre de crash en français, non personnalisables |
| Interface | `SwitchButton` n'expose pas son état |
| Build | Chemin absolu vers `jfxrt.jar` dans le `pom.xml` |
