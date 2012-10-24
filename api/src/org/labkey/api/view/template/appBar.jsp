<%
/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    ViewContext context = HttpView.currentView().getViewContext();
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
        <div class="button-bar">
            <ul>
                <%
                    for (NavTree navTree : tabs)
                    {
                        if (null != navTree.getText() && navTree.getText().length() > 0)
                        {
                %>
                        <li class="<%=text(navTree.isSelected() ? "labkey-app-bar-tab-active" : "labkey-app-bar-tab-inactive")%>">
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
                    if(context.getUser().isAdministrator())
                    {
                %>
                    <li class="labkey-app-bar-add-tab">
                        <a href="<%=PageFlowUtil.urlProvider(AdminUrls.class).getAddTabURL(context.getContainer().getProject(), context.getActionURL())%>">+</a>
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
    (function(){
        var setMinWidth = function(){
            var buttonBar = Ext4.get(Ext4.query('.labkey-folder-header .button-bar')[0]);
            var folderTitle = Ext4.get(Ext4.query('.labkey-folder-title')[0]);
            var appBar = Ext4.get(Ext4.query('.labkey-app-bar')[0]);
            var tabMenus = Ext4.query('span.labkey-tab-menu');
            var tabs = Ext4.query('.labkey-app-bar ul li');
            var totalWidth = 0;

            for(var i = 0; i < tabMenus.length; i++){
                var tabMenu = Ext4.get(tabMenus[i]);
            }

            for(var i = 0; i < tabs.length; i++){
                var anchor = Ext4.get(tabs[i]);
                totalWidth = totalWidth + anchor.getWidth() + 10; // add two for tab padding and margin.
            }

            appBar.dom.setAttribute('style', 'min-width: ' + (totalWidth + folderTitle.getWidth()) + 'px;');

            if(Ext4.isIE7){
                // We add a few more px for IE7
                buttonBar.setWidth(totalWidth + 10);
            }
        };

        var addTabListeners = function(){
            var tabs = Ext4.query('.labkey-app-bar ul li');
            for(var i = 0; i < tabs.length; i++){
                var tab = Ext4.get(tabs[i]);
                tab.on('mouseover', function(){
                    var tabMenu =  Ext4.get(this.query('span[class=labkey-tab-menu]')[0]);
                    if(tabMenu){
                       tabMenu.show();
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

        var tabs = LABKEY.ExtAdapter.query('.labkey-app-bar ul li');

        Ext4.onReady(function(){
            setMinWidth();
            addTabListeners();
        });
    })();
</script>
