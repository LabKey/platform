/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.view.menu;

import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Container;

import java.util.Collections;
import java.util.List;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 10:19:50 AM
 */
public class ProjectsMenu extends NavTreeMenu
{
    public ProjectsMenu(ViewContext context)
    {
        super(context, "projectsMenu", "Projects", null, !isHomePage(context), true, getNavTree(context));
    }

    private static boolean isHomePage(ViewContext context)
    {
        ActionURL url = context.getActionURL();
        Container homeContainer = ContainerManager.getHomeContainer();
        boolean isHomeContainer = context.getContainer().equals(homeContainer);
        return isHomeContainer && "project".equalsIgnoreCase(url.getController()) && "begin".equalsIgnoreCase(url.getAction());
    }
    
    public static List<NavTree> getNavTree(ViewContext context)
    {
        NavTree projects = ContainerManager.getProjectList(context, false);
        if (null == projects)
            return Collections.emptyList();

        return projects.getChildren();
    }
}
