/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <code>JarToCommandArgs</code>
*/
public class JarToCommandArgs extends ListToCommandArgs
{
    private String _jarPath;
    private String _softwarePackage;
    private String _versionParamName;

    public JarToCommandArgs()
    {
        setSwitchFormat(new UnixCompactSwitchFormat());
    }

    public String getJarPath()
    {
        return _jarPath;
    }

    public void setJarPath(String jarPath)
    {
        _jarPath = jarPath;
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

    public List<String> toArgsInner(CommandTask task, Set<TaskToCommandArgs> visited) throws IOException
    {
        if (_jarPath == null || _jarPath.length() == 0)
            return Collections.emptyList();

        ArrayList<String> args = new ArrayList<>();

        RequiredInLine converterInline = new RequiredInLine();
        converterInline.setParent(this);
        converterInline.setValue(PipelineJobService.get().getJavaPath());
        args.addAll(converterInline.toArgs(task, visited));

        for (TaskToCommandArgs converter : getConverters())
            args.addAll(converter.toArgs(task, visited));

        args.add("-jar");

        converterInline.setValue(PipelineJobService.get().getJarPath(_jarPath,
                task.getInstallPath(), _softwarePackage, getVersion(task)));
        args.addAll(converterInline.toArgs(task, visited));

        return args;
    }
}
