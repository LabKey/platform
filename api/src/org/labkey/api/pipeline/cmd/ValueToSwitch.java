package org.labkey.api.pipeline.cmd;

/**
 * <code>ValueToSwitch</code> returns the switch name regardless of what the value is, as long as it is not null
 * most likely used in conjuction with another cmd type (ex. ValueToSwitch and ValueToMultiCommandArgs) 
*/
public class ValueToSwitch extends AbstractValueToNamedSwitch
{
    public String[] toArgs(String value)
    {
        if (value != null && value.length() > 0)
            return getSwitchFormat().format(getSwitchName());

        return new String[0];
    }
}
