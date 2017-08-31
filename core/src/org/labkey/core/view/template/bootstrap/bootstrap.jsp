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
        // NOTE: If this generated DOM is changed make concurrent change to LABKEY.NavTrail.setTrail
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

    boolean showRight = me.getView(WebPartFactory.LOCATION_RIGHT) instanceof HttpView
            && ((HttpView) me.getView(WebPartFactory.LOCATION_RIGHT)).isVisible();
%>
<div class="container">
    <div class="row">
        <div class="col-md-12">
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
        </div>
    </div>
    <div class="row">
        <div class="col-md-12 lk-body-title">
            <% /* NOTE: If this generated DOM is changed make concurrent change to LABKEY.NavTrail.setTrail */ %>
            <% if (trail != null) { %><%= text(trail) %><% } %>
            <% if (pageTitle != null) { %>
                <h3 style="display: inline-block;">
                    <%= h(pageTitle) %>
                </h3>
                <% if (!getActionURL().equals(getContainer().getStartURL(getUser()))) { %>
                    <a class="lk-body-title-folder" href="<%= h(pageConfig.getAppBar().getHref()) %>">
                        <i class="fa fa-folder-o"></i><%= h(getContainer().getName()) %>
                    </a>
                <% } %>
            <% } %>
            <%     }
                }
            %>
        </div>
    </div>
    <div class="row content-row">
        <div class="content-left">
            <% me.include(me.getBody(), out); %>
        </div>
        <% if (showRight) { %>
        <div class="content-right">
            <div class="content-right-spacer"></div>
            <% me.include(me.getView(WebPartFactory.LOCATION_RIGHT), out); %>
        </div>
        <% } %>
    </div>
</div>