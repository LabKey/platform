package org.labkey.experiment;

/**
 * User: jeckels
 * Date: Sep 12, 2006
 */
public enum XarExportType
{
    BROWSER_DOWNLOAD("Browser download"),
    PIPELINE_FILE("Write to exportedXars directory in pipeline root");

    private final String _description;

    private XarExportType(String description)
    {
        _description = description;
    }

    public String getDescription()
    {
        return _description;
    }
}
