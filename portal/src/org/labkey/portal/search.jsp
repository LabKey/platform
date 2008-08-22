<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.util.Search" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Search.SearchBean> me = ((JspView<Search.SearchBean>)HttpView.currentView());
    Search.SearchBean bean = me.getModelBean();
    Container c = me.getViewContext().getContainer();
%>
<form method="get" action="<%=bean.postURL%>"><%
    if (bean.showExplanatoryText)
    { %>
Search <%=h(bean.what)%> in this <%=(c.isProject() ? "project" : "folder")%><%
    } %>
<table>
<tr>
    <td colspan=2><input type="text" id="search" name="search" value="<%=h(bean.searchTerm)%>"<%=bean.textBoxWidth > 0 ? " size=\"" + bean.textBoxWidth + "\"" : ""%>></td><%

    if (bean.showSettings)
    { %>
</tr>
<tr>
    <td width="1"><input type="checkbox" name="includeSubfolders" value="on" <%=bean.includeSubfolders ? "checked" : ""%>></td><td>Search subfolders</td>
</tr>
<tr>
    <td colspan=2>&nbsp;</td>
</tr>
<tr>
    <td colspan=2><%=PageFlowUtil.generateSubmitButton("Search")%></td>
    <%
    }
    else
    { %>
    <td><input type="hidden" name="includeSubfolders" value="<%=bean.includeSubfolders ? "on" : "off"%>"></td>
    <td colspan=2><%=PageFlowUtil.generateSubmitButton("Search")%></td>
    <%
    }
    %>
</tr>
</table>
</form>
