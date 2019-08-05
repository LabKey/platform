package org.labkey.api.settings;

import org.labkey.api.data.Container;

public class BannerProperties implements TemplateProperties
{
    private final String BANNER_CONFIGS = "BannerProperties";
    private final String SHOW_BANNER_PROPERTY_NAME = "ShowBanner";
    private final String BANNER_MODULE_PROPERTY_NAME = "BannerModule";
    private final String FILE_NAME = "_banner";

    private Container _container;

    public BannerProperties(Container container)
    {
        _container = container;
    }

    @Override
    public Container getContainer()
    {
        return _container;
    }

    @Override
    public String getDisplayConfigs()
    {
        return BANNER_CONFIGS;
    }

    @Override
    public String getDisplayPropertyName()
    {
        return SHOW_BANNER_PROPERTY_NAME;
    }

    @Override
    public String getModulePropertyName()
    {
        return BANNER_MODULE_PROPERTY_NAME;
    }

    @Override
    public String getFileName()
    {
        return FILE_NAME;
    }

    @Override
    public String getShowByDefault()
    {
        return String.valueOf(false);
    }
}
