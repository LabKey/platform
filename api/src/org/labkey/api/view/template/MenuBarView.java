/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

package org.labkey.api.view.template;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/*
* User: markigra
* Date: Jan 4, 2009
* Time: 9:44:28 AM
*/
public class MenuBarView extends JspView<MenuBarView.MenuBarBean>
{
    private static final Logger LOG = Logger.getLogger(MenuBarView.class);

    public MenuBarView(ViewContext ctx, PageConfig page)
    {
        super(MenuBarView.class, "menuBar.jsp", null /* will be set later in constructor */);
        setFrame(FrameType.NONE);

        Container container = ctx.getContainer();
        Container project = container.getProject();

        if (null != project)
        {
            Collection<Portal.WebPart> allParts = Portal.getParts(project, ctx);
            MultiValuedMap<String, Portal.WebPart> locationMap = Portal.getPartsByLocation(allParts);
            List<Portal.WebPart> menuParts = (List<Portal.WebPart>) locationMap.get("menubar");

            if (null == menuParts)
                menuParts = Collections.emptyList();

            for (Portal.WebPart part : menuParts)
            {
                try
                {
                    WebPartFactory factory = Portal.getPortalPart(part.getName());
                    if (null != factory)
                    {
                        WebPartView view = factory.getWebPartView(getViewContext(), part);
                        if (!view.isEmpty())
                        {
                            addClientDependencies(view.getClientDependencies());
                        }
                    }
                }
                catch (Exception x)
                {
                    LOG.error("Failed to add client dependencies", x);
                }
            }

            setModelBean(new MenuBarBean(menuParts, page));
        }
        else
        {
            setModelBean(new MenuBarBean(Collections.emptyList(), page));
        }
    }

    public static class MenuBarBean
    {
        public List<Portal.WebPart> menus;
        public PageConfig pageConfig;

        private MenuBarBean(List<Portal.WebPart> menus, PageConfig page)
        {
            this.menus = menus;
            this.pageConfig = page;
        }
    }
}
