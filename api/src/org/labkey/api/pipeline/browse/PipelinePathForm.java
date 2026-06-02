/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewForm;
import org.labkey.vfs.FileLike;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * <code>PipelinePathForm</code>
 * 
 * Form bean class for pipeline root navigation.
 */
public class PipelinePathForm extends ViewForm
{
    private String _path;
    private String[] _file = new String[0];
    private int[] _fileIds = new int[0];

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
        if (null == file)
            return;
        for (String s : file)
        {
            if (s != null)
            {
                if (s.contains("..") || s.contains("/") || s.contains("\\"))
                {
                    throw new IllegalArgumentException("File names should not include any path information");
                }
            }
        }
        _file = file;
    }

    public int[] getFileIds()
    {
        return _fileIds;
    }

    public void setFileIds(int[] fileIds)
    {
        _fileIds = fileIds;
    }

    /**
     * For the string filesnames provided, ensures that the files are all in the same directory, which is under the container's pipeline root,
     * and that they all exist on disk, though they could be directories, not files.
     * For ExpData IDs provided, ensures the files exists and the user has read permission on the associated container.  The files do not need to be located in the same directory.
     * Throws NotFoundException if no files are specified, invalid files are specified, there's no pipeline root, etc.
     */
    public List<FileLike> getValidatedFiles(Container c)
    {
        return getValidatedFiles(c, false);
    }

    public List<FileLike> getValidatedFiles(Container c, boolean allowNonExistentFiles)
    {
        PipeRoot pr = getPipeRoot(c);

        FileLike dir = pr.resolvePathToFileLike(getPath());
        if (dir == null || !dir.exists())
            throw new NotFoundException("Could not find path " + getPath());

        if ((getFile() == null || getFile().length == 0) && (getFileIds() == null || getFileIds().length == 0))
        {
            throw new NotFoundException("No files specified");
        }

        List<FileLike> result = new ArrayList<>();
        for (String fileName : _file)
        {
            FileLike f = pr.resolvePathToFileLike(getPath() + "/" + fileName);
            if (!allowNonExistentFiles && !NetworkDrive.exists(f))
            {
                throw new NotFoundException("Could not find file '" + fileName + "' in '" + getPath() + "'");
            }
            result.add(f);
        }

        ExperimentService es = ExperimentService.get();
        if (_fileIds != null)
        {
            for (int fileId : _fileIds)
            {
                ExpData data = es.getExpData(fileId);
                if(data == null)
                {
                    throw new NotFoundException("Could not find file associated with Data Id: '" + fileId);
                }

                if (!data.getContainer().hasPermission(getUser(), ReadPermission.class))
                {
                    throw new NotFoundException("Insufficient permissions for file '" + data.getFile());
                }

                FileLike file = data.getFileLike();
                if (!allowNonExistentFiles && !NetworkDrive.exists(file))
                {
                    throw new NotFoundException("Could not find file '" + file + "'");
                }
                result.add(file);
            }
        }

        return result;
    }

    public List<Path> getValidatedPaths(Container c, boolean allowNonExistentFiles)
    {
        List<FileLike> files = getValidatedFiles(c, allowNonExistentFiles);
        List<Path> result = new ArrayList<>();
        for (FileLike file : files)
        {
            result.add(file.toNioPathForRead());
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
    public FileLike getValidatedSingleFile(Container c)
    {
        List<FileLike> files = getValidatedFiles(c);
        if (files.size() != 1)
        {
            throw new IllegalArgumentException("Expected a single file but got " + files.size());
        }
        return files.getFirst();
    }

    /** Verifies that only a single file was selected and returns it, throwing an exception if there isn't exactly one */
    @Deprecated // use the FileLike version
    public Path getValidatedSinglePath(Container c)
    {
        List<Path> files = getValidatedPaths(c, false);
        if (files.size() != 1)
        {
            throw new IllegalArgumentException("Expected a single file but got " + files.size());
        }
        return files.getFirst();
    }
}
