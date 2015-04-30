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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <code>EnumToCommandArgs</code>
*/
public class EnumToCommandArgs extends JobParamToCommandArgs
{
    private Map<String, TaskToCommandArgs> _converters;

    public Map<String, TaskToCommandArgs> getConverters()
    {
        return _converters;
    }

    public void setConverters(Map<String, TaskToCommandArgs> converters)
    {
        _converters = converters;
        for (TaskToCommandArgs converter : converters.values())
            converter.setParent(this);
    }

    public TaskToCommandArgs getConverter(String value)
    {
        return _converters.get(value);
    }

    public List<String> toArgsInner(CommandTask task, Set<TaskToCommandArgs> visited) throws IOException
    {
        String keyConverter = getValue(task.getJob());
        if (keyConverter != null)
        {
            TaskToCommandArgs converter = getConverter(keyConverter);
            if (converter != null)
                return converter.toArgs(task, visited);
        }

        return Collections.emptyList();
    }
}
