# 10. Référence de l'API

Les classes publiques utiles pour écrire un launcher, par paquet. Les classes internes
(modèles JSON de Mojang, téléchargeurs, analyse des librairies…) ne sont pas listées :
vous n'avez pas à les utiliser directement.

Préfixe commun : `fr.trxyy.alternative`.

---

## `alternative_api` — le moteur

### `GameFolder`

| Membre | Description |
|---|---|
| `GameFolder(String nom)` | Dossier `%APPDATA%\.<nom>` (Windows), `~/.<nom>` (Linux), `~/Library/Application Support/<nom>` (macOS). Crée l'arborescence |
| `getGameDir()` | Racine |
| `getBinDir()` | `bin/` |
| `getPlayDir()` | `bin/game/` |
| `getGameJar()` | `bin/minecraft.jar` |
| `getLibsDir()` | `libraries/` |
| `getAssetsDir()` | `assets/` |
| `getLogConfigsDir()` | `assets/log_configs/` |
| `getCacheDir()` | `cache/` |
| `getNativesDir()` / `getNativesCacheDir()` | `bin/natives/` / `bin/cache_natives/` |

### `GameLinks`

| Membre | Description |
|---|---|
| `GameLinks(String jsonUrl)` | JSON seul, aucune fonction serveur |
| `GameLinks(String baseUrl, String jsonName)` | Tout dans un dossier : `<base><jsonName>`, `status.json`, `ignore.json`, `delete.json`, `files/` |
| `GameLinks(String jsonUrl, String ignoreUrl, String deleteUrl, String maintenanceUrl, String customFilesUrl)` | URL par URL, `null` ou `""` pour désactiver |
| `getJsonUrl()`, `getJsonName()`, `getBaseUrl()` | JSON de version, son nom de fichier, son dossier |
| `getIgnoreListUrl()`, `getDeleteListUrl()`, `getMaintenanceUrl()`, `getCustomFilesUrl()` | URL des fonctions serveur (`null` si désactivées) |

### `LauncherPreferences`

| Membre | Description |
|---|---|
| `LauncherPreferences(String titre, int largeur, int hauteur, Mover mover)` | Fenêtre du launcher |
| `getName()`, `getWidth()`, `getHeight()`, `isMovable()` | Accesseurs |
| `getResourceLocation()` | Préfixe des ressources : `/resources/` |

### `GameEngine`

| Membre | Description |
|---|---|
| `GameEngine(GameFolder, GameLinks, LauncherPreferences, GameStyle)` | Constructeur principal |
| `GameEngine(..., GameStyle, GameSize)` | Avec une taille de fenêtre de jeu |
| `GameEngine(..., GameStyle, GameForge)` | Avec des métadonnées Forge déjà chargées |
| `reg(...)` | Enregistre un réglage : `GameLinks`, `GameStyle`, `GameMemory`, `GameSize`, `GameConnect`, `JVMArguments`, `GameArguments`, `GameMaintenance`, `GameUpdater`, `Stage`, `GameForge`, `MinecraftVersion` ; combinés : `(links, connect)`, `(links, connect, memory)`, `(links, forge, connect, memory)` |
| `setRequestedVersionId(String)` / `getRequestedVersionId()` | Version demandée, utilisée dans le nom du dossier de jeu |
| `setProfileDirectoryName(String)` / `getProfileDirectoryName()` | Nom libre du dossier de jeu (`bin/game/<nom>`) |
| `setGameStyle(GameStyle)` | Équivalent de `reg(GameStyle)` |
| `getGameFolder()`, `getGameLinks()`, `getGameStyle()`, `getGameSize()`, `getGameMemory()`, `getGameConnect()`, `getJVMArguments()`, `getGameArguments()`, `getGameUpdater()`, `getLauncherPreferences()` | Accesseurs |
| `getStage()` | Fenêtre du launcher (enregistrée par `LauncherBase`) |
| `getGameMaintenance()` | Maintenance enregistrée, ou une maintenance désactivée par défaut |
| `getMinecraftVersion()` | JSON de version analysé (renseigné pendant la mise à jour) |
| `isOnline()` | Résultat du dernier test de connexion de l'updater |
| `getWidth()`, `getHeight()` | Taille du launcher |

### `GameStyle` (énumération)

