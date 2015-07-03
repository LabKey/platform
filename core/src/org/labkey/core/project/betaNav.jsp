<%
/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.core.project.FolderNavigationForm" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("internal/jQuery"));
        resources.add(ClientDependency.fromPath("internal/jQuery/jquery.menu-aim.js"));
        resources.add(ClientDependency.fromPath("Ext4"));
        resources.add(ClientDependency.fromPath("nav/betanav.css"));
        return resources;
    }
%>
<%
    JspView<FolderNavigationForm> me = (JspView<FolderNavigationForm>) HttpView.currentView();
    FolderNavigationForm form = me.getModelBean();

    // Create Project URL
    ActionURL createProjectURL = PageFlowUtil.urlProvider(AdminUrls.class).getCreateProjectURL(me.getViewContext().getActionURL());

    NavTree projects = ContainerManager.getProjectList(getViewContext());
%>
<div class="beta-nav">
    <div class="list-content">

        <div class="project-list-container iScroll">
            <p class="title">Projects</p>
            <div class="project-list" style="max-height: 375px; overflow-x: hidden;">
                <ul class="dropdown-menu">
                <%
                    for (NavTree p : projects.getChildren())
                    {
                        String projectTitle = p.getText();
                        if (projectTitle.length() > 36) {
                            projectTitle = projectTitle.substring(0, 31) + "...";
                        }
                %>
                    <li data-submenu-id = "<%=h(projectTitle)%>"><a data-field="<%=h(p.getText())%>" title="<%=h(projectTitle)%>" href="<%=h(p.getHref())%>"><%=h(projectTitle)%></a></li>
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
<div class="nav-buttons">
    <span class="button-icon"><a href="<%=createProjectURL%>" title="New Project"><img src="<%=getContextPath()%>/_images/icon_projects_add.png" alt="New Project" /></a></span>
</div>
<script type="text/javascript">
    Ext4.onReady(function() {
        var prepFolderNav = function() {
            var toggle = function(selector) {
                var p = selector.parent();
                if (p) {
                    var collapse = true;
                    if (p.hasCls('expand-folder')) {
                        // collapse the tree
                        p.replaceCls('expand-folder', 'collapse-folder');
                    }
                    else {
                        // expand the tree
                        p.replaceCls('collapse-folder', 'expand-folder');
                        collapse = false;
                    }

                    var a = p.child('a');
                    if (a) {
                        var url = a.getAttribute('expandurl');
                        if (url) {
                            url += (collapse ? '&collapse=true' : '');
                            Ext4.Ajax.request({ url : url });
                        }
                    }
                }
            };

            // nodes - the set of +/- icons
            var nodes = Ext4.DomQuery.select('.folder-nav .clbl span.marked');
            for (var n=0; n < nodes.length; n++) {
                Ext4.get(nodes[n]).on('click', function(x,node) { toggle(Ext4.get(node)); });
            }

            // scrollIntoView
            var siv = function(t, ct) {
                ct = Ext4.getDom(ct) || Ext4.getBody().dom;
                var el = t.dom,
                        offsets = t.getOffsetsTo(ct),
                // el's box
                        top = offsets[1] + ct.scrollTop,
                        bottom = top + el.offsetHeight,
                // ct's box
                        ctClientHeight = ct.clientHeight,
                        ctScrollTop = parseInt(ct.scrollTop, 10),
                        ctBottom = ctScrollTop + ctClientHeight,
                        ctHalf = (ctBottom / 2);
                if (bottom > ctBottom) { // outside the visible area
                    ct.scrollTop = bottom - (ctClientHeight / 2);
                }
                else if (bottom > ctHalf) { // centering
                    ct.scrollTop = bottom - ctHalf;
                }
                // corrects IE, other browsers will ignore
                ct.scrollTop = ct.scrollTop;

                return this;
            };

            // Folder Scrolling
            var t = Ext4.get('folder-target');
            if (t) { siv(t, Ext4.get('folder-tree-wrap')); }
        };
        var cache = [];
        var requestProject = function(el) {
            var link = Ext4.get(el);
            if(link)
            {
                var name = link.getAttribute('data-field');

                if (name)
                {
                    name = decodeURIComponent(name);
                    //ajax call to request folder navigation
                    if (cache[name])
                    {
                        //using cached response
                        Ext4.get('folder-tree-wrap').update(cache[name]);
                    }
                    else
                    {
                        Ext4.Ajax.request({
                            url: LABKEY.ActionURL.buildURL('project', 'getFolderNavigation', null, {projectName: name}),
                            success: function (response)
                            {
                                var data = Ext4.decode(response.responseText);
                                // add result to the cache
                                cache[name] = data.html;
                                Ext4.get('folder-tree-wrap').update(data.html);
                                prepFolderNav();
                            }
                        });
                    }
                }
            }

            return false;
        };

        // TODO: Center focus on active project
        prepFolderNav();
        var $ = jQuery;
        var $menu = $('.dropdown-menu');

        // requests a submenu for the curent directory, takes current row as a parameter
        function activateFolder(row) {
            var $row = $(row),
                    submenuId = $row.data("submenuId");
            // gets the new folder on the list
            if($(row).children('a')[0])
            {
                requestProject($(row).children('a')[0]);
            }
            $row.find("a").addClass("maintainHover");
        }
        function deactivateFolder(row) {
            var $row = $(row),
                    submenuId = $row.data("submenuId");
            $row.find("a").removeClass("maintainHover");
        }
        $menu.menuAim({
            // runs the active row and creates the delay function and triangle
            activate: activateFolder,
            deactivate: deactivateFolder
        });
    });
</script>