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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

// Handles synchronization and reference counting of IndexSearchers
abstract class SearchableIndex
{
    private final Analyzer _analyzer;
    private Directory _directory;
    protected final Object _searcherLock = new Object();
    protected @NotNull LabKeyIndexSearcher _searcher;

    // A read-only Lucene index.  This class guarantees a single IndexSearcher is used across multiple threads; when
    // that searcher is closed, the directory is no longer in use.  Subclasses can behave differently -- they may
    // had out different instances of IndexSearcher, e.g., to accommodate writes, commits and optimization.
    public SearchableIndex(File indexPath, Analyzer analyzer) throws IOException
    {
        openDirectory(indexPath);
        _analyzer = analyzer;
        _searcher = newSearcher();
    }

    protected LabKeyIndexSearcher newSearcher() throws IOException
    {
        return new LabKeyIndexSearcher(_directory); // TODO: Warm the searcher for performance?
    }

    protected void openDirectory(File indexPath) throws IOException
    {
        _directory = FSDirectory.open(indexPath);
    }

    protected Directory getDirectory()
    {
        return _directory;
    }

    public Analyzer getAnalyzer()
    {
        return _analyzer;
    }

    @NotNull
    LabKeyIndexSearcher getSearcher() throws IOException
    {
        synchronized (_searcherLock)
        {
            // Increment ref count
            _searcher.increment();

            return _searcher;
        }
    }

    void releaseSearcher(LabKeyIndexSearcher searcher) throws IOException
    {
        synchronized (_searcherLock)
        {
            // Decrement ref count
            searcher.decrement();
        }
    }

    void close() throws IOException, InterruptedException
    {
        _directory.close();
    }
}