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

La lib propose deux façons de connecter un compte Microsoft :

| Méthode | Ce que fait le joueur | Réglage Azure nécessaire |
|---|---|---|
| **Connexion dans le navigateur** (`MicrosoftBrowserAuth`, recommandée) | La page de connexion Microsoft s'ouvre ; il se connecte (souvent un seul clic s'il l'est déjà) et revient automatiquement au launcher | Adresse de retour `http://localhost` |
| **Code d'appareil** (`MicrosoftOAuthClient`) | Il saisit un code (par exemple `ABCD-EFGH`) sur `https://www.microsoft.com/link`, puis se connecte | « Flux de clients publics » autorisés |

Dans les deux cas, le launcher ne voit jamais le mot de passe du joueur : il reçoit des
jetons Microsoft, qu'il échange contre un profil Minecraft (Xbox Live → XSTS →
Minecraft) pour obtenir la `Session`.

La connexion dans le navigateur est bien plus confortable (gestionnaire de mots de
passe, Windows Hello, clés d'accès, session Microsoft déjà ouverte…). Gardez le code
d'appareil en **secours**, pour le cas où le navigateur ne peut pas être ouvert.

### Connexion dans le navigateur (recommandée)

1. `start()` démarre un petit serveur local sur un port libre de la machine
   (`http://localhost:<port>/`) et renvoie l'adresse de la page de connexion Microsoft ;
2. le launcher ouvre cette page dans le navigateur du joueur ;
3. une fois connecté, Microsoft renvoie le navigateur vers le serveur local, qui affiche
   « Connexion réussie, tu peux revenir dans le launcher » ;
4. `awaitTokens(...)` récupère le code reçu et l'échange contre les jetons.

Les appels sont bloquants : exécutez-les hors du thread JavaFX.

```java
import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import fr.trxyy.alternative.alternative_auth.account.Session;
import fr.trxyy.alternative.alternative_auth.base.AuthConfig;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftBrowserAuth;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftXboxAuth;
import fr.trxyy.alternative.alternative_auth.microsoft.model.MicrosoftModel;

private MicrosoftBrowserAuth browserAuth;

private void connectMicrosoft(GameEngine engine) {
    browserAuth = new MicrosoftBrowserAuth().setLauncherName("Mon launcher");
    showWaitingWindow();   // « Connecte-toi dans ton navigateur… » + bouton Annuler

    Thread thread = new Thread(() -> {
        try {
            // 1. Serveur local + page de connexion Microsoft
            String url = browserAuth.start();
            Desktop.getDesktop().browse(URI.create(url));

            // 2. Attente du retour du navigateur, puis échange contre les jetons
            MicrosoftModel tokens = browserAuth.awaitTokens(10, TimeUnit.MINUTES);

            // 3. Mémorise les jetons pour la reconnexion automatique
            new AuthConfig(engine).createConfigFile(tokens);

            // 4. Profil Minecraft
            Session session = new MicrosoftXboxAuth().getLiveToken(tokens.getAccess_token());
            Platform.runLater(() -> onLoggedIn(session));
        } catch (MicrosoftBrowserAuth.LoginCancelledException e) {
            Platform.runLater(() -> onLoginCancelled());      // refus sur la page, ou Annuler
        } catch (TimeoutException e) {
            Platform.runLater(() -> onLoginFailed(e));        // aucune connexion dans le délai
        } catch (Exception e) {
            Platform.runLater(() -> onLoginFailed(e));
        }
    }, "connexion-microsoft");
    thread.setDaemon(true);
    thread.start();
}

// Bouton « Annuler » de la fenêtre d'attente :
private void cancelMicrosoftLogin() {
    browserAuth.cancel();   // awaitTokens lève aussitôt LoginCancelledException
}
```

`showWaitingWindow`, `onLoggedIn`, `onLoginCancelled` et `onLoginFailed` sont à écrire
selon votre interface. Dans la fenêtre d'attente, proposez aussi :

- **« Rouvrir la page »**, au cas où le joueur aurait fermé l'onglet (gardez l'adresse
  renvoyée par `start()`) ;
