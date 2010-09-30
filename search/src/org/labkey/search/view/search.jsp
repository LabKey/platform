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
<%@ page import="org.jetbrains.annotations.Nullable" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>                 
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.*" %>
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<script type="text/javascript">

    function loginStatusChanged()
    {
        return LABKEY.user.sessionid != LABKEY.Utils.getCookie("JSESSIONID");
    }
    if (loginStatusChanged())
        window.location.reload(true);
    
</script>
<%
    JspView<SearchForm> me = (JspView<SearchForm>) HttpView.currentView();
    SearchForm form = me.getModelBean();
    ViewContext ctx = me.getViewContext();
    Container c = ctx.getContainer();
    User user = ctx.getUser();
    String contextPathStr = ctx.getContextPath();
    Path contextPath = Path.parse(contextPathStr);
    SearchService ss = ServiceRegistry.get().getService(SearchService.class);
    boolean wideView = true;
    List<String> q = new ArrayList<String>(Arrays.asList(form.getQ()));

    SearchController.SearchConfiguration searchConfig = form.getConfig();

    if (form.isAdvanced())
    {
%>
<%=textLink("reindex (full)", new ActionURL(IndexAction.class, c).addParameter("full", "1"))%><br>
<%=textLink("reindex (incremental)", new ActionURL(IndexAction.class, c))%><br><%
    }

    if (!form.isWebPart() && searchConfig.includeAdvancedUI())
    { %>
<form class="labkey-search-form" style="padding-bottom: 0px" id="searchForm" name="search" onsubmit="resubmit(); return true;" action="<%=h(searchConfig.getPostURL(c))%>">
 <% }
    else
    { %>
<form class="labkey-search-form" style="padding-bottom: 0px" id="searchForm" name="search" action="<%=h(searchConfig.getPostURL(c))%>">
<%  } %>
    <table><tr><td><%
    if (form.isPrint())
    {
        %><input type="hidden" name="_print" value=1><%
    }

    %>
    <input type="hidden" name="_dc" value="<%=Math.round(1000*Math.random())%>">
    <input type="text" size="<%=form.getTextBoxWidth()%>" id="query" name="q" value="<%=h(StringUtils.trim(StringUtils.join(q, " ")))%>"></td>
    <td>&nbsp;<%=generateSubmitButton("Search")%><%

    if (form.getIncludeHelpLink())
    {
        %>&nbsp;&nbsp;&nbsp;<%=textLink("help", new HelpTopic("luceneSearch").getHelpTopicLink())%><%
    }
        
    %></td></tr><%

    String category = form.getCategory();
    String queryString = form.getQueryString();

    if (!form.isWebPart() && searchConfig.includeAdvancedUI())
    {
        %>
        <input type="hidden" id="hidden-category" name="category">
        <input type="hidden" id="hidden-showAdv" name="showAdvanced" value="true" >
        <%
    }

    if (c.isProject()){ %>
        <input type="hidden" id="hidden-container" name="container" value="<%=projectInfo(c, false)%>">
    <% }
    else
    {
    %>
        <input type="hidden" id="hidden-container" name="container" value="<%=projectInfo(c, true)%>">
    <%
    }

    if (form.getIncludeSubfolders())
    {
    %>
        <input type="hidden" id="hidden-include-folders" name="includeSubfolders" value="1" >
    <% }
    else
    {
    %>
        <input type="hidden" id="hidden-include-folders" name="includeSubfolders" value="0" > 
    <%
    } %>
    </table>
