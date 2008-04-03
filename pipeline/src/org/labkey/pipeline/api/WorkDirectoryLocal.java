/*
 * Copyright (c) 2007 LabKey Software Foundation
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
package org.labkey.pipeline.api;

import org.apache.commons.io.FileUtils;
import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URIUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/**
 * <code>WorkDirectoryLocal</code>
 *
 * @author brendanx
 */
public class WorkDirectoryLocal implements WorkDirectory
{
    public static class Factory implements WorkDirFactory
    {
        public WorkDirectory createWorkDirectory(String jobId, FileAnalysisJobSupport support) throws IOException
        {
            return new WorkDirectoryLocal(support);
        }
    }

    private static final FileType FT_WORK_DIR = new FileType(".work");
    private static final FileType FT_COPY = new FileType(".copy");
    private static final FileType FT_MOVE = new FileType(".move");

    private FileAnalysisJobSupport _support;
    private File _dir;
    private HashMap<File, File> _copiedInputs = new HashMap<File, File>();

    public WorkDirectoryLocal(FileAnalysisJobSupport support) throws IOException
    {
        _support = support;

        _dir = FT_WORK_DIR.newFile(support.getAnalysisDirectory(),
                support.getBaseName());
        
        if (_dir.exists())
        {
            if (!FileUtil.deleteDirectoryContents(_dir))
                throw new IOException("Failed to clean up existing work directory " + _dir);
        }
        else
        {
            if (!_dir.mkdir())
                throw new IOException("Failed to create work directory " + _dir);
        }
    }

    public File getDir()
    {
        return _dir;
    }

    private File getDir(Function f, String name)
    {
        if (Function.output.equals(f))
        {
            // All output goes to the root work directory for now.
            return _dir;
        }
        else
        {
            File file = _support.findInputFile(name);
            return file.getParentFile();
        }
    }

    public File newFile(FileType type)
    {
        return newFile(Function.output, type);
    }

    public File newFile(Function f, FileType type)
    {
        return newFile(f, type.getName(_support.getBaseName()));
    }

    public File newFile(String name)
    {
        return newFile(Function.output, name);
    }

    public File newFile(Function f, String name)
    {
        File file = new File(getDir(f, name), name);
        
        if (Function.input.equals(f))
        {
            // See if the file has already been copied into the working directory.
            // In which case, the copied version should be used.
            File fileWork = _copiedInputs.get(file);
            if (fileWork != null)
                return fileWork;
        }

        return file;
    }

    public File inputFile(File fileInput) throws IOException
    {
        return inputFile(fileInput, false);
    }

    public File inputFile(File fileInput, boolean forceCopy) throws IOException
    {
        if (!forceCopy)
            return fileInput;

        File fileWork = newFile(fileInput.getName());
        FileUtils.copyFile(fileInput, fileWork);
        _copiedInputs.put(fileInput, fileWork);
        return fileWork;
    }

    public String getRelativePath(File fileWork) throws IOException
    {
        return FileUtil.relativize(_dir, fileWork);
    }

    public void outputFile(File fileWork) throws IOException
    {
        outputFile(fileWork, fileWork.getName());
    }

    public void outputFile(File fileWork, String nameDest) throws IOException
    {
        outputFile(fileWork, _support.findOutputFile(nameDest));
    }

    private void outputFile(File fileWork, File fileDest) throws IOException
    {
        ensureDescendent(fileWork);
        File fileReplace = null;
        if (fileDest.exists())
        {
            fileReplace = FT_MOVE.newFile(fileDest.getParentFile(), fileDest.getName());
            if (!fileDest.renameTo(fileReplace))
                throw new IOException("Failed to move file " + fileDest + " for replacement by " + fileWork);
        }
        if (!fileWork.renameTo(fileDest))
            throw new IOException("Failed to move file " + fileWork + " to " + fileDest);
        if (fileReplace != null)
            fileReplace.delete();
    }

    public void discardFile(File fileWork) throws IOException
    {
        ensureDescendent(fileWork);
        if (fileWork.exists() && !fileWork.delete())
            throw new IOException("Failed to remove file " + fileWork);
    }

    public void remove() throws IOException
    {
        // Delete the copied input files, and clear the map pointing to them.
        for (File input : _copiedInputs.values())
            discardFile(input);
        _copiedInputs.clear();
        
        if (!_dir.delete())
            throw new IOException("Failed to remove work directory " + _dir);
    }

    private void ensureDescendent(File fileWork) throws IOException
    {
        if (!URIUtil.isDescendent(_dir.toURI(), fileWork.toURI()))
            throw new IOException("The file " + fileWork + " is not a descendent of " + _dir);
    }
}
