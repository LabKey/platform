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
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="java.io.IOException" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="java.util.Arrays" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuesController.SearchResultsView> me = (JspView<IssuesController.SearchResultsView>) HttpView.currentView();
    IssuesController.SearchResultsView bean = me.getModelBean();
    Container c = bean._c;
    User user = me.getViewContext().getUser();
    SearchService ss = ServiceRegistry.get().getService(SearchService.class);

    String q = StringUtils.trimToEmpty(bean._query);
    String status = bean._status;

%><form id=searchForm name="search"><%

    if (bean._print)
    { 
        %><input type=hidden name=_print value=1>";<%
    }

    if (null != status)
    {
        %><input type=hidden name=status value="<%=h(status)%>"><%
    }

    %><input type="text" size=50 id="query" name="q" value="<%=h(q)%>">&nbsp;<%=generateSubmitButton("Search")%>&nbsp;&nbsp;&nbsp;<%=textLink("help", new HelpTopic("luceneSearch").getHelpTopicLink())%>
</form><%
    if (0 < q.length())
    {
        ActionURL statusResearchURL = me.getViewContext().cloneActionURL().deleteParameter("status");
        if (null != status)
            q = "+(" + q + ") +status:" + status;

        %>
        <table width=100% cellpadding="0" cellspacing="0"><tr>
            <td class="labkey-search-filter">&nbsp;<%
                %><span><%if (null == status)            { %>All<%      } else { %><a href="<%=h(statusResearchURL)%>">All</a><% } %></span>&nbsp;<%
                %><span><%if ("Open".equals(status))     { %>Open<%     } else { %><a href="<%=h(statusResearchURL.clone().addParameter("status", "Open"))     %>">Open</a><%     } %></span>&nbsp;<%
                %><span><%if ("Resolved".equals(status)) { %>Resolved<% } else { %><a href="<%=h(statusResearchURL.clone().addParameter("status", "Resolved")) %>">Resolved</a><% } %></span>&nbsp;<%
                %><span><%if ("Closed".equals(status))   { %>Closed<%   } else { %><a href="<%=h(statusResearchURL.clone().addParameter("status", "Closed"))   %>">Closed</a><%   } %></span><%
            %></td>

        </tr></table><br><%

        try
        {
            SearchService.SearchResult result = ss.search(q, Arrays.asList(IssueManager.searchCategory), user, c, false, 0, SearchService.DEFAULT_PAGE_SIZE);
            List<SearchService.SearchHit> hits = result.hits;

            %><div id="searchResults" style="max-width:800px;"><%

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

                %><a href="<%=h(hit.url)%>"><%=h(hit.displayTitle)%></a><br><%
                if (!StringUtils.isEmpty(hit.summary))
                {
                    %><div style="margin-left:10px;"><%=PageFlowUtil.filter(StringUtils.trimToEmpty(hit.summary), false)%></div><%
                }
                %><%-- <div style='margin-left:10px; color:green;'><%=h(href)%></div> --%>
                <br><%
            }
        }
        catch (IOException e)
        {
            out.write("<br>" + h("Error: " + e.getMessage()));
        }
    %></div><%
    }
%>
