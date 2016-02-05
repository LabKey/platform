/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.ViewContext;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * User: jeckels
 * Date: Sep 21, 2007
 */
public class AssayFileWriter<ContextType extends AssayRunUploadContext<? extends AssayProvider>>
{
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

    /** Make sure there's a subdirectory of the specified name available for this container */
    public static File ensureUploadDirectory(Container container, String dirName) throws ExperimentException
    {
        if (dirName == null)
        {
            dirName = "";
        }

        PipeRoot root = getPipelineRoot(container);

        File dir = root.resolvePath(dirName);
        if (!NetworkDrive.exists(dir))
        {
            boolean success = dir.mkdir();
            if (!success) throw new ExperimentException("Could not create directory: " + dir);
        }
        return dir;
    }

    public static File createFile(ExpProtocol protocol, File dir, String extension)
    {
        //File name is studyname_datasetname_date_hhmm.ss
        Date dateCreated = new Date();
        String dateString = DateUtil.formatDateTime(dateCreated, "yyy-MM-dd-HHmm");
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

    public static File findUniqueFileName(String originalFilename, File dir)
    {
        if (originalFilename == null || "".equals(originalFilename))
        {
            originalFilename = "[unnamed]";
        }
        File file;
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
            file = new File(dir, fullName);
            uniquifier++;
        }
        while (file.exists());

        return file;
    }

    protected File getFileTargetDir(ContextType context) throws ExperimentException
    {
        return ensureUploadDirectory(context.getContainer());
    }

    public Map<String, File> savePostedFiles(ContextType context, Set<String> parameterNames) throws ExperimentException, IOException
    {
        Map<String, File> files = new TreeMap<>();
        Set<String> originalFileNames = new HashSet<>();
        if (context.getRequest() instanceof MultipartHttpServletRequest)
        {
            MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest)context.getRequest();
            Iterator<Map.Entry<String, MultipartFile>> iter = multipartRequest.getFileMap().entrySet().iterator();
            File dir = getFileTargetDir(context);
            while (iter.hasNext())
            {
                Map.Entry<String, MultipartFile> entry = iter.next();
                if (parameterNames == null || parameterNames.contains(entry.getKey()))
                {
                    MultipartFile multipartFile = entry.getValue();
                    String fileName = multipartFile.getOriginalFilename();
                    if (!fileName.isEmpty() && !originalFileNames.add(fileName))
                    {
                        throw new ExperimentException("The file '" + fileName + " ' was uploaded twice - all files must be unique");
                    }
                    if (!multipartFile.isEmpty())
                    {
                        File file = findUniqueFileName(fileName, dir);
                        multipartFile.transferTo(file);
                        files.put(multipartFile.getName(), file);
                    }
                }
            }
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
