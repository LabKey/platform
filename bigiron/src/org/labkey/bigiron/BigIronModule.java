/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
import org.labkey.bigiron.mssql.MicrosoftSqlServer2000DialectFactory;
import org.labkey.bigiron.mssql.MicrosoftSqlServer2005DialectFactory;
import org.labkey.bigiron.mysql.MySqlDialectFactory;
import org.labkey.bigiron.sas.Sas91DialectFactory;
import org.labkey.bigiron.sas.Sas92DialectFactory;
import org.labkey.bigiron.sas.SasExportScriptFactory;

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
        return 10.39;
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
        SqlDialectManager.register(new MicrosoftSqlServer2000DialectFactory());
        SqlDialectManager.register(new MicrosoftSqlServer2005DialectFactory());
        SqlDialectManager.register(new MySqlDialectFactory());
        SqlDialectManager.register(new Sas91DialectFactory());
        SqlDialectManager.register(new Sas92DialectFactory());

        QueryView.register(new SasExportScriptFactory());
    }

    public void startup(ModuleContext moduleContext)
    {
    }
}