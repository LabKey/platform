/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.core.security;

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ContainerTreeSelected;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartView;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * User: jeckels
* Date: May 1, 2008
*/ //
// VIEWS
//
public class ContainersView extends WebPartView
{
    Container _c;
    String _id = "permissionsContainerTree";
    String _className = null;


    public ContainersView(Container c)
    {
        super("Folders in project " + c.getName());
        _c = c;
    }

    public ContainersView(Container c, String id, String className)
    {
        super("Folders in project " + c.getName());
        _id = id;
        _className = className;
        _c = c;
    }

    public void renderView(Object model, PrintWriter out) throws IOException, ServletException
    {
        ActionURL url = PageFlowUtil.urlProvider(SecurityUrls.class).getContainerURL(_c);
        PermissionsContainerTree ct = new PermissionsContainerTree(_c.getPath(), getViewContext().getUser(), url);
        ct.setCurrent(getViewContext().getContainer());
        LookAndFeelProperties props = LookAndFeelProperties.getInstance(getViewContext().getContainer());
        StringBuilder html = new StringBuilder("<div id=\"" + PageFlowUtil.filter(_id) + "\" class=\"extContainer " + _className + "\"><table class=\"labkey-data-region-legacy\" style=\"" + props.getNavigationBarWidth() + "px\">");
        ct.render(html);
        html.append("</table><br>");
        html.append("<span style=\"font-style:italic\">*Indicates permissions are inherited</span></div>");

        out.println(html.toString());
    }

    public static class PermissionsContainerTree extends ContainerTreeSelected
    {
        public PermissionsContainerTree(String rootPath, User user, ActionURL url)
        {
            super(rootPath, user, AdminPermission.class, url);
        }

        protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
        {
            if (c.equals(current))
                html.append("<span class=\"labkey-nav-tree-selected\">");

            if (null != url)
            {
                html.append("<a href=\"");
                url.setContainer(c);
                html.append(url.getEncodedLocalURIString());
                html.append("\">");
                if (c.isInheritedAcl())
                    html.append('*');
                html.append(PageFlowUtil.filter(c.getName()));
                html.append("</a>");
            }
            else
            {
                html.append(PageFlowUtil.filter(c.getName()));
            }

            if (c.equals(current))
                html.append("</span>");
        }
    }
}
