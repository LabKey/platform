package org.labkey.core.search;

import org.labkey.api.search.SearchService;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.webdav.Resource;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 18, 2009
 * Time: 11:09:03 AM
 */
public class DavCrawler
{
    public static Runnable start(SearchService ss, String path)
    {
        WebdavResolver res = WebdavService.getResolver();
        if ("/".equals(path))
            path = "/" + WebdavService.getServletPath();
        return start(ss,res,Path.parse(path));
    }


    public static Runnable start(final SearchService ss, final WebdavResolver res, final Path path)
    {
        return new Runnable()
        {
            public void run()
            {
                crawl(ss, res, path);
            }
        };
    }


    static void crawl(SearchService ss, WebdavResolver res, Path path)
    {
        Resource r = res.lookup(path);
        if (null == r)
            return;

        if (r.isFile())
        {
            ss.addResource("dav:" + path, SearchService.PRIORITY.background);
        }
        else if (r.isCollection())
        {
            for (Resource child : r.list())
            {
                if (child.isFile())
                    ss.addResource("dav:" + child.getPath(), SearchService.PRIORITY.background);
                else if (!skipContainer(child.getName()))
                    ss.addRunnable(start(ss, res, child.getPath()), SearchService.PRIORITY.crawl);
            }
        }
    }

    static boolean skipContainer(String name)
    {
        // TODO configurable map, patterns
        return ".svn".equals(name);
    }
}