/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.search.SearchModule;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * User: adam
 * Date: Apr 19, 2010
 * Time: 3:33:05 PM
 */
public class ExternalIndexManager extends IndexManager implements SecurableResource
{
    private static final String RESOURCE_ID = "0d9aaf80-4102-102d-87fb-c74c764dc328";  // Constant, arbitrary GUID for permissions

    private final Object _swapLock = new Object();
    private final File _indexRoot;
    private final Analyzer _analyzer;

    public static ExternalIndexManager get(File indexRoot, Analyzer analyzer) throws IOException
    {
        File indexPath = ensureIndexPath(indexRoot);

/*
        This comment and code were taken from the old LabKeyIndexSearcher... it may be needed here.
        // IndexSearcher() will throw if the directory is empty or non-existent... opening an IndexWriter first ensures
        // that the directory is ready.
        IndexWriter iw = new IndexWriter(directory, new SimpleAnalyzer(), IndexWriter.MaxFieldLength.UNLIMITED);
        iw.close();

 */

        Directory directory = FSDirectory.open(indexRoot.toPath());

        return new ExternalIndexManager(directory, indexPath, analyzer);
    }

    public ExternalIndexManager(Directory directory, File indexRoot, Analyzer analyzer) throws IOException
    {
        super(new SearcherManager(directory, null), directory);
        _indexRoot = indexRoot;
        _analyzer = analyzer;
    }

    @Override
    public @NotNull IndexSearcher getSearcher() throws IOException
    {
        // Must get the swap lock -- swapping operation needs to block searcher gets, but not releases.
        synchronized (_swapLock)
        {
            return super.getSearcher();
        }
    }

    protected void openDirectory(File indexPath) throws IOException
    {
        _directory = FSDirectory.open(indexPath.toPath());
    }

    public Analyzer getAnalyzer()
    {
        return _analyzer;
    }

    void swap() throws IOException, InterruptedException
    {
        synchronized (_swapLock)
        {
            close();
            File current = ensureIndexPath(_indexRoot);
            openDirectory(current);
        }
    }


    public void close() throws IOException, InterruptedException
    {
        synchronized (_swapLock)
        {
            _manager.close();
            _directory.close();
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

    @NotNull
    @Override
    public String getResourceId()
    {
        // Unique enough, since there's only one external index
        return RESOURCE_ID;
    }

    @NotNull
    @Override
    public String getResourceName()
    {
        return "ExternalIndex";
    }

    @NotNull
    @Override
    public String getResourceDescription()
    {
        return "External Index";
    }

    @NotNull
    @Override
    public Module getSourceModule()
    {
        return ModuleLoader.getInstance().getModule(SearchModule.class);
    }

    @Override
    public SecurableResource getParentResource()
    {
        return ContainerManager.getRoot();
    }

    @NotNull
    @Override
    public Container getResourceContainer()
    {
        return ContainerManager.getRoot();
    }

    @NotNull
    @Override
    public List<SecurableResource> getChildResources(User user)
    {
        return Collections.emptyList();
    }

    @Override
    public boolean mayInheritPolicy()
    {
        return false;
    }
}
