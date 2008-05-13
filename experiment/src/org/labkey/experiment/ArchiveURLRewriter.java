/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
import org.labkey.experiment.api.ExperimentRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Jul 28, 2006
 */
public class ArchiveURLRewriter extends URLRewriter
{
    public String rewriteURL(File f, ExpData data, ExperimentRun run) throws ExperimentException
    {
        String directoryName;
        File rootDir;
        FileInfo existingInfo = _files.get(f);
        if (existingInfo != null)
        {
            return existingInfo.getName();
        }
        if (run == null)
        {
            directoryName = "Other";
            rootDir = null;
        }
        else
        {
            directoryName = "Run" + run.getRowId();
            rootDir = new File(run.getFilePathRoot());
        }
        return addFile(ExperimentService.get().getExpData(data.getRowId()), f, directoryName, rootDir, data.findDataHandler());
    }

    private String addFile(ExpData data, File f, String directoryName, File rootDir, ExperimentDataHandler dataHandler)
            throws ExperimentException
    {
        String name;
        try
        {
            f = f.getCanonicalFile();
            boolean inSubTree = false;
            File fileParentDir = f.getParentFile();
            while (fileParentDir != null)
            {
                if (fileParentDir.equals(rootDir))
                {
                    inSubTree = true;
                    break;
                }
                fileParentDir = fileParentDir.getParentFile();
            }

            if (inSubTree)
            {
                name = FileUtil.relativizeUnix(rootDir, f);
            }
            else
            {
                name = f.getName();
            }

            if (!_files.containsKey(f))
            {
                if (name != null)
                {
                    name = uniquifyFileName(name, directoryName, null);
                }
                if (f.exists())
                {
                    _files.put(f, new FileInfo(data, f, name, dataHandler));
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
        String name = directoryName + "/" + originalName;
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
