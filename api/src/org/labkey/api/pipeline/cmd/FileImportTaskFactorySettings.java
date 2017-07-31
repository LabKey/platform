package org.labkey.api.pipeline.cmd;

import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.file.FileImportTask;

/**
 * User: tgaluhn
 * Date: 7/31/2017
 */
public class FileImportTaskFactorySettings extends AbstractTaskFactorySettings
{
    private String _cloneName;

    public FileImportTaskFactorySettings(String name)
    {
        super(FileImportTask.class, name);
    }

    public String getCloneName()
    {
        return _cloneName;
    }

    public void setCloneName(String cloneName)
    {
        _cloneName = cloneName;
    }

    @Override
    public TaskId getCloneId()
    {
        return new TaskId(FileImportTask.class, _cloneName);
    }
}
