/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */

package org.labkey.saml;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;

public class SamlModule extends DefaultModule
{
    public static final String NAME = "SAML";
    public static final String EXPERIMENTAL_SAML_SERVICE_PROVIDER = "experimental-saml-sp";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 15.21;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void init()
    {
        addController("saml", SamlController.class);

        AdminConsole.addExperimentalFeatureFlag(EXPERIMENTAL_SAML_SERVICE_PROVIDER, "Login using SAML", "Authenticate using a SAML Identity Provider.", true);
        if (AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_SAML_SERVICE_PROVIDER))
            AuthenticationManager.registerProvider(new SamlProvider(), AuthenticationManager.Priority.Low);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
    }
}