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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.project.NavigationFolderForm" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("internal/jQuery/jquery.menu-aim.js");
        if (!PageFlowUtil.useExperimentalCoreUI()) // see core/_navigation.scss
            dependencies.add("nav/betanav.css");
    }
%>
<%
    Container c = getContainer();
    ActionURL startURL = c.getStartURL(getUser()); // 30975: Return to startURL due to async view context

    JspView<NavigationFolderForm> me = (JspView<NavigationFolderForm>) HttpView.currentView();
    NavigationFolderForm form = me.getModelBean();

    ActionURL createProjectURL = PageFlowUtil.urlProvider(AdminUrls.class).getCreateProjectURL(null);
    createProjectURL.addParameter(ActionURL.Param.returnUrl, startURL.toString());

    ActionURL createFolderURL = PageFlowUtil.urlProvider(AdminUrls.class).getCreateFolderURL(c, null);
    createFolderURL.addParameter(ActionURL.Param.returnUrl, startURL.toString());

    ActionURL folderManagementURL = PageFlowUtil.urlProvider(AdminUrls.class).getManageFoldersURL(c);

    List<Container> navTrailContainers = ContainerManager.containersToRootList(c);
%>
<div class="beta-nav">
    <div class="beta-folder-tree">
        <div class="beta-nav-trail">
            <%
                for (Container curr : navTrailContainers) {
            %>
            <a href="<%=h(curr.getStartURL(getUser()))%>"> <%=h(curr.getName())%></a> /
            <%
                }
            %>
        </div>
        <div class="folder-nav">
            <% me.include(form.getFolderMenu(), out); %>
        </div>
    </div>
</div>
<div class="beta-nav-buttons">
<%
    if (getUser().hasRootAdminPermission())
    {
%>
        <span class="beta-nav-button-icon">
            <a href="<%=createProjectURL%>" title="New Project">
                <span class="fa-stack fa-1x labkey-fa-stacked-wrapper">
                    <span class="fa fa-folder-open-o fa-stack-2x labkey-main-menu-icon" alt="New Project"></span>
                    <span class="fa fa-plus-circle fa-stack-1x" style="left: 10px; top: -7px;"></span>
                </span>
            </a>
        </span>
<%
    }

    if (c.hasPermission(getUser(), AdminPermission.class))
    {
%>
        <span class="beta-nav-button-icon">
            <a href="<%=createFolderURL%>" title="New Subfolder">
                <span class="fa-stack fa-1x labkey-fa-stacked-wrapper">
                    <span class="fa fa-folder-o fa-stack-2x labkey-main-menu-icon" alt="New Subfolder"></span>
                    <span class="fa fa-plus-circle fa-stack-1x" style="left: 10px; top: -7px;"></span>
                </span>
            </a>
        </span>
        &nbsp;&nbsp;
        <span class="beta-nav-button-icon">
            <a href="<%=folderManagementURL%>" title="Folder Management">
                <span class="fa fa-gear" alt="Folder Management"></span>
            </a>
        </span>
<%
    }

    if (!c.isRoot())
    {
%>
        <span class="beta-nav-button-icon">
            <a id="permalink_vis" href="#" title="Permalink Page">
                <span class="fa fa-link" alt="Permalink Page"></span>
            </a>
        </span>
<%
    }
%>
</div>
<script type="text/javascript">
    +function($) {
        'use strict';

        var toggle = function() {
            var folderListItem = $(this).parent();

            if (folderListItem && folderListItem.length) {
                folderListItem.toggleClass('expand-folder');
                folderListItem.toggleClass('collapse-folder');
            }
        };

        $(function() {
            var folderMenu = $('.beta-nav');
            var selected = folderMenu.find('.nav-tree-selected');
            selected[0].scrollIntoView(false);

            folderMenu.on('click', '.clbl span.marked', toggle);

            var p = document.getElementById('permalink');
            var pvis = document.getElementById('permalink_vis');
            if (p && pvis) {
                pvis.href = p.href;
            }
        });
    }(jQuery);
</script>
