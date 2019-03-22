package org.labkey.api.reports;

import org.labkey.api.premium.PremiumFeatureNotEnabledException;

public class RemoteRNotEnabledException extends PremiumFeatureNotEnabledException
{
    public static final String BASE_MESSAGE = "Connecting to a remote R service is a Premium Feature which is not currently enabled. Please contact your LabKey admin or representative.";

    public RemoteRNotEnabledException()
    {
        this(BASE_MESSAGE);
    }

    public RemoteRNotEnabledException(String message)
    {
        super(message);
    }

    public RemoteRNotEnabledException(ExternalScriptEngineDefinition def)
    {
        this(String.format("Remote R engine [%1$s] requested, but premium module not available/enabled.", def.getName()));
    }
}
