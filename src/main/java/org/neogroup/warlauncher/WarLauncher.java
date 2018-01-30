package org.neogroup.warlauncher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Class that load a war file
 * @author Luis Manuel Amengual
 */
public class WarLauncher {

    public static final String START_CLASS_ATTRIBUTE_NAME = "Start-Class";
    public static final String WEB_ROOT_ATTRIBUTE_NAME = "Web-Root-Attribute";
    public static final String WAR_FILE_ATTRIBUTE_NAME = "War-File-Attribute";
    public static final String DEFAULT_WEB_ROOT_PARAMETER_NAME = "web.dir";
    public static final String DEFAULT_WAR_FILE_PARAMETER_NAME = "web.filename";

    public static void main(String[] args) throws Exception {

        try {

            URL url = WarLauncher.class.getProtectionDomain().getCodeSource().getLocation();
            String filename = url.getFile();
            String warFilename = filename.substring(filename.lastIndexOf(File.separator) + 1);
            String warName = warFilename.substring(0, warFilename.indexOf("."));
            Path warFolderPath = Files.createTempDirectory(warName);

            System.out.println ();
            System.out.println (MessageFormat.format("Extracting \"{0}\" to \"{1}\" ...", warFilename, warFolderPath));
            System.out.println ("================================================");
            System.out.println ();

            JarFile warFile = new JarFile(url.getFile());
            Enumeration enumEntries = warFile.entries();
            while (enumEntries.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumEntries.nextElement();
                String jarEntryName = jarEntry.getName();
                if (!jarEntryName.endsWith("WarLauncher.class") && !jarEntryName.endsWith("WarLauncher$1.class")) {
                    System.out.println(MessageFormat.format("Extracting \"{0}\" ...", jarEntryName));

                    File file = new File(warFolderPath.toString(), jarEntryName);
                    if (!file.exists()) {
                        file.getParentFile().mkdirs();
                        file = new File(warFolderPath.toString(), jarEntryName);
                    }
                    if (jarEntry.isDirectory()) {
                        continue;
                    }
                    try (InputStream inputStream = warFile.getInputStream(jarEntry); FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                        byte[] buffer = new byte[2048];
                        int readSize = inputStream.read(buffer);
                        while (readSize >= 0) {
                            fileOutputStream.write(buffer, 0, readSize);
                            fileOutputStream.flush();
                            readSize = inputStream.read(buffer);
                        }
                    }
                }
            }
            warFile.close();

            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    System.out.println ();
                    System.out.println (MessageFormat.format("Deleting war path \"{0}\" ...", warFolderPath));
                    System.out.println ("================================================");
                    try {
                        deleteDir(warFolderPath.toFile());
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            System.out.println ();
            System.out.println (MessageFormat.format("Executing war path \"{0}\" ...",warFolderPath));
            System.out.println ("================================================");
            System.out.println ();

            Path classesPath = warFolderPath.resolve("WEB-INF").resolve("classes");
            Path libsPath = warFolderPath.resolve("WEB-INF").resolve("lib");

            List<URL> classPathUrls = new ArrayList<>();
            classPathUrls.add(classesPath.toUri().toURL());
            File[] libs = libsPath.toFile().listFiles();
            for (File lib : libs) {
                classPathUrls.add(lib.toURI().toURL());
            }

            URLClassLoader urlClassLoader = new URLClassLoader (classPathUrls.toArray(new URL[0]), WarLauncher.class.getClassLoader());

            String startClassName = null;
            String webRootParameterName = null;
            String warFileParameterName = null;
            URL manifestResource = WarLauncher.class.getClassLoader().getResource("META-INF/MANIFEST.MF");
            try (InputStream inputStream = manifestResource.openStream()) {
                Manifest manifest = new Manifest(inputStream);
                Attributes attributes = manifest.getMainAttributes();
                startClassName = attributes.getValue (START_CLASS_ATTRIBUTE_NAME);

                if (startClassName == null) {
                    throw new Exception (MessageFormat.format("Manifest attribute \"{0}\" not found !!", START_CLASS_ATTRIBUTE_NAME));
                }

                webRootParameterName = manifest.getMainAttributes().getValue(WEB_ROOT_ATTRIBUTE_NAME);
                if (webRootParameterName == null) {
                    webRootParameterName = DEFAULT_WEB_ROOT_PARAMETER_NAME;
                }

                warFileParameterName = manifest.getMainAttributes().getValue(WAR_FILE_ATTRIBUTE_NAME);
                if (warFileParameterName == null) {
                    warFileParameterName = DEFAULT_WAR_FILE_PARAMETER_NAME;
                }
            }

            System.setProperty(webRootParameterName, warFolderPath.toString());
            System.setProperty(warFileParameterName, warFilename);

            Class mainClass = Class.forName(startClassName, true, urlClassLoader);
            Method method = mainClass.getDeclaredMethod("main", String[].class);
            method.invoke(null, new Object[]{new String[]{MessageFormat.format("--warFile={0}", warFilename), MessageFormat.format("--webRoot={0}", warFolderPath)}});

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Deletes a directory and all of the files inside it
     * @param file File to delete
     */
    private static void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }
}
