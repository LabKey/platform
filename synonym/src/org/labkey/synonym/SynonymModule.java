/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        return 15.21;
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