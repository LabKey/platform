package org.labkey.api.reports;

import org.labkey.api.premium.PremiumFeatureNotEnabledException;

public class RServeNotEnabledException extends PremiumFeatureNotEnabledException
{
    private static final String BASE_MESSAGE = "Using a remote R service is a Premium Feature, which is not currently enabled. Please contact your LabKey representative.";

    public RServeNotEnabledException()
    {
        this(BASE_MESSAGE);
    }

    public RServeNotEnabledException(String message)
    {
        super(message);
    }

    public RServeNotEnabledException(ExternalScriptEngineDefinition def)
    {
        this(String.format("Remote R serve engine [%1$s] requested, but premium module not available/enabled.", def.getName()));
    }
}
