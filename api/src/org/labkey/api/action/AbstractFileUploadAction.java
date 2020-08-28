/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.NavTree;
import org.labkey.api.writer.PrintWriters;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Base class for actions that want to accept a file by HTTP upload.
 * Writes error messages directly back to the stream as plain text
 * User: jeckels
 * Date: Jan 19, 2009
 */
public abstract class AbstractFileUploadAction<FORM extends AbstractFileUploadAction.FileUploadForm> extends FormViewAction<FORM>
{
    private ModelAndView _successView = null;

    public static class FileUploadForm
    {
        private String[] _fileName = new String[0];
        private String[] _fileContent  = new String[0];
        /** If false, only send back the multiple result format if multiple files were uploaded */
        private boolean _forceMultipleResults;

        public String[] getFileName()
        {
            return _fileName;
        }

        @SuppressWarnings("unused")
        public void setFileName(String[] fileName)
        {
            _fileName = fileName;
        }

        public String[] getFileContent()
        {
            return _fileContent;
        }

        @SuppressWarnings("unused")
        public void setFileContent(String[] fileContent)
        {
            _fileContent = fileContent;
        }

        public boolean isForceMultipleResults()
        {
            return _forceMultipleResults;
        }

        @SuppressWarnings("unused")
        public void setForceMultipleResults(boolean forceMultipleResults)
        {
            _forceMultipleResults = forceMultipleResults;
        }
    }

    @Override
    public ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception
    {
        return null; // handlePost() sends the response... null view here to suppress all further rendering
    }

    @Override
    public void addNavTrail(NavTree root)
    {
        throw new IllegalStateException("Shouldn't be called");
    }

    @Override
    public void validateCommand(FORM target, Errors errors)
    {
    }

    @Override
    public boolean handlePost(FORM form, BindException errors) throws Exception
    {
        try
        {
            export(form, getViewContext().getResponse());
        }
        catch (ExportException e)
        {
            _successView = e.getErrorView();
        }

        return true;
    }

    @Override
    public URLHelper getSuccessURL(FORM form)
    {
        return null;
    }

    @Override
    public ModelAndView getSuccessView(FORM form)
    {
        return _successView;
    }

    @Override
    protected String getCommandClassMethodName()
    {
        return "getResponse";
    }

    protected void setContentType(HttpServletResponse response)
    {
        response.setContentType("text/html");
    }

    private void export(FORM form, HttpServletResponse response) throws Exception
    {
        response.reset();
        setContentType(response);

        try (OutputStream out = response.getOutputStream(); PrintWriter writer = PrintWriters.getPrintWriter(out))
        {
            if (form.getFileName() == null)
            {
                error(writer, "No fileName parameter values included", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            if (form.getFileContent() == null)
            {
                error(writer, "No fileContent parameter values included", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            if (form.getFileName().length != form.getFileContent().length)
            {
                error(writer, "Must include the same number of fileName and fileContent parameter values", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            HttpServletRequest basicRequest = getViewContext().getRequest();

            // Parameter name (String) -> File on disk/original file name Pair
            Map<String, Pair<File, String>> savedFiles = new HashMap<>();

            if (basicRequest instanceof MultipartHttpServletRequest)
            {
                MultipartHttpServletRequest request = (MultipartHttpServletRequest) basicRequest;

                Iterator<String> nameIterator = request.getFileNames();
                while (nameIterator.hasNext())
                {
                    String formElementName = nameIterator.next();
                    MultipartFile file = request.getFile(formElementName);
                    String filename = file.getOriginalFilename();

                    try (InputStream input = file.getInputStream())
                    {
                        if (!file.isEmpty())
                        {
                            File f = handleFile(filename, input, writer);
                            if (f == null)
                            {
                                return;
                            }
                            savedFiles.put(formElementName, new Pair<>(f, filename));
                        }
                    }
                }
            }

            for (int i = 0; i < form.getFileName().length; i++)
            {
                String filename = form.getFileName()[i];
                String content = form.getFileContent()[i];
                if (content != null)
                {
                    File f = handleFile(filename, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), writer);
                    if (f != null)
                    {
                        savedFiles.put("FileContent" + (i == 0 ? "" : (i + 1)), new Pair<>(f, filename));
                    }
                }
            }

            try
            {
                writer.write(getResponse(form, savedFiles));
                writer.flush();
            }
            catch (UploadException e)
            {
                error(writer, "Must include the same number of fileName and fileContent parameter values", HttpServletResponse.SC_BAD_REQUEST);
            }
        }
    }

    protected File handleFile(String filename, InputStream input, Writer writer) throws IOException
    {
        if (filename == null || input == null)
        {
            error(writer, "No file uploaded, or no filename specified", HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        // Issue 12845: clean the upload filename before trying to create the file
        String legalName = FileUtil.makeLegalName(filename);
        try
        {
            File targetFile = getTargetFile(legalName);

            try (OutputStream output = new FileOutputStream(targetFile))
            {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = input.read(buffer)) > 0)
                    output.write(buffer, 0, len);

                output.flush();
                input.close();
                return targetFile;

            }
            catch (IOException ioe)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), ioe);
                error(writer, ioe.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return null;
            }
        }
        catch (UploadException e)
        {
            error(writer, e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }
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
     * @param files HTTP parameter name -> [File as saved on disk (potentially renamed to be unique, Original file name in POST]
     * @return a meaningful handle that the client can use to refer to the file
     */
    public abstract String getResponse(FORM form, Map<String, Pair<File, String>> files) throws UploadException;

    private void error(Writer writer, String message, int statusCode) throws IOException
    {
        getViewContext().getResponse().reset();
        getViewContext().getResponse().setContentType("text/plain");
        getViewContext().getResponse().setStatus(statusCode);
        writer.write(PageFlowUtil.jsString(message));
        writer.flush();
    }
}
