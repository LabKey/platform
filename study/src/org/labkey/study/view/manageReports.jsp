<%
/*
 * Copyright (c) 2007-2011 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.controllers.reports.StudyManageReportsBean" %>
<%@ page import="org.labkey.study.reports.ReportManager" %>
<%@ page import="java.io.Writer" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="org.labkey.study.controllers.reports.ReportsController" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.reports.model.ViewInfo" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<StudyManageReportsBean> me = (JspView<StudyManageReportsBean>) HttpView.currentView();
    StudyManageReportsBean bean = me.getModelBean();

    ViewContext context = HttpView.currentContext();
    Container c = context.getContainer();
    User user = context.getUser();

    // group by query name
    List<ViewInfo> allViews = ReportManager.get().getViews(context, null, null, true, false);
    Map<String, List<ViewInfo>> groups = new TreeMap<String, List<ViewInfo>>();
    for (ViewInfo view : allViews)
    {
        List<ViewInfo> views;
        String queryName = view.getQuery();

        if (groups.containsKey(queryName))
        {
            views = groups.get(queryName);
        }
        else
        {
            views = new ArrayList<ViewInfo>();
            groups.put(queryName, views);
        }
        views.add(view);
    }

    int maxColumns = 3;
    int reportsPerColumn = (allViews.size() + 1) / maxColumns;
    int reportCount = 0;

%>

<labkey:errors/>

<%
    if (bean.isWideView())
    {
        out.print("<table width=\"100%\"><tr><td valign=\"top\">");
        maxColumns--;
    }
    else if (bean.getAdminView())
        out.print("<table>");

    for (Map.Entry<String, List<ViewInfo>> entry : groups.entrySet())
    {
        if (entry.getValue().isEmpty())
            continue;
        if (bean.isWideView() && reportCount >= reportsPerColumn && maxColumns > 0)
        {
            out.print("</td><td valign=\"top\">");
            reportCount = 0;
            maxColumns--;
        }
        startReportSection(out, entry.getKey(), bean);
        for (ViewInfo view : entry.getValue())
        {
            reportCount++;
            if (view.getRunUrl() != null) { %>
                <tr>
                    <td>
                        <%
                        if (view.getIcon() != null)
                        {
                        %>
                        <img src="<%= h(view.getIcon())%>" alt="">
                        <%
                        }
                        %>
                    </td>
                <td><a href="<%=h(view.getRunUrl().getLocalURIString())%>"><%=h(view.getName())%></a></td></tr>
         <% } else { %>
                <tr><td><%=h(view.getName())%></td></tr>
         <% }
        }
        endReportSection(out, bean);
    }

    if (bean.isWideView())
        out.println("</td></tr></table>");
    else if (bean.getAdminView())
        out.println("</table>");

    if (user.isAdministrator() || c.hasPermission(user, AdminPermission.class)) {
%>
        <table>
            <tr><td>&nbsp;</td></tr>
            <tr><td colspan="4">
            <%=textLink("Manage Views", new ActionURL(ReportsController.ManageReportsAction.class, c))%>
            </td></tr>
        </table>
<%
    }
%>


<%!
    void startReportSection(Writer out, String title, StudyManageReportsBean bean) throws Exception
    {
        if (!bean.getAdminView())
        {
            WebPartView.startTitleFrame(out, title, null, "100%", null);
            out.write("<table>");
        }
        else
        {
            out.write("<tr width=\"100%\"><td colspan=\"7\" class=\"labkey-announcement-title\" align=left><span>");
            out.write(h(title));
            out.write("</span></td></tr>");
            out.write("<tr width=\"100%\"><td colspan=\"7\" class=\"labkey-title-area-line\"></td></tr>");
        }
    }

    void endReportSection(Writer out, StudyManageReportsBean bean) throws Exception
    {
        if (!bean.getAdminView())
        {
            out.write("</table>");
            WebPartView.endTitleFrame(out);
        }
    }
%>
