/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.assay;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CollectionUtils;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewContext;

import org.labkey.vfs.FileLike;

import org.labkey.vfs.FileSystemLike;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.labkey.api.util.FileUtil.toFileForRead;
import static org.labkey.api.util.FileUtil.toFileForWrite;


public class AssayFileWriter<ContextType extends AssayRunUploadContext<? extends AssayProvider>>
{
    private static final Logger LOG = LogManager.getLogger(AssayFileWriter.class);

    public static final String DIR_NAME = "assaydata";
    public static final String ARCHIVED_DIR_NAME = "archived";
    public static final String TEMP_DIR_NAME = ".uploadTemp";

    /** Make sure there's an assaydata subdirectory available for this container */
    public static FileLike ensureUploadDirectory(Container container) throws ExperimentException
    {
        return ensureUploadDirectory(container, DIR_NAME);
    }

    public static FileLike ensureSubdirectory(Container container, String subName) throws ExperimentException
    {
        FileLike subDir=null;
        try
        {
            FileLike uploadDir = ensureUploadDirectory(container);
            subDir = uploadDir.resolveChild(subName);
            if (!NetworkDrive.exists(subDir))
            {
                    boolean success = FileUtil.mkdir(subDir);
                    if (!success)
                        throw new IOException();
            }
            return subDir;
        }
        catch (IOException e)
        {
            throw new ExperimentException("Could not create directory: " + subDir);
        }
    }

    public static FileLike getUploadDirectoryPath(Container container, String dirName)
    {
        if (dirName == null)
            dirName = "";
        PipeRoot root = getPipelineRoot(container);
        FileLike fileObject = root.getRootFileLike();
        return fileObject.resolveChild(dirName);
    }

    public static FileLike ensureUploadDirectory(Container container, String dirName) throws ExperimentException
    {
        FileLike dir = getUploadDirectoryPath(container, dirName);
        ensureUploadDirectory(dir);
        return dir;
    }

    public static FileLike ensureUploadDirectory(FileLike dir) throws ExperimentException
    {
        FileLike fileObject = ensureUploadDirectoryPath(dir);
        if (null != fileObject)
        {
            if (!FileUtil.hasCloudScheme(fileObject.getFileSystem().getURI()))
                return fileObject;
            else
                throw new ExperimentException("Operation not supported for cloud-based file storage.");
        }

        return null;
    }

    /** Make sure there's a subdirectory of the specified name available for this container */
    public static FileLike ensureUploadDirectoryPath(FileLike dir) throws ExperimentException
    {
        try
        {
            if (null != dir && !dir.isDirectory())
                FileUtil.createDirectories(dir);
            return dir;
        }
        catch (IOException e)
        {
            throw new ExperimentException("Could not create directory: " + dir);
        }
    }

    /**
     * Create file name based upon the assay protocol's name and the current time.
     * e.g., <code>assayname-2020-04-14-1602345</code>
     */
    public static FileLike createFile(ExpProtocol protocol, FileLike dir, String extension)
    {
        Date dateCreated = new Date();
        String dateString = DateUtil.formatDateTime(dateCreated, "yyy-MM-dd-HHmmss-SSS");
        int id = 0;

        String protocolName = protocol.getName();
        char[] characters = protocolName.toCharArray();

        for (int i = 0; i < characters.length; i++)
        {
            char character = characters[i];
            boolean isAtoZchar = character >= 'A' && character <= 'z';
            if (!Character.isDigit(character) && !isAtoZchar)
                characters[i] = '_';
        }
        protocolName = new String(characters);

        FileLike file;
        do
        {
            String extra = id++ == 0 ? "" : String.valueOf(id);
            String fileName = protocolName + "-" + dateString + extra + "." + extension;
            fileName = fileName.replace('\\', '_').replace('/', '_').replace(':', '_');
            file = dir.resolveChild(fileName);
        }
        while (file.exists());
        return file;
    }

    protected void writeFile(InputStream in, File file) throws IOException
    {
        FileUtils.copyInputStreamToFile(in, file);
    }

    @NotNull
    protected static PipeRoot getPipelineRoot(Container container)
    {
        PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(container);
        if (null == pipelineRoot || !pipelineRoot.isValid())
            throw new IllegalStateException("Please have your administrator set up a pipeline root for this folder.");
        return pipelineRoot;
    }

    public static File findUniqueFileName(String originalFilename, File dir)
    {
        if (originalFilename == null || originalFilename.isEmpty())
        {
            originalFilename = "[unnamed]";
        }
        File file;
        int uniquifier = 0;
        do
        {
            String fullName = getAppendedFileName(originalFilename, uniquifier);
            file = FileUtil.appendName(dir, fullName);
            uniquifier++;
        }
        while (file.exists());
        return file;
    }

    public static FileLike findUniqueFileName(String originalFilename, FileLike dir)
    {
        if (originalFilename == null || originalFilename.isEmpty())
        {
            originalFilename = "[unnamed]";
        }
        FileLike file;
        int uniquifier = 0;
        do
        {
            String fullName = getAppendedFileName(originalFilename, uniquifier);
            file = dir.resolveChild(fullName);
            uniquifier++;
        }
        while (file.exists());
        return file;
    }

