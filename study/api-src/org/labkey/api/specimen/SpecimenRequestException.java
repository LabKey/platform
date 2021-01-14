package org.labkey.api.specimen;

public class SpecimenRequestException extends RuntimeException
{
    public SpecimenRequestException()
    {
    }

    public SpecimenRequestException(String message)
    {
        super(message);
    }
}
