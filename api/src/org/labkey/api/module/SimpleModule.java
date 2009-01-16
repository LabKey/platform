/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.api.module;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Container;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.security.User;

import java.io.File;
import java.util.Collection;

/*
* User: Dave
* Date: Dec 3, 2008
* Time: 4:08:20 PM
*/

/**
 * Used for simple, entirely file-based modules
 */
public class SimpleModule extends DefaultModule
{
    public SimpleModule(@NotNull String name)
    {
        setName(name);
    }

    protected void init()
    {

    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return null;
    }

    public boolean hasScripts()
    {
        return new File(getSqlScriptsPath(CoreSchema.getInstance().getSqlDialect())).exists();
    }

    public void startup(ModuleContext moduleContext)
    {
    }

    protected String getResourcePath()
    {
        return null;
    }

    public ActionURL getTabURL(Container c, User user)
    {
        //simple modules will not have tabs for now
        //once we develop a SimpleController for simple modules
        //we can register a page flow name and a begin action
        return null;
    }
}