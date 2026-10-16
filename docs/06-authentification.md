# 6. Authentification

Le jeu a besoin d'une **`Session`** (`alternative_auth.account.Session`) : un pseudo,
un jeton d'accès et un UUID. On la transmet à l'updater avec `updater.reg(session)`.

```java
Session session = ...;
session.getUsername();   // "Steve"
session.getToken();      // jeton d'accès Minecraft
session.getUuid();       // UUID du joueur
```

Deux façons de l'obtenir : un **compte hors ligne** (n'importe quel pseudo, rien n'est
vérifié) ou un **compte Microsoft** (le joueur possède le jeu).

> `AccountType.MOJANG` et `GameAuth.connectMinecraft(...)` correspondent à l'ancienne
> authentification Mojang, fermée depuis la migration des comptes vers Microsoft. Ne
> les utilisez pas.

---

## Compte hors ligne

```java
GameAuth auth = new GameAuth("Steve", "", AccountType.OFFLINE);
Session session = auth.getSession();
```

- Le mot de passe est ignoré.
- Le joueur peut jouer en solo et sur les serveurs en `online-mode=false`. Les serveurs
  en `online-mode=true` (la valeur par défaut) refusent ces sessions.
- Pas de skin personnalisé : le jeu affiche un skin par défaut.

### L'UUID du joueur

La session reçoit l'**UUID hors ligne standard** de Minecraft, celui qu'un serveur en
`online-mode=false` attribue au pseudo : il est toujours le même pour un pseudo donné,
ce qui préserve les progrès et statistiques du joueur (rangés par UUID) d'une session à
l'autre. Il est aussi disponible directement :

```java
String uuid = GameAuth.offlineUuid("Steve");   // sans tirets
```

### Valider le pseudo

Minecraft accepte des pseudos de 3 à 16 caractères, lettres, chiffres et `_` :

```java
if (!pseudo.matches("[A-Za-z0-9_]{3,16}")) { /* refuser */ }
```

---

## Compte Microsoft

La lib implémente le **« device code flow »** de Microsoft, le même que sur les consoles :

1. le launcher demande un **code** à Microsoft (par exemple `ABCD-EFGH`) ;
2. le joueur ouvre dans son navigateur l'adresse fournie par Microsoft
   (`getVerificationUri()`, en général `https://www.microsoft.com/link`), saisit le code
   et se connecte ;
3. pendant ce temps, le launcher interroge Microsoft jusqu'à la validation ;
4. il échange les jetons Microsoft contre un profil Minecraft (Xbox Live → XSTS →
   Minecraft) et obtient la `Session`.

Le launcher ne voit jamais le mot de passe du joueur.

### Implémentation recommandée

Le code doit être **affiché au joueur** : c'est à votre interface de le faire. Les
appels réseau sont bloquants, exécutez-les hors du thread JavaFX.

```java
import fr.trxyy.alternative.alternative_auth.account.Session;
import fr.trxyy.alternative.alternative_auth.base.AuthConfig;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftOAuthClient;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftOAuthClient.DeviceCode;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftXboxAuth;
import fr.trxyy.alternative.alternative_auth.microsoft.model.MicrosoftModel;

private void connectMicrosoft(GameEngine engine) {
    Thread thread = new Thread(() -> {
        try {
            MicrosoftOAuthClient oauth = new MicrosoftOAuthClient();

            // 1. Code à afficher au joueur
            DeviceCode code = oauth.requestDeviceCode();
            Platform.runLater(() -> showCode(code.getUserCode(), code.getVerificationUri()));
            openInBrowser(code.getVerificationUri());

            // 2. Attente de la validation par le joueur (bloquant)
            MicrosoftModel tokens = oauth.pollForToken(code);

            // 3. Mémorise les jetons pour la reconnexion automatique
            new AuthConfig(engine).createConfigFile(tokens);

            // 4. Profil Minecraft
            Session session = new MicrosoftXboxAuth().getLiveToken(tokens.getAccess_token());
            Platform.runLater(() -> onLoggedIn(session));
        } catch (Exception e) {
            Platform.runLater(() -> onLoginFailed(e));
        }
    }, "connexion-microsoft");
    thread.setDaemon(true);
    thread.start();
}
```

