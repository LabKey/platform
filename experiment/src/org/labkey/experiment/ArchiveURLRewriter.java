/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

package org.labkey.experiment;

import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.collections.CaseInsensitiveHashSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Transforms file URLs from how they are stored in the exp.data (a full path on the server's file system, typically)
 * to a relative, unique path within a XAR export.
 * User: jeckels
 * Date: Jul 28, 2006
 */
public class ArchiveURLRewriter extends URLRewriter
{
    private final Set<String> _roles;

    public ArchiveURLRewriter(boolean includeXarXml, List<String> roles)
    {
        super(includeXarXml);
        _roles = roles == null ? null : new CaseInsensitiveHashSet(roles);
    }

    public String rewriteURL(Path path, ExpData data, String roleName, ExpRun run, User user) throws ExperimentException
    {
        if (path != null && (_roles == null || _roles.contains(roleName)))
        {
            FileInfo existingInfo = _files.get(path);
            if (existingInfo != null)
            {
                return existingInfo.getName();
            }
            Path rootDir = null;
            if (run != null)
            {
                rootDir = run.getFilePathRootPath();
            }
            return addFile(ExperimentService.get().getExpData(data.getRowId()), path, getDirectoryName(run), rootDir, data.findDataHandler(), user);
        }
        return null;
    }

    protected String getDirectoryName(ExpRun run)
    {
        if (!isIncludeXarXml())
        {
            return null;
        }
        if (run == null)
        {
            return "Other";
        }
        return "Run" + run.getRowId();
    }

    public String addFile(ExpData data, Path path, String directoryName, Path rootDir, ExperimentDataHandler dataHandler, User user)
            throws ExperimentException
    {
        String name;
        try
        {
            path = FileUtil.getAbsoluteCaseSensitivePath(data.getContainer(), path.toUri());
            boolean inSubTree = false;
            Path fileParentDir = path.getParent();
            while (fileParentDir != null)
            {
                if (fileParentDir.equals(rootDir))
                {
                    inSubTree = true;
                    break;
                }
                fileParentDir = fileParentDir.getParent();
            }

            if (inSubTree)
            {
                name = dataHandler.getFileName(data, FileUtil.relativizeUnix(rootDir, path, true));
            }
            else
            {
                name = dataHandler.getFileName(data, FileUtil.getFileName(path));
            }

            if (!_files.containsKey(path))
            {
                if (name != null)
                {
                    name = uniquifyFileName(name, directoryName, null);
                }
                if (Files.exists(path) || (data.isFinalRunOutput()))
                {
                    _files.put(path, new FileInfo(data, path, name, dataHandler, user));
                }
            }
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
        return name;
    }

    private String uniquifyFileName(String originalName, String directoryName, Integer copy)
    {
        String name;
        if (directoryName == null || directoryName.equals(""))
        {
            name = originalName;
        }
        else
        {
            name = directoryName + "/" + originalName;
        }
        
        String prefix;
        String suffix;
        int index = name.indexOf('.');
        if (index != -1)
        {
            prefix = name.substring(0, index);
            suffix = name.substring(index);
        }
        else
        {
            prefix = name;
            suffix = "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        if (copy != null)
        {
            sb.append("-");
            sb.append(copy.toString());
        }
        sb.append(suffix);

        String newName = sb.toString();
        if (!isFileNameTaken(newName))
        {
            return newName;
        }
        else
        {
            Integer newCopy = copy == null ? new Integer(1) : new Integer(copy.intValue() + 1);
            return uniquifyFileName(originalName, directoryName, newCopy);
        }
    }

    private boolean isFileNameTaken(String name)
    {
        for (FileInfo info : _files.values())
        {
            if (info.getName().equals(name))
            {
                return true;
            }
        }
        return false;
    }
}
