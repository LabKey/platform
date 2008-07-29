/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URIUtil;
import org.labkey.api.util.NetworkDrive;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.HashMap;

/*
* User: jeckels
* Date: Jun 17, 2008
*/
public abstract class AbstractWorkDirectory implements WorkDirectory
{
    protected static final FileType FT_WORK_DIR = new FileType(".work");
    protected static final FileType FT_COPY = new FileType(".copy");
    protected static final FileType FT_MOVE = new FileType(".move");

    protected FileAnalysisJobSupport _support;
    protected File _dir;
    protected Logger _log;
    protected HashMap<File, File> _copiedInputs = new HashMap<File, File>();

    protected CopyingResource _copyingResource;

    public AbstractWorkDirectory(FileAnalysisJobSupport support, File dir, Logger log) throws IOException
    {
        _support = support;
        _dir = dir;
        _log = log;

        if (_dir.exists())
        {
            if (!FileUtil.deleteDirectoryContents(_dir))
                throw new IOException("Failed to clean up existing work directory " + _dir);
        }
        else
        {
            if (!_dir.mkdirs())
                throw new IOException("Failed to create work directory " + _dir);
        }
    }

    public File getDir()
    {
        return _dir;
    }

    private void copyFile(File source, File target) throws IOException
    {
        NetworkDrive.ensureDrive(source.getAbsolutePath());
        NetworkDrive.ensureDrive(target.getAbsolutePath());
        CopyingResource resource = null;
        try
        {
            resource = ensureCopyingLock();
            _log.info("Copying " + source + " to " + target);
            FileUtils.copyFile(source, target);
        }
        finally
        {
            if (resource != null)
            {
                resource.release();
            }
        }
    }

    protected File copyInputFile(File fileInput) throws IOException
    {
        File fileWork = newFile(fileInput.getName());
        copyFile(fileInput, fileWork);
        _copiedInputs.put(fileInput, fileWork);
        return fileWork;
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

    protected void outputFile(File fileWork, File fileDest) throws IOException
    {
        NetworkDrive.ensureDrive(fileDest.getAbsolutePath());
        if (!fileWork.exists())
        {
            // If the work file does not exist, and the destination does
            // assume the task wrote to the desired location.
            if (fileDest.exists())
                return;
            throw new FileNotFoundException("Failed to find expected output " + fileWork);
        }
        ensureDescendent(fileWork);
        File fileReplace = null;
        CopyingResource resource = null;
        try
        {
            resource = ensureCopyingLock();
            if (fileDest.exists())
            {
                fileReplace = FT_MOVE.newFile(fileDest.getParentFile(), fileDest.getName());
                _log.info("Moving " + fileDest + " to " + fileReplace);
                if (!fileDest.renameTo(fileReplace))
                {
                    throw new IOException("Failed to move file " + fileDest + " to " + fileReplace);
                }
            }
            _log.info("Moving " + fileWork + " to " + fileDest);
            if (!fileWork.renameTo(fileDest))
            {
                // File.renameTo() is the most efficient way to move a file, but it annoyingly doesn't necessarily
                // work across different file systems.
                FileUtils.copyFile(fileWork, fileDest);
                if (!fileWork.delete())
                {
                    throw new IOException("Failed to move file " + fileWork + " to " + fileDest);
                }
            }
            if (fileReplace != null)
                fileReplace.delete();
        }
        finally
        {
            if (resource != null)
            {
                resource.release();
            }
        }
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
        {
            StringBuffer message = new StringBuffer();
            message.append("Failed to remove work directory ").append(_dir);
            File[] files = _dir.listFiles();
            if (files.length > 0)
            {
                message.append(" unexpected files found:");
                for (File f : files)
                    message.append("\n").append(f.getName());
            }

            throw new IOException(message.toString());
        }
    }

    private void ensureDescendent(File fileWork) throws IOException
    {
        if (!URIUtil.isDescendent(_dir.toURI(), fileWork.toURI()))
            throw new IOException("The file " + fileWork + " is not a descendent of " + _dir);
    }

    /**
     * Ensures that we have a lock, if needed. The lock must be released by the caller.
     */
    public CopyingResource ensureCopyingLock() throws IOException
    {
        if (_copyingResource != null)
        {
            // Hand out a dummy. There's already a lock established, so rely on the place that created it to release it
            return new SimpleCopyingResource();
        }
        _copyingResource = createCopyingLock();
        return _copyingResource;
    }

    /**
     * Creates an actual lock resource. Used internally by the WorkDirectory - callers should use ensureCopyingLock() instead
     */
    protected abstract CopyingResource createCopyingLock() throws IOException;

    public class SimpleCopyingResource implements CopyingResource
    {
        public void release()
        {
            // If this is the real resource for the working directory, it can be released now
            if (_copyingResource == this)
            {
                _copyingResource = null;
            }
        }
    }
}