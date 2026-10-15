# Exemple : launcher basique

Le projet complet du [tutoriel](../../docs/02-tutoriel-launcher-basique.md) : un launcher
Minecraft 1.21.1 vanilla avec un compte hors ligne, en trois classes.

| Fichier | Rôle |
|---|---|
| `MonLauncher.java` | Point d'entrée : dossier, liens, moteur, fenêtre |
| `LauncherPanel.java` | Champ pseudo, bouton Jouer, barre de progression |
| `MojangVersions.java` | Trouve l'URL du JSON officiel d'une version |

## Lancer l'exemple

Prérequis : un JDK 8 avec JavaFX, et AlternativeAPI installée dans le dépôt Maven local
(`mvn clean install` à la racine de ce dépôt, voir [Installation](../../docs/01-installation.md)).

```bash
cd examples/launcher-basique
mvn clean package
java -jar target/MonLauncher.jar
```

Le jeu est installé dans `%APPDATA%\.monlauncher` (≈ 1 Go au premier lancement).

Pour changer de version, modifiez `MC_VERSION` dans `MonLauncher.java` et choisissez le
`GameStyle` correspondant ([tableau](../../docs/03-concepts.md#gamestyle)).
