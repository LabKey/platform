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