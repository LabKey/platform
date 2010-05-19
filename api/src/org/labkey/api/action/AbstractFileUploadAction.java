/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
package org.labkey.api.action;

import org.labkey.api.util.PageFlowUtil;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartFile;
import org.labkey.api.util.ExceptionUtil;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.Iterator;

/**
 * Base class for actions that just want to accept a file by HTTP upload.
 * Writes error messages directly back to the stream as text
 * User: jeckels
 * Date: Jan 19, 2009
 */
public abstract class AbstractFileUploadAction<FORM> extends ExportAction<FORM>
{
    public void export(FORM form, HttpServletResponse response, BindException errors) throws Exception
    {
        response.reset();
        response.setContentType("text/html");

        OutputStream out = response.getOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(out);

        HttpServletRequest basicRequest = getViewContext().getRequest();
        String filename;
        InputStream input = null;

        if (basicRequest instanceof MultipartHttpServletRequest)
        {
            MultipartHttpServletRequest request = (MultipartHttpServletRequest)basicRequest;

            //noinspection unchecked
            Iterator<String> nameIterator = request.getFileNames();
            String formElementName = nameIterator.next();
            MultipartFile file = request.getFile(formElementName);
            filename = file.getOriginalFilename();
            input = file.getInputStream();
        }
        else
        {
            filename = basicRequest.getParameter("fileName");
            String content = basicRequest.getParameter("fileContent");
            if (content != null)
            {
                input = new ByteArrayInputStream(content.getBytes());
            }
        }

        if (filename == null || input == null)
        {
            error(writer, "No file uploaded, or no filename specified", HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        File targetFile;
        try
        {
            targetFile = getTargetFile(filename);

            OutputStream output = new FileOutputStream(targetFile);
            try
            {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = input.read(buffer)) > 0)
                    output.write(buffer, 0, len);

                output.flush();
                output.close();
                input.close();

            }
            catch (IOException ioe)
            {
                ExceptionUtil.logExceptionToMothership(basicRequest, ioe);
                error(writer, ioe.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }

            String message = handleFile(targetFile, filename);
            writer.write(message);
        }
        catch (UploadException e)
        {
            error(writer, e.getMessage(), e.getStatusCode());
            return;
        }


        writer.flush();
        writer.close();
    }

    public static class UploadException extends IOException
    {
        private int _statusCode;

        public UploadException(String message, int statusCode)
        {
            super(message);
            _statusCode = statusCode;
        }

        public int getStatusCode()
        {
            return _statusCode;
        }
    }

    /** Figures out where to write the uploaded file */
    protected abstract File getTargetFile(String filename) throws IOException;

    /**
     * Callback once the file has been written to the server's file system.
     * @return a meaningful handle that the client can use to refer to the file
     */
    protected abstract String handleFile(File file, String originalName) throws UploadException;

    private void error(Writer writer, String message, int statusCode) throws IOException
    {
        getViewContext().getResponse().setStatus(statusCode);
        writer.write(PageFlowUtil.jsString(message));
        writer.flush();
        writer.close();
    }
}