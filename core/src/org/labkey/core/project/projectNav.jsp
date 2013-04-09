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
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    int webPartId = me.getModelBean().getRowId();
    JSONObject jsonProps = new JSONObject(me.getModelBean().getPropertyMap());
    String renderTarget = "project-" + me.getModelBean().getIndex();
    ViewContext ctx = me.getViewContext();
    boolean isAdmin = ctx.getUser().isAdministrator();
    boolean hasPermission;

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

    // Based on the number of projects calculate a rectangle with a maximum width of 4.
    int rowsPerCol = projects.getChildList().size() / 4;
    int cols = 0;

    // TODO: Adjust for remainder due to integer division

    if (rowsPerCol != 0 && rowsPerCol < 4)
    {
        cols = rowsPerCol;
        rowsPerCol = projects.getChildList().size() / cols;
    }

    // Based on how these are displayed we have to walk to list/array by offset in order to display
    // the proper order to the user
    int row = 0; int col = 0; int c; int r;
    List<NavTree> children = projects.getChildList();

    if (projects.hasChildren())
    {
%>
<style type="text/css">

    #projectBar_menu {
        padding: 10px;
    }

    .project-nav {
        max-height: 350px;
        min-height: 75px;
        min-width: 280px;
        max-width: 800px;
        white-space: nowrap;
    }

    .project-nav ul {
        list-style: none;
        width: <%=10*cols%>em;
        padding: 0;
    }

    .project-nav ul li {
        float: left;
        width: 10em;
        height: 25px;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
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
                NavTree p = children.get(idx);

                %><li>
                    <a title="<%=p.getText()%>" href="<%=p.getHref()%>"><%=p.getText()%></a>
                </li><%
            }
        }
%>
    </ul>
</div>
<div><%=generateButton("Create Project", "#")%></div>
<%
    }
%>