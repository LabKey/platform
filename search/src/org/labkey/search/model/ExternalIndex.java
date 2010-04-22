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

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.store.Directory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * User: adam
 * Date: Apr 19, 2010
 * Time: 3:33:05 PM
 */
public class ExternalIndex extends SearchableIndex
{
    private final Object _swapLock = new Object();
    private final File _indexRoot;

    public ExternalIndex(File indexRoot, Analyzer analyzer) throws IOException
    {
        super(ensureIndexPath(indexRoot), analyzer);
        _indexRoot = indexRoot;
    }

    @NotNull
    @Override
    LabKeyIndexSearcher getSearcher() throws IOException
    {
        // Must get the swap lock as well -- swapping operation needs to block searcher gets, but not releases.
        synchronized (_swapLock)
        {
            return super.getSearcher();
        }
    }

    void swap() throws IOException, InterruptedException
    {
        synchronized (_swapLock)
        {
            close();
            File current = ensureIndexPath(_indexRoot);
            openDirectory(current);
            _searcher = newSearcher();
        }
    }


    void close() throws IOException, InterruptedException
    {
        synchronized (_swapLock)
        {
            LabKeyIndexSearcher searcher = getSearcher();
            releaseSearcher(searcher);

            // Wait until all active searchers are released
            while (searcher.isInUse())
                Thread.sleep(1);

            // Now release the one held by this class, which should close the underlying searcher.
            releaseSearcher(searcher);

            // Now we can close the current directory
            Directory directory = getDirectory();
            directory.close();
        }
    }


    // If staging exists, rename current -> old and staging -> current
    private static File ensureIndexPath(File indexRoot) throws IOException
    {
        // Rename "index" directory to "old"
        File current = new File(indexRoot, "index");
        File staging = new File(indexRoot, "staging");
        File old = new File(indexRoot, "old");

        if (staging.exists())
        {
            FileUtils.deleteDirectory(old);

            if (current.exists())
            {
                if (!current.renameTo(old))
                    throw new IOException("Failed to rename '" + current + "' to '" + old + "'");
            }

            if (!staging.renameTo(current))
                throw new IOException("Failed to rename '" + staging + "' to '" + current + "'");
        }

        return current;
    }
}
