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
import java.net.URI;
import java.util.List;

import org.eclipse.tycho.core.TargetEnvironment;

public abstract class AbstractDirectorMojo extends AbstractProductMojo {
    protected String toCommaSeparatedList(List<URI> repositories) {
        if (repositories.size() == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (URI uri : repositories) {
            result.append(uri.toString());
            result.append(',');
        }
        result.setLength(result.length() - 1);
        return result.toString();
    }

    protected String[] getArgsForDirectorCall(Product product, TargetEnvironment env, File destination,
            String metadataRepositoryURLs, String artifactRepositoryURLs, String nameForEnvironment,
            boolean installFeatures) {
        String[] args = new String[] { "-metadatarepository", metadataRepositoryURLs, //
                "-artifactrepository", artifactRepositoryURLs, //
                "-installIU", product.getId(), //
                "-destination", destination.getAbsolutePath(), //
                "-profile", nameForEnvironment, //
                "-profileProperties", "org.eclipse.update.install.features=" + String.valueOf(installFeatures), //
                "-roaming", //
                "-p2.os", env.getOs(), "-p2.ws", env.getWs(), "-p2.arch", env.getArch() };
        return args;
    }

}
