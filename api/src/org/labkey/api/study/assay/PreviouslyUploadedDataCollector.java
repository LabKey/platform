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
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.InsertView;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Aug 3, 2007
 */
public class PreviouslyUploadedDataCollector extends AbstractAssayDataCollector
{
    private boolean _uploadComplete = false;

    private static final String PATH_FORM_ELEMENT_NAME = "PreviouslyUploadedFilePaths";
    private static final String NAME_FORM_ELEMENT_NAME = "PreviouslyUploadedFileNames";
    private final Map<String, File> _uploadedFiles;

    public PreviouslyUploadedDataCollector()
    {
        this(Collections.<String, File>emptyMap());
    }

    public PreviouslyUploadedDataCollector(Map<String, File> uploadedFiles)
    {
        _uploadedFiles = uploadedFiles;
    }

    public String getHTML(AssayRunUploadContext context)
    {
        StringBuilder sb = new StringBuilder();
        String separator = "";
        PipeRoot pipeRoot = getPipelineRoot(context.getContainer());
        for (Map.Entry<String, File> entry : _uploadedFiles.entrySet())
        {
            sb.append(separator);
            separator = ", ";
            sb.append(PageFlowUtil.encode(entry.getValue().getName()));
            sb.append("<input name=\"");
            sb.append(PATH_FORM_ELEMENT_NAME);
            sb.append("\" type=\"hidden\" value=\"");
            sb.append(PageFlowUtil.filter(pipeRoot.relativePath(entry.getValue()).replace('\\', '/')));
            sb.append("\"/><input type=\"hidden\" name=\"");
            sb.append(NAME_FORM_ELEMENT_NAME);
            sb.append("\" value=\"");
            sb.append(PageFlowUtil.encode(entry.getKey()));
            sb.append("\"/>");
        }
        return sb.toString();
    }

    public String getShortName()
    {
        return "Previously uploaded files";
    }

    public String getDescription(AssayRunUploadContext context)
    {
        return "Use the data that was already uploaded to the server";
    }

    @NotNull
    public Map<String, File> createData(AssayRunUploadContext context) throws IOException
    {
        if (_uploadComplete)
            return Collections.emptyMap();

        String[] paths = context.getRequest().getParameterValues(PATH_FORM_ELEMENT_NAME);
        String[] names = context.getRequest().getParameterValues(NAME_FORM_ELEMENT_NAME);
        if (paths == null)
        {
            paths = new String[0];
        }
        if (names == null)
        {
            names = new String[0];
        }
        if (paths.length != names.length)
        {
            throw new IOException("The number of paths did not match the number of names");
        }
        Map<String, File> result = new HashMap<String, File>();

        PipeRoot pipelineRoot = getPipelineRoot(context.getContainer());

        for (int i = 0; i < paths.length; i++)
        {
            result.put(names[i], pipelineRoot.resolvePath(paths[i]));
        }
        return result;
    }

    public boolean isVisible()
    {
        return !_uploadedFiles.isEmpty();
    }

    public void addHiddenFormFields(InsertView view, AssayRunUploadContext context)
    {
        PipeRoot pipeRoot = getPipelineRoot(context.getContainer());

        view.getDataRegion().addHiddenFormField("dataCollectorName", getShortName());
        for (Map.Entry<String, File> entry : _uploadedFiles.entrySet())
        {
            view.getDataRegion().addHiddenFormField(NAME_FORM_ELEMENT_NAME, entry.getKey());
            view.getDataRegion().addHiddenFormField(PATH_FORM_ELEMENT_NAME, pipeRoot.relativePath(entry.getValue()));
        }
    }

    public void uploadComplete(AssayRunUploadContext context)
    {
        _uploadComplete = true;
    }
}
