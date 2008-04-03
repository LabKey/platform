package org.labkey.api.exp;

/**
* User: jeckels
* Date: Feb 19, 2008
*/
public class ChangePropertyDescriptorException extends Exception
{
    public ChangePropertyDescriptorException(String message)
    {
        super(message);
    }
    public ChangePropertyDescriptorException(Throwable cause)
    {
        super("An exception occurred: " + cause, cause);
    }
}
