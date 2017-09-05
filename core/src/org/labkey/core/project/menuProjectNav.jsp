<%
    /*
     * Copyright (c) 2017 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.view.PopupFolderNavView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ViewContext context = getViewContext();
    Container c = context.getContainer();
    User user = context.getUser();

    PopupFolderNavView popupFolderNavView = new PopupFolderNavView(context);
    List<Container> containers = ContainerManager.containersToRootList(c);
    int size = containers.size();

    // Only show the nav trail if you are at least one level into the nav tree (i.e. not at project)
    if (size > 1)
    {
        String title = containers.get(size - 1).isWorkbook()
                ? containers.get(size - 1).getName() : containers.get(size - 1).getTitle();

        if (c.isProject() && c.equals(ContainerManager.getHomeContainer()))
            title = "Home";
%>
        <div class="lk-project-nav-trail">
<%
        if (size < 5)
        {
            for (int i = 0; i < size - 1; i++)
                PopupFolderNavView.renderFolderNavTrailLink(containers.get(i), user, out);
        }
        else
        {
            for (int i = 0; i < 2; i++)
                PopupFolderNavView.renderFolderNavTrailLink(containers.get(i), user, out);
%>
                ...<i class="fa fa-chevron-right"></i>
<%
            for (int i = (size - 2); i < (size - 1); i++)
                PopupFolderNavView.renderFolderNavTrailLink(containers.get(i), user, out);
        }
%>
            <span><%=h(title)%></span>
        </div>
        <div class="divider lk-project-nav-divider"></div>
<%
    }
%>
<% popupFolderNavView.render(out); %>
<div class="lk-project-nav-buttons">
<%
    if (c.hasPermission(user, AdminPermission.class))
    {
        ActionURL createProjectUrl = PageFlowUtil.urlProvider(AdminUrls.class).getCreateProjectURL(c.getStartURL(getUser()));
        ActionURL createFolderUrl = PageFlowUtil.urlProvider(AdminUrls.class).getCreateFolderURL(c, c.getStartURL(getUser()));
        ActionURL folderManagementUrl = PageFlowUtil.urlProvider(AdminUrls.class).getManageFoldersURL(c);

        if (user.hasRootAdminPermission())
        {
%>
            <span class="button-icon">
                <a href="<%=createProjectUrl%>" title="New Project">
                    <span class="fa-stack fa-1x" alt="New Project">
                      <i class="fa fa-folder fa-stack-2x"></i>
                      <i class="fa fa-plus fa-stack-1x" style="color: white;"></i>
                    </span>
                </a>
            </span>
<%
        }
%>
            <span class="button-icon">
                <a href="<%=createFolderUrl%>" title="New Subfolder">
                    <span class="fa-stack fa-1x" alt="New Subfolder">
                      <i class="fa fa-folder-o fa-stack-2x"></i>
                      <i class="fa fa-plus-circle fa-stack-1x"></i>
                    </span>
                </a>
            </span>
            <span class="button-icon">
                <a href="<%=folderManagementUrl%>" title="Folder Management">
                    <span class="fa fa-gear fa-lg" alt="Folder Management"></span>
                </a>
            </span>
<%
    }

    if (!c.isRoot())
    {
%>
            <span class="button-icon">
                <a id="permalink_vis" href="#" title="Permalink Page">
                    <span class="fa fa-link fa-lg" alt="Permalink Page"></span>
                </a>
            </span>
<%
    }
%>
</div>

<script type="text/javascript">
    +function($) {
        var p = document.getElementById('permalink');
        var pvis = document.getElementById('permalink_vis');
        if (p && pvis) {
            pvis.href = p.href;
        }
    }(jQuery);
</script>