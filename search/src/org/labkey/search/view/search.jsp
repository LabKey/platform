<%
/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.jetbrains.annotations.NotNull" %>
<%@ page import="org.jetbrains.annotations.Nullable" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.search.SearchMisconfiguredException" %>
<%@ page import="org.labkey.api.search.SearchResultTemplate" %>
<%@ page import="org.labkey.api.search.SearchScope" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.search.SearchUtils" %>
<%@ page import="org.labkey.api.search.SearchUtils.HtmlParseException" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.webdav.WebdavResource" %>
<%@ page import="org.labkey.search.SearchController" %>
<%@ page import="org.labkey.search.SearchController.SearchForm" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext3"));
        return resources;
    }
%>
<%
    JspView<SearchForm> me = (JspView<SearchForm>) HttpView.currentView();
    SearchForm form = me.getModelBean();
    ViewContext ctx = getViewContext();
    Container c = getContainer();
    User user = getUser();
    Path contextPath = Path.parse(ctx.getContextPath());
    SearchService ss = ServiceRegistry.get().getService(SearchService.class);
    boolean wideView = true;
    List<String> q = new ArrayList<>(Arrays.asList(form.getQ()));
    SearchResultTemplate template = form.getSearchResultTemplate();
    SearchScope scope = (null == template.getSearchScope() ? form.getSearchScope() : template.getSearchScope());
    String categories = (null == template.getCategories() ? form.getCategory() : template.getCategories());
    String queryString = form.getQueryString();

    SearchController.SearchConfiguration searchConfig = form.getConfig();
    boolean showAdvancedUI = !form.isWebPart() && searchConfig.includeAdvancedUI() && template.includeAdvanceUI();

    // if login status changes, cookie will change, and we want to not cache this page
    if (!StringUtils.isEmpty(queryString))
        response.setHeader("Vary", "Cookie");

    if (showAdvancedUI)
    { %>
<style type="text/css">
    .x-panel-body {
        background-color: transparent; /* Fix 10930: Advanced search background is solid white. */
    }
</style>
<form class="labkey-search-form" style="padding-bottom: 0" id="searchForm" name="search" onsubmit="resubmit(); return true;" action="<%=h(searchConfig.getPostURL(c))%>">
 <% }
    else
    { %>
<form class="labkey-search-form" style="padding-bottom: 0" id="searchForm" name="search" action="<%=h(searchConfig.getPostURL(c))%>">
<%  } %>
    <table><tr>
        <td><%
    if (form.isPrint())
    {
        %><input type="hidden" name="_print" value=1><%
    }

    %>
        <input type="hidden" name="_dc" value="<%=Math.round(1000 * Math.random())%>">
        <input type="text" size="<%=form.getTextBoxWidth()%>" id="query" name="q" value="<%=h(StringUtils.trim(StringUtils.join(q, " ")))%>"></td>
        <td><div style="margin: 2px 0; float: left;"><%= button("Search").submit(true) %></div><%

    if (form.getIncludeHelpLink())
    {
        %>&nbsp;&nbsp;&nbsp;<%=SearchUtils.getHelpTopic().getLinkHtml("help")%><%
    }

    if (showAdvancedUI)
    {
        %>
        <input type="hidden" id="hidden-category" name="category">
        <input type="hidden" id="hidden-showAdv" name="showAdvanced" value="true" ><%
    }

    if (null == template.getSearchScope())
    {
        %>
        <input type="hidden" id="hidden-scope" name="scope" value="<%=form.getSearchScope()%>"><%
    }

    if (null != form.getTemplate())
    {
        %>
        <input type="hidden" id="hidden-template" name="template" value="<%=form.getTemplate()%>"><%
    }

    if (null != getActionURL().getParameter("status"))
    {
        %>
        <input type="hidden" id="search-type" name="status" value="<%=getActionURL().getParameter("status")%>"><%
    }   %>

        </td></tr>
    </table>
