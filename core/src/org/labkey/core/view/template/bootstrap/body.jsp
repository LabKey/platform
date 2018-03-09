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
<%@ page import="org.labkey.core.view.template.bootstrap.PageTemplate" %>
<%@ page import="org.labkey.api.view.template.AppBar" %>
<%@ page import="org.labkey.api.data.Container" %>
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
    AppBar appBar = pageConfig.getAppBar();

    boolean hasContainerTabs = appBar != null && appBar.getSubContainerTabs() != null && appBar.getSubContainerTabs().size() > 0;

    boolean showRight = me.getView(WebPartFactory.LOCATION_RIGHT) instanceof HttpView
            && ((HttpView) me.getView(WebPartFactory.LOCATION_RIGHT)).isVisible();
%>
<div class="container">
    <div class="row">
        <div class="col-md-12">
            <%= text(PageTemplate.renderSiteMessages(pageConfig)) %>
            <% if (pageConfig.showHeader() != PageConfig.TrueFalse.False && null != pageConfig.getAppBar())
               {
                   String trail = renderTrail(pageConfig.getAppBar().getNavTrail());
                   String pageTitle = pageConfig.getAppBar().getPageTitle();

                   if (pageTitle == null)
                   {
                       pageTitle = pageConfig.getAppBar().getFolderTitle();

                       // The intent of this is to suppress pageTitle for the project, presumably so we dont double-show this on the page, given the project menu also shows project name?
                       // This should be reviewed, since it creates inconsistent behavior between top-level containers and subfolders.
                       // Perhaps this exception should be limited to the startURL()/getAppBar().getHref() alone?
                       if (pageTitle != null)
                       {
                           Container targetFolder = null;
                           if (getContainer().isProject())
                               targetFolder = getContainer();
                           else if (getContainer().getProject() != null)
                               targetFolder = getContainer().getProject();

                           if (targetFolder != null && (pageTitle.equalsIgnoreCase(targetFolder.getName()) || pageTitle.equalsIgnoreCase(targetFolder.getTitle())))
                               pageTitle = null;
                       }
                   }

                   if (trail != null || pageTitle != null || hasContainerTabs)
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
                <%--For a normal folder, just show the title, unless this is the start page--%>
                <%--For a workbook, always show ParentTitle / Workbook, making it easier for the user to return to the parent --%>
                <% if (getContainer().isWorkbook() || !getActionURL().toString().equals(pageConfig.getAppBar().getHref())) { %>
                    <span class="lk-body-title-folder-outer">
                        <i class="fa fa-folder-o"></i>
                        <%-- Note: pageConfig.getAppBar() returns a URL pointing to the current non-workbook container (i.e. parent if current container is a workbook --%>
                        <a class="lk-body-title-folder" href="<%= h(pageConfig.getAppBar().getHref()) %>"><%= h(getContainer().getSelfOrWorkbookParent().getTitle()) %></a>
                        <% if (getContainer().isWorkbook()) { %>
                         / <a class="lk-body-title-folder" href="<%= h(getContainer().getStartURL(getUser()))%>"><%= h(getContainer().getDisplayTitle()) %></a>
                        <% } %>
                    </span>
                <% } %>
            <% } %>
            <% if (hasContainerTabs) {%>
            <div style="float: right;">
                <select title="subContainerTabs" onchange="window.location = this.options[this.selectedIndex].value;">
                    <%for (NavTree tree : appBar.getSubContainerTabs())
                    {
                        String selectedText = tree.isSelected() ? "selected" : "";
                    %>
                        <option value="<%=h(tree.getHref())%>" <%=h(selectedText)%> ><%=h(tree.getText())%></option>
                    <%}%>
                </select>
            </div>

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