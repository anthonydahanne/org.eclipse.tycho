/*******************************************************************************
 * Copyright (c) 2012 Compuware Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Compuware Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.plugins.p2.director;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.sisu.equinox.EquinoxServiceFactory;
import org.eclipse.sisu.equinox.launching.DefaultEquinoxInstallationDescription;
import org.eclipse.sisu.equinox.launching.EquinoxInstallation;
import org.eclipse.sisu.equinox.launching.EquinoxLauncher;
import org.eclipse.sisu.equinox.launching.internal.DefaultEquinoxInstallation;
import org.eclipse.sisu.equinox.launching.internal.EquinoxLaunchConfiguration;
import org.eclipse.tycho.core.TargetEnvironment;
import org.eclipse.tycho.core.TychoConstants;
import org.eclipse.tycho.core.utils.PlatformPropertiesUtils;
import org.eclipse.tycho.p2.facade.RepositoryReferenceTool;
import org.eclipse.tycho.p2.tools.RepositoryReferences;
import org.eclipse.tycho.p2.tools.director.facade.DirectorApplicationWrapper;

/**
 * @phase package
 * @goal materialize-products-metarequirements
 */
@SuppressWarnings("nls")
public final class DynamicDirectorMojo extends AbstractDirectorMojo {

    /**
     * @component role="org.codehaus.plexus.archiver.UnArchiver" role-hint="zip"
     */
    private UnArchiver unArchiver;

    /** @component */
    private EquinoxServiceFactory p2;

    /**
     * @component role="org.apache.maven.repository.RepositorySystem"
     */
    private RepositorySystem repositorySystem;

    /** @parameter default-value="DefaultProfile" */
    private String profile;

    /* @parameter */
    private List<ProfileName> profileNames;

    /** @parameter default-value="true" */
    private boolean installFeatures;

    /** @component */
    private RepositoryReferenceTool repositoryReferenceTool;

    /** @component */
    private EquinoxLauncher launcher;

    public void execute() throws MojoExecutionException, MojoFailureException {
        List<Product> products = getProductConfig().getProducts();
        if (products.isEmpty()) {
            getLog().info("No product definitions found. Nothing to do.");
        }

        //dirty hack to get the current version of the plugin; Maven gurus,there should be a single line to get it
        // do not hesitate to replace this !
        String version = null;
        List<Plugin> buildPlugins = getProject().getBuildPlugins();
        for (Plugin plugin : buildPlugins) {
            if (plugin.getArtifactId().equalsIgnoreCase("tycho-p2-director-plugin")) {
                version = plugin.getVersion();
                break;
            }

        }

        //getting the tycho-bundles-external-dynamic product definition  (.product)      
        final File directorApplicationDir = materializeLocalDirector(version);

        for (Product product : products) {
            for (TargetEnvironment env : getEnvironments()) {

                final DirectorApplicationWrapper director = new DynamicDirectorApplicationWrapper(
                        directorApplicationDir);

                File destination = getProductMaterializeDirectory(product, env);
                String rootFolder = product.getRootFolder();
                if (rootFolder != null && rootFolder.length() > 0) {
                    destination = new File(destination, rootFolder);
                }

                String localP2Repository = "";
                try {
                    File localP2RepositoryFile = new File(getBuildDirectory(), "repository");
                    localP2Repository = localP2RepositoryFile.getCanonicalPath();
                    localP2Repository = localP2Repository.replace("\\", "/");
                    localP2Repository = "file:/" + localP2Repository;
                } catch (IOException e) {
                    e.printStackTrace();
                }

                String nameForEnvironment = ProfileName.getNameForEnvironment(env, profileNames, profile);
                String[] args = getArgsForDirectorCall(product, env, destination, localP2Repository, localP2Repository,
                        nameForEnvironment, installFeatures);
                getLog().info("Calling director with arguments: " + Arrays.toString(args));
                final Object result = director.run(args);
                if (!DirectorApplicationWrapper.EXIT_OK.equals(result)) {
                    throw new MojoFailureException("P2 director return code was " + result);
                }
            }
        }
    }

