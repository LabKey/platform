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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.search;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.queryParser.ParseException;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.*;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.*;
import org.labkey.api.webdav.WebdavService;
import org.labkey.search.model.AbstractSearchService;
import org.labkey.search.model.ExternalIndexProperties;
import org.labkey.search.model.LuceneSearchServiceImpl;
import org.labkey.search.model.SearchPropertyManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Date;
import java.util.List;

public class SearchController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SearchController.class);

    public SearchController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(ReadPermission.class)
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
        int _msgid = 0;
        
        public void validateCommand(AdminForm target, Errors errors)
        {
        }

        public ModelAndView getView(AdminForm form, boolean reshow, BindException errors) throws Exception
        {
            ExternalIndexProperties props = SearchPropertyManager.getExternalIndexProperties();

            WebPartView indexerView = new JspView<AdminForm>(SearchController.class, "view/indexerAdmin.jsp", form, errors);
            indexerView.setTitle("Primary Index Configuration");

            WebPartView externalIndexView = new JspView<ExternalIndexProperties>(SearchController.class, "view/externalIndex.jsp", props, errors);
            externalIndexView.setTitle("External Index Configuration");

            WebPartView statsView = new JspView<AdminForm>(SearchController.class, "view/indexerStats.jsp", form, errors);
            statsView.setTitle("Primary Index Statistics");
            
            return new VBox(indexerView, externalIndexView, statsView);
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
                ss.start();
                SearchPropertyManager.setCrawlerRunningState(true);
                audit(getViewContext().getUser(), null, "(admin action)", "Crawler Started");
            }
            else if (form.isPause())
            {
                ss.pause();
                SearchPropertyManager.setCrawlerRunningState(false);
                audit(getViewContext().getUser(), null, "(admin action)", "Crawler Paused");
            }
            else if (form.isDelete())
            {
                ss.clear();
                _msgid = 1;
                audit(getViewContext().getUser(), null, "(admin action)", "Index Deleted");
            }
            else if (form.isPath())
            {
                SearchPropertyManager.setPrimaryIndexPath(form.getPrimaryIndexPath()); 
                ss.updatePrimaryIndex();
                _msgid = 2;
                audit(getViewContext().getUser(), null, "(admin action)", "Index Path Set");
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
            PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "Full-Text Search Configuration", null);
            return root;
        }
    }


    @RequiresSiteAdmin
    public class SetExternalIndexAction extends SimpleRedirectAction<AdminForm>
    {
        @Override
        public ActionURL getRedirectURL(AdminForm form) throws Exception
        {
            SearchPropertyManager.saveExternalIndexProperties(form);
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            // TODO: Add to SearchService interface
            ((LuceneSearchServiceImpl)ss).resetExternalIndex();

            ActionURL url = new ActionURL(AdminAction.class, ContainerManager.getRoot());
            url.addParameter("externalMessage", "External index set");
            return url;
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
                HttpServletResponse response = getViewContext().getResponse();
                response.setContentType("text/plain");
                PrintWriter out = response.getWriter();
                out.print(message);
                out.close();
                response.flushBuffer();

                return null;
            }
        }

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
                String returnUrl = getViewContext().getRequest().getParameter("returnUrl");
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
                String returnUrl = getViewContext().getRequest().getParameter("returnUrl");
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
            String returnUrl = getViewContext().getRequest().getParameter("returnUrl");
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
                task = ss.indexContainer(null, getViewContext().getContainer(), null);
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



    @RequiresPermissionClass(ReadPermission.class)
    public class JsonAction extends ApiAction<SearchForm>
    {
        @Override
        public ApiResponse execute(SearchForm form, BindException errors) throws Exception
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            if (null == ss)
            {
                HttpView.throwNotFound();
                return null;
            }

            String query = form.getQueryString();
            JSONObject response = new JSONObject();
            Object[] arr = new Object[0];
            int totalHits = 0;

            if (null != StringUtils.trimToNull(query))
            {
                //UNDONE: paging, rowlimit etc
                int limit = form.getLimit() < 0 ? 1000 : form.getLimit();
                SearchService.SearchResult result = ss.search(query, null, getViewContext().getUser(), ContainerManager.getRoot(), form.getIncludeSubfolders(),
                        form.getOffset(), limit);
                List<SearchService.SearchHit> hits = result.hits;
                totalHits = result.totalHits;

                arr = new Object[hits.size()];

                int i=0;
                for (SearchService.SearchHit hit : hits)
                {
                    JSONObject o = new JSONObject();
                    String id = StringUtils.isEmpty(hit.docid) ? String.valueOf(i) : hit.docid;
                    o.put("id", id);
                    o.put("title", hit.displayTitle);
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


    @RequiresPermissionClass(ReadPermission.class)
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

    @RequiresPermissionClass(ReadPermission.class)
    public class SearchAction extends SimpleViewAction<SearchForm>
    {
        private String _category = null;
        private SearchForm.SearchScope _scope = null;

        public ModelAndView getView(SearchForm form, BindException errors) throws Exception
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null == ss)
            {
                HttpView.throwNotFound("Search service is not registered");
                return null;
            }

            form.setPrint(isPrint());
            _category = form.getCategory();
            _scope = form.getSearchScope(getViewContext().getContainer());

            audit(form);

            // reenable caching for search results page (fast browser back button)
            HttpServletResponse response = getViewContext().getResponse();
            response.setDateHeader("Expires", System.currentTimeMillis() + (5 * 60 * 1000));
            response.setHeader("Cache-Control", "private");
            response.setHeader("Pragma", "cache");
            response.addHeader("Vary", "Cookie");
            getPageConfig().setMetaTag("robots", "noindex");
            getPageConfig().setHelpTopic(new HelpTopic("luceneSearch"));

            HttpView search= new JspView<SearchForm>("/org/labkey/search/view/search.jsp", form);
            return search;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            String title = "Search";
            switch (_scope)
            {
                case All:
                    title += " site";
                    break;
                case Project:
                    title += " project '" + getContainer().getProject().getName() + "'";
                    break;
                case Folder:
                    title += " folder '" + getContainer().getName() + "'";
                    break;
            }
            if (null != _category)
                title += " for " + _category + "s";
            return root.addChild(title);
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
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
        private String _container = null;
        private boolean _advanced = false;
        private int _offset = 0;
        private int _limit = 1000;
        private String _category = null;
        private boolean _includeSubfolders = true;
        private String _comment = null;
        private int _textBoxWidth = 50; // default size
        private boolean _includeHelpLink = true;

        public @Nullable String searchExternalIndex(String queryString) throws IOException, ParseException
        {
            return LuceneSearchServiceImpl.searchExternal(queryString);
        }

        public static enum SearchScope {All, Project, Folder}

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

        public String getContainer()
        {
            return _container;
        }

        public Container getSearchContainer()
        {
            return null == getContainer() ? ContainerManager.getRoot() : ContainerManager.getForId(getContainer());
        }

        public SearchScope getSearchScope(Container currentContainer)
        {
            Container searchContainer = getSearchContainer();

            if (ContainerManager.getRoot().equals(searchContainer) && getIncludeSubfolders())
                return SearchScope.All;

            if (searchContainer.isProject() && getIncludeSubfolders())
                return SearchScope.Project;

            return SearchScope.Folder;
        }

        public void setContainer(String container)
        {
            _container = container;
        }

        public void setIncludeSubfolders(boolean b)
        {
            _includeSubfolders = b;
        }

        public boolean getIncludeSubfolders()
        {
            return null == _container || _includeSubfolders;
        }

        public boolean isAdvanced()
        {
            return _advanced;
        }

        public void setAdvanced(boolean advanced)
        {
            _advanced = advanced;
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
    }

    
    protected void audit(SearchForm form)
    {
        ViewContext c = getViewContext();
        audit(c.getUser(), c.getContainer(), form.getQueryString(), form.getComment());
    }

    
    protected void audit(User user, Container c, String query, String comment)
    {
        if (user == User.getSearchUser() || StringUtils.isEmpty(query))
            return;

        AuditLogService.I audit = AuditLogService.get();
        if (null == audit)
            return;

        if (query.length() > 200)
            query = query.substring(0,197) + "...";

        audit.addEvent(user, c, SearchModule.EVENT_TYPE, query, comment);
    }
}
