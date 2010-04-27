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

import org.apache.lucene.analysis.SimpleAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;

import java.io.IOException;

/**
* User: adam
* Date: Apr 17, 2010
* Time: 7:22:11 PM
*/

// Add reference counting to IndexSearcher so we know when to safely close it.  See #9785.
class LabKeyIndexSearcher extends IndexSearcher
{
    private int _references = 1;

    LabKeyIndexSearcher(Directory directory) throws IOException
    {
        super(ensureIndexCreated(directory));
    }

    // IndexSearcher() will throw if the directory is empty or non-existent... opening an IndexWriter first ensures
    // that the directory is ready.
    private static Directory ensureIndexCreated(Directory directory)
    {
        try
        {
            IndexWriter iw = new IndexWriter(directory, new SimpleAnalyzer(), IndexWriter.MaxFieldLength.UNLIMITED);
            iw.close();
            return directory;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Couldn't open an IndexWriter on " + directory, e);
        }
    }

    synchronized void increment()
    {
        _references++;
    }

    synchronized void decrement() throws IOException
    {
        _references--;

        if (0 == _references)
            super.close();
    }

    @Override
    public void close() throws IOException
    {
        throw new IllegalStateException("Can't close a LabKeyIndexSearcher; call Index.releaseSearcher() instead.");
    }

    synchronized boolean isInUse()
    {
        return (_references > 1);
    }
}
