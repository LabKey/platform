package org.labkey.api.compliance;

import org.labkey.api.util.SafeToRenderEnum;

public enum PhiColumnBehavior implements SafeToRenderEnum
{
    show,
    hide,
    blank;

    public static PhiColumnBehavior get(String value)
    {
        if ("hide".equals(value))
            return hide;

        if ("blank".equals(value))
            return blank;

        return show;
    }
}
