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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuesController.SearchResultsView> me = (JspView<IssuesController.SearchResultsView>) HttpView.currentView();
    IssuesController.SearchResultsView form = me.getModelBean();
    Container c = form._c;
    User user = me.getViewContext().getUser();
    SearchService ss = ServiceRegistry.get().getService(SearchService.class);

    String q = StringUtils.trimToEmpty(form._query);

%><form id=searchForm name="search"><%
    if (form._print)
    { 
        %><input type=hidden name=_print value=1>";<%
    }
%>
    <input type="hidden" name="guest" value=0>
    <input type="text" size=50 id="query" name="q" value="<%=h(q)%>">&nbsp;
    <%=generateSubmitButton("Search")%>
</form>
<%
    if (0 < q.length())
    {
        String qs = "+(" + q + ") +container:" + c.getId() + " +searchCategory:issues";
        if (-1 == q.indexOf("status:"))
            qs += " status:open^2 status:closed^1";

        SearchService.SearchResult result = ss.search(qs, user, ContainerManager.getRoot());
        List<SearchService.SearchHit> hits = result.hits;

        %><div id="searchResults"><%

        for (SearchService.SearchHit hit : hits)
        {
            String href = hit.url;

            try
            {
                if (href.startsWith("/"))
                {
                    ActionURL url = new ActionURL(href);
                    Container cc = ContainerManager.getForId(url.getExtraPath());
                    url.setExtraPath(cc.getPath());
                    href = url.getLocalURIString();
                }
            }
            catch (Exception x)
            {
                //
            }

%><a href="<%=h(hit.url)%>"><%=h(hit.title)%>
</a><br><%
            String summary = StringUtils.trimToNull(hit.summary);
            if (null != summary)
            {
%>
    <div style="margin-left:10px;"><%=PageFlowUtil.filter(summary, false)%>
    </div>
    <%
            }
    %>
    <div style='margin-left:10px; color:green;'><%=h(href)%>
    </div>
    <br><%
        }
        %></div><%
    }
%>
