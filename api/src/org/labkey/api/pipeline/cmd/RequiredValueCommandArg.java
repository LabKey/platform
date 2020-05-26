/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
import java.util.Map;
import java.util.Set;

/**
 * <code>RequiredValueCommandArg</code>
*/
public class RequiredValueCommandArg extends JobParamToCommandArgs
{
    private String _value;
    private ValueToCommandArgs _formatter;

    public String getValue()
    {
        return _value;
    }

    public void setValue(String value)
    {
        _value = value;
    }

    public ValueToCommandArgs getFormatter()
    {
        return _formatter;
    }

    public void setFormatter(ValueToCommandArgs formatter)
    {
        _formatter = formatter;
    }

    @Override
    public List<String> toArgsInner(CommandTask task, Map<String, String> params, Set<TaskToCommandArgs> visited)
    {
        return _formatter.toArgs(_value);
    }
}
