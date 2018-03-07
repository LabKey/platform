<%
    /*
     * Copyright (c) 2009-2017 LabKey Corporation
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
<%@ page import="org.jetbrains.annotations.NotNull" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.search.SearchController.SearchConfiguration" %>
<%@ page import="org.labkey.search.SearchController.SearchForm" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.search.SearchService.SearchResult" %>
<%@ page import="org.labkey.api.search.SearchResultTemplate" %>
<%@ page import="org.labkey.api.search.SearchScope" %>
<%@ page import="java.io.IOException" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.json.JSONArray" %>
<%@ page import="org.labkey.api.search.SearchMisconfiguredException" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.search.SearchUtils" %>
<%@ page import="org.labkey.api.search.SearchUtils.HtmlParseException" %>
<%@ page import="org.labkey.api.webdav.WebdavResource" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }

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

    Collection<NavTree> getActions(SearchService.SearchHit hit)
    {
        String docid = hit.docid;
        if (null == docid)
            return null;
        WebdavResource r =  SearchService.get().resolveResource(docid);
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
            return new NavTree(hit.url, hit.url);
        }

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

    String getPageSummary(long totalHits, @NotNull SearchResultTemplate template, int hitsPerPage, int pageNo, int pageCount)
    {
        StringBuilder sb = new StringBuilder();

        if (hitsPerPage < totalHits)
        {
            sb.append("Displaying page ")
                    .append(Formats.commaf0.format(pageNo))
                    .append(" of ")
                    .append(Formats.commaf0.format(pageCount));
        }
        else if (totalHits > 0)
        {
            sb.append("Displaying all ")
                    .append(h(template.getResultNamePlural()));
        }

        return sb.toString();
    }

    String getResultsSummary(long totalHits, @NotNull SearchResultTemplate template)
    {
        StringBuilder sb = new StringBuilder("Found ")
                .append(Formats.commaf0.format(totalHits))
                .append(" ");

        if (totalHits == 1)
            sb.append(h(template.getResultNameSingular()));
        else
            sb.append(h(template.getResultNamePlural()));

        return sb.toString();
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
%>
<%
    JspView<SearchForm> me = (JspView<SearchForm>) HttpView.currentView();
    SearchForm form = me.getModelBean();
    ViewContext ctx = getViewContext();
    Container c = getContainer();
    User user = getUser();
    Path contextPath = Path.parse(ctx.getContextPath());
    SearchService ss = SearchService.get();

    List<String> q = new ArrayList<>(Arrays.asList(form.getQ()));
    String value = StringUtils.trim(StringUtils.join(q, " "));
    SearchResultTemplate template = form.getSearchResultTemplate();
    SearchScope scope = (null == template.getSearchScope() ? form.getSearchScope() : template.getSearchScope());
    String categories = (null == template.getCategories() ? form.getCategory() : template.getCategories());
    String safeCategories = categories == null ? "" : categories.toLowerCase();
    String queryString = form.getQueryString();

    SearchConfiguration searchConfig = form.getConfig();
    boolean includeNavigationResults = !form.isWebPart() && searchConfig.includeNavigationLinks() && template.includeNavigationLinks() && scope != SearchScope.Folder;
    boolean hasNavResults = false;
    boolean showAdvancedUI = !form.isWebPart() && searchConfig.includeAdvancedUI() && template.includeAdvanceUI();

    int hitsPerPage = 20;
    int offset = form.getOffset();
    int pageNo = (offset / hitsPerPage) + 1;
    SearchResult result = null;
    SearchResult navResult = null;
    String searchFormId = "search-form-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    String advFormId = "search-form-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    String advFormCt = "search-form-ct-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());

    // exceptions for display
    HtmlParseException parseException = null;
    boolean hasSearchException = false;

    if (null != StringUtils.trimToNull(queryString))
    {
        try
        {
            result = searchConfig.getSearchResult(template.reviseQuery(ctx, queryString), categories, user, c, scope, offset, hitsPerPage);

            if (includeNavigationResults)
            {
                navResult = SearchService.get().search(queryString, Arrays.asList(SearchService.navigationCategory), user, c, scope, offset, hitsPerPage);
                hasNavResults = navResult != null && navResult.hits.size() > 0;
            }
        }
        catch (HtmlParseException html)
        {
            parseException = html;
        }
        catch (IOException e)
        {

        }
        catch (SearchMisconfiguredException e)
        {
            hasSearchException = true;
        }
    }
%>
<div<%=text(form.isWebPart() ? "" : " class=\"col-md-12\"")%>>
    <div style="position:relative;">
        <labkey:form id="<%=h(searchFormId)%>" className="lk-search-form" action="<%=h(searchConfig.getPostURL(c))%>">
            <labkey:input type="text" name="q" placeholder="<%=h(form.isWebPart() ? \"\" : SearchUtils.getPlaceholder(c))%>" formGroup="false" value="<%=h(value)%>"/>
            <a class="search-overlay fa fa-search"></a>
            <% if (showAdvancedUI) { %>
            <small>
                <a class="search-advanced-toggle">advanced options</a>
                <% if (form.getIncludeHelpLink()) { %>
                | <a target="_blank" href="<%=text(SearchUtils.getHelpTopic().getHelpTopicHref())%>">help</a>
                <% } %>
            </small>
            <% } %>
            <% if (showAdvancedUI) { %>
            <labkey:input type="hidden" name="category" value="<%=h(categories)%>"/>
            <labkey:input type="hidden" name="showAdvanced" value="<%=h(form.isShowAdvanced())%>"/>
            <% } %>
            <% if (null == template.getSearchScope()) { %>
            <labkey:input type="hidden" name="scope" value="<%=h(form.getSearchScope())%>"/>
            <% } %>
            <% if (null != form.getTemplate()) { %>
            <labkey:input type="hidden" name="template" value="<%=h(form.getTemplate())%>"/>
            <% } %>
            <input type="hidden" name="_dc" value="<%=h(Math.round(1000 * Math.random()))%>">
            <%
                String hiddenInputs = template.getHiddenInputsHtml(ctx);
                if (hiddenInputs != null)
                    out.write(hiddenInputs);
            %>
        </labkey:form>
    </div>
</div>
<div id="<%=h(advFormCt)%>" class="col-md-12" <%=text(form.isShowAdvanced() ? "" : "style=\"display:none;\"")%>>
    <div class="panel panel-default">
        <div class="panel-body">
            <labkey:form id="<%=h(advFormId)%>">
                <div class="form-group">
                    <div class="col-sm-6">
                        <h5>
                            Scope
                            <%= helpPopup("Scope", "Scoping allows the search to be refined to the contents of the entire site (default), contents of this project including sub-folders, or contents of just this folder.")%>
                        </h5>
                        <div style="padding-top: 1px;">
                            <div class="radio">
                                <label>
                                    <input type="radio" name="scope" value="<%=h(SearchScope.All.name())%>" <%=checked(scope == null || SearchScope.All.equals(scope))%>> Site
                                </label>
                            </div>
                            <div class="radio">
                                <label>
                                    <input type="radio" name="scope" value="<%=h(SearchScope.Project.name())%>" <%=disabled(c.equals(ContainerManager.getRoot()))%> <%=checked(SearchScope.Project.equals(scope))%>> Project
                                </label>
                            </div>
                            <div class="radio">
                                <label>
                                    <input type="radio" name="scope" value="<%=h(SearchScope.Folder.name())%>" <%=checked(SearchScope.Folder.equals(scope))%>> Current Folder
                                </label>
                            </div>
                            <div class="radio">
                                <label>
                                    <input type="radio" name="scope" value="<%=h(SearchScope.FolderAndSubfolders.name())%>" <%=checked(SearchScope.FolderAndSubfolders.equals(scope))%>> Current Folder & Subfolders
                                </label>
                            </div>
                        </div>
                    </div>
                </div>
                <div class="form-group">
                    <div class="col-sm-6">
                        <h5>
                            Categories
                            <%= helpPopup("Categories", "Choosing one or more categories will refine your search to only those data types. For example, if you select 'Files' you will see only files and attachments in your " + h(template.getResultNameSingular()) + ".")%>
                        </h5>
                        <div style="padding-top: 1px;">
                            <div class="col-xs-6 col-sm-6" style="padding-left: 0;">
                                <div class="checkbox">
                                    <label>
                                        <input type="checkbox" name="category" value="Assay" <%=checked(safeCategories.contains("assay"))%>> Assays
                                    </label>
                                </div>
                                <div class="checkbox">
                                    <label>
                                        <input type="checkbox" name="category" value="Dataset" <%=checked(safeCategories.contains("dataset"))%>> Datasets
                                    </label>
                                </div>
                                <div class="checkbox">
                                    <label>
                                        <input type="checkbox" name="category" value="File" <%=checked(safeCategories.contains("file"))%>> Files
                                    </label>
                                </div>
                                <div class="checkbox">
                                    <label>
                                        <input type="checkbox" name="category" value="Issue" <%=checked(safeCategories.contains("issue"))%>> Issues
                                    </label>
                                </div>
                            </div>
                            <div class="col-xs-6 col-sm-6" style="padding-left: 0;">
                                <div class="checkbox">
                                    <label>
                                        <input type="checkbox" name="category" value="List" <%=checked(safeCategories.contains("list"))%>> Lists
                                    </label>
                                </div>
                                <div class="checkbox">
                                    <label>
                                        <input type="checkbox" name="category" value="Message" <%=checked(safeCategories.contains("message"))%>> Messages
                                    </label>
                                </div>
                                <div class="checkbox">
                                    <label>
                                        <input type="checkbox" name="category" value="Subject" <%=checked(safeCategories.contains("subject"))%>> Subjects
                                    </label>
                                </div>
                                <div class="checkbox">
                                    <label>
                                        <input type="checkbox" name="category" value="Wiki" <%=checked(safeCategories.contains("wiki"))%>> Wikis
                                    </label>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </labkey:form>
        </div>
    </div>
</div>
<%
    String extraHtml = template.getExtraHtml(ctx);
    if (null != extraHtml)
    {
%>
<div class="col-md-12"><%=text(extraHtml)%></div>
<%
    }
%>
<% if (result != null) { %>
<div class="<%=text(hasNavResults ? "col-md-9" : "col-md-12")%>">
    <div class="panel panel-portal">
        <div class="panel-body">
            <%
                long hits = result.totalHits;
                int pageCount = (int)Math.ceil((double)hits / hitsPerPage);
            %>
            <div class="labkey-search-results-counts">
                <div class="pull-left"><%=text(getResultsSummary(hits, template))%></div>
                <div class="pull-right"><%=text(getPageSummary(hits, template, hitsPerPage, pageNo, pageCount))%></div>
            </div>
            <%
                for (SearchService.SearchHit hit : result.hits)
                {
                    Container documentContainer = ContainerManager.getForId(hit.container);

                    String href = hit.normalizeHref(contextPath, documentContainer);
            %>
            <div class="labkey-search-result">
                <h4 style="margin:0;">
                    <a href="<%=h(href)%>"><%=h(hit.title)%></a>
                </h4>
                <div class="labkey-search-content">
                    <%
                        NavTree nav = getDocumentContext(documentContainer, hit);
                        if (null != nav)
                        {
                    %>
                    <div>
                        <a class="labkey-search-cite" href="<%=h(nav.getHref())%>"><%=h(nav.getText())%></a>
                        <%
                            if (!nav.getHref().equals(documentContainer.getStartURL(user).toString()))
                            {
                        %>
                        in <a class="labkey-search-cite" href="<%=h(documentContainer.getStartURL(user))%>"><%=h(documentContainer.getPath())%></a>
                        <% } %>
                    </div>
                    <%
                        }

                        if (!StringUtils.isEmpty(hit.navtrail))
                        {
                    %>&nbsp;<%=text(formatNavTrail(parseNavTrail(hit.navtrail)))%><%
                    }
                    Collection<NavTree> actions = getActions(hit);
                    if (null != actions && !actions.isEmpty())
                    {
                %>&nbsp;<%=text(formatNavTrail(actions))%><%
                    }

                    HttpView summaryView = ss.getCustomSearchResult(user, hit.docid);
                    if (null != summaryView)
                        include(summaryView, out);
                    else
                    {
                        String summary = StringUtils.trimToNull(hit.summary);
                        if (null != summary)
                %><%=h(summary, false)%><%
                    }
                %>
                </div>
            </div>
            <%
                }

                if (pageCount > 1)
                {
            %>
            <div style="text-align:center;">
                <%
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
                <a href="<%=h(previousURL)%>">&lt; Previous</a>
                <%
                    if (pageNo < pageCount)
                    {
                %>
                &nbsp;|&nbsp;
                <%
                        }
                    }

                    if (pageNo < pageCount)
                    {
                %>
                <a href="<%=h(currentURL.clone().replaceParameter("offset", String.valueOf(offset + hitsPerPage)))%>">Next &gt;</a>
                <%
                    }
                %>
            </div>
            <%
                }
            %>
        </div>
    </div>
</div>
<% if (hasNavResults) { %>
<div class="col-md-3">
    <div class="panel panel-portal">
        <div class="panel-body">
            <div class="labkey-search-results-counts">
                <span>Folders</span>
            </div>
            <%
                for (SearchService.SearchHit hit : navResult.hits)
                {
            %>
            <div class="labkey-search-result">
                <h4>
                    <img style="vertical-align: top;" src="<%=getContextPath()%>/_icons/folder.gif"/>
                    <a href="<%=h(hit.url)%>"><%=h(hit.title)%></a>
                </h4>
                <%
                    String summary = StringUtils.trimToNull(hit.summary);
                    if (null != summary)
                    {
                %>
                <div><%=h(summary, false)%></div>
                <%
                    }
                %>
            </div>
            <%
                }
            %>
        </div>
    </div>
</div>
<% } %>
<% } else if (parseException != null) { %>
<div class="col-md-12">
    <div class="alert alert-warning">
        <% SearchUtils.renderError(out, parseException.getMessage(), parseException.includesSpecialSymbol(), parseException.includesBooleanOperator(), true); %>
    </div>
</div>
<% } else if (hasSearchException) { %>
<div class="col-md-12">
    <div class="alert alert-warning">
        <% SearchUtils.renderError(out, "Search is disabled because the search index is misconfigured. Contact the system administrator of this server.", false, false, false); %>
    </div>
</div>
<% } %>
<script type="application/javascript">
    +function($){
        'use strict';

        var advFormCtSelector = '#' + <%=PageFlowUtil.jsString(advFormCt)%>;

        function getAdvForm() {
            return $('#' + <%=PageFlowUtil.jsString(advFormId)%>);
        }

        function getSearchForm() {
            return $('#' + <%=PageFlowUtil.jsString(searchFormId)%>);
        }

        $(function() {
            var form = getSearchForm();
            var advForm = getAdvForm();

            advForm.change(function() {
                var category = '', sep = '';
                advForm.serializeArray().forEach(function(p) {
                    if (p.name.toLowerCase() === 'scope') {
                        form.find('input[name="scope"]').val(p.value);
                    }
                    else {
                        category += sep + p.value;
                        sep = '+';
                    }
                });
                form.find('input[name="category"]').val(category);
            });

            form.submit(function(e) {
                form.serializeArray().forEach(function(e) {
                    if (e.name === 'scope' && e.value.toLowerCase() === 'all') {
                        e.value = '';
                    }
                    else if (e.name === 'showAdvanced' && e.value.toLowerCase() === 'false') {
                        e.value = '';
                    }
                    if (e.value === '' && e.name !== 'q') {
                        form.find('input[name="' + e.name + '"]').remove();
                    }
                });
            });

            <% if (!form.isWebPart()) { %>
            var q = form.find('input[name="q"]');
            if (!q.val()) {
                q.focus();
            }
            <% } %>

            form.find('.search-overlay').click(function() {
                form.submit();
            });

            form.find('.search-advanced-toggle').click(function() {
                $(advFormCtSelector).toggle();
                form.find('input[name="showAdvanced"]').val($(advFormCtSelector + ':visible').length === 1);
            });
        });

        <% if (!form.isWebPart()) { %>
        $(document).keypress(function(e){if(e.which === 13){getSearchForm().submit();}});
        <% } %>
    }(jQuery);
</script>