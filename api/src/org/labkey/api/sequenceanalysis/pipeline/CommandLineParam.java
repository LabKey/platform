/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.sequenceanalysis.pipeline;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
* User: bimber
* Date: 6/14/2014
* Time: 8:02 AM
*/
public class CommandLineParam
{
    private String _argName;
    protected boolean _isSwitch = false;

    private CommandLineParam(String argName, boolean isSwitch)
    {
        _argName = argName;
        _isSwitch = isSwitch;
    }

    public static CommandLineParam create(String argName)
    {
        return new CommandLineParam(argName, false);
    }

    public static CommandLineParam createSwitch(String argName)
    {
        return new CommandLineParam(argName, true);
    }

    public List<String> getArguments(String value)
    {
        return getArguments(null, value);
    }

    public List<String> getArguments(String separator, String value)
    {
        String ret = _argName;
        if (_isSwitch)
        {
            //use true as a proxy for include or not
            if ("true".equals(value))
            {
                return Collections.singletonList(ret);
            }

            return Collections.EMPTY_LIST;
        }
        else
        {
            if (value == null)
                return Collections.EMPTY_LIST;

            if (StringUtils.trimToNull(separator) == null)
            {
                return Arrays.asList(ret, value);
            }
            else
            {
                return Arrays.asList(ret + separator + value);
            }
        }
    }

    public String getArgName()
    {
        return _argName;
    }
}
