<%
/*
 * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    String contextPath = ctx.getContextPath();
    User user = ctx.getUser();

    // Create Project URL
    ActionURL createProjectURL = new ActionURL(AdminController.CreateFolderAction.class, ContainerManager.getRoot());

    List<Container> projects = ContainerManager.getAllChildren(ContainerManager.getRoot(), user);

    // Based on the number of projects calculate a rectangle with a column number of MAX_COLS
    int MAX_COLS = 4;

    int numProjects = projects.size();
    int rowsPerCol = numProjects / MAX_COLS;
    int cols = 1;
    int remainder = numProjects - (rowsPerCol * MAX_COLS);

    if (remainder == 0)
    {
        remainder = -1; // ensure that remainder is not compared
    }

    if (numProjects > 2)
    {
        if (rowsPerCol != 0 && rowsPerCol < MAX_COLS)
        {
            cols = rowsPerCol;
            rowsPerCol = numProjects / cols;
        }
        else if (rowsPerCol == 0)
        {
            // Number of Projects is less than MAX_COLS
            cols = 1;
            rowsPerCol = numProjects;
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
    Set<Integer> duplicates = new HashSet<>();

    if (projects.size() > 0)
    {
%>
<div class="project-nav">
    <ul style="width: <%=(11.333*cols)+(cols*2.188)+1%>em;">
<%
        int idx, last = rowsPerCol-1;
        for (r=0; r < rowsPerCol; r++)
        {
            for (c=0; c < cols; c++)
            {
                if (remainder > 0)
                {
                    if (c == 0)
                    {
                        idx = r;
                    }
                    else if (c <= remainder)
                    {
                        if (c == remainder && (r == last))
                        {
                            continue;
                        }
                        idx = (rowsPerCol * c) + r;
                    }
                    else
                    {
                        idx = (rowsPerCol * c) + r - (c-remainder);
                    }
                }
                else
                {
                    idx = (rowsPerCol * c) + r;
                }

                if (idx < projects.size() && !duplicates.contains(Integer.valueOf(idx)))
                {
                    duplicates.add(Integer.valueOf(idx));
                    Container p = projects.get(idx);

                    %><li>
                        <%
                            if (null != p.getStartURL(user))
                            {
                        %>
                        <a title="<%=h(p.getName())%>" href="<%=PageFlowUtil.filter(p.getStartURL(user))%>"><%=h(p.getName())%></a>
                        <%
                            }
                            else
                            {
                        %>
                        <span><%=h(p.getName())%></span>
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