    /**
     * Creates a local director runtime, based on tycho-bundles-external-dynamic product definition
     * Compared to tycho-bundles-external director runtime, this one can be updated and so can
     * successfully use and understand metarequirements
     * https://bugs.eclipse.org/bugs/show_bug.cgi?id=%20351487
     * 
     * @param version
     * @return
     * @throws MojoExecutionException
     * @throws MojoFailureException
     */
    private File materializeLocalDirector(String version) throws MojoExecutionException, MojoFailureException {
        Artifact artifact = repositorySystem.createArtifact("org.eclipse.tycho", "tycho-bundles-external-dynamic",
                version, "eclipse-repository");
        String artifactPath = getSession().getLocalRepository().getBasedir() + System.getProperty("file.separator")
                + getSession().getLocalRepository().pathOf(artifact);
//        String productArtifactPath = artifactPath.substring(0, artifactPath.indexOf(".zip")) + ".product";
        File productRepoZipped = new File(artifactPath);

        //creating the target/director directory that will contain the director
        File directorApplicationDir = new File(getBuildDirectory(), "director");
        directorApplicationDir.mkdir();

        //creating the target/directorRepo directory that will contain the director repository
        //containing all the IUs we need to build it
        File productRepoUnzipped = new File(getBuildDirectory(), "directorRepo");
        productRepoUnzipped.mkdir();

        unArchiver.setSourceFile(productRepoZipped);
        unArchiver.setDestDirectory(productRepoUnzipped);
        unArchiver.setDestFile(null);
        try {
            unArchiver.extract();
        } catch (ArchiverException e) {
            throw new MojoExecutionException("Failed to unpack director p2 repo " + e.getMessage(), e);

        }

        //materializing the director from the tycho-bundles-external-dynamic product definition
        Product directorProduct = new Product("tycho-bundles-external-dynamic");
        final DirectorApplicationWrapper director = p2.getService(DirectorApplicationWrapper.class);
        int flags = RepositoryReferenceTool.REPOSITORIES_INCLUDE_CURRENT_MODULE;
        RepositoryReferences sources = repositoryReferenceTool
                .getVisibleRepositories(getProject(), getSession(), flags);

        // the environment is the local environment since we're going to run the director from this env.
        Properties properties = (Properties) getProject().getContextValue(TychoConstants.CTX_MERGED_PROPERTIES);
        String os = PlatformPropertiesUtils.getOS(properties);
        String ws = PlatformPropertiesUtils.getWS(properties);
        String arch = PlatformPropertiesUtils.getArch(properties);

        TargetEnvironment env = new TargetEnvironment(os, ws, arch, null /* nl */);

        //adding the repository containing the tycho-bundles-external-dynamic product
        String productRepository = null;
        try {
            productRepository = productRepoUnzipped.getCanonicalPath();
            productRepository = productRepository.replace("\\", "/");
            productRepository = "file:/" + productRepository;
        } catch (IOException e) {
            throw new MojoFailureException(
                    "Impossible to determine tycho-bundles-external-dynamic p2 repository location " + e.getMessage());
        }

        String metadataRepositoryURLs = toCommaSeparatedList(sources.getMetadataRepositories()) + ","
                + productRepository;
        String artifactRepositoryURLs = toCommaSeparatedList(sources.getArtifactRepositories()) + ","
                + productRepository;
        String nameForEnvironment = ProfileName.getNameForEnvironment(env, profileNames, profile);
        String[] args = getArgsForDirectorCall(directorProduct, env, directorApplicationDir, metadataRepositoryURLs,
                artifactRepositoryURLs, nameForEnvironment, true);
        getLog().info("Calling director with arguments: " + Arrays.toString(args));
        final Object result = director.run(args);
        if (!DirectorApplicationWrapper.EXIT_OK.equals(result)) {
            throw new MojoFailureException("P2 director return code was " + result);
        }
        return directorApplicationDir;
    }

    /**
     * This class wraps the tycho-bundles-external-dynamic product based director runtime that lays
     * in target/director (directorApplicationDir)
     * 
     */
    private class DynamicDirectorApplicationWrapper implements DirectorApplicationWrapper {

        private File directorApplicationDir;

        public DynamicDirectorApplicationWrapper(File directorApplicationDir) {
            this.directorApplicationDir = directorApplicationDir;
        }

        public Object run(String[] args) {

            DefaultEquinoxInstallationDescription installationDescription = new DefaultEquinoxInstallationDescription();
            EquinoxInstallation testRuntime = new DefaultEquinoxInstallation(installationDescription,
                    directorApplicationDir);
            EquinoxLaunchConfiguration cli = new EquinoxLaunchConfiguration(testRuntime) {
                public File getLauncherJar() {
                    try {
                        String pluginDirectory = directorApplicationDir.getCanonicalPath()
                                + System.getProperty("file.separator") + "plugins"
                                + System.getProperty("file.separator");
                        @SuppressWarnings("unchecked")
                        List<File> fileNames = FileUtils.getFiles(new File(pluginDirectory),
                                "org.eclipse.equinox.launcher*.jar", null, false);
                        File equinoxLauncher = fileNames.get(0);
                        equinoxLauncher = new File(pluginDirectory, equinoxLauncher.getPath());
                        return equinoxLauncher;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            };
            cli.addProgramArguments(args);
            return launcher.execute(cli, 0);
        }

    }

}