</form>
<%
    if (showAdvancedUI)
    {
%>
<table width=100% cellpadding="0" cellspacing="0" style="padding-left: 10px;">
    <tr>
        <td>
            <input id="adv-search-btn" type="image" src="<%=getWebappURL("_images/plus.gif")%>" onclick="showPanel(); return false;"><span> Advanced Search</span>
        </td>
    </tr>
    <tr>
        <td>
            <div id="advancedPanelDiv" <%= form.isShowAdvanced() ? "" : "style=\"display: none;\""%>></div>
        </td>
    </tr>
</table>
<%
    }

    String extraHtml = template.getExtraHtml(ctx);

    if (null != extraHtml)
        out.print(extraHtml);

    if (null != StringUtils.trimToNull(queryString))
    {
        %><table cellspacing=0 cellpadding=0 style="margin-top:10px;">
        <tr><td valign="top" align="left" style="padding-right:10px;"><img title="" src="<%=getWebappURL("_.gif")%>" width=500 height=1>
           <div id="searchResults" class="labkey-search-results">
               <%

        int hitsPerPage = 20;
        int offset = form.getOffset();
        int pageNo = (offset / hitsPerPage) + 1;

        try
        {
            SearchService.SearchResult result = searchConfig.getPrimarySearchResult(template.reviseQuery(ctx, queryString), categories, user, c, scope, offset, hitsPerPage);

            int primaryHits = result.totalHits;
            int pageCount = (int)Math.ceil((double)primaryHits / hitsPerPage);

            %>
            <table cellspacing=0 cellpadding=0 width=100%><%
               boolean includesSecondarySearch = false;

               if (searchConfig.hasSecondaryPermissions(user))
               {
                   includesSecondarySearch = true;
                   SearchService.SearchResult secondaryResult = searchConfig.getSecondarySearchResult(queryString, categories, user, c, scope, offset, hitsPerPage);

                   %>
               <tr><td align=left colspan="2"><%
                   if (secondaryResult.totalHits > 0)
                   { %>
                   <a href="<%=h(searchConfig.getSecondarySearchURL(c, queryString))%>"><%
                   }

                   out.print(getResultsSummary(secondaryResult.totalHits, searchConfig.getSecondaryDescription(c), template.getResultName(), "click to view"));

                   if (secondaryResult.totalHits > 0)
                   { %>
                   </a><%
                   } %>
               </td></tr><%
               }
               %>
               <tr><td align=left><%=getResultsSummary(primaryHits, includesSecondarySearch ? searchConfig.getPrimaryDescription(c) : null, template.getResultName(), includesSecondarySearch ? "shown below" : null)%></td><%

            if (hitsPerPage < primaryHits)
            {
                %><td align=right>Displaying page <%=Formats.commaf0.format(pageNo)%> of <%=Formats.commaf0.format(pageCount)%></td><%
            }
            else if (primaryHits > 0)
            {
                %><td align=right>Displaying all <%=h(template.getResultName())%>s</td><%
            }
            %></tr></table><br>
            <%

            for (SearchService.SearchHit hit : result.hits)
            {
                Container documentContainer = ContainerManager.getForId(hit.container);

                String href = normalizeHref(documentContainer, contextPath, hit.url);
                String summary = StringUtils.trimToNull(hit.summary);
                %>

                <a class="labkey-search-title" href="<%=h(href)%>"><%=h(hit.title)%></a><div style='margin-left:10px; width:600px;'><%

                if (null != summary)
                {
                    %><%=h(summary, false)%><br><%
                }

                NavTree nav = getDocumentContext(documentContainer, hit);
                if (null != nav)
                {
                    %><a style='color:green;' href="<%=h(nav.getHref())%>"><%=h(nav.getText())%></a><%
                    if (!nav.getHref().equals(documentContainer.getStartURL(user).toString())) {
                        %> in <a style='color:green;' href="<%=h(documentContainer.getStartURL(user))%>"><%=h(documentContainer.getPath())%></a><%
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

                ActionURL currentURL = getActionURL();

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
                        out.print(text("&nbsp;|&nbsp;"));
                }

                if (pageNo < pageCount)
                { %><a href="<%=h(currentURL.clone().replaceParameter("offset", String.valueOf(offset + hitsPerPage)))%>">Next &gt;</a><%
                } %>
                </div><%
            }

            %></div></td><%

            if (null == categories && wideView && searchConfig.includeNavigationLinks() && scope != SearchScope.Folder)
            {
                result = ss.search(queryString, Arrays.asList(SearchService.navigationCategory), user, c, scope, offset, hitsPerPage);

                if (result.hits.size() > 0)
                {
                    %><td valign="top" align="left"><img title="" src="<%=getWebappURL("_.gif")%>" width=200 height=1><%
                    %><div id="navigationResults" class="labkey-search-navresults"><h3>Folders</h3><%

                    for (SearchService.SearchHit hit : result.hits)
                    {
                        %><table><tr><td><img src="<%=getContextPath()%>/_icons/folder.gif"></td><td><a class="labkey-search-title" href="<%=h(hit.url)%>"><%=h(hit.title)%></a></td></tr></table><%
                        String summary = StringUtils.trimToNull(hit.summary);
                        if (null != summary)
                        {
                            %><div style="margin-left:10px;"><%=h(summary, false)%></div><%
                        }
                    }

                    %><br><%
                    %></div><%
                    %></td><%
                }
            }
        }
        catch (HtmlParseException html)
        {
            out.write("</div>");
            SearchUtils.renderError(out, html.getMessage(), html.includesSpecialSymbol(), html.includesBooleanOperator(), true);
            out.write("</td>");
        }
        catch (IOException e)
        {
            out.write("</div>");
            SearchUtils.renderError(out, h(e.getMessage()), true, false, true);  // Assume it's special characters
            out.write("</td>");
        }
        catch (SearchMisconfiguredException e)
        {
            out.write("</div>");
            SearchUtils.renderError(out, "Search is disabled because the search index is misconfigured. Contact the system administrator of this server.", false, false, false);
            out.write("</td>");
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
            sb.append("<a style='text-decoration:underline; color:#808080;' href='").append(h(n.getHref())).append("'>").append(h(n.getText())).append("</a>");
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
        ArrayList<NavTree> list = new ArrayList<>(length);
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

SearchService ss = ServiceRegistry.get(SearchService.class);

Collection<NavTree> getActions(SearchService.SearchHit hit)
{
    String docid = hit.docid;
    if (null == docid)
        return null;
    WebdavResource r = ss.resolveResource(docid);
    if (null == r || !r.exists())
        return null;
    Collection<NavTree> nav = r.getActions(getUser());
    return nav.isEmpty() ? null : nav;
}

Path files = new Path("@files");
Path pipeline = new Path("@pipeline");
Path dav = new Path("_webdav");

NavTree getDocumentContext(Container c, SearchService.SearchHit hit)
{
    if (null == c)
    {
        if (null == hit.url)
            return null;
        else
            return new NavTree(hit.url, hit.url);
    }
    else
    {
        String text = c.getPath();
        ActionURL url = c.getStartURL(getUser());

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
                        url = urlProvider(ProjectUrls.class).getFileBrowserURL(c, rel.toString("/",""));
                    }
                }
            }
        }
        catch (Exception x)
        {
        }

        return new NavTree(text, url);
    }
}


