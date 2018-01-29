package org.neogroup.warlauncher;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Mojo to repackage a war file and make it executable
 * @author Luis Manuel Amengual
 */
@Mojo(name = "repackage", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class WarLauncherMojo extends AbstractMojo {

    private static final String START_CLASS_ATTRIBUTE_NAME = "Start-Class";

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private File buildDirectory;

    @Parameter(property = "startClass", required = true)
    private String startClass;

    /**
     * Executes the mojo
     * @throws MojoExecutionException
     */
    public void execute() throws MojoExecutionException {

        try {
            getLog().info("Repackaging war to be executable ...");
            String warFileName = MessageFormat.format("{0}-{1}.war", project.getArtifactId(), project.getVersion());
            File warFile = buildDirectory.toPath().resolve(warFileName).toFile();
            Path tempWarFolderDestinationPath = buildDirectory.toPath().resolve("tmp");

            //Unpack the war file
            WarUtils.unpackWarFile(warFile, tempWarFolderDestinationPath);

            //Copy the WarLauncher classes to the war folder
            Path classDestinationPath = tempWarFolderDestinationPath.resolve("org").resolve("neogroup").resolve("warlauncher");
            classDestinationPath.toFile().mkdirs();
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("org/neogroup/warlauncher/WarLauncher.class"); FileOutputStream outputStream = new FileOutputStream(classDestinationPath.resolve("WarLauncher.class").toFile())) {
                inputStream.transferTo(outputStream);
            }
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("org/neogroup/warlauncher/WarLauncher$1.class"); FileOutputStream outputStream = new FileOutputStream(classDestinationPath.resolve("WarLauncher$1.class").toFile())) {
                inputStream.transferTo(outputStream);
            }

            //Create a customized manifest for the war file
            JarFile jarFile = new JarFile(warFile);
            Manifest manifest = jarFile.getManifest();
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, WarLauncher.class.getName());
            manifest.getMainAttributes().putValue(START_CLASS_ATTRIBUTE_NAME, startClass);

            //Delete the original war file
            FileUtils.forceDelete(warFile);

            //Pack a new war file
            WarUtils.packWarFile(warFile, tempWarFolderDestinationPath, manifest);

            //Delete the temp war folder
            FileUtils.deleteDirectory(tempWarFolderDestinationPath.toFile());
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
