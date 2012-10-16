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
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.FolderTab" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.PopupMenu" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.AppBar" %>
<%@ page import="org.labkey.api.view.template.AppBarView" %>
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
%>
<div class="labkey-app-bar">
    <div class="labkey-folder-header">
        <div class="labkey-folder-title"><a href="<%=h(bean.getHref())%>"><%=h(folderTitle)%></a></div>
        <div class="button-bar">
            <ul class="labkey-tab-strip">
                <%
                    NavTree[] tabs = bean.getButtons();
                    for (NavTree navTree : tabs)
                    {
                        if (null != navTree.getText() && navTree.getText().length() > 0)
                        {
                %>
                        <li class="<%=text(navTree.isSelected() ? "labkey-tab-active" : "labkey-tab-inactive")%>"><a href="<%=h(navTree.getHref())%>" id="<%=h(navTree.getText())%>Tab"><%=h(navTree.getText())%></a>
                <%
                        }
                    }
                    if (context.getContainer().getFolderType().hasConfigurableTabs() && context.hasPermission(org.labkey.api.security.permissions.AdminPermission.class))
                    {
                        NavTree link = new NavTree("Tab Administration");
                        if (tabs.length > 1)
                        {
                            NavTree removeNode = new NavTree("Remove tab");
                            for (NavTree tab : tabs)
                            {
                                if (tab.getId().startsWith("portal:"))
                                {
                                    String pageId = tab.getId().substring("portal:".length());
                                    ActionURL url = PageFlowUtil.urlProvider(ProjectUrls.class).getHidePortalPageURL(context.getContainer(), pageId, getViewContext().getActionURL());
                                    NavTree removeTab = new NavTree(tab.getText(), url);
                                    removeNode.addChild(removeTab);
                                }
                            }
                            link.addChild(removeNode);
                        }

                        link.addChild(new NavTree("Reset to default tabs", PageFlowUtil.urlProvider(ProjectUrls.class).getResetDefaultTabsURL(context.getContainer(), getViewContext().getActionURL())));

                        PopupMenu menu = new PopupMenu(link);
                        menu.setAlign(PopupMenu.Align.RIGHT);
                        link.setImage(request.getContextPath() + "/_images/text_link_arrow.gif", 10, 5);
                        org.labkey.api.view.PopupMenuView popup = new org.labkey.api.view.PopupMenuView(menu);
                        popup.setButtonStyle(org.labkey.api.view.PopupMenu.ButtonStyle.IMAGE);
                %>
                    <li class="labkey-tab-inactive"><% me.include(popup, out); %>
                <%
                    }
                %>
            </ul>
        </div>
        <div style="clear:both;"></div>
    </div>
    
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
</div>

<script type="text/javascript">
    (function(){
        Ext4.onReady(function(){
            var buttonBar = Ext4.get(Ext4.query('.labkey-folder-header .button-bar')[0]);
            var folderTitle = Ext4.get(Ext4.query('.labkey-folder-title')[0]);
            var appBar = Ext4.get(Ext4.query('.labkey-app-bar')[0]);
            var tabAnchors = Ext4.query('.labkey-app-bar ul.labkey-tab-strip li a');
            var totalWidth = 0;

            for(var i = 0; i < tabAnchors.length; i++){
                var anchor = Ext4.get(tabAnchors[i]);
                totalWidth = totalWidth + anchor.getWidth() + 2; // add two for tab margin.
            }

            // Add 60 for padding and margins.
            appBar.dom.setAttribute('style', 'min-width: ' + (totalWidth + folderTitle.getWidth() + 60 ) + 'px;');

            if(Ext4.isIE7){
                // We add a few more px for IE7 be
//                appBar.setWidth(totalWidth + 20);
                buttonBar.setWidth(totalWidth+10);
            }
        });
    })();
</script>
