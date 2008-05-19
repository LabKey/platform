<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController.ListLinkBar.ListBean" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<ListBean> me = (HttpView<ListBean>) HttpView.currentView();
    ListBean bean = me.getModelBean();
%>
<!--ANNOUNCEMENTS-->
<table width="100%">
<tr>
<td align="left" class="normal" style="padding-top:2px;width:33%;"><%
if (null != bean.insertURL)
    {
    %>[<a href="<%=bean.insertURL%>">new <%=h(bean.settings.getConversationName().toLowerCase())%></a>]&nbsp;<%
    }
%></td>
<td align="center" class="normal" style="padding-top:2px;width:33%;"><%=h(bean.filterText)%></td>
<td align="right" class="normal"  style="padding-top:2px;width:33%;"><%
if (null != bean.emailPrefsURL)
    {
    %>[<a href="<%=bean.emailPrefsURL%>">email&nbsp;preferences</a>]<%
    }
if (null != bean.emailManageURL)
    {
    %>&nbsp;[<a href="<%=bean.emailManageURL%>">email&nbsp;admin</a>]<%
    }
if (null != bean.customizeURL)
    {
    %>&nbsp;[<a href="<%=bean.customizeURL%>">customize</a>]<%
    }
%></td>
</tr><%
if (null != bean.urlFilterText)
{
    %><tr><td colspan=3><br>Filter: <%=h(bean.urlFilterText)%></td></tr><%
}
%></table>
