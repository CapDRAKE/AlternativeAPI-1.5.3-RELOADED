package fr.trxyy.alternative.alternative_api.utils.file;

import fr.trxyy.alternative.alternative_api.*;
import fr.trxyy.alternative.alternative_api.minecraft.json.DownloadInfo;
import fr.trxyy.alternative.alternative_api.minecraft.json.MinecraftLibrary;
import fr.trxyy.alternative.alternative_api.minecraft.json.MinecraftRules;
import fr.trxyy.alternative.alternative_api.minecraft.json.MinecraftVersion;
import fr.trxyy.alternative.alternative_api.minecraft.utils.Arch;
import fr.trxyy.alternative.alternative_api.utils.OperatingSystem;

import java.io.*;
import java.math.*;
import java.net.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/**
 * @author Trxyy
 */
public class FileUtil {

	/**
	 * Skip folders in extractions
	 */
	public static String skipFoldersInExtraction = "META-INF/";

	/**
	 * Delete fake natives
	 * @param targetDir The target directory to delete files
	 * @param engine The GameEngine instance
	 * @throws IOException
	 */
	public static void deleteFakeNatives(File targetDir, GameEngine engine) throws IOException {
	    int currentPlatform = getPlatform();
	    if (targetDir == null) {
	        return;
	    }
	    File[] listOfFiles = targetDir.listFiles();
	    if (listOfFiles == null) {
	        return;
	    }

	    move(targetDir, targetDir);

	    for (File file : listOfFiles) {
	        if (file == null || !file.exists()) {
	            continue;
	        }

	        if (file.isDirectory()) {
	            deleteFolder(file);
	            continue;
	        }

	        String fileName = file.getName().toLowerCase(Locale.ROOT);
	        boolean isNativeForCurrentPlatform;

	        if (currentPlatform == 1 || currentPlatform == 2) { // Linux/Unix
	            isNativeForCurrentPlatform = fileName.endsWith(".so");
	        } else if (currentPlatform == 3) { // Windows
	            isNativeForCurrentPlatform = fileName.endsWith(".dll");
	        } else if (currentPlatform == 4) { // Mac
	            isNativeForCurrentPlatform = fileName.endsWith(".dylib") || fileName.endsWith(".jnilib");
	        } else {
	            isNativeForCurrentPlatform = false;
	        }

	        if (!isNativeForCurrentPlatform) {
	            file.delete();
	        }
	    }
	}

