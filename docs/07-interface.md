# 7. Interface

La partie graphique de la lib (`alternative_api_ui`) est une fine couche au-dessus de
**JavaFX 8** : des classes de base pour la fenêtre, et des composants qui sont des
contrôles JavaFX standards avec quelques raccourcis. Tout ce que vous savez faire en
JavaFX (CSS, mises en page, animations, autres bibliothèques comme JFoenix) reste
utilisable.

## La fenêtre

### `AlternativeBase`

Classe abstraite à étendre pour votre classe principale. C'est une
`javafx.application.Application` :

```java
public class MonLauncher extends AlternativeBase {
    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage primaryStage) { /* ... */ }
}
```

### `LauncherBase`

À créer en dernier dans `start()`, une fois la scène prête :

```java
LauncherBase base = new LauncherBase(primaryStage, scene, StageStyle.DECORATED, engine);
base.setIconImage(primaryStage, "icon.png");   // icône de la fenêtre (voir Ressources)
```

Le constructeur :

- enregistre la fenêtre dans le moteur (`engine.reg(stage)`) : c'est elle qui sera
  masquée au lancement du jeu ;
- applique le style, le titre et la taille des `LauncherPreferences`, rend la fenêtre
  non redimensionnable ;
- avec `StageStyle.TRANSPARENT`, rend le fond de la scène transparent ;
- quitte complètement l'application (`Platform.exit()` puis `System.exit(0)`) quand on
  ferme la fenêtre ;
- avec `Mover.MOVE`, rend la fenêtre déplaçable en la faisant glisser ;
- si la [maintenance](08-serveur-de-fichiers.md#maintenance) est activée et que le
  serveur n'indique pas `Ok`, affiche le message et **n'ouvre pas** la fenêtre ;
- sinon, affiche la fenêtre.

Les styles utiles :

| `StageStyle` | Résultat |
|---|---|
| `DECORATED` | Fenêtre classique avec la barre de titre du système (le plus simple) |
| `UNDECORATED` | Sans barre de titre ni bordure |
| `TRANSPARENT` | Sans barre de titre, fond transparent : coins arrondis, ombres, formes libres |

### `LauncherPane`

Un `Pane` aux dimensions du launcher, à utiliser comme racine de la scène :

```java
LauncherPane root = new LauncherPane(engine);                              // simple
LauncherPane root = new LauncherPane(engine, 20, WindowStyle.TRANSPARENT); // coins arrondis de 20 px, fond transparent
```

`setCorners(taille)` arrondit les coins (par découpage), `setTransparent()` rend le
fond transparent.

### Exemple : fenêtre sans bordure avec coins arrondis

```java
LauncherPreferences prefs = new LauncherPreferences("Mon launcher", 1000, 600, Mover.MOVE);
// ... GameFolder, GameLinks, GameEngine ...

LauncherPane root = new LauncherPane(engine, 20, WindowStyle.TRANSPARENT);
new LauncherPanel(root, engine);   // votre contenu, avec une image de fond par exemple
new LauncherBase(primaryStage, new Scene(root), StageStyle.TRANSPARENT, engine);
```

Sans barre de titre, ajoutez vos propres boutons :

```java
LauncherButton close = new LauncherButton("✕", root);
close.setBounds(960, 10, 30, 30);
close.setOnAction(e -> { Platform.exit(); System.exit(0); });

LauncherButton minimize = new LauncherButton("—", root);
minimize.setBounds(925, 10, 30, 30);
minimize.setOnAction(e -> engine.getStage().setIconified(true));
```

> `setStyle(...)` remplace tout le style CSS en ligne d'un nœud : sur un
> `LauncherPane` transparent, donnez la couleur de fond à un nœud enfant (image de fond,
> `Rectangle`) plutôt qu'au panneau lui-même.

## `IScreen` : la classe de votre contenu

Faites hériter votre panneau d'`IScreen` pour profiter de ses utilitaires :

| Méthode | Effet |
|---|---|
| `drawBackgroundImage(engine, root, "fond.png")` | Image de fond étirée à la taille du launcher |
| `drawAnimatedBackground(engine, root, "fond.mp4")` | Vidéo de fond en boucle |
| `drawImage(engine, image, x, y, largeur, hauteur, root, Mover.DONT_MOVE)` | Ajoute une image. Avec `Mover.MOVE`, l'image se décale légèrement selon la position de la souris (effet de parallaxe) |
| `loadImage(engine, "logo.png")` | Charge une image des ressources |
| `drawRect(root, x, y, largeur, hauteur, couleur)` | Ajoute un rectangle coloré, le renvoie |
| `openLink("https://...")` | Ouvre une page dans le navigateur |
| `fadeIn(noeud, ms)` / `fadeOut(noeud, ms)` | Fondu d'apparition / de disparition |

Ajoutez les éléments de fond **en premier** : JavaFX dessine les enfants dans leur ordre
d'ajout.

```java
public LauncherPanel(Pane root, GameEngine engine) {
    this.drawBackgroundImage(engine, root, "background.png");
    this.drawImage(engine, loadImage(engine, "logo.png"), 40, 30, 300, 100, root, Mover.DONT_MOVE);
    // ... puis les champs et boutons
}
```

> `loadImage(String)` et `playSound(String)` (sans le paramètre `engine`) ne trouvent
> pas les ressources dans la version actuelle : utilisez `loadImage(engine, nom)`, et
> pour un son, un `javafx.scene.media.MediaPlayer` (voir ci-dessous).

## Ressources (images, sons, polices)

Les méthodes qui prennent un **nom de fichier** le cherchent dans le classpath, sous
`/resources/`. Dans un projet Maven, placez donc vos fichiers dans
`src/main/resources/resources/` :

```
src/main/resources/resources/
├── background.png
├── logo.png
├── icon.png
└── police.ttf
```

Un fichier introuvable provoque une exception au chargement : vérifiez le nom exact
(sensible à la casse dans le jar).

Pour jouer un son :

```java
URL url = getClass().getResource("/resources/clic.mp3");
new MediaPlayer(new Media(url.toExternalForm())).play();
```

Pour une police : `FontLoader.loadFont("police.ttf", "Nom De La Police", 16f)` charge le
fichier et renvoie la `Font`, utilisable avec `label.setFont(...)` ou, une fois
chargée, en CSS (`-fx-font-family: "Nom De La Police";`).

## Composants

Tous les composants ci-dessous **s'ajoutent eux-mêmes** au `Pane` passé au constructeur
et se positionnent en coordonnées absolues :

- `setBounds(x, y, largeur, hauteur)`, ou `setPosition(x, y)` + `setSize(largeur, hauteur)` ;
- `addStyle("...")` ajoute une déclaration CSS au style en ligne existant (alors que
  `setStyle` le remplace). Les deux chaînes sont collées telles quelles : terminez
  toujours vos déclarations par `;` ;
- la plupart ont `setHover(...)` / `setUnHover(...)` (survol) et `setAction(...)`
  (clic de souris).

| Composant | Hérite de | Constructeurs | Particularités |
|---|---|---|---|
| `LauncherButton` | `Button` | `(root)`, `(texte, root)` | Opacité 80 % au survol. `setInvisible()` : fond transparent |
| `LauncherLabel` | `Label` | `(root)` | `setInvisible()` : fond transparent |
| `LauncherTextField` | `TextField` | `(root)`, `(texte, root)`, `(l, h, x, y, root)`, `(texte, l, h, x, y, root)` | `setVoidText(...)` : texte indicatif quand le champ est vide |
| `LauncherPasswordField` | `PasswordField` | `(root)`, `(l, h, x, y, root)`, `(texte, l, h, x, y, root)` | `setVoidText(...)` |
| `LauncherProgressBar` | `ProgressBar` | `()`, `(root)` | `setProgress(0..1)` ; `setCurrentFile`/`getCurrentFile` pour mémoriser un texte |
| `LauncherImage` | `ImageView` | `(root)`, `(root, image)` | `setSize`, `setBounds` |
| `LauncherRectangle` | `Rectangle` | `(root, x, y, l, h)`, `(l, h)` | `setColor(Color)` |
| `LauncherConsole` | `TextArea` | `(root)` | Zone de texte, par exemple pour afficher un journal |
| `SwitchButton` | `StackPane` | `()` | Interrupteur visuel. N'expose pas son état : préférez une `CheckBox` si vous devez le lire |

`setAction(...)` ne réagit qu'à la souris. Pour un bouton, `setOnAction(...)` (hérité de
`Button`) réagit aussi au clavier.

