/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.bigiron;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.bigiron.ClrAssemblyManager;
import org.labkey.api.data.dialect.SqlDialectRegistry;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.bigiron.mssql.GroupConcatInstallationManager;
import org.labkey.bigiron.mssql.MicrosoftSqlServerDialectFactory;
import org.labkey.bigiron.mssql.MicrosoftSqlServerVersion;
import org.labkey.bigiron.mssql.synonym.SynonymTestCase;
import org.labkey.bigiron.mysql.MySqlDialectFactory;
import org.labkey.bigiron.oracle.OracleDialectFactory;
import org.labkey.bigiron.sas.SasDialectFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class BigIronModule extends CodeOnlyModule
{
    // Register these dialects extra early, since we need to initialize the data sources before calling DefaultModule.initialize()
    static
    {
        SqlDialectRegistry.register(new MicrosoftSqlServerDialectFactory());
        SqlDialectRegistry.register(new MySqlDialectFactory());
        SqlDialectRegistry.register(new SasDialectFactory());
        SqlDialectRegistry.register(new OracleDialectFactory());
    }

    @Override
    public String getName()
    {
        return "BigIron";
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
        addController("bigiron", BigIronController.class);
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        if (CoreSchema.getInstance().getSqlDialect().isSqlServer())
        {
            ClrAssemblyManager.registerInstallationManager(GroupConcatInstallationManager.get());
        }
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return Set.of(
            GroupConcatInstallationManager.TestCase.class,
            MicrosoftSqlServerVersion.TestCase.class,
            SynonymTestCase.class
        );
    }
}