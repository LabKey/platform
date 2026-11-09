<%
/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.PopupMenuView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.WebPartFactory" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.core.view.template.bootstrap.PageTemplate.NavigationModel" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    private String getSafeName(Portal.WebPart menu)
    {
        return (menu.getName() + menu.getIndex()).replaceAll("\\s+", "");
    }
%>
<%
    JspView<NavigationModel> me = HttpView.currentView();
    NavigationModel model = me.getModelBean();
    ViewContext context = getViewContext();
    Container c = getContainer();
    List<NavTree> tabs = model.getTabs();
    LinkedHashSet<Pair<String, Portal.WebPart>> menus = new LinkedHashSet<>();
    boolean isPageAdminMode = PageFlowUtil.isPageAdminMode(getViewContext()) && !c.isRoot();

    // process custom menus
    for (Portal.WebPart menu : model.getCustomMenus())
    {
        String caption = menu.getName();

        try
        {
            WebPartFactory factory = Portal.getPortalPart(menu.getName());
            if (null == factory)
                continue;
            WebPartView<?> view = factory.getWebPartView(context, menu);
            if (view.isEmpty())
                continue;
            if (null != view.getTitle())
                caption = view.getTitle();
        }
        catch (Exception e)
        {
            // Use the part name...
        }

        menus.add(new Pair<>(caption, menu));
    }
%>
<nav class="labkey-page-nav">
    <div class="container">
        <div id="nav_dropdowns" class="navbar-header">
            <ul class="nav">
<%
    if (context.isShowFolders())
    {
%>
                <li class="dropdown dropdown-folder-nav" data-webpart="FolderNav" data-name="FolderNav">
                    <a data-target="#" class="dropdown-toggle" data-toggle="dropdown">
                        <%=h(model.getProjectTitle().isEmpty() ? "Site Navigation" : model.getProjectTitle())%><i style="margin-left: 5px" class="fa fa-chevron-down"></i>
                    </a>
                    <ul class="dropdown-menu"></ul>
                </li>
<%
    }

    for (Pair<String, Portal.WebPart> pair : menus)
    {
%>
                <li class="dropdown" data-webpart="<%=unsafe(getSafeName(pair.second))%>" data-name="<%=unsafe(pair.second.getName())%>">
                    <a data-target="#" class="dropdown-toggle" data-toggle="dropdown"><%=h(pair.first)%><i style="margin-left: 5px" class="fa fa-chevron-down"></i></a>
                    <ul class="dropdown-menu lk-custom-dropdown-menu"></ul>
                </li>
<%
    }
%>
            </ul>
        </div>
        <div id="nav_tabs" class="lk-nav-tabs-ct">
            <ul id="lk-nav-tabs-separate" class="nav lk-nav-tabs pull-right <%=h(isPageAdminMode ? "lk-nav-tabs-admin" : "")%>" style="opacity: 0;">
                <%
                    if (tabs.size() > 1 || isPageAdminMode)
                    {
                        for (NavTree tab : tabs)
                        {
                            boolean show = isPageAdminMode || !tab.isDisabled();

                            if (show && null != tab.getText() && !tab.getText().isEmpty())
                            {
                %>
                <li class="<%= unsafe(tab.isSelected() ? "active" : "") %>">
                    <a href="<%=h(tab.getHref())%>" id="<%=h(tab.getText().replace(" ", ""))%>Tab"<%= unsafe(tab.isSelected() ? " aria-current=\"page\"" : "") %>>
                        <% if (tab.isDisabled()) { %>
                        <i class="fa fa-eye-slash"></i>
                        <% } %>
                        <%=h(tab.getText())%>
                    </a>
                    <% if (isPageAdminMode && tab.getChildren().size() == 1) { %>
                    <div class="dropdown lk-menu-drop skip-align">
                        <a data-target="#" class="dropdown-toggle" data-toggle="dropdown">
                            <i class="fa fa-caret-down"></i>
                        </a>
                        <ul class="dropdown-menu dropdown-menu-right">
                            <% PopupMenuView.renderTree(tab.getChildren().getFirst(), out); %>
                        </ul>
                    </div>
                    <% } %>
                </li>
                <%
                            }
                        }
                    }

                    if (isPageAdminMode && c.getFolderType() != FolderType.NONE)
                    {
                        HtmlString plus = HtmlString.unsafe("<i class=\"fa fa-plus\" style=\"font-size: 12px;\"></i>");
                %>
                <li>
                    <%=simpleLink(plus).id("addTab").title("Add New Tab").onClick("LABKEY.Portal.addTab();")%>
                </li>
                <%
                    }
                %>
            </ul>
            <ul id="lk-nav-tabs-collapsed" class="nav lk-nav-tabs pull-right" style="display: none;">
                <%
                    // Generate selected tab
                    if (tabs.size() > 1 || isPageAdminMode)
                    {
                        for (NavTree tab : tabs)
                        {
                            if (null != tab.getText() && !tab.getText().isEmpty())
                            {
                                if (tab.isSelected())
                                {
                %>
                <li class="dropdown active">
                    <a data-target="#" class="dropdown-toggle" data-toggle="dropdown">
                        <%=h(tab.getText())%>&nbsp;
                        <span class="fa fa-chevron-down" style="font-size: 12px;"></span>
                    </a>
                    <%
                                }
                            }
                        }
                    %>
                    <ul class="dropdown-menu dropdown-menu-right">
                        <%
                            // Generate all other tabs
                            for (NavTree tab : tabs)
                            {
                                boolean show = isPageAdminMode || !tab.isDisabled();

                                if (show && null != tab.getText() && !tab.getText().isEmpty())
                                {
                        %>
                        <li>
                            <a href="<%=h(tab.getHref())%>"<%= unsafe(tab.isSelected() ? " aria-current=\"page\"" : "") %>>
                                <% if (tab.isSelected())
                                   {
                                        %><b><%=h(tab.getText())%></b><%
                                   }
                                   else
                                   {
                                        %><%=h(tab.getText())%><%
                                   }
                                %>
                            </a>
                        </li>
                        <%
                                }
                            }
                        %>
                    </ul>
                </li>
                <%
                    }
                %>
            </ul>
        </div>
    </div>
</nav>
<% if (!model.getCustomMenus().isEmpty()) { %>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    <%--  note __menus must be a var, not a let or const  --%>
    var __menus = {};
    LABKEY.Utils.onReady(function() {
        <%
            for (Portal.WebPart menu : model.getCustomMenus())
            {
                String safeName = getSafeName(menu);
                %>__menus[<%=q(safeName)%>] = {};<%
                    for (Map.Entry<String,String> entry : menu.getPropertyMap().entrySet())
                    {
                        %>__menus[<%=q(safeName)%>][<%=q(entry.getKey())%>] = <%=q(entry.getValue())%>;<%
                    }
            }
        %>
    });
</script>
<% } %>
