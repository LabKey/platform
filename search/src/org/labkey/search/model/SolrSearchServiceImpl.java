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
package org.labkey.search.model;

import org.apache.log4j.Category;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.Resource;

import java.io.IOException;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 16, 2009
 * Time: 11:45:44 AM
 */
public class SolrSearchServiceImpl extends AbstractSearchService
{
    static Category _log = Category.getInstance(SolrSearchServiceImpl.class);
    
    protected void index(String id, Resource r, Map preprocessMap)
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

    public SearchResult search(String queryString, SearchCategory category, User user, Container root, boolean recursive, int offset, int limit) throws IOException
    {
        return null;
    }

    public void clearIndex()
    {
        
    }

    protected void commitIndex()
    {
        _log.info("COMMIT");
    }

    protected void shutDown()
    {
    }

    @Override
    protected void deleteIndexedContainer(String id)
    {
    }

    public String escapeTerm(String term)
    {
        return term;
    }

    @Override
    protected void deleteDocument(String id)
    {
        
    }

    public WebPartView getSearchView(boolean includeSubfolders, int textBoxWidth)
    {
        return null;
    }
}
