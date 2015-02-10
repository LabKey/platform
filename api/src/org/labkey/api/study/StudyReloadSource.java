package org.labkey.api.study;

import com.drew.lang.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * Represents a potential source for study artifacts to be created and reloaded automatically through the
 * normal study reload framework. The source of the study artifacts could be an external repository or server
 * that is being used as a synchronization source for a LabKey server.
 *
 * Created by klum on 1/16/2015.
 */
public interface StudyReloadSource
{
    /**
     * Returns the descriptive name
     */
    String getName();

    boolean isEnabled(Container container);

    @Nullable
    ActionURL getManageAction(Container c, User user);

    /**
     * Generate the study reload source artifacts from an external source repository in order for the
     * study reload mechanism to update the source study.
     *
     * @param job the pipeline job that this reload task is running in, useful for adding logging information into.
     * @throws PipelineJobException
     */
    void generateReloadSource(@Nullable PipelineJob job, Study study) throws PipelineJobException;
}
