package fr.trxyy.alternative.alternative_api.updater;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;

import fr.trxyy.alternative.alternative_api.GameEngine;
import fr.trxyy.alternative.alternative_api.GameVerifier;
import fr.trxyy.alternative.alternative_api.utils.Logger;
import fr.trxyy.alternative.alternative_api.utils.file.FileUtil;

public class Downloader extends Thread {
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
		
		GameVerifier.addToFileList(file.getAbsolutePath().replace(engine.getGameFolder().getGameDir().getAbsolutePath(), "").replace("\\", "/"));
		file.getParentFile().mkdirs();
	}

	@Override
	public void run() {
		try {
			download();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void download() throws IOException {
		Logger.log("Acquiring file '" + this.file.getName() + "'");
		engine.getGameUpdater().setCurrentFile(this.file.getName());
		if (this.file.getAbsolutePath().contains("assets")) {
			engine.getGameUpdater().setCurrentInfoText("Telechargement d'une ressource.");
		}
		else if (this.file.getAbsolutePath().contains("jre-legacy") || this.file.getAbsolutePath().contains("java-runtime-alpha")) {
			engine.getGameUpdater().setCurrentInfoText("Telechargement de java.");
		}
		else {
			engine.getGameUpdater().setCurrentInfoText("Telechargement d'une librairie.");
		}

		try (BufferedInputStream bufferedInputStream = new BufferedInputStream(new URL(this.url.replace(" ", "%20")).openConnection().getInputStream());
			 FileOutputStream fileOutputStream = new FileOutputStream(this.file)) {

			byte[] data = new byte[1024];
			int read;

			while ((read = bufferedInputStream.read(data, 0, 1024)) != -1) {
				fileOutputStream.write(data, 0, read);
			}

			engine.getGameUpdater().downloadedFiles++;
		} catch (MalformedURLException e) {
			e.printStackTrace();
			Logger.err(e.getLocalizedMessage() + " " + this.url);
		}
	}

	public boolean requireUpdate() {
		return !((this.file.exists()) && (FileUtil.matchSHA1(this.file, this.sha1)));
	}
}
