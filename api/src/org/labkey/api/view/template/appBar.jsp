<%
/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.template.AppBar" %>
<%@ page import="org.labkey.api.view.template.AppBarView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    AppBar bean = ((AppBarView) HttpView.currentView()).getModelBean();
    if (null == bean)
        return;
    ViewContext context = HttpView.currentContext();
    Container c = context.getContainer();
    ActionURL startUrl = c.getStartURL(context.getUser());
%>
<div class="labkey-app-bar">
<div class="folder-header" id="labkey-app-bar-div">
<table class="folder-header" id="labkey-app-bar-table">
    <tr>
        <td class="folder-title"><a href="<%=h(startUrl.getLocalURIString())%>"><%=h(bean.getFolderTitle())%></a></td>
        <td class="button-bar">
            <ul class="labkey-tab-strip">
                <table cellpadding="0" cellspacing="0">
                    <tr>
                <%
                    for (NavTree navTree : bean.getButtons())
                    {
                %>
                        <td><li class="<%=navTree.isSelected() ? "labkey-tab-active" : "labkey-tab-inactive"%>"><a href="<%=h(navTree.getValue())%>" id="<%=h(navTree.getKey())%>Tab"><%=h(navTree.getKey())%></a></td>
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
<%if(null != bean.getPageTitle()) {%>
<table class="labkey-nav-trail">
    <%if (null != bean.getNavTrail() && bean.getNavTrail().size() > 0) {
        %>
        <tr>
            <td colspan=1 class="labkey-crumb-trail"><span id="navTrailAncestors" style="visibility:hidden">
                <% for(NavTree curLink : bean.getNavTrail()) {%>
                    <% if (curLink.getValue() != null) { %><a href="<%=curLink.getValue()%>"><% } %><%=h(curLink.getKey())%><% if (curLink.getValue() != null) { %></a><% } %>&nbsp;&gt;&nbsp;
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
<%}%>

<script type="text/javascript">
    Ext.onReady(function(){
        resizeTask.delay(0);
    });

    var resizeTask = new Ext.util.DelayedTask(function(){
        var folderHeaderTableWidth = Ext.getDom("labkey-app-bar-div").offsetWidth;
        var bodyWidth = Ext.getBody().getWidth();
        var leftMenuWidth = Ext.getDom("leftmenupanel") ? Ext.getDom("leftmenupanel").offsetWidth : 0;

        if ((folderHeaderTableWidth + leftMenuWidth) > bodyWidth)
            Ext.getDom("labkey-app-bar-table").style.width = (bodyWidth - leftMenuWidth) + "px";
        else
            Ext.getDom("labkey-app-bar-table").style.width = "100%";
    });

    Ext.EventManager.on(window, 'resize', function(){
        resizeTask.delay(100);
    });
</script>