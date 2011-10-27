/*
 * Copyright (c) 2007-2011 LabKey Corporation
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

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NavTreeManager;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Container;

import java.util.Set;

/**
 * User: brittp
 * Date: Apr 9, 2007
 * Time: 9:51:02 AM
 */
public class ContainerMenu extends NavTreeMenu
{
    public ContainerMenu(ViewContext context)
    {
        super(context, "container-menu", "Project Folders", false, getNavTree(context));
    }

    private static NavTree getNavTree(ViewContext context)
    {
        if (context.getContainer().getProject() != null)
        {
            NavTree tree = ContainerManager.getFolderListForUser(context.getContainer().getProject(), context);
            Set<Container> mustExpand = ContainerManager.containersToRoot(context.getContainer());
            Container home = ContainerManager.getHomeContainer();
            //Ensure path to current container is expanded and stays that way.
            for (Container f : mustExpand)
            {
                String path = f.getPath();
                if (home.equals(f.getProject()))
                {
                    assert path.toLowerCase().startsWith("/home") : "Home path not in expected format";
                    path = path.replaceFirst("/home", "/Home");
                }
                // save the expand/collapse state here: after viewing a container, it will
                // stay visible (expanded) even if the user navigates to another container.
                // Note that this does not apply the state to our NavTree; we do that below.
                NavTreeManager.expandCollapsePath(context, tree.getId(), path, false);
            }
            // apply the saved expand state to the NavTree itself:
            NavTreeManager.applyExpandState(tree, context);
            return tree;
        }
        else
            return null;
    }

    @Override
    public boolean isVisible()
    {
        return getViewContext().getContainer().getProject() != null;
    }
}
