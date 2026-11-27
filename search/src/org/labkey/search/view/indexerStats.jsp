<%
/*
 * Copyright (c) 2010-2026 LabKey Corporation
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
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.search.SearchController" %>
<%@ page import="org.labkey.search.model.AbstractSearchService" %>
<%@ page import="org.labkey.search.model.DavCrawler" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
SearchService ss = SearchService.get();

if (ss == SearchService.NO_OP)
{
    %>Indexing service is not configured.<%
}
else
{
    Map<String, Object> stats = new LinkedHashMap<>();
    if (ss instanceof AbstractSearchService)
        stats.putAll(((AbstractSearchService) ss).getIndexerStats());
    stats.putAll(DavCrawler.getInstance().getStats());
    %><table><%
    for (Map.Entry<String, Object> e : stats.entrySet())
    {
        %><tr><td valign="top"><%=h(e.getKey())%>&nbsp;&nbsp;</td><td><%=format(e.getValue())%></td></tr>
<%
    }
    %></table><%
}
%>
<%=link("Export index contents", SearchController.IndexContentsAction.class)%>
<%!
    private HtmlString format(Object v)
    {
        if (v instanceof HtmlString html)
            return html;
        if (v instanceof Integer || v instanceof Long)
            return h(Formats.commaf0.format(((Number)v).longValue()));
        return h(v);
    }
%>