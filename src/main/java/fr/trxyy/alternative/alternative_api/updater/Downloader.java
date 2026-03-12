package fr.trxyy.alternative.alternative_api.updater;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import fr.trxyy.alternative.alternative_api.GameEngine;
import fr.trxyy.alternative.alternative_api.GameVerifier;
import fr.trxyy.alternative.alternative_api.utils.Logger;
import fr.trxyy.alternative.alternative_api.utils.file.FileUtil;

public class Downloader extends Thread {
    private static final int MAX_ATTEMPTS = 3;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final String url;
    private final String sha1;
    private final File file;
    private final GameEngine engine;

    public Downloader(File file, String url, String sha1, GameEngine engine_) {
        this.file = file;
        if (url == null || url.trim().isEmpty()) {
            System.err.println("The url is empty");
            this.url = "";
        } else {
            this.url = url;
        }
        this.sha1 = sha1;

        if (engine_ == null) {
            throw new IllegalArgumentException("Engine cannot be null");
        }
        this.engine = engine_;

        GameVerifier.addToFileList(file.getAbsolutePath()
                .replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "")
                .replace("\\", "/"));

        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
    }

    @Override
    public void run() {
        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                downloadOnce();
                return;
            } catch (IOException e) {
                lastException = e;
                Logger.err("Download failed (" + attempt + "/" + MAX_ATTEMPTS + ") for '" + this.file.getName()
                        + "' from " + this.url + " : " + e.getMessage());

                if (this.file.exists() && !this.file.delete()) {
                    Logger.err("Unable to delete corrupted file: " + this.file.getAbsolutePath());
                }

                File partFile = new File(this.file.getAbsolutePath() + ".part");
                if (partFile.exists() && !partFile.delete()) {
                    Logger.err("Unable to delete temporary file: " + partFile.getAbsolutePath());
                }

                try {
                    Thread.sleep(500L * attempt);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (lastException != null) {
            this.engine.getGameUpdater().registerDownloadFailure(this.file, this.url, lastException);
        }
    }

    private void downloadOnce() throws IOException {
        Logger.log("Acquiring file '" + this.file.getName() + "'");
        engine.getGameUpdater().setCurrentFile(this.file.getName());

        if (this.file.getAbsolutePath().contains("assets")) {
            engine.getGameUpdater().setCurrentInfoText("Telechargement d'une ressource.");
        } else if (this.file.getAbsolutePath().contains("jre-legacy")
                || this.file.getAbsolutePath().contains("java-runtime-alpha")) {
            engine.getGameUpdater().setCurrentInfoText("Telechargement de java.");
        } else {
            engine.getGameUpdater().setCurrentInfoText("Telechargement d'une librairie.");
        }

        if (this.file.getParentFile() != null) {
            this.file.getParentFile().mkdirs();
        }

        File partFile = new File(this.file.getAbsolutePath() + ".part");
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(this.url.replace(" ", "%20")).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "MajestyLauncher");

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode);
            }

            try (BufferedInputStream bufferedInputStream = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream fileOutputStream = new FileOutputStream(partFile)) {

                byte[] data = new byte[8192];
                int read;

                while ((read = bufferedInputStream.read(data)) != -1) {
                    fileOutputStream.write(data, 0, read);
                }
            }

            if (this.sha1 != null && !this.sha1.trim().isEmpty() && !FileUtil.matchSHA1(partFile, this.sha1)) {
                throw new IOException("SHA1 mismatch");
            }

            if (this.file.exists() && !this.file.delete()) {
                throw new IOException("Unable to delete existing target file");
            }

            if (!partFile.renameTo(this.file)) {
                throw new IOException("Unable to move temporary file to final destination");
            }

            engine.getGameUpdater().downloadedFiles++;
        } catch (MalformedURLException e) {
            Logger.err(e.getLocalizedMessage() + " " + this.url);
            throw e;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public boolean requireUpdate() {
        if (!this.file.exists()) {
            return true;
        }
        if (this.sha1 == null || this.sha1.trim().isEmpty()) {
            return false;
        }
        return !FileUtil.matchSHA1(this.file, this.sha1);
    }
}