</form>
<%
    if (!form.isWebPart() && searchConfig.includeAdvancedUI())
    {
%>
<table width=100% cellpadding="0" cellspacing="0" style="padding-left: 18px;">
    <tr>
        <td><input id="adv-search-btn" type="image" src="<%=contextPathStr%>/_images/plus.gif" onclick="showPanel(); return false;"><span> Advanced Search</span></td>
    </tr>
    <tr>
        <td><%  if (form.isShowAdvanced())
                { %>
            <div id="advancedPanelDiv"></div><%
                }
                else { %>
            <div id='advancedPanelDiv' style='display: none;'></div><%  }  %>
        </td>
    </tr>
</table>
<%
    }

    if (null != StringUtils.trimToNull(queryString))
    {
        %><table cellspacing=0 cellpadding=0 style="margin-top:10px;">
        <tr><td valign="top" align="left" style="padding-right:10px;"><img title="" src="<%=contextPathStr%>/_.gif" width=500 height=1>
           <div id="searchResults" class="labkey-search-results"><%

        int hitsPerPage = 20;
        int offset = form.getOffset();
        int pageNo = (offset / hitsPerPage) + 1;

        try
        {
            SearchService.SearchResult result = searchConfig.getPrimarySearchResult(queryString, category, user, form.getSearchContainer(), c, form.getIncludeSubfolders(), offset, hitsPerPage);

            int primaryHits = result.totalHits;
            int pageCount = (int)Math.ceil((double)primaryHits / hitsPerPage);

            %>
            <table cellspacing=0 cellpadding=0 width=100%><%
               boolean includesSecondarySearch = false;

               if (searchConfig.hasSecondaryPermissions(user))
               {
                   includesSecondarySearch = true;
                   SearchService.SearchResult secondaryResult = searchConfig.getSecondarySearchResult(queryString, category, user, form.getSearchContainer(), c, form.getIncludeSubfolders(), offset, hitsPerPage);

                   %>
               <tr><td align=left colspan="2"><%
                   if (secondaryResult.totalHits > 0)
                   { %>
                   <a href="<%=h(searchConfig.getSecondarySearchURL(c, queryString))%>"><%
                   }

                   out.print(getResultsSummary(secondaryResult.totalHits, searchConfig.getSecondaryDescription(c), "click to view"));

                   if (secondaryResult.totalHits > 0)
                   { %>
                   </a><%
                   } %>
               </td></tr><%
               }
               %>
               <tr><td align=left><%=getResultsSummary(primaryHits, includesSecondarySearch ? searchConfig.getPrimaryDescription(c) : null, includesSecondarySearch ? "shown below" : null)%></td><%

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
                Container documentContainer = ContainerManager.getForId(hit.container);

                String href = normalizeHref(documentContainer, contextPath, hit.url);
                String summary = StringUtils.trimToNull(hit.summary);
                %>

<a class="labkey-search-title" href="<%=h(href)%>"><%=h(hit.displayTitle)%></a><div style='margin-left:10px; width:600;'><%
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
                    NavTree nav = getDocumentContext(documentContainer, hit);
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

            if (null == category && wideView && searchConfig.includeNavigationLinks() && form.getSearchScope() != SearchScope.Folder)
            {
                result = ss.search(queryString, Arrays.asList(SearchService.navigationCategory), user, form.getSearchContainer(), c, true, offset, hitsPerPage);

                if (result.hits.size() > 0)
                {
                    %><td valign="top" align="left"><img title="" src="<%=contextPathStr%>/_.gif" width=200 height=1><%
                    %><div id="navigationResults" class="labkey-search-navresults"><h3>Folders</h3><%

                    for (SearchService.SearchHit hit : result.hits)
                    {
                        %><table><tr><td><img src="<%=contextPathStr%>/_icons/folder.gif"></td><td><a class="labkey-search-title" href="<%=h(hit.url)%>"><%=h(hit.displayTitle)%></a></td></tr></table><%
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

NavTree getDocumentContext(Container c, org.labkey.api.search.SearchService.SearchHit hit)
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
        ActionURL url = c.getStartURL(getViewContext().getUser());

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

        return new NavTree(text, url);
    }
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


String normalizeHref(Container c, Path contextPath, String href)
{
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

String projectInfo(Container c, boolean returnID)
{
    if (returnID)
    {
        return c.getId();
    }

    if (c.getProject() != null)
    {
        return c.getProject().getId();
    }
    return "";
}

%>
<%
    if (!form.isWebPart() && searchConfig.includeAdvancedUI())
    {
%>
<script type="text/javascript">
    var params = LABKEY.ActionURL.getParameters();
    function establishParams()
    {
        params['q'] = document.getElementById('query').value;

        checkOptions('adv-category', 'category');
        checkRadio('adv-scope', 'container');

        if (!seen && document.getElementById('hidden-showAdv'))
        {
            document.getElementById('hidden-showAdv').disabled = "disabled";
        }
    }

    function checkRadio(el, param)
    {
        var rad = Ext.getCmp(el);
        params[param] = "";

        var incEl = document.getElementById('hidden-include-folders');
        var conEl = document.getElementById('hidden-container');

        if (rad && rad.getValue() && rad.getValue().value)
        {
            params[param] = rad.getValue().value;
            conEl.value = params[param];

            if (incEl)
            {
                if (rad.getValue().id === "cb_folder")
                {
                    incEl.value = "0";
                }
                else if (rad.getValue().id === "cb_project" || rad.getValue().id === "cb_folder_subfolder")
                {
                    incEl.value = "1";
                }
            }
        }
        else
        {
            if (conEl)
                conEl.disabled = "disabled";
            if (incEl)
                incEl.disabled = "disabled";
        }
    }

    function checkOptions(el, param)
    {
        var cat = Ext.getCmp(el)
        if(cat)
        {
            cat = cat.getValue();
        }
        params[param] = "";
        var catEl = document.getElementById('hidden-category');

        if (cat && cat.length)
        {
            params[param] = cat[0].value;
            for(var j = 1; j < cat.length; j++)
            {
                params[param] += " " + cat[j].value;
            }
            catEl.value = params[param];
        }
        else
        {
            if (catEl)
            {
                catEl.disabled = "disabled";
            }
        }
    }

    var seen = false;
    var initOpen = true;

    function init()
    {
        var header = {
            layout: 'form',
            html : <%=PageFlowUtil.jsString("<span>Categories" + helpPopup("Categories", "Choosing one or more categories will refine your search to only those data types. For example, if you select 'Files' you will see only files and attachments in your results.") + "</span>")%>
        };

        var categories = {
            layout: 'form',
            html  : <%=PageFlowUtil.jsString("<span>Scope" + helpPopup("Scope", "Scoping allows the search to be refined to the contents of the entire site (default), contents of this project including sub-folders, or contents of just this folder.") + "</span>")%>,
            items: {
                id        : 'adv-category',
                xtype     : 'checkboxgroup',
                columns   : [90,90,90,90],
                autoHeight: true,
                defaults  : {
                    listeners : {
                        afterrender: function(chkbox) {
                            var cats = LABKEY.ActionURL.getParameter('category');
                            if (cats)
                            {
                                cats = cats.split('+');
                                for(var i = 0; i < cats.length; i++)
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
                    value   : 'File',
                    name    : 'category'
                },{
                    boxLabel: 'Subjects',
                    value   : 'Subject',
                    name    : 'category'
                },{
                    boxLabel: 'Datasets',
                    value   : 'Dataset',
                    name    : 'category'
                },{
                    boxLabel: 'Assays',
                    value   : 'Assay',
                    name    : 'category'
                },{
                    boxLabel: 'Wikis',
                    value   : 'Wiki',
                    name    : 'category'
                },{
                    boxLabel: 'Lists',
                    value   : 'List',
                    name    : 'category'
                },{
                    boxLabel: 'Issues',
                    value   : 'Issue',
                    name    : 'category'
                },{
                    boxLabel: 'Messages',
                    value   : 'Message',
                    name    : 'category'
                }]
            }
        };

        var scopes = {
            layout: 'form',
            items: {
                id        : 'adv-scope',
                xtype     : 'radiogroup',
                columns   : 1,
                autoHeight: true,
                items : [{
                    boxLabel: 'Site',
                    id      : 'cb_site',
                    name    : 'scope',
                    listeners : {
                        check : function(chkbox, checked)
                        {
                            if (checked)
                            {
                                delete params["includeSubfolders"];
                            }
                        },
                        afterrender : function(chkbox)
                        {
                            if (!(params['container']))
                            {
                                chkbox.setValue(true);
                            }
                            else if(params['container'] == "")
                            {
                                chkbox.setValue(true);
                            }
                        }
                    }
                },{
                    boxLabel: 'Project',
                    value   : "<%=h(projectInfo(c, false))%>",
                    id      : 'cb_project',
                    name    : 'scope',
                    listeners : {
                        afterrender : function(chkbox)
                        {
                            if ((params['container'] === chkbox.value) && (LABKEY.ActionURL.getParameter('includeSubfolders') === "1"))
                            {
                                if (params['container'] != "")
                                {
                                    chkbox.setValue(true);
                                }
                            }

                            if ("<%=h(projectInfo(c, false))%>" == "")
                            {
                                chkbox.disable();
                            }
                        }
                    }
                },{
                    boxLabel: 'Current Folder',
                    id      : 'cb_folder',
                    value   : "<%=h(projectInfo(c, true))%>",
                    name    : 'scope',
                    listeners : {
                        afterrender: function(chkbox) {
                            if ((params['container'] === chkbox.value && ((LABKEY.ActionURL.getParameter('includeSubfolders') === "0") || (!LABKEY.ActionURL.getParameter('includeSubfolders')))) || ((params['container'] === chkbox.value)&&(<%=h(!c.isProject())%>)))
                            {
                                chkbox.setValue(true);
                            }
                        }
                    }
                },{
                    boxLabel: 'Current Folder & SubFolders',
                    id      : 'cb_folder_subfolder',
                    value   : "<%=h(projectInfo(c, true))%>",
                    name    : 'scope',
                    listeners : {
                        afterrender: function(chkbox) {
                            if (params['container'] === chkbox.value && (LABKEY.ActionURL.getParameter('includeSubfolders') === "1") && <%=h(!c.isProject())%>)
                            {
                                chkbox.setValue(true);
                            }
                        }
                    }
                }]
            }
        };

        /* The panel that contains the Advanced Search options. */
        var panel = new Ext.Panel({
            id : 'advanced-panel',
            renderTo: 'advancedPanelDiv',
            width: 340,                            // Fix 10502 : Extended the width to stop word wrapping.
            items: [header, categories, scopes],
            border: false,
            defaults: {
                border: false,
                style : {
                    padding : '5px'
                }
            },
            listeners : {
                beforerender : function(pnl) {
                    <% if (form.isShowAdvanced()) {%>
                        showPanel();
                    <% } %>
                }
            }
        });
    }

    // This is to swap out the image on the form +/-
    var minus_img = new Image();
    minus_img.src = LABKEY.contextPath + "/_images/minus.gif";
    var org_src   = LABKEY.contextPath + "/_images/plus.gif";
    
    function showPanel()
    {
        var ppanel = Ext.get('advancedPanelDiv');
        if(!(seen) && ppanel)
        {
            Ext.getCmp('advanced-panel').show();
            var adv = <%=form.isShowAdvanced()%>;
            if (adv && initOpen)
            {
                initOpen = false;
                ppanel.show();
            }
            else
            {
                ppanel.slideIn();
            }
            seen = true;
            document.getElementById('adv-search-btn').src = minus_img.src;
        }
        else if(ppanel){
            ppanel.slideOut();
            setTimeout("hidehelp()", 500);
            seen = false;
            document.getElementById('adv-search-btn').src = org_src;
        }
    }

    /* Give the panel time to animate sliding away. */
    function hidehelp()
    {
        Ext.getCmp('advanced-panel').hide();
        document.getElementById('advancedPanelDiv').style.display = "none";
    }

    function resubmit()
    {
        establishParams();
    }
    
    Ext.onReady(init);
</script>
<%
    }
%>