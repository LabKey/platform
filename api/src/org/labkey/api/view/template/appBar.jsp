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
%>
<div class="labkey-app-bar">
<div class="labkey-folder-header">
<table class="labkey-folder-header" id="labkey-app-bar-table">
    <tr>
        <td class="labkey-folder-title"><a href="<%=h(bean.getHref())%>"><%=h(bean.getFolderTitle())%></a></td>
        <td class="button-bar">
            <ul class="labkey-tab-strip">
                <table cellpadding="0" cellspacing="0">
                    <tr>
                <%
                    for (NavTree navTree : bean.getButtons())
                    {
                %>
                        <td><li class="<%=navTree.isSelected() ? "labkey-tab-active" : "labkey-tab-inactive"%>"><a href="<%=h(navTree.getHref())%>" id="<%=h(navTree.getText())%>Tab"><%=h(navTree.getText())%></a></td>
                <%
                    }
                    if (context.getContainer().getFolderType().hasConfigurableTabs() && context.hasPermission(org.labkey.api.security.permissions.AdminPermission.class))
                    {
                        NavTree link = new NavTree("Tab Administration");
                        NavTree removeNode = new NavTree("Remove tab");
                        int index = 1;
                        for (NavTree tab : bean.getButtons())
                        {
                            ActionURL url = PageFlowUtil.urlProvider(ProjectUrls.class).getDeleteWebPartURL(context.getContainer(), FolderTab.FOLDER_TAB_PAGE_ID, index++, getViewContext().getActionURL());
                            NavTree removeTab = new NavTree(tab.getText(), url);
                            removeNode.addChild(removeTab);
                        }
                        link.addChild(removeNode);

                        link.addChild(new NavTree("Reset to default tabs", PageFlowUtil.urlProvider(ProjectUrls.class).getResetDefaultTabsURL(context.getContainer(), getViewContext().getActionURL())));

                        PopupMenu menu = new PopupMenu(link);
                        menu.setAlign(PopupMenu.Align.RIGHT);
                        link.setImage(request.getContextPath() + "/_images/text_link_arrow.gif", 10, 5);
                        org.labkey.api.view.PopupMenuView popup = new org.labkey.api.view.PopupMenuView(menu);
                        popup.setButtonStyle(org.labkey.api.view.PopupMenu.ButtonStyle.IMAGE);
                %>
                    <td><li class="labkey-tab-inactive"><% me.include(popup, out); %></td>
                <%
                    }
                %>
                    </tr>
                </table>
            </ul>
        </td>
    </tr>
</table>
</div>
<table class="labkey-nav-trail">
    <%if (null != bean.getNavTrail() && bean.getNavTrail().size() > 0) {
        %>
        <tr>
            <td colspan=1 class="labkey-crumb-trail"><span id="navTrailAncestors" style="visibility:hidden">
                <% for(NavTree curLink : bean.getNavTrail()) {%>
                    <% if (curLink.getHref() != null) { %><a href="<%=curLink.getHref()%>"><% } %><%=h(curLink.getText())%><% if (curLink.getHref() != null) { %></a><% } %>&nbsp;&gt;&nbsp;
                <%
                    }%>
            </span></td></tr>

    <%}%>
    <tr>
    <td class="labkey-nav-page-header-container">
        <span class="labkey-nav-page-header" id="labkey-nav-trail-current-page" style="visibility:hidden"><%=h(bean.getPageTitle())%></span>
    </td>
</tr>
    </table>
</div>

<script type="text/javascript">
    (function(){
        var resizeTask = new Ext.util.DelayedTask(function(){
            var bodyWidth = Ext.getBody().getWidth();
            var leftMenuWidth = Ext.getDom("leftmenupanel") ? Ext.getDom("leftmenupanel").offsetWidth : 0;
            Ext.getDom("labkey-app-bar-table").style.width = (bodyWidth - leftMenuWidth) + "px";
        });

        Ext.EventManager.on(window, 'resize', function(){
            resizeTask.delay(100);
        });

        Ext.onReady(function(){
            resizeTask.delay(0);
        });
    })();
</script>