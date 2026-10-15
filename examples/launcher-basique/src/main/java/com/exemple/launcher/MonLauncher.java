package com.exemple.launcher;

import fr.trxyy.alternative.alternative_api.GameEngine;
import fr.trxyy.alternative.alternative_api.GameFolder;
import fr.trxyy.alternative.alternative_api.GameLinks;
import fr.trxyy.alternative.alternative_api.GameStyle;
import fr.trxyy.alternative.alternative_api.LauncherPreferences;
import fr.trxyy.alternative.alternative_api.utils.Mover;
import fr.trxyy.alternative.alternative_api_ui.LauncherAlert;
import fr.trxyy.alternative.alternative_api_ui.LauncherPane;
import fr.trxyy.alternative.alternative_api_ui.base.AlternativeBase;
import fr.trxyy.alternative.alternative_api_ui.base.LauncherBase;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Point d'entrée du launcher : prépare le moteur AlternativeAPI puis ouvre la fenêtre.
 */
public class MonLauncher extends AlternativeBase {

    /** La version de Minecraft que ce launcher installe et lance. */
    public static final String MC_VERSION = "1.21.1";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // 1. Dossier d'installation : %APPDATA%\.monlauncher sous Windows,
        //    ~/.monlauncher sous Linux, ~/Library/Application Support/monlauncher sous macOS.
        GameFolder gameFolder = new GameFolder("monlauncher");

        // 2. Titre et taille de la fenêtre du launcher. DONT_MOVE : la fenêtre garde sa
        //    barre de titre système, inutile de la rendre déplaçable à la souris.
        LauncherPreferences preferences = new LauncherPreferences(
                "Mon Launcher - Minecraft " + MC_VERSION, 880, 520, Mover.DONT_MOVE);

        // 3. Où trouver le JSON de la version. Avec une seule URL, la lib utilise
        //    directement le JSON officiel de Mojang (pas de serveur à héberger).
        GameLinks gameLinks;
        try {
            gameLinks = new GameLinks(MojangVersions.resolveVersionJsonUrl(MC_VERSION));
        } catch (Exception e) {
            new LauncherAlert("Impossible de joindre les serveurs de Mojang :\n" + e.getMessage(), AlertType.ERROR);
            Platform.exit();
            return;
        }

        // 4. Le moteur relie le tout. VANILLA_1_19_HIGHER : Minecraft vanilla 1.19 et plus.
        GameEngine gameEngine = new GameEngine(gameFolder, gameLinks, preferences, GameStyle.VANILLA_1_19_HIGHER);
        gameEngine.setRequestedVersionId(MC_VERSION);

        // 5. L'interface : un panneau aux dimensions du launcher, rempli par LauncherPanel.
        LauncherPane root = new LauncherPane(gameEngine);
        new LauncherPanel(root, gameEngine);

        // 6. LauncherBase configure la fenêtre (titre, taille, fermeture) et l'affiche.
        new LauncherBase(primaryStage, new Scene(root), StageStyle.DECORATED, gameEngine);
    }
}
