package org.labkey.api.settings;

/**
 * Created by marty on 7/5/2017.
 */
public class HeaderProperties implements TemplateProperties
{
    private final String HEADER_CONFIGS = "HeaderProperties";
    private final String SHOW_HEADER_PROPERTY_NAME = "ShowHeader";
    private final String HEADER_MODULE_PROPERTY_NAME = "HeaderModule";
    private final String FILE_NAME = "_header";

    public String getDisplayConfigs()
    {
        return HEADER_CONFIGS;
    }

    public String getDisplayPropertyName()
    {
        return SHOW_HEADER_PROPERTY_NAME;
    }

    public String getModulePropertyName()
    {
        return HEADER_MODULE_PROPERTY_NAME;
    }

    public String getFileName()
    {
        return FILE_NAME;
    }

    public String getShowByDefault() { return "FALSE";}
}
