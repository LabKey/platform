package org.labkey.api.premium;

public class PremiumFeatureNotEnabledException extends UnsupportedOperationException
{
    private static final String BASE_MESSAGE = "This premium feature is not enabled. Please contact your LabKey representative.";

    public PremiumFeatureNotEnabledException()
    {
        this(BASE_MESSAGE);
    }

    public PremiumFeatureNotEnabledException(String message)
    {
        super(message);
    }
}
