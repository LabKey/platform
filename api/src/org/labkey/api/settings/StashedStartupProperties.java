package org.labkey.api.settings;

public enum StashedStartupProperties implements StartupProperty
{
    homeProjectFolderType("Home project folder type"),
    homeProjectResetPermissions("Reset the home project permissions to remove default assignments given at server install"),
    homeProjectWebparts("Semicolon-delimited list of webpart names to add to the home project"),
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
