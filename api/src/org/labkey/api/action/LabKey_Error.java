package org.labkey.api.action;

import org.labkey.api.annotations.RefactorIn17_3;

/**
 * Created by adam on 6/19/2017.
 */
@Deprecated
@RefactorIn17_3
// TODO: Delete... added just in case there are Git feature branches that still use the previous name
public class LabKey_Error extends LabKeyError
{
    public LabKey_Error(Throwable t)
    {
        super(t);
    }

    public LabKey_Error(String message)
    {
        super(message);
    }
}
