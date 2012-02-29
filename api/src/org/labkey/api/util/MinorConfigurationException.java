package org.labkey.api.util;

/**
 * User: adam
 * Date: 2/28/12
 * Time: 4:48 PM
 */

// Use to report minor configuration problems to users/admins (but NOT potential code issues, since the exceptions are
// not reported to mothership). For example, file system permissions problem, missing directory, etc.
public class MinorConfigurationException extends RuntimeException implements SkipMothershipLogging
{
    public MinorConfigurationException(String message)
    {
        super(message);
    }


    public MinorConfigurationException(String message, Throwable t)
    {
        super(message, t);
    }
}