    /**
     * Unpack natives in a designed folder
     * @param targetDir The target directory
     * @param engine The GameEngine instance
     * @throws IOException
     */
    public static void unpackNatives(File targetDir, GameEngine engine) throws IOException {
        int currentPlatform = getPlatform();

        if (targetDir.exists()) {
            File[] existingFiles = targetDir.listFiles();
            if (existingFiles != null) {
                for (File existing : existingFiles) {
                    if (existing.isDirectory()) {
                        deleteFolder(existing);
                    } else {
                        existing.delete();
                    }
                }
            }
        } else {
            targetDir.mkdirs();
        }

        Map<String, MinecraftRules> nativeArchiveRules = resolveCurrentNativeArchiveRules(engine);
        Set<String> allowedNativeArchives = nativeArchiveRules.keySet();
        List<File> nativeArchives = resolveAvailableNativeArchives(engine, allowedNativeArchives);
        if (nativeArchives.isEmpty()) {
            return;
        }

        for (File nativeArchive : nativeArchives) {
            MinecraftRules extractRules = nativeArchiveRules.get(nativeArchive.getName());
            ZipFile zip = new ZipFile(nativeArchive);
            try {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (skipFoldersInExtraction != null && entryName.startsWith(skipFoldersInExtraction)) {
                        continue;
                    }
                    if (extractRules != null && !extractRules.shouldExtract(entryName)) {
                        continue;
                    }

                    if (currentPlatform == 1 || currentPlatform == 2) { // Linux/Unix
                        if (!entryName.endsWith(".so")) {
                            continue;
                        }
                    } else if (currentPlatform == 3) { // Windows
                        if (!entryName.endsWith(".dll")) {
                            continue;
                        }
                    } else if (currentPlatform == 4) { // Mac
                        if (!entryName.endsWith(".dylib") && !entryName.endsWith(".jnilib")) {
                            continue;
                        }
                    } else {
                        continue;
                    }

                    File targetFile = new File(targetDir, entryName);
                    File parent = targetFile.getParentFile();
                    if (parent != null) {
                        parent.mkdirs();
                    }

                    if (!entry.isDirectory()) {
                        BufferedInputStream inputStream = new BufferedInputStream(zip.getInputStream(entry));
                        FileOutputStream outputStream = new FileOutputStream(targetFile);
                        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
                        try {
                            byte[] buffer = new byte[2048];
                            int length;
                            while ((length = inputStream.read(buffer, 0, buffer.length)) != -1) {
                                bufferedOutputStream.write(buffer, 0, length);
                            }
                        } finally {
                            closeSilently(bufferedOutputStream);
                            closeSilently(outputStream);
                            closeSilently(inputStream);
                        }
                    }
                }
            } finally {
                zip.close();
            }
        }
    }

    private static List<File> resolveAvailableNativeArchives(GameEngine engine, Set<String> allowedNativeArchives) {
        LinkedHashMap<String, File> archivesByName = new LinkedHashMap<String, File>();
        collectNativeArchivesFromDirectory(engine == null ? null : engine.getGameFolder().getNativesCacheDir(), allowedNativeArchives, archivesByName, false);
        collectNativeArchivesFromDirectory(engine == null ? null : engine.getGameFolder().getLibsDir(), allowedNativeArchives, archivesByName, true);
        collectNativeArchivesFromRuntimeClasspath(engine, archivesByName);
        return new ArrayList<File>(archivesByName.values());
    }

    private static void collectNativeArchivesFromDirectory(File directory, Set<String> allowedNativeArchives,
            Map<String, File> archivesByName, boolean recursive) {
        if (allowedNativeArchives == null || allowedNativeArchives.isEmpty()) {
            return;
        }
        if (directory == null || !directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file == null || !file.exists()) {
                continue;
            }
            if (file.isDirectory()) {
                if (recursive) {
                    collectNativeArchivesFromDirectory(file, allowedNativeArchives, archivesByName, true);
                }
                continue;
            }
            if (allowedNativeArchives.contains(file.getName()) && !archivesByName.containsKey(file.getName())) {
                archivesByName.put(file.getName(), file);
            }
        }
    }

    private static void collectNativeArchivesFromRuntimeClasspath(GameEngine engine, Map<String, File> archivesByName) {
        if (engine == null || engine.getGameUpdater() == null) {
            return;
        }

        Collection<String> runtimeJars = engine.getGameUpdater().getJars();
        if (runtimeJars == null || runtimeJars.isEmpty()) {
            return;
        }

        List<String> suffixCandidates = resolveNativeArchiveSuffixCandidates();
        if (suffixCandidates.isEmpty()) {
            return;
        }

        for (String runtimeJarPath : runtimeJars) {
            if (runtimeJarPath == null || runtimeJarPath.trim().isEmpty()) {
                continue;
            }

            File runtimeJar = new File(runtimeJarPath);
            if (!runtimeJar.exists() || runtimeJar.isDirectory()) {
                continue;
            }

            String runtimeJarName = runtimeJar.getName();
            if (!runtimeJarName.endsWith(".jar")) {
                continue;
            }

            String baseName = runtimeJarName.substring(0, runtimeJarName.length() - 4);
            File parentDir = runtimeJar.getParentFile();
            if (parentDir == null || !parentDir.exists()) {
                continue;
            }

            for (String suffix : suffixCandidates) {
                File nativeArchive = new File(parentDir, baseName + suffix + ".jar");
                if (nativeArchive.exists() && !archivesByName.containsKey(nativeArchive.getName())) {
                    archivesByName.put(nativeArchive.getName(), nativeArchive);
                }
            }
        }
    }

    private static List<String> resolveNativeArchiveSuffixCandidates() {
        ArrayList<String> candidates = new ArrayList<String>();
        OperatingSystem currentOs = OperatingSystem.getCurrent();
        if (currentOs == OperatingSystem.WINDOWS) {
            if (isArm64Architecture()) {
                candidates.add("-natives-windows-arm64");
            }
            if (Arch.CURRENT == Arch.x86) {
                candidates.add("-natives-windows-x86");
            }
            candidates.add("-natives-windows");
        } else if (currentOs == OperatingSystem.LINUX) {
            if (isArm64Architecture()) {
                candidates.add("-natives-linux-arm64");
                candidates.add("-natives-linux-aarch64");
            }
            candidates.add("-natives-linux");
        } else if (currentOs == OperatingSystem.OSX) {
            if (isArm64Architecture()) {
                candidates.add("-natives-macos-arm64");
                candidates.add("-natives-osx-arm64");
                candidates.add("-natives-macos-aarch64");
                candidates.add("-natives-osx-aarch64");
            }
            candidates.add("-natives-macos");
            candidates.add("-natives-osx");
        }
        return candidates;
    }

    private static boolean isArm64Architecture() {
        return OperatingSystem.isArm64Architecture();
    }
	
	
	private static void move(File toDir, File currDir) {
	    for (File file : currDir.listFiles()) {
	        if (file.isDirectory()) {
	            move(toDir, file);
	        } else {
	            if (shouldBeMoved(file.getName())) {
	                file.renameTo(new File(toDir, file.getName()));
	            }
	        }
	    }
	}
	
	private static boolean shouldBeMoved(String fileName) {
	    int currentPlatform = getPlatform();
	    boolean shouldMove = false;

	    if (currentPlatform == 1 || currentPlatform == 2) { // Linux/Unix
	        shouldMove = fileName.endsWith(".so");
	    } else if (currentPlatform == 3) { // Windows
	        shouldMove = fileName.endsWith(".dll");
	    } else if (currentPlatform == 4) { // Mac
	        shouldMove = fileName.endsWith(".dylib") || fileName.endsWith(".jnilib");
	    }
	    return shouldMove;
	}

	/**
	 * Force delete on exit
	 * @param file The file in question
	 * @throws IOException
	 */
	public static void forceDeleteOnExit(File file) throws IOException {
		if (file.isDirectory()) {
			deleteDirectoryOnExit(file);
		} else {
			file.deleteOnExit();
		}
	}

	/**
	 * Delete a directory on exit
	 * @param directory The directory in question
	 * @throws IOException
	 */
	private static void deleteDirectoryOnExit(File directory) throws IOException {
		if (!directory.exists()) {
			return;
		}
		directory.deleteOnExit();
		if (!isSymlink(directory)) {
			cleanDirectoryOnExit(directory);
		}
	}

	/**
	 * Checks if a file is a symbolic link.
	 *
	 * @param file The file to check.
	 * @return true if the file is a symbolic link, false otherwise.
	 * @throws IOException if an I/O error occurs.
	 */
	public static boolean isSymlink(File file) throws IOException {
	    if (file == null) {
	        throw new NullPointerException("File must not be null");
	    }
	    File fileInCanonicalDir = null;
	    if (file.getParent() == null) {
	        fileInCanonicalDir = file;
	    } else {
	        File canonicalDir = file.getParentFile().getCanonicalFile();
	        fileInCanonicalDir = new File(canonicalDir, file.getName());
	    }
	    if (fileInCanonicalDir.getCanonicalFile().equals(fileInCanonicalDir.getAbsoluteFile())) {
	        return false;
	    }
	    return true;
	}

	/**
	 * Clean a directory on exit
	 * @param directory The directory to clean
	 * @throws IOException
	 */
	private static void cleanDirectoryOnExit(File directory) throws IOException {
		if (!directory.exists()) {
			String message = directory + " does not exist";
			throw new IllegalArgumentException(message);
		}
		if (!directory.isDirectory()) {
			String message = directory + " is not a directory";
			throw new IllegalArgumentException(message);
		}
		File[] files = directory.listFiles();
		if (files == null) {
			throw new IOException("Failed to list contents of " + directory);
		}
		IOException exception = null;
		for (File file : files) {
			try {
				forceDeleteOnExit(file);
			} catch (IOException ioe) {
				exception = ioe;
			}
		}
		if (null != exception) {
			throw exception;
		}
	}

	/**
	 * @return The Charset
	 */
	public static Charset getCharset() {
	    return Charset.forName("UTF-8");
	}

	/**
	 * @param file The file
	 * @param algorithm The algorithm to use
	 * @param hashLength The hash lenght
	 * @return A new String
	 */
	public static String getDigest(File file, String algorithm, int hashLength) {
		DigestInputStream stream = null;
		try {
			stream = new DigestInputStream(new FileInputStream(file), MessageDigest.getInstance(algorithm));
			byte[] ignored = new byte[65536];
			int read;
			do {
				read = stream.read(ignored);
			} while (read > 0);
			return String.format("%1$0" + hashLength + "x",
					new Object[] { new BigInteger(1, stream.getMessageDigest().digest()) });
		} catch (Exception localException) {
		} finally {
			close(stream);
		}
		return null;
	}

	/**
	 * Close
	 * @param a The Closeable
	 */
	private static void close(Closeable a) {
		if (a == null) {
			return;
		}
		try {
			a.close();
		} catch (Exception var2) {
			var2.printStackTrace();
		}
	}

	public static String getSHA(File file) {
		return getDigest(file, "SHA", 40);
	}

	/**
	 * @param file The File
	 * @param sha1 The Sha1
	 * @return True if the file Md5 Match with the Sha1 String
	 */
	public static boolean matchSHA1(final File file, final String sha1) {
		try {
			String currentSha = getSHA(file);
			return currentSha != null && currentSha.equalsIgnoreCase(sha1);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Close silently 
	 * @param closeable The Closeable
	 */
	public static void closeSilently(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException localIOException) {
			}
		}
	}

	/**
	 * Copy a file from mc directory to our directory
	 * @param mc The mc folder
	 * @param local The directory in question
	 * @throws IOException
	 */
	public static void copy(File mc, File local) throws IOException {
		InputStream input = null;
		OutputStream output = null;
		try {
			input = new FileInputStream(mc);
			output = new FileOutputStream(local);
			byte[] buf = new byte[1024];
			int bytesRead;
			while ((bytesRead = input.read(buf)) > 0) {
				output.write(buf, 0, bytesRead);
			}
		} finally {
			input.close();
			output.close();
		}
	}

	/**
	 * Delete a folder
	 * @param folder The folder in question
	 */
	public static void deleteFolder(File folder) {
		File[] files = folder.listFiles();
		if (files != null) { // some JVMs return null for empty dirs
			for (File f : files) {
				if (f.isDirectory()) {
					deleteFolder(f);
				} else {
					f.delete();
				}
			}
		}
		folder.delete();
	}

	/**
	 * Delete something
	 * @param path The pathto the file to delete
	 */
	public static void deleteSomething(String path) {
		Path filePath_1 = Paths.get(path);
		try {
			Files.delete(filePath_1);
		} catch (NoSuchFileException x) {
			System.err.format("%s: no such" + " file or directory%n", path);
		} catch (DirectoryNotEmptyException x) {
			System.err.format("%s not empty%n", path);
		} catch (IOException x) {
			System.err.println(x);
		}
	}

	/**
	 * @param etag The etag
	 * @return The etag as a String
	 */
	public static String getEtag(String etag) {
		if (etag == null)
			etag = "-";
		else if (etag.startsWith("\"") && etag.endsWith("\""))
			etag = etag.substring(1, etag.length() - 1);

		return etag;
	}

	/**
	 * @param file The file to get the MD5
	 * @return The MD5 as a String
	 */
	public static String getMD5(final File file) {
		DigestInputStream stream = null;
		try {
			stream = new DigestInputStream(new FileInputStream(file), MessageDigest.getInstance("MD5"));
			final byte[] buffer = new byte[65536];

			int read = stream.read(buffer);
			while (read >= 1)
				read = stream.read(buffer);
		} catch (final Exception ignored) {
			return null;
		} finally {
			closeSilently(stream);
		}

		return String.format("%1$032x", new Object[] { new BigInteger(1, stream.getMessageDigest().digest()) });
	}
	
	/**
	 * @param url The URL to read
	 * @return The result of the url
	 */
	@SuppressWarnings("deprecation")
	public static String readMD5(String url) {
		String result = "";
		try {
			Scanner scan = new Scanner((new URL(url)).openStream(), "UTF-8");
			if (!scan.hasNextLine()) {
				scan.close();
			}
			result = scan.nextLine();
			scan.close();
			return result;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}
	
    /**
     * @return The current Platform
     */
    private static int getPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("linux"))
            return 1;
        if (osName.contains("unix"))
            return 1;
        if (osName.contains("solaris"))
            return 2;
        if (osName.contains("sunos"))
            return 2;
        if (osName.contains("win"))
            return 3;
        if (osName.contains("mac"))
            return 4;
        return 5;
    }
    
    private static Map<String, MinecraftRules> resolveCurrentNativeArchiveRules(GameEngine engine) {
        LinkedHashMap<String, MinecraftRules> archiveRules = new LinkedHashMap<String, MinecraftRules>();
        MinecraftVersion version = resolveCurrentMinecraftVersion(engine);
        if (version == null || version.getLibraries() == null) {
            return archiveRules;
        }

        for (MinecraftLibrary lib : version.getLibraries()) {
            if (lib == null || lib.isSkipped() || !lib.appliesToCurrentEnvironment() || !declaredNativeMatchesCurrentEnvironment(lib)) {
                continue;
            }

            if (isNativeCoordinateLibrary(lib)) {
                String nativeFileName = resolveDeclaredNativeFileName(lib);
                if (nativeFileName != null && !nativeFileName.trim().isEmpty()) {
                    registerNativeArchiveRule(archiveRules, nativeFileName, lib.getExtractRules());
                }
                continue;
            }

            String classifier = resolveNativeClassifier(lib);
            if (classifier != null) {
                String nativeFileName = lib.getArtifactNatives(classifier);
                if (nativeFileName != null && !nativeFileName.trim().isEmpty()) {
                    registerNativeArchiveRule(archiveRules, nativeFileName, lib.getExtractRules());
                }
            }
        }

        return archiveRules;
    }

    private static void registerNativeArchiveRule(Map<String, MinecraftRules> archiveRules, String archiveName, MinecraftRules rules) {
        if (archiveRules == null || archiveName == null || archiveName.trim().isEmpty()) {
            return;
        }
        if (!archiveRules.containsKey(archiveName)) {
            archiveRules.put(archiveName, rules);
        }
    }
    
    private static MinecraftVersion resolveCurrentMinecraftVersion(GameEngine engine) {
        if (engine == null) {
            return null;
        }
        if (engine.getMinecraftVersion() != null) {
            return engine.getMinecraftVersion();
        }
        if (engine.getGameUpdater() != null) {
            return engine.getGameUpdater().getLocalVersion();
        }
        return null;
    }
    
    private static boolean isNativeCoordinateLibrary(MinecraftLibrary lib) {
        return lib != null && lib.isDeclaredNativeLibrary();
    }

    private static boolean declaredNativeMatchesCurrentEnvironment(MinecraftLibrary lib) {
        return lib == null || lib.declaredNativeMatchesCurrentEnvironment();
    }

    private static String resolveDeclaredNativeFileName(MinecraftLibrary lib) {
        try {
            if (lib.getDownloads() != null && lib.getDownloads().getArtifact() != null
                    && lib.getDownloads().getArtifact().getPath() != null
                    && !lib.getDownloads().getArtifact().getPath().trim().isEmpty()) {
                return new File(lib.getDownloads().getArtifact().getPath()).getName();
            }
        } catch (Exception ignored) {
        }
        return lib == null ? null : lib.getArtifactFilename(null);
    }

    private static String resolveNativeClassifier(MinecraftLibrary lib) {
        if (lib == null || !lib.hasNatives() || lib.getDownloads() == null || lib.getDownloads().getClassifiers() == null) {
            return null;
        }

        Map<String, DownloadInfo> classifiers = lib.getDownloads().getClassifiers();
        if (classifiers.isEmpty()) {
            return null;
        }

        String classifier = null;
        if (lib.getNatives() != null) {
            classifier = lib.getNatives().get(OperatingSystem.getCurrent());
        }

        if (classifier != null) {
            classifier = classifier.replace("${arch}", Arch.CURRENT.getBit());
            if (classifiers.containsKey(classifier)) {
                return classifier;
            }
        }

        List<String> candidates = new ArrayList<String>();
        OperatingSystem currentOs = OperatingSystem.getCurrent();
        if (currentOs == OperatingSystem.WINDOWS) {
            if (Arch.CURRENT == Arch.x86) {
                candidates.add("natives-windows-x86");
                candidates.add("natives-windows");
            } else {
                candidates.add("natives-windows");
                candidates.add("natives-windows-x86");
            }
            if (OperatingSystem.isArm64Architecture()) {
                candidates.add("natives-windows-arm64");
            }
        } else if (currentOs == OperatingSystem.LINUX) {
            if (OperatingSystem.isArm64Architecture()) {
                candidates.add("natives-linux-arm64");
                candidates.add("natives-linux-aarch64");
            }
            candidates.add("natives-linux");
        } else if (currentOs == OperatingSystem.OSX) {
            if (OperatingSystem.isArm64Architecture()) {
                candidates.add("natives-macos-arm64");
                candidates.add("natives-osx-arm64");
                candidates.add("natives-macos-aarch64");
                candidates.add("natives-osx-aarch64");
            }
            candidates.add("natives-macos");
            candidates.add("natives-osx");
        }

        for (String candidate : candidates) {
            if (classifiers.containsKey(candidate)) {
                return candidate;
            }
        }

        for (String key : classifiers.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT);
            if (currentOs == OperatingSystem.WINDOWS && lower.contains("windows")) {
                return key;
            }
            if (currentOs == OperatingSystem.LINUX && lower.contains("linux")) {
                return key;
            }
            if (currentOs == OperatingSystem.OSX && (lower.contains("osx") || lower.contains("macos") || lower.contains("mac-os"))) {
                return key;
            }
        }

        return null;
    }

}