- **« Utiliser un code à la place »** : `browserAuth.cancel()`, puis connexion par
  [code d'appareil](#code-dappareil-secours).

Si `Desktop.getDesktop().browse(...)` échoue (aucun navigateur, environnement sans
bureau), basculez directement sur le code d'appareil.

**Sécurité, déjà gérée par la lib :** le serveur local n'écoute que sur la machine du
joueur ; la demande est protégée par PKCE et par un `state` aléatoire (une requête
étrangère au port est ignorée) ; le serveur s'arrête dès la réponse reçue, en cas
d'annulation ou d'expiration du délai. La page Microsoft propose toujours de choisir le
compte (`prompt=select_account`), ce qui permet de changer de compte même si le
navigateur est déjà connecté.

#### Réglage Azure nécessaire

Microsoft n'autorise le retour vers le launcher que si l'application Azure le déclare :
portail Azure → **Inscriptions d'applications** → votre application →
**Authentification** → **Ajouter une plateforme** → **Applications de bureau et
mobiles** → URI de redirection personnalisé **`http://localhost`** (sans port ni
chemin) → Enregistrer.

Pour les comptes Microsoft personnels, Microsoft accepte alors n'importe quel port mais
**aucun chemin** : la lib utilise donc `http://localhost:<port>/`. Sans ce réglage, la
page Microsoft affiche « The provided value for the input parameter 'redirect_uri' is
not valid ». L'application Azure utilisée par défaut par la lib est déjà configurée.

### Code d'appareil (secours)

Le launcher affiche un code que le joueur saisit sur `https://www.microsoft.com/link`.
Le code doit être **affiché au joueur** : c'est à votre interface de le faire.

```java
import fr.trxyy.alternative.alternative_auth.account.Session;
import fr.trxyy.alternative.alternative_auth.base.AuthConfig;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftOAuthClient;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftOAuthClient.DeviceCode;
import fr.trxyy.alternative.alternative_auth.microsoft.MicrosoftXboxAuth;
import fr.trxyy.alternative.alternative_auth.microsoft.model.MicrosoftModel;

private void connectMicrosoftWithCode(GameEngine engine) {
    Thread thread = new Thread(() -> {
        try {
            MicrosoftOAuthClient oauth = new MicrosoftOAuthClient();

            // 1. Code à afficher au joueur
            DeviceCode code = oauth.requestDeviceCode();
            Platform.runLater(() -> showCode(code.getUserCode(), code.getVerificationUri()));
            openInBrowser(code.getVerificationUri());

            // 2. Attente de la validation par le joueur (bloquant)
            MicrosoftModel tokens = oauth.pollForToken(code);

            // 3. et 4. : identiques à la connexion dans le navigateur
            new AuthConfig(engine).createConfigFile(tokens);
            Session session = new MicrosoftXboxAuth().getLiveToken(tokens.getAccess_token());
            Platform.runLater(() -> onLoggedIn(session));
        } catch (Exception e) {
            Platform.runLater(() -> onLoginFailed(e));
        }
    }, "connexion-microsoft-code");
    thread.setDaemon(true);
    thread.start();
}
```

Une fenêtre affichant le code en gros, avec « Copier le code », « Ouvrir Microsoft » et
« Annuler », convient bien. `pollForToken` attend jusqu'à l'expiration du code
(`getExpiresIn()`, 15 minutes) : pour permettre l'annulation, interrompez le thread
(`thread.interrupt()`) et ignorez l'exception qui en résulte.

### Erreurs à prévoir

`awaitTokens`, `pollForToken` et `getLiveToken` lèvent une exception quand :

- le joueur a refusé ou annulé, ou le délai a expiré ;
- le compte Microsoft **ne possède pas Minecraft** (pas de profil) ;
- c'est un compte mineur sans autorisation parentale Xbox, ou un compte Xbox jamais
  initialisé ;
- il n'y a pas de connexion Internet.

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

Pour un launcher public, enregistrez votre propre application Azure (comptes Microsoft
personnels, « flux de clients publics » autorisés, adresse de retour `http://localhost`
sur la plateforme « Applications de bureau et mobiles », voir
[Réglage Azure nécessaire](#réglage-azure-nécessaire)), demandez à Mojang l'accès à
l'API Minecraft pour cette application, puis déclarez-la **au démarrage du launcher,
avant toute connexion** :

```java
MicrosoftOAuthClient.setClientId("votre-identifiant-d-application-azure");
```

Ce réglage vaut pour toute la lib (connexion, reconnexion silencieuse,
`connectMicrosoft`). Les jetons enregistrés sont liés à l'application qui les a émis :
après un changement d'application, les joueurs doivent se reconnecter une fois.

### Méthode tout-en-un : `GameAuth.connectMicrosoft`

Pour un launcher simple, la lib propose une méthode qui fait tout : reconnexion
silencieuse si des jetons sont enregistrés, sinon connexion dans le navigateur, avec une
petite fenêtre d'attente (la fermer annule la connexion).

```java
GameAuth auth = new GameAuth(AccountType.MICROSOFT);
auth.connectMicrosoft(engine, root, connecte -> {     // appelé sur le thread JavaFX
    if (connecte) {
        Session session = auth.getSession();
    } else {
        // annulé, refusé ou en erreur
    }
});
```

Elle rend la main tout de suite : le résultat arrive dans le rappel. Elle ne propose pas
le code d'appareil en secours ; pour une fenêtre à vos couleurs et ce secours, utilisez
[`MicrosoftBrowserAuth`](#connexion-dans-le-navigateur-recommandée) directement.

---

## Utiliser une session existante

Si vous obtenez pseudo, jeton et UUID par un autre moyen (votre propre système
d'authentification, des tests…), construisez la session directement :

```java
Session session = new Session("Steve", jeton, uuid);
updater.reg(session);
```
