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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * <code>ListToCommandArgs</code>
*/
public class ListToCommandArgs extends TaskToCommandArgs
{
    private List<TaskToCommandArgs> _converters;

    public List<TaskToCommandArgs> getConverters()
    {
        return _converters;
    }

    public void setConverters(List<TaskToCommandArgs> converters)
    {
        _converters = converters;
        for (TaskToCommandArgs converter : converters)
            converter.setParent(this);
    }

    public void addInline(String arg)
    {
        RequiredInLine converter = new RequiredInLine();
        converter.setValue(arg);
        converter.setParent(this);
        addConverter(converter);
    }

    public void addSwitch(String switchName)
    {
        RequiredSwitch converter = new RequiredSwitch();
        converter.setSwitchName(switchName);
        converter.setParent(this);
        addConverter(converter);
    }

    public void addConverter(TaskToCommandArgs converter)
    {
        if (_converters == null)
            _converters = new ArrayList<>();
        _converters.add(converter);
    }

    public List<String> toArgsInner(CommandTask task, Set<TaskToCommandArgs> visited) throws IOException
    {
        ArrayList<String> args = new ArrayList<>();
        for (TaskToCommandArgs converter : getConverters())
            args.addAll(converter.toArgs(task, visited));
        return args;
    }
}
