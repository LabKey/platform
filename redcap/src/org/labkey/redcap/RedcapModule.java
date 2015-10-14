/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.redcap;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by klum on 1/16/2015.
 */
public class RedcapModule extends DefaultModule
{
    public static final String NAME = "REDCap";
    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 15.20;
    }

    @Override
    public boolean hasScripts()
    {
        return false;
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
        addController("redcap", RedcapController.class);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        StudyService.get().registerStudyReloadSource(new RedcapReloadSource());

        //add the REDCap import task to the list of system maintenance tasks
        SystemMaintenance.addTask(new RedcapMaintenanceTask());
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }
}
