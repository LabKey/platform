<%
/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.views.DataViewInfo" %>
<%@ page import="org.labkey.api.data.views.DataViewProvider" %>
<%@ page import="org.labkey.api.data.views.DataViewService" %>
<%@ page import="org.labkey.api.reports.model.ViewCategory" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.controllers.reports.StudyManageReportsBean" %>
<%@ page import="java.io.Writer" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    JspView<StudyManageReportsBean> me = (JspView<StudyManageReportsBean>) HttpView.currentView();
    StudyManageReportsBean bean = me.getModelBean();

    ViewContext context = getViewContext();
    Container c = getContainer();
    User user = getUser();
    List<DataViewInfo> allViews = new ArrayList<>();

    // group by category name
    for (DataViewProvider.Type type : DataViewService.get().getDataTypes(getContainer(), getUser()))
    {
        String typeName = type.getName();
        if (typeName.equalsIgnoreCase("reports") || typeName.equalsIgnoreCase("queries"))
        {
            allViews.addAll(DataViewService.get().getViews(context, Collections.singletonList(type)));
        }
    }

    Map<String, List<DataViewInfo>> groups = new TreeMap<>();
    for (DataViewInfo view : allViews)
    {
        List<DataViewInfo> views;
        ViewCategory category = view.getCategory();

        if (category == null)
        {
            category = new ViewCategory();
            category.setLabel("Uncategorized");
        }

        if (groups.containsKey(category.getLabel()))
        {
            views = groups.get(category.getLabel());
        }
        else
        {
            views = new ArrayList<>();
            groups.put(category.getLabel(), views);
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

    for (Map.Entry<String, List<DataViewInfo>> entry : groups.entrySet())
    {
        if (entry.getValue().isEmpty())
            continue;
        if (bean.isWideView() && reportCount >= reportsPerColumn && maxColumns > 0)
        {
            out.print("</td><td valign=\"top\" style=\"padding-left: 20px;\">");
            reportCount = 0;
            maxColumns--;
        }

        %><labkey:panel title="<%=h(entry.getKey())%>"><%
        startReportSection(out, entry.getKey(), bean);

        for (DataViewInfo view : entry.getValue())
        {
            reportCount++;
            if (view.getRunUrl() != null) { %>
                <tr>
                    <td>
                        <%
                        if (view.getIconCls() != null)
                        {
                        %>
                        <span class="<%= h(view.getIconCls())%>"></span>
                        <%
                        } else if (view.getIconUrl() != null)
                        {
                        %>
                        <img src="<%= h(view.getIconUrl())%>" alt="report_icon" height="16" width="16">
                        <%
                        }
                        %>
                    </td>
                <td><a href="<%=h(view.getRunUrl().getLocalURIString())%>" <%=h(view.getRunTarget() != null ? " target=\"" + view.getRunTarget() + "\"" : "")%>><%=h(view.getName())%></a></td></tr>
         <% } else { %>
                <tr><td><%=h(view.getName())%></td></tr>
         <% }
        }

        endReportSection(out, bean);
        %></labkey:panel><%
    }

    if (bean.isWideView())
        out.println("</td></tr></table>");
    else if (bean.getAdminView())
        out.println("</table>");

    if (user.hasRootAdminPermission() || c.hasPermission(user, AdminPermission.class)) {
%>
        <table>
            <tr><td>&nbsp;</td></tr>
            <tr><td colspan="4">
            <%=textLink("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(c))%>
            </td></tr>
        </table>
<%
    }
%>


<%!
    int countSection = 0;

    void startReportSection(Writer out, String title, StudyManageReportsBean bean) throws Exception
    {
        if (!bean.getAdminView())
        {
            out.write("<table class=\"lk-fields-table\">");
        }
        else
        {
            out.write("<tr width=\"100%\"><td colspan=\"7\" class=\"labkey-announcement-title\" align=left><span>");
            out.write(h(title) + " " + countSection);
            out.write("</span></td></tr>");
            out.write("<tr width=\"100%\"><td colspan=\"7\" class=\"labkey-title-area-line\"></td></tr>");
        }
        countSection++;
    }

    void endReportSection(Writer out, StudyManageReportsBean bean) throws Exception
    {
        if (!bean.getAdminView())
        {
            out.write("</table>");
        }
    }
%>
