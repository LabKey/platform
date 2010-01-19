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
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.webdav.WebdavService;
import org.labkey.search.model.AbstractSearchService;
import org.labkey.search.model.DavCrawler;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.sql.Date;
import java.util.List;
import java.util.Map;

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
            return new ActionURL(SearchAction.class, getContainer());
        }
    }


    public static class AdminForm
    {
        private boolean pause;
        private boolean start;

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
    }
    

    @RequiresSiteAdmin
    public class AdminAction extends FormViewAction<AdminForm>
    {
        public void validateCommand(AdminForm target, Errors errors)
        {
        }

        public ModelAndView getView(AdminForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<AdminForm>(SearchController.class, "view/admin.jsp", form, errors);
        }

        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            if (null == ss)
            {
                errors.reject("Indexing service is not running");
                return false;
            }

            Map<String,String> m = PropertyManager.getProperties(SearchModule.class.getName(),true);
            if (form.isStart())
            {
                ss.start();
                m.put(SearchModule.searchRunningState,"true");
            }
            else if (form.isPause())
            {
                ss.pause();
                m.put(SearchModule.searchRunningState,"false");
            }
            PropertyManager.saveProperties(m);
            return true;
        }
        
        public URLHelper getSuccessURL(AdminForm o)
        {
            return new ActionURL(AdminAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Admin Console", new ActionURL("admin","showAdmin","/"));
            root.addChild("Indexing Configuration");
            return root;
        }

        public ActionURL getRedirectURL(Object o) throws Exception
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
            return new ActionURL(SearchAction.class, getContainer());
        }
    }


    // UNDONE: remove; for testing only
    @RequiresSiteAdmin
    public class ClearAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            // SimpleRedirectAction doesn't take a form
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null == ss)
                return null;

            ss.clear();

            try
            {
                String returnUrl = getViewContext().getRequest().getParameter("returnUrl");
                if (null != returnUrl)
                    return new ActionURL(returnUrl);
            }
            catch (Exception x)
            {
            }
            return new ActionURL(SearchAction.class, getContainer());
        }
    }


    // UNDONE: remove; for testing only
    @RequiresSiteAdmin
    public class CommitAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            // SimpleRedirectAction doesn't take a form
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null == ss)
                return null;

            ((AbstractSearchService)ss).commit();

            try
            {
                String returnUrl = getViewContext().getRequest().getParameter("returnUrl");
                if (null != returnUrl)
                    return new ActionURL(returnUrl);
            }
            catch (Exception x)
            {
            }
            return new ActionURL(SearchAction.class, getContainer());
        }
    }



    // UNDONE: remove; for testing only
    @RequiresSiteAdmin
    public class PauseAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            // SimpleRedirectAction doesn't take a form
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null == ss)
                return null;

            ss.pause();

            try
            {
                String returnUrl = getViewContext().getRequest().getParameter("returnUrl");
                if (null != returnUrl)
                    return new ActionURL(returnUrl);
            }
            catch (Exception x)
            {
            }
            return new ActionURL(SearchAction.class, getContainer());
        }
    }


    // UNDONE: remove; for testing only
    @RequiresSiteAdmin
    public class StartAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            // SimpleRedirectAction doesn't take a form
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null == ss)
                return null;

            ss.start();

            try
            {
                String returnUrl = getViewContext().getRequest().getParameter("returnUrl");
                if (null != returnUrl)
                    return new ActionURL(returnUrl);
            }
            catch (Exception x)
            {
            }
            return new ActionURL(SearchAction.class, getContainer());
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
                    Path.parse(WebdavService.getServletPath()).append(getContainer().getParsedPath()),
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
            return new ActionURL(SearchAction.class, getContainer());
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

            if (wait)
            {
                ss.purgeQueues();
                ss.start();
            }

            SearchService.IndexTask task = null;

            SearchService.IndexTask lastFullTask = _lastFullTask;
            boolean fullInProgress = false;
            for (SearchService.IndexTask t : ss.getTasks())
            {
                if (lastFullTask == t)
                    fullInProgress = true;
            }

            if (!fullInProgress)
            {
                if (full)
                {
                    if (!fullInProgress)
                    {
                        task = ss.indexFull(true);
                        _lastFullTask = task;
                    }
                    else
                        task = lastFullTask;
                }
                else
                {
                    task = ss.indexContainer(null, getViewContext().getContainer(), null);
                    _lastIncrementalTask = task;
                }
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
            return new ActionURL(SearchAction.class, getContainer());
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
                SearchService.SearchResult result = ss.search(query, getViewContext().getUser(), ContainerManager.getRoot());
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
    

    static SearchService.IndexTask _lastFullTask = null;
    static SearchService.IndexTask _lastIncrementalTask = null;

    
    @RequiresPermissionClass(ReadPermission.class)
    public class SearchAction extends SimpleViewAction<SearchForm>
    {
        public ModelAndView getView(SearchForm form, BindException errors) throws Exception
        {

            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            if (null == ss)
            {
                HttpView.throwNotFound();
                return null;
            }

            String statusMessage = "";

            for (String q : form.getQ())
            {
                q = StringUtils.strip(q," +-");
                if (StringUtils.isEmpty(q))
                    continue;
                if (ss.isParticipantId(getUser(), q))
                    statusMessage = q + " is a participantid\n";
            }

            if (ss.isRunning())
                statusMessage += "indexing is running\n";
            else
                statusMessage += "indexing is paused\n";

/*
            List<SearchService.IndexTask> tasks = ss.getTasks();

            if (tasks.isEmpty())
            {
                tasks = new ArrayList<SearchService.IndexTask>();
                if (null != _lastFullTask)
                    tasks.add(_lastFullTask);
                if (null != _lastIncrementalTask)
                    tasks.add(_lastIncrementalTask);
            }

            for (SearchService.IndexTask task : tasks)
            {
                int count = task.getIndexedCount();
                long start = task.getStartTime();
                long end = task.getCompleteTime();
                if (0 == end)
                    end = System.currentTimeMillis();
                int skipped = task.getFailedCount();
                if (task.getCompleteTime() != 0)
                    statusMessage += "Indexing complete: ";
                else
                    statusMessage += "Indexing in progress: ";
                statusMessage += Formats.commaf0.format(count) + " documents (" + DateUtil.formatDuration(end-start) + ") "; // Remove for demo: Formats.commaf0.format(skipped) + " skipped or failed <br>";
            }
*/

            form.setPrint(isPrint());
            form.setStatusMessage(statusMessage);

            HttpView search= new JspView<SearchForm>("/org/labkey/search/view/search.jsp", form);
            return search;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Search");
        }
    }


    public static class SearchForm
    {
        private String[] _query;
        private String _sort;
        private boolean _print = false;
        private boolean _guest = false;       // TODO: Just for testing
        private String _statusMessage;
        private int _page = 0;
        private String _container = null;

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

        public String getStatusMessage()
        {
            return _statusMessage;
        }

        public void setStatusMessage(String statusMessage)
        {
            _statusMessage = statusMessage;
        }

        public boolean isGuest()
        {
            return _guest;
        }

        public void setGuest(boolean guest)
        {
            _guest = guest;
        }

        public int getPage()
        {
            return _page;
        }

        public void setPage(int page)
        {
            _page = page;
        }

        public String getContainer()
        {
            return _container;
        }

        public void setContainer(String container)
        {
            _container = container;
        }
    }
}
