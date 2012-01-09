/*******************************************************************************
 * Copyright (c) 2011 Compuware Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Compuware Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.test.bug351487_testMetaRequirements;

import java.util.Arrays;

import org.apache.maven.it.Verifier;
import org.eclipse.tycho.test.AbstractTychoIntegrationTest;
import org.junit.Test;

public class TestMetaRequirementsTest extends AbstractTychoIntegrationTest {
    @Test
    public void test() throws Exception {
        Verifier verifier = getVerifier("/351487_metaRequirements", false);
        verifier.executeGoals(Arrays.asList("clean", "install"));
        verifier.verifyErrorFreeLog();
    }
}
