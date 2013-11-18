package org.labkey.api.study;

import com.drew.lang.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.util.FileType;
import org.labkey.api.view.ActionURL;

import java.io.File;

/**
 * User: klum
 * Date: 11/12/13
 */
public interface SpecimenTransform
{
    /**
     * Returns the descriptive name
     */
    String getName();

    boolean isEnabled(Container container);

    /**
     * Returns the file type that this transform can accept
     */
    FileType getFileType();

    /**
     * Transform the input file into a specimen archive that a basic specimen import can
     * process.
     */
    void transform(@Nullable PipelineJob job, File input, File outputArchive) throws PipelineJobException;

    /**
     * An optional post transform step.
     */
    void postTransform(@Nullable PipelineJob job, File input, File outputArchive) throws PipelineJobException;

    @Nullable
    ActionURL getManageAction(Container c, User user);
}
