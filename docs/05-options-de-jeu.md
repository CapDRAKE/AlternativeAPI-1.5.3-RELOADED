# 5. Options de jeu

Toutes ces options s'enregistrent dans le `GameEngine` avec `engine.reg(...)`,
**avant** `updater.start()`. Elles sont relues à chaque lancement : vous pouvez les
modifier entre deux parties (par exemple depuis un écran de paramètres).

## Mémoire

```java
engine.reg(GameMemory.RAM_4G);
```

| Valeur | `-Xmx` |
|---|---|
| `DEFAULT` | 1G |
| `RAM_2G` … `RAM_10G` | 2G … 10G |

Pour une valeur venant d'un curseur ou d'un fichier de configuration :

```java
engine.reg(GameMemory.getMemory(4.0));   // 1 à 10 -> la constante correspondante
                                         // toute autre valeur (0, 2.5, 12...) -> DEFAULT (1G)
```

À savoir :

- **Sans `reg(GameMemory)`, le jeu démarre avec 1 Go**, trop peu pour les versions
  récentes : enregistrez au moins `RAM_2G`, idéalement `RAM_4G` pour un modpack.
- La lib ajoute toujours son `-Xmx` (et `-Xmn128M`) **après** vos
  [arguments JVM](#arguments-jvm) : un `-Xmx` passé dans `JVMArguments` est donc
  ignoré. Les tailles possibles sont celles de l'énumération (1 à 10 Go).
- Si le **launcher** tourne sur un Java 32 bits, la lib force `-Xmx512M`.

## Taille de la fenêtre

```java
engine.reg(GameSize.SIZE_1920x1080);
```

| Valeur | Taille |
|---|---|
| `DEFAULT` | 854 × 480 |
| `SIZE_1024x768` | 1024 × 768 |
| `SIZE_1280x1024` | 1280 × 1024 |
| `SIZE_1366x768` | 1366 × 768 |
| `SIZE_1600x900` | 1600 × 900 |
| `SIZE_1920x1080` | 1920 × 1080 |
| `SIZE_2560x1440` | 2560 × 1440 |

Conversions utiles pour une liste déroulante :

```java
GameSize size = GameSize.getWindowSize(3);          // index 0..6 -> SIZE_1366x768
int index = GameSize.getWindowSize("1920x1080");    // texte -> index (5)
String texte = GameSize.SIZE_1600x900.getDesc();    // "1600x900"
```

La taille est transmise au jeu (`--width` / `--height`). Pour démarrer en plein écran,
ajoutez l'argument de jeu `--fullscreen` (voir [Arguments de jeu](#arguments-de-jeu)).

## Connexion directe à un serveur

```java
engine.reg(new GameConnect("play.monserveur.fr", "25565"));
```

Le joueur arrive directement sur le serveur au lancement. Selon la version, la lib
utilise `--quickPlayMultiplayer` (versions récentes) ou `--server` / `--port` (anciennes
versions). Le port peut aussi être inclus dans l'adresse avec un port vide :
`new GameConnect("play.monserveur.fr:25566", "")`.

> Pour désactiver la connexion directe, il n'existe pas de `reg(null)` explicite :
> recréez le moteur, ou évitez d'enregistrer `GameConnect` quand l'option est
> décochée. Notez que `reg(GameConnect)` remet aussi à zéro les métadonnées Forge (sans
> conséquence : elles sont rechargées à chaque lancement).

## Arguments JVM

```java
engine.reg(new JVMArguments(new String[] { "-XX:+UseG1GC", "-Dfile.encoding=UTF-8" }));
```

Ajoutés juste après l'exécutable Java, avant les arguments de la version. À partir
d'une chaîne saisie par le joueur :

```java
String saisie = "-XX:+UseG1GC -XX:MaxGCPauseMillis=50";
engine.reg(new JVMArguments(saisie.trim().split("\\s+")));
```

La lib ajoute déjà un réglage du ramasse-miettes adapté à la version : n'en ajoutez un
que si vous savez pourquoi.

## Arguments de jeu

```java
engine.reg(new GameArguments(new String[] { "--fullscreen" }));
```

Ajoutés après les arguments de la version, avant `--width` / `--height`.

## Récapitulatif

```java
engine.reg(GameMemory.RAM_4G);
engine.reg(GameSize.SIZE_1280x1024);
engine.reg(new GameConnect("play.monserveur.fr", "25565"));
engine.reg(new JVMArguments(new String[] { "-Dfile.encoding=UTF-8" }));
engine.reg(new GameArguments(new String[] { "--fullscreen" }));
```

Pour sauvegarder les choix du joueur entre deux sessions, utilisez votre propre fichier
de configuration (`java.util.Properties`, JSON avec Gson…), **en dehors de `bin/`**
(voir [le nettoyage](04-mise-a-jour-et-lancement.md#le-nettoyage-du-dossier-bin)).
