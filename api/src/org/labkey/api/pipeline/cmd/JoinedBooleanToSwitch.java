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

import java.util.List;
import java.util.Set;
import java.io.IOException;

/**
 * <code>JoinedBooleanToSwitch</code>
*/
public class JoinedBooleanToSwitch extends TaskToCommandArgs
{
    private List<BooleanToSwitch> _converters;

    public List<BooleanToSwitch> getConverters()
    {
        return _converters;
    }

    public void setConverters(List<BooleanToSwitch> converters)
    {
        _converters = converters;
        for (TaskToCommandArgs converter : converters)
            converter.setParent(this);
    }

    public String[] toArgsInner(CommandTask task, Set<TaskToCommandArgs> visited) throws IOException
    {
        StringBuffer switches = new StringBuffer();
        for (BooleanToSwitch converter : getConverters())
        {
            if (converter.toArgs(task, visited).length > 0)
                switches.append(converter.getSwitchName());
        }
        if (switches.length() > 0)
            return getSwitchFormat().format(switches.toString());

        return new String[0];
    }
}
