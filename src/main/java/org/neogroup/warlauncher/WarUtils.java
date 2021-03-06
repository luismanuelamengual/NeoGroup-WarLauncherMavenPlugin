package org.neogroup.warlauncher;

import java.io.*;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Utilities for war files
 * @author Luis Manuel Amengual
 */
public abstract class WarUtils {

    /**
     * Unpacks a war file
     * @param warFile War file to unpack
     * @param warFolder Destination folder for the war file contents
     */
    public static void unpackWarFile(File warFile, Path warFolder) {

        try {
            JarFile warJarFile = new JarFile(warFile);
            Enumeration enumEntries = warJarFile.entries();
            while (enumEntries.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumEntries.nextElement();
                File file = new File(warFolder.toString(), jarEntry.getName());
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    file = new File(warFolder.toString(), jarEntry.getName());
                }
                if (jarEntry.isDirectory()) {
                    continue;
                }
                try (InputStream inputStream = warJarFile.getInputStream(jarEntry); FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                    byte[] buffer = new byte[2048];
                    int readSize = inputStream.read(buffer);
                    while (readSize >= 0) {
                        fileOutputStream.write(buffer, 0, readSize);
                        fileOutputStream.flush();
                        readSize = inputStream.read(buffer);
                    }
                }
            }
            warJarFile.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Packs a folder into a war file
     * @param warFile Destination War file
     * @param warFolder Folder to pack
     */
    public static void packWarFile(File warFile, Path warFolder) {
        packWarFile(warFile, warFolder, null);
    }

    /**
     * Packs a folder into a war file
     * @param warFile Destination War file
     * @param warFolder Folder to pack
     * @param manifest Manifest that will be used in the war file
     */
    public static void packWarFile(File warFile, Path warFolder, Manifest manifest) {

        try {
            JarOutputStream target = null;
            FileOutputStream warFileOutputStream = new FileOutputStream(warFile.toString());
            if (manifest != null) {
                target = new JarOutputStream(warFileOutputStream, manifest);
            }
            else {
                target = new JarOutputStream(warFileOutputStream);
            }
            for (File file : warFolder.toFile().listFiles()) {
                addJarEntry(warFolder, file, target, manifest != null);
            }
            target.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Adds a new jar entry to the war
     * @param warFolder Base war folder
     * @param source source file to copy
     * @param target target outputstream of the jar
     * @param excludeManifest exclude or not a manifest file
     * @throws IOException
     */
    private static void addJarEntry(Path warFolder, File source, JarOutputStream target, boolean excludeManifest) throws IOException {

        BufferedInputStream in = null;
        try {
            if (source.isDirectory()) {
                for (File nestedFile : source.listFiles()) {
                    addJarEntry(warFolder, nestedFile, target, excludeManifest);
                }
            } else {
                String fileName = warFolder.relativize(source.toPath()).toString();
                if (!excludeManifest || !fileName.equals(JarFile.MANIFEST_NAME)) {
                    JarEntry entry = new JarEntry(fileName);
                    entry.setTime(source.lastModified());
                    target.putNextEntry(entry);
                    in = new BufferedInputStream(new FileInputStream(source));
                    byte[] buffer = new byte[1024];
                    while (true) {
                        int count = in.read(buffer);
                        if (count == -1) {
                            break;
                        }
                        target.write(buffer, 0, count);
                    }
                    target.closeEntry();
                }
            }
        }
        finally {
            if (in != null)
                in.close();
        }
    }
}
