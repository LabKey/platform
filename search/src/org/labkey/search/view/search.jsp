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
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.webdav.WebdavResource" %>
<%@ page import="org.labkey.api.webdav.WebdavService" %>
<%@ page import="org.labkey.search.SearchController" %>
<%@ page import="org.labkey.search.SearchController.IndexAction" %>
<%@ page import="org.labkey.search.SearchController.SearchForm" %>
<%@ page import="org.labkey.search.SearchController.SearchForm.SearchScope" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page import="org.jetbrains.annotations.Nullable" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<script type="text/javascript">

    function loginStatusChanged()
    {
        return LABKEY.user.sessionid != LABKEY.Utils.getCookie("JSESSIONID");
    }
    if (loginStatusChanged())
        window.location.reload(true);

    var params = {};
    var isModified = false;

    function establishParams() {
        if (!(isModified))
        {
            params['q'] = document.getElementById('query').value;
        }
        params["_dc"] = document.getElementsByName('_dc')[0].value;
        var _newSearchURL = LABKEY.ActionURL.buildURL(
                LABKEY.ActionURL.getController(),
                "search",
                LABKEY.ActionURL.getContainer(),
                params);
        return _newSearchURL;
    }

    function modifiedSearch(key, value)
    {
        if(!(key) && !(value))
        {
            // User modified the search string
            isModified = true;
            console.info("User modified search: " + document.getElementById('query').value);
            params['q'] = document.getElementById('query').value;
        }
        else if (!(value))
        {
            params.remove(key);
        }
        else
        {
            params[key] = value;
        }
    }

    function resubmit()
    {
        window.location = establishParams();
    }

    function showURL(obj, key, value)
    {
        if (value)
        {
            params[key] = value;
        }
        else
        {
            delete params[key];
        }
        obj.href = establishParams();
    }
    
</script>
<%
    JspView<SearchForm> me = (JspView<SearchForm>) HttpView.currentView();
    SearchForm form = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();
    User user = ctx.getUser();
    String contextPath = ctx.getContextPath();
    SearchService ss = ServiceRegistry.get().getService(SearchService.class);
    boolean wideView = true;
    List<String> q = new ArrayList<String>(Arrays.asList(form.getQ()));

    SearchController.SearchParameters params = form.getParams();

    if (form.isAdvanced())
    {
%>
[<a href="<%=h(new ActionURL(IndexAction.class, c).addParameter("full", "1"))%>">reindex (full)</a>]<br>
[<a href="<%=h(new ActionURL(IndexAction.class, c))%>">reindex (incremental)</a>]<br><%
    }
%>
<form class="labkey-search-form" id=searchForm name="search" action="<%=h(params.getPostURL(c))%>">
    <table><tr><td><%
    if (form.isPrint())
    {
        %><input type=hidden name=_print value=1><%
    }

    if (null != form.getCategory())
    {
        %><input type=hidden name=category value="<%=h(form.getCategory())%>"><%
    }

    if (!form.getIncludeSubfolders())
    {
        %><input type=hidden name=includeSubfolders value="0"><%
    }

    if (null != form.getContainer())
    {
        %><input type=hidden name=container value="<%=form.getContainer()%>"><%
    }

    %>
    <input type="hidden" name="_dc" value="<%=Math.round(1000*Math.random())%>">
    <input type="text" size="<%=form.getTextBoxWidth()%>" id="query" name="q" onchange="modifiedSearch()" value="<%=h(StringUtils.trim(StringUtils.join(q, " ")))%>"></td>
    <td>&nbsp;<%=generateSubmitButton("Search")%><%

    if (form.getIncludeHelpLink())
    {
        %>&nbsp;&nbsp;&nbsp;<%=textLink("help", new HelpTopic("luceneSearch").getHelpTopicLink())%><%
    }

    %></td></tr><%

    String category = form.getCategory();
    ActionURL categoryResearchURL = ctx.cloneActionURL().deleteParameter("category");
    ActionURL scopeResearchURL = ctx.cloneActionURL().deleteParameter("includeSubfolders").deleteParameter("container");
    String queryString = form.getQueryString();

    if (params.includeAdvancedUI() && null != StringUtils.trimToNull(queryString))
    {
        %><tr><td colspan=1>
        <table width=100% cellpadding="0" cellspacing="0"><tr>
            <td class="labkey-search-filter"><%
                %><span><%if (null == category)           { %>All<%      } else { %><a onmouseover="showURL(this, 'category', null);" onclick="resubmit();">All</a><% } %></span>&nbsp;<%
                %><span><%if ("File".equals(category))    { %>Files<%    } else { %><a onmouseover="showURL(this, 'category', 'File');" onclick="resubmit();">Files</a><% } %></span>&nbsp;<%
                %><span><%if ("Subject".equals(category)) { %>Subjects<% } else { %><a onmouseover="showURL(this, 'category', 'Subject');" onclick="resubmit();">Subjects</a><% } %></span>&nbsp;<%
                %><span><%if ("Dataset".equals(category)) { %>Datasets<% } else { %><a onmouseover="showURL(this, 'category', 'Dataset');" onclick="resubmit();">Datasets</a><% } %></span><%
            %></td>
            <td class="labkey-search-filter" align="right" style="width:100%"><%
                SearchScope scope = form.getSearchScope(c);
                %><span title="<%=h(LookAndFeelProperties.getInstance(c).getShortName())%>"><%if (scope == SearchScope.All) { %>Site<% } else { %><a onmouseover="showURL(this, 'container', null);" onclick="resubmit();">Site</a><% } %></span>&nbsp;<%

                Container project = c.getProject();

                // Skip the "Project" and "Folder" links if we're at the root
                if (null != project)
                {
                    %><span title="<%=h(c.getProject().getName())%>"><%if (scope == SearchScope.Project) { %>Project<% } else { %><a onmouseover="showURL(this, 'container', '<%=c.getProject().getId()%>');" onclick="resubmit();">Project</a><% } %></span>&nbsp;<%
                    %><span title="<%=h(c.getName())%>"><%if (scope == SearchScope.Folder && !form.getIncludeSubfolders()) { %>Folder<% } else { %><a onmouseover="showURL(this, 'container', '<%=c.getId()%>');" onclick="resubmit();">Folder</a><% } %></span><%
                }
            %></td>
            <td>&nbsp;</td>
        </tr></table>
        </td></tr><%
    }
    %>
    </table>
