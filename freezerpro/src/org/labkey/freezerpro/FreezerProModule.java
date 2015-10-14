/*
 * Copyright (c) 2013-2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */

package org.labkey.freezerpro;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FreezerProModule extends DefaultModule
{
    @Override
    public String getName()
    {
        return "FreezerPro";
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
        addController("freezerpro", FreezerProController.class);

        //add the FreezerPro Upload task to the list of system maintenance tasks
        SystemMaintenance.addTask(new FreezerProUploadTask());
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        SpecimenService.get().registerSpecimenTransform(new FreezerProTransform());
    }

    @Override
    @NotNull
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        Set<Class> set = new HashSet<>();
        set.add(FreezerProTransformTask.TestCase.class);

        return set;
    }
}