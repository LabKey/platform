/*
 * Copyright (c) 2005-2012 Fred Hutchinson Cancer Research Center
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
package org.labkey.portal;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.api.view.Portal;
import org.labkey.api.view.WebPartFactory;

import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * User: migra
 * Date: Jul 25, 2005
 * Time: 1:56:13 PM
 */
public class PortalModule extends DefaultModule
{
    private static final Logger _log = Logger.getLogger(DefaultModule.class);

    public String getName()
    {
        return "Portal";
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return Collections.singleton((Class) PortalJUnitTest.class);
    }

    // NOTE: the version number of the portal module does not govern the scripts run for the
    // portal schema.  Bump the core module version number to cause a portal-xxx.sql script to run
    public double getVersion()
    {
        return 12.21;
    }

    protected void init()
    {
        addController("project", ProjectController.class);
        addController("util", UtilController.class);
    }

    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    public boolean hasScripts()
    {
        return false;
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_USER_PREFERENCE_DEFAULT;
    }


    public void doStartup(ModuleContext context)
    {
        ContainerManager.addContainerListener(new ContainerManager.ContainerListener()
        {
            public void containerCreated(Container c, User user)
            {
            }

            public void containerDeleted(Container c, User user)
            {
                try
                {
                    Portal.containerDeleted(c);
                }
                catch (Exception e)
                {
                    _log.error("Unable to delete WebParts for container " + c.getPath() + " Error:  " + e.getMessage());
                }
            }

            @Override
            public void containerMoved(Container c, Container oldParent, User user)
            {                
            }

            public void propertyChange(PropertyChangeEvent evt)
            {
            }
        });
    }
}
