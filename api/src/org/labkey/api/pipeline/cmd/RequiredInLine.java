/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.pipeline.cmd;

import org.labkey.api.pipeline.PipelineJobService;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <code>RequiredInLine</code>
*/
public class RequiredInLine extends TaskToCommandArgs
{
    private String _value;
    private boolean _addPipelineToolsDir = false;
    private String _softwarePackage;
    private String _versionParamName;

    public String getValue()
    {
        return _value;
    }

    public void setValue(String value)
    {
        _value = value;
    }

    public boolean isAddPipelineToolsDir()
    {
        return _addPipelineToolsDir;
    }

    public void setAddPipelineToolsDir(boolean addPipelineToolsDir)
    {
        this._addPipelineToolsDir = addPipelineToolsDir;
    }

    public String getSoftwarePackage()
    {
        return _softwarePackage;
    }

    public void setSoftwarePackage(String softwarePackage)
    {
        _softwarePackage = softwarePackage;
    }

    public String getVersionParamName()
    {
        return _versionParamName;
    }

    public void setVersionParamName(String versionParamName)
    {
        _versionParamName = versionParamName;
    }

    private String getVersion(CommandTask task)
    {
        if (_versionParamName == null)
            return null;

        Map<String, String> jobParams = task.getJob().getParameters();

        return (jobParams == null ? null : jobParams.get(_versionParamName));
    }

    protected String getFullValue(CommandTask task) throws FileNotFoundException
    {
        return isAddPipelineToolsDir() ? PipelineJobService.get().getToolPath(getValue(), null,  _softwarePackage, getVersion(task), task.getJob().getLogger()) : getValue();
    }

    public List<String> toArgsInner(CommandTask task, Set<TaskToCommandArgs> visited) throws IOException
    {
        return Collections.singletonList(getFullValue(task));
    }
}
