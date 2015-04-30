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
import java.util.List;
import java.util.Set;

/**
 * <code>TaskToCommandArgs</code>
*/
abstract public class TaskToCommandArgs
{
    private TaskToCommandArgs _parent;
    private SwitchFormat _switchFormat;

    protected TaskToCommandArgs getParent()
    {
        return _parent;
    }

    protected void setParent(TaskToCommandArgs parent)
    {
        _parent = parent;
    }

    public SwitchFormat getSwitchFormat()
    {
        // If switch format has not been explicitly set, then look to the parent.
        if (_switchFormat == null && _parent != null)
            return _parent.getSwitchFormat();
        return _switchFormat;
    }

    public void setSwitchFormat(SwitchFormat switchFormat)
    {
        _switchFormat = switchFormat;
    }

    public final List<String> toArgs(CommandTask task, Set<TaskToCommandArgs> visited) throws IOException
    {
        if (visited.contains(this))
            throw new IllegalStateException("Circular logic in job to command args logic.");

        try
        {
            visited.add(this);

            return toArgsInner(task, visited);
        }
        finally
        {
            visited.remove(this);
        }
    }

    abstract public List<String> toArgsInner(CommandTask task, Set<TaskToCommandArgs> visited) throws IOException;
}
