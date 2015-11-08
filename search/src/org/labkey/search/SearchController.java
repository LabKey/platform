/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
import org.labkey.api.action.SimpleErrorView;
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
import org.labkey.api.search.SearchUrls;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.webdav.WebdavService;
import org.labkey.search.model.AbstractSearchService;
import org.labkey.search.model.ExternalAnalyzer;
import org.labkey.search.model.ExternalIndexProperties;
import org.labkey.search.model.IndexInspector;
import org.labkey.search.model.LuceneSearchServiceImpl;
import org.labkey.search.model.SearchPropertyManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.sql.Date;
import java.util.List;
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


    public static class AdminForm implements ExternalIndexProperties
    {
        public String[] _messages = {"", "Primary index deleted", "Primary index path changed"};
        private int msg = 0;
        private boolean pause;
        private boolean start;
        private boolean delete;
        private String primaryIndexPath;

        private String externalIndexPath = null;
        private String externalIndexDescription = null;
        private String externalIndexAnalyzer = null;
        private boolean _path;

        public String getMessage()
        {
            return msg >= 0 && msg < _messages.length ? _messages[msg] : "";
        }

        public void setMsg(int m)
        {
            msg = m;
        }

        public String getExternalIndexPath()
        {
            return externalIndexPath;
        }

        public void setExternalIndexPath(String externalIndexPath)
        {
            this.externalIndexPath = externalIndexPath;
        }

        public String getExternalIndexDescription()
        {
            return externalIndexDescription;
        }

        public void setExternalIndexDescription(String externalIndexDescription)
        {
            this.externalIndexDescription = externalIndexDescription;
        }

        public String getExternalIndexAnalyzer()
        {
            return externalIndexAnalyzer;
        }

        public boolean hasExternalIndex()
        {
            throw new IllegalArgumentException();
        }

        public void setExternalIndexAnalyzer(String externalIndexAnalyzer)
        {
            this.externalIndexAnalyzer = externalIndexAnalyzer;
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

        public String getPrimaryIndexPath()
        {
            return primaryIndexPath;
        }

        public void setPrimaryIndexPath(String primaryIndexPath)
        {
            this.primaryIndexPath = primaryIndexPath;
        }

        public boolean isPath()
        {
            return _path;
        }

        public void setPath(boolean path)
        {
            _path = path;
        }
    }
    

    @AdminConsoleAction
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
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null == ss)
                throw new ConfigurationException("Search is misconfigured");

            @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
            Throwable t = ss.getConfigurationError();

            ExternalIndexProperties props = SearchPropertyManager.getExternalIndexProperties();

            VBox vbox = new VBox();

            if (null != t)
            {
                String html = "<span class=\"labkey-error\">Your search index is misconfigured. Search is disabled and documents are not being indexed, pending resolution of this issue. See below for details about the cause of the problem.</span></br></br>";
                html += ExceptionUtil.renderException(t);
                WebPartView configuErrorView = new HtmlView(html);
                configuErrorView.setTitle("Search Configuration Error");
                configuErrorView.setFrame(WebPartView.FrameType.PORTAL);
                vbox.addView(configuErrorView);
            }

            // Spring errors get displayed in the "Primary Index Configuration" pane
            WebPartView indexerView = new JspView<>(SearchController.class, "view/indexerAdmin.jsp", form, errors);
            indexerView.setTitle("Primary Index Configuration");
            vbox.addView(indexerView);

            WebPartView externalIndexView = new JspView<>(SearchController.class, "view/externalIndex.jsp", props);
            externalIndexView.setTitle("External Index Configuration");
            vbox.addView(externalIndexView);

            // Won't be able to gather statistics if the search index is misconfigured
            if (null == t)
            {
                WebPartView indexerStatsView = new JspView<>(SearchController.class, "view/indexerStats.jsp", form);
                indexerStatsView.setTitle("Primary Index Statistics");
                vbox.addView(indexerStatsView);
            }

            WebPartView searchStatsView = new JspView<>(SearchController.class, "view/searchStats.jsp", form);
            searchStatsView.setTitle("Search Statistics");
            vbox.addView(searchStatsView);

            return vbox;
        }

        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
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
                SearchPropertyManager.setPrimaryIndexPath(form.getPrimaryIndexPath()); 
                ss.updatePrimaryIndex();
                _msgid = 2;
                audit(getUser(), null, "(admin action)", "Index Path Set");
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
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            ss.waitForIdle();
            throw new RedirectException(new ActionURL(AdminAction.class, getContainer()));
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class SetExternalIndexAction extends SimpleRedirectAction<AdminForm>
    {
        @Override
        public ActionURL getRedirectURL(AdminForm form) throws Exception
        {
            String message = getValidationError(form);

            if (null == message)
            {
                SearchPropertyManager.saveExternalIndexProperties(form);
                SearchService ss = ServiceRegistry.get().getService(SearchService.class);

                // TODO: Add to SearchService interface
                ((LuceneSearchServiceImpl)ss).resetExternalIndex();
                message = "External index set";
            }

            ActionURL url = new ActionURL(AdminAction.class, ContainerManager.getRoot());
            url.addParameter("externalMessage", message);
            return url;
        }

        private @Nullable String getValidationError(ExternalIndexProperties props)
        {
            if (StringUtils.isBlank(props.getExternalIndexDescription()))
                return "You must enter a description";

            String path = props.getExternalIndexPath();

            if (StringUtils.isBlank(path))
                return "You must enter a valid path";

            if (!new File(path).exists())
                return "You must enter a path to an existing directory";

            try
            {
                ExternalAnalyzer.valueOf(props.getExternalIndexAnalyzer());
            }
            catch (IllegalArgumentException e)
            {
                return "Invalid analyzer";
            }

            return null;
        }
    }


    @RequiresSiteAdmin
    public class ClearExternalIndexAction extends SimpleRedirectAction
    {
        @Override
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            SearchPropertyManager.clearExternalIndexProperties();
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            // TODO: Add to SearchService interface
            ((LuceneSearchServiceImpl)ss).resetExternalIndex();

            ActionURL url = new ActionURL(AdminAction.class, ContainerManager.getRoot());
            url.addParameter("externalMessage", "External index cleared");
            return url;
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


    @RequiresNoPermission
    public class SwapExternalIndexAction extends SimpleViewAction<SwapForm>
    {
        @Override
        public ModelAndView getView(SwapForm form, BindException errors) throws Exception
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            // TODO: Add to SearchService interface
            ((LuceneSearchServiceImpl)ss).swapExternalIndex();

            String message = "External index replaced";

            // If this was initiated from the UI and reload was not queued up then reshow the form and display the message
            if (form.isUi())
            {
                ActionURL url = new ActionURL(AdminAction.class, ContainerManager.getRoot());
                url.addParameter("externalMessage", message);

                return HttpView.redirect(url);
            }
            else
            {
                // Plain text response for scripts
                sendPlainText(message);
                return null;
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresSiteAdmin
    public class PermissionsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.Dialog);
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null != ss)
            {
                if (SearchPropertyManager.getExternalIndexProperties().hasExternalIndex())
                {
                    List<SecurableResource> resources = ss.getSecurableResources(getUser());

                    if (resources.size() < 1)
                    {
                        throw new IllegalStateException("No securable resources found");
                    }
                    else if (resources.size() > 1)
                    {
                        throw new IllegalStateException("Multiple securable resources found");
                    }
                    else
                    {
                        return new JspView<>(SearchController.class, "view/externalIndexPermissions.jsp", resources.get(0));
                    }
                }
                else
                {
                    errors.reject(ERROR_MSG, "External index is not configured");
                }
            }
            else
            {
                errors.reject(ERROR_MSG, "Search service is not running");
            }

            return new SimpleErrorView(errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    // UNDONE: remove; for testing only
    @RequiresSiteAdmin
    public class CancelAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            // SimpleRedirectAction doesn't take a form
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
    
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
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

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

            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

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
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            if (null == ss)
            {
                throw new NotFoundException();
            }

            String query = form.getQueryString();
            JSONObject response = new JSONObject();
            Object[] arr = new Object[0];
            int totalHits = 0;

            if (null != StringUtils.trimToNull(query))
            {
                SearchService.SearchResult result;
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
                    o.put("url", hit.url);
                    o.put("summary", StringUtils.trimToEmpty(hit.summary));
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
    

    private abstract class AbstractSearchAction extends SimpleViewAction<SearchForm>
    {
        public ModelAndView getView(SearchForm form, BindException errors, boolean external) throws Exception
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null == ss)
            {
                throw new NotFoundException("Search service is not registered");
            }

            form.setPrint(isPrint());

            audit(form, external);

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

    // This interface hides all the specifics of internal vs. external index search, keeping search.jsp reasonably generic.
    public interface SearchConfiguration
    {
        ActionURL getPostURL(Container c);    // Search does not actually post
        ActionURL getSecondarySearchURL(Container c, String queryString); // TODO: Need other params? (category, scope)
        String getPrimaryDescription(Container c);
        String getSecondaryDescription(Container c);
        SearchService.SearchResult getPrimarySearchResult(String queryString, @Nullable String category, User user, Container currentContainer, SearchScope scope, int offset, int limit) throws IOException;
        SearchService.SearchResult getSecondarySearchResult(String queryString, @Nullable String category, User user, Container currentContainer, SearchScope scope, int offset, int limit) throws IOException;
        boolean hasSecondaryPermissions(User user);
        boolean includeAdvancedUI();
        boolean includeNavigationLinks();
    }


    public static class InternalSearchConfiguration implements SearchConfiguration
    {
        private final SearchService _ss = ServiceRegistry.get(SearchService.class);

        private InternalSearchConfiguration()
        {
        }

        @Override
        public ActionURL getPostURL(Container c)
        {
            return getSearchURL(c);
        }

        @Override
        public ActionURL getSecondarySearchURL(Container c, String queryString)
        {
            return getSearchExternalURL(c, queryString);
        }

        @Override
        public String getPrimaryDescription(Container c)
        {
            return LookAndFeelProperties.getInstance(c).getShortName();
        }

        @Override
        public String getSecondaryDescription(Container c)
        {
            return SearchPropertyManager.getExternalIndexProperties().getExternalIndexDescription();
        }

        @Override
        public SearchService.SearchResult getPrimarySearchResult(String queryString, @Nullable String category, User user, Container currentContainer, SearchScope scope, int offset, int limit) throws IOException
        {
            return _ss.search(queryString, _ss.getCategories(category), user, currentContainer, scope, offset, limit);
        }

        @Override
        public SearchService.SearchResult getSecondarySearchResult(String queryString, @Nullable String category, User user, Container currentContainer, SearchScope scope, int offset, int limit) throws IOException
        {
            return _ss.searchExternal(queryString, offset, limit);
        }

        @Override
        public boolean hasSecondaryPermissions(User user)
        {
            return _ss.hasExternalIndexPermission(user);
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
    public class SearchAction extends AbstractSearchAction
    {
        private String _category = null;
        private SearchScope _scope = null;
        private SearchForm _form = null;

        public ModelAndView getView(SearchForm form, BindException errors) throws Exception
        {
            _category = form.getCategory();
            _scope = form.getSearchScope();
            _form = form;

            return super.getView(form, errors, false);
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
            SearchService ss = ServiceRegistry.get(SearchService.class);
            final CountDownLatch latch = new CountDownLatch(1);

            // TODO: This doesn't seem quite right... doon't we need to wait for _itemQueue and _indexQueue as well?
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



    public static ActionURL getSearchExternalURL(Container c)
    {
        return new ActionURL(SearchExternalAction.class, c);
    }


    public static ActionURL getSearchExternalURL(Container c, String queryString)
    {
        ActionURL url = getSearchExternalURL(c);
        url.addParameter("q", queryString);
        return url;
    }


    @RequiresPermission(ReadPermission.class)
    public class SearchExternalAction extends AbstractSearchAction
    {
        String _description;

        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            super.checkPermissions();

            // Show results page only if user has permission to see external index results.
            SearchService ss = ServiceRegistry.get(SearchService.class);

            if (!ss.hasExternalIndexPermission(getUser()))
            {
                throw new UnauthorizedException();
            }
        }

        public ModelAndView getView(SearchForm form, BindException errors) throws Exception
        {
            SearchConfiguration config = new ExternalSearchConfiguration();
            form.setConfiguration(config);
            _description = config.getPrimaryDescription(getContainer());
            return super.getView(form, errors, true);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Search " + _description);
        }
    }


    public class ExternalSearchConfiguration implements SearchConfiguration
    {
        private final SearchService _ss = ServiceRegistry.get(SearchService.class);

        private ExternalSearchConfiguration()
        {
        }

        @Override
        public ActionURL getPostURL(Container c)
        {
            return getSearchExternalURL(c);
        }

        @Override
        public ActionURL getSecondarySearchURL(Container c, String queryString)
        {
            return getSearchURL(c, queryString);
        }

        @Override
        public String getPrimaryDescription(Container c)
        {
            return SearchPropertyManager.getExternalIndexProperties().getExternalIndexDescription();
        }

        @Override
        public String getSecondaryDescription(Container c)
        {
            return LookAndFeelProperties.getInstance(c).getShortName();
        }

        @Override
        public SearchService.SearchResult getPrimarySearchResult(String queryString, @Nullable String category, User user, Container currentContainer, SearchScope scope, int offset, int limit) throws IOException
        {
            return _ss.searchExternal(queryString, offset, limit);
        }

        @Override
        public SearchService.SearchResult getSecondarySearchResult(String queryString, @Nullable String category, User user, Container currentContainer, SearchScope scope, int offset, int limit) throws IOException
        {
            return _ss.search(queryString, _ss.getCategories(category), user, currentContainer, scope, offset, limit);
        }

        @Override
        public boolean hasSecondaryPermissions(User user)
        {
            return true;
        }

        @Override
        public boolean includeAdvancedUI()
        {
            return false;
        }

        @Override
        public boolean includeNavigationLinks()
        {
            return false;
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
            audit(searchForm, false);
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
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            return ss.getSearchResultTemplate(getTemplate());
        }
    }

    
    protected void audit(SearchForm form, boolean external)
    {
        ViewContext ctx = getViewContext();
        String comment = form.getComment();

        if (external)
        {
            String prefix = "Searched against " + form.getConfig().getPrimaryDescription(ctx.getContainer());

            if (StringUtils.isBlank(comment))
                comment = prefix;
            else
                comment = prefix + ": " + comment;
        }

        audit(ctx.getUser(), ctx.getContainer(), form.getQueryString(), comment);
    }

    
    protected void audit(User user, @Nullable Container c, String query, String comment)
    {
        if (user.isSearchUser() || StringUtils.isEmpty(query))
            return;

        AuditLogService.I audit = AuditLogService.get();
        if (null == audit)
            return;

        if (null == c)
            c = ContainerManager.getRoot();

        if (query.length() > 200)
            query = query.substring(0, 197) + "...";

        audit.addEvent(user, c, SearchModule.EVENT_TYPE, query, comment);
    }
}
