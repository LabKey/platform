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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    // Create Project URL
    ActionURL createProjectURL = PageFlowUtil.urlProvider(AdminUrls.class).getCreateProjectURL(getContainer().getStartURL(getUser()));

    NavTree projects = ContainerManager.getProjectList(getViewContext(), false);
    Container currentProject = getContainer().getProject();
    String projectName = null;
    if (null != currentProject)
        projectName = currentProject.getTitle();

    // Based on the number of projects calculate a rectangle with a column number of MAX_COLS
    int MAX_COLS = 4;

    int numProjects = projects.getChildCount();
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
    List<NavTree> children = projects.getChildren();
    Set<Integer> duplicates = new HashSet<>();

    if (projects.hasChildren())
    {
%>
<div class="project-nav">
    <ul style="width: <%=(11.333*cols)+(cols*2.188)+3.8%>em;">
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

                if (idx < children.size() && !duplicates.contains(Integer.valueOf(idx)))
                {
                    duplicates.add(Integer.valueOf(idx));
                    NavTree p = children.get(idx);
                    String text = p.getText();
                    String highlight = (text.equalsIgnoreCase(projectName) ? "style=\"font-weight: bold; font-style: italic;\"" : "");

                    %><li>
                        <%
                            if (null != p.getHref())
                            {
                        %>
                        <a title="<%=h(text)%>" href="<%=h(p.getHref())%>" <%=text(highlight)%>><%=h(text)%></a>
                        <%
                            }
                            else
                            {
                        %>
                        <span><%=h(text)%></span>
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
        if (getUser().hasRootAdminPermission())
        {
%>
<div class="project-menu-buttons">
    <span class="button-icon"><a href="<%=createProjectURL%>" title="New Project"><img src="<%=getContextPath()%>/_images/icon_projects_add.png" alt="New Project" /></a></span>
</div>
<%
        }
        else
        {
%>
<div style="padding-top: 10px"></div>
<%
        }
    }
%>