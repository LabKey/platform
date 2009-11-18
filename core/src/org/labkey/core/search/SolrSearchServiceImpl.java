package org.labkey.core.search;

import org.labkey.api.webdav.Resource;
import org.apache.log4j.Category;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 16, 2009
 * Time: 11:45:44 AM
 */
public class SolrSearchServiceImpl extends AbstractSearchService
{
    static Category _log = Category.getInstance(SolrSearchServiceImpl.class);
    
    protected void index(String id, Resource r)
    {
        _log.info("INDEX: " + r.getExecuteHref(null));
//        if ("text/html".equals(r.getContentType()))
//        {
//            try
//            {
//                IOUtils.copy(r.getInputStream(User.getSearchUser()), System.out);
//            }
//            catch (IOException x)
//            {
//                _log.error(x);
//            }
//        }
    }

    protected void commit()
    {
        _log.info("COMMIT");
    }
}
