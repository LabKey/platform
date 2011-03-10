/*
 * Copyright (c) 2011 LabKey Corporation
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
import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;
import org.labkey.api.search.SearchMisconfiguredException;

import java.io.IOException;

/*
* User: adam
* Date: Mar 9, 2011
* Time: 10:39:44 PM
*/
public class NoopWritableIndex implements WritableIndex
{
    private final Logger _log;

    public NoopWritableIndex(Logger log)
    {
        _log = log;
    }

    @Override
    public void clear()
    {
        log("clear the search index");
    }

    @Override
    public void deleteDocument(String id)
    {
        log("delete documents from the search index");
    }

    @Override
    public LabKeyIndexSearcher getSearcher() throws IOException
    {
        throw new SearchMisconfiguredException();
    }

    @Override
    public void releaseSearcher(LabKeyIndexSearcher searcher) throws IOException
    {
    }

    @Override
    public void deleteQuery(Query query) throws IOException
    {
        log("delete documents from the search index");
    }

    @Override
    public void index(String documentId, Document doc) throws IOException
    {
        log("index documents for search");
    }

    @Override
    public void commit()
    {
    }

    @Override
    public void close() throws IOException, InterruptedException
    {
    }

    @Override
    public void optimize()
    {
        log("optimize the search index");
    }

    private void log(String action)
    {
        _log.warn("Unable to " + action + "; the search index is misconfigured. Search is disabled and new documents are not being indexed. Correct the problem and restart your server.");
    }
}
