package fr.trxyy.alternative.alternative_api.updater;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Duplicator extends Thread {
	private final File source;
	private final File dest;

	public Duplicator(File source, File dest) {
		if (source == null || dest == null) {
			throw new IllegalArgumentException("Source and destination files cannot be null");
		}
		this.source = source;
		this.dest = dest;
	}

	@Override
	public void run() {
		try {
			startCloning();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void startCloning() throws IOException {
		if (!source.exists()) {
			throw new IOException("Source file does not exist: " + source);
		}

		try (InputStream input = new FileInputStream(this.source);
			 OutputStream output = new FileOutputStream(this.dest)) {

			byte[] buf = new byte[1024];
			int bytesRead;
			while ((bytesRead = input.read(buf)) > 0) {
				output.write(buf, 0, bytesRead);
			}
		}
	}
}
