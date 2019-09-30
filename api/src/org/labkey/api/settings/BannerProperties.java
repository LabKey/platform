package org.labkey.api.settings;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;

public class BannerProperties implements TemplateProperties
{
    private final Container _container;

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
        return "BannerProperties";
    }

    @Override
    public String getDisplayPropertyName()
    {
        return "ShowBanner";
    }

    @Override
    public String getModulePropertyName()
    {
        return "BannerModule";
    }

    @Override
    public String getFileName()
    {
        return "_banner";
    }

    @Override
    public String getPropertyDisplayType()
    {
        return "Banner";
    }

    @Override
    public String getDefaultModule()
    {
        return null;
    }

    @Override
    public TemplateProperties getRootProperties()
    {
        return new BannerProperties(ContainerManager.getRoot());
    }
}
