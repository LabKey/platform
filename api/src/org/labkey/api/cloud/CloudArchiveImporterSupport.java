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
        if (!studyXml.startsWith(job.getPipeRoot().getImportDirectory().toPath().toAbsolutePath()))
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
