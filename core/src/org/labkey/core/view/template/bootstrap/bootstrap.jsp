<%--
/*
 * Copyright (c) 2016 LabKey Corporation
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
--%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebPartFactory" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="java.io.Writer" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    private void renderTrail(List<NavTree> trees, Writer out) throws Exception
    {
        if (trees == null)
            return;
        else if (trees.isEmpty())
            return;

        out.write("<ol class=\"breadcrumb\" style=\"background-color: transparent; padding: 0; margin-bottom: 8px;\">");

        for (NavTree child : trees)
        {
            out.write("<li>");
            if (child.getHref() != null)
                out.write("<a href=\"" + h(child.getHref()) + "\">" + h(child.getText()) + "</a>");
            else
                out.write(h(child.getText()));
            out.write("</li>");
        }

        out.write("</ol>");
    }
%>
<%
    HttpView me = HttpView.currentView();
    PageConfig pageConfig = (PageConfig) me.getModelBean();

    boolean showRight = me.getView(WebPartFactory.LOCATION_RIGHT) instanceof HttpView && ((HttpView) me.getView(WebPartFactory.LOCATION_RIGHT)).isVisible();
    ActionURL url = new ActionURL(AdminController.ExperimentalFeaturesAction.class, ContainerManager.getRoot());
    // TODO: Remove all inline styles
%>
<div class="container" style="padding: 20px 0 0 0;">
    <div class="alert alert-warning" role="alert" style="margin: 0 15px 15px;">
        <strong>Under construction!</strong>
        This layout is under development. <a href="<%=h(url.getLocalURIString())%>" class="alert-link">Turn it off here</a> by disabling the "Core UI Migration" feature.
    </div>
    <% if (pageConfig.showHeader() != PageConfig.TrueFalse.False) { %>
    <div class="col-md-12" style="margin-bottom: 20px;">
        <%
            if (null != pageConfig.getAppBar())
            {
                renderTrail(pageConfig.getAppBar().getNavTrail(), out);
        %>
            <% if (pageConfig.getAppBar().getPageTitle() != null) { %>
            <h3 style="margin: 0;"><%= h(pageConfig.getAppBar().getPageTitle()) %></h3>
            <% } else if (pageConfig.getAppBar().getFolderTitle() != null) { %>
            <h3 style="margin: 0;"><%= h(pageConfig.getAppBar().getFolderTitle()) %></h3>
            <% }
            }%>
    </div>
    <% } %>
    <div class="<%= h(showRight ? "col-md-9" : "col-md-12" ) %>">
        <% me.include(me.getBody(), out); %>
    </div>

    <% if (showRight) { %>
    <div class="col-md-3">
        <% me.include(me.getView(WebPartFactory.LOCATION_RIGHT), out); %>
    </div>
    <% } %>
</div>