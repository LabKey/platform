package org.labkey.api.study.assay.pipeline;

import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.study.assay.pipeline.AssayImportRunTaskId;

/**
 * User: kevink
 * Date: 12/18/13
 */
public class AssayImportRunTaskFactorySettings extends AbstractTaskFactorySettings
{
    private String _cloneName;
    private String _providerName;
    private String _protocolName;

    public AssayImportRunTaskFactorySettings(String name)
    {
        this(AssayImportRunTaskId.class, name);
    }

    public AssayImportRunTaskFactorySettings(Class namespaceClass, String name)
    {
        super(namespaceClass, name);
    }

    public AssayImportRunTaskFactorySettings(TaskId taskId)
    {
        super(taskId);
    }

    @Override
    public TaskId getCloneId()
    {
        return new TaskId(AssayImportRunTaskId.class, _cloneName);
    }

    public String getCloneName()
    {
        return _cloneName;
    }

    public void setCloneName(String cloneName)
    {
        _cloneName = cloneName;
    }

    public String getProviderName()
    {
        return _providerName;
    }

    public void setProviderName(String providerName)
    {
        _providerName = providerName;
    }

    public String getProtocolName()
    {
        return _protocolName;
    }

    public void setProtocolName(String protocolName)
    {
        _protocolName = protocolName;
    }
}
