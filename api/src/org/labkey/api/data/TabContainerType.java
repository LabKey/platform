package org.labkey.api.data;

import org.labkey.api.admin.ImportContext;

public class TabContainerType extends NormalContainerType implements ContainerType
{
    public static final String NAME = "tab";

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public Boolean canHaveChildren()
    {
        return false;
    }

    @Override
    public Boolean includeForImportExport(ImportContext context)
    {
        return true;
    }

    @Override
    public Boolean shouldRemoveFromPortal()
    {
        return true;
    }

    @Override
    public Boolean includePropertiesAsChild(boolean includeTabs)
    {
        return includeTabs;
    }

    @Override
    public Boolean isInFolderNav()
    {
        return false;
    }

    @Override
    public Boolean isContainerFor(DataType dataType)
    {
        switch (dataType)
        {
            case fileRoot:
            case folderManagement:
            case tabs:
                return false;
            default:
                return true;
        }
    }

    @Override
    public Container getContainerFor(DataType dataType, Container currentContainer)
    {
        switch (dataType)
        {
            case fileRoot:
            case folderManagement:
            case tabs:
                return currentContainer.getParent();
            default:
                return currentContainer;
        }
    }

}
