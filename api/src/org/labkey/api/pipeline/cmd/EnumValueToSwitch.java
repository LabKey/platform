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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <code>EnumValueToSwitch</code>
*/
public class EnumValueToSwitch extends ValueToCommandArgs
{
    private Map<String, String> _switches;

    public String getSwitchName(String value)
    {
        return _switches.get(value);
    }

    public Map<String, String> getSwitches()
    {
        return _switches;
    }

    public void setSwitches(Map<String, String> switches)
    {
        _switches = switches;
    }

    public List<String> toArgs(String value)
    {
        if (value != null)
            return getSwitchFormat().format(getSwitchName(value));

        return Collections.emptyList();
    }
}