`showCode`, `openInBrowser`, `onLoggedIn` et `onLoginFailed` sont à écrire selon votre
interface (une fenêtre affichant le code en gros, avec un bouton « Copier »,
convient bien). Pour ouvrir le navigateur :
`Desktop.getDesktop().browse(URI.create(url))`, ou `IScreen.openLink(url)`.

`DeviceCode` fournit aussi `getExpiresIn()` (durée de validité du code, en secondes) et
`getInterval()` (intervalle d'interrogation).

**Erreurs à prévoir** (`pollForToken` et `getLiveToken` lèvent une exception) :

- le joueur a refusé, ou le code a expiré avant d'être saisi ;
- le compte Microsoft **ne possède pas Minecraft** (pas de profil) ;
- compte mineur sans autorisation parentale Xbox, compte Xbox jamais initialisé ;
- pas de connexion Internet.

Le message de l'exception est technique : affichez au joueur un message générique et
gardez le détail dans les logs.

### Reconnexion automatique

Après une première connexion, les jetons sont enregistrés (étape 3 ci-dessus). Au
démarrage suivant, essayez une reconnexion silencieuse :

```java
GameAuth auth = new GameAuth(AccountType.MICROSOFT);
if (auth.trySilentRefresh(engine)) {          // bloquant : hors du thread JavaFX
    Session session = auth.getSession();
} else {
    // pas de jetons, ou jetons expirés : relancer la connexion complète
}
```

`trySilentRefresh` utilise le jeton de rafraîchissement enregistré, met à jour les
jetons sur le disque et ne modifie rien en cas d'échec.

> Le jeton d'accès Minecraft expire (au bout d'environ 24 heures) : appelez
> `trySilentRefresh` **juste avant chaque lancement** plutôt qu'une seule fois au
> démarrage du launcher.

### Déconnexion et changement de compte

```java
auth.logout(engine);   // vide la session et supprime les jetons enregistrés
```

Sans cette suppression, la reconnexion silencieuse reconnecterait toujours le même
compte. Pour changer de compte : `logout`, puis une nouvelle connexion complète.

### Où sont stockés les jetons

`AuthConfig` enregistre les jetons Microsoft dans `%APPDATA%\authconfig\auth_infos.json`.

- Ce fichier est **commun à tous les launchers** basés sur AlternativeAPI installés
  sur la machine : se déconnecter dans l'un déconnecte les autres.
- Hors de Windows, la variable `APPDATA` n'existe pas : le dossier `authconfig` est
  alors créé dans le **répertoire courant** du launcher.
- Il contient un jeton de rafraîchissement, qui donne accès au compte Minecraft du
  joueur : ne le journalisez pas, ne le copiez pas ailleurs. La lib masque les jetons
  (et le `device_code` de la connexion en cours) dans ses propres logs.

### L'application Azure utilisée

La connexion Microsoft passe par une application enregistrée sur Microsoft Azure. Par
défaut, la lib utilise celle du mainteneur d'AlternativeAPI
(`MicrosoftOAuthClient.DEFAULT_CLIENT_ID`) : c'est son nom que le joueur verra sur la
page de consentement Microsoft.

Pour un launcher public, enregistrez votre propre application Azure (type « client
public », flux d'appareil activé), demandez à Mojang l'accès à l'API Minecraft pour
cette application, puis déclarez-la **au démarrage du launcher, avant toute
connexion** :

```java
MicrosoftOAuthClient.setClientId("votre-identifiant-d-application-azure");
```

Ce réglage vaut pour toute la lib (connexion, reconnexion silencieuse,
`connectMicrosoft`). Les jetons enregistrés sont liés à l'application qui les a émis :
après un changement d'application, les joueurs doivent se reconnecter une fois.

### `GameAuth.connectMicrosoft(engine, root)`

La lib propose aussi une méthode tout-en-un, qui affiche une petite fenêtre d'attente et
ouvre le navigateur. **Elle n'affiche pas le code à saisir** (il n'apparaît que dans la
console) et rend la main immédiatement, sans signaler la fin de la connexion : elle
n'est pas utilisable telle quelle pour vos joueurs. Préférez l'implémentation
ci-dessus.

---

## Utiliser une session existante

Si vous obtenez pseudo, jeton et UUID par un autre moyen (votre propre système
d'authentification, des tests…), construisez la session directement :

```java
Session session = new Session("Steve", jeton, uuid);
updater.reg(session);
```
