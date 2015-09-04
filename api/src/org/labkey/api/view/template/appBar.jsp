<%
/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.PopupMenu" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.AppBar" %>
<%@ page import="org.labkey.api.view.template.AppBarView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4"));
        return resources;
    }
%>
<%
    ViewContext context = getViewContext();
    Container tabContainer = getContainer();
    if (tabContainer.isContainerTab() || tabContainer.isWorkbook())
        tabContainer = tabContainer.getParent();

    String tabEditMode = session.getAttribute("tabEditMode") == null ? "" : (String) session.getAttribute("tabEditMode");
    boolean isTabEditMode = tabEditMode.equals(tabContainer.getId());
    AppBarView me = (AppBarView) HttpView.currentView();
    AppBar bean = me.getModelBean();
    if (null == bean)
        return;

    // 16065: case is wrong on frame header for home project
    String folderTitle = bean.getFolderTitle();
    if (folderTitle.equals("home"))
        folderTitle = "Home";

    NavTree[] tabs = bean.getButtons();
    List<NavTree> portalTabs = bean.getSubContainerTabs();
%>
<div class="labkey-app-bar">
    <div class="labkey-folder-header">
        <div class="labkey-folder-title"><a href="<%=h(bean.getHref())%>"><%=h(folderTitle)%></a></div>
        <div class="button-bar <%=text(isTabEditMode ? "tab-edit-mode-enabled" : "tab-edit-mode-disabled")%>">
            <ul class="tab-nav">
                <%
                    for (NavTree navTree : tabs)
                    {
                        if (null != navTree.getText() && navTree.getText().length() > 0)
                        {
                            String classes = "";
                            if (navTree.isSelected())
                                classes = classes + "tab-nav-active";
                            else
                                classes = classes + "tab-nav-inactive";

                            if (navTree.isDisabled())
                                classes = classes + " tab-nav-hidden";

                            if (!context.hasPermission(getUser(), AdminPermission.class) || navTree.getChildCount() == 0)
                                classes = classes + " tab-nav-no-menu";
                %>
                        <li class="<%=text(classes)%>">
                            <a href="<%=h(navTree.getHref())%>" id="<%=h(navTree.getText()).replace(" ", "")%>Tab"><%=h(navTree.getText())%></a>
                            <%
                                if(context.hasPermission(getUser(), AdminPermission.class) && navTree.getChildCount() > 0)
                                {
                            %>
                                    <span class="labkey-tab-menu" style="visibility:hidden;">
                            <%
                                    NavTree tabMenu = navTree.getChildren()[0];
                                    PopupMenu tabMenuPopup = new PopupMenu(tabMenu);
                                    tabMenu.setImage(request.getContextPath() + "/_images/text_link_arrow.gif", 10, 5);
                                    tabMenuPopup.setAlign(PopupMenu.Align.RIGHT);
                                    org.labkey.api.view.PopupMenuView popup = new org.labkey.api.view.PopupMenuView(tabMenuPopup);
                                    popup.setButtonStyle(org.labkey.api.view.PopupMenu.ButtonStyle.IMAGE);
                                    me.include(popup, out);
                            %>
                                    </span>
                            <%
                                }
                            %>
                        </li>
                <%
                        }
                    }
                    if(context.hasPermission(getUser(), AdminPermission.class) && tabContainer.getFolderType() != FolderType.NONE)
                    {
                %>
                        <li class="tab-nav-add" id="addTab">
                            <a href="javascript:LABKEY.Portal.addTab();" title="Add New Tab">+</a>
                        </li>
                        <li class="tab-nav-edit" id="editTabs">
                            <a href="javascript:LABKEY.Portal.toggleTabEditMode();" title="Toggle Edit Mode">
                                &nbsp;
                                <span class="fa fa-pencil" unselectable="on"></span>
                            </a>
                        </li>
                <%
                    }
                %>
            </ul>
        </div>
        <div style="clear:both;"></div>
    </div>
</div>
<div class="labkey-app-bar labkey-app-bar-replicate"></div>

<%
     if (portalTabs != null && portalTabs.size() > 1)
     {
%>
        <div class="labkey-sub-container-tab-strip">
            <ul>
                <%
                    for (NavTree portalTab : portalTabs)
                    {
                        String liClasses = "";

                        if (portalTab.isSelected())
                            liClasses ="selected";
                %>
                        <li>
                            <a href="<%=h(portalTab.getHref())%>" class="<%=text(liClasses)%>">
                                <%=h(portalTab.getText())%>
                            </a>
                        </li>
                <%
                    }
                %>
            </ul>
            <div style="clear: both;"></div>
        </div>
<%
    }
%>
    
    <div class="labkey-nav-trail">
        <%if (null != bean.getNavTrail() && bean.getNavTrail().size() > 0) {
        %>
            <div colspan=1 class="labkey-crumb-trail">
                <span id="navTrailAncestors" style="visibility:hidden">
                <% for(NavTree curLink : bean.getNavTrail()) {%>
                <% if (curLink.getHref() != null) { %><a href="<%=h(curLink.getHref())%>"><% } %><%=h(curLink.getText())%><% if (curLink.getHref() != null) { %></a><% } %>&nbsp;&gt;&nbsp;
                <%
                    }%>
                </span>
            </div>

        <%}%>
            <div class="labkey-nav-page-header-container">
                <span class="labkey-nav-page-header" id="labkey-nav-trail-current-page" style="visibility:hidden"><%=h(bean.getPageTitle())%></span>
            </div>
    </div>

<script type="text/javascript">
    Ext4.onReady(function() {

        var fatBars = function() {
            var topBar = Ext4.get('topmenu');
            if (topBar) {
                var contentTable = Ext4.get(Ext4.DomQuery.select('table.labkey-proj')[0]).getBox();
                var width = contentTable.width-5;
                var menuBarRep = Ext4.get(Ext4.DomQuery.select('.main-menu-replicate')[0]);
                menuBarRep.setWidth(width);
                menuBarRep.setStyle('top', '' + topBar.getBox().top + 'px');
            }

            var appBar = Ext4.get(Ext4.DomQuery.select('.labkey-app-bar')[0]);
            var appBarRep = Ext4.get(Ext4.DomQuery.select('.labkey-app-bar-replicate')[0]);
            if (appBar && appBarRep) {
                appBarRep.setSize(width, 32);
                appBarRep.setStyle('top', '' + appBar.getBox().top + 'px');
            }
        };

        var addTabListeners = function() {
            var tabs = Ext4.query('.tab-nav li');
            var tab, i=0;
            for(; i < tabs.length; i++){
                tab = Ext4.get(tabs[i]);
                tab.on('mouseover', function(){
                    // only show menu if tab edit mode is enabled.
                    if (Ext4.query('.tab-edit-mode-enabled').length > 0) {
                        var tabMenu =  Ext4.get(this.query('span[class=labkey-tab-menu]')[0]);
                        if(tabMenu){
                            tabMenu.show();
                        }
                    }
                });

                tab.on('mouseout', function(){
                    var tabMenu =  Ext4.get(this.query('span[class=labkey-tab-menu]')[0]);
                    if(tabMenu){
                        tabMenu.hide();
                    }
                });
            }
        };

        fatBars();
        addTabListeners();
        Ext4.EventManager.onWindowResize(fatBars);
    });
</script>
