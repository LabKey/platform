/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.study.assay.pipeline;

import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.TaskId;

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
