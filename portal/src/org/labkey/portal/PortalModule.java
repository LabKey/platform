/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.util.Search;
import org.labkey.api.util.Search.SearchWebPart;
import org.labkey.api.view.*;
import org.labkey.api.security.User;

import java.beans.PropertyChangeEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Arrays;

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

    // NOTE: the version number of the portal module does not govern the scripts run for the
    // portal schema.  Bump the core module version number to cause a portal-xxx.sql script to run
    public double getVersion()
    {
        return 9.30;
    }

    protected void init()
    {
        addController("project", ProjectController.class);
    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(new SearchWebPartFactory("Search", null),
            new SearchWebPartFactory("Search", "right"));
    }

    public boolean hasScripts()
    {
        return false;
    }

    public static class SearchWebPartFactory extends AlwaysAvailableWebPartFactory
    {
        public SearchWebPartFactory(String name, String location)
        {
            super(name, location, true, false);
            addLegacyNames("Narrow Search");
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            int width = 40;
            if ("right".equals(webPart.getLocation()))
            {
                width = 0;
            }
            boolean includeSubfolders = Search.includeSubfolders(webPart);
            return new SearchWebPart(Search.ALL_SEARCHABLES, "", ProjectController.getSearchUrl(portalCtx.getContainer()), includeSubfolders, false, width, false);
        }


        @Override
        public HttpView getEditView(Portal.WebPart webPart)
        {
            return new JspView<Portal.WebPart>("/org/labkey/portal/customizeSearchWebPart.jsp", webPart);
        }
    }


    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_USER_PREFERENCE_DEFAULT;
    }


    public void startup(ModuleContext context)
    {
        ContainerManager.addContainerListener(new ContainerManager.ContainerListener()
        {
            public void containerCreated(Container c)
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

            public void propertyChange(PropertyChangeEvent evt)
            {
            }
        });
    }
}
