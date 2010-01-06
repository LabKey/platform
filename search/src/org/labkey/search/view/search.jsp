<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.search.SearchController" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SearchController.SearchForm> me = (JspView<SearchController.SearchForm>) HttpView.currentView();
    SearchController.SearchForm form = me.getModelBean();
    Container c = me.getViewContext().getContainer();

    String queryString = form.getQueryString();
    
    SearchService ss = ServiceRegistry.get().getService(SearchService.class);
    List<SearchService.SearchCategory> categories = ss.getSearchCategories();
    SearchService.SearchCategory selected = null;
    for (SearchService.SearchCategory cat : categories)
    {
        String s = "+searchCategory:" + cat.toString();
        if (queryString.contains(s))
        {
            queryString = queryString.replace(s," ");
            selected = cat;
            break;
        }
    }

    if (!StringUtils.isEmpty(form.getStatusMessage()))
    {
        out.println(h(form.getStatusMessage())+ "<br><br>");
    }
%>
[<a href="<%=h(new ActionURL(SearchController.IndexAction.class, c).addParameter("full", "1"))%>">reindex (full)</a>]<br>
[<a href="<%=h(new ActionURL(SearchController.IndexAction.class, c))%>">reindex (incremental)</a>]<br>
<form name="search"><%
    if (form.isPrint())
    { %>
    <input type=hidden name=_print value=1>";<%
    }
%>
    <input type="hidden" name="guest" value=0>
    <input type="text" size=50 id="query" name="q" value="<%=h(StringUtils.trim(queryString))%>">&nbsp;
    <%=generateSubmitButton("Search")%>
    <%=buttonImg("Search As Guest", "document.search.guest.value=1; return true;")%><br>
<%
    %><input type=radio name=q value="" <%=null==selected?"checked":""%>>all<br><%
    for (SearchService.SearchCategory cat : categories)
    {
        String s = "+searchCategory:cat.toString()";
        boolean checked = form.getQueryString().contains(s);
        %><input type=radio name=q value="+searchCategory:<%=h(cat.toString())%>" <%=cat==selected?"checked":""%>><%=h(cat.getDescription())%><br><%
    }
%>
</form>
