package org.labkey.api.action;

import org.labkey.api.view.BadRequestException;

public class AntiVirusException extends BadRequestException
{
    public AntiVirusException(String message)
    {
        super(message, null);
    }
}
