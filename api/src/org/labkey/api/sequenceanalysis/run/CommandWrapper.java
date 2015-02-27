package org.labkey.api.sequenceanalysis.run;

import org.labkey.api.pipeline.PipelineJobException;

import java.io.File;
import java.util.List;

/**
 * User: bimber
 * Date: 6/19/2014
 * Time: 9:34 AM
 */
public interface CommandWrapper
{
    /**
     *
     * @param params A list of params used to create the command.  This will be passed directly to ProcessBuilder()
     * @return The output of this command.
     * @throws PipelineJobException
     */
    String execute(List<String> params) throws PipelineJobException;

    String execute(List<String> params, File stdout) throws PipelineJobException;
}