</form><%

    if (null != StringUtils.trimToNull(queryString))
    {
        %><table cellspacing=0 cellpadding=0 style="margin-top:10px;">
        <tr><td valign="top" align="left" style="padding-right:10px;"><img title="" src="<%=contextPath%>/_.gif" width=500 height=1>
           <div id="searchResults" class="labkey-search-results"><%

        int hitsPerPage = 20;
        int offset = form.getOffset();
        int pageNo = (offset / hitsPerPage) + 1;

        try
        {
            SearchService.SearchResult result = params.getPrimarySearchResult(queryString, category, user, form.getSearchContainer(), form.getIncludeSubfolders(), offset, hitsPerPage);

            int primaryHits = result.totalHits;
            int pageCount = (int)Math.ceil((double)primaryHits / hitsPerPage);

            %>
            <table cellspacing=0 cellpadding=0 width=100%><%
               boolean includesSecondarySearch = false;

               if (params.hasSecondaryPermissions(user))
               {
                   includesSecondarySearch = true;
                   SearchService.SearchResult secondaryResult = params.getSecondarySearchResult(queryString, category, user, form.getSearchContainer(), form.getIncludeSubfolders(), offset, hitsPerPage);

                   %>
               <tr><td align=left colspan="2"><%
                   if (secondaryResult.totalHits > 0)
                   { %>
                   <a href="<%=h(params.getSecondarySearchURL(c, queryString))%>"><%
                   }

                   out.print(getResultsSummary(secondaryResult.totalHits, params.getSecondaryDescription(c), "click to view"));

                   if (secondaryResult.totalHits > 0)
                   { %>
                   </a><%
                   } %>
               </td></tr><%
               }
               %>
               <tr><td align=left colspan="2">&nbsp;</td>
               <tr><td align=left><%=getResultsSummary(primaryHits, includesSecondarySearch ? params.getPrimaryDescription(c) : null, includesSecondarySearch ? "shown below" : null)%></td><%

            if (hitsPerPage < primaryHits)
            {
                %><td align=right>Displaying page <%=Formats.commaf0.format(pageNo)%> of <%=Formats.commaf0.format(pageCount)%></td><%
            }
            else if (primaryHits > 0)
            {
                %><td align=right>Displaying all results</td><%
            }
            %></tr></table><br>
            <%

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

                %>

<a class="labkey-search-title" href="<%=h(hit.url)%>"><%=h(hit.displayTitle)%></a><div style='margin-left:10px; width:600;'><%
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
                    if (null != nav)
                    {
                        %><a style='color:green;' href="<%=h(nav.getValue())%>"><%=h(nav.getKey())%></a><%
                    }
                }
                if (!StringUtils.isEmpty(hit.navtrail))
                {
                    %>&nbsp;<%=formatNavTrail(parseNavTrail(hit.navtrail))%><%
                }
                Collection<NavTree> actions = getActions(hit);
                if (null != actions && !actions.isEmpty())
                {
                    %>&nbsp;<%=formatNavTrail(getActions(hit))%><%
                }
                %></div><br><%
            }

            if (pageCount > 1)
            { %>
                <div style="text-align:center;"><%

                ActionURL currentURL = ctx.getActionURL();

                if (pageNo > 1)
                {
                    ActionURL previousURL = currentURL.clone();

                    int newOffset = offset - hitsPerPage;

                    if (newOffset > 0)
                        previousURL.replaceParameter("offset", String.valueOf(newOffset));
                    else
                        previousURL.deleteParameter("offset");
                %>
                    <a href="<%=h(previousURL)%>">&lt; Previous</a><%

                    if (pageNo < pageCount)
                        out.print("&nbsp;|&nbsp;");
                }

                if (pageNo < pageCount)
                { %><a href="<%=h(currentURL.clone().replaceParameter("offset", String.valueOf(offset + hitsPerPage)))%>">Next &gt;</a><%
                } %>
                </div><%
            }

            %></div></td><%

            if (null == category && wideView && form.getSearchScope(null) != SearchScope.Folder)
            {
                result = ss.search(queryString, SearchService.navigationCategory, user, form.getSearchContainer(), true, offset, hitsPerPage);

                if (result.hits.size() > 0)
                {
                    %><td valign="top" align="left"><img title="" src="<%=contextPath%>/_.gif" width=200 height=1><%
                    %><div id="navigationResults" class="labkey-search-navresults"><h3>Folders</h3><%

                    for (SearchService.SearchHit hit : result.hits)
                    {
                        %><table><tr><td><img src="<%=contextPath%>/_icons/folder.gif"></td><td><a class="labkey-search-title" href="<%=h(hit.url)%>"><%=h(hit.displayTitle)%></a></td></tr></table><%
                        String summary = StringUtils.trimToNull(hit.summary);
                        if (null != summary)
                        {
                            %><div style="margin-left:10px;"><%=PageFlowUtil.filter(summary, false)%></div><%
                        }
                    }

                    %><br><%
                    %></div><%
                    %></td><%
                }
            }
        }
        catch (IOException e)
        {
            out.write(h("Error: " + e.getMessage()));
            out.write("</div></td>");
        } %>
        </tr>
    </table><%
    }
