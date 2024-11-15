package org.labkey.api.util;

/**
 * Signals that we're trying to abort processing. Examples include a HTTP request that's exceeded a timeout or a
 * PipelineJob that's been canceled.
 */
public class AbortedRequestException extends RuntimeException implements SkipMothershipLogging
{
    public AbortedRequestException(String message)
    {
        super(message);
    }
}
