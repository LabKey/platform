/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.cloud;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.VirtualFile;
import org.springframework.validation.BindException;

import java.nio.file.Path;

public interface CloudArchiveImporterSupport
{
    VirtualFile getRoot();
    String getOriginalFilename();


    /**
     * Retrieve an expanded study archive's files from the CloudStoreService if necessary
     *
     * Note: Updates job's working root to point to the local temp dir download location
     * @param job Job being executed
     * @param studyXml Path to study file being downloaded
     * @param errors Delayed errors collection
     */
    default void downloadCloudArchive(@NotNull PipelineJob job, @NotNull Path studyXml, BindException errors) throws UnsupportedOperationException
    {
        //check if cloud based pipeline root, and study xml hasn't been downloaded already
        if (!studyXml.startsWith(job.getPipeRoot().getImportDirectory().toNioPathForRead().toAbsolutePath()))
        {
            if (CloudStoreService.get() != null)   //proxy of is Cloud Module enabled for the current job/container
            {
                try
                {
                    Path importRoot = CloudStoreService.get().downloadExpandedArchive(job);

                    // Replace remote based context with local temp dir based context
                    updateWorkingRoot(importRoot);
                }
                catch (PipelineJobException e)
                {
                    errors.addSuppressed(e);
                }
            }
            else
            {
                throw new IllegalStateException("Cloud module service not available.");
            }
        }
    }

    /**
     * Set the working root to the path provided
     * @param newRoot new path to use
     */
    void updateWorkingRoot(Path newRoot) throws PipelineJobException;

    default boolean useLocalImportDir(PipelineJob job, String studyLocation)
    {
        return job != null && job.getPipeRoot().isCloudRoot() && FileUtil.hasCloudScheme(studyLocation);
    }
}
