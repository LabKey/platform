package org.labkey.api;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Created by susanh on 1/19/17.
 */
public class ApiModule extends CodeOnlyModule
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

    @Override
    public @NotNull Set<Class> getUnitTests()
    {
        return ImmutableSet.of(
            Constants.TestCase.class,
            DataIteratorUtil.TestCase.class
        );
    }
}