`VANILLA`, `VANILLA_1_19_HIGHER`, `VANILLA_PLUS`, `VANILLA_PLUS_1_16_HIGHER`, `FABRIC`,
`QUILT`, `NEOFORGE`, `OPTIFINE`, `FORGE_1_7_10_OLD`, `FORGE_1_8_TO_1_12_2`,
`FORGE_1_13_HIGHER`, `FORGE_1_17_HIGHER`, `FORGE_1_19_HIGHER`.
Voir le [tableau de choix](03-concepts.md#gamestyle). Méthodes : `getMainClass()`,
`getTweakArgument()`, `getSpecificsArguments()`.

### `GameMemory` (énumération)

`DEFAULT` (1G), `RAM_2G` … `RAM_10G`. `getCount()` renvoie la valeur `-Xmx` (`"4G"`).
`GameMemory.getMemory(double gigaoctets)` : 1 à 10 vers la constante, sinon `DEFAULT`.

### `GameSize` (énumération)

`DEFAULT` (854×480), `SIZE_1024x768`, `SIZE_1280x1024`, `SIZE_1366x768`,
`SIZE_1600x900`, `SIZE_1920x1080`, `SIZE_2560x1440`.
`getWidth()`, `getHeight()`, `getDesc()` (`"1920x1080"`), `GameSize.getWindowSize(int index)`
(0 à 6), `GameSize.getWindowSize(String desc)` (renvoie l'index).

### `GameConnect`

| Membre | Description |
|---|---|
| `GameConnect(String ip, String port)` | Serveur à rejoindre au lancement |
| `getIp()`, `getPort()` | Accesseurs |

### `JVMArguments` / `GameArguments`

| Membre | Description |
|---|---|
| `JVMArguments(String[] args)` / `getJVMArguments()` | Arguments JVM supplémentaires |
| `GameArguments(String[] args)` / `getGameArguments()` | Arguments de jeu supplémentaires |

---

## `alternative_api.updater`

### `GameUpdater extends Thread`

| Membre | Description |
|---|---|
| `GameUpdater()` | Un par lancement |
| `reg(GameEngine)`, `reg(Session)` | Obligatoires avant `start()` |
| `start()` | Télécharge, vérifie, puis lance le jeu et attend sa fermeture |
| `filesToDownload`, `downloadedFiles` | Compteurs publics (`int`) |
| `getCurrentInfo()` | Étape en cours (texte en français) |
| `getCurrentFile()` | Fichier en cours, quand renseigné |
| `hasDownloadError()`, `getFailedDownloads()` | Échecs de téléchargement (le jeu n'est alors pas lancé) |
| `isOnline()` | Teste la connexion vers le dossier du JSON |
| `getEngine()` | Le moteur |

---

## `alternative_api.build`

### `GameRunner`

Construit la commande et lance le jeu ; créé par l'updater, vous ne l'instanciez pas.

| Membre | Description |
|---|---|
| `static LongConsumer gameSessionCallback` | Appelé à la fin de chaque partie avec sa durée en ms (depuis le thread de l'updater) |

---

## `alternative_api.maintenance`

| Membre | Description |
|---|---|
| `GameMaintenance(Maintenance, GameEngine)` | À enregistrer avec `engine.reg(...)` avant `LauncherBase` |
| `Maintenance.USE` / `Maintenance.DONT_USE` | Active / désactive |
| `readMaintenance()` | Lit le statut (`"Ok"` ou le message). Lève `IOException` si le fichier est inaccessible |
| `isAccessBlocked()` | `true` si `LauncherBase` a bloqué l'ouverture |

---

## `alternative_api.utils`

| Classe | Membres utiles |
|---|---|
| `Mover` | `MOVE`, `DONT_MOVE` |
| `WindowStyle` | `TRANSPARENT`, `BASIC` |
| `Logger` | `log(String)`, `err(String)` (préfixe `[AUpdater]`), `getLines()` : tout le journal depuis le démarrage |
| `OperatingSystem` | `getCurrentPlatform()` (`WINDOWS`, `LINUX`, `OSX`…), `isWindows()`, `isLinux()`, `isMac()`, `openLink(URI)`, `openFolder(File)`, `getJavaPath()` |
| `FontLoader` | `FontLoader.loadFont(fichier, nomDeLaPolice, taille)`, `loadFontItalic(...)` |
| `ResourceLocation` | `loadImage(engine, nom)`, `getMedia(engine, nom)` |

`HastebinSender` vise un service de partage de texte qui n'est plus en ligne : ne
l'utilisez pas.

### `alternative_api.utils.config`

`LauncherConfig` et `EnumConfig` forment le système de configuration de
MajestyLauncher, inclus dans la lib : clés prédéfinies (RAM, version, Discord,
OptiFine…) et fichier `launcher_config.json` placé **à la racine** de
`%APPDATA%` (Windows), `~/Library/Application Support` (macOS) ou du dossier personnel
(Linux), donc partagé avec tout autre launcher qui l'utilise. Pour votre launcher,
préférez votre propre fichier de configuration, dans votre dossier de jeu (hors `bin/`).

---

## `alternative_api_ui` — l'interface

| Classe | Membres |
|---|---|
| `base.AlternativeBase` | Classe abstraite (`Application`) : `start(Stage)` |
| `base.LauncherBase` | `LauncherBase(Stage, Scene, StageStyle, GameEngine)`, `setIconImage(Stage, String)`, `loadImage(engine, nom)` |
| `base.IScreen` | `drawBackgroundImage(engine, root, nom)`, `drawAnimatedBackground(engine, root, nom)`, `drawImage(engine, image, x, y, l, h, root, Mover)`, `loadImage(engine, nom)`, `loadImage(nom)`, `playSound(nom)`, `drawRect(root, x, y, l, h, Color)`, `openLink(url)`, `fadeIn(noeud, ms)`, `fadeOut(noeud, ms)` |
| `LauncherPane` | `LauncherPane(engine)`, `LauncherPane(engine, rayonDesCoins, WindowStyle)`, `setCorners(double)`, `setTransparent()` |
| `LauncherAlert` | `LauncherAlert(message, AlertType)`, `LauncherAlert(message, details)` |
| `LauncherBackground` | `LauncherBackground(engine, nom, root)` (image, ou vidéo `.mp4`/`.avi`/`.mov`) |
| `LauncherLogo` | `LauncherLogo(image, l, h, root)`, `(image, l, h, x, y, root)` |
| `LauncherConsole` | `LauncherConsole(root)` (`TextArea`) |

Composants (`alternative_api_ui.components`), détaillés dans [Interface](07-interface.md#composants) :
`LauncherButton`, `LauncherLabel`, `LauncherTextField`, `LauncherPasswordField`,
`LauncherProgressBar`, `LauncherImage`, `LauncherRectangle`, `SwitchButton`.

---

## `alternative_auth` — l'authentification

### `account.Session`

| Membre | Description |
|---|---|
| `Session(String pseudo, String jeton, String uuid)` | Session complète |
| `getUsername()`, `getToken()`, `getUuid()` | Accesseurs |
| `setUsername(...)`, `setToken(...)`, `setUuid(...)` | Modificateurs |

### `account.AccountType`

`OFFLINE`, `MICROSOFT`, `MOJANG` (obsolète).

### `base.GameAuth`

| Membre | Description |
|---|---|
| `GameAuth(String pseudo, String motDePasse, AccountType.OFFLINE)` | Session hors ligne immédiate, avec l'UUID hors ligne standard |
| `static offlineUuid(String pseudo)` | UUID hors ligne standard de Minecraft pour ce pseudo (sans tirets) |
| `GameAuth(AccountType)` | Objet vide, pour `trySilentRefresh` ou `connectMicrosoft` |
| `trySilentRefresh(GameEngine)` | Reconnexion Microsoft avec les jetons enregistrés. Bloquant. `true` si réussi |
| `logout(GameEngine)` | Vide la session et supprime les jetons enregistrés |
| `getSession()`, `setSession(Session)`, `isLogged()` | Session courante |
| `connectMicrosoft(GameEngine, Pane, Consumer<Boolean>)` | Connexion tout-en-un (reconnexion silencieuse, sinon navigateur) ; le rappel reçoit `true` si connecté. Voir [Authentification](06-authentification.md#méthode-tout-en-un--gameauthconnectmicrosoft) |
| `connectMicrosoft(GameEngine, Pane)` | Idem, sans rappel |

### `base.AuthConfig`

| Membre | Description |
|---|---|
| `AuthConfig(GameEngine)` | Fichier `%APPDATA%\authconfig\auth_infos.json` |
| `createConfigFile(MicrosoftModel)` | Enregistre les jetons Microsoft |
| `canRefresh()` | Des jetons sont enregistrés |
| `deleteConfigFile()` | Supprime les jetons |

### `microsoft.MicrosoftBrowserAuth`

Connexion dans le navigateur (une instance par tentative), voir
[Authentification](06-authentification.md#connexion-dans-le-navigateur-recommandée).

| Membre | Description |
|---|---|
| `setLauncherName(String)` | Nom affiché sur la page de confirmation du navigateur |
| `start()` | Démarre le serveur local sur un port libre, renvoie l'adresse de la page de connexion Microsoft à ouvrir |
| `awaitTokens(long, TimeUnit)` | Attend le retour du navigateur et renvoie un `MicrosoftModel`. Bloquant. Lève `LoginCancelledException` (refus ou `cancel()`) ou `TimeoutException` |
| `cancel()` | Abandonne la connexion et arrête le serveur local |

### `microsoft.MicrosoftOAuthClient`

| Membre | Description |
|---|---|
| `static setClientId(String)` / `static getClientId()` | Application Azure utilisée par toute la lib ; `null` rétablit la valeur par défaut |
| `DEFAULT_CLIENT_ID` | Application Azure par défaut (celle du mainteneur de la lib) |
| `requestDeviceCode()` | Code d'appareil : renvoie un `DeviceCode` (`getUserCode()`, `getVerificationUri()`, `getExpiresIn()`, `getInterval()`, `getDeviceCode()`) |
| `pollForToken(DeviceCode)` | Attend la validation du code par le joueur, renvoie un `MicrosoftModel`. Bloquant |
| `exchangeAuthorizationCode(code, redirectUri, codeVerifier)` | Échange un code de connexion contre les jetons (utilisé par `MicrosoftBrowserAuth`) |
| `refreshWithToken(String refreshToken)` | Renouvelle les jetons |

### `microsoft.MicrosoftXboxAuth`

| Membre | Description |
|---|---|
| `getLiveToken(String accessTokenMicrosoft)` | Xbox Live → XSTS → Minecraft, renvoie la `Session`. Lève une exception si le compte ne possède pas le jeu |

### `microsoft.model.MicrosoftModel`

`getAccess_token()`, `getRefresh_token()`, `getExpires_in()`, `getToken_type()`,
`getScope()`, `getUser_id()`.
