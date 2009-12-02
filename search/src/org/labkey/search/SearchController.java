/*
 * Copyright (c) 2008 LabKey Corporation
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
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.view.*;
import org.labkey.api.data.Container;
import org.labkey.search.model.DavCrawler;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.sql.Date;

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


    // UNDONE: remove; for testing only
    @RequiresSiteAdmin
    public class IndexAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            // SimpleRedirectAction doesn't take a form
            boolean full = "1".equals(getViewContext().getRequest().getParameter("full"));
            String returnUrl = getViewContext().getRequest().getParameter("returnUrl");

            SearchService ss = ServiceRegistry.get().getService(SearchService.class);

            if (null == ss)
                return null;

            boolean fullInProgress = false;
            for (SearchService.IndexTask task : ss.getTasks())
                if ("Full Index".equals(task.getDescription()))
                    fullInProgress = true;

            if (!fullInProgress)
            {
                ss.clearIndex();


                Date since = full ? null : new Date(0);
                Container c = full ? null : getViewContext().getContainer();

                SearchService.IndexTask task = ss.createTask(full ? "Full Index" : c.getName());

                for (Module m : ModuleLoader.getInstance().getModules())
                {
                    m.enumerateDocuments(task, c, since);
                }

//                if (full) DavCrawler.start(task, Path.rootPath, since);

                task.setReady();
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
    public class SearchAction extends SimpleViewAction<SearchForm>
    {
        public ModelAndView getView(SearchForm form, BindException errors) throws Exception
        {
            String query = form.getQuery();

            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            if (null == ss)
            {
                HttpView.throwNotFound();
                return null;
            }

            String html = "";

            List<SearchService.IndexTask> tasks = ss.getTasks();
            for (SearchService.IndexTask task : tasks)
            {
                int count = task.getIndexedCount();
                long start = task.getStartTime();
                long end = task.getCompleteTime();
                if (0 == end)
                    end = System.currentTimeMillis();
                int skipped = task.getFailedCount();
                html += "Indexing in progress: " + count + " documents (" + DateUtil.formatDuration(end-start) + ") " + skipped + " skipped or failed <br>";
            }
            html += "[<a href=\"" + PageFlowUtil.filter(new ActionURL(IndexAction.class, getContainer()).addParameter("full","1")) + "\">reindex (full)</a>]<br>";
            html += "[<a href=\"" + PageFlowUtil.filter(new ActionURL(IndexAction.class, getContainer())) + "\">reindex (incremental)</a>]<br>";

            html += "<form><input type=\"text\" size=50 id=\"query\" name=\"query\" value=\"" + PageFlowUtil.filter(query) + "\">&nbsp;" + PageFlowUtil.generateSubmitButton("Search") + "</form>";
            HtmlView searchBox = new HtmlView(html);
            getPageConfig().setFocusId("query");

            if (StringUtils.isEmpty(StringUtils.trimToEmpty(query)))
                return searchBox;

            String results = ss.search(query);

            return new VBox(searchBox, new HtmlView(results));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Search");
        }
    }


    public static class SearchForm
    {
        private String _query;
        private String _sort;

        public String getQuery()
        {
            return _query;
        }

        public void setQuery(String query)
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
    }

}