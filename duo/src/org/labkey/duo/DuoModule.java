/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */

package org.labkey.duo;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.view.WebPartFactory;
import org.labkey.authentication.duo.DuoController;
import org.labkey.authentication.duo.DuoProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class DuoModule extends DefaultModule
{
    public static final String NAME = "Duo";

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
        addController("duo", DuoController.class);
        AuthenticationManager.registerProvider(new DuoProvider(), AuthenticationManager.Priority.Low);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
    }
}