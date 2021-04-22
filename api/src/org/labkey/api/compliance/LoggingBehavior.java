package org.labkey.api.compliance;

import org.labkey.api.util.SafeToRenderEnum;

public enum LoggingBehavior implements SafeToRenderEnum
{
    none
            {
                @Override
                public boolean shouldLog(boolean hasPhi)
                {
                    return false;
                }
            },
    phi
            {
                @Override
                public boolean shouldLog(boolean hasPhi)
                {
                    return hasPhi;
                }
            },
    all
            {
                @Override
                public boolean shouldLog(boolean hasPhi)
                {
                    return true;
                }
            };

    public abstract boolean shouldLog(boolean hasPhi);

    public static LoggingBehavior get(String value)
    {
        if ("all".equals(value))
            return all;

        if ("none".equals(value))
            return none;

        return phi;
    }
}
