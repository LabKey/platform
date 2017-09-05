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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.project.FolderNavigationForm" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
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
    JspView<FolderNavigationForm> me = (JspView<FolderNavigationForm>) HttpView.currentView();
    FolderNavigationForm form = me.getModelBean();

    ActionURL createProjectURL = PageFlowUtil.urlProvider(AdminUrls.class).getCreateProjectURL(me.getViewContext().getActionURL());
    ActionURL createFolderURL = PageFlowUtil.urlProvider(AdminUrls.class).getCreateFolderURL(getContainer(), me.getViewContext().getActionURL());
    ActionURL folderManagementURL = PageFlowUtil.urlProvider(AdminUrls.class).getManageFoldersURL(getContainer());

    NavTree projects = ContainerManager.getProjectList(getViewContext(), false);
%>
<div class="beta-nav">
    <div class="list-content">
        <div class="project-list-container iScroll">
            <p class="title">Projects</p>
            <div class="project-list" style="max-height: 375px; overflow-x: hidden;">
                <ul>
                <%
                    for (NavTree p : projects.getChildren())
                    {
                        String projectTitle = p.getText();
                        String projectDisplay = projectTitle;
                        if (projectDisplay.length() > 28) {
                            projectDisplay = projectDisplay.substring(0, 28) + "...";
                        }
                %>
                    <li data-submenu-id="<%=h(projectTitle.toLowerCase())%>">
                        <a data-field="<%=h(projectTitle)%>" title="<%=h(projectTitle)%>" href="<%=h(p.getHref())%>"><%=h(projectDisplay)%></a>
                    </li>
                <%
                    }
                %>
                </ul>
            </div>
        </div>
        <div class="folder-list-container">
            <p class="title">Project Folders & Pages</p>
            <div id="folder-tree-wrap" class="beta-folder-tree">
                <% me.include(form.getFolderMenu(), out); %>
            </div>
        </div>
    </div>
</div>
<div class="beta-nav-buttons">
<%
    if (getUser().hasRootAdminPermission())
    {
%>
        <span class="button-icon">
            <a href="<%=createProjectURL%>" title="New Project">
                <img src="<%=getContextPath()%>/_images/icon_projects_add.png" alt="New Project" />
            </a>
        </span>
<%
    }

    if (getContainer().hasPermission(getUser(), AdminPermission.class))
    {
%>
        <span class="beta-nav-button-icon">
            <a href="<%=createFolderURL%>" title="New Subfolder">
                <span class="fa-stack fa-1x labkey-fa-stacked-wrapper">
                    <span class="fa fa-folder-o fa-stack-2x labkey-main-menu-icon" alt="New Subfolder"></span>
                    <span class="fa fa-plus-circle fa-stack-1x"></span>
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

    if (!getContainer().isRoot())
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

        var cache = [];

        var toggle = function() {
            var p = $(this).parent();
            if (p && p.length) {
                var collapse = true;
                if (p.hasClass('expand-folder')) {
                    // collapse the tree
                    p.removeClass('expand-folder').addClass('collapse-folder');
                }
                else {
                    // expand the tree
                    p.removeClass('collapse-folder').addClass('expand-folder');
                    collapse = false;
                }

                p.children('a').each(function() {
                    LABKEY.Utils.notifyExpandCollapse($(this).attr('expandurl'), collapse);
                });
            }
        };

        var requestProject = function(el) {
            var link = $(el);
            if (link) {
                var name = link.data('field');

                if (name) {
                    name = decodeURIComponent(name);
                    if (cache[name]) {
                        $('#folder-tree-wrap').html(cache[name]);
                    }
                    else {
                        LABKEY.Ajax.request({
                            url: LABKEY.ActionURL.buildURL('project', 'getFolderNavigation', null, {projectName: name}),
                            success: function (response) {
                                var data = JSON.parse(response.responseText);
                                // add result to the cache
                                cache[name] = data.html;
                                $('#folder-tree-wrap').html(data.html);
                            }
                        });
                    }
                }
            }

            return false;
        };

        $(function() {
            $('.beta-nav').on('click', '.clbl span.marked', toggle);

            var projectList = $('.beta-nav .project-list');
            projectList.find('ul').menuAim({
                activate: function(row) {
                    var $row = $(row);
                    $row.addClass('last-active');

                    var links = $(row).children('a');
                    if (links.length) {
                        requestProject(links[0]);
                    }
                },
                deactivate: function(row) {
                    $(row).removeClass('last-active');
                }
            });

            <% if (getContainer().getProject() != null && !getContainer().isRoot()) { %>
                var l = projectList.find('li[data-submenu-id="<%=h(getContainer().getProject().getName().toLowerCase())%>"]');
                if (l.length) {
                    l.trigger('mouseenter');

                    // scroll the selected project into view
                    var projectListBottom = projectList.offset().top + projectList.height();
                    if (l.offset().top > projectListBottom) {
                        projectList.scrollTop(l.offset().top - projectListBottom + 50);
                    }
                }

                // scroll the selected folder into view
                var folderList = $('.beta-nav .beta-folder-tree');
                var folderListBottom = folderList.offset().top + folderList.height();
                var s = folderList.find('.nav-tree-selected');
                if (s.offset().top > folderListBottom) {
                    folderList.scrollTop(s.offset().top - folderListBottom + 50);
                }
            <% } %>

            var p = document.getElementById('permalink');
            var pvis = document.getElementById('permalink_vis');
            if (p && pvis) {
                pvis.href = p.href;
            }
        });
    }(jQuery);
</script>