String getResultsSummary(int totalHits, @Nullable String description, @NotNull String resultName, @Nullable String nonZeroInstruction)
{
    StringBuilder sb = new StringBuilder("Found ");
    sb.append(Formats.commaf0.format(totalHits));

    if (null != description)
    {
        sb.append(" ");
        sb.append(h(description));
    }

    sb.append(" ").append(h(resultName));

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


String normalizeHref(Container c, Path contextPath, String href)
{
    // see issue #11481
    if (href.startsWith("files/"))
        href = "/" + href;
        
    try
    {
        if (null != c && href.startsWith("/"))
        {
            URLHelper url = new URLHelper(href);
            Path path = url.getParsedPath();
            if (path.startsWith(contextPath))
            {
                int pos = contextPath.size() + 1;
                if (path.size() > pos && c.getId().equals(path.get(pos)))
                {
                    path = path.subpath(0,pos)
                            .append(c.getParsedPath())
                            .append(path.subpath(pos+1,path.size()));
                    url.setPath(path);
                    return url.getLocalURIString(false);
                }
            }
        }
    }
    catch (Exception x)
    {
        //
    }
    return href;
}

%>
<%
    if (showAdvancedUI)
    {
%>
<script type="text/javascript">
    var params = LABKEY.ActionURL.getParameters();
    var seen = false;
    var initOpen = true;

    var resubmit = function() {
        params['q'] = document.getElementById('query').value;

        checkOptions('adv-category', 'category');

        var el = document.getElementById('hidden-showAdv');
        if (!seen && el) {
            el.disabled = "disabled";
        }
    };

    var checkOptions = function(el, param) {

        params[param] = ""; // reset parameter
        var catEl = document.getElementById('hidden-category');

        var cat = Ext.getCmp(el);
        if (cat) {
            cat = cat.getValue();
        }

        if (cat && cat.length > 0) {
            params[param] = cat[0].value;
            for (var j = 1; j < cat.length; j++) {
                params[param] += " " + cat[j].value;
            }
            catEl.value = params[param];
        }
        else if (catEl) {
            catEl.disabled = "disabled";
        }
    };

    var init = function() {

        var header = {
            html : <%=PageFlowUtil.jsString("<span>Categories" + helpPopup("Categories", "Choosing one or more categories will refine your search to only those data types. For example, if you select 'Files' you will see only files and attachments in your " + h(template.getResultName()) + ".") + "</span>")%>
        };

        var categories = {
            html  : <%=PageFlowUtil.jsString("<span>Scope" + helpPopup("Scope", "Scoping allows the search to be refined to the contents of the entire site (default), contents of this project including sub-folders, or contents of just this folder.") + "</span>")%>,
            items: {
                id        : 'adv-category',
                xtype     : 'checkboxgroup',
                columns   : [90,90,90,100],
                autoHeight: true,
                defaults  : {
                    name  : 'category',
                    listeners : {
                        afterrender: function(chkbox) {
                            var cats = LABKEY.ActionURL.getParameter('category');
                            if (cats)
                            {
                                cats = cats.split('+');
                                for (var i = 0; i < cats.length; i++)
                                {
                                    if (cats[i] == chkbox.value)
                                    {
                                        chkbox.setValue(true);
                                    }
                                }
                            }
                        }
                    }
                },
                items : [{
                    boxLabel: 'Files',
                    value   : 'File'
                },{
                    boxLabel: 'Subjects',
                    value   : 'Subject'
                },{
                    boxLabel: 'Datasets',
                    value   : 'Dataset'
                },{
                    boxLabel: 'Assays',
                    value   : 'Assay'
                },{
                    boxLabel: 'Wikis',
                    value   : 'Wiki'
                },{
                    boxLabel: 'Lists',
                    value   : 'List'
                },{
                    boxLabel: 'Issues',
                    value   : 'Issue'
                },{
                    boxLabel: 'Messages',
                    value   : 'Message'
                }]
            }
        };

        var scopes = {
            items: {
                id        : 'adv-scope',
                xtype     : 'radiogroup',
                columns   : 1,
                autoHeight: true,
                listeners: {
                    afterrender : function(group)
                    {
                        // Check the option button associated with the current scope
                        var scope = <%= PageFlowUtil.jsString(form.getScope()) %>;

                        if (!(scope in {"Project":1, "Folder":1, "FolderAndSubfolders":1}))
                        {
                            scope = 'All';
                        }

                        group.setValue("cb_" + scope, true);
                    },
                    change : function(group, chkbox)
                    {
                        var scopeEl = document.getElementById('hidden-scope');

                        if (chkbox.id == 'cb_All')
                        {
                            scopeEl.disabled = true;
                        }
                        else
                        {
                            scopeEl.disabled = false;
                            scopeEl.value = chkbox.id.substring(3);
                        }
                    }
                },
                items : [{
                    boxLabel: 'Site',
                    name    : 'scope',
                    id      : 'cb_<%=SearchScope.All.name()%>'
                },{
                    boxLabel: 'Project',
                    disabled: <%=c.equals(ContainerManager.getRoot())%>,
                    name    : 'scope',
                    id      : 'cb_<%=SearchScope.Project.name()%>'
                },{
                    boxLabel: 'Current Folder',
                    name    : 'scope',
                    id      : 'cb_<%=SearchScope.Folder.name()%>'
                },{
                    boxLabel: 'Current Folder & SubFolders',
                    name    : 'scope',
                    id      : 'cb_<%=SearchScope.FolderAndSubfolders.name()%>'
                }]
            }
        };

        /* The panel that contains the Advanced Search options. */
        var panel = new Ext.Panel({
            id : 'advanced-panel',
            renderTo: 'advancedPanelDiv',
            width: 345,                            // Fix 10502 : Extended the width to stop word wrapping.
            items: [header, categories, scopes],
            border: false,
            defaults: {
                layout : 'form',
                border: false,
                style : {
                    padding : '5px'
                }
            }

            <% if (form.isShowAdvanced()) { %>

            ,listeners : {
                beforerender : function(pnl) {
                    showPanel();
                }
            }

            <% } %>
        });
    };

    var showPanel = function() {

        var wrapEl = Ext.get('advancedPanelDiv');

        if (wrapEl) {

            var p = Ext.getCmp('advanced-panel');
            var el = document.getElementById('adv-search-btn');

            if (!seen) {
                wrapEl.show();
                p.show();
                p.doLayout();

                if (<%=form.isShowAdvanced()%> && initOpen) {
                    initOpen = false;
                }
                seen = true;
                el.src = LABKEY.contextPath + "/_images/minus.gif";
            }
            else {
                wrapEl.hide();
                p.hide();
                wrapEl.setStyle('display', 'none');

                seen = false;
                el.src = LABKEY.contextPath + "/_images/plus.gif";
            }
        }
    };

    Ext.onReady(init);
</script>
<%
    }
%>
