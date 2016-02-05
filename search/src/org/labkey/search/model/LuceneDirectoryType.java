/*
 * Copyright (c) 2016 LabKey Corporation
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

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.SimpleFSDirectory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Created by adam on 2/3/2016.
 */
public enum LuceneDirectoryType
{
    Default
            {
                @Override
                public Directory open(Path path) throws IOException
                {
                    return FSDirectory.open(path);
                }
            },
    MMapDirectory
            {
                @Override
                public Directory open(Path path) throws IOException
                {
                    return new MMapDirectory(path);
                }
            },
    NIOFSDirectory
            {
                @Override
                public Directory open(Path path) throws IOException
                {
                    return new NIOFSDirectory(path);
                }
            },
    SimpleFSDirectory
            {
                @Override
                public Directory open(Path path) throws IOException
                {
                    return new SimpleFSDirectory(path);
                }
            };

    public abstract Directory open(Path path) throws IOException;
}
