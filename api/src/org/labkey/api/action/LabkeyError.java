package org.labkey.api.action;

import org.springframework.validation.ObjectError;

/**
 * User: jeckels
 * Date: May 2, 2008
 */
public class LabkeyError extends ObjectError
{
    public LabkeyError(String message)
    {
        super("main", new String[] { "Error" }, new Object[] { message }, message);
    }
}
