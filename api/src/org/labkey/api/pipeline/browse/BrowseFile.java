/*
 * Copyright (c) 2007 LabKey Corporation
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

package org.labkey.api.pipeline.browse;

import org.labkey.api.pipeline.PipeRoot;

import java.io.File;

public class BrowseFile
{
    final private PipeRoot pipeRoot;
    final private File file;
    final private String relativePath;

    public BrowseFile(PipeRoot pipeRoot, File file)
    {
        this.file = file;
        this.pipeRoot = pipeRoot;
        this.relativePath = pipeRoot.relativePath(file);
    }

    /*public BrowseFile(PipeRoot pipeRoot, String path)
    {
        this.pipeRoot = pipeRoot;
        this.file = pipeRoot.resolvePath(path);
        this.relativePath = path;
    }*/

    public File getFile()
    {
        return file;
    }

    public PipeRoot getPipeRoot()
    {
        return pipeRoot;
    }

    public String getRelativePath()
    {
        return relativePath;
    }

    public boolean isDirectory()
    {
        return file.isDirectory();
    }

    public String getName()
    {
        return file.getName();
    }
}
