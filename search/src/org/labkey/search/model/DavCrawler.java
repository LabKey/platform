/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.search.model;

import org.labkey.api.search.SearchService;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.webdav.Resource;
import org.jetbrains.annotations.NotNull;

import java.sql.Date;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 18, 2009
 * Time: 11:09:03 AM
 */
public class DavCrawler
{
    public static Runnable start(@NotNull SearchService.IndexTask task, Path path, Date modifiedSince)
    {
        WebdavResolver res = WebdavService.getResolver();
        Path fullpath = new Path(WebdavService.getServletPath()).append(path);
        return start(task,res,fullpath);
    }


    public static Runnable start(@NotNull final SearchService.IndexTask task, final WebdavResolver res, final Path path)
    {
        return new Runnable()
        {
            public void run()
            {
                crawl(task, res, path);
            }
        };
    }


    static void crawl(@NotNull SearchService.IndexTask task, WebdavResolver res, Path path)
    {
        Resource r = res.lookup(path);
        if (null == r)
            return;

        if (r.isFile())
        {
            task.addResource("dav:" + path, SearchService.PRIORITY.background);
        }
        else if (r.isCollection())
        {
            for (Resource child : r.list())
            {
                if (child.isFile())
                    task.addResource("dav:" + child.getPath(), SearchService.PRIORITY.background);
                else if (!skipContainer(child.getName()))
                    task.addRunnable(start(task, res, child.getPath()), SearchService.PRIORITY.crawl);
            }
        }
    }

    static boolean skipContainer(String name)
    {
        // TODO configurable map, patterns
        return ".svn".equals(name);
    }
}