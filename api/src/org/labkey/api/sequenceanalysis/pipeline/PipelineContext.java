package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.WorkDirectory;

import java.io.File;

/**
 * User: bimber
 * Date: 6/13/2014
 * Time: 1:21 PM
 */
public interface PipelineContext
{
    public Logger getLogger();

    public PipelineJob getJob();

    public WorkDirectory getWorkDir();

    public SequenceAnalysisJobSupport getSequenceSupport();

    /**
     * This is the directory where most of the work should take place, usually the remote pipeline working folder.
     */
    public File getWorkingDirectory();

    /**
     * This is the directory where the source files were located and where we expect to deposit the files on completion.
     */
    public File getSourceDirectory();
}
