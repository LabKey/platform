package org.labkey.api.pipeline;

/**
 * Thrown when an executable has a non-zero return/exit code
 * 
 * User: jeckels
 * Date: Jan 7, 2011
 */
public class ToolExecutionException extends PipelineJobException
{
    private final int _exitCode;

    public ToolExecutionException(String message, int exitCode)
    {
        super(message);
        _exitCode = exitCode;
    }

    public int getExitCode()
    {
        return _exitCode;
    }
}
