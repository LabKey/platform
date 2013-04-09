/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

import org.labkey.api.data.dialect.SqlDialectManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.WebPartFactory;
import org.labkey.bigiron.mssql.MicrosoftSqlServerDialectFactory;
import org.labkey.bigiron.mysql.MySqlDialectFactory;
import org.labkey.bigiron.sas.SasDialectFactory;
import org.labkey.bigiron.sas.SasExportScriptFactory;
import org.labkey.bigiron.oracle.OracleDialectFactory;

import java.util.Collection;
import java.util.Collections;

public class BigIronModule extends DefaultModule
{
    public String getName()
    {
        return "BigIron";
    }

    public double getVersion()
    {
        return 13.10;
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    public boolean hasScripts()
    {
        return false;
    }

    protected void init()
    {
        SqlDialectManager.register(new MicrosoftSqlServerDialectFactory());
        SqlDialectManager.register(new MySqlDialectFactory());
        SqlDialectManager.register(new SasDialectFactory());
        SqlDialectManager.register(new OracleDialectFactory());

        QueryView.register(new SasExportScriptFactory());
    }

    public void doStartup(ModuleContext moduleContext)
    {
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }
}