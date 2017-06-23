<%--
/*
 * Copyright (c) 2017 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.PageConfig" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="java.util.List" %>
<%@ page import="org.jetbrains.annotations.Nullable" %>
<%@ page import="org.labkey.core.view.template.bootstrap.BootstrapTemplate" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Nullable
    private String renderTrail(List<NavTree> trees)
    {
        if (trees == null || trees.isEmpty())
            return null;

        String trail = "<ol class=\"breadcrumb\">";

        for (NavTree child : trees)
        {
            trail += "<li>";
            if (child.getHref() != null)
                trail += "<a href=\"" + h(child.getHref()) + "\">" + h(child.getText()) + "</a>";
            else
                trail += h(child.getText());
            trail += "</li>";
        }

        trail += "</ol>";
        return trail;
    }
%>
<%
    HttpView me = HttpView.currentView();
    PageConfig pageConfig = (PageConfig) me.getModelBean();

    boolean showRight = me.getView(WebPartFactory.LOCATION_RIGHT) instanceof HttpView && ((HttpView) me.getView(WebPartFactory.LOCATION_RIGHT)).isVisible();
%>
<div class="container lk-body-ct">
    <%= text(BootstrapTemplate.renderSiteMessages(pageConfig)) %>
    <% if (pageConfig.showHeader() != PageConfig.TrueFalse.False && null != pageConfig.getAppBar())
       {
           String trail = renderTrail(pageConfig.getAppBar().getNavTrail());
           String pageTitle = pageConfig.getAppBar().getPageTitle();

           if (pageTitle == null)
           {
               pageTitle = pageConfig.getAppBar().getFolderTitle();

               if (pageTitle != null)
               {
                   String folder = null;
                   if (getContainer().isProject())
                       folder = getContainer().getName();
                   else if (getContainer().getProject() != null)
                       folder = getContainer().getProject().getName();
                   if (folder != null && pageTitle.equalsIgnoreCase(folder))
                       pageTitle = null;
               }
           }

           if (trail != null || pageTitle != null)
           {
    %>
    <div class="col-md-12 lk-body-title">
        <% if (trail != null) { %><%= text(trail) %><% } %>
        <% if (pageTitle != null) { %><h3><%= h(pageTitle)%></h3><% } %>
    </div>
    <%
            }
       }
    %>
    <div class="<%= h(showRight ? "col-md-9" : "col-md-12" ) %>">
        <% me.include(me.getBody(), out); %>
    </div>
    <% if (showRight) { %>
    <div class="col-md-3">
        <% me.include(me.getView(WebPartFactory.LOCATION_RIGHT), out); %>
    </div>
    <% } %>
</div>