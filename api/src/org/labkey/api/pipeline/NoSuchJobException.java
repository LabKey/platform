package org.labkey.api.pipeline;

/**
 * User: jeckels
 * Date: 10/28/13
 */
public class NoSuchJobException extends Exception
{
    public NoSuchJobException(String message)
    {
        super(message);
    }
}
