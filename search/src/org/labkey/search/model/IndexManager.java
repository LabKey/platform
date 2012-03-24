/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

// Manages IndexSearchers via SearcherManager
abstract class IndexManager
{
    protected final SearcherManager _manager;
    protected Directory _directory;

    public IndexManager(SearcherManager manager, Directory directory)
    {
        _manager = manager;
        _directory = directory;
    }

    public @NotNull IndexSearcher getSearcher() throws IOException
    {
        return _manager.acquire();
    }

    public void releaseSearcher(IndexSearcher searcher) throws IOException
    {
        _manager.release(searcher);
    }
}