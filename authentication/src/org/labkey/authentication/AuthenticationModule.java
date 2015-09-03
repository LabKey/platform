/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

package org.labkey.authentication;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;

public class AuthenticationModule extends DefaultModule
{
    private static Logger _log = Logger.getLogger(AuthenticationModule.class);

    public String getName()
    {
        return "Authentication";
    }

    public double getVersion()
    {
        return 15.20;
    }

    @NotNull
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


    }

    public void doStartup(ModuleContext moduleContext)
    {
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }
}