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

package org.labkey.search;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.search.SearchResultTemplate;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchService.SearchResult;
import org.labkey.api.search.SearchUrls;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FolderManagement.FolderManagementViewPostAction;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.webdav.WebdavService;
import org.labkey.search.audit.SearchAuditProvider;
import org.labkey.search.model.AbstractSearchService;
import org.labkey.search.model.IndexInspector;
import org.labkey.search.model.SearchPropertyManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SearchController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SearchController.class);

    public SearchController() throws Exception
    {
        setActionResolver(_actionResolver);
    }


    public static class SearchUrlsImpl implements SearchUrls
    {
        @Override
        public ActionURL getSearchURL(Container c, @Nullable String query)
        {
            return SearchController.getSearchURL(c, query);
        }

        @Override
        public ActionURL getSearchURL(String query, String category)
        {
            return SearchController.getSearchURL(ContainerManager.getRoot(), query, category, null);
        }

        @Override
        public ActionURL getSearchURL(Container c, @Nullable String query, @NotNull String template)
        {
            return SearchController.getSearchURL(c, query, null, template);
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            return getSearchURL();
        }
    }


    public static class AdminForm
    {
        public String[] _messages = {"", "Index deleted", "Index path changed", "Directory type changed"};
        private int msg = 0;
        private boolean pause;
        private boolean start;
        private boolean delete;
        private String indexPath;

        private boolean _path;

        private boolean _directory;
        private String _directoryType;

        public String getMessage()
        {
            return msg >= 0 && msg < _messages.length ? _messages[msg] : "";
        }

        public void setMsg(int m)
        {
            msg = m;
        }

        public boolean isDelete()
        {
            return delete;
        }

        public void setDelete(boolean delete)
        {
            this.delete = delete;
        }

        public boolean isStart()
        {
            return start;
        }

        public void setStart(boolean start)
        {
            this.start = start;
        }

        public boolean isPause()
        {
            return pause;
        }

        public void setPause(boolean pause)
        {
            this.pause = pause;
        }

        public String getIndexPath()
        {
            return indexPath;
        }

        public void setIndexPath(String indexPath)
        {
            this.indexPath = indexPath;
        }

        public boolean isPath()
        {
            return _path;
        }

        public void setPath(boolean path)
        {
            _path = path;
        }

        public boolean isDirectory()
        {
            return _directory;
        }

        public void setDirectory(boolean directory)
        {
            _directory = directory;
        }

        public String getDirectoryType()
        {
            return _directoryType;
        }

        public void setDirectoryType(String directoryType)
        {
            _directoryType = directoryType;
        }
    }
    

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class AdminAction extends FormViewAction<AdminForm>
    {
        @SuppressWarnings("UnusedDeclaration")
        public AdminAction()
        {
        }

        public AdminAction(PageConfig pageConfig)
        {
            setPageConfig(pageConfig);
        }

        private int _msgid = 0;
        
        public void validateCommand(AdminForm target, Errors errors)
        {
        }

        public ModelAndView getView(AdminForm form, boolean reshow, BindException errors) throws Exception
        {
            SearchService ss = SearchService.get();

            if (null == ss)
                throw new ConfigurationException("Search is misconfigured");

            @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
            Throwable t = ss.getConfigurationError();

            VBox vbox = new VBox();

            if (null != t)
            {
                String html = "<span class=\"labkey-error\">Your search index is misconfigured. Search is disabled and documents are not being indexed, pending resolution of this issue. See below for details about the cause of the problem.</span></br></br>";
                html += ExceptionUtil.renderException(t);
                WebPartView configErrorView = new HtmlView(html);
                configErrorView.setTitle("Search Configuration Error");
                configErrorView.setFrame(WebPartView.FrameType.PORTAL);
                vbox.addView(configErrorView);
            }

            // Spring errors get displayed in the "Index Configuration" pane
            WebPartView indexerView = new JspView<>(SearchController.class, "view/indexerAdmin.jsp", form, errors);
            indexerView.setTitle("Index Configuration");
            vbox.addView(indexerView);

            // Won't be able to gather statistics if the search index is misconfigured
            if (null == t)
            {
                WebPartView indexerStatsView = new JspView<>(SearchController.class, "view/indexerStats.jsp", form);
                indexerStatsView.setTitle("Index Statistics");
                vbox.addView(indexerStatsView);
            }

            WebPartView searchStatsView = new JspView<>(SearchController.class, "view/searchStats.jsp", form);
            searchStatsView.setTitle("Search Statistics");
            vbox.addView(searchStatsView);

            return vbox;
        }

        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            SearchService ss = SearchService.get();
            if (null == ss)
            {
                errors.reject("Indexing service is not running");
                return false;
            }

            if (form.isStart())
            {
                ss.startCrawler();
                SearchPropertyManager.setCrawlerRunningState(true);
                audit(getUser(), null, "(admin action)", "Crawler Started");
            }
            else if (form.isPause())
            {
                ss.pauseCrawler();
                SearchPropertyManager.setCrawlerRunningState(false);
                audit(getUser(), null, "(admin action)", "Crawler Paused");
            }
            else if (form.isDelete())
            {
                ss.clear();
                _msgid = 1;
                audit(getUser(), null, "(admin action)", "Index Deleted");
            }
            else if (form.isPath())
            {
                SearchPropertyManager.setIndexPath(form.getIndexPath());
                ss.updateIndex();
                _msgid = 2;
                audit(getUser(), null, "(admin action)", "Index Path Set");
            }
            else if (form.isDirectory())
            {
                SearchPropertyManager.setDirectoryType(form.getDirectoryType());
                ss.resetIndex();
                _msgid = 3;
                audit(getUser(), null, "(admin action)", "Directory type set to " + form.getDirectoryType());
            }

            return true;
        }
        
        public URLHelper getSuccessURL(AdminForm o)
        {
            ActionURL success = new ActionURL(AdminAction.class, getContainer());
            if (0 != _msgid)
                success.addParameter("msg", String.valueOf(_msgid));
            return success;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("searchAdmin"));
            PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "Full-Text Search Configuration", new ActionURL(AdminAction.class, ContainerManager.getRoot()));
            return root;
        }
    }


    @AdminConsoleAction
    public class IndexContentsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/search/view/exportContents.jsp");
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            NavTree admin = new AdminAction(getPageConfig()).appendNavTrail(root);
            admin.addChild("Index Contents");

            return admin;
        }
    }


    public static class ExportForm
    {
        private String _format = "Text";

        public String getFormat()
        {
            return _format;
        }

        public void setFormat(String format)
        {
            _format = format;
        }
    }


    @AdminConsoleAction
    public class ExportIndexContentsAction extends ExportAction<ExportForm>
    {
        @Override
        public void export(ExportForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            new IndexInspector().export(response, form.getFormat());
        }
    }


    /** for selenium testing */
    @RequiresSiteAdmin
    public class WaitForIdleAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            SearchService ss = SearchService.get();
            ss.waitForIdle();
            throw new RedirectException(new ActionURL(AdminAction.class, getContainer()));
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class SwapForm
    {
        boolean _ui = true;

        public boolean isUi()
        {
            return _ui;
        }

        public void setUi(boolean ui)
        {
            _ui = ui;
        }
    }


    // UNDONE: remove; for testing only
    @RequiresSiteAdmin
    public class CancelAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            // SimpleRedirectAction doesn't take a form
            SearchService ss = SearchService.get();
    
            if (null == ss)
                return null;

            SearchService.IndexTask defaultTask = ss.defaultTask();
            for (SearchService.IndexTask task : ss.getTasks())
            {
                if (task != defaultTask && !task.isCancelled())
                    task.cancel(true);
            }

            try
            {
                String returnUrl = getViewContext().getRequest().getParameter(ActionURL.Param.returnUrl.name());
                if (null != returnUrl)
                    return new ActionURL(returnUrl);
            }
            catch (Exception x)
            {
            }
            return getSearchURL();
        }
    }


    // UNDONE: remove; for testing only
    // cause the current directory to be crawled soon
    @RequiresSiteAdmin
    public class CrawlAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            // SimpleRedirectAction doesn't take a form
            SearchService ss = SearchService.get();

            if (null == ss)
                return null;

            ss.addPathToCrawl(
                    WebdavService.getPath().append(getContainer().getParsedPath()),
                    new Date(System.currentTimeMillis()));

            try
            {
                String returnUrl = getViewContext().getRequest().getParameter(ActionURL.Param.returnUrl.name());
                if (null != returnUrl)
                    return new ActionURL(returnUrl);
            }
            catch (Exception x)
            {
            }
            return getSearchURL();
        }
    }


    // for testing only
    @RequiresSiteAdmin
    public class IndexAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            // SimpleRedirectAction doesn't take a form
            boolean full = "1".equals(getViewContext().getRequest().getParameter("full"));
            String returnUrl = getViewContext().getRequest().getParameter(ActionURL.Param.returnUrl.name());
            boolean wait = "1".equals(getViewContext().getRequest().getParameter("wait"));

            SearchService ss = SearchService.get();

            if (null == ss)
                return null;

            SearchService.IndexTask task = null;

            if (full)
            {
                ss.indexFull(true);
            }
            else
            {
                task = ss.indexContainer(null, getContainer(), null);
            }

            if (wait && null != task)
            {
                task.get(); // wait for completion
                if (ss instanceof AbstractSearchService)
                    ((AbstractSearchService)ss).commit();
            }

            try
            {
                if (null != returnUrl)
                    return new ActionURL(returnUrl);
            }
            catch (Exception x)
            {
            }
            return getSearchURL();
        }
    }



    @RequiresPermission(ReadPermission.class)
    public class JsonAction extends ApiAction<SearchForm>
    {
        @Override
        public ApiResponse execute(SearchForm form, BindException errors) throws Exception
        {
            SearchService ss = SearchService.get();
            if (null == ss)
            {
                throw new NotFoundException();
            }

            final Path contextPath = Path.parse(getViewContext().getContextPath());
            final String query = form.getQueryString();
            final JSONObject response = new JSONObject();
            Object[] arr = new Object[0];
            long totalHits = 0;

            if (null != StringUtils.trimToNull(query))
            {
                SearchResult result;
                try
                {
                    //UNDONE: paging, rowlimit etc
                    int limit = form.getLimit() < 0 ? 1000 : form.getLimit();
                    result = ss.search(query, ss.getCategories(form.getCategory()), getUser(), getContainer(), form.getSearchScope(),
                        form.getOffset(), limit);
                }
                catch (Exception x)
                {
                    errors.rejectValue("q", ERROR_MSG,x.getMessage());
                    return null;
                }

                List<SearchService.SearchHit> hits = result.hits;
                totalHits = result.totalHits;

                arr = new Object[hits.size()];

                int i=0;
                for (SearchService.SearchHit hit : hits)
                {
                    JSONObject o = new JSONObject();
                    String id = StringUtils.isEmpty(hit.docid) ? String.valueOf(i) : hit.docid;

                    o.put("id", id);
                    o.put("title", hit.title);
                    o.put("container", hit.container);
                    o.put("url", form.isNormalizeUrls() ? hit.normalizeHref(contextPath) : hit.url);
                    o.put("summary", StringUtils.trimToEmpty(hit.summary));

                    if (form.isExperimentalCustomJson())
                    {
                        Map<String, Object> custom = ss.getCustomSearchJson(getUser(), hit.docid);
                        if (custom != null)
                            o.put("data", custom);
                    }

                    arr[i++] = o;
                }
            }

            JSONObject metaData = new JSONObject();
            metaData.put("idProperty","id");
            metaData.put("root", "hits");
            metaData.put("successProperty", "success");

            response.put("metaData", metaData);
            response.put("success",true);
            response.put("hits", arr);
            response.put("totalHits", totalHits);
            response.put("q", query);
            return new ApiSimpleResponse(response);
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class TestJson extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView(SearchController.class, "view/testJson.jsp", null, null);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }
    

    public static ActionURL getSearchURL(Container c)
    {
        return new ActionURL(SearchAction.class, c);
    }

    private ActionURL getSearchURL()
    {
        return getSearchURL(getContainer());
    }

    private static ActionURL getSearchURL(Container c, @Nullable String queryString)
    {
        return getSearchURL(c, queryString, null, null);
    }

    private static ActionURL getSearchURL(Container c, @Nullable String queryString, @Nullable String category, @Nullable String template)
    {
        ActionURL url = getSearchURL(c);

        if (null != queryString)
            url.addParameter("q", queryString);

        if (null != category)
            url.addParameter("category", category);

        if (null != template)
            url.addParameter("template", template);

        return url;
    }

    // This interface used to be used to hide all the specifics of internal vs. external index search, but we no longer support external indexes. This interface could be removed.
    public interface SearchConfiguration
    {
        ActionURL getPostURL(Container c);    // Search does not actually post
        String getDescription(Container c);
        SearchResult getSearchResult(String queryString, @Nullable String category, User user, Container currentContainer, SearchScope scope, int offset, int limit) throws IOException;
        boolean includeAdvancedUI();
        boolean includeNavigationLinks();
    }


    public static class InternalSearchConfiguration implements SearchConfiguration
    {
        private final SearchService _ss = SearchService.get();

        private InternalSearchConfiguration()
        {
        }

        @Override
        public ActionURL getPostURL(Container c)
        {
            return getSearchURL(c);
        }

        @Override
        public String getDescription(Container c)
        {
            return LookAndFeelProperties.getInstance(c).getShortName();
        }

        @Override
        public SearchResult getSearchResult(String queryString, @Nullable String category, User user, Container currentContainer, SearchScope scope, int offset, int limit) throws IOException
        {
            return _ss.search(queryString, _ss.getCategories(category), user, currentContainer, scope, offset, limit);
        }

        @Override
        public boolean includeAdvancedUI()
        {
            return true;
        }

        @Override
        public boolean includeNavigationLinks()
        {
            return true;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class SearchAction extends SimpleViewAction<SearchForm>
    {
        private String _category = null;
        private SearchScope _scope = null;
        private SearchForm _form = null;

        public ModelAndView getView(SearchForm form, BindException errors) throws Exception
        {
            _category = form.getCategory();
            _scope = form.getSearchScope();
            _form = form;

            SearchService ss = SearchService.get();

            if (null == ss)
            {
                throw new NotFoundException("Search service is not registered");
            }

            form.setPrint(isPrint());

            audit(form);

            // reenable caching for search results page (fast browser back button)
            HttpServletResponse response = getViewContext().getResponse();
            response.setDateHeader("Expires", HeartBeat.currentTimeMillis() + (5 * 60 * 1000));
            response.setHeader("Cache-Control", "private");
            response.setHeader("Pragma", "cache");
            response.addHeader("Vary", "Cookie");
            getPageConfig().setNoIndex();
            getPageConfig().setHelpTopic(new HelpTopic("luceneSearch"));

            return new JspView<>("/org/labkey/search/view/search.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return _form.getSearchResultTemplate().appendNavTrail(root, getViewContext(), _scope, _category);
        }
    }


    // This is intended to help test search indexing. This action sticks a special runnable in the indexer queue
    // and then returns when that runnable is executed (or if five minutes goes by without the runnable executing).
    // The tests can invoke this action to ensure that the indexer has executed all previous indexing tasks. It
    // does not guarantee that all indexed content has been committed... but that may not be required in practice.

    @RequiresSiteAdmin
    public class WaitForIndexerAction extends ExportAction
    {
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            SearchService ss = SearchService.get();
            final CountDownLatch latch = new CountDownLatch(1);

            // TODO: This doesn't seem quite right... don't we need to wait for _itemQueue and _indexQueue as well?
            SearchService.IndexTask task = ss.defaultTask();
            task.addRunnable(new Runnable() {
                @Override
                public void run()
                {
                    latch.countDown();
                }
            }, SearchService.PRIORITY.item);

            boolean success = latch.await(5, TimeUnit.MINUTES);

            // Return an error if we time out
            if (!success)
                response.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class CommentAction extends FormHandlerAction<SearchForm>
    {
        @Override
        public void validateCommand(SearchForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(SearchForm searchForm, BindException errors) throws Exception
        {
            audit(searchForm);
            return true;
        }

        @Override
        public URLHelper getSuccessURL(SearchForm searchForm)
        {
            return getSearchURL();
        }
    }


    public static class SearchForm
    {
        private String[] _query;
        private String _sort;
        private boolean _print = false;
        private int _offset = 0;
        private int _limit = 1000;
        private String _category = null;
        private String _comment = null;
        private int _textBoxWidth = 50; // default size
        private boolean _includeHelpLink = true;
        private boolean _webpart = false;
        private boolean _showAdvanced = false;
        private SearchConfiguration _config = new InternalSearchConfiguration();    // Assume internal search (for webparts, etc.)
        private String _template = null;
        private SearchScope _scope = SearchScope.All;
        private boolean _normalizeUrls = false;
        private boolean _experimentalCustomJson = false;

        public void setConfiguration(SearchConfiguration config)
        {
            _config = config;
        }

        public SearchConfiguration getConfig()
        {
            return _config;
        }

        public String[] getQ()
        {
            return null == _query ? new String[0] : _query;
        }

        public String getQueryString()
        {
            if (null == _query || _query.length == 0)
                return "";

            return StringUtils.join(_query, " ");
        }

        public void setQ(String[] query)
        {
            _query = query;
        }

        public String getSort()
        {
            return _sort;
        }

        public void setSort(String sort)
        {
            _sort = sort;
        }

        public boolean isPrint()
        {
            return _print;
        }

        public void setPrint(boolean print)
        {
            _print = print;
        }

        public int getOffset()
        {
            return _offset;
        }

        public void setOffset(int o)
        {
            _offset = o;
        }

        public int getLimit()
        {
            return _limit;
        }

        public void setLimit(int o)
        {
            _limit = o;
        }

        public String getScope()
        {
            return _scope.name();
        }

        public void setScope(String scope)
        {
            try
            {
                _scope = SearchScope.valueOf(scope);
            }
            catch (IllegalArgumentException e)
            {
                _scope = SearchScope.All;
            }
        }

        public SearchScope getSearchScope()
        {
            return _scope;
        }

        public String getCategory()
        {
            return _category;
        }

        public void setCategory(String category)
        {
            _category = category;
        }

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }

        public int getTextBoxWidth()
        {
            return _textBoxWidth;
        }

        public void setTextBoxWidth(int textBoxWidth)
        {
            _textBoxWidth = textBoxWidth;
        }

        public boolean getIncludeHelpLink()
        {
            return _includeHelpLink;
        }

        public void setIncludeHelpLink(boolean includeHelpLink)
        {
            _includeHelpLink = includeHelpLink;
        }

        public boolean isWebPart()
        {
            return _webpart;
        }

        public void setWebPart(boolean webpart)
        {
            _webpart = webpart;
        }

        public boolean isShowAdvanced()
        {
            return _showAdvanced;
        }

        public void setShowAdvanced(boolean showAdvanced)
        {
            _showAdvanced = showAdvanced;
        }

        public String getTemplate()
        {
            return _template;
        }

        public void setTemplate(String template)
        {
            _template = template;
        }

        public SearchResultTemplate getSearchResultTemplate()
        {
            SearchService ss = SearchService.get();
            return ss.getSearchResultTemplate(getTemplate());
        }

        public boolean isNormalizeUrls()
        {
            return _normalizeUrls;
        }

        public void setNormalizeUrls(boolean normalizeUrls)
        {
            _normalizeUrls = normalizeUrls;
        }

        public boolean isExperimentalCustomJson()
        {
            return _experimentalCustomJson;
        }

        public void setExperimentalCustomJson(boolean experimentalCustomJson)
        {
            _experimentalCustomJson = experimentalCustomJson;
        }
    }

    
    protected void audit(SearchForm form)
    {
        ViewContext ctx = getViewContext();
        String comment = form.getComment();

        audit(ctx.getUser(), ctx.getContainer(), form.getQueryString(), comment);
    }

    
    protected void audit(User user, @Nullable Container c, String query, String comment)
    {
        if (user.isSearchUser() || StringUtils.isEmpty(query))
            return;

        AuditLogService audit = AuditLogService.get();
        if (null == audit)
            return;

        if (null == c)
            c = ContainerManager.getRoot();

        if (query.length() > 200)
            query = query.substring(0, 197) + "...";

        SearchAuditProvider.SearchAuditEvent event = new SearchAuditProvider.SearchAuditEvent(c.getId(), comment);
        event.setQuery(query);

        AuditLogService.get().addEvent(user, event);
    }


    public static class SearchSettingsForm
    {
        private boolean _searchable;
        private String _provider;

        public boolean isSearchable()
        {
            return _searchable;
        }

        public void setSearchable(boolean searchable)
        {
            _searchable = searchable;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class SearchSettingsAction extends FolderManagementViewPostAction<SearchSettingsForm>
    {
        @Override
        protected HttpView getTabView(SearchSettingsForm form, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/search/view/fullTextSearch.jsp", form, errors);
        }

        @Override
        public void validateCommand(SearchSettingsForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(SearchSettingsForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            if (container.isRoot())
            {
                throw new NotFoundException();
            }

            ContainerManager.updateSearchable(container, form.isSearchable(), getUser());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(SearchSettingsForm searchForm)
        {
            // In this case, must redirect back to view so Container is reloaded (simple reshow will continue to show the old value)
            return getViewContext().getActionURL();
        }
    }
}