    public static String getAppendedFileName(String originalFilename, int uniquifier)
    {
        String prefix = originalFilename;
        String suffix = "";

        int index = originalFilename.indexOf('.');
        if (index != -1)
        {
            prefix = originalFilename.substring(0, index);
            suffix = originalFilename.substring(index);
        }

        return prefix + (uniquifier == 0 ? "" : "-" + uniquifier) + suffix;
    }

    protected FileLike getFileTargetDir(ContextType context) throws ExperimentException
    {
        return ensureUploadDirectory(context.getContainer());
    }

    /* TODO: this is a really awkward transition between File->FileLike (files come from FileQueue) */
    public Map<String, FileLike> savePipelineFiles(ContextType context, Map<String, File> files) throws ExperimentException, IOException
    {
        Map<String, FileLike> savedFiles = CollectionUtils.enforceValueClass(new TreeMap<>(), FileLike.class);
        if (context.getRequest() instanceof MultipartHttpServletRequest)
        {
            PipeRoot root = getPipelineRoot(context.getContainer());
            FileLike dir = getFileTargetDir(context);

            for (String key : files.keySet())
            {
                // Copy the user uploaded files not already under the pipeline root to the temp directory.
                File file = files.get(key);
                if (!root.isUnderRoot(file))
                {
                    FileLike savedFile = dir.resolveChild(file.getName());
                    LOG.debug("savePipelineFiles: file '" + file + "' is not under pipeline root. copying to savedFile=" + savedFile);
                    FileUtils.copyFile(file, toFileForWrite(savedFile));
                    savedFiles.put(key, savedFile);
                }
                else
                {
                    savedFiles.put(key, FileSystemLike.wrapFile(file));
                    LOG.debug("savePipelineFiles: file '" + file.getPath() + "' is already under pipeline root. not copying");
                }
            }
        }
        else
        {
            for (var entry : files.entrySet())
            {
                savedFiles.put(entry.getKey(), FileSystemLike.wrapFile((entry.getValue())));
            }
        }

        return savedFiles;
    }

    public String getFileName(MultipartFile file)
    {
        return file.getOriginalFilename();
    }

    public Map<String, FileLike> savePostedFiles(ContextType context, Set<String> parameterNames, boolean allowMultiple, boolean ensureExpData) throws ExperimentException, IOException
    {
        Map<String, FileLike> files = CollectionUtils.enforceValueClass(new TreeMap<>(), FileLike.class);
        Set<String> originalFileNames = new HashSet<>();
        if (context.getRequest() instanceof MultipartHttpServletRequest multipartRequest)
        {
            Iterator<Map.Entry<String, List<MultipartFile>>> iter = multipartRequest.getMultiFileMap().entrySet().iterator();
            Deque<FileLike> overflowFiles = new ArrayDeque<>();  // using a deque for easy removal of single elements
            Set<String> unusedParameterNames = new HashSet<>(parameterNames);
            while (iter.hasNext())
            {
                Map.Entry<String, List<MultipartFile>> entry = iter.next();
                if (parameterNames == null || parameterNames.contains(entry.getKey()))
                {
                    List<MultipartFile> multipartFiles = entry.getValue();
                    boolean isAfterFirstFile = false;
                    for (MultipartFile multipartFile : multipartFiles)
                    {
                        String fileName = getFileName(multipartFile);
                        if (!fileName.isEmpty() && !originalFileNames.add(fileName))
                        {
                            throw new ExperimentException("The file '" + fileName + " ' was uploaded twice - all files must be unique");
                        }
                        if (!multipartFile.isEmpty())
                        {
                            FileLike dir = getFileTargetDir(context);
                            FileLike file = findUniqueFileName(fileName, dir);
                            multipartFile.transferTo(toFileForWrite(file));
                            if (!isAfterFirstFile)  // first file gets stored with multipartFile's name
                            {
                                files.put(multipartFile.getName(), file);
                                isAfterFirstFile = true;
                                unusedParameterNames.remove(multipartFile.getName());
                            }
                            else  // other files get stored in leftover keys later to store only one file per key (bit of a hack)
                            {
                                overflowFiles.add(file);
                            }

                            if (ensureExpData)
                                AbstractQueryUpdateService.ensureExpData(context.getUser(), context.getContainer(), toFileForWrite(file));
                        }
                    }
                }
            }
            // now process overflow files, if any
            for (String unusedParameterName : unusedParameterNames)
            {
                if (overflowFiles.isEmpty())
                    break;  // we're done
                else
                {
                    files.put(unusedParameterName, overflowFiles.remove());
                }
            }

            if (!overflowFiles.isEmpty() && !allowMultiple)  // too many files; shouldn't happen, but if it does, throw an error
                throw new ExperimentException("Tried to save too many files: number of keys is " + parameterNames.size() +
                        ", but " + overflowFiles.size() + " extra file(s) were found.");
        }
        return files;
    }

    public FileLike safeDuplicate(ViewContext context, FileLike file) throws ExperimentException
    {
        FileLike dir = ensureUploadDirectory(context.getContainer());
        FileLike newFile = findUniqueFileName(file.getName(), dir);
        try
        {
            FileUtils.copyFile(toFileForRead(file), newFile.openOutputStream());
            return newFile;
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testGetAppendedFileName()
        {
            String originalFilename = "test.txt";
            assertEquals("test.txt", getAppendedFileName(originalFilename, 0));
            assertEquals("test-1.txt", getAppendedFileName(originalFilename, 1));
            assertEquals("test-2.txt", getAppendedFileName(originalFilename, 2));
        }
    }
}
