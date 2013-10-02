<%
/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.PopupMenu" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.AppBar" %>
<%@ page import="org.labkey.api.view.template.AppBarView" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>
<%
    ViewContext context = HttpView.currentView().getViewContext();
    boolean tabEditMode = session.getAttribute("tabEditMode") != null && ((boolean) session.getAttribute("tabEditMode"));
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
        <div class="button-bar <%=tabEditMode ? "tab-edit-mode-enabled" : "tab-edit-mode-disabled"%>">
            <ul>
                <%
                    for (NavTree navTree : tabs)
                    {
                        if (null != navTree.getText() && navTree.getText().length() > 0)
                        {
                            String classes = "";
                            if (navTree.isSelected())
                                classes = classes + "labkey-app-bar-tab-active";
                            else
                                classes = classes + "labkey-app-bar-tab-inactive";

                            if (navTree.isDisabled())
                                classes = classes + " labkey-app-bar-tab-hidden";

                            if (navTree.getChildCount() == 0)
                                classes = classes + " labkey-no-tab-menu";
                %>
                        <li class="<%=text(classes)%>">
                            <a href="<%=h(navTree.getHref())%>" id="<%=h(navTree.getText())%>Tab"><%=h(navTree.getText())%></a>
                            <%
                                if(navTree.getChildCount() > 0)
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
                    if(context.hasPermission(context.getUser(), AdminPermission.class) && context.getContainer().getFolderType() != org.labkey.api.module.FolderType.NONE)
                    {
                %>
                        <li class="labkey-app-bar-add-tab" id="addTab">
                            <a href="javascript:LABKEY.Portal.addTab();" title="Add New Tab">+</a>
                        </li>
                        <li class="labkey-app-bar-edit-tab" id="editTabs">
                            <a href="javascript:LABKEY.Portal.toggleTabEditMode();" title="Toggle Edit Mode"><img src="<%=context.getContextPath()%>/_images/pencil2.png" /></a>
                        </li>
                <%
                    }
                %>
            </ul>
        </div>
        <div style="clear:both;"></div>
    </div>
</div>

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
        var setMinWidth = function() {
            var tabs = Ext4.query('.labkey-app-bar ul li');
            var folderTitle = Ext4.get(Ext4.query('.labkey-folder-title')[0]);
            var folderHeader = Ext4.get(Ext4.query('.labkey-folder-header')[0]);
            var signIn = Ext4.get(Ext4.query('.headermenu')[0]);
            var appBar = Ext4.query('.labkey-app-bar')[0];
            var viewportWidth = Ext4.Element.getViewportWidth();
            var folderHeaderWidth = viewportWidth - appBar.getBoundingClientRect().left - 60; // 60 is for some extra padding between the + tab and right side of the screen.
            var totalWidth = folderTitle.getWidth();

            for(var i = 0; i < tabs.length; i++){
                var anchor = Ext4.get(tabs[i]);
                totalWidth = totalWidth + anchor.getWidth();
                if(tabs[i].getAttribute('id') !== 'addTab'){
                    totalWidth = totalWidth + 20;
                }
            }

            if(folderHeader){ // Why wouldn't it be there? Better safe than javascript errors.
                if(folderHeaderWidth < totalWidth){
                    folderHeader.setWidth(totalWidth);
                } else {
                    folderHeader.setWidth(folderHeaderWidth);
                }
            }
            if (signIn) {
                signIn.setLeft(viewportWidth - signIn.getBox().width - 20);
            }
        };

        var lastScrollLeft = Math.max(document.documentElement.scrollLeft, document.body.scrollLeft);

        var scrollListener = function(){
            // Some browsers keep the value in documentElement, some in body.
            var scrollLeft = Math.max(document.documentElement.scrollLeft, document.body.scrollLeft);

            if(scrollLeft != lastScrollLeft){
                setMinWidth();
            }
        };

        var addTabListeners = function() {
            var tabs = Ext4.query('.labkey-app-bar ul li');
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

        setMinWidth();
        addTabListeners();
        Ext4.EventManager.onWindowResize(setMinWidth);

        if (window.addEventListener) {
            // Most browsers.
            window.addEventListener('scroll', scrollListener, false);
        }
        else if(window.attachEvent) {
            // <= IE8
            window.attachEvent('onscroll',scrollListener);
        }
    });
</script>
