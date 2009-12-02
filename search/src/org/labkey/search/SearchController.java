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
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

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
            return new ActionURL(SearchAction.class, getContainer());
        }
    }

    // UNDONE: remove; for testing only
    @RequiresSiteAdmin
    public class IndexAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            SearchService ss = ServiceRegistry.get().getService(SearchService.class);
            if (null == ss)
                return null;

//            List<SearchService.IndexTask> list = ss.getTasks();
//            if (list.size() == 0)
            {
                ss.clearIndex();

                SearchService.IndexTask task = ss.createTask("Full Index");

                for (Module m : ModuleLoader.getInstance().getModules())
                {
                    m.enumerateDocuments(task, null, null);
                }

                task.setReady();
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
            html += "[<a href=\"" + PageFlowUtil.filter(new ActionURL(IndexAction.class, getContainer())) + "\">reindex</a>]<br>";

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