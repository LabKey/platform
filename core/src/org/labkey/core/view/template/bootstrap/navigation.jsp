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
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.settings.TemplateResourceHandler" %>
<%@ page import="org.labkey.core.view.template.bootstrap.PageTemplate.NavigationModel" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.WebPartFactory" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.PopupMenuView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }

    private String getSafeName(Portal.WebPart menu)
    {
        return (menu.getName() + menu.getIndex()).replaceAll("\\s+", "");
    }
%>
<%
    NavigationModel model = (NavigationModel) HttpView.currentView().getModelBean();
    ViewContext context = getViewContext();
    Container c = getContainer();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(c);
    List<NavTree> tabs = model.getTabs();
    LinkedHashSet<Pair<String, Portal.WebPart>> menus = new LinkedHashSet<>();
    boolean isPageAdminMode = PageFlowUtil.isPageAdminMode(getViewContext()) && !c.isRoot();
    boolean isMobile = false; // disabled mobile until experience is refined

    // process custom menus
    for (Portal.WebPart menu : model.getCustomMenus())
    {
        String caption = menu.getName();

        try
        {
            WebPartFactory factory = Portal.getPortalPart(menu.getName());
            if (null == factory)
                continue;
            WebPartView view = factory.getWebPartView(context, menu);
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
                <% if (isMobile) { %>
                <li id="project-mobile" class="dropdown" style="display: none;">
                    <a data-target="#" class="dropdown-toggle" data-toggle="dropdown">
<%
                    if (context.isShowFolders())
                    {
%>
                        <i class="fa fa-folder-open"></i>&nbsp;&nbsp;<%=h(model.getProjectTitle())%>
<%
                    }
                    else if (!menus.isEmpty())
                    {
%>
                        <%=h(menus.iterator().next().first)%>
<%
                    }

                    if ((context.isShowFolders() && !menus.isEmpty()) || (!context.isShowFolders() && menus.size() > 1))
                    {
%>
                        &nbsp;&nbsp;<span>...</span>
<%
                    }
%>
                    </a>
                    <ul class="dropdown-menu">
                        <div class="lk-header-close">
                            <i class="fa fa-close"></i>
                            <a class="brand-logo-mobile" href="<%=h(laf.getLogoHref())%>">
                                <img src="<%=h(TemplateResourceHandler.LOGO_MOBILE.getURL(c))%>" alt="<%=h(laf.getShortName())%>" height="30">
                            </a>
                        </div>
                        <div class="lk-horizontal-menu">
<%
                            if (context.isShowFolders())
                            {
%>
                            <li class="mobiledrop" data-webpart="MenuProjectNav" data-name="MenuProjectNav">
                                <a data-target="#" class="mobiledrop-toggle" data-toggle="mobiledrop">
                                    <i class="fa fa-folder-open"></i>
                                    <span>Projects</span>
                                </a>
                            </li>
<%
                            }
                            
                            for (Pair<String, Portal.WebPart> pair : menus)
                            {
%>
                            <li class="mobiledrop" data-webpart="<%=text(getSafeName(pair.second))%>" data-name="<%=text(pair.second.getName())%>">
                                <a data-target="#" class="mobiledrop-toggle" data-toggle="mobiledrop">
                                    <span><%=h(pair.first)%></span>
                                </a>
                            </li>
<%
                            }
%>
                        </div>
                        <ul class="mobiledrop-menu dropdown-menu lk-dropdown-menu-area"></ul>
                    </ul>
                </li>
                <% } if (context.isShowFolders()) { %>
                <li class="dropdown" data-webpart="FolderNav" data-name="FolderNav">
                    <a data-target="#" class="dropdown-toggle" data-toggle="dropdown">
                        <i class="fa fa-folder-open"></i>&nbsp;<%=h(model.getProjectTitle())%>
                    </a>
                    <ul class="dropdown-menu"></ul>
                </li>
                <% } for (Pair<String, Portal.WebPart> pair : menus) { %>
                <li class="dropdown" data-webpart="<%=text(getSafeName(pair.second))%>" data-name="<%=text(pair.second.getName())%>">
                    <a data-target="#" class="dropdown-toggle" data-toggle="dropdown"><%=h(pair.first)%></a>
                    <ul class="dropdown-menu lk-custom-dropdown-menu"></ul>
                </li>
                <% } %>
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

                            if (show && null != tab.getText() && tab.getText().length() > 0)
                            {
                %>
                <li role="presentation" class="<%= text(tab.isSelected() ? "active" : "") %>">
                    <a href="<%=h(tab.getHref())%>" id="<%=h(tab.getText()).replace(" ", "")%>Tab">
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
                            <% PopupMenuView.renderTree(tab.getChildren().get(0), out); %>
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
                %>
                <li role="presentation">
                    <a href="javascript:LABKEY.Portal.addTab();" id="addTab" title="Add New Tab">
                        <i class="fa fa-plus" style="font-size: 12px;"></i>
                    </a>
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
                            if (null != tab.getText() && tab.getText().length() > 0)
                            {
                                if (tab.isSelected())
                                {
                %>
                <li role="presentation" class="dropdown active">
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

                                if (show && null != tab.getText() && tab.getText().length() > 0)
                                {
                        %>
                        <li>
                            <a href="<%=h(tab.getHref())%>">
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
<% if (model.getCustomMenus().size() > 0) { %>
<script type="application/javascript">
    var __menus = {};
    LABKEY.Utils.onReady(function() {
        <%
            for (Portal.WebPart menu : model.getCustomMenus())
            {
                String safeName = getSafeName(menu);
                %>__menus[<%=PageFlowUtil.jsString(safeName)%>] = {};<%
                    for (Map.Entry<String,String> entry : menu.getPropertyMap().entrySet())
                    {
                        %>__menus[<%=PageFlowUtil.jsString(safeName)%>][<%=PageFlowUtil.jsString(entry.getKey())%>] = <%=PageFlowUtil.jsString(entry.getValue())%>;<%
                    }
            }
        %>
    });
</script>
<% } %>