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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Used for -SWITCH_NAME SWITCH_VALUE arguments
*/
public class UnixSwitchFormat implements SwitchFormat
{
    private String _switch = "-";
    private String _separator = " ";

    public String getSeparator()
    {
        return _separator;
    }

    public void setSeparator(String separator)
    {
        _separator = separator;
    }

    public String getSwitch()
    {
        return _switch;
    }

    public void setSwitch(String aSwitch)
    {
        _switch = aSwitch;
    }

    public String getCommandSwitch(String name)
    {
        return _switch + name;
    }

    public List<String> format(String name)
    {
        return format(name, null);
    }

    public List<String> format(String name, String value)
    {
        // If no value, return only the formatted switch.
        if (value == null)
            return Collections.singletonList(getCommandSwitch(name));

        // If separator is a space, then return two separate command args.
        if (" ".equals(_separator))
            return Arrays.asList(getCommandSwitch(name), value);

        // Format as a single command arg.
        return Arrays.asList(getCommandSwitch(name) + getSeparator() + value);
    }
}
