/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.view.WebPartView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.util.ContainerTreeSelected;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.admin.AdminUrls;

import javax.servlet.ServletException;
import java.io.PrintWriter;
import java.io.IOException;

/**
 * User: jeckels
* Date: May 1, 2008
*/ //
// VIEWS
//
public class ContainersView extends WebPartView
{
    Container _c;

    public ContainersView(Container c)
    {
        setTitle("Folders in project " + c.getName());
        _c = c;
    }


    public void renderView(Object model, PrintWriter out) throws IOException, ServletException
    {
        ActionURL url = PageFlowUtil.urlProvider(SecurityUrls.class).getContainerURL(_c);
        PermissionsContainerTree ct = new PermissionsContainerTree(_c.getPath(), getViewContext().getUser(), ACL.PERM_ADMIN, url);
        ct.setCurrent(getViewContext().getContainer());
        StringBuilder html = new StringBuilder("<table class=\"labkey-data-region\">");
        ct.render(html);
        html.append("</table><br>");
        ActionURL manageFoldersURL = PageFlowUtil.urlProvider(AdminUrls.class).getManageFoldersURL(_c);
        html.append("*Indicates that this folder's permissions are inherited from the parent folder<br><br>");
        html.append("[<a href=\"").append(PageFlowUtil.filter(manageFoldersURL)).append("\">manage folders</a>]");

        out.println(html.toString());
    }

    public static class PermissionsContainerTree extends ContainerTreeSelected
    {
        public PermissionsContainerTree(String rootPath, User user, int perm, ActionURL url)
        {
            super(rootPath, user, perm, url);
        }

        protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
        {
            if (c.equals(current))
                html.append("<span class=\"labkey-nav-tree-selected\">");

            if (null != url)
            {
                html.append("<a href=\"");
                url.setExtraPath(c.getPath());
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
