/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.labkey.api.search.SearchMisconfiguredException;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/*
* User: adam
* Date: Mar 9, 2011
* Time: 10:39:44 PM
*/
public class NoopWritableIndex implements WritableIndexManager
{
    private final Logger _log;
    private final String _statusMessage;
    private final AtomicLong _errors = new AtomicLong(0);

    public NoopWritableIndex(String statusMessage, Logger log)
    {
        _log = log;
        _statusMessage = statusMessage;
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
    public IndexSearcher getSearcher() throws IOException
    {
        throw new SearchMisconfiguredException();
    }

    @Override
    public void releaseSearcher(IndexSearcher searcher) throws IOException
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
    public Map<String, String> getIndexFormatProperties()
    {
        return Collections.singletonMap("Format", "No-op index");
    }

    @Override
    public Directory getCurrentDirectory()
    {
        throw new SearchMisconfiguredException();
    }

    @Override
    public boolean isReal()
    {
        return false;
    }

    private void log(String action)
    {
        if (0 == (_errors.get() % 10000))
            _log.warn("Unable to " + action + "; " + _statusMessage);

        _errors.incrementAndGet();
    }
}
