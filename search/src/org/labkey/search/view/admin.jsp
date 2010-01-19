<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.search.SearchController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.search.model.DavCrawler" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SearchController.AdminForm> me = (JspView<SearchController.AdminForm>) HttpView.currentView();
    SearchController.AdminForm form = me.getModelBean();
    SearchService ss = ServiceRegistry.get().getService(SearchService.class);

    %><labkey:errors /><%
    if (null == ss)
    {
        %>Indexing service is not configured.<%
    }
    else if(ss.isRunning())
    {
        %> The indexing crawler is running.<br>
        <form method="POST">
            <input type="hidden" name="pause" value="1">
            <%=PageFlowUtil.generateSubmitButton("PAUSE")%>
        </form><%
    }
    else
    {
        %> The indexing crawler is paused.<br>
        <form method="POST">
            <input type="hidden" name="start" value="1">
            <%=PageFlowUtil.generateSubmitButton("START")%>
        </form><%
    }

    WebPartView.startTitleFrame(out,"Statistics");
    Map<String,Object> m = DavCrawler.getInstance().getStats();
    %><table><%
    for (Map.Entry e : m.entrySet())
    {
        String l = String.valueOf(e.getKey());
        String v = String.valueOf(e.getValue());
        %><tr><td valign="top"><%=l%></td><td><%=v%></td></tr><%
    }
    %></table>
