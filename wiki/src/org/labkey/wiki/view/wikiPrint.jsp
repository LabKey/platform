<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="org.labkey.wiki.model.Wiki" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%
    JspView<Wiki> me = (JspView<Wiki>) HttpView.currentView();
    Wiki wiki = me.getModelBean();
%>
<div style="padding:10px;">
    <%  if (null == wiki.latestVersion())
        {%>
            This page does not have any printable content. The page may have been deleted or renamed by another user.<br><br>
        <%}
        else
        {%>
            <table style="width:100%;">
                <tr>
                    <td align=left><h3 class="labkey-heading-1"><%=PageFlowUtil.filter(wiki.latestVersion().getTitle())%></h3></td>
                    <td align=right><%=DateUtil.formatDate()%></td>
                </tr>
            </table>
            <hr>
            <%=wiki.latestVersion().getHtml(me.getViewContext().getContainer(),wiki)%>
        <%}%>
</div>