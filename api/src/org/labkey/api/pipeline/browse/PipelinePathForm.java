/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewForm;
import org.labkey.api.util.URIUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;

import java.io.File;
import java.util.List;
import java.util.ArrayList;

/**
 * <code>PipelinePathForm</code>
 * 
 * Form bean class for pipeline root navigation.
 */
public class PipelinePathForm extends ViewForm
{
    private String _path;
    private String[] _file = new String[0];

    public String getPath()
    {
        return _path;
    }

    public void setPath(String path)
    {
        _path = path;
    }

    public String[] getFile()
    {
        return _file;
    }

    public void setFile(String[] file)
    {
        for (String s : file)
        {
            if (s != null)
            {
                if (s.indexOf("..") != -1 || s.indexOf("/") != -1 || s.indexOf("\\") != -1)
                {
                    throw new IllegalArgumentException("File names should not include any path information");
                }
            }
        }
        _file = file;
    }

    /**
     * Ensures that the files are all in the same directory, which is under the container's pipeline root, and that
     * they all exist on disk.
     * Ensures that all files exist on disk, but they could be directories.
     * Throws NotFoundException if no files are specified, invalid files are specified, there's no pipeline root, etc.
     */
    public List<File> getValidatedFiles(Container c)
    {
        PipeRoot pr = getPipeRoot(c);

        File dir = pr.resolvePath(getPath());
        if (dir == null || !dir.exists())
            throw new NotFoundException("Could not find path " + getPath());

        if (getFile() == null || getFile().length == 0)
        {
            throw new NotFoundException("No files specified");
        }

        List<File> result = new ArrayList<File>();
        for (String fileName : _file)
        {
            File f = pr.resolvePath(getPath() + "/" + fileName);
            if (!NetworkDrive.exists(f))
            {
                throw new NotFoundException("Could not find file '" + fileName + "' in '" + getPath() + "'");
            }
            result.add(f);
        }
        return result;
    }

    public PipeRoot getPipeRoot(Container c)
    {
        PipeRoot pr = PipelineService.get().findPipelineRoot(c);
        if (pr == null)
            throw new NotFoundException("Could not find a pipeline root for " + c.getPath());
        return pr;
    }

    /** Verifies that only a single file was selected and returns it, throwing an exception if there isn't exactly one */
    public File getValidatedSingleFile(Container c)
    {
        List<File> files = getValidatedFiles(c);
        if (files.size() != 1)
        {
            throw new IllegalArgumentException("Expected a single file but got " + files.size());
        }
        return files.get(0);
    }
}
