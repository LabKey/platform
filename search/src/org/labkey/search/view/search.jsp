<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.                                                             :
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
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.text.ParseException" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SearchController.SearchForm> me = (JspView<SearchController.SearchForm>) HttpView.currentView();
    SearchController.SearchForm form = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();
    User user = ctx.getUser();
    SearchService ss = ServiceRegistry.get().getService(SearchService.class);
    boolean wideView = true;

    List<String> q = new ArrayList<String>(Arrays.asList(form.getQ()));

    List<SearchService.SearchCategory> categories = ss.getSearchCategories();
    SearchService.SearchCategory selected = null;
    for (SearchService.SearchCategory cat : categories)
    {
        String s = "+searchCategory:" + cat.toString();
        if (q.remove(s))
        {
            selected = cat;
            break;
        }
    }

    if (form.isAdvanced())
    {
        if (!StringUtils.isEmpty(form.getStatusMessage()))
        {
            out.println(h(form.getStatusMessage())+ "<br><br>");
        }
%>
[<a href="<%=h(new ActionURL(SearchController.IndexAction.class, c).addParameter("full", "1"))%>">reindex (full)</a>]<br>
[<a href="<%=h(new ActionURL(SearchController.IndexAction.class, c))%>">reindex (incremental)</a>]<br><%
    }
%>
<form id=searchForm name="search" action="<%=SearchController.getSearchURL(c)%>"><%
    if (form.isPrint())
    {
        %><input type=hidden name=_print value=1><%
    }

    if (null != form.getCategory())
    {
        %><input type=hidden name=category value="<%=h(form.getCategory())%>"><%
    }

    if (form.isAdvanced())
    {
        %><input type="hidden" name="guest" value=0><%
    }
    %><input type="text" size=50 id="query" name="q" value="<%=h(StringUtils.trim(StringUtils.join(q," ")))%>">&nbsp;
    <%=generateSubmitButton("Search")%><%

    if (form.isAdvanced())
    {
    %>
    <%=buttonImg("Search As Guest", "document.search.guest.value=1; return true;")%>
    <%=buttonImg("Google", "return google();")%><br>
    <input type=checkbox name="container" value="<%=c.getId()%>" <%=null==form.getContainer()?"":"checked"%>>this folder and children<br>
    <input type=radio name=q value="" <%=null==selected?"checked":""%>>all<br><%
        for (SearchService.SearchCategory cat : categories)
        {
            String s = "+searchCategory:cat.toString()";
            boolean checked = form.getQueryString().contains(s);
            %><input type=radio name=q value="+searchCategory:<%=h(cat.toString())%>" <%=cat==selected?"checked":""%>><%=h(cat.getDescription())%><br><%
        }
    }
%><input type="hidden" name="_dc" value="<%=Math.round(1000*Math.random())%>"></form><%
    if (form.isAdvanced())
    {
        %><script type="text/javascript">
        function google()
        {
            var query = document.getElementById('query').value;
            window.location = 'http://www.google.com/search?q=' + encodeURIComponent(query);
            return false;
        }
        </script>
        <%
    }

    String category = form.getCategory();
    ActionURL researchURL = ctx.cloneActionURL().deleteParameter("category");
    String queryString = form.getQueryString();

    if (null != StringUtils.trimToNull(queryString))
    {
        %><br>
        <table>
            <tr><td><font size="2">
                <% if (null == category) { %>All<% } else { %><a href="<%=h(researchURL)%>">All</a><% } %>
                <% if ("File".equals(category)) { %>Files<% } else { %><a href="<%=h(researchURL.clone().addParameter("category", "File"))%>">Files</a><% } %>
                <% if ("Subject".equals(category)) { %>Subjects<% } else { %><a href="<%=h(researchURL.clone().addParameter("category", "Subject"))%>">Subjects</a><% } %>
                <% if ("Dataset".equals(category)) { %>Datasets<% } else { %><a href="<%=h(researchURL.clone().addParameter("category", "Dataset"))%>">Datasets</a><% } %>
            </font></td></tr>
            <tr><td valign="top" align="left" width=500><%
        int hitsPerPage = 20;  // UNDONE
        int offset = 0;

        int pageNo = (offset / hitsPerPage) + 1;

        try
        {
            String qs = queryString;
            long start = System.nanoTime();
            Container searchContainer = null == form.getContainer() ? ContainerManager.getRoot() : ContainerManager.getForId(form.getContainer());
            SearchService.SearchResult result = ss.search(qs, ss.getCategory(category), user, searchContainer, offset, hitsPerPage);
            long time = (System.nanoTime() - start)/1000000;
            int totalHits = result.totalHits;

            %><p /><p />Found <%=Formats.commaf0.format(totalHits)%> result<%=totalHits != 1 ? "s" : ""%> in <%=Formats.commaf0.format(time)%>ms.<br><%

            if (hitsPerPage < totalHits)
            {
                %>Displaying page <%=Formats.commaf0.format(pageNo)%> of <%=Formats.commaf0.format((int)Math.ceil((double)totalHits / hitsPerPage))%><br><%
            }
            else
            {
                %>Displaying all results<br><%
            }
            %><p />
            <div id="searchResults"><%

            for (SearchService.SearchHit hit : result.hits)
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

            %><a href="<%=h(hit.url)%>"><%=h(hit.displayTitle)%>
            </a><br><%
                String summary = StringUtils.trimToNull(hit.summary);
                if (null != summary)
                {
            %>
                <div style="margin-left:10px;"><%=PageFlowUtil.filter(summary, false)%>
                </div>
                <%
                }

                if (form.isAdvanced())
                {
                %>
                <div style='margin-left:10px; color:green;'><%=h(href)%>
                </div><%
                }
                %>
                <br><%
            }
            %></div></td><%

            if (-1 == queryString.indexOf("searchCategory") && wideView)
            {
                result = ss.search(queryString, SearchService.navigationCategory, user, ContainerManager.getRoot(), offset, hitsPerPage);

                if (result.hits.size() > 0)
                {
                    %><td valign="top" align="left" width="350>"><%
                    WebPartView.startTitleFrame(out, "Quick Links");
                    %><div id="navigationResults"><%

                    for (SearchService.SearchHit hit : result.hits)
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
                        String summary = StringUtils.trimToNull(hit.summary);
                        if (null != summary)
                        {
                            %><div style="margin-left:10px;"><%=PageFlowUtil.filter(summary, false)%></div><%
                        }
                        // %><div style='margin-left:10px; color:green;'><%=h(href)%></div><br><%
                    }
                    %></div><%
                    WebPartView.endTitleFrame(out);
                    %></td><%
                }
            }
        }
        catch (IOException e)
        {
            Throwable t = e;
            if (e.getCause() instanceof ParseException)
                t = e.getCause();
            out.write("Error: " + t.getMessage());
        } %>
        </tr></table><%
    }
%>

