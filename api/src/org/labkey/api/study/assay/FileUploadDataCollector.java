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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: Jul 12, 2007
 */
public class FileUploadDataCollector<ContextType extends AssayRunUploadContext<? extends AssayProvider>> extends AbstractTempDirDataCollector<ContextType>
{
    private int _maxFileInputs = 1;
    private final Map<String, File> _reusableFiles;
    // Name of the form <input> for the file.
    private String _fileInputName;

    public FileUploadDataCollector()
    {
        this(1);
    }

    public FileUploadDataCollector(int maxFileInputs)
    {
        this(maxFileInputs, Collections.emptyMap());
    }

    public FileUploadDataCollector(int maxFileInputs, Map<String, File> reusableFiles)
    {
        this(maxFileInputs, reusableFiles, PRIMARY_FILE);
    }

    public FileUploadDataCollector(int maxFileInputs, Map<String, File> reusableFiles, String fileInputName)
    {
        _maxFileInputs = maxFileInputs;
        _reusableFiles = Collections.unmodifiableMap(reusableFiles);
        _fileInputName = fileInputName;
        if (_reusableFiles.size() > _maxFileInputs)
        {
            throw new IllegalArgumentException("A max of " + _maxFileInputs + " is allowed, but " + _reusableFiles.size() + " are being offered for reuse.");
        }
    }

    public HttpView getView(ContextType context)
    {
        return new JspView<FileUploadDataCollector>("/org/labkey/api/study/assay/fileUpload.jsp", this);
    }

    public Map<String, File> getReusableFiles()
    {
        return _reusableFiles;
    }

    public String getShortName()
    {
        return "File upload";
    }

    public String getDescription(ContextType context)
    {
        return _maxFileInputs == 1 ? "Upload a data file" : "Upload one or more data files";
    }

    @NotNull
    public Map<String, File> createData(ContextType context) throws IOException, IllegalArgumentException, ExperimentException
    {
        if (_uploadComplete)
            return Collections.emptyMap();

        Set<String> fileInputs = new HashSet<>();
        fileInputs.add(_fileInputName);

        // if assay type allows for > 1 file, add those inputs to the set as well
        int fileInputIndex = 1;
        while(fileInputIndex < getMaxFileInputs())
        {
            fileInputs.add(_fileInputName + fileInputIndex);
            fileInputIndex++;
        }

        Map<String, File> files = savePostedFiles(context, fileInputs);

        // Figure out if we have any data files to work with -
        boolean foundFiles = files.containsKey(_fileInputName);

        if (_maxFileInputs > 1)
        {
            // In the case that we're allowing reuse through this codepath
            // use any previously uploaded files that are still selected
            PreviouslyUploadedDataCollector<ContextType> previousCollector = new PreviouslyUploadedDataCollector<>(Collections.emptyMap(), PreviouslyUploadedDataCollector.Type.ReRun);
            Map<String, File> reusedFiles = previousCollector.createData(context);

            // Merge the two sets of files
            Map<String, File> mergedFiles = new HashMap<>();
            int index = 0;
            // Start with the reused ones so we preserve the original "primary" file if possible
            for (Map.Entry<String, File> entry : reusedFiles.entrySet())
            {
                mergedFiles.put(PRIMARY_FILE + (index++ == 0 ? "" : Integer.toString(index)), entry.getValue());
            }
            // Add in any newly uploaded files
            for (Map.Entry<String, File> entry : files.entrySet())
            {
                mergedFiles.put(PRIMARY_FILE + (index++ == 0 ? "" : Integer.toString(index)), entry.getValue());
            }
            files = mergedFiles;
            // It's OK to just reuse files and not upload any new ones
            foundFiles |= !reusedFiles.isEmpty();
        }

        if (!foundFiles)
        {
            ExperimentException x = new ExperimentException("No data file was uploaded. Please select a file.");
            ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
            throw x;
        }
        return files;
    }

    public int getMaxFileInputs()
    {
        return _maxFileInputs;
    }
}
