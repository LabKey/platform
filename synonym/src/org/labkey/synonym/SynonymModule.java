/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */

package org.labkey.synonym;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class SynonymModule extends DefaultModule
{
    static
    {
        SqlDialectManager.getFactories().stream()
                .filter(factory -> factory.getClass().getSimpleName().equals("MicrosoftSqlServerDialectFactory"))
                .forEach(factory -> factory.setTableResolver(new SynonymTableResolver()));
    }

    public static final String NAME = "Synonym";

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
    protected void init()
    {
    }

    @NotNull
    @Override
    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    public boolean hasScripts()
    {
        return false;
    }

    @Override
    protected void doStartup(ModuleContext moduleContext)
    {

    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    @NotNull
    @Override
    public Set<Class> getIntegrationTests()
    {
        return Collections.<Class>singleton(SynonymTestCase.class);
    }
}