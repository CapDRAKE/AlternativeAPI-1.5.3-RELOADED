package com.exemple.launcher;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import fr.trxyy.alternative.alternative_api.GameEngine;
import fr.trxyy.alternative.alternative_api.GameMemory;
import fr.trxyy.alternative.alternative_api.GameSize;
import fr.trxyy.alternative.alternative_api.updater.GameUpdater;
import fr.trxyy.alternative.alternative_api_ui.LauncherAlert;
import fr.trxyy.alternative.alternative_api_ui.base.IScreen;
import fr.trxyy.alternative.alternative_api_ui.components.LauncherButton;
import fr.trxyy.alternative.alternative_api_ui.components.LauncherLabel;
import fr.trxyy.alternative.alternative_api_ui.components.LauncherProgressBar;
import fr.trxyy.alternative.alternative_api_ui.components.LauncherTextField;
import fr.trxyy.alternative.alternative_auth.account.AccountType;
import fr.trxyy.alternative.alternative_auth.account.Session;
import fr.trxyy.alternative.alternative_auth.base.GameAuth;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

/**
 * Contenu de la fenêtre : un champ pseudo, un bouton "Jouer" et une barre de progression.
 */
public class LauncherPanel extends IScreen {

    private final GameEngine gameEngine;
    private final LauncherTextField usernameField;
    private final LauncherButton playButton;
    private final LauncherProgressBar progressBar;
    private final LauncherLabel statusLabel;
    private Timeline progressTimeline;

    public LauncherPanel(Pane root, GameEngine gameEngine) {
        this.gameEngine = gameEngine;
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #1f2b38, #0f151c);");

        // Chaque composant AlternativeAPI s'ajoute lui-même au panneau passé en paramètre.
        LauncherLabel title = new LauncherLabel(root);
        title.setText("Minecraft " + MonLauncher.MC_VERSION);
        title.setStyle("-fx-text-fill: white; -fx-font-size: 30px; -fx-font-weight: bold;");
        title.setBounds(60, 60, 500, 44);

        LauncherLabel usernameLabel = new LauncherLabel(root);
        usernameLabel.setText("Pseudo");
        usernameLabel.setStyle("-fx-text-fill: #c9d4df; -fx-font-size: 14px;");
        usernameLabel.setBounds(60, 140, 300, 20);

        this.usernameField = new LauncherTextField(root);
        this.usernameField.setVoidText("Steve");
        this.usernameField.setBounds(60, 165, 300, 36);

        this.playButton = new LauncherButton("Jouer", root);
        this.playButton.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        this.playButton.setBounds(60, 220, 300, 44);
        // setOnAction (hérité de Button) réagit aussi au clavier, contrairement à setAction.
        this.playButton.setOnAction(event -> play());

        this.progressBar = new LauncherProgressBar(root);
        this.progressBar.setBounds(60, 400, 760, 18);
        this.progressBar.setVisible(false);

        this.statusLabel = new LauncherLabel(root);
        this.statusLabel.setStyle("-fx-text-fill: #c9d4df; -fx-font-size: 13px;");
        this.statusLabel.setBounds(60, 425, 760, 22);
    }

    /** Clic sur "Jouer" : authentifie le joueur, puis met à jour et lance le jeu. */
    private void play() {
        String username = this.usernameField.getText().trim();
        if (!username.matches("[A-Za-z0-9_]{3,16}")) {
            new LauncherAlert("Le pseudo doit faire de 3 à 16 caractères (lettres, chiffres ou _).", AlertType.WARNING);
            return;
        }

        // Compte hors ligne : aucun mot de passe, aucune vérification de licence.
        // Utilisable en solo et sur les serveurs en online-mode=false.
        GameAuth auth = new GameAuth(username, "", AccountType.OFFLINE);
        // La lib tire un UUID au hasard à chaque connexion hors ligne. Or le jeu range
        // les progrès et statistiques du joueur par UUID : on utilise donc l'UUID
        // hors ligne standard, toujours le même pour un pseudo donné.
        Session session = auth.getSession();
        session.setUuid(offlineUuid(username));

        // Options du jeu : mémoire allouée et taille de la fenêtre de Minecraft.
        this.gameEngine.reg(GameMemory.RAM_2G);
        this.gameEngine.reg(GameSize.DEFAULT);

        // Le GameUpdater est un Thread : il télécharge ce qui manque, puis lance le jeu.
        // Il ne peut être démarré qu'une fois : on en crée un nouveau à chaque clic.
        GameUpdater updater = new GameUpdater();
        updater.reg(this.gameEngine);
        updater.reg(session);
        this.gameEngine.reg(updater);

        this.playButton.setDisable(true);
        this.usernameField.setDisable(true);
        this.progressBar.setVisible(true);
        updater.start();

        // La lib n'émet pas d'événements de progression : on lit son état régulièrement.
        this.progressTimeline = new Timeline(new KeyFrame(Duration.millis(200), event -> refreshProgress(updater)));
        this.progressTimeline.setCycleCount(Animation.INDEFINITE);
        this.progressTimeline.play();
    }

    /** UUID hors ligne de Minecraft : celui qu'un serveur en online-mode=false attribue au pseudo. */
    private static String offlineUuid(String username) {
        byte[] seed = ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(seed).toString().replace("-", "");
    }

    private void refreshProgress(GameUpdater updater) {
        int total = updater.filesToDownload;
        int done = updater.downloadedFiles;
        this.progressBar.setProgress(total > 0 ? (double) done / total : ProgressBar.INDETERMINATE_PROGRESS);

        String info = updater.getCurrentInfo() == null ? "" : updater.getCurrentInfo();
        this.statusLabel.setText(total > 0 ? info + " (" + done + "/" + total + ")" : info);

        // Le thread se termine quand le jeu est fermé, ou plus tôt en cas d'échec.
        // Quand le jeu démarre, la lib masque la fenêtre du launcher : avec le
        // comportement JavaFX par défaut, le launcher se ferme avec le jeu.
        if (!updater.isAlive()) {
            this.progressTimeline.stop();
            if (updater.hasDownloadError()) {
                this.statusLabel.setText("Échec : " + updater.getFailedDownloads().size()
                        + " fichier(s) n'ont pas pu être téléchargés. Vérifiez votre connexion et réessayez.");
                this.playButton.setDisable(false);
                this.usernameField.setDisable(false);
            }
        }
    }
}
