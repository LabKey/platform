/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.pipeline.WorkDirectory;

import java.io.IOException;

/**
 * <code>TaskPathToCommandArgs</code>
*/
abstract public class TaskPathToCommandArgs extends TaskToCommandArgs
{
    private WorkDirectory.Function _function = WorkDirectory.Function.input;
    private String _key;

    public WorkDirectory.Function getFunction()
    {
        return _function;
    }

    public void setFunction(WorkDirectory.Function function)
    {
        _function = function;
    }

    public void setFunction(String function)
    {
        _function = WorkDirectory.Function.valueOf(function);
    }

    public String getKey()
    {
        return (_key != null ? _key : _function.toString());
    }

    public void setKey(String key)
    {
        _key = key;
    }

    public String[] getPaths(CommandTask task) throws IOException
    {
        return task.getProcessPaths(getFunction(), getKey());
    }
}
