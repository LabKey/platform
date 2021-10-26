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
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.ViewContext;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * User: jeckels
 * Date: Sep 21, 2007
 */
public class AssayFileWriter<ContextType extends AssayRunUploadContext<? extends AssayProvider>>
{
    private static final Logger LOG = LogManager.getLogger(AssayFileWriter.class);

    public static final String DIR_NAME = "assaydata";
    public static final String ARCHIVED_DIR_NAME = "archived";
    public static final String TEMP_DIR_NAME = "uploadTemp";

    /** Make sure there's an assaydata subdirectory available for this container */
    public static File ensureUploadDirectory(Container container) throws ExperimentException
    {
        return ensureUploadDirectory(container, DIR_NAME);
    }

    public static File ensureSubdirectory(Container container, String subName) throws ExperimentException
    {
        File uploadDir = ensureUploadDirectory(container);
        File subDir = new File(uploadDir, subName);
        if (!NetworkDrive.exists(subDir))
        {
            boolean success = subDir.mkdir();
            if (!success) throw new ExperimentException("Could not create directory: " + subDir);
        }
        return subDir;
    }

    public static File ensureUploadDirectory(Container container, String dirName) throws ExperimentException
    {
        Path path = ensureUploadDirectoryPath(container, dirName);
        if (null != path && !FileUtil.hasCloudScheme(path))
            return path.toFile();
        return null;
    }

    /** Make sure there's a subdirectory of the specified name available for this container */
    public static Path ensureUploadDirectoryPath(Container container, String dirName) throws ExperimentException
    {
        if (dirName == null)
        {
            dirName = "";
        }

        PipeRoot root = getPipelineRoot(container);

        Path dir = root.resolveToNioPath(dirName);
        if (null != dir && !Files.exists(dir))
        {
            try
            {
                dir = Files.createDirectory(dir);
            }
            catch (IOException e)
            {
                throw new ExperimentException("Could not create directory: " + dir);
            }
        }
        return dir;
    }

    /**
     * Create file name based upon the assay protocol's name and the current time.
     * e.g., <code>assayname-2020-04-14-1602345</code>
     */
    public static File createFile(ExpProtocol protocol, File dir, String extension)
    {
        Date dateCreated = new Date();
        String dateString = DateUtil.formatDateTime(dateCreated, "yyy-MM-dd-HHmmss");
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

        File file;

        do
        {
            String extra = id++ == 0 ? "" : String.valueOf(id);
            String fileName = protocolName + "-" + dateString + extra + "." + extension;
            fileName = fileName.replace('\\', '_').replace('/','_').replace(':','_');
            file = new File(dir, fileName);
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

    @Deprecated //Prefer the Path version
    public static File findUniqueFileName(String originalFilename, File dir)
    {
        return findUniqueFileName(originalFilename, dir.toPath()).toFile();
    }

    public static Path findUniqueFileName(String originalFilename, Path dir)
    {
        if (originalFilename == null || "".equals(originalFilename))
        {
            originalFilename = "[unnamed]";
        }
        Path file;
        int uniquifier = 0;
        do
        {
            String prefix;
            String suffix;

            int index = originalFilename.indexOf('.');
            if (index != -1)
            {
                prefix = originalFilename.substring(0, index);
                suffix = originalFilename.substring(index);
            }
            else
            {
                prefix = originalFilename;
                suffix = "";
            }
            String fullName = prefix + (uniquifier == 0 ? "" : "-" + uniquifier) + suffix;
            file = dir.resolve(fullName);
            uniquifier++;
        }
        while (Files.exists(file));

        return file;
    }

    protected File getFileTargetDir(ContextType context) throws ExperimentException
    {
        return ensureUploadDirectory(context.getContainer());
    }

    public Map<String, File> savePipelineFiles(ContextType context, Map<String, File> files) throws ExperimentException, IOException
    {
        Map<String, File> savedFiles = new TreeMap<>();
        if (context.getRequest() instanceof MultipartHttpServletRequest)
        {
            PipeRoot root = getPipelineRoot(context.getContainer());
            File dir = getFileTargetDir(context);

            for (String key : files.keySet())
            {
                // Copy the user uploaded files not already under the pipeline root to the temp directory.
                File file = files.get(key);
                if (!root.isUnderRoot(file))
                {
                    File savedFile = new File(dir, file.getName());
                    LOG.debug("savePipelineFiles: file '" + file.getPath() + "' is not under pipeline root. copying to savedFile=" + savedFile.getPath());
                    FileUtils.copyFile(file, savedFile);
                    file = savedFile;
                }
                else
                {
                    LOG.debug("savePipelineFiles: file '" + file.getPath() + "' is already under pipeline root. not copying");
                }
                savedFiles.put(key, file);
            }
        }
        else
        {
            savedFiles = files;
        }

        return savedFiles;
    }

    public Map<String, File> savePostedFiles(ContextType context, Set<String> parameterNames) throws ExperimentException, IOException
    {
        Map<String, File> files = new TreeMap<>();
        Set<String> originalFileNames = new HashSet<>();
        if (context.getRequest() instanceof MultipartHttpServletRequest)
        {
            MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest)context.getRequest();
            Iterator<Map.Entry<String, List<MultipartFile>>> iter = multipartRequest.getMultiFileMap().entrySet().iterator();
            File dir = getFileTargetDir(context);
            Deque<File> overflowFiles = new ArrayDeque<>();  // using a deque for easy removal of single elements
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
                        String fileName = multipartFile.getOriginalFilename();
                        if (!fileName.isEmpty() && !originalFileNames.add(fileName))
                        {
                            throw new ExperimentException("The file '" + fileName + " ' was uploaded twice - all files must be unique");
                        }
                        if (!multipartFile.isEmpty())
                        {
                            File file = findUniqueFileName(fileName, dir);
                            multipartFile.transferTo(file);
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
                        }
                    }
                }
            }
            // now process overflow files, if any
            for (String unusedParameterName : unusedParameterNames)
            {
                if (overflowFiles.size() < 1)
                    break;  // we're done
                else
                {
                    files.put(unusedParameterName, overflowFiles.remove());
                }
            }

            if (overflowFiles.size() > 0)  // too many files; shouldn't happen, but if it does, throw an error
                throw new ExperimentException("Tried to save too many files: number of keys is " + parameterNames.size() +
                        ", but " + overflowFiles.size() + " extra file(s) were found.");
        }
        return files;
    }

    public File safeDuplicate(ViewContext context, File file) throws ExperimentException
    {
        File dir = ensureUploadDirectory(context.getContainer());
        File newFile = findUniqueFileName(file.getName(), dir);
        try
        {
            FileUtils.copyFile(file, newFile);
            return newFile;
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }
}
