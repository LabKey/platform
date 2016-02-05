/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.util.NetworkDrive;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
 * Date: 8/17/12
 */
public abstract class AbstractTempDirDataCollector<ContextType extends AssayRunUploadContext<? extends AssayProvider>> extends AbstractAssayDataCollector<ContextType>
{
    protected boolean _uploadComplete = false;

    protected File getFileTargetDir(ContextType context) throws ExperimentException
    {
        if (context instanceof AssayRunUploadForm)
        {
            File tempDir = ensureSubdirectory(context.getContainer(), TEMP_DIR_NAME);
            File uploadAttemptDir = new File(tempDir, ((AssayRunUploadForm)context).getUploadAttemptID());

            if (!NetworkDrive.exists(uploadAttemptDir))
            {
                uploadAttemptDir.mkdir();
            }
            if (!uploadAttemptDir.isDirectory())
            {
                throw new ExperimentException("Unable to create temporary assay directory " + uploadAttemptDir);
            }
            return uploadAttemptDir;
        }
        else
        {
            return super.getFileTargetDir(context);
        }
    }

    public Map<String, File> uploadComplete(ContextType context, @Nullable ExpRun run) throws ExperimentException
    {
        Map<File, String> fileToName = new HashMap<>();
        Map<String, File> uploadedData = context.getUploadedData();
        Map<String, File> result = new HashMap<>(uploadedData);
        for (Map.Entry<String, File> entry : uploadedData.entrySet())
        {
            fileToName.put(entry.getValue(), entry.getKey());
        }

        // Copy the data files from the temp directory into the real assay directory, and fix up any references
        // to the file that are stored in the exp.data table
        try
        {
            List<? extends ExpData> allData = run == null ? Collections.<ExpData>emptyList() : run.getAllDataUsedByRun();
            File assayDir = ensureUploadDirectory(context.getContainer());
            File tempDir = getFileTargetDir(context);
            for (File tempDirFile : tempDir.listFiles())
            {
                File assayDirFile = findUniqueFileName(tempDirFile.getName(), assayDir);
                String uploadName = fileToName.get(tempDirFile);
                if (uploadName != null)
                {
                    result.put(uploadName, assayDirFile);
                }
                for (ExpData expData : allData)
                {
                    if (tempDirFile.equals(expData.getFile()))
                    {
                        expData.setDataFileURI(assayDirFile.toURI());
                        expData.save(context.getUser());
                    }
                }
                if (run != null)
                {
                    // Fixup the path in the run itself so that it's not pointed at the temp directory
                    run.setFilePathRoot(assayDir);

                    // If the run name is the filename, and the filename was changed to another unique value, change the run name.
                    if (run.getName().equals(tempDirFile.getName()) ) {
                        run.setName(getPreferredAssayId(assayDirFile));
                    }

                    run.save(context.getUser());
                }
                FileUtils.moveFile(tempDirFile, assayDirFile);
            }
            FileUtils.deleteDirectory(tempDir);
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
        _uploadComplete = true;

        return result;
    }

    /** @return the preferred name for the run given the primary data file */
    protected String getPreferredAssayId(File primaryFile)
    {
        return primaryFile.getName();
    }

    public boolean isVisible()
    {
        return true;
    }
}
