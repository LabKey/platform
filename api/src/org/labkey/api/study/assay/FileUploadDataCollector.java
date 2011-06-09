/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Jul 12, 2007
 */
public class FileUploadDataCollector<ContextType extends AssayRunUploadContext> extends AbstractAssayDataCollector<ContextType>
{
    private boolean _uploadComplete = false;
    private Set<String> _validExtensions = null;
    private int _maxFileInputs = 1;

    public FileUploadDataCollector()
    {

    }

    public FileUploadDataCollector(int maxFileInputs)
    {
        _maxFileInputs = maxFileInputs; 
    }

    public FileUploadDataCollector(String... validExtensions)
    {
        _validExtensions = new CaseInsensitiveHashSet();
        _validExtensions.addAll(Arrays.asList(validExtensions));
    }

    public HttpView getView(ContextType context)
    {
        return new JspView<FileUploadDataCollector>("/org/labkey/api/study/assay/fileUpload.jsp", this);
    }

    public String getShortName()
    {
        return "File upload";
    }

    public String getDescription(ContextType context)
    {
        return "Upload a data file";
    }

    @NotNull
    public Map<String, File> createData(ContextType context) throws IOException, IllegalArgumentException, ExperimentException
    {
        if (_uploadComplete)
            return Collections.emptyMap();

        if (!(context.getRequest() instanceof MultipartHttpServletRequest))
            throw new IllegalStateException("Expected MultipartHttpServletRequest when posting files.");

        Set<String> fileInputs = new HashSet<String>();
        fileInputs.add(PRIMARY_FILE);

        // if assay type allows for > 1 file, add those inputs to the set as well
        int fileInputIndex = 1;
        while(fileInputIndex < getMaxFileInputs())
        {
            fileInputs.add(PRIMARY_FILE + fileInputIndex);
            fileInputIndex++;
        }

        Map<String, File> files = savePostedFiles(context, fileInputs);
        if (!files.containsKey(PRIMARY_FILE))
            throw new ExperimentException("No data file was uploaded. Please enter a file name.");
        return files;
    }

    public boolean isVisible()
    {
        return true;
    }

    public void uploadComplete(ContextType context)
    {
        _uploadComplete = true;
    }

    public int getMaxFileInputs()
    {
        return _maxFileInputs;
    }
}
