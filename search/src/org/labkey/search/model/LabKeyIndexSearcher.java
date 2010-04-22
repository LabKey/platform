package org.labkey.search.model;

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
        super(directory, true);
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
