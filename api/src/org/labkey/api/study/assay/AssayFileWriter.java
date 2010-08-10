/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.data.Container;
import org.labkey.api.view.ViewContext;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.*;
import java.util.*;

/**
 * User: jeckels
 * Date: Sep 21, 2007
 */
public class AssayFileWriter
{
    public static final String DIR_NAME = "assaydata";

    public static File ensureUploadDirectory(Container container) throws ExperimentException
    {
        PipeRoot root = getPipelineRoot(container);

        File dir = root.resolvePath(DIR_NAME);
        if (!dir.exists()) {
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
            if (!Character.isLetterOrDigit(character))
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

    protected void writeFile(InputStream in, File file)
            throws IOException
    {
        FileOutputStream out = null;
        try
        {
            out = new FileOutputStream(file);
            int count;
            byte[] b = new byte[4096];
            while ((count = in.read(b)) > 0)
                out.write(b, 0, count);
        }
        finally
        {
            if (null != out)
                try { out.close(); } catch (Exception x) { /* fall through */ }
        }
    }

    @NotNull
    protected static PipeRoot getPipelineRoot(Container container)
    {
        PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(container);
        if (null == pipelineRoot || !pipelineRoot.isValid())
            throw new IllegalStateException("Please have your administrator set up a pipeline root for this folder.");
        return pipelineRoot;
    }

    public File findUniqueFileName(String originalFilename, File dir)
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

    public static interface PostedFileSaveFilter
    {
        boolean saveFile(String parameterName);
    }

    public Map<String, File> savePostedFiles(AssayRunUploadContext context, final Set<String> parameterNames) throws ExperimentException, IOException
    {
        return savePostedFiles(context, new PostedFileSaveFilter()
        {
            @Override
            public boolean saveFile(String parameterName)
            {
                return parameterNames.contains(parameterName);
            }
        });
    }
    
    public Map<String, File> savePostedFiles(AssayRunUploadContext context, PostedFileSaveFilter filter) throws ExperimentException, IOException
    {
        Map<String, File> files = new HashMap<String, File>();
        if (context.getRequest() instanceof MultipartHttpServletRequest)
        {
            MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest)context.getRequest();
            Iterator<Map.Entry<String, MultipartFile>> iter = multipartRequest.getFileMap().entrySet().iterator();
            File dir = ensureUploadDirectory(context.getContainer());
            while (iter.hasNext())
            {
                Map.Entry<String, MultipartFile> entry = iter.next();
                if (filter == null || filter.saveFile(entry.getKey()))
                {
                    MultipartFile multipartFile = entry.getValue();
                    String fileName = multipartFile.getOriginalFilename();
                    if (!multipartFile.isEmpty())
                    {
                        File file = findUniqueFileName(fileName, dir);
                        writeFile(multipartFile.getInputStream(), file);
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
            writeFile(new FileInputStream(file), newFile);
            return newFile;
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }
}
