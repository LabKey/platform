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
