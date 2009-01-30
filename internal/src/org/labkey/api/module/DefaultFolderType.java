/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.security.User;
import org.labkey.api.view.Portal;
import org.labkey.api.view.Portal.WebPart;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.AppBar;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.util.PageFlowUtil;

import java.sql.SQLException;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Aug 2, 2006
 * Time: 8:44:04 PM
 */
public class DefaultFolderType implements FolderType
{
    protected List<WebPart> requiredParts;
    protected List<WebPart> preferredParts;
    protected Set<Module> activeModules;
    protected String description;
    protected String name;
    protected  Module defaultModule;

    public DefaultFolderType(String name, String description)
    {
        this.name = name;
        this.description = description;
    }

    public DefaultFolderType(String name, String description, List<Portal.WebPart> requiredParts, List<Portal.WebPart> preferredParts, Set<Module> activeModules)
    {
        this.name = name;
        this.description = description;
        this.requiredParts = requiredParts == null ? Collections.<WebPart>emptyList() : requiredParts;
        this.preferredParts = preferredParts == null ? Collections.<WebPart>emptyList() : preferredParts;
        this.activeModules = activeModules;
    }

    public DefaultFolderType(String name, String description, List<Portal.WebPart> requiredParts, List<Portal.WebPart> preferredParts, Set<Module> activeModules, Module defaultModule)
    {
        this(name, description, requiredParts, preferredParts, activeModules);
        this.defaultModule = defaultModule;
    }


    public void configureContainer(Container c)
    {
        List<Portal.WebPart> required = getRequiredWebParts();
        List<Portal.WebPart> defaultParts = getPreferredWebParts();

        //Just to be sure, make sure required web  parts are set correctly
        if (null != required)
            for (Portal.WebPart part : required)
                part.setPermanent(true);

        ArrayList<Portal.WebPart> all = new ArrayList<Portal.WebPart>();

        try
        {
            Portal.WebPart[] existingParts = Portal.getParts(c.getId());
            if (null == existingParts || existingParts.length == 0)
            {
                if (null != required)
                    all.addAll(required);
                if (null != defaultParts)
                    all.addAll(defaultParts);
            }
            else
            {
                //Order will be required,preferred,optional
                all.addAll(Arrays.asList(existingParts));
                for (WebPart p : all)
                    p.setIndex(2);

                if (null != required)
                    for (Portal.WebPart part: required)
                    {
                        Portal.WebPart foundPart = findPart(all, part);
                        if (null != foundPart)
                        {
                            foundPart.setPermanent(true);
                            foundPart.setIndex(0);
                        }
                        else
                        {
                            part.setIndex(0);
                            all.add(part);
                        }
                    }

                if (null != defaultParts)
                    for (Portal.WebPart part: defaultParts)
                    {
                        Portal.WebPart foundPart = findPart(all, part);
                        if (null == foundPart)
                        {
                            part.setIndex(1); //Should put these right after required parts
                            all.add(part);
                        }
                        else
                            foundPart.setIndex(1);
                    }
            }

            Set<Module> active = c.getActiveModules(false);
            Set<Module> requiredActive = getActiveModules();

            if (null == active)
                active = new HashSet<Module>();
            else
                active = new HashSet<Module>(active); //Need to copy since returned set is unmodifiable.

            active.addAll(requiredActive);
            c.setActiveModules(active);
            Portal.saveParts(c.getId(), all.toArray(new Portal.WebPart[all.size()]));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void unconfigureContainer(Container c)
    {
        WebPart[] parts = Portal.getParts(c.getId());
        if (null != parts)
        {
            boolean saveRequired = false;

            for (WebPart part : parts)
            {
                if (part.isPermanent())
                {
                    part.setPermanent(false);
                    saveRequired = true;
                }
            }

            if (saveRequired)
                Portal.saveParts(c.getId(), parts);
        }
    }

    /**
     * Find a web part. Don't use strict equality, just name and location
     * @return matchingPart
     */
    private Portal.WebPart findPart(List<Portal.WebPart> parts, Portal.WebPart partToFind)
    {
        String location = partToFind.getLocation();
        String name = partToFind.getName();
        for (Portal.WebPart part : parts)
            if (name.equals(part.getName()) && location.equals(part.getLocation()))
                return part;

        return null;
    }


    public ActionURL getStartURL(Container c, User user)
    {
        return ModuleLoader.getInstance().getModule("Portal").getTabURL(c, user);
    }

    public String getStartPageLabel(ViewContext ctx)
    {
        return getLabel() + " Dashboard";
    }

    public Module getDefaultModule()
    {
        return defaultModule;
    }

    public List<Portal.WebPart> getRequiredWebParts()
    {
        return requiredParts;
    }

    public List<Portal.WebPart> getPreferredWebParts()
    {
        return preferredParts;
    }

    public String getName()
    {
        return name;
    }

    public String getDescription()
    {
        return description;
    }

    public String getLabel()
    {
        return name;
    }

    public Set<Module> getActiveModules()
    {
        return activeModules;
    }

    private static Set<Module> s_defaultModules = null;
    public static Set<Module> getDefaultModuleSet(Module...additionalModules)
    {
        //NOT thread safe, but worst thing that will happen is that it is set to the same thing twice
        if (null == s_defaultModules)
        {
            Set<Module> defaultModules = new HashSet<Module>();
            defaultModules.add(getModule("Announcements"));
            defaultModules.add(getModule("FileContent"));
            defaultModules.add(getModule("Wiki"));
            defaultModules.add(getModule("Query"));
            defaultModules.add(getModule("Portal"));
            defaultModules.add(getModule("Issues"));
            s_defaultModules = defaultModules;
        }

        Set<Module> modules = new HashSet<Module>(s_defaultModules);
        modules.addAll(Arrays.asList(additionalModules));

        return modules;
    }

    protected static Module getModule(String moduleName)
    {
        return ModuleLoader.getInstance().getModule(moduleName);
    }

    public void addManageLinks(NavTree adminNavTree, Container container)
    {
        adminNavTree.addChild(new NavTree("Manage Assays", PageFlowUtil.urlProvider(AssayUrls.class).getAssayListURL(container)));
        adminNavTree.addChild(new NavTree("Manage Lists", ListService.get().getManageListsURL(container)));
    }

    public AppBar getAppBar(ViewContext context)
    {
        return null;
    }
}
