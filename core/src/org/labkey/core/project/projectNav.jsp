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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    String contextPath = ctx.getContextPath();

    // Create Project URL
    ActionURL createProjectURL = new ActionURL(AdminController.CreateFolderAction.class, ContainerManager.getRoot());

    NavTree projects = ContainerManager.getProjectList(ctx);

    // Based on the number of projects calculate a rectangle with a column number of MAX_COLS
    int MAX_COLS = 4;

    int numProjects = projects.getChildCount();
    int rowsPerCol = numProjects / MAX_COLS;
    int cols = 1;

    if (numProjects > 2)
    {
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
    }
    else
    {
        rowsPerCol = numProjects;
    }

    // Based on how these are displayed we have to walk to list/array by offset in order to display
    // the proper order to the user
    int c; int r;
    List<NavTree> children = projects.getChildList();

    if (projects.hasChildren())
    {
%>
<div class="project-nav">
    <ul style="width: <%=(11.333*cols)+(cols*2.188)+1%>em;">
<%
        for (r=0; r < rowsPerCol; r++)
        {
            for (c=0; c < cols; c++)
            {
                int idx = (rowsPerCol*c)+r;

                if (idx < children.size())
                {
                    NavTree p = children.get(idx);

                    %><li>
                        <%
                            if (null != p.getHref())
                            {
                        %>
                        <a title="<%=h(p.getText())%>" href="<%=PageFlowUtil.filter(p.getHref())%>"><%=h(p.getText())%></a>
                        <%
                            }
                            else
                            {
                        %>
                        <span><%=h(p.getText())%></span>
                        <%
                            }
                        %>
                    </li><%
                }
            }
        }
%>
    </ul>
</div>
<%
        if (getViewContext().getUser().isAdministrator())
        {
%>
<div class="project-menu-buttons">
    <span class="button-icon"><a href="<%=createProjectURL%>" title="New Project"><img src="<%=contextPath%>/_images/icon_projects_add.png" alt="New Project" /></a></span>
</div>
<%
        }
%>
<%
    }
%>