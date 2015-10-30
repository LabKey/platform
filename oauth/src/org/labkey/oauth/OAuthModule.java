/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */

package org.labkey.oauth;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;

public class OAuthModule extends DefaultModule
{
    public static final String NAME = "OAuth";
    public static final String EXPERIMENTAL_OPENID_GOOGLE = "experimental-openid-google";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 15.30;
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
        addController("oauth", OAuthController.class);
        AdminConsole.addExperimentalFeatureFlag(EXPERIMENTAL_OPENID_GOOGLE, "Login using your Google account", "Authenticate using Google and OAuth 2.0.", true);

        if (AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_OPENID_GOOGLE))
            AuthenticationManager.registerProvider(new GoogleOAuthProvider(), AuthenticationManager.Priority.Low);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
    }
}