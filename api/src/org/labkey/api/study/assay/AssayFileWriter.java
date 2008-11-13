/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.util.DateUtil;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.*;

/**
 * User: jeckels
 * Date: Sep 21, 2007
 */
public class AssayFileWriter
{
    protected File ensureUploadDirectory(ExpProtocol protocol, AssayRunUploadContext context) throws ExperimentException
    {
        File rootFile = getPipelineRoot(context).getRootPath();

        if (!rootFile.exists())
            throw new ExperimentException("Pipeline directory: " + rootFile + " does not exist. Please see your administrator.");

        File dir = new File(rootFile, TextAreaDataCollector.DIR_NAME);
        if (!dir.exists()) {
            boolean success = dir.mkdir();
            if (!success) throw new ExperimentException("Could not create directory: " + dir);
        }
        return dir;
    }

    protected File createFile(ExpProtocol protocol, File dir, String extension)
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

    protected PipeRoot getPipelineRoot(AssayRunUploadContext context)
    {
        PipeRoot pipelineRoot;
            pipelineRoot = PipelineService.get().findPipelineRoot(context.getContainer());
            if (null == pipelineRoot)
                throw new IllegalStateException("Please have your administrator set up a pipeline root for this folder.");
        return pipelineRoot;
    }

    protected File findUniqueFileName(String originalFilename, File dir)
    {
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
    
    public Map<String, File> savePostedFiles(AssayRunUploadContext context, Set<String> parameterNames) throws ExperimentException, IOException
    {
        Map<String, File> files = new HashMap<String, File>();
        if (context.getRequest() instanceof MultipartHttpServletRequest)
        {
            ExpProtocol protocol = context.getProtocol();
            MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest)context.getRequest();
            Iterator<Map.Entry<String, MultipartFile>> iter = multipartRequest.getFileMap().entrySet().iterator();
            File dir = ensureUploadDirectory(protocol, context);
            while (iter.hasNext())
            {
                Map.Entry<String, MultipartFile> entry = iter.next();
                if (parameterNames == null || parameterNames.contains(entry.getKey()))
                {
                    MultipartFile multipartFile = entry.getValue();
                    String fileName = multipartFile.getOriginalFilename();
                    if (fileName.equals(""))
                        fileName = "[unnamed]";
                    if (!multipartFile.isEmpty())
                    {
                        File file = findUniqueFileName(fileName, dir);
                        writeFile(multipartFile.getInputStream(), file);
                        files.put(entry.getKey(), file);
                    }
                }
            }
        }
        return files;
    }
}
