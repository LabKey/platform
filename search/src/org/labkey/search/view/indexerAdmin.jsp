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
<%@ page import="org.labkey.search.model.AbstractSearchService" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.search.model.DavCrawler" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView<SearchController.AdminForm> me = (JspView<SearchController.AdminForm>) HttpView.currentView();
User user = me.getViewContext().getUser();
SearchController.AdminForm form = me.getModelBean();
SearchService ss = ServiceRegistry.get().getService(SearchService.class);

%><labkey:errors /><%
    if (!StringUtils.isEmpty(form.getMessage()))
    { %><br>
        <span style="color:green;"><%=form.getMessage()%></span><%
    }

if (null == ss)
{
    %>Indexing service is not configured.<%
}
else
{
    WebPartView.startTitleFrame(out,"Admin Actions");

    %><p><form method="POST" action="admin.view"><%
    if (ss.isRunning())
    {
        %>The document crawler is running.<br><%

        if (user.isAdministrator())
        {
        %>
        <input type="hidden" name="pause" value="1">
        <%=PageFlowUtil.generateSubmitButton("Pause")%><%
        }
    }
    else
    {
        %>The document crawler is paused.<br><%

        if (user.isAdministrator())
        {
        %>
        <input type="hidden" name="start" value="1">
        <%=PageFlowUtil.generateSubmitButton("Start")%><%
        }
    }
    %></form></p><%
    if (user.isAdministrator())
    {
    %>
    <p><form method="POST" action="admin.view">
        Delete the search index<br>
        You shouldn't need to do this, but if something goes wrong, you can give it a try.  Note that re-indexing can be very expensive.<br>
        <input type="hidden" name="delete" value="1">
        <%=PageFlowUtil.generateSubmitButton("Delete Index")%>
    </form></p><%
    }
    
    WebPartView.endTitleFrame(out);
    WebPartView.startTitleFrame(out,"Statistics");
    %><table><%
    if (ss instanceof AbstractSearchService)
    {
        Map<String,Object> m = ((AbstractSearchService)ss).getStats();
        for (Map.Entry e : m.entrySet())
        {
            String l = String.valueOf(e.getKey());
            Object v = e.getValue();
            if (v instanceof Integer || v instanceof Long)
                v = Formats.commaf0.format(((Number)v).longValue());
            v = null==v ? "" : String.valueOf(v);
            %><tr><td valign="top"><%=l%></td><td><%=v%></td></tr><%
        }
    }
    Map<String,Object> m = DavCrawler.getInstance().getStats();
    for (Map.Entry e : m.entrySet())
    {
        String l = String.valueOf(e.getKey());
        Object v = e.getValue();
        if (v instanceof Integer || v instanceof Long)
            v = Formats.commaf0.format(((Number)v).longValue());
        v = null==v ? "" : String.valueOf(v);
        %><tr><td valign="top"><%=l%></td><td><%=v%></td></tr><%
    }
    %></table><%
    WebPartView.endTitleFrame(out);
}
%>