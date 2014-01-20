package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;

/**
* User: adam
* Date: 1/17/14
* Time: 3:20 PM
*/
public enum Phi
{
    NotPHI,
    Limited,
    PHI,
    Restricted;

    public static Phi fromString(@Nullable String value)
    {
        for (Phi phi : values())
            if (phi.name().equals(value))
                return phi;

        return null;
    }
}
