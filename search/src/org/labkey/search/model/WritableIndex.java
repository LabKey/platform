/*
 * Copyright (c) 2010 LabKey Corporation
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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.labkey.api.util.ExceptionUtil;

import java.io.File;
import java.io.IOException;

/**
 * User: adam
 * Date: Apr 18, 2010
 * Time: 9:08:19 AM
 */

// Adds synchronization for writing, in addition to searching synchronization provided by SearchableIndex
class WritableIndex extends SearchableIndex
{
    private static final Logger _log = Logger.getLogger(WritableIndex.class);

    private final Object _writerLock = new Object();

    private IndexWriter _iw = null;      // TODO: reference count and support multi-threading on this


    WritableIndex(File indexPath, Analyzer analyzer) throws IOException
    {
        super(indexPath, analyzer);
    }


    private IndexWriter getIndexWriter() throws IOException
    {
        synchronized (_writerLock)
        {
            // CONSIDER: Set a large, but finite max field length if we get OutOfMemory errors during indexing
            if (null == _iw)
                _iw = new IndexWriter(getDirectory(), getAnalyzer(), IndexWriter.MaxFieldLength.UNLIMITED);

            return _iw;
        }
    }


    void index(String id, Document doc) throws IOException
    {
        synchronized (_writerLock)
        {
            deleteDocument(id);
            getIndexWriter().addDocument(doc);
        }
    }


    void deleteDocument(String id)
    {
        try
        {
            synchronized (_writerLock)
            {
                IndexWriter iw = getIndexWriter();
                iw.deleteDocuments(new Term(LuceneSearchServiceImpl.FIELD_NAMES.uniqueId.toString(), id));
            }
        }
        catch(Throwable e)
        {
            _log.error("Indexing error deleting " + id, e);
        }
    }


    void deleteQuery(Query query) throws IOException
    {
        synchronized (_writerLock)
        {
            IndexWriter w = getIndexWriter();
            w.deleteDocuments(query);
        }
    }


    void clear()
    {
        try
        {
            synchronized (_writerLock)
            {
                getIndexWriter().deleteAll();
                commit();
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException();
        }
    }


    void commit()
    {
        synchronized (_writerLock)
        {
            try
            {
                if (null != _iw)
                    _iw.close();
            }
            catch (IOException e)
            {
                try
                {
                    _log.error("Exception closing index", e);
                    _log.error("Attempting to index close again");
                    _iw.close();
                }
                catch (IOException e2)
                {
                    _log.error("Exception closing index", e2);
                }
            }
            finally
            {
                resetSearcher();
                _iw = null;
            }
        }
    }


    protected void resetSearcher()
    {
        synchronized (_searcherLock)
        {
            try
            {
                _searcher.decrement();
                _searcher = newSearcher();
            }
            catch (IOException x)
            {

            }
        }
    }


    void optimize()
    {
        try
        {
            synchronized (_writerLock)
            {
                IndexWriter iw = getIndexWriter();
                iw.optimize();
            }
        }
        catch (IOException e)
        {
            ExceptionUtil.logExceptionToMothership(null, e);
        }
    }
}
