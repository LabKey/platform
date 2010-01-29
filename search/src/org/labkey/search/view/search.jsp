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
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.search.SearchController" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.text.ParseException" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.List" %>
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
    %><input type="text" size=50 id="query" name="q" value="<%=h(StringUtils.trim(StringUtils.join(q, " ")))%>">&nbsp;
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
        %><table>
            <tr><td><font size="-1"><%
                if (null == category) { %>All<% } else { %><a href="<%=h(researchURL)%>">All</a><% } %>&nbsp;<%
                if ("File".equals(category)) { %>Files <% } else { %><a href="<%=h(researchURL.clone().addParameter("category", "File"))%>">Files</a><% } %>&nbsp;<%
                if ("Subject".equals(category)) { %>Subjects <% } else { %><a href="<%=h(researchURL.clone().addParameter("category", "Subject"))%>">Subjects</a><% } %>&nbsp;<%
                if ("Dataset".equals(category)) { %>Datasets <% } else { %><a href="<%=h(researchURL.clone().addParameter("category", "Dataset"))%>">Datasets</a><% }
             %></font></td></tr>
            <tr><td valign="top" align="left" width=700><%
        int hitsPerPage = 20;  // UNDONE
        int offset = 0;

        int pageNo = (offset / hitsPerPage) + 1;

        try
        {
            long start = System.nanoTime();
            Container searchContainer = null == form.getContainer() ? ContainerManager.getRoot() : ContainerManager.getForId(form.getContainer());
            SearchService.SearchResult result = ss.search(queryString, ss.getCategory(category), user, searchContainer, true, offset, hitsPerPage);
            long time = (System.nanoTime() - start)/1000000;
            int totalHits = result.totalHits;

            %><br>Found <%=Formats.commaf0.format(totalHits)%> result<%=totalHits != 1 ? "s" : ""%> in <%=Formats.commaf0.format(time)%>ms.<br><%

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
                String summary = StringUtils.trimToNull(hit.summary);

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

                %><a href="<%=h(hit.url)%>"><%=h(hit.displayTitle)%></a><div style='margin-left:10px; width:690px;'><%
                if (null != summary)
                {
                    %><%=PageFlowUtil.filter(summary, false)%><br><%
                }
                if (form.isAdvanced())
                {
                    %><span style='color:green;'><%=h(href)%></span><%
                }
                else
                {
                    NavTree nav = getDocumentContext(hit);
                    %><a style='color:green;' href="<%=h(nav.getValue())%>"><%=h(nav.getKey())%></a><%
                }
                if (!StringUtils.isEmpty(hit.navtrail))
                {
                    %>&nbsp;<%=formatNavTrail(hit.navtrail)%><br><%
                }
                %></div><br><%
            }
            %></div></td><%

            if (-1 == queryString.indexOf("searchCategory") && wideView)
            {
                result = ss.search(queryString, SearchService.navigationCategory, user, ContainerManager.getRoot(), true, offset, hitsPerPage);

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
                        %><%--<div style='margin-left:10px; color:green;'><%=h(href)%></div><br>--%><%
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
<%!
String formatNavTrail(String s)
{
    try
    {
        JSONArray a;
        if (s.startsWith("["))
        {
            a = new JSONArray(s);
        }
        else if (s.startsWith("{"))
        {
            JSONObject o = new JSONObject(s);
            a = new JSONArray(new Object[]{o});
        }
        else
            return "";
        int length = a.length();
        StringBuilder sb = new StringBuilder("<span style='color:#808080;'>");
        String connector = " - ";
        for (int i=0 ; i<length ; i++)
        {
            JSONObject o = a.getJSONObject(i);
            String text = o.getString("text");
            String href = o.getString("href");
            if (!StringUtils.isEmpty(text) && !StringUtils.isEmpty(href))
            {
                sb.append(connector);
                sb.append("<a style='text-decoration:underline; color:#808080;' href='" + PageFlowUtil.filter(href) + "'>" + PageFlowUtil.filter(text) + "</a>");
                connector = " - ";
            }
        }
        sb.append("</span>");
        return sb.toString();
    }
    catch (Throwable t)
    {
        return "";
    }
}

Path files = new Path("@files");
Path pipeline = new Path("@pipeline");
Path dav = new Path("_webdav");
    
NavTree getDocumentContext(org.labkey.api.search.SearchService.SearchHit hit)
{
    Container c = ContainerManager.getForId(hit.container);
    String text = c.getPath();
    ActionURL url = c.getStartURL(getViewContext());

    try
    {
        if (hit.docid.startsWith("dav:"))
        {
            Path containerPath = c.getParsedPath();
            Path path = Path.parse(hit.docid.substring(4));
            if (path.startsWith(dav))
                path = dav.relativize(path);
            if (path.startsWith(containerPath))
            {
                Path rel = containerPath.relativize(path);
                if (rel.startsWith(files) || rel.startsWith(pipeline))
                {
                    text = path.getParent().toString("/","");
                    url = new ActionURL("project","fileBrowser",c).addParameter("path",rel.encode());
                }
            }
        }
    }
    catch (Exception x)
    {
    }
    NavTree ret = new NavTree(text, url);
    return ret;
}
%>