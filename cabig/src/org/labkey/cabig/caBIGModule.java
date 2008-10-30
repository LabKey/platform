/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.cabig;

import org.apache.log4j.Logger;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class caBIGModule extends DefaultModule
{
    private static final Logger _log = Logger.getLogger(caBIGModule.class);
    public static final String NAME = "caBIG";

    public caBIGModule()
    {
        super(NAME, 8.20, null, true);
    }

    protected void init()
    {
        addController("cabig", caBIGController.class);
    }

    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        SecurityManager.addViewFactory(new caBIGController.caBIGPermissionsViewFactory());
    }

    public Set<String> getSchemaNames()
    {
        return Collections.singleton("cabig");
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(caBIGSchema.getInstance().getSchema());
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return TabDisplayMode.DISPLAY_NEVER;
    }
}