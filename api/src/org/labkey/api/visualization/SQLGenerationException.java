package org.labkey.api.visualization;

/**
* User: jeckels
* Date: 3/9/14
*/
public class SQLGenerationException extends Exception
{
    public SQLGenerationException(String message)
    {
        super(message);
    }

    public SQLGenerationException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