Exemple :

```java
LauncherTextField pseudo = new LauncherTextField(root);
pseudo.setVoidText("Pseudo");
pseudo.setBounds(60, 165, 300, 36);
pseudo.addStyle("-fx-background-radius: 8;");

LauncherButton jouer = new LauncherButton("Jouer", root);
jouer.setBounds(60, 220, 300, 44);
jouer.setStyle("-fx-background-color: #3fa34d; -fx-text-fill: white; -fx-font-size: 16px;");
jouer.setOnAction(e -> lancer());
```

### Autres classes

| Classe | Rôle |
|---|---|
| `LauncherAlert(message, AlertType)` | Boîte de dialogue modale (titre « Error/Erreur » quel que soit le type) |
| `LauncherAlert(message, details)` | Boîte d'erreur avec une zone de texte dépliable (pile d'exception…) |
| `LauncherBackground(engine, "fond.png" ou "fond.mp4", root)` | Fond image ou vidéo (`.mp4`, `.avi`, `.mov`) |
| `LauncherLogo(image, l, h, root)` | Logo avec effet de parallaxe. Le logo est **statique** : une seule instance à la fois |
| `FontLoader` | Chargement de polices depuis `/resources/` |

Les textes des boîtes `LauncherAlert` (titres, en-têtes) sont en français et ne sont
pas personnalisables : pour une interface dans une autre langue, utilisez directement
`javafx.scene.control.Alert`.

## Bonnes pratiques

- **Ne bloquez jamais le thread JavaFX** : les appels réseau (manifest, authentification
  Microsoft, vos propres API) se font dans un autre thread, et le retour à l'interface
  passe par `Platform.runLater(...)`.
- Désactivez les boutons pendant une opération (connexion, téléchargement) pour éviter
  les doubles clics.
- Le `GameUpdater` met à jour ses compteurs depuis son propre thread : lisez-les depuis
  une `Timeline` (thread JavaFX), comme dans le [tutoriel](02-tutoriel-launcher-basique.md#étape-6--afficher-la-progression).
