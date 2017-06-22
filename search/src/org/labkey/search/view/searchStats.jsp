<%
/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.search.model.AbstractSearchService" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
SearchService ss = SearchService.get();

if (null == ss)
{
    %>Indexing service is not configured.<%
}
else
{
    %><table><%
    if (ss instanceof AbstractSearchService)
    {
        Map<String, Double> m = ((AbstractSearchService)ss).getSearchStats();

        %>
        <tr><td colspan=3 valign="top">Average time in milliseconds for each phase of searching the index:</td></tr>
        <tr><td colspan=3 valign="top">&nbsp;</td></tr><%

        for (Map.Entry<String, Double> e : m.entrySet())
        {
            String label = h(ColumnInfo.labelFromName(e.getKey())).replaceAll(" ", "&nbsp;");
            String v = Formats.f2.format(e.getValue());
            %>
        <tr><td valign="top"><%=label%></td><td align="right">&nbsp;&nbsp;<%=v%></td><td width="100%" align="right">&nbsp;</td></tr><%
        }
    }
    %></table><%
}
%>