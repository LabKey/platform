package org.labkey.api.microarray;

/**
 * User: jeckels
 * Date: Jan 8, 2008
 */
public class ExtractionException extends Exception
{
    public ExtractionException()
    {
        super();
    }

    public ExtractionException(String message)
    {
        super(message);
    }

    public ExtractionException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ExtractionException(Throwable cause)
    {
        super(cause);
    }
}
