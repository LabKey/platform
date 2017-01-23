package org.labkey;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by susanh on 1/9/17.
 */
public class InternalModule extends CodeOnlyModule
{
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
    protected void doStartup(ModuleContext moduleContext)
    {
    }
}
