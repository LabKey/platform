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
 * Allows different arguments to be generated based on the value of a single parameter.
 * The converters map has keys which are the values of the parameter that should cause the inclusion of specific
 * arguments, and the values are the set of TaskToCommandArgs that generate the desired arguments if the parameter's
 * value matches.
 *
 * The default controls which key should be used if the protocol has not specified a value for the parameter.
 *
 * If none of the keys match the value (either from the protocol or the default value), no arguments will be added.
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
