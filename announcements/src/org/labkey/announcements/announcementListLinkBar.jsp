<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
        <td>
            <div style="text-align: left"><%
            if (null != bean.insertURL)
            {
            %><%= button("New").href(bean.insertURL) %><%
            }%>
            <div style="padding-top: 5px;">Showing: <%=h(bean.filterText)%></div>
            </div>
        </td>
    </tr><%
    if (null != bean.urlFilterText)
    {
        %><tr><td colspan=3><br>Filter: <%=h(bean.urlFilterText)%></td></tr>
    <%}%>
</table>
