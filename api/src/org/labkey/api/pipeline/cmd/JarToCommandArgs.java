/*
 * Copyright (c) 2008 LabKey Software Foundation
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

import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import java.io.IOException;

/**
 * <code>JarToCommandArgs</code>
*/
public class JarToCommandArgs extends ListToCommandArgs
{
    private String _jarPath;

    public JarToCommandArgs()
    {
        setSwitchFormat(new UnixSwitchFormat());

        addSwitch("client");
        addSwitch("Xmx512M");   // TODO: Make settable
        addSwitch("jar");
    }

    public String getJarPath()
    {
        return _jarPath;
    }

    public void setJarPath(String jarPath)
    {
        _jarPath = jarPath;
    }

    public String[] toArgsInner(CommandTask task, Set<TaskToCommandArgs> visited) throws IOException
    {
        if (_jarPath == null || _jarPath.length() == 0)
            return new String[0];

        ArrayList<String> args = new ArrayList<String>();

        RequiredInLine converterInline = new RequiredInLine();
        converterInline.setParent(this);
        converterInline.setValue(PipelineJobService.get().getJavaPath());
        args.addAll(Arrays.asList(converterInline.toArgs(task, visited)));

        for (TaskToCommandArgs converter : getConverters())
            args.addAll(Arrays.asList(converter.toArgs(task, visited)));

        // TODO: Versioning?
        converterInline.setValue(PipelineJobService.get().getJarPath(_jarPath));
        args.addAll(Arrays.asList(converterInline.toArgs(task, visited)));

        return args.toArray(new String[args.size()]);
    }
}
