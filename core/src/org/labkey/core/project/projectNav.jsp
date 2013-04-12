<%
/*
 * Copyright (c) 2011-2013 LabKey Corporation
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
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    int webPartId = me.getModelBean().getRowId();
    JSONObject jsonProps = new JSONObject(me.getModelBean().getPropertyMap());
    String renderTarget = "project-" + me.getModelBean().getIndex();
    ViewContext ctx = me.getViewContext();
    String contextPath = ctx.getContextPath();
    boolean isAdmin = ctx.getUser().isAdministrator();
    boolean hasPermission;

    // Create Project URL
    ActionURL createProjectURL = new ActionURL(AdminController.CreateFolderAction.class, ContainerManager.getRoot());

    Container target;
    String containerPath = (String)jsonProps.get("containerPath");
    if(containerPath == null || "".equals(containerPath))
    {
        hasPermission = true; //this means current container
        target = ctx.getContainer();
    }
    else
    {
        target = ContainerManager.getForPath(containerPath);
        if (target == null)
        {
            // Could also be an entityId
            target = ContainerManager.getForId(containerPath);
        }
        hasPermission = target != null && target.hasPermission(ctx.getUser(), ReadPermission.class);

        //normalize entityId vs path.
        jsonProps.put("containerPath", target.getPath());
    }
    NavTree projects = ContainerManager.getProjectList(ctx);

    // TODO: check ctx.isAdminMode()?

    // Based on the number of projects calculate a rectangle with a column number of MAX_COLS
    int MAX_COLS = 4;

    int numProjects = projects.getChildCount();
    int rowsPerCol = numProjects / MAX_COLS;
    int cols = 1;

    if (rowsPerCol != 0 && rowsPerCol < MAX_COLS)
    {
        cols = rowsPerCol;
        rowsPerCol = numProjects / cols;
    }
    else
        cols = MAX_COLS;

    if (rowsPerCol * cols != numProjects)
    {
        rowsPerCol++;
    }

    // Based on how these are displayed we have to walk to list/array by offset in order to display
    // the proper order to the user
    int c; int r;
    List<NavTree> children = projects.getChildList();

    if (projects.hasChildren())
    {
%>
<style type="text/css">

    #projectBar_menu {
        padding: 10px 10px 0 10px;
    }

    .project-nav {
        max-height: 350px;
        min-height: 75px;
        min-width: 280px;
        max-width: 800px;
        white-space: nowrap;
        display: inline-block;
    }

    .project-nav ul {
        list-style: none;
        width: <%=11.333*cols%>em;
        padding: 0;
    }

    .project-nav ul li {
        float: left;
        width: 11em;
        height: 25px;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
        padding-right: 3px;
    }

    .project-menu-buttons {
        border-top: 1px solid #d5d5d5;
        padding-top: 5px;
        text-align: right;
    }

    .button-icon {
        background-color: #126495;
        opacity: 0.5;
        display: inline-block;
        width: 26px;
        height: 20px;
    }

    .button-icon:hover {
        opacity: 1.0;
    }

    .button-icon a {
        display: inline-block;
        width: 26px;
        height: 20px;
        margin-bottom: 0;
    }

    .button-icon img {
        width: 26px;
        height: 20px;
        margin-bottom: 0;
    }
</style>
<div class="project-nav">
    <ul>
<%
        for (r=0; r < rowsPerCol; r++)
        {
            for (c=0; c < cols; c++)
            {
                int idx = (rowsPerCol*c)+r;

                if (idx < (children.size()))
                {
                    NavTree p = children.get(idx);

                    %><li>
                        <a title="<%=p.getText()%>" href="<%=p.getHref()%>"><%=p.getText()%></a>
                    </li><%
                }
            }
        }
%>
    </ul>

</div>
<div class="project-menu-buttons">
    <span class="button-icon"><a href="<%=createProjectURL%>" title="New Project"><img src="<%=contextPath%>/_images/icon_projects_add.png" alt="New Project" /></a></span>
    <span class="button-icon"><a id="permalink_vis" href="#" title="Permalink Page"><img src="<%=contextPath%>/_images/icon_permalink.png" alt="Permalink Page" /></a></span>
    <script type="text/javascript">
        (function(){
            var p = document.getElementById('permalink');
            var pvis = document.getElementById('permalink_vis');
            if (p && pvis) {
                pvis.href = p.href;
            }
        })();
    </script>
</div>
<%
    }
%>