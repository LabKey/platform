package org.labkey.api.pipeline;

/**
* User: jeckels
* Date: Jun 6, 2012
*/
public class PipelineValidationException extends Exception
{
    public PipelineValidationException(String message)
    {
        super(message);
    }

    public PipelineValidationException(Throwable cause)
    {
        super(cause);
    }
}
