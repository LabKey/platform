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
<table class="folder-header">
    <tr>
        <td class="folder-title"><a href="<%=h(startUrl.getLocalURIString())%>"><%=h(bean.getFolderTitle())%></a></td>
        <td class="button-bar">
            <ul class="labkey-tab-strip">
                <%
                    for (NavTree navTree : bean.getButtons())
                    {
                %>
                        <li class="<%=navTree.isSelected() ? "labkey-tab-active" : "labkey-tab-inactive"%>"><a href="<%=h(navTree.getValue())%>"><%=h(navTree.getKey())%></a>
                <%
                    }
                %>
            </ul>
        </td>
    </tr>
</table>
<%if(null != bean.getPageTitle()) {%>
<table class="labkey-nav-trail">
    <tr>
    <td class="labkey-nav-page-header-container">
    <span class="labkey-nav-page-header" id="labkey-nav-trail-current-page" style="visibility:hidden"><%=h(bean.getPageTitle())%></span>
    </td>
</tr>
    </table>
</div>
<%}%>

