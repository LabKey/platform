/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.apache.commons.collections15.MultiMap;
import org.labkey.api.view.ViewContext;

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
    public static final String EXPERIMENTAL_NAV = "experimental-navigation";

    public MenuBarView(List<Portal.WebPart> menus, PageConfig page)
    {
        super(MenuBarView.class,  "menuBar.jsp", new MenuBarBean(menus, page));
        setFrame(FrameType.NONE);
    }

    public MenuBarView(ViewContext ctx, PageConfig page)
    {
        this(Collections.<Portal.WebPart>emptyList(), page);
        Container container = ctx.getContainer();
        Container project = container.getProject();

        if (null != project && LookAndFeelProperties.getInstance(project).isMenuUIEnabled())
        {
            Collection<Portal.WebPart> allParts = Portal.getParts(project, ctx);
            MultiMap<String, Portal.WebPart> locationMap = Portal.getPartsByLocation(allParts);
            List<Portal.WebPart> menuParts = (List<Portal.WebPart>) locationMap.get("menubar");

            if (null == menuParts)
                menuParts = Collections.emptyList();

            setModelBean(new MenuBarBean(menuParts, page));
        }
        else
        {
            List<Portal.WebPart> menuParts = Collections.emptyList();
            setModelBean(new MenuBarBean(menuParts, page));
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
