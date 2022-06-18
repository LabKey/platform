package org.labkey.api.settings;

public enum StashedStartupProperties implements StartupProperty
{
    siteAvailableEmailFrom("Site available from address"),
    siteAvailableEmailMessage("Site available message"),
    siteAvailableEmailSubject("Site available subject");

    private final String _description;

    StashedStartupProperties(String description)
    {
        _description = description;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }
}
