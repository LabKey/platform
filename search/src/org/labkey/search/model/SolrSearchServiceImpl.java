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
package org.labkey.search.model;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.search.SearchScope;
import org.labkey.api.security.User;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.WebdavResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * User: matthewb
 * Date: Nov 16, 2009
 * Time: 11:45:44 AM
 */
public class SolrSearchServiceImpl extends AbstractSearchService
{
    private static final Logger _log = Logger.getLogger(SolrSearchServiceImpl.class);
    
    protected boolean index(String id, WebdavResource r, Map preprocessMap)
    {
        _log.info("INDEX: " + r.getExecuteHref(null));
        return true;
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

    @Override
    public SearchResult search(String queryString, @Nullable List<SearchCategory> categories, User user, Container current, SearchScope scope, int offset, int limit) throws IOException
    {
        return null;
    }

    @Override
    public SearchResult searchExternal(String queryString, int offset, int limit) throws IOException
    {
        return null;
    }

    @Override
    public void clearIndex()
    {
    }

    @Override
    public void upgradeIndex()
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

    @Override
    protected void deleteDocumentsForPrefix(String prefix)
    {
    }

    @Override
    public String getIndexFormatDescription()
    {
        return "Solr Index";
    }

    public WebPartView getSearchView(boolean includeSubfolders, int textBoxWidth, boolean includeHelpLink, boolean isWebpart)
    {
        return null;
    }

    @Override
    public Map<String, Double> getSearchStats()
    {
        return null;
    }
}
