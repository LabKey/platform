<%
/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.project.FolderNavigationForm" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }
%>
<%
    JspView<FolderNavigationForm> me = (JspView<FolderNavigationForm>) HttpView.currentView();
    FolderNavigationForm form = me.getModelBean();
    User user = getUser();
    Container c = getContainer();
    List<Container> containers = ContainerManager.containersToRootList(c);
    int size = containers.size();

    ActionURL startURL = c.getStartURL(getUser()); // 30975: Return to startURL due to async view context

    ActionURL createProjectURL = PageFlowUtil.urlProvider(AdminUrls.class).getCreateProjectURL(null);
    createProjectURL.addParameter(ActionURL.Param.returnUrl, startURL.toString());

    ActionURL createFolderURL = PageFlowUtil.urlProvider(AdminUrls.class).getCreateFolderURL(c, null);
    createFolderURL.addParameter(ActionURL.Param.returnUrl, startURL.toString());

    ActionURL folderManagementURL = PageFlowUtil.urlProvider(AdminUrls.class).getManageFoldersURL(c);
%>
<%!
    public _HtmlString getTrailSeparator()
    {
        return _hs("&nbsp;/&nbsp;");
    }

    public _HtmlString getTrailLink(Container c, User u)
    {
        if (c.hasPermission(u, ReadPermission.class))
        {
            return _hs("<a href=\"" + h(c.getStartURL(u)) +"\">" + h(c.getTitle()) + "</a>" + getTrailSeparator());
        }
        return _hs("<span>" + h(c.getTitle()) + "</span>" + getTrailSeparator());
    }
%>
<% if (size > 1) { // Only show the nav trail if subfolders exist %>
    <div class="folder-trail">
        <%
            if (size < 5)
            {
                for (int p=0; p < size-1; p++)
                {
                    %><%=getTrailLink(containers.get(p), user)%><%
                }
                String title = containers.get(size - 1).isWorkbook() ? containers.get(size - 1).getName() : containers.get(size - 1).getTitle();
                %><span style="color: black;"><%=h(title)%></span><%
            }
            else
            {
                for (int p=0; p < 2; p++)
                {
                    %><%=getTrailLink(containers.get(p), user)%><%
                }
                %>...<%=getTrailSeparator()%><%
                for (int p=(size-2); p < size-1 ; p++)
                {
                    %><%=getTrailLink(containers.get(p), user)%><%
                }
                String title = containers.get(size - 1).isWorkbook() ? containers.get(size - 1).getName() : containers.get(size - 1).getTitle();
                %><span style="color: black;"><%=h(title)%></span><%
            }
        %>
    </div>
<% } %>
<div class="folder-tree"><% me.include(form.getFolderMenu(), out); %></div>
<div class="folder-menu-buttons">
    <% if (getUser().hasRootAdminPermission()) { %>
    <span class="folder-menu-button-icon">
        <a href="<%=createProjectURL%>" title="New Project">
            <span class="fa-stack fa-1x labkey-fa-stacked-wrapper">
                <span class="fa fa-folder-open-o fa-stack-2x labkey-main-menu-icon" alt="New Project"></span>
                <span class="fa fa-plus-circle fa-stack-1x" style="left: 10px; top: -7px;"></span>
            </span>
        </a>
    </span>
    <% } if (c.hasPermission(getUser(), AdminPermission.class)) {%>
    <span class="folder-menu-button-icon" style="margin-left: 2px">
        <a href="<%=createFolderURL%>" title="New Subfolder">
            <span class="fa-stack fa-1x labkey-fa-stacked-wrapper">
                <span class="fa fa-folder-o fa-stack-2x labkey-main-menu-icon" alt="New Subfolder"></span>
                <span class="fa fa-plus-circle fa-stack-1x" style="left: 10px; top: -7px;"></span>
            </span>
        </a>
    </span>
    <span class="folder-menu-button-icon" style="margin-left: 6px;">
        <a href="<%=folderManagementURL%>" title="Folder Management">
            <span class="fa fa-gear" alt="Folder Management"></span>
        </a>
    </span>
    <% } if (!c.isRoot()) { %>
    <span class="folder-menu-button-icon" style="margin-left: -2px">
            <a id="permalink_vis" href="#" title="Permalink Page">
                <span class="fa fa-link" alt="Permalink Page"></span>
            </a>
        </span>
    <% } %>
</div>
<script type="text/javascript">
    +function($) {
        'use strict';

        var toggle = function() {
            $(this).parent().toggleClass('expand-folder').toggleClass('collapse-folder');
        };

        $(function() {
            var menu = $('.folder-tree');
            var s = menu.find('.nav-tree-selected');
            if (s && s.length > 0) {
                menu.animate({ scrollTop: s.offset().top - parseInt(menu.css('maxHeight'), 10) }, 0);
            }

            menu.on('click', '.clbl span.marked', toggle);

            var p = document.getElementById('permalink');
            var pvis = document.getElementById('permalink_vis');
            if (p && pvis) {
                pvis.href = p.href;
            }
        });
    }(jQuery);
</script>