%>
<%!
String formatNavTrail(Collection<NavTree> list)
{
    if (null == list || list.isEmpty())
        return "";

    try
    {
        StringBuilder sb = new StringBuilder("<span style='color:#808080;'>");
        String connector = " - ";
        for (NavTree n : list)
        {
            sb.append(connector);
            sb.append("<a style='text-decoration:underline; color:#808080;' href='").append(PageFlowUtil.filter(n.getValue())).append("'>").append(PageFlowUtil.filter(n.getKey())).append("</a>");
            connector = " - ";
        }
        sb.append("</span>");
        return sb.toString();
    }
    catch (Throwable t)
    {
        return "";
    }
}


List<NavTree> parseNavTrail(String s)
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
            return null;

        int length = a.length();
        ArrayList<NavTree> list = new ArrayList<NavTree>(length);
        for (int i=0 ; i<length ; i++)
        {
            JSONObject o = a.getJSONObject(i);
            String text = o.getString("text");
            String href = o.getString("href");

            if (!StringUtils.isEmpty(text) && !StringUtils.isEmpty(href))
                list.add(new NavTree(text, href));
        }
        return list;
    }
    catch (Throwable t)
    {
        return null;
    }
}


Collection<NavTree> getActions(SearchService.SearchHit hit)
{
    String docid = hit.docid;
    if (null == docid || !docid.startsWith("dav:"))
        return null;
    Path p = Path.parse(docid.substring(4));
    WebdavResource r = WebdavService.get().getResolver().lookup(p);
    if (null == r || !r.exists())
        return null;
    Collection<NavTree> nav = r.getActions(HttpView.currentContext().getUser());
    return nav.isEmpty() ? null : nav;
}

Path files = new Path("@files");
Path pipeline = new Path("@pipeline");
Path dav = new Path("_webdav");

NavTree getDocumentContext(org.labkey.api.search.SearchService.SearchHit hit)
{
    Container c = ContainerManager.getForId(hit.container);
    if (null == c)
        return null;
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
                    if (path.size() > 0) path = path.getParent();
                    text = path.toString("/","");

                    if (rel.size() > 0) rel = rel.getParent();
                    url = new ActionURL("project","fileBrowser",c).addParameter("path",rel.toString("/",""));
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


String getResultsSummary(int totalHits, @Nullable String description, @Nullable String nonZeroInstruction)
{
    StringBuilder sb = new StringBuilder("Found ");
    sb.append(Formats.commaf0.format(totalHits));

    if (null != description)
    {
        sb.append(" ");
        sb.append(h(description));
    }

    sb.append(" result");

    if (totalHits != 1)
        sb.append("s");

    if (null != nonZeroInstruction && totalHits > 0)
    {
        sb.append(" (");
        sb.append(nonZeroInstruction);
        sb.append(")");
    }

    return sb.toString();
}
%